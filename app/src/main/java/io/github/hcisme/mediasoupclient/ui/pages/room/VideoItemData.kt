package io.github.hcisme.mediasoupclient.ui.pages.room

import org.webrtc.VideoTrack

/**
 * UI层使用的统一数据模型
 */
data class VideoItemData(
    /**
     * "Me" 或 socketId
     */
    val id: String,
    val isLocal: Boolean,
    val track: VideoTrack?,
    val isOpenCamera: Boolean,
    val isOpenMic: Boolean,
    val isFrontCamera: Boolean = false,
    val volume: Int = 0,
    val label: String,
    val isScreenContent: Boolean = false
)