package io.github.hcisme.mediasoupclient.ui.pages.room

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hcisme.mediasoupclient.components.Dialog
import io.github.hcisme.mediasoupclient.components.VideoTile
import io.github.hcisme.mediasoupclient.utils.LocalNavController
import io.github.hcisme.mediasoupclient.utils.LocalRoomClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomPage(
    roomId: String,
    initOpenCamera: Boolean,
    initOpenMic: Boolean
) {
    val view = LocalView.current
    val roomClient = LocalRoomClient.current
    val navHostController = LocalNavController.current
    val roomVM = viewModel<RoomViewModel>()
    val insetsController = remember(view) {
        if (!view.isInEditMode) {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
        } else {
            null
        }
    }

    val roomId by roomClient.currentRoomId.collectAsState()
    val localState by roomClient.localState.collectAsState()

    // 远程
    val remotePeersMap by roomClient.remotePeers.collectAsState()

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
        roomClient.startLocalMedia(isOpenCamera = initOpenCamera, isOpenMic = initOpenMic)
    }

    BackHandler { roomVM.backDialogVisible = true }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TopAppBar(title = { roomId?.let { Text(text = it) } })

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (remotePeersMap.size) {
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

                else -> ConferenceGridLayout()
            }

            ControlBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 16.dp),
                initOpenMic = initOpenMic,
                initOpenCamera = initOpenCamera,
                isMicMuted = localState.isMicMuted,
                isCameraOff = localState.isCameraOff,
                onToggleMic = { roomClient.toggleMic() },
                onToggleCamera = { roomClient.toggleCamera() },
                onFlipCamera = { roomClient.videoController.flipCamera() },
                onHangUp = {
                    roomVM.backDialogVisible = true
                }
            )
        }
    }

    Dialog(
        visible = roomVM.backDialogVisible,
        confirmButtonText = "确定",
        cancelButtonText = "取消",
        onConfirm = {
            roomVM.backDialogVisible = false
            navHostController.popBackStack()
        },
        onDismissRequest = { roomVM.backDialogVisible = false }
    ) {
        Text("确认退出房间吗")
    }
}
