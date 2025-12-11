package io.github.hcisme.mediasoupclient.controller

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory

class AudioController(
    context: Context,
    private val factory: PeerConnectionFactory
) {
    companion object {
        private const val TAG = "AudioController"
        private const val TRACK_ID = "local_audio_track"

        // WebRTC 音频约束键名
        private const val AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        private const val AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        private const val AUDIO_HIGHPASS_FILTER_CONSTRAINT = "googHighpassFilter"
        private const val AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
    }

    // =================================================================================
    // WebRTC 麦克风采集 (Input)
    // =================================================================================
    private var audioSource: AudioSource? = null
    var audioTrack: AudioTrack? = null
        private set

    /**
     * 创建本地音频轨道 (开启麦克风采集)
     */
    fun createLocalAudioTrack(): AudioTrack? {
        if (audioTrack != null) return audioTrack

        // 开启回声消除、增益控制、降噪
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"))
            mandatory.add(MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "true"))
            mandatory.add(MediaConstraints.KeyValuePair(AUDIO_HIGHPASS_FILTER_CONSTRAINT, "true"))
            mandatory.add(MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"))
        }
        try {
            audioSource = factory.createAudioSource(audioConstraints)
            audioTrack = factory.createAudioTrack(TRACK_ID, audioSource)
            audioTrack?.setEnabled(true)
            Log.i(TAG, "Microphone audio track created successfully with constraints")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio track", e)
        }
        return audioTrack
    }

    /**
     * 仅控制麦克风静音 (不释放资源)
     * true: 静音 (发送空包), false: 正常说话
     */
    fun setMicMuted(muted: Boolean) {
        if (audioTrack == null) {
            Log.w(TAG, "setMicMuted ignored: audioTrack is null")
            return
        }
        audioTrack!!.setEnabled(!muted)
        Log.d(TAG, "Microphone state changed: muted=$muted")
    }

    /**
     * 销毁 WebRTC 音频资源
     */
    private fun disposeWebRTC() {
        try {
            audioTrack?.dispose()
            audioTrack = null
            audioSource?.dispose()
            audioSource = null
            Log.d(TAG, "WebRTC audio resources disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing WebRTC audio", e)
        }
    }

    // =================================================================================
    // Android 系统音频路由 (Output)
    // 负责切换 扬声器 / 听筒 / 蓝牙
    // =================================================================================
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // 记录初始状态以便还原
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var wasSpeakerOn: Boolean = false

    /**
     * 初始化音频系统 (进入通话模式)
     * 建议在加入房间前调用
     */
    fun initAudioSystem() {
        Log.d(TAG, "Initializing audio system")
        savedAudioMode = audioManager.mode
        wasSpeakerOn = isSpeakerOnCompat()

        Log.d(TAG, "Saved previous state: mode=$savedAudioMode, speaker=$wasSpeakerOn")

        // 切换到通话模式，WebRTC 需要在 MODE_IN_COMMUNICATION 下才能有效进行硬件回声消除
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        // 默认开启扬声器 (视频会议通常默认扬声器)
        setSpeakerphoneOn(true)

        Log.d(TAG, "Audio system initialized in communication mode")
    }

    /**
     * 兼容性地检查扬声器是否开启
     *
     * 解决 API 34 isSpeakerphoneOn Deprecated 问题
     */
    private fun isSpeakerOnCompat(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) 及以上
            val device = audioManager.communicationDevice
            device?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            // Android 11 及以下
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn
        }
    }

    /**
     * 切换 扬声器 (true) / 听筒/蓝牙/有线耳机，取决于系统路由策略 (false)
     */
    fun setSpeakerphoneOn(on: Boolean) {
        Log.i(TAG, "Requesting switch audio output. Target Speaker: $on")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            if (on) {
                val speakerDevice =
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                speakerDevice?.let {
                    val result = audioManager.setCommunicationDevice(it)
                    if (result) {
                        Log.d(TAG, "Success: Switched to Built-in Speaker")
                    } else {
                        Log.e(TAG, "Failed: Audio system refused to switch to Speaker")
                    }
                } ?: Log.w(TAG, "Failed: No built-in speaker found in available devices")
            } else {
                // 清除后，系统会自动回退到听筒或已连接的蓝牙耳机
                audioManager.clearCommunicationDevice()
                Log.d(TAG, "Success: Cleared communication device (Reverted to Earpiece/Headset)")
            }
        } else {
            // 旧版本
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
            Log.d(TAG, "Legacy API: setSpeakerphoneOn($on) called")
        }
    }

    /**
     * 还原音频系统
     */
    private fun disposeAudioSystem() {
        Log.d(TAG, "Restoring audio system")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = wasSpeakerOn
        }
        // 还原模式
        audioManager.mode = savedAudioMode

        Log.d(TAG, "Audio system restored")
    }

    /**
     * 公共销毁方法
     */
    fun dispose() {
        Log.i(TAG, "Dispose called")
        disposeWebRTC()
        disposeAudioSystem()
    }
}