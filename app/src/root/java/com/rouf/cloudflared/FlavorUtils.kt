package com.rouf.cloudflared

import android.content.Context

object FlavorUtils {
    fun updateMagiskScript(context: Context, enabled: Boolean, delay: Int) {
        try {
            if (enabled) {
                val scriptFile = java.io.File(context.filesDir, "cloudflared_autostart.sh")
                val script = """
                    #!/system/bin/sh
                    while [ "${'$'}(getprop sys.boot_completed)" != "1" ]; do sleep 1; done
                    sleep $delay
                    am start-foreground-service -a com.rouf.cloudflared.START_WITH_DELAY -n com.rouf.cloudflared/.CloudflaredService
                """.trimIndent()
                scriptFile.writeText(script)
                val command = arrayOf("su", "-c", "cp ${scriptFile.absolutePath} /data/adb/service.d/cloudflared_autostart.sh && chmod 755 /data/adb/service.d/cloudflared_autostart.sh")
                Runtime.getRuntime().exec(command).waitFor()
            } else {
                val command = arrayOf("su", "-c", "rm -f /data/adb/service.d/cloudflared_autostart.sh")
                Runtime.getRuntime().exec(command).waitFor()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun requestBatteryOptimization(context: Context) {
        // Not strictly required for root/Magisk script, but included for API parity
    }
}
