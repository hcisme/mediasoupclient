package io.github.hcisme.mediasoupclient.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hcisme.mediasoupclient.R
import io.github.hcisme.mediasoupclient.client.RoomClient
import org.webrtc.VideoTrack

@Composable
fun VideoTile(
    modifier: Modifier = Modifier,
    videoTrack: VideoTrack?,
    isCameraOff: Boolean,
    isMicMuted: Boolean,
    isLocal: Boolean,
    isFrontCamera: Boolean = false,
    networkScore: Int,
    label: String,
    isOverlay: Boolean = false
) {
    Box(modifier = modifier) {
        if (!isCameraOff && videoTrack != null) {
            VideoRenderer(
                track = videoTrack,
                eglContext = RoomClient.eglBaseContext,
                modifier = Modifier.fillMaxSize(),
                isLocal = isLocal,
                isFrontCamera = isFrontCamera,
                isOverlay = isOverlay
            )
        } else {
            UserAvatarPlaceholder(Modifier.fillMaxSize())
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NetworkQualityIcon(score = networkScore)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 名字
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (isMicMuted) {
                Icon(
                    painter = painterResource(R.drawable.mic_off),
                    contentDescription = "Muted",
                    tint = Color.Red,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.mic_on),
                    contentDescription = "Unmuted",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 网络信号图标
 */
@Composable
private fun NetworkQualityIcon(score: Int, modifier: Modifier = Modifier) {
    // Mediasoup score 范围 0-10
    // 9-10: 优 (绿), 7-8: 良 (黄), 4-6: 中 (橙), <4: 差 (红)
    val (color, icon) = when {
        score >= 9 -> Color.Green to R.drawable.signal4
        score >= 7 -> Color.Yellow to R.drawable.signal3
        score >= 4 -> Color(0xFFFFA500) to R.drawable.signal2
        else -> Color.Red to R.drawable.signal1
    }

    Icon(
        painter = painterResource(icon),
        tint = color,
        contentDescription = "Network Signal $score",
        modifier = modifier.size(20.dp)
    )
}

/**
 * 摄像头关闭时的占位图
 */
@Composable
private fun UserAvatarPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = "No Video",
            tint = Color.LightGray,
            modifier = Modifier.fillMaxSize(0.4f)
        )
    }
}
