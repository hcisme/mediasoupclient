package io.github.hcisme.mediasoupclient.pages.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.hcisme.mediasoupclient.R

@Composable
fun ControlBottomBar(
    modifier: Modifier = Modifier,
    isMicMuted: Boolean,
    isCameraOff: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onHangUp: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(
            space = 24.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 麦克风按钮
        ControlButton(
            icon = if (isMicMuted) R.drawable.mic_off else R.drawable.mic_on,
            isActive = !isMicMuted,
            onClick = onToggleMic
        )

        // 挂断按钮
        IconButton(
            onClick = onHangUp,
            modifier = Modifier
                .size(56.dp)
                .background(Color.Red, CircleShape)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Hangup", tint = Color.White)
        }

        // 摄像头按钮
        ControlButton(
            icon = if (isCameraOff) R.drawable.camera_off else R.drawable.camera_on,
            isActive = !isCameraOff,
            onClick = onToggleCamera
        )
    }
}

@Composable
fun ControlButton(
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