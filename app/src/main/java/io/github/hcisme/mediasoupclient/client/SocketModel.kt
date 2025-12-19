package io.github.hcisme.mediasoupclient.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.webrtc.VideoTrack

/**
 * Socket.IO 返回的 Transport 信息
 */
@Serializable
data class TransportInfo(
    val transportId: String,
    val iceParameters: JsonObject,
    val iceCandidates: JsonElement, // 可能是 Array
    val dtlsParameters: JsonObject
)

/**
 * Produce 返回
 */
@Serializable
data class ProduceResponse(val id: String)

/**
 * Consume 返回
 */
@Serializable
data class ConsumeResponse(
    val id: String,
    val producerId: String,
    val kind: String,
    val rtpParameters: JsonObject,
    val socketId: String
)

@Serializable
data class ExistingProducer(
    val producerId: String,
    val kind: String,
    val paused: Boolean,
    val socketId: String,
    val appData: AppData
)

/**
 * 通用 Join 返回
 */
@Serializable
data class JoinResponse(
    val rtpCapabilities: JsonObject,
    val existingProducers: List<ExistingProducer> = emptyList(),
    /**
     * socketId
     */
    val existingPeers: List<String> = emptyList()
)

/**
 * 远端流的附加状态 (是否静音、网络评分)
 */
data class RemoteStreamState(
    val producerId: String,
    val socketId: String,
    val kind: String,
    val videoTrack: VideoTrack? = null,
    val screenTrack: VideoTrack? = null,
    val isPaused: Boolean,
    val volume: Int? = null,
    val isScreenShare: Boolean
)

@Serializable
data class NewProducerResponse(
    val producerId: String,
    val socketId: String,
    val kind: String,
    val paused: Boolean,
    val appData: AppData
)

@Serializable
data class AppData(val source: String)

@Serializable
data class RemotePauseResumeDataResponse(
    val producerId: String,
    val kind: String,
    val socketId: String
)

@Serializable
data class AudioLevelData(
    val audioProducerId: String,
    /**
     * -127 ~ 0
     */
    val volume: Int
)
