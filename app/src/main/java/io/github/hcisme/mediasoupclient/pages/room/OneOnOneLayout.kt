package io.github.hcisme.mediasoupclient.pages.room

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.hcisme.mediasoupclient.components.VideoTile
import io.github.hcisme.mediasoupclient.model.RemoteStreamState
import io.github.hcisme.mediasoupclient.utils.parseRemoteState
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

@Composable
fun OneOnOneLayout(
    localTrack: VideoTrack?,
    isLocalCameraOff: Boolean,
    isLocalMicMuted: Boolean,
    isFrontCamera: Boolean = false,
    remoteVideoTracksMap: Map<String, VideoTrack>,
    remoteStates: Map<String, RemoteStreamState>
) {
    val density = LocalDensity.current
    // false: 远端是大屏，本地是小窗
    var isLocalMain by remember { mutableStateOf(false) }
    val remoteEntry = remoteVideoTracksMap.entries.first()
    val remoteProducerId = remoteEntry.key
    val remoteTrack = remoteEntry.value

    // 解析远端状态
    val (remoteName, isRemoteMicMuted, isRemoteCameraOff, score) = parseRemoteState(
        remoteProducerId,
        remoteStates
    )

    // 定义小窗尺寸
    val smallWidth = remember { 100.dp }
    val smallHeight = remember { 167.dp }
    val padding = remember { 40.dp }
    val smallWidthPx = remember(key1 = density) { with(receiver = density) { smallWidth.toPx() } }
    val smallHeightPx = remember(key1 = density) { with(receiver = density) { smallHeight.toPx() } }
    val paddingPx = remember(key1 = density) { with(receiver = density) { padding.toPx() } }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        // 初始化位置：右上角 (屏幕宽 - 小窗宽 - padding)
        var offsetX by remember { mutableFloatStateOf(maxWidthPx - smallWidthPx) }
        var offsetY by remember { mutableFloatStateOf(paddingPx) }

        VideoTile(
            modifier = Modifier.fillMaxSize(),
            videoTrack = if (isLocalMain) localTrack else remoteTrack,
            isCameraOff = if (isLocalMain) isLocalCameraOff else isRemoteCameraOff,
            isMicMuted = if (isLocalMain) isLocalMicMuted else isRemoteMicMuted,
            networkScore = if (isLocalMain) 10 else score,
            label = if (isLocalMain) "Me" else remoteName,
            isLocal = isLocalMain,
            isFrontCamera = if (isLocalMain) isFrontCamera else false,
            isOverlay = false
        )

        VideoTile(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(smallWidth)
                .aspectRatio(0.6f)
                .zIndex(1f)
                .pointerInput(Unit) {
                    detectTapGestures { isLocalMain = !isLocalMain }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxWidthPx - smallWidthPx)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxHeightPx - smallHeightPx)
                    }
                },
            videoTrack = if (isLocalMain) remoteTrack else localTrack,
            isCameraOff = if (isLocalMain) isRemoteCameraOff else isLocalCameraOff,
            isMicMuted = if (isLocalMain) isRemoteMicMuted else isLocalMicMuted,
            networkScore = 10,
            label = "",
            isOverlay = true,
            isLocal = !isLocalMain,
            isFrontCamera = if (isLocalMain) false else isFrontCamera
        )
    }
}
