package io.github.hcisme.mediasoupclient.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.text.isNotEmpty

data class NotificationState(
    val message: String = "",
    val isVisible: Boolean = false
)

object NotificationManager {
    val contentWidth = 160.dp
    val yOffset = 100.dp
    var notificationState by mutableStateOf(NotificationState())

    fun show(message: String) {
        notificationState = NotificationState(
            message = message,
            isVisible = true
        )
    }

    fun hide() {
        notificationState = notificationState.copy(
            isVisible = false
        )
    }
}

@Composable
fun NotificationPopup(
    notificationState: NotificationState,
    contentWidth: Dp = NotificationManager.contentWidth,
    yOffset: Dp = NotificationManager.yOffset,
    animateDt: Int = 300,
    onDismiss: () -> Unit = {}
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val windowWidthPx = windowInfo.containerSize.width
    val scope = rememberCoroutineScope()
    val contentPx = remember(contentWidth) { with(density) { contentWidth.toPx() } }
    val yOffsetPx = remember(yOffset) { with(density) { 100.dp.toPx() } }
    val xOffsetPx = remember(windowWidthPx) { (windowWidthPx / 2) - (contentPx / 2) }

    // 动画状态
    var innerIsVisible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (innerIsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = animateDt),
        label = "notification_alpha"
    )
    val translateY by animateFloatAsState(
        targetValue = if (innerIsVisible) 0f else 50f,
        animationSpec = tween(durationMillis = animateDt),
        label = "notification_translate"
    )

    val hide: () -> Unit = {
        scope.launch {
            innerIsVisible = false
            delay(animateDt.toLong())
            onDismiss()
        }
    }

    EnhancedLifecycleAware(
        onPaused = {
            onDismiss()
        }
    )

    LaunchedEffect(notificationState.isVisible) {
        if (notificationState.isVisible) {
            innerIsVisible = true
            delay(1400)
            innerIsVisible = false
            delay(animateDt.toLong())
            onDismiss()
        }
    }

    if (notificationState.message.isNotEmpty() && notificationState.isVisible) {
        Popup(
            offset = IntOffset(x = xOffsetPx.toInt(), y = yOffsetPx.toInt()),
            onDismissRequest = hide
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha
                        translationY = translateY
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(with(density) { contentPx.toDp() })
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f * alpha))
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = notificationState.message,
                        color = MaterialTheme.colorScheme.background,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
