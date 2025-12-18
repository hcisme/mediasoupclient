package io.github.hcisme.mediasoupclient.client

import android.content.Context
import android.util.Log
import io.github.crow_misia.mediasoup.Consumer
import io.github.crow_misia.mediasoup.Producer
import io.github.hcisme.mediasoupclient.BuildConfig
import io.github.hcisme.mediasoupclient.controller.AudioController
import io.github.hcisme.mediasoupclient.controller.VideoController
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
        private const val SERVER_URL = BuildConfig.BASE_URL
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

        mediaSoup.onProduceTransport = { id, kind, rtp ->
            runBlocking(Dispatchers.IO) {
                val data = JSONObject().apply {
                    put(JsonKey.TRANSPORT_ID, id)
                    put(JsonKey.KIND, kind)
                    put(JsonKey.RTP_PARAMETERS, JSONObject(rtp))
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

        signaling.connect(
            onConnect = {
                if (isJoiningOrJoined) return@connect
                isJoiningOrJoined = true
                Log.d(TAG, "Connected to server, joining room...")
                scope.launch {
                    try {
                        joinRoom(roomId)
                        withContext(Dispatchers.Main) { onSuccess() }
                    } catch (e: Exception) {
                        Log.e(TAG, "Join room failed", e)
                        withContext(Dispatchers.Main) { onError() }
                    }
                }
            },
            onDisconnect = {
                Log.d(TAG, "Socket disconnected")
                isJoiningOrJoined = false
            },
            onPeerJoined = { socketId ->
                _remotePeers.update { current ->
                    if (current.containsKey(socketId)) current
                    else current + (socketId to RemotePeer(socketId))
                }
            },
            onNewProducer = { handleNewProducer(it) },
            onConsumerClosed = { consumerId ->
                handleConsumerClosed(consumerId)
            },
            onProducerPaused = { data ->
                updateRemoteState(data.producerId) { it.copy(isPaused = true) }
            },
            onProducerResumed = { data ->
                updateRemoteState(data.producerId) { it.copy(isPaused = false) }
            },
            onProducerScore = { producerId, score ->
                updateRemoteState(producerId) { it.copy(score = score) }
            },
            onPeerLeave = { socketId ->
                handlePeerLeave(socketId = socketId)
            },
            onActiveSpeaker = { handleActiveSpeaker(volumes = it) }
        )
    }

    /**
     * videoController 状态观察
     */
    private fun observeLocalControllers() {
        roomSessionJob?.cancel()
        roomSessionJob = SupervisorJob()

        val sessionScope = scope + roomSessionJob!!
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
            _remotePeers.update { current ->
                val peer = current[producer.socketId] ?: RemotePeer(socketId = producer.socketId)
                val newPeer = if (producer.kind == MediaType.VIDEO) {
                    peer.copy(videoProducerId = producer.producerId)
                } else {
                    peer.copy(audioProducerId = producer.producerId)
                }
                current + (producer.socketId to newPeer)
            }

            _remoteStreamStates.update { current ->
                current + (producer.producerId to RemoteStreamState(
                    producerId = producer.producerId,
                    kind = producer.kind,
                    isPaused = producer.paused,
                    socketId = producer.socketId
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
        _remotePeers.update { current ->
            val peer = current[data.socketId] ?: RemotePeer(socketId = data.socketId)
            val newPeer = if (data.kind == MediaType.VIDEO) {
                peer.copy(videoProducerId = data.producerId)
            } else {
                peer.copy(audioProducerId = data.producerId)
            }
            current + (data.socketId to newPeer)
        }

        _remoteStreamStates.update { state ->
            state + (data.producerId to RemoteStreamState(
                producerId = data.producerId,
                kind = data.kind,
                isPaused = data.paused,
                socketId = data.socketId
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
                            updateRemoteState(producerId = res.producerId) { it.copy(videoTrack = track) }
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

        _remoteStreamStates.value[producerId]?.videoTrack.safeDispose()
        _remoteStreamStates.update { it - producerId }

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
        _localState.update { it.copy(isCameraOff = !isOpenCamera, isMicMuted = !isOpenMic) }

        if (isOpenCamera) {
            videoController.start()
            val track = videoController.localVideoTrackFlow.value
            if (track != null && mediaSoup.sendTransport != null) {
                localVideoProducer = mediaSoup.sendTransport!!.produce(
                    listener = producerListener(MediaType.VIDEO),
                    track = track
                )
            } else {
                Log.w(TAG, "Video start failed")
                _localState.update { it.copy(isCameraOff = true) }
            }
        }

        // 音频输出
        audioController.initAudioSystem()
        // 音频输入
        val audioTrack = audioController.createLocalAudioTrack()
        if (audioTrack != null && mediaSoup.sendTransport != null) {
            localAudioProducer = mediaSoup.sendTransport?.produce(
                listener = producerListener(MediaType.AUDIO),
                track = audioTrack
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
            _localState.update { it.copy(isMicMuted = true) }
        }
    }

    /**
     * 开关 Camera
     */
    fun toggleCamera() {
        val newOff = !_localState.value.isCameraOff

        scope.launch {
            try {
                if (newOff) {
                    localVideoProducer?.pause()
                    signaling.emit(
                        SocketEvent.PAUSE_PRODUCER,
                        JSONObject().put(JsonKey.PRODUCER_ID, localVideoProducer?.id)
                    )
                    videoController.localVideoTrackFlow.value?.setEnabled(false)
                    videoController.stopCapture()
                } else {
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
                            track = track
                        )
                    } else {
                        localVideoProducer!!.resume()
                        signaling.emit(
                            SocketEvent.RESUME_PRODUCER,
                            JSONObject().put(JsonKey.PRODUCER_ID, localVideoProducer?.id)
                        )
                    }
                }
                _localState.update { it.copy(isCameraOff = newOff) }
                Log.d(TAG, "Camera toggled: off=$newOff")
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
                    track = track
                )
            } ?: return
        }

        val producer = localAudioProducer!!
        val newMuted = !_localState.value.isMicMuted

        scope.launch {
            try {
                if (newMuted) {
                    producer.pause()
                    signaling.emit(
                        SocketEvent.PAUSE_PRODUCER,
                        JSONObject().put(JsonKey.PRODUCER_ID, producer.id)
                    )
                    audioController.setMicMuted(true)
                } else {
                    producer.resume()
                    signaling.emit(
                        SocketEvent.RESUME_PRODUCER,
                        JSONObject().put(JsonKey.PRODUCER_ID, producer.id)
                    )
                    audioController.setMicMuted(false)
                }
                _localState.update { it.copy(isMicMuted = newMuted) }
                Log.d(TAG, "Mic toggled: muted=$newMuted")
            } catch (e: Exception) {
                Log.e(TAG, "Toggle Mic Failed", e)
            }

        }
    }

    /**
     * 退出房间
     */
    fun exitRoom() {
        try {
            isJoiningOrJoined = false
            _currentRoomId.update { null }

            signaling.disconnect()

            localVideoProducer?.close()
            localAudioProducer?.close()

            roomSessionJob?.cancel()

            videoController.dispose()
            audioController.dispose()

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
            Log.d(TAG, "$source Producer transport closed")
        }
    }

    private fun updateRemoteState(
        producerId: String,
        update: (RemoteStreamState) -> RemoteStreamState
    ) {
        _remoteStreamStates.update { current ->
            current[producerId]?.let { current + (producerId to update(it)) } ?: current
        }
    }

    data class LocalMediaState(
        val isCameraOff: Boolean = false,
        val isFrontCamera: Boolean = true,
        val isMicMuted: Boolean = false,
        val videoTrack: VideoTrack? = null,
        val volume: Int? = null
    )

    data class RemotePeer(
        val socketId: String,
        val videoProducerId: String? = null,
        val audioProducerId: String? = null
    )
}