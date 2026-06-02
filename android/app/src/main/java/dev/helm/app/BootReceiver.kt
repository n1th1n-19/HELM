package dev.helm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.helm.app.service.HelmForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, HelmForegroundService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
