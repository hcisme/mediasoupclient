package io.github.hcisme.mediasoupclient.ui.pages.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hcisme.mediasoupclient.components.VideoTile
import io.github.hcisme.mediasoupclient.utils.LocalRoomClient
import org.webrtc.VideoTrack

@Composable
fun ConferenceGridLayout() {
    val roomClient = LocalRoomClient.current
    val roomVM = viewModel<RoomViewModel>()
    // 本地
    val localState by roomClient.localState.collectAsState()
    // 远程
    val remotePeersMap by roomClient.remotePeers.collectAsState()
    val remoteStates by roomClient.remoteStreamStates.collectAsState()

    LazyVerticalGrid(
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
                track = localState.videoTrack,
                isCameraOff = localState.isCameraOff,
                isMicMuted = localState.isMicMuted,
                isLocal = true,
                isFrontCamera = localState.isFrontCamera,
                networkScore = 10,
                label = "Me"
            )
        }

        // 远端用户
        items(remotePeersMap.entries.toList()) { (socketId, remotePeer) ->
            val videoId = remotePeer.videoProducerId
            val audioId = remotePeer.audioProducerId
            val videoState = if (videoId != null) remoteStates[videoId] else null
            val audioState = if (audioId != null) remoteStates[audioId] else null

            val isCameraOff = videoId == null || (videoState?.isPaused == true)
            val isMicMuted = audioId == null || (audioState?.isPaused == true)

            val score = videoState?.score ?: 10

            GridVideoItem(
                modifier = Modifier.aspectRatio(1f),
                track = videoState?.videoTrack,
                isCameraOff = isCameraOff,
                isMicMuted = isMicMuted,
                isLocal = false,
                networkScore = score,
                label = "User ${socketId.takeLast(4)}"
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
    label: String
) {
    VideoTile(
        modifier = modifier.fillMaxSize(),
        videoTrack = track,
        isCameraOff = isCameraOff,
        isMicMuted = isMicMuted,
        networkScore = networkScore,
        label = label,
        isLocal = isLocal,
        isFrontCamera = isFrontCamera,
        isOverlay = true
    )
}