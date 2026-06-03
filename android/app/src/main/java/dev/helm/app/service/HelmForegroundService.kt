package dev.helm.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.helm.app.MainActivity
import dev.helm.app.data.repository.HelmRepository
import javax.inject.Inject

@AndroidEntryPoint
class HelmForegroundService : Service() {

    @Inject lateinit var repository: HelmRepository

    private var isStarted = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        // Always re-post startForeground to satisfy Android's 5-second requirement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!isStarted) {
            isStarted = true
            repository.connect()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        repository.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("HELM")
        .setContentText("Connected to workstation agent")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HELM Connection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "HELM agent connection status" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "helm_connection"
        const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set
    }
}
