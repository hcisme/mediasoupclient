package io.github.hcisme.mediasoupclient.client

import android.content.Context
import android.util.Log
import io.github.crow_misia.mediasoup.Consumer
import io.github.crow_misia.mediasoup.Producer
import io.github.hcisme.mediasoupclient.controller.AudioController
import io.github.hcisme.mediasoupclient.controller.VideoController
import io.github.hcisme.mediasoupclient.model.ConsumeResponse
import io.github.hcisme.mediasoupclient.model.JoinResponse
import io.github.hcisme.mediasoupclient.model.ProduceResponse
import io.github.hcisme.mediasoupclient.model.RemoteStreamState
import io.github.hcisme.mediasoupclient.model.TransportInfo
import io.github.hcisme.mediasoupclient.utils.JsonKey
import io.github.hcisme.mediasoupclient.utils.MediaType
import io.github.hcisme.mediasoupclient.utils.SocketEvent
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
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap

class RoomClient(private val context: Context) {
    companion object {
        private const val TAG = "RoomClient"
        private const val SERVER_URL = "http://192.168.2.5:3000"
        val eglBaseContext: EglBase.Context by lazy { EglBase.create().eglBaseContext }
    }

    // 协程、锁
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val recvTransportMutex = Mutex()

    // 状态标志
    private var isJoiningOrJoined = false

    // === 三层架构核心组件 ===
    private val signaling = SignalingClient(serverUrl = SERVER_URL)
    private val mediaSoup = MediaSoupManager(context = context, eglBaseContext = eglBaseContext)

    // 音视频控制器
    val videoController = VideoController(context, mediaSoup.peerConnectionFactory, eglBaseContext)
    val audioController = AudioController(context, mediaSoup.peerConnectionFactory)

    // 状态流
    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomId = _currentRoomId.asStateFlow()

    private val _localState = MutableStateFlow(LocalMediaState())
    val localState = _localState.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks = _remoteVideoTracks.asStateFlow()

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
        observeLocalControllers()
    }

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

    private fun observeLocalControllers() {
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

    fun connectToRoom(roomId: String, onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        _currentRoomId.update { roomId }

        signaling.connect(
            onConnect = {
                if (isJoiningOrJoined) return@connect
                isJoiningOrJoined = true
                Log.d(TAG, "Connected to server, joining room...")
                scope.launch {
                    try {
                        joinRoom(roomId)
                        onSuccess()
                    } catch (e: Exception) {
                        Log.e(TAG, "Join room failed", e)
                        onError()
                    }
                }
            },
            onDisconnect = {
                Log.d(TAG, "Socket disconnected")
                isJoiningOrJoined = false
            },
            onPeerJoined = {},
            onNewProducer = { data ->
                _remoteStreamStates.update { state ->
                    state + (data.producerId to RemoteStreamState(
                        producerId = data.producerId,
                        kind = data.kind,
                        isPaused = data.paused,
                        socketId = data.socketId
                    ))
                }
                scope.launch { consumeStream(data.producerId) }
            },
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
            }
        )
    }

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
            id = sendInfo.id,
            iceParameters = sendInfo.iceParameters.toString(),
            iceCandidates = sendInfo.iceCandidates.toString(),
            dtlsParameters = sendInfo.dtlsParameters.toString()
        )
        mediaSoup.createReceiveTransport(
            id = recvInfo.id,
            iceParameters = recvInfo.iceParameters.toString(),
            iceCandidates = recvInfo.iceCandidates.toString(),
            dtlsParameters = recvInfo.dtlsParameters.toString()
        )

        // 处理已经在房间的用户
        joinResponse.existingProducers.forEach { producer ->
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

    private suspend fun consumeStream(producerId: String) {
        recvTransportMutex.withLock {
            withContext(Dispatchers.IO) {
                val data = JSONObject().apply {
                    put(JsonKey.PRODUCER_ID, producerId)
                    put(JsonKey.TRANSPORT_ID, mediaSoup.recvTransport!!.id)
                    put(JsonKey.RTP_CAPABILITIES, JSONObject(mediaSoup.getRtpCapabilities()))
                }

                try {
                    val res = signaling.suspendSafeEmit<ConsumeResponse>(SocketEvent.CONSUME, data)

                    val consumer = mediaSoup.recvTransport?.consume(
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
                            (consumer?.track as? VideoTrack)?.let { track ->
                                track.setEnabled(true)
                                _remoteVideoTracks.update { it + (res.producerId to track) }
                            }
                        }

                        MediaType.AUDIO -> {
                            (consumer?.track as? AudioTrack)?.let { track ->
                                track.setEnabled(true)
                                remoteAudioTracks[res.producerId] = track
                            }
                        }
                    }

                    // Resume
                    signaling.emit(
                        SocketEvent.RESUME,
                        JSONObject().put(JsonKey.CONSUMER_ID, res.id)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Consume failed", e)
                }
            }
        }
    }

    private fun handleConsumerClosed(consumerId: String) {
        val producerId = consumerIdToProducerId[consumerId] ?: return

        remoteAudioTracks[producerId].safeDispose()
        remoteAudioTracks.remove(producerId)

        _remoteVideoTracks.value[producerId].safeDispose()
        _remoteVideoTracks.update { it - producerId }

        consumerIdToProducerId.remove(consumerId)
        Log.d(TAG, "Remote track removed: producerId=$producerId, consumerId=$consumerId")
    }

    fun startLocalMedia(isOpenCamera: Boolean, isOpenMic: Boolean) {
        _localState.update { it.copy(isCameraOff = !isOpenCamera, isMicMuted = !isOpenMic) }

        if (isOpenCamera) {
            videoController.start()
            val track = videoController.localVideoTrackFlow.value
            if (track != null && mediaSoup.sendTransport != null) {
                localVideoProducer = mediaSoup.sendTransport?.produce(
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
                    JSONObject().put("producerId", localAudioProducer!!.id)
                )
                audioController.setMicMuted(true)
            }
        } else {
            Log.w(TAG, "Audio start failed")
            _localState.update { it.copy(isMicMuted = true) }
        }
    }

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

    fun exitRoom() {
        try {
            isJoiningOrJoined = false
            _currentRoomId.update { null }

            signaling.disconnect()

            localVideoProducer?.close()
            localAudioProducer?.close()

            mediaSoup.dispose()
            videoController.dispose()
            audioController.dispose()

            _remoteVideoTracks.value.forEach { (_, t) -> t.dispose() }
            remoteAudioTracks.values.forEach { it.dispose() }
            Log.d(TAG, "Exit Room Success")
        } catch (e: Exception) {
            Log.e(TAG, "Exit Room Exception", e)
        } finally {
            localVideoProducer = null
            localAudioProducer = null
            _remoteVideoTracks.value = emptyMap()
            remoteAudioTracks.clear()
            consumerIdToProducerId.clear()
            _remoteStreamStates.value = emptyMap()
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
        val videoTrack: VideoTrack? = null
    )
}