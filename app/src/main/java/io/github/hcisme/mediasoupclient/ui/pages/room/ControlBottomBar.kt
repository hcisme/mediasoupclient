package io.github.hcisme.mediasoupclient.ui.pages.room

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import io.github.hcisme.mediasoupclient.R
import io.github.hcisme.mediasoupclient.components.Dialog
import io.github.hcisme.mediasoupclient.utils.PermissionPreferenceManager
import io.github.hcisme.mediasoupclient.utils.startSettingActivity

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ControlBottomBar(
    modifier: Modifier = Modifier,
    initOpenCamera: Boolean,
    initOpenMic: Boolean,
    isMicMuted: Boolean,
    isCameraOff: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onHangUp: () -> Unit
) {
    val context = LocalContext.current
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val roomVM = viewModel<RoomViewModel>()

    // 麦克风
    val onChangeMicrophoneStatus = {
        if (isMicMuted) {
            if (audioPermissionState.status.isGranted) {
                onToggleMic()
            } else {
                roomVM.audioDialogVisible = true
            }
        } else {
            onToggleMic()
        }
    }

    // 视频
    val onChangeVideoStatus = {
        if (isCameraOff) {
            if (cameraPermissionState.status.isGranted) {
                onToggleCamera()
            } else {
                roomVM.videoDialogVisible = true
            }
        } else {
            onToggleCamera()
        }
    }

    LaunchedEffect(Unit) {
        if (initOpenCamera && !cameraPermissionState.status.isGranted) {
            roomVM.videoDialogVisible = true
        }
        if (initOpenMic && !audioPermissionState.status.isGranted) {
            roomVM.audioDialogVisible = true
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(
            space = 16.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 麦克风按钮
        ControlButton(
            icon = if (isMicMuted) R.drawable.mic_off else R.drawable.mic_on,
            isActive = !isMicMuted,
            onClick = onChangeMicrophoneStatus
        )

        // 摄像头按钮
        ControlButton(
            icon = if (isCameraOff) R.drawable.cam_off else R.drawable.cam_on,
            isActive = !isCameraOff,
            onClick = onChangeVideoStatus
        )

        // 切换摄像头前后的按钮
        IconButton(
            onClick = onFlipCamera,
            modifier = Modifier
                .size(56.dp)
                .background(Color.White, CircleShape)
        ) {
            Icon(
                painter = painterResource(R.drawable.flip_camera),
                contentDescription = "toggle_camera",
                tint = Color.Black
            )
        }

        // 挂断按钮
        IconButton(
            onClick = onHangUp,
            modifier = Modifier
                .size(56.dp)
                .background(Color.Red, CircleShape)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Hangup", tint = Color.White)
        }
    }

    Dialog(
        visible = roomVM.audioDialogVisible,
        confirmButtonText = "允许",
        cancelButtonText = "取消",
        onConfirm = {
            PermissionPreferenceManager.managePermissionRequestFlow(
                context = context,
                permissionType = audioPermissionState.permission,
                onFirstRequest = { audioPermissionState.launchPermissionRequest() }
            ) {
                if (audioPermissionState.status.shouldShowRationale) {
                    audioPermissionState.launchPermissionRequest()
                } else {
                    context.startSettingActivity(tooltip = "前往设置 手动开启该应用麦克风权限")
                }
            }
            roomVM.audioDialogVisible = false
        },
        onDismissRequest = { roomVM.audioDialogVisible = false }
    ) {
        Text(text = "需要访问麦克风，才能让其他参会者听到您的声音")
    }

    Dialog(
        visible = roomVM.videoDialogVisible,
        confirmButtonText = "允许",
        cancelButtonText = "取消",
        onConfirm = {
            PermissionPreferenceManager.managePermissionRequestFlow(
                context = context,
                permissionType = cameraPermissionState.permission,
                onFirstRequest = { cameraPermissionState.launchPermissionRequest() }
            ) {
                if (cameraPermissionState.status.shouldShowRationale) {
                    cameraPermissionState.launchPermissionRequest()
                } else {
                    context.startSettingActivity(tooltip = "前往设置 手动开启该应用摄像头权限")
                }
            }
            roomVM.videoDialogVisible = false
        },
        onDismissRequest = {
            roomVM.videoDialogVisible = false
        }
    ) {
        Text(text = "开启摄像头权限，让参会成员看到您的实时画面")
    }
}

@Composable
private fun ControlButton(
    icon: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isActive) Color.White else Color.Gray
    val iconColor = if (isActive) Color.Black else Color.White

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(backgroundColor, CircleShape)
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = iconColor)
    }
}