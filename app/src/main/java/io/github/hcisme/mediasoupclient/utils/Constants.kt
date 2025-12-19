package io.github.hcisme.mediasoupclient.utils

/**
 * Socket.IO 事件名称
 */
object SocketEvent {
    const val CONNECT = io.socket.client.Socket.EVENT_CONNECT
    const val DISCONNECT = io.socket.client.Socket.EVENT_DISCONNECT
    const val PEER_JOINED = "peerJoined"
    const val PEER_LEAVE = "peerLeave"
    const val NEW_PRODUCER = "newProducer"
    const val CONSUMER_CLOSED = "consumerClosed"
    const val JOIN_ROOM = "joinRoom"
    const val CREATE_WEBRTC_TRANSPORT = "createWebRtcTransport"
    const val CONNECT_TRANSPORT = "connectTransport"
    const val PRODUCE = "produce"
    const val CONSUME = "consume"
    const val RESUME = "resume"
    const val PAUSE_PRODUCER = "pauseProducer" // 发送
    const val RESUME_PRODUCER = "resumeProducer" // 发送

    const val PRODUCER_PAUSED = "producerPaused" // 接收
    const val PRODUCER_RESUMED = "producerResumed" // 接收
    const val ACTIVE_SPEAKER = "activeSpeaker"
    const val CLOSE_PRODUCER = "closeProducer"
}

/**
 * JSON 数据字段 Key
 */
object JsonKey {
    const val ROOM_ID = "roomId"
    const val PRODUCER_ID = "producerId"
    const val AUDIO_PRODUCER_ID = "audioProducerId"
    const val CONSUMER_ID = "consumerId"
    const val TRANSPORT_ID = "transportId"
    const val RTP_CAPABILITIES = "rtpCapabilities"
    const val SENDER = "sender"
    const val KIND = "kind"
    const val RTP_PARAMETERS = "rtpParameters"
    const val DTLS_PARAMETERS = "dtlsParameters"
    const val APP_DATA = "appData"
    const val PAUSED = "paused"
    const val SOCKET_ID = "socketId"
    const val VOLUME = "volume"
}

/**
 * 媒体类型
 */
object MediaType {
    const val VIDEO = "video"
    const val AUDIO = "audio"
    const val SCREEN = "screen"
}

/**
 * 媒体类型
 */
object AppDataType {
    const val WEB_CAM = "webcam"
    const val MIC = "mic"
    const val SCREEN = "screen"
}

/**
 * WebRTC 配置常量
 */
object WebRTCConfig {
    const val VIDEO_WIDTH = 320
    const val VIDEO_HEIGHT = 240
    const val VIDEO_FPS = 15
}

/**
 * 屏幕捕获 配置常量
 */
object ScreenCaptureConfig {
    const val VIDEO_WIDTH = 1280
    const val VIDEO_HEIGHT = 720
    const val VIDEO_FPS = 15
}
