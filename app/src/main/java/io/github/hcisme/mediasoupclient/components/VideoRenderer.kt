package io.github.hcisme.mediasoupclient.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun VideoRenderer(
    modifier: Modifier = Modifier,
    track: VideoTrack?,
    isLocal: Boolean,
    isFrontCamera: Boolean,
    eglContext: EglBase.Context,
    isOverlay: Boolean = false,
    isScreenContent: Boolean = false
) {
    key(track?.id()) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(eglContext, null)
                    setScalingType(
                        if (isScreenContent) RendererCommon.ScalingType.SCALE_ASPECT_FIT
                        else RendererCommon.ScalingType.SCALE_ASPECT_FILL
                    )
                    setEnableHardwareScaler(true)
                    setZOrderMediaOverlay(isOverlay)
                }
            },
            update = { view ->
                try {
                    // 先清空当前的 sink (防止重复添加)
                    track?.removeSink(view)

                    if (track != null) {
                        track.addSink(view)
                        val shouldMirror = isLocal && isFrontCamera
                        view.setMirror(shouldMirror)
                    } else {
                        view.clearImage()
                    }
                } catch (_: Exception) {
                }
            },
            onRelease = { view ->
                try {
                    view.visibility = View.GONE
                    track?.removeSink(view)
                    view.release()
                } catch (_: Exception) {
                }
            }
        )
    }
}
