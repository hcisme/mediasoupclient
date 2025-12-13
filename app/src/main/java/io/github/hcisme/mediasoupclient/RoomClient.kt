package io.github.hcisme.mediasoupclient

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.crow_misia.mediasoup.Consumer
import io.github.crow_misia.mediasoup.Device
import io.github.crow_misia.mediasoup.MediasoupClient
import io.github.crow_misia.mediasoup.Producer
import io.github.crow_misia.mediasoup.RecvTransport
import io.github.crow_misia.mediasoup.SendTransport
import io.github.crow_misia.mediasoup.Transport
import io.github.crow_misia.webrtc.log.DefaultLogHandler
import io.github.hcisme.mediasoupclient.controller.AudioController
import io.github.hcisme.mediasoupclient.controller.VideoController
import io.github.hcisme.mediasoupclient.model.ConsumeResponse
import io.github.hcisme.mediasoupclient.model.JoinResponse
import io.github.hcisme.mediasoupclient.model.NewProducerResponse
import io.github.hcisme.mediasoupclient.model.ProduceResponse
import io.github.hcisme.mediasoupclient.model.RemoteStreamState
import io.github.hcisme.mediasoupclient.model.TransportInfo
import io.github.hcisme.mediasoupclient.utils.JsonKey
import io.github.hcisme.mediasoupclient.utils.MediaType
import io.github.hcisme.mediasoupclient.utils.SocketEvent
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoTrack
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class RoomClient(private val context: Context) {
    companion object {
        private const val TAG = "RoomClient"
        private const val SERVER_URL = "http://192.168.2.5:3000"
        private const val TIMEOUT_MS = 5000L
        val eglBaseContext: EglBase.Context by lazy { EglBase.create().eglBaseContext }
    }

    private val recvTransportMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var socket: Socket? = null
    private val jsonFormat = Json { ignoreUnknownKeys = true }
    private var isJoiningOrJoined = false
    private var isMediasoupInitialized = false
    private lateinit var peerConnectionFactory: PeerConnectionFactory

    // 房间号
    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomId = _currentRoomId.asStateFlow()

    // 音视频控制器
    lateinit var videoController: VideoController
    lateinit var audioController: AudioController

    // Mediasoup 核心对象
    private var device: Device? = null
    private var sendTransport: SendTransport? = null
    private var recvTransport: RecvTransport? = null
    private var localVideoProducer: Producer? = null
    private var localAudioProducer: Producer? = null

    // 本地状态
    private val _localState = MutableStateFlow(LocalMediaState())
    val localState = _localState.asStateFlow()

    // 远程
    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks = _remoteVideoTracks.asStateFlow()
    private val remoteAudioTracks = ConcurrentHashMap<String, AudioTrack>()
    private val _remoteStreamStates = MutableStateFlow<Map<String, RemoteStreamState>>(emptyMap())
    val remoteStreamStates = _remoteStreamStates.asStateFlow()
    private val consumerIdToProducerId = ConcurrentHashMap<String, String>()

    init {
        setupMediasoupAndWebRTC()
        setupControllers()
        scope.launch {
            videoController.localVideoTrackFlow.collect { track ->
                _localState.update { it.copy(videoTrack = track) }
            }
        }

        scope.launch {
            videoController.isFrontCamera.collect { isFront ->
                _localState.update { it.copy(isFrontCamera = isFront) }
            }
        }
    }

    private fun setupMediasoupAndWebRTC() {
        if (isMediasoupInitialized) return
        MediasoupClient.initialize(
            context = context.applicationContext as Application,
            logHandler = DefaultLogHandler
        )
        val options =
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .createPeerConnectionFactory()
        isMediasoupInitialized = true
    }

    private fun setupControllers() {
        videoController = VideoController(context, peerConnectionFactory, eglBaseContext)
        audioController = AudioController(context, peerConnectionFactory)
    }

    fun connectToRoom(roomId: String, onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        _currentRoomId.update { roomId }

        socket = IO.socket(SERVER_URL).apply {
            on(SocketEvent.CONNECT) {
                // 防止重连时重复 join
                if (isJoiningOrJoined) return@on
                isJoiningOrJoined = true

                Log.d(TAG, "Connected to server")
                scope.launch {
                    try {
                        joinRoom(roomId, onSuccess)
                    } catch (e: Exception) {
                        Log.e(TAG, "Join room failed", e)
                        onError()
                    }
                }
            }
        }

        setupNewProducerListener()
        setupConsumerClosedListener()
        setupRemoteStateListeners()
        socket?.connect()
    }

    /**
     * 监听新的生产者 (别人开视频)
     */
    private fun setupNewProducerListener() {
        socket?.on(SocketEvent.NEW_PRODUCER) { args ->
            runCatching {
                val data = args.firstOrNull() as? JSONObject
                    ?: error("Empty response for ${SocketEvent.NEW_PRODUCER}")
                val newProducerResponse =
                    jsonFormat.decodeFromString<NewProducerResponse>(data.toString())
                val producerId = newProducerResponse.producerId

                _remoteStreamStates.update { state ->
                    state + (producerId to RemoteStreamState(
                        producerId = producerId,
                        kind = newProducerResponse.kind,
                        isPaused = newProducerResponse.paused,
                        socketId = newProducerResponse.socketId
                    ))
                }

                scope.launch { consumeStream(producerId) }
            }.onFailure { error ->
                Log.e(TAG, "Error Handling NewProducer Event", error)
            }
        }
    }

    /**
     * 监听消费者关闭 离开
     */
    private fun setupConsumerClosedListener() {
        socket?.on(SocketEvent.CONSUMER_CLOSED) { args ->
            runCatching {
                val data = args.firstOrNull() as? JSONObject
                val consumerId = data?.getString(JsonKey.CONSUMER_ID)

                consumerId?.let { handleConsumerClosed(it) }
            }.onFailure { error ->
                Log.e(TAG, "Error Handling ConsumerClosed Event", error)
            }
        }
    }

    /**
     * 处理消费者关闭 离开 释放音视频轨道
     */
    private fun handleConsumerClosed(consumerId: String) {
        val producerId = consumerIdToProducerId[consumerId]

        producerId?.let { pid ->
            // 释放音频轨道
            remoteAudioTracks[pid].safeDispose()
            remoteAudioTracks.remove(pid)

            // 释放视频轨道
            val trackToRemove = _remoteVideoTracks.value[pid]
            trackToRemove.safeDispose()
            _remoteVideoTracks.update { currentMap ->
                currentMap - pid
            }
            consumerIdToProducerId.remove(consumerId)

            Log.d(TAG, "Remote track removed: producerId=$pid, consumerId=$consumerId")
        } ?: Log.w(TAG, "Could not find producerId for consumerId: $consumerId")
    }

    private fun setupRemoteStateListeners() {
        // 别人暂停了 (Mic/Camera)
        socket?.on(SocketEvent.PRODUCER_PAUSED) { args ->
            handleRemotePauseResume(args, isPaused = true)
        }

        // 别人恢复了
        socket?.on(SocketEvent.PRODUCER_RESUMED) { args ->
            handleRemotePauseResume(args, isPaused = false)
        }

        // 网络质量评分 (Score)
        socket?.on(SocketEvent.PRODUCER_SCORE) { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val producerId = data.optString("producerId")
            val scores = data.optJSONArray("score")

            // Mediasoup 的 score 是个数组，取第一个即可
            // 结构: [{ ssrc: 123, score: 10 }]
            if (scores != null && scores.length() > 0) {
                val scoreObj = scores.getJSONObject(0)
                val scoreVal = scoreObj.optInt("score", 0)

                _remoteStreamStates.update { current ->
                    val oldState = current[producerId]
                    if (oldState != null) {
                        current + (producerId to oldState.copy(score = scoreVal))
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun handleRemotePauseResume(args: Array<Any>, isPaused: Boolean) {
        val data = args.firstOrNull() as? JSONObject ?: return
        val producerId = data.optString("producerId")
        // "audio" or "video"
        val kind = data.optString("kind")
        val socketId = data.optString("socketId")

        _remoteStreamStates.update { current ->
            val oldState = current[producerId]
            // 如果之前没有记录，现在新建一个
            val newState = oldState?.copy(isPaused = isPaused)
                ?: RemoteStreamState(producerId, kind, isPaused = isPaused, socketId = socketId)

            current + (producerId to newState)
        }
        Log.d(TAG, "Remote $kind ($producerId) paused: $isPaused")
    }

    private suspend fun joinRoom(roomId: String, onSuccess: () -> Unit) = coroutineScope {
        val response = suspendEmit<JoinResponse>(
            SocketEvent.JOIN_ROOM,
            JSONObject().apply { put(JsonKey.ROOM_ID, roomId) }
        )

        initializeDevice(rtpCapabilities = response.rtpCapabilities.toString())

        val (sendInfo, recvInfo) = awaitAll(
            async { createWebRtcTransportInfo(true) },
            async { createWebRtcTransportInfo(false) }
        )
        sendTransport = createTransportFromInfo(true, sendInfo) as? SendTransport
            ?: error("Failed to create SendTransport")
        recvTransport = createTransportFromInfo(false, recvInfo) as? RecvTransport
            ?: error("Failed to create RecvTransport")

        response.existingProducers.map { producer ->
            // 新增进房时，把现有的流状态存起来
            _remoteStreamStates.update { current ->
                current + (producer.producerId to RemoteStreamState(
                    producerId = producer.producerId,
                    kind = producer.kind ?: "video",
                    isPaused = producer.paused ?: false,
                    socketId = producer.socketId
                ))
            }

            try {
                consumeStream(producer.producerId)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to consume existing producer ${producer.producerId}",
                    e
                )
            }
        }

        onSuccess()
    }

    private suspend fun initializeDevice(rtpCapabilities: String) = withContext(Dispatchers.IO) {
        device = Device(peerConnectionFactory).apply {
            load(rtpCapabilities)
        }
    }

    private suspend fun createWebRtcTransportInfo(sender: Boolean): TransportInfo {
        return suspendEmit<TransportInfo>(
            SocketEvent.CREATE_WEBRTC_TRANSPORT,
            JSONObject().apply { put(JsonKey.SENDER, sender) }
        )
    }

    private fun createTransportFromInfo(sender: Boolean, info: TransportInfo): Transport {
        return if (sender) {
            requireNotNull(device).createSendTransport(
                listener = sendListener,
                id = info.id,
                iceParameters = info.iceParameters.toString(),
                iceCandidates = info.iceCandidates.toString(),
                dtlsParameters = info.dtlsParameters.toString()
            )
        } else {
            requireNotNull(device).createRecvTransport(
                listener = recvListener,
                id = info.id,
                iceParameters = info.iceParameters.toString(),
                iceCandidates = info.iceCandidates.toString(),
                dtlsParameters = info.dtlsParameters.toString()
            )
        }
    }

    // SendTransport 监听器
    private val sendListener = object : SendTransport.Listener {
        override fun onConnect(transport: Transport, dtlsParameters: String) {
            val requestData = JSONObject().apply {
                put(JsonKey.TRANSPORT_ID, transport.id)
                put(JsonKey.DTLS_PARAMETERS, JSONObject(dtlsParameters))
            }
            socket?.emit(SocketEvent.CONNECT_TRANSPORT, requestData)
        }

        override fun onProduce(
            transport: Transport,
            kind: String,
            rtpParameters: String,
            appData: String?
        ): String {
            val requestData = JSONObject().apply {
                put(JsonKey.TRANSPORT_ID, transport.id)
                put(JsonKey.KIND, kind)
                put(JsonKey.RTP_PARAMETERS, JSONObject(rtpParameters))
            }

            return try {
                runBlocking(Dispatchers.IO) {
                    withTimeout(TIMEOUT_MS) {
                        suspendEmit<ProduceResponse>(SocketEvent.PRODUCE, requestData).id
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onProduce failed", e)
                ""
            }
        }

        override fun onConnectionStateChange(transport: Transport, newState: String) {
            Log.d(TAG, "Send transport connection state changed: $newState")
        }

        override fun onProduceData(
            transport: Transport,
            sctpStreamParameters: String,
            label: String,
            protocol: String,
            appData: String?
        ): String = ""
    }

    // RecvTransport 监听
    private val recvListener = object : RecvTransport.Listener {
        override fun onConnect(transport: Transport, dtlsParameters: String) {
            val requestData = JSONObject().apply {
                put(JsonKey.TRANSPORT_ID, transport.id)
                put(JsonKey.DTLS_PARAMETERS, JSONObject(dtlsParameters))
            }
            socket?.emit(SocketEvent.CONNECT_TRANSPORT, requestData)
        }

        override fun onConnectionStateChange(transport: Transport, newState: String) {
            Log.d(TAG, "Receive transport connection state changed: $newState")
        }
    }

    // 消费流
    private suspend fun consumeStream(producerId: String) {
        recvTransportMutex.withLock {
            withContext(Dispatchers.IO) {
                val requestData = JSONObject().apply {
                    put(JsonKey.PRODUCER_ID, producerId)
                    put(JsonKey.TRANSPORT_ID, recvTransport!!.id)
                    put(JsonKey.RTP_CAPABILITIES, JSONObject(device!!.rtpCapabilities))
                }

                try {
                    val response = suspendEmit<ConsumeResponse>(SocketEvent.CONSUME, requestData)
                    processConsumer(response)
                } catch (e: Exception) {
                    Log.e(TAG, "Consume failed for producer $producerId", e)
                }
            }
        }
    }

    private fun processConsumer(response: ConsumeResponse) {
        val consumer = recvTransport?.consume(
            listener = object : Consumer.Listener {
                override fun onTransportClose(consumer: Consumer) {
                    Log.d(TAG, "Consumer transport closed for consumerId: ${consumer.id}")
                }
            },
            id = response.id,
            producerId = response.producerId,
            kind = response.kind,
            rtpParameters = response.rtpParameters.toString()
        )

        consumerIdToProducerId[response.id] = response.producerId

        when (response.kind) {
            MediaType.VIDEO -> setupVideoConsumer(response, consumer)
            MediaType.AUDIO -> setupAudioConsumer(response, consumer)
            else -> Log.w(TAG, "Unknown media kind: ${response.kind}")
        }

        // 恢复消费者
        resumeConsumer(response.id)
    }

    private fun setupVideoConsumer(response: ConsumeResponse, consumer: Consumer?) {
        val videoTrack = consumer?.track as? VideoTrack
        videoTrack?.apply {
            setEnabled(true)
            _remoteVideoTracks.update { currentMap ->
                currentMap + (response.producerId to this@apply)
            }
        } ?: Log.w(TAG, "Failed to get video track from consumer")
    }

    private fun setupAudioConsumer(response: ConsumeResponse, consumer: Consumer?) {
        val audioTrack = consumer?.track as? AudioTrack
        audioTrack?.apply {
            setEnabled(true)
            remoteAudioTracks[response.producerId] = this@apply
        } ?: Log.w(TAG, "Failed to get audio track from consumer")
    }

    private fun resumeConsumer(consumerId: String) {
        val resumeRequest = JSONObject().apply {
            put(JsonKey.CONSUMER_ID, consumerId)
        }
        socket?.emit(SocketEvent.RESUME, resumeRequest)
    }

    /**
     * 开启本地媒体 摄像头 Mic
     */
    fun startLocalMedia(isOpenCamera: Boolean, isOpenMic: Boolean) {
        _localState.update { it.copy(isCameraOff = !isOpenCamera, isMicMuted = !isOpenMic) }

        // 处理视频
        if (isOpenCamera) {
            videoController.start()
            val videoTrack = videoController.localVideoTrackFlow.value

            if (videoTrack != null && sendTransport != null) {
                localVideoProducer = sendTransport?.produce(
                    listener = producerListener("Video"),
                    track = videoTrack
                )
            } else {
                Log.w(TAG, "Video start failed (no permission?), rolling back state")
                _localState.update { it.copy(isCameraOff = true) }
            }
        } // else: 如果 isOpenCamera=false，则不采集、不推流

        // 音频输出
        audioController.initAudioSystem()
        // 音频输入
        val audioTrack = audioController.createLocalAudioTrack()
        if (audioTrack != null && sendTransport != null) {
            localAudioProducer = sendTransport?.produce(
                listener = producerListener("Audio"),
                track = audioTrack
            )
            if (!isOpenMic) {
                localAudioProducer!!.pause()
                socket?.emit(
                    SocketEvent.PAUSE_PRODUCER,
                    JSONObject().put("producerId", localAudioProducer!!.id)
                )
                audioController.setMicMuted(true)
            }
        } else {
            Log.w(TAG, "Audio start failed (no permission?), rolling back state")
            _localState.update { it.copy(isMicMuted = true) }
        }
    }

    private fun producerListener(source: String) = object : Producer.Listener {
        override fun onTransportClose(producer: Producer) {
            Log.d(TAG, "$source Producer transport closed")
        }
    }

    /**
     * 开关本地麦克风
     */
    fun toggleMic() {
        if (localAudioProducer == null) {
            val audioTrack = audioController.createLocalAudioTrack()
            if (audioTrack != null && sendTransport != null) {
                localAudioProducer = sendTransport?.produce(
                    listener = producerListener("Audio"),
                    track = audioTrack
                )
            } else {
                Log.e(TAG, "Toggle mic failed: still no permission or device error")
                return
            }
        }

        val producer = localAudioProducer!!
        val newMuted = !_localState.value.isMicMuted

        scope.launch {
            try {
                if (newMuted) {
                    producer.pause()
                    socket?.emit(
                        SocketEvent.PAUSE_PRODUCER,
                        JSONObject().put("producerId", producer.id)
                    )
                    // 硬件层面静音
                    audioController.setMicMuted(true)
                } else {
                    producer.resume()
                    socket?.emit(
                        SocketEvent.RESUME_PRODUCER,
                        JSONObject().put("producerId", producer.id)
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
     * 开关本地摄像头
     */
    fun toggleCamera() {
        val newOff = !_localState.value.isCameraOff

        scope.launch {
            try {
                if (newOff) {
                    localVideoProducer?.pause()
                    socket?.emit(
                        SocketEvent.PAUSE_PRODUCER,
                        JSONObject().put("producerId", localVideoProducer?.id)
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
                        // 保持 isCameraOff = true (不更新状态)，并直接返回
                        return@launch
                    }
                    track.setEnabled(true)

                    if (localVideoProducer == null) {
                        if (sendTransport != null) {
                            localVideoProducer = sendTransport!!.produce(
                                listener = producerListener("Video"),
                                track = track
                            )
                        }
                    } else {
                        // 只是恢复
                        localVideoProducer!!.resume()
                        socket?.emit(
                            SocketEvent.RESUME_PRODUCER,
                            JSONObject().put("producerId", localVideoProducer!!.id)
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
     * 退出房间调用
     */
    fun exitRoom() {
        _currentRoomId.update { null }
        isJoiningOrJoined = false

        // 断开 Socket
        socket?.apply { disconnect(); off(); }
        socket = null
        videoController.dispose()
        audioController.dispose()

        // 释放所有远程流
        // 视频
        _remoteVideoTracks.value.forEach { (_, track) -> track.safeDispose() }
        _remoteVideoTracks.update { emptyMap() }
        // 音频
        remoteAudioTracks.forEach { (_, track) -> track.safeDispose() }
        remoteAudioTracks.clear()

        consumerIdToProducerId.clear()

        // 释放 Mediasoup 对象
        try {
            localVideoProducer?.close()
            localAudioProducer?.close()
        } catch (error: Exception) {
            Log.e(TAG, "Release Producer Object Error", error)
        }
        localVideoProducer = null
        localAudioProducer = null

        try {
            sendTransport?.apply { close(); dispose(); }
            recvTransport?.apply { close(); dispose(); }
            device?.dispose()
        } catch (error: Exception) {
            Log.e(TAG, "Release Mediasoup Object Error", error)
        }
        sendTransport = null
        recvTransport = null
        device = null

        Log.d(TAG, "Exited Room And Cleaned Up Resources")
    }

    // 通用挂起函数：发送 Socket 请求并等待响应
    private suspend inline fun <reified T> suspendEmit(
        event: String,
        requestData: JSONObject? = null
    ): T = withContext(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) {
            suspendCoroutine { continuation ->
                val args = if (requestData != null) arrayOf(requestData) else emptyArray()

                socket?.emit(event, args) { responses ->
                    try {
                        val jsonStr = (responses.firstOrNull() as? JSONObject)?.toString()
                            ?: error("Empty response for $event")

                        val result = jsonFormat.decodeFromString<T>(jsonStr)
                        continuation.resume(result)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                } ?: continuation.resumeWithException(IOException("Socket not connected"))
            }
        }
    }

    data class LocalMediaState(
        val isCameraOff: Boolean = false,
        val isFrontCamera: Boolean = true,
        val isMicMuted: Boolean = false,
        val videoTrack: VideoTrack? = null
    )
}

fun MediaStreamTrack?.safeDispose() {
    try {
        this?.dispose()
    } catch (e: Exception) {
        Log.e("Track safeDispose Ext", "Failed to dispose track", e)
    }
}
