package io.github.hcisme.mediasoupclient.components

import androidx.compose.runtime.Composable
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
    isOverlay: Boolean = false
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setZOrderMediaOverlay(isOverlay)
            }
        },
        update = { view ->
            if (track != null) {
                track.addSink(view)
            } else {
                view.clearImage()
            }

            // 如果是本地视频 且 使用的是前置摄像头 -> 开启镜像
            // 否则（本地后置、或者远端视频） -> 关闭镜像
            val shouldMirror = isLocal && isFrontCamera
            view.setMirror(shouldMirror)
        },
        onRelease = { view ->
            try {
                track?.removeSink(view)
                view.release()
            } catch (_: Exception) {
            }
        }
    )
}
