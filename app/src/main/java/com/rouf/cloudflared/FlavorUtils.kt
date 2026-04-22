package com.rouf.cloudflared

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object FlavorUtils {
    fun updateMagiskScript(context: Context, enabled: Boolean, delay: Int) {
        // No-op in consolidated non-root version
    }

    fun requestBatteryOptimization(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
