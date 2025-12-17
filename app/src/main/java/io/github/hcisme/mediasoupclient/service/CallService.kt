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
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.hcisme.mediasoupclient.MainActivity
import io.github.hcisme.mediasoupclient.R

class CallService : Service() {
    companion object {
        private const val START_FOREGROUND_SERVICE_ID = 1
        const val DEFAULT_CHANNEL_ID = "default_channel_id_$START_FOREGROUND_SERVICE_ID"
        const val CHANNEL_ID = "channel_id_$START_FOREGROUND_SERVICE_ID"
        const val CONTENT_TITLE = "正在通话中"
        const val CHANNEL_NAME = "点击返回"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceCompat()
    }

    private fun startForegroundServiceCompat() {
        val notification = createNotification()
        val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

        try {
            ServiceCompat.startForeground(
                this,
                START_FOREGROUND_SERVICE_ID,
                notification,
                serviceType
            )
        } catch (e: Exception) {
            e.printStackTrace()
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

    fun start(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, CallService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动服务失败: ${e.message}")
            e.printStackTrace()
            runCatching {
                Toast.makeText(appContext, "无法启动通话服务", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, CallService::class.java)

        val stopped = appContext.stopService(intent)
        Log.d(TAG, "停止服务结果: $stopped")
    }
}
