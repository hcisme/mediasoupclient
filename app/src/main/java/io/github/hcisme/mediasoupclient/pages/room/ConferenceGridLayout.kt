package io.github.hcisme.mediasoupclient.pages.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.hcisme.mediasoupclient.components.VideoTile
import io.github.hcisme.mediasoupclient.model.RemoteStreamState
import org.webrtc.VideoTrack

@Composable
fun ConferenceGridLayout(
    localTrack: VideoTrack?,
    isLocalCameraOff: Boolean,
    isLocalMicMuted: Boolean,
    isFrontCamera: Boolean = false,
    remoteVideoTracksMap: Map<String, VideoTrack>,
    remoteStates: Map<String, RemoteStreamState>,
    onSwitchCamera: () -> Unit
) {
    LazyVerticalGrid(
        // 如果只有2-4人，用2列；人更多可以动态调整，这里演示固定2列
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 本地用户
        item {
            GridVideoItem(
                modifier = Modifier.aspectRatio(1f),
                track = localTrack,
                isCameraOff = isLocalCameraOff,
                isMicMuted = isLocalMicMuted,
                isLocal = true,
                isFrontCamera = isFrontCamera,
                networkScore = 10,
                label = "Me",
                onDoubleClick = onSwitchCamera
            )
        }

        // 远端用户
        items(remoteVideoTracksMap.entries.toList()) { (producerId, track) ->
            val (name, isMicMuted, isCameraOff, score) = parseRemoteState(
                producerId,
                remoteStates
            )

            GridVideoItem(
                modifier = Modifier.aspectRatio(1f),
                track = track,
                isCameraOff = isCameraOff,
                isMicMuted = isMicMuted,
                isLocal = false,
                networkScore = score,
                label = name
            )
        }
    }
}

@Composable
fun GridVideoItem(
    modifier: Modifier,
    track: VideoTrack?,
    isCameraOff: Boolean,
    isMicMuted: Boolean,
    isLocal: Boolean,
    isFrontCamera: Boolean = false,
    networkScore: Int = 0,
    label: String,
    onDoubleClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        VideoTile(
            modifier = Modifier.fillMaxSize(),
            videoTrack = track,
            isCameraOff = isCameraOff,
            isMicMuted = isMicMuted,
            networkScore = networkScore,
            label = label,
            isLocal = isLocal,
            isFrontCamera = isFrontCamera,
            isOverlay = true,
            onDoubleClick = onDoubleClick
        )
    }
}