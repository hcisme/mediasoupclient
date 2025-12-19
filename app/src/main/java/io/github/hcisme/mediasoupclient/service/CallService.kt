package io.github.hcisme.mediasoupclient.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.hcisme.mediasoupclient.MainActivity
import io.github.hcisme.mediasoupclient.R

class CallService : Service() {
    companion object {
        private const val TAG = "CallService"
        private const val START_FOREGROUND_SERVICE_ID = 1
        private const val DEFAULT_CHANNEL_ID = "default_channel_id_$START_FOREGROUND_SERVICE_ID"
        private const val CHANNEL_ID = "channel_id_$START_FOREGROUND_SERVICE_ID"
        private const val CONTENT_TITLE = "正在通话中"
        private const val CHANNEL_NAME = "点击返回"
        const val EXTRA_IS_SCREEN_SHARE = "extra_is_screen_share"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        updateServiceState(isScreenSharing = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 每次调用 startService/startForegroundService 都会走到这里
        // Intent 中的参数，更新服务类型
        val isScreenSharing = intent?.getBooleanExtra(EXTRA_IS_SCREEN_SHARE, false) ?: false
        updateServiceState(isScreenSharing)
        return START_NOT_STICKY
    }

    /**
     * 更新前台服务状态
     * @param isScreenSharing 是否正在屏幕共享
     */
    private fun updateServiceState(isScreenSharing: Boolean) {
        val notification = createNotification()

        // 基础类型：麦克风 + 摄像头
        var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

        // 如果是屏幕共享且系统版本 >= Android 10 (Q)，叠加 mediaProjection 类型
        if (isScreenSharing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 必须确保此时 App 已经有了录屏权限，否则在 Android 14+ 会崩溃
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }

        try {
            ServiceCompat.startForeground(
                this,
                START_FOREGROUND_SERVICE_ID,
                notification,
                serviceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "StartForeground Fail", e)
        }
    }

    private fun createNotification(): Notification {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else DEFAULT_CHANNEL_ID

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            // Android 12+ 必须指定 FLAG_IMMUTABLE
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(CONTENT_TITLE)
            .setContentText(CHANNEL_NAME)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 无法侧滑删除
            .build()
    }

    /**
     * 从 Android 8.0 开始，系统要求所有通知必须关联一个通知渠道
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return CHANNEL_ID
    }

    override fun onDestroy() {
        super.onDestroy()
        // ServiceCompat.STOP_FOREGROUND_REMOVE 等同于之前的 true (停止服务并移除通知)
        // ServiceCompat.STOP_FOREGROUND_DETACH 等同于之前的 false (停止服务但保留通知)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }
}

object CallServiceManager {
    private const val TAG = "CallServiceManager"

    /**
     * 启动服务 (基础模式：仅麦克风/摄像头)
     */
    fun start(context: Context) {
        startOrUpdate(context, isScreenSharing = false)
    }

    /**
     * 更新服务状态 (开启/关闭屏幕共享)
     * 在用户授权录屏后调用此方法传入 true
     */
    fun updateScreenShareState(context: Context, isSharing: Boolean) {
        startOrUpdate(context, isScreenSharing = isSharing)
    }

    private fun startOrUpdate(context: Context, isScreenSharing: Boolean) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, CallService::class.java).apply {
            putExtra(CallService.EXTRA_IS_SCREEN_SHARE, isScreenSharing)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动/更新服务失败", e)
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, CallService::class.java)

        val stopped = appContext.stopService(intent)
        Log.d(TAG, "停止服务结果: $stopped")
    }
}
