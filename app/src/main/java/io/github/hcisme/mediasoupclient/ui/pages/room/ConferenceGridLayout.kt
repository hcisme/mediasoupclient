package io.github.hcisme.mediasoupclient.ui.pages.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
            add(
                VideoItemData(
                    id = "me",
                    isLocal = true,
                    track = localState.videoTrack,
                    isCameraOff = localState.isCameraOff,
                    isMicMuted = localState.isMicMuted,
                    isFrontCamera = localState.isFrontCamera,
                    label = "Me",
                    volume = localState.volume ?: 0
                )
            )
            // 远端
            val remoteVideoDataItems = remotePeersMap.map { (socketId, peer) ->
                val videoState = peer.videoProducerId?.let { remoteStates[it] }
                val audioState = peer.audioProducerId?.let { remoteStates[it] }
                VideoItemData(
                    id = socketId,
                    isLocal = false,
                    track = videoState?.videoTrack,
                    isCameraOff = peer.videoProducerId == null || videoState?.isPaused == true,
                    isMicMuted = peer.audioProducerId == null || audioState?.isPaused == true,
                    score = videoState?.score ?: 10,
                    volume = audioState?.volume ?: 0,
                    label = socketId.takeLast(4)
                )
            }
            addAll(remoteVideoDataItems)
        }
    }

    LaunchedEffect(allPeers.size) {
        if (allPeers.size > 2 && roomVM.focusedId == null) {
            roomVM.focusedId = allPeers.first().id
        } else if (allPeers.size <= 2) {
            roomVM.focusedId = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (roomVM.focusedId != null && allPeers.size > 2) {
            FocusLayout(
                allPeers = allPeers,
                focusedId = roomVM.focusedId!!,
                onItemClick = { clickedId -> roomVM.focusedId = clickedId }
            )
        } else {
            GridLayout(
                allPeers = allPeers,
                onItemClick = { clickedId ->
                    if (allPeers.size > 2) {
                        roomVM.focusedId = clickedId
                    }
                }
            )
        }
    }
}

@Composable
fun GridLayout(
    allPeers: List<VideoItemData>,
    onItemClick: (id: String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(allPeers, key = { it.id }) { item ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .noRippleClickable { onItemClick(item.id) }
            ) {
                GridVideoItem(
                    modifier = Modifier.fillMaxSize(),
                    track = item.track,
                    isCameraOff = item.isCameraOff,
                    isMicMuted = item.isMicMuted,
                    isLocal = item.isLocal,
                    isFrontCamera = item.isFrontCamera,
                    networkScore = item.score,
                    volume = item.volume,
                    label = item.label
                )
            }
        }
    }
}

@Composable
fun FocusLayout(
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
                    isCameraOff = focusedPeer.isCameraOff,
                    isMicMuted = focusedPeer.isMicMuted,
                    isLocal = focusedPeer.isLocal,
                    isFrontCamera = focusedPeer.isFrontCamera,
                    networkScore = focusedPeer.score,
                    volume = focusedPeer.volume,
                    label = focusedPeer.label
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
                        isCameraOff = item.isCameraOff,
                        isMicMuted = item.isMicMuted,
                        isLocal = item.isLocal,
                        isFrontCamera = item.isFrontCamera,
                        networkScore = item.score,
                        volume = item.volume,
                        label = item.label // 小窗也可以显示名字
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
    isCameraOff: Boolean,
    isMicMuted: Boolean,
    isLocal: Boolean,
    isFrontCamera: Boolean = false,
    networkScore: Int = 0,
    volume: Int,
    label: String
) {
    VideoTile(
        modifier = modifier.fillMaxSize(),
        videoTrack = track,
        isCameraOff = isCameraOff,
        isMicMuted = isMicMuted,
        networkScore = networkScore,
        volume = volume,
        label = label,
        isLocal = isLocal,
        isFrontCamera = isFrontCamera,
        isOverlay = true
    )
}