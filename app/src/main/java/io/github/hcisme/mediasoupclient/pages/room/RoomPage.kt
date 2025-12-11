package io.github.hcisme.mediasoupclient.pages.room

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.hcisme.mediasoupclient.components.Dialog
import io.github.hcisme.mediasoupclient.components.VideoTile
import io.github.hcisme.mediasoupclient.model.RemoteStreamState
import io.github.hcisme.mediasoupclient.utils.LocalNavController
import io.github.hcisme.mediasoupclient.utils.LocalRoomClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomPage(roomId: String) {
    val view = LocalView.current
    val insetsController = remember(view) {
        if (!view.isInEditMode) {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
        } else {
            null
        }
    }
    val roomClient = LocalRoomClient.current
    val navHostController = LocalNavController.current
    var backDialogVisible by remember { mutableStateOf(false) }

    val roomId by roomClient.currentRoomId.collectAsState()
    val localState by roomClient.localState.collectAsState()

    // 远程视频轨道 Map <ProducerId, VideoTrack>
    val remoteVideoTracksMap by roomClient.remoteVideoTracks.collectAsState()
    // 远程状态 Map <ProducerId, RemoteStreamState>
    val remoteStates by roomClient.remoteStreamStates.collectAsState()

    SideEffect {
        insetsController?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            insetsController?.apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }

            roomClient.audioController.setSpeakerphoneOn(false)
            roomClient.exitRoom()
        }
    }

    LaunchedEffect(Unit) {
        roomClient.audioController.setSpeakerphoneOn(true)
        roomClient.startLocalMedia()
    }

    BackHandler { backDialogVisible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = { roomId?.let { Text(text = it) } },
            navigationIcon = {
                IconButton(
                    onClick = { backDialogVisible = true }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (remoteVideoTracksMap.size) {
                0 -> {
                    VideoTile(
                        modifier = Modifier.fillMaxSize(),
                        videoTrack = localState.videoTrack,
                        isCameraOff = localState.isCameraOff,
                        isMicMuted = localState.isMicMuted,
                        networkScore = 10,
                        label = "Me (Waiting...)",
                        isLocal = true,
                        isFrontCamera = localState.isFrontCamera,
                        onDoubleClick = { roomClient.videoController.switchCamera() }
                    )
                }

                1 -> {
                    OneOnOneLayout(
                        localTrack = localState.videoTrack,
                        isLocalCameraOff = localState.isCameraOff,
                        isLocalMicMuted = localState.isMicMuted,
                        isFrontCamera = localState.isFrontCamera,
                        remoteVideoTracksMap = remoteVideoTracksMap,
                        remoteStates = remoteStates,
                        onSwitchCamera = { roomClient.videoController.switchCamera() }
                    )
                }

                else -> {
                    ConferenceGridLayout(
                        localTrack = localState.videoTrack,
                        isLocalCameraOff = localState.isCameraOff,
                        isLocalMicMuted = localState.isMicMuted,
                        isFrontCamera = localState.isFrontCamera,
                        remoteVideoTracksMap = remoteVideoTracksMap,
                        remoteStates = remoteStates,
                        onSwitchCamera = { roomClient.videoController.switchCamera() }
                    )
                }
            }
        }

        ControlBottomBar(
            isMicMuted = localState.isMicMuted,
            isCameraOff = localState.isCameraOff,
            onToggleMic = {
                roomClient.toggleMic()
            },
            onToggleCamera = {
                roomClient.toggleCamera()
            },
            onHangUp = {
                backDialogVisible = true
            }
        )
    }

    Dialog(
        visible = backDialogVisible,
        confirmButtonText = "确定",
        cancelButtonText = "取消",
        onConfirm = {
            navHostController.popBackStack()
        },
        onDismissRequest = { backDialogVisible = false }
    ) {
        Text("确认退出房间吗")
    }
}

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
