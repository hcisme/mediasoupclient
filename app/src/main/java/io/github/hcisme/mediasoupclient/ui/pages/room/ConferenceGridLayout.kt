package io.github.hcisme.mediasoupclient.ui.pages.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hcisme.mediasoupclient.components.VideoTile
import io.github.hcisme.mediasoupclient.utils.LocalRoomClient
import io.github.hcisme.mediasoupclient.utils.noRippleClickable
import org.webrtc.VideoTrack


@Composable
fun ConferenceGridLayout() {
    val roomClient = LocalRoomClient.current
    val localState by roomClient.localState.collectAsState()
    val remotePeersMap by roomClient.remotePeers.collectAsState()
    val remoteStates by roomClient.remoteStreamStates.collectAsState()
    val roomVM = viewModel<RoomViewModel>()
    val allPeers = remember(localState, remotePeersMap, remoteStates) {
        mutableListOf<VideoItemData>().apply {
            // 自己
            val isLocalScreenSharing = localState.isOpenScreenShare
            val localScreenTrack = if (isLocalScreenSharing) localState.screenTrack else null
            val showLocalScreen = localScreenTrack != null
            add(
                VideoItemData(
                    id = "me",
                    isLocal = true,
                    track = if (showLocalScreen) localScreenTrack else localState.videoTrack,
                    isOpenCamera = if (showLocalScreen) true else localState.isOpenCamera,
                    isOpenMic = localState.isOpenMic,
                    isFrontCamera = if (showLocalScreen) false else localState.isFrontCamera,
                    label = "Me",
                    volume = localState.volume ?: 0,
                    isScreenContent = showLocalScreen
                )
            )

            // 远端
            val remoteVideoDataItems = remotePeersMap.map { (socketId, peer) ->
                val camState = peer.videoProducerId?.let { remoteStates[it] }
                val screenState = peer.screenProducerId?.let { remoteStates[it] }
                val audioState = peer.audioProducerId?.let { remoteStates[it] }

                val hasRemoteScreen = screenState?.screenTrack != null
                val trackToShow =
                    if (hasRemoteScreen) screenState.screenTrack else camState?.videoTrack
                // 是否展示视频
                val isDisplayVideo = if (hasRemoteScreen) {
                    !screenState.isPaused
                } else {
                    // 有视频生产id 且 摄像头状态不为 null 且 isPaused 为 false (未暂停)
                    peer.videoProducerId != null && camState?.isPaused == false
                }

                VideoItemData(
                    id = socketId,
                    isLocal = false,
                    track = trackToShow,
                    isOpenCamera = isDisplayVideo,
                    isOpenMic = peer.audioProducerId != null && audioState?.isPaused == false,
                    volume = audioState?.volume ?: 0,
                    label = socketId.takeLast(4),
                    isScreenContent = hasRemoteScreen
                )
            }
            addAll(remoteVideoDataItems)
        }
    }

    LaunchedEffect(allPeers.size) {
        roomVM.focusedId = allPeers.first().id
    }

    Box(modifier = Modifier.fillMaxSize()) {
        roomVM.focusedId?.let { focusedId ->
            FocusLayout(
                allPeers = allPeers,
                focusedId = focusedId,
                onItemClick = { clickedId -> roomVM.focusedId = clickedId }
            )
        }
    }
}

@Composable
private fun FocusLayout(
    allPeers: List<VideoItemData>,
    focusedId: String,
    onItemClick: (id: String) -> Unit
) {
    val focusedPeer = allPeers.find { it.id == focusedId } ?: allPeers.firstOrNull()
    val otherPeers = allPeers.filter { it.id != focusedId }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (focusedPeer != null) {
                GridVideoItem(
                    modifier = Modifier.fillMaxSize(),
                    track = focusedPeer.track,
                    isOpenCamera = focusedPeer.isOpenCamera,
                    isOpenMic = focusedPeer.isOpenMic,
                    isLocal = focusedPeer.isLocal,
                    isFrontCamera = focusedPeer.isFrontCamera,
                    volume = focusedPeer.volume,
                    label = focusedPeer.label,
                    isScreenContent = focusedPeer.isScreenContent
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(otherPeers, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .noRippleClickable { onItemClick(item.id) }
                ) {
                    GridVideoItem(
                        modifier = Modifier.fillMaxSize(),
                        track = item.track,
                        isOpenCamera = item.isOpenCamera,
                        isOpenMic = item.isOpenMic,
                        isLocal = item.isLocal,
                        isFrontCamera = item.isFrontCamera,
                        volume = item.volume,
                        label = item.label,
                        isScreenContent = item.isScreenContent
                    )
                }
            }
        }
    }
}

@Composable
private fun GridVideoItem(
    modifier: Modifier,
    track: VideoTrack?,
    isOpenCamera: Boolean,
    isOpenMic: Boolean,
    isLocal: Boolean,
    isFrontCamera: Boolean = false,
    volume: Int,
    label: String,
    isScreenContent: Boolean = false
) {
    VideoTile(
        modifier = modifier.fillMaxSize(),
        videoTrack = track,
        isOpenCamera = isOpenCamera,
        isOpenMic = isOpenMic,
        volume = volume,
        label = label,
        isLocal = isLocal,
        isFrontCamera = isFrontCamera,
        isOverlay = true,
        isScreenContent = isScreenContent
    )
}