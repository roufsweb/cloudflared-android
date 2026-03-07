package com.rouf.cloudflared

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = AppPreferences(context)
            if (prefs.autoStart) {
                Log.d("CloudflaredBoot", "Auto-starting Cloudflared Service")
                val serviceIntent = Intent(context, CloudflaredService::class.java)
                serviceIntent.action = CloudflaredService.ACTION_START_WITH_DELAY
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
