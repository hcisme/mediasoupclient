package io.github.hcisme.mediasoupclient.ui.pages.room

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hcisme.mediasoupclient.components.Dialog
import io.github.hcisme.mediasoupclient.components.KeepScreenOn
import io.github.hcisme.mediasoupclient.components.VideoTile
import io.github.hcisme.mediasoupclient.service.CallServiceManager
import io.github.hcisme.mediasoupclient.utils.LocalNavController
import io.github.hcisme.mediasoupclient.utils.LocalRoomClient

@Composable
fun RoomPage(
    roomId: String,
    initOpenCamera: Boolean,
    initOpenMic: Boolean
) {
    val context = LocalContext.current
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

    // 本地
    val localState by roomClient.localState.collectAsState()
    // 远程
    val remotePeersMap by roomClient.remotePeers.collectAsState()

    DisposableEffect(Unit) {
        insetsController?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        onDispose {
            insetsController?.apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }

            roomClient.audioController.setSpeakerphoneOn(false)
            roomClient.exitRoom()
            CallServiceManager.stop(context = context)
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
    ) {
        RoomTopBar()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (remotePeersMap.size) {
                0 -> {
                    val isLocalScreenSharing = localState.isOpenScreenShare
                    val localScreenTrack =
                        if (isLocalScreenSharing) localState.screenTrack else null
                    val showLocalScreen = localScreenTrack != null

                    VideoTile(
                        modifier = Modifier.fillMaxSize(),
                        videoTrack = if (showLocalScreen) localScreenTrack else localState.videoTrack,
                        isOpenCamera = if (showLocalScreen) true else localState.isOpenCamera,
                        isOpenMic = localState.isOpenMic,
                        volume = localState.volume ?: 0,
                        label = "Me (Waiting...)",
                        isLocal = true,
                        isFrontCamera = if (showLocalScreen) false else localState.isFrontCamera,
                        isScreenContent = showLocalScreen
                    )
                }

                else -> ConferenceGridLayout()
            }
        }

        ControlBottomBar(
            modifier = Modifier.navigationBarsPadding(),
            initOpenMic = initOpenMic,
            initOpenCamera = initOpenCamera,
            isOpenMic = localState.isOpenMic,
            isOpenCamera = localState.isOpenCamera,
            isOpenScreenShare = localState.isOpenScreenShare,
            onToggleMic = { roomClient.toggleMic() },
            onToggleCamera = { roomClient.toggleCamera() },
            onFlipCamera = { roomClient.videoController.flipCamera() },
            onToggleScreenShare = { roomClient.toggleScreenShare(permissionIntent = it) },
            onHangUp = { roomVM.backDialogVisible = true }
        )
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

    KeepScreenOn()
}
