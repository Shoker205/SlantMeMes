package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ChatRealtimeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        PushNotificationManager.init(this)
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "chat_realtime_service_channel_2" // Changed ID to apply new importance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Фоновая синхронизация",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Slant")
            .setContentText("Служба синхронизации активна")
            .setSmallIcon(R.drawable.ic_notification_minimal)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1001, notification)
    }
}
