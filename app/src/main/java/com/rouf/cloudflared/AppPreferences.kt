package com.rouf.cloudflared

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cloudflared_prefs", Context.MODE_PRIVATE)

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) = prefs.edit().putString("token", value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean("autoStart", false)
        set(value) = prefs.edit().putBoolean("autoStart", value).apply()

    var startDelaySeconds: Int
        get() = prefs.getInt("startDelay", 5)
        set(value) = prefs.edit().putInt("startDelay", value).apply()
}
