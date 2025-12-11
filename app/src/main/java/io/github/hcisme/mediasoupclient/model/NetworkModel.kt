package io.github.hcisme.mediasoupclient.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Socket.IO 返回的 Transport 信息
 */
@Serializable
data class TransportInfo(
    val id: String,
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
    val rtpParameters: JsonObject
)

@Serializable
data class ExistingProducer(
    val producerId: String,
    val kind: String? = null,
    val paused: Boolean? = null,
    val socketId: String,
)

/**
 * 通用 Join 返回
 */
@Serializable
data class JoinResponse(
    val rtpCapabilities: JsonObject,
    val existingProducers: List<ExistingProducer> = emptyList()
)

/**
 * 远端流的附加状态 (是否静音、网络评分)
 */
data class RemoteStreamState(
    val producerId: String,
    val kind: String,
    val isPaused: Boolean,
    val score: Int = 10,
    val socketId: String
)

@Serializable
data class NewProducerResponse(
    val producerId: String,
    val socketId: String,
    val kind: String,
    val paused: Boolean,
)
