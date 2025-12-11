package io.github.hcisme.mediasoupclient.pages.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.hcisme.mediasoupclient.components.VideoTile
import io.github.hcisme.mediasoupclient.model.RemoteStreamState
import org.webrtc.VideoTrack

@Composable
fun OneOnOneLayout(
    localTrack: VideoTrack?,
    isLocalCameraOff: Boolean,
    isLocalMicMuted: Boolean,
    isFrontCamera: Boolean = false,
    remoteVideoTracksMap: Map<String, VideoTrack>,
    remoteStates: Map<String, RemoteStreamState>,
    onSwitchCamera: () -> Unit
) {
    val remoteEntry = remoteVideoTracksMap.entries.first()
    val remoteProducerId = remoteEntry.key
    val remoteTrack = remoteEntry.value

    // 解析远端状态
    val (remoteName, isRemoteMicMuted, isRemoteCameraOff, score) = parseRemoteState(
        remoteProducerId,
        remoteStates
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 底层：远端视频
        VideoTile(
            modifier = Modifier.fillMaxSize(),
            videoTrack = remoteTrack,
            isCameraOff = isRemoteCameraOff,
            isMicMuted = isRemoteMicMuted,
            networkScore = score,
            label = remoteName,
            isLocal = false,
            isOverlay = false
        )

        // 顶层：本地视频 (悬浮小窗)
        // 可以添加 draggble 修饰符使其可拖动，这里先固定右上角
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .width(100.dp)
                .aspectRatio(0.6f)
                .clip(MaterialTheme.shapes.medium),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            VideoTile(
                modifier = Modifier.fillMaxSize(),
                videoTrack = localTrack,
                isCameraOff = isLocalCameraOff,
                isMicMuted = isLocalMicMuted,
                networkScore = 10,
                label = "Me",
                isOverlay = true,
                isLocal = true,
                isFrontCamera = isFrontCamera,
                onDoubleClick = onSwitchCamera
            )
        }
    }
}
