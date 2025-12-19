package io.github.hcisme.mediasoupclient.controller

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import io.github.hcisme.mediasoupclient.client.safeDispose
import io.github.hcisme.mediasoupclient.utils.ScreenCaptureConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class ScreenShareController(
    private val context: Context,
    private val factory: PeerConnectionFactory,
    private val eglContext: EglBase.Context
) {
    companion object {
        private const val TAG = "ScreenShareController"
        private const val THREAD_NAME = "ScreenShareThread"
        private val TRACK_ID = "screen_track_${System.currentTimeMillis()}"
    }

    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    var screenTrack: VideoTrack? = null
        private set
    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    /**
     * 开始屏幕共享
     * @param permissionIntent 用户在系统弹窗授权后返回的 Intent (resultData)
     * @return 创建成功的 VideoTrack，如果失败则为 null
     */
    fun start(permissionIntent: Intent): VideoTrack? {
        if (_isScreenSharing.value) {
            Log.w(TAG, "Screen sharing is already active.")
            return screenTrack
        }

        Log.i(TAG, "Starting screen capture")

        try {
            videoCapturer = ScreenCapturerAndroid(
                permissionIntent,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.w(TAG, "System stopped screen projection.")
                        // 系统层面停止（如下拉菜单关闭），我们也同步停止
                        stop()
                    }
                }
            )
            videoSource = factory.createVideoSource(true)
            surfaceTextureHelper = SurfaceTextureHelper.create(THREAD_NAME, eglContext)
            videoCapturer?.initialize(
                surfaceTextureHelper,
                context,
                videoSource!!.capturerObserver
            )

            // 720p (1280x720)
            videoCapturer?.startCapture(
                ScreenCaptureConfig.VIDEO_WIDTH,
                ScreenCaptureConfig.VIDEO_HEIGHT,
                ScreenCaptureConfig.VIDEO_FPS
            )

            screenTrack = factory.createVideoTrack(TRACK_ID, videoSource)
            screenTrack?.setEnabled(true)
            _isScreenSharing.update { true }

            Log.i(TAG, "Screen capture started successfully. Track ID: $TRACK_ID")
            return screenTrack
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen share", e)
            stop()
            return null
        }
    }

    /**
     * 停止屏幕共享并释放资源
     */
    fun stop() {
        if (!_isScreenSharing.value) return
        Log.i(TAG, "Stopping screen capture...")

        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()

            videoSource?.dispose()

            screenTrack.safeDispose()

            surfaceTextureHelper?.dispose()

            // Track 的 dispose 通常由 PeerConnection 负责，但在销毁 Controller 时也可以手动释放
            // 这里我们置空，RoomClient 里的 Producer.close() 会停止发送
            // _screenTrack?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen share", e)
        } finally {
            videoCapturer = null
            videoSource = null
            screenTrack = null
            surfaceTextureHelper = null
            _isScreenSharing.update { false }
        }
    }

    /**
     * 销毁（退出房间时调用）
     */
    fun dispose() {
        stop()
    }
}