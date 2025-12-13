package io.github.hcisme.mediasoupclient.pages.room

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import io.github.hcisme.mediasoupclient.components.Dialog
import io.github.hcisme.mediasoupclient.components.VideoTile
import io.github.hcisme.mediasoupclient.utils.LocalNavController
import io.github.hcisme.mediasoupclient.utils.LocalRoomClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomPage(
    roomId: String,
    isOpenCamera: Boolean,
    isOpenMic: Boolean
) {
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

    LaunchedEffect(Unit) {
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
        roomClient.startLocalMedia(isOpenCamera = isOpenCamera, isOpenMic = isOpenMic)
    }

    BackHandler { backDialogVisible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        TopAppBar(title = { roomId?.let { Text(text = it) } })

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
                        isFrontCamera = localState.isFrontCamera
                    )
                }

                1 -> {
                    OneOnOneLayout(
                        localTrack = localState.videoTrack,
                        isLocalCameraOff = localState.isCameraOff,
                        isLocalMicMuted = localState.isMicMuted,
                        isFrontCamera = localState.isFrontCamera,
                        remoteVideoTracksMap = remoteVideoTracksMap,
                        remoteStates = remoteStates
                    )
                }

                else -> {
                    ConferenceGridLayout(
                        localTrack = localState.videoTrack,
                        isLocalCameraOff = localState.isCameraOff,
                        isLocalMicMuted = localState.isMicMuted,
                        isFrontCamera = localState.isFrontCamera,
                        remoteVideoTracksMap = remoteVideoTracksMap,
                        remoteStates = remoteStates
                    )
                }
            }

            ControlBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 16.dp),
                isMicMuted = localState.isMicMuted,
                isCameraOff = localState.isCameraOff,
                onToggleMic = { roomClient.toggleMic() },
                onToggleCamera = { roomClient.toggleCamera() },
                onSwitchCamera = { roomClient.videoController.switchCamera() },
                onHangUp = {
                    backDialogVisible = true
                }
            )
        }
    }

    Dialog(
        visible = backDialogVisible,
        confirmButtonText = "确定",
        cancelButtonText = "取消",
        onConfirm = {
            backDialogVisible = false
            navHostController.popBackStack()
        },
        onDismissRequest = { backDialogVisible = false }
    ) {
        Text("确认退出房间吗")
    }
}
