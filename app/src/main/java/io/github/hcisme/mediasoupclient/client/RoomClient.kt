package io.github.hcisme.mediasoupclient.client

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.crow_misia.mediasoup.Consumer
import io.github.crow_misia.mediasoup.Producer
import io.github.hcisme.mediasoupclient.BuildConfig
import io.github.hcisme.mediasoupclient.controller.AudioController
import io.github.hcisme.mediasoupclient.controller.ScreenShareController
import io.github.hcisme.mediasoupclient.controller.VideoController
import io.github.hcisme.mediasoupclient.service.CallServiceManager
import io.github.hcisme.mediasoupclient.utils.AppDataType
import io.github.hcisme.mediasoupclient.utils.JsonKey
import io.github.hcisme.mediasoupclient.utils.MediaType
import io.github.hcisme.mediasoupclient.utils.SocketEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap

class RoomClient(private val context: Context) {
    companion object {
        private const val TAG = "RoomClient"
        private val SERVER_URL = BuildConfig.BASE_URL
        val eglBaseContext: EglBase.Context by lazy { EglBase.create().eglBaseContext }
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var roomSessionJob: Job? = null
    private val recvTransportMutex = Mutex()
    private var isJoiningOrJoined = false

    private val signaling = SignalingClient(serverUrl = SERVER_URL)
    private val mediaSoup = MediaSoupManager(context = appContext, eglBaseContext = eglBaseContext)

    // 音视频控制器
    val videoController = VideoController(
        context = appContext,
        factory = mediaSoup.peerConnectionFactory,
        eglContext = eglBaseContext
    )
    val audioController = AudioController(
        context = appContext,
        factory = mediaSoup.peerConnectionFactory
    )
    val screenController = ScreenShareController(
        context = appContext,
        factory = mediaSoup.peerConnectionFactory,
        eglContext = eglBaseContext
    )

    // 状态流
    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomId = _currentRoomId.asStateFlow()

    // 本地状态
    private val _localState = MutableStateFlow(LocalMediaState())
    val localState = _localState.asStateFlow()

    // 远程状态
    // key 是 socketId
    private val _remotePeers = MutableStateFlow<Map<String, RemotePeer>>(emptyMap())
    val remotePeers = _remotePeers.asStateFlow()

    private val _remoteStreamStates = MutableStateFlow<Map<String, RemoteStreamState>>(emptyMap())
    val remoteStreamStates = _remoteStreamStates.asStateFlow()

    // 内部
    private val remoteAudioTracks = ConcurrentHashMap<String, AudioTrack>()
    private val consumerIdToProducerId = ConcurrentHashMap<String, String>()

    // 本地 Producer 引用
    private var localVideoProducer: Producer? = null
    private var localAudioProducer: Producer? = null
    private var localScreenProducer: Producer? = null

    init {
        initMediaSoupCallbacks()
    }

    /**
     * 初始化 MediaSoup 类里的回调
     */
    private fun initMediaSoupCallbacks() {
        mediaSoup.onConnectTransport = { id, dtls ->
            val data = JSONObject().apply {
                put(JsonKey.TRANSPORT_ID, id)
                put(JsonKey.DTLS_PARAMETERS, JSONObject(dtls))
            }
            signaling.emit(SocketEvent.CONNECT_TRANSPORT, data)
        }

        mediaSoup.onProduceTransport = { id, kind, rtp, appData ->
            runBlocking(Dispatchers.IO) {
                val data = JSONObject().apply {
                    put(JsonKey.TRANSPORT_ID, id)
                    put(JsonKey.KIND, kind)
                    put(JsonKey.RTP_PARAMETERS, JSONObject(rtp))
                    appData?.let { put(JsonKey.APP_DATA, JSONObject(it)) }
                }
                val res = signaling.suspendSafeEmit<ProduceResponse>(SocketEvent.PRODUCE, data)
                res.id
            }
        }
    }

    /**
     * 连接到房间
     */
    fun connectToRoom(roomId: String, onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        _currentRoomId.update { roomId }
        observeLocalControllers()

        val listener = object : SignalingClient.ConnectListener {
            override fun onConnect() {
                if (isJoiningOrJoined) return
                isJoiningOrJoined = true
                Log.d(TAG, "Connected to server, joining room...")
                scope.launch {
                    try {
                        joinRoom(roomId = roomId)
                        withContext(Dispatchers.Main) { onSuccess() }
                    } catch (e: Exception) {
                        Log.e(TAG, "Join room failed", e)
                        withContext(Dispatchers.Main) { onError() }
                    }
                }
            }

            override fun onDisconnect() {
                Log.d(TAG, "Socket disconnected")
                isJoiningOrJoined = false
            }

            override fun onPeerJoined(socketId: String) {
                _remotePeers.update { current ->
                    if (current.containsKey(socketId)) current
                    else current + (socketId to RemotePeer(socketId))
                }
            }

            override fun onPeerLeave(socketId: String) {
                handlePeerLeave(socketId = socketId)
            }

            override fun onNewProducer(data: NewProducerResponse) {
                handleNewProducer(data = data)
            }

            override fun onConsumerClosed(consumerId: String) {
                handleConsumerClosed(consumerId = consumerId)
            }

            override fun onProducerPaused(data: RemotePauseResumeDataResponse) {
                updateRemoteState(producerId = data.producerId) { it.copy(isPaused = true) }
            }

            override fun onProducerResumed(data: RemotePauseResumeDataResponse) {
                updateRemoteState(producerId = data.producerId) { it.copy(isPaused = false) }
            }

            override fun onActiveSpeaker(volumes: Array<AudioLevelData>) {
                handleActiveSpeaker(volumes = volumes)
            }
        }
        signaling.connect(listener)
    }

    /**
     * videoController 状态观察
     */
    private fun observeLocalControllers() {
        roomSessionJob?.cancel()
        roomSessionJob = SupervisorJob()

        val sessionScope = scope + roomSessionJob!!
        // camera
        sessionScope.launch {
            videoController.localVideoTrackFlow.collect { track ->
                _localState.update { it.copy(videoTrack = track) }
            }
        }
        sessionScope.launch {
            videoController.isFrontCamera.collect { isFront ->
                _localState.update { it.copy(isFrontCamera = isFront) }
            }
        }
        // screen share
        sessionScope.launch {
            screenController.isScreenSharing.collect { isScreenSharing ->
                val track = if (isScreenSharing) screenController.screenTrack else null
                _localState.update {
                    it.copy(
                        isOpenScreenShare = isScreenSharing,
                        screenTrack = track
                    )
                }
            }
        }
    }

    /**
     * 加入房间
     */
    private suspend fun joinRoom(roomId: String) = coroutineScope {
        val joinResponse = signaling.suspendSafeEmit<JoinResponse>(
            SocketEvent.JOIN_ROOM,
            JSONObject().put(JsonKey.ROOM_ID, roomId)
        )

        mediaSoup.loadDevice(rtpCapabilities = joinResponse.rtpCapabilities.toString())

        val (sendInfo, recvInfo) = awaitAll(
            async {
                signaling.suspendSafeEmit<TransportInfo>(
                    SocketEvent.CREATE_WEBRTC_TRANSPORT,
                    JSONObject().put(JsonKey.SENDER, true)
                )
            },
            async {
                signaling.suspendSafeEmit<TransportInfo>(
                    SocketEvent.CREATE_WEBRTC_TRANSPORT,
                    JSONObject().put(JsonKey.SENDER, false)
                )
            }
        )

        mediaSoup.createSendTransport(
            id = sendInfo.transportId,
            iceParameters = sendInfo.iceParameters.toString(),
            iceCandidates = sendInfo.iceCandidates.toString(),
            dtlsParameters = sendInfo.dtlsParameters.toString()
        )
        mediaSoup.createReceiveTransport(
            id = recvInfo.transportId,
            iceParameters = recvInfo.iceParameters.toString(),
            iceCandidates = recvInfo.iceCandidates.toString(),
            dtlsParameters = recvInfo.dtlsParameters.toString()
        )

        // 处理已经在房间的用户
        joinResponse.existingPeers.forEach { socketId ->
            _remotePeers.update { current ->
                if (current.containsKey(socketId)) current
                else current + (socketId to RemotePeer(socketId))
            }
        }

        // 处理已经在房间用户的流
        joinResponse.existingProducers.forEach { producer ->
            val producerId = producer.producerId
            val source = producer.appData.source
            val kind = producer.kind

            _remotePeers.update { current ->
                val peer = current[producer.socketId] ?: RemotePeer(socketId = producer.socketId)
                val newPeer = when {
                    source == AppDataType.SCREEN && kind == MediaType.VIDEO -> {
                        peer.copy(screenProducerId = producerId)
                    }

                    kind == MediaType.VIDEO -> {
                        peer.copy(videoProducerId = producerId)
                    }

                    else -> {
                        peer.copy(audioProducerId = producerId)
                    }
                }
                current + (producer.socketId to newPeer)
            }

            _remoteStreamStates.update { current ->
                current + (producer.producerId to RemoteStreamState(
                    producerId = producer.producerId,
                    kind = producer.kind,
                    isPaused = producer.paused,
                    socketId = producer.socketId,
                    isScreenShare = source == AppDataType.SCREEN
                ))
            }
            // 忽略旧流消费失败，不影响进房
            runCatching { consumeStream(producer.producerId) }
        }
    }

    /**
     * 有新人发布流
     */
    private fun handleNewProducer(data: NewProducerResponse) {
        val producerId = data.producerId
        val source = data.appData.source
        val kind = data.kind
        _remotePeers.update { current ->
            val peer = current[data.socketId] ?: RemotePeer(socketId = data.socketId)
            val newPeer = when {
                source == AppDataType.SCREEN && kind == MediaType.VIDEO -> {
                    peer.copy(screenProducerId = producerId)
                }

                kind == MediaType.VIDEO -> {
                    peer.copy(videoProducerId = producerId)
                }

                else -> {
                    peer.copy(audioProducerId = producerId)
                }
            }
            current + (data.socketId to newPeer)
        }

        _remoteStreamStates.update { state ->
            state + (data.producerId to RemoteStreamState(
                producerId = data.producerId,
                kind = data.kind,
                isPaused = data.paused,
                socketId = data.socketId,
                isScreenShare = source == AppDataType.SCREEN
            ))
        }

        // 消费流
        scope.launch { consumeStream(data.producerId) }
    }

    /**
     * 消费流
     */
    private suspend fun consumeStream(producerId: String, onSuccess: () -> Unit = {}) {
        recvTransportMutex.withLock {
            val transport = mediaSoup.recvTransport
            if (transport == null) {
                Log.w(TAG, "RecvTransport is null, cannot consume")
                return
            }

            withContext(Dispatchers.IO) {
                val data = JSONObject().apply {
                    put(JsonKey.PRODUCER_ID, producerId)
                    put(JsonKey.TRANSPORT_ID, transport.id)
                    put(JsonKey.RTP_CAPABILITIES, JSONObject(mediaSoup.getRtpCapabilities()))
                }

                try {
                    val res = signaling.suspendSafeEmit<ConsumeResponse>(SocketEvent.CONSUME, data)

                    val consumer = transport.consume(
                        listener = object : Consumer.Listener {
                            override fun onTransportClose(consumer: Consumer) {
                                Log.d(
                                    TAG,
                                    "Consumer transport closed for consumerId: ${consumer.id}"
                                )
                            }
                        },
                        id = res.id,
                        producerId = res.producerId,
                        kind = res.kind,
                        rtpParameters = res.rtpParameters.toString()
                    )

                    consumerIdToProducerId[res.id] = res.producerId

                    when (res.kind) {
                        MediaType.VIDEO -> {
                            val track = consumer.track as VideoTrack
                            track.setEnabled(true)
                            updateRemoteState(producerId = res.producerId) { currentState ->
                                if (currentState.isScreenShare) {
                                    currentState.copy(screenTrack = track)
                                } else {
                                    currentState.copy(videoTrack = track)
                                }
                            }
                        }

                        MediaType.AUDIO -> {
                            val track = consumer.track as AudioTrack
                            track.setEnabled(true)
                            remoteAudioTracks[res.producerId] = track
                        }
                    }

                    // Resume
                    signaling.emit(
                        SocketEvent.RESUME,
                        JSONObject().put(JsonKey.CONSUMER_ID, res.id)
                    )
                    onSuccess()
                } catch (e: Exception) {
                    Log.e(TAG, "Consume failed", e)
                }
            }
        }
    }

    /**
     * 消费的人关闭流了
     */
    private fun handleConsumerClosed(consumerId: String) {
        val producerId = consumerIdToProducerId[consumerId] ?: return

        remoteAudioTracks[producerId].safeDispose()
        remoteAudioTracks.remove(producerId)

        val streamState = _remoteStreamStates.value[producerId]
        streamState?.videoTrack?.dispose()
        streamState?.screenTrack?.dispose()
        _remoteStreamStates.update { it - producerId }

        // 从 RemotePeers 中移除对应的 ID 引用
        _remotePeers.update { currentPeers ->
            val targetEntry = currentPeers.entries.find {
                it.value.videoProducerId == producerId ||
                        it.value.audioProducerId == producerId ||
                        it.value.screenProducerId == producerId
            }

            if (targetEntry != null) {
                val (socketId, peer) = targetEntry
                val newPeer = when (producerId) {
                    peer.screenProducerId -> peer.copy(screenProducerId = null) // 屏幕共享结束
                    peer.videoProducerId -> peer.copy(videoProducerId = null)   // 摄像头关闭
                    peer.audioProducerId -> peer.copy(audioProducerId = null)   // 麦克风关闭
                    else -> peer
                }
                currentPeers + (socketId to newPeer)
            } else {
                currentPeers
            }
        }

        consumerIdToProducerId.remove(consumerId)
        Log.d(TAG, "Remote track removed: producerId=$producerId, consumerId=$consumerId")
    }

    /**
     * 有人离开
     */
    private fun handlePeerLeave(socketId: String) {
        _remotePeers.update { current -> current - socketId }

        // 防止有残留
        _remoteStreamStates.update { current ->
            current.filterValues { it.socketId != socketId }
        }
    }

    /**
     * 处理活跃人的声音大小
     */
    private fun handleActiveSpeaker(volumes: Array<AudioLevelData>) {
        volumes.forEach { data ->
            val audioProducerId = data.audioProducerId
            val normalizedVolume = when {
                data.volume >= -20 -> 10
                data.volume >= -40 -> 7
                data.volume >= -60 -> 4
                data.volume >= -80 -> 1
                else -> 0
            }
            // 本地
            if (audioProducerId == localAudioProducer?.id) {
                _localState.update { it.copy(volume = normalizedVolume) }
            }

            // 远程
            updateRemoteState(audioProducerId) { state ->
                state.copy(volume = normalizedVolume)
            }
        }
    }

    /**
     * 开启本地媒体 Camera / Mic
     */
    fun startLocalMedia(isOpenCamera: Boolean, isOpenMic: Boolean) {
        _localState.update { it.copy(isOpenCamera = isOpenCamera, isOpenMic = isOpenMic) }

        if (isOpenCamera) {
            videoController.start()
            val track = videoController.localVideoTrackFlow.value
            if (track != null && mediaSoup.sendTransport != null) {
                localVideoProducer = mediaSoup.sendTransport!!.produce(
                    listener = producerListener(MediaType.VIDEO),
                    track = track,
                    appData = webCamAppData()
                )
            } else {
                Log.w(TAG, "Video start failed")
                _localState.update { it.copy(isOpenCamera = false) }
            }
        }

        // 音频输出
        audioController.initAudioSystem()
        // 音频输入
        val audioTrack = audioController.createLocalAudioTrack()
        if (audioTrack != null && mediaSoup.sendTransport != null) {
            localAudioProducer = mediaSoup.sendTransport?.produce(
                listener = producerListener(MediaType.AUDIO),
                track = audioTrack,
                appData = micAppData()
            )
            if (!isOpenMic) {
                localAudioProducer!!.pause()
                signaling.emit(
                    SocketEvent.PAUSE_PRODUCER,
                    JSONObject().put(JsonKey.PRODUCER_ID, localAudioProducer!!.id)
                )
            }
            audioController.setMicMuted(!isOpenMic)
        } else {
            Log.w(TAG, "Audio start failed")
            _localState.update { it.copy(isOpenMic = false) }
        }
    }

    /**
     * 开关 Camera
     */
    fun toggleCamera() {
        val newOpen = !_localState.value.isOpenCamera

        scope.launch {
            try {
                if (newOpen) {
                    if (videoController.localVideoTrackFlow.value == null) {
                        videoController.start()
                    } else {
                        videoController.resumeCapture()
                    }

                    val track = videoController.localVideoTrackFlow.value
                    if (track == null) {
                        Log.e(TAG, "Failed to start camera (no permission?)")
                        return@launch
                    }
                    track.setEnabled(true)
                    if (localVideoProducer == null) {
                        localVideoProducer = mediaSoup.sendTransport?.produce(
                            listener = producerListener(MediaType.VIDEO),
                            track = track,
                            appData = webCamAppData()
                        )
                    } else {
                        localVideoProducer!!.resume()
                        signaling.emit(
                            SocketEvent.RESUME_PRODUCER,
                            JSONObject().put(JsonKey.PRODUCER_ID, localVideoProducer?.id)
                        )
                    }
                } else {
                    localVideoProducer?.pause()
                    signaling.emit(
                        SocketEvent.PAUSE_PRODUCER,
                        JSONObject().put(JsonKey.PRODUCER_ID, localVideoProducer?.id)
                    )
                    videoController.localVideoTrackFlow.value?.setEnabled(false)
                    videoController.stopCapture()
                }
                _localState.update { it.copy(isOpenCamera = newOpen) }
                Log.d(TAG, "Camera toggled: off=$newOpen")
            } catch (e: Exception) {
                Log.e(TAG, "Toggle Camera Failed", e)
            }
        }
    }

    /**
     * 开关 Mic
     */
    fun toggleMic() {
        // 重试逻辑
        if (localAudioProducer == null) {
            audioController.createLocalAudioTrack()?.let { track ->
                localAudioProducer = mediaSoup.sendTransport?.produce(
                    listener = producerListener(MediaType.AUDIO),
                    track = track,
                    appData = micAppData()
                )
            } ?: return
        }

        val producer = localAudioProducer!!
        val newOpen = !_localState.value.isOpenMic

        scope.launch {
            try {
                if (newOpen) {
                    producer.resume()
                    signaling.emit(
                        SocketEvent.RESUME_PRODUCER,
                        JSONObject().put(JsonKey.PRODUCER_ID, producer.id)
                    )
                    audioController.setMicMuted(false)
                } else {
                    producer.pause()
                    signaling.emit(
                        SocketEvent.PAUSE_PRODUCER,
                        JSONObject().put(JsonKey.PRODUCER_ID, producer.id)
                    )
                    audioController.setMicMuted(true)
                }
                _localState.update { it.copy(isOpenMic = newOpen) }
                Log.d(TAG, "Mic toggled: muted=$newOpen")
            } catch (e: Exception) {
                Log.e(TAG, "Toggle Mic Failed", e)
            }

        }
    }

    /**
     * 开关屏幕共享
     */
    fun toggleScreenShare(permissionIntent: Intent?) {
        val isOpenScreenShare = _localState.value.isOpenScreenShare
        if (!isOpenScreenShare && permissionIntent != null) {
            startScreenShare(permissionIntent = permissionIntent)
        } else {
            stopScreenShare()
        }
    }

    /**
     * 启动屏幕共享
     */
    private fun startScreenShare(permissionIntent: Intent) {
        scope.launch {
            try {
                // 此时 permissionIntent 已经拿到，说明用户点了“允许”，所以升级是安全的
                CallServiceManager.updateScreenShareState(appContext, true)

                // 延迟确保服务类型已升级
                delay(500)

                // 然后再初始化 ScreenCapturer
                val track = screenController.start(permissionIntent)
                if (track != null && mediaSoup.sendTransport != null) {
                    localScreenProducer = mediaSoup.sendTransport!!.produce(
                        listener = producerListener(MediaType.SCREEN),
                        track = track,
                        appData = screenAppData()
                    )
                }
                _localState.update { it.copy(isOpenScreenShare = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Produce screen track failed", e)
                _localState.update { it.copy(isOpenScreenShare = false) }
                screenController.stop()
                CallServiceManager.updateScreenShareState(appContext, false)
            }
        }
    }

    /**
     * 停止屏幕共享
     */
    private fun stopScreenShare() {
        val producerId = localScreenProducer?.id
        localScreenProducer?.close()
        localScreenProducer = null

        screenController.stop()

        _localState.update { it.copy(isOpenScreenShare = false, screenTrack = null) }
        // 服务降级
        CallServiceManager.updateScreenShareState(appContext, false)

        // 主动通知后端关闭共享屏幕流
        if (producerId != null) {
            val data = JSONObject().put(JsonKey.PRODUCER_ID, producerId)
            signaling.emit(SocketEvent.CLOSE_PRODUCER, data)
        }
    }

    /**
     * 退出房间
     */
    fun exitRoom() {
        try {
            isJoiningOrJoined = false
            _currentRoomId.update { null }

            // 先断开信令, 停止接收新消息
            signaling.disconnect()

            // 先关闭所有 Producer (包括屏幕共享)
            // 必须在销毁 MediaSoup 之前做
            stopScreenShare()
            localVideoProducer?.close()
            localAudioProducer?.close()

            // 取消协程任务
            roomSessionJob?.cancel()

            videoController.dispose()
            audioController.dispose()

            // 最后销毁 MediaSoup 核心 (Transport/Device)
            mediaSoup.dispose()

            _remoteStreamStates.value.forEach { (_, t) -> t.videoTrack.safeDispose() }
            remoteAudioTracks.values.forEach { it.safeDispose() }

            Log.d(TAG, "Exit Room Success")
        } catch (e: Exception) {
            Log.e(TAG, "Exit Room Exception", e)
        } finally {
            localVideoProducer = null
            localAudioProducer = null
            roomSessionJob = null
            remoteAudioTracks.clear()
            consumerIdToProducerId.clear()
            _remoteStreamStates.value = emptyMap()
            _remotePeers.value = emptyMap()
            _localState.update { LocalMediaState() }
        }
    }

    private fun producerListener(source: String) = object : Producer.Listener {
        override fun onTransportClose(producer: Producer) {
            if (source == MediaType.SCREEN) {
                stopScreenShare()
            }
            Log.d(TAG, "$source Producer transport closed")
        }
    }

    private fun webCamAppData() = JSONObject().put("source", AppDataType.WEB_CAM).toString()
    private fun micAppData() = JSONObject().put("source", AppDataType.MIC).toString()
    private fun screenAppData() = JSONObject().put("source", AppDataType.SCREEN).toString()

    private fun updateRemoteState(
        producerId: String,
        update: (RemoteStreamState) -> RemoteStreamState
    ) {
        _remoteStreamStates.update { current ->
            current[producerId]?.let { current + (producerId to update(it)) } ?: current
        }
    }

    data class LocalMediaState(
        val isOpenCamera: Boolean = false,
        val isFrontCamera: Boolean = true,
        val isOpenMic: Boolean = false,
        val isOpenScreenShare: Boolean = false,
        val videoTrack: VideoTrack? = null,
        val screenTrack: VideoTrack? = null,
        val volume: Int? = null
    )

    data class RemotePeer(
        val socketId: String,
        val videoProducerId: String? = null,
        val audioProducerId: String? = null,
        val screenProducerId: String? = null
    )
}