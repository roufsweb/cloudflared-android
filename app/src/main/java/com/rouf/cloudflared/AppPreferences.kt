package com.rouf.cloudflared

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = run {
        val storageContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val deviceContext = context.createDeviceProtectedStorageContext()
            // Migrate from credential-protected storage if it exists and we haven't migrated yet
            if (!deviceContext.moveSharedPreferencesFrom(context, "cloudflared_prefs")) {
                Log.w("AppPreferences", "Failed to migrate shared preferences or already migrated")
            }
            deviceContext
        } else {
            context
        }
        storageContext.getSharedPreferences("cloudflared_prefs", Context.MODE_PRIVATE)
    }

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) = prefs.edit().putString("token", value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean("autoStart", false)
        set(value) = prefs.edit().putBoolean("autoStart", value).apply()

    var startDelaySeconds: Int
        get() = prefs.getInt("startDelay", 1)
        set(value) = prefs.edit().putInt("startDelay", value).apply()

    var binaryVersion: String
        get() = prefs.getString("binaryVersion", "bundled") ?: "bundled"
        set(value) = prefs.edit().putString("binaryVersion", value).apply()
}
