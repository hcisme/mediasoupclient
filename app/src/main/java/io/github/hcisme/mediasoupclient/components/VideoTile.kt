package io.github.hcisme.mediasoupclient.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
    isOpenCamera: Boolean,
    isOpenMic: Boolean,
    isLocal: Boolean,
    label: String,
    isFrontCamera: Boolean = false,
    volume: Int = 0,
    isOverlay: Boolean = false,
    isScreenContent: Boolean = false
) {
    Box(modifier = modifier) {
        if (isOpenCamera && videoTrack != null) {
            VideoRenderer(
                track = videoTrack,
                eglContext = RoomClient.eglBaseContext,
                modifier = Modifier.fillMaxSize(),
                isLocal = isLocal,
                isFrontCamera = isFrontCamera,
                isOverlay = isOverlay,
                isScreenContent = isScreenContent
            )
        } else {
            UserAvatarPlaceholder(Modifier.fillMaxSize())
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

            if (isOpenMic) {
                VolumeMicIcon(
                    volume = volume,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.mic_off),
                    contentDescription = "Muted",
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
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

/**
 * 音量大小指示器
 */
@Composable
fun VolumeMicIcon(
    volume: Int, // 0 - 10
    modifier: Modifier = Modifier
) {
    val targetFillRatio = (volume / 10f).coerceIn(0f, 1f)
    val animatedRatio by animateFloatAsState(
        targetValue = targetFillRatio,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "volume_anim"
    )

    Icon(
        painter = painterResource(R.drawable.mic_on),
        contentDescription = "Active Mic",
        tint = Color.White,
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()

                if (animatedRatio > 0.05f) {
                    val fillHeight = size.height * animatedRatio

                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(0f, size.height - fillHeight),
                        size = Size(size.width, fillHeight),
                        blendMode = BlendMode.SrcIn
                    )
                }
            }
    )
}