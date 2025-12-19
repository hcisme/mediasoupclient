package io.github.hcisme.mediasoupclient.controller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.hcisme.mediasoupclient.client.safeDispose
import io.github.hcisme.mediasoupclient.utils.WebRTCConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean

class VideoController(
    private val context: Context,
    private val factory: PeerConnectionFactory,
    private val eglContext: EglBase.Context
) {
    companion object {
        private const val TAG = "VideoController"
        private const val THREAD_NAME = "VideoCaptureThread"
        private val TRACK_ID = "local_video_track${System.currentTimeMillis()}"
    }

    // 内部使用
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    // 当前正在使用的摄像头 ID
    private var currentDeviceName: String? = null
    private val isSwitching = AtomicBoolean(false)

    // 状态流
    private val _localVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrackFlow.asStateFlow()
    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    /**
     * 开启摄像头
     */
    fun start() {
        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "start() failed: No CAMERA permission")
            return
        }
        if (_localVideoTrackFlow.value != null) {
            Log.w(TAG, "start() called but video track already exists.")
            return
        }
        Log.i(TAG, "Starting video capture")

        try {
            // 创建 Helper
            surfaceTextureHelper = SurfaceTextureHelper.create(THREAD_NAME, eglContext)

            // 创建 Capturer
            videoCapturer = createCameraCapturer()
            if (videoCapturer == null) {
                Log.e(TAG, "Failed to create VideoCapturer. No camera found.")
                return
            }

            // 创建 Source (isScreencast: false 表示摄像头)
            videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)

            Log.d(
                TAG,
                "Initializing capturer with resolution: ${WebRTCConfig.VIDEO_WIDTH}x${WebRTCConfig.VIDEO_HEIGHT} @ ${WebRTCConfig.VIDEO_FPS}fps"
            )
            // 初始化并开始采集
            videoCapturer!!.apply {
                initialize(
                    surfaceTextureHelper,
                    context,
                    videoSource!!.capturerObserver
                )
                startCapture(
                    WebRTCConfig.VIDEO_WIDTH,
                    WebRTCConfig.VIDEO_HEIGHT,
                    WebRTCConfig.VIDEO_FPS
                )
            }

            // 创建 Track
            val videoTrack = factory.createVideoTrack(TRACK_ID, videoSource)
            videoTrack.setEnabled(true)

            // 更新状态
            _localVideoTrackFlow.update { videoTrack }
            Log.i(TAG, "Video capture started successfully. Track ID: $TRACK_ID")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting video capture", e)
            dispose()
        }
    }

    /**
     * 创建 videoCapturer
     */
    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // 优先找前置，没有则找后置，再没有就随便找一个
        val deviceName = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: deviceNames.firstOrNull { enumerator.isBackFacing(it) }
            ?: deviceNames.firstOrNull()

        return deviceName?.let { name ->
            currentDeviceName = name

            val isFront = enumerator.isFrontFacing(name)
            _isFrontCamera.update { isFront }
            Log.i(TAG, "Selected camera: $name (isFront: $isFront)")

            enumerator.createCapturer(name, null)
        }
    }

    /**
     * 切换摄像头
     */
    fun flipCamera() {
        if (isSwitching.get()) {
            Log.w(TAG, "Camera is already switching, ignoring request.")
            return
        }

        val capturer = videoCapturer as? CameraVideoCapturer
        if (capturer == null) {
            Log.e(TAG, "switchCamera failed: videoCapturer is null or not a CameraVideoCapturer")
            return
        }

        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // 当前是否是前置
        val isNowFront = if (currentDeviceName != null) {
            enumerator.isFrontFacing(currentDeviceName)
        } else {
            true
        }

        // 寻找目标摄像头
        val targetName = if (isNowFront) {
            deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        } else {
            deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        }

        isSwitching.set(true)

        val switchHandler = object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isSwitching.set(false)
                _isFrontCamera.update { isFront }

                // 如果指定了 targetName，更新 currentDeviceName
                if (targetName != null) {
                    currentDeviceName = targetName
                }

                Log.i(TAG, "Camera switch DONE. isFront: $isFront, Device: $currentDeviceName")
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                isSwitching.set(false)
                Log.e(TAG, "Camera switch ERROR: $errorDescription")
            }
        }

        if (targetName != null) {
            capturer.switchCamera(switchHandler, targetName)
        } else {
            Log.w(TAG, "Target camera name not found. Attempting generic switch.")
            capturer.switchCamera(switchHandler)
        }
    }

    /**
     * 暂停摄像头 (不销毁对象，只关硬件)
     */
    fun stopCapture() {
        try {
            videoCapturer?.stopCapture()
            Log.d(TAG, "Camera capture stopped (Hardware off)")
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while stopping capture", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
    }

    /**
     * 恢复摄像头
     */
    fun resumeCapture() {
        if (videoCapturer == null) {
            Log.w(TAG, "resumeCapture ignored: videoCapturer is null")
            return
        }

        try {
            // 需要确保 capturer 还是活的
            videoCapturer!!.startCapture(
                WebRTCConfig.VIDEO_WIDTH,
                WebRTCConfig.VIDEO_HEIGHT,
                WebRTCConfig.VIDEO_FPS
            )
            Log.d(TAG, "Camera capture resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming capture", e)
        }
    }

    fun dispose() {
        try {
            isSwitching.set(false)
            // 停止硬件占用
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()

            // 释放 Source
            videoSource?.dispose()

            // 释放 Track
            _localVideoTrackFlow.value?.safeDispose()

            // 释放 EGL 上下文
            surfaceTextureHelper?.dispose()

            Log.d(TAG, "Video resources disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing video resources", e)
        } finally {
            videoCapturer = null
            videoSource = null
            _localVideoTrackFlow.update { null }
            surfaceTextureHelper = null
        }
    }
}
