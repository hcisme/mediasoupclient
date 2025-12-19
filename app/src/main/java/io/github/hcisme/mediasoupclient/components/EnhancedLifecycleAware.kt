package io.github.hcisme.mediasoupclient.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 增强版生命周期监听器，提供更具体的回调
 */
@Composable
fun EnhancedLifecycleAware(
    onCreated: () -> Unit = {},
    onStarted: () -> Unit = {},
    onResumed: () -> Unit = {},
    onPaused: () -> Unit = {},
    onStopped: () -> Unit = {},
    onDestroyed: () -> Unit = {},
    onAnyEvent: (Lifecycle.Event) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            onAnyEvent(event)

            when (event) {
                Lifecycle.Event.ON_CREATE -> onCreated()
                Lifecycle.Event.ON_START -> onStarted()
                Lifecycle.Event.ON_RESUME -> onResumed()
                Lifecycle.Event.ON_PAUSE -> onPaused()
                Lifecycle.Event.ON_STOP -> onStopped()
                Lifecycle.Event.ON_DESTROY -> onDestroyed()
                Lifecycle.Event.ON_ANY -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
