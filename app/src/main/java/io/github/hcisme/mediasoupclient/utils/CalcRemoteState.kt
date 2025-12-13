package io.github.hcisme.mediasoupclient.utils

import io.github.hcisme.mediasoupclient.model.RemoteStreamState

/**
 * 返回结果：Name, isMicMuted, isCameraOff, Score
 */
fun parseRemoteState(
    videoProducerId: String,
    statesMap: Map<String, RemoteStreamState>
): Tuple4<String, Boolean, Boolean, Int> {
    val videoState = statesMap[videoProducerId]
    val socketId = videoState?.socketId
    val isCameraOff = videoState?.isPaused == true
    val videoScore = videoState?.score ?: 0
    val label = "User ${socketId?.takeLast(4) ?: "..."}"

    // 寻找音频状态 (同 socketId 且 kind="audio")
    val audioState = statesMap.values.find {
        it.socketId == socketId && it.kind == "audio"
    }
    val isMicMuted = audioState?.isPaused == true // 暂停即静音

    return Tuple4(label, isMicMuted, isCameraOff, videoScore)
}

data class Tuple4<A, B, C, D>(
    val first: A, val second: B, val third: C, val fourth: D
)
