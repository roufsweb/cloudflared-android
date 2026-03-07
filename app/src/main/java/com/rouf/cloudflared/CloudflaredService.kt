package com.rouf.cloudflared

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class CloudflaredService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var process: Process? = null
    private var isRunning = false

    companion object {
        const val ACTION_START = "com.rouf.cloudflared.START"
        const val ACTION_START_WITH_DELAY = "com.rouf.cloudflared.START_WITH_DELAY"
        const val ACTION_STOP = "com.rouf.cloudflared.STOP"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "CloudflaredChannel"

        private val _status = MutableStateFlow("Disconnected")
        val status = _status.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("Starting..."))
                startDaemon(delaySeconds = 0)
            }
            ACTION_START_WITH_DELAY -> {
                val prefs = AppPreferences(this)
                val delaySeconds = prefs.startDelaySeconds
                startForeground(NOTIFICATION_ID, createNotification("Waiting ${delaySeconds}s to start..."))
                startDaemon(delaySeconds)
            }
            ACTION_STOP -> {
                stopDaemon()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startDaemon(delaySeconds: Int) {
        if (isRunning) return
        isRunning = true

        serviceScope.launch {
            try {
                if (delaySeconds > 0) {
                    _status.value = "Waiting ${delaySeconds}s..."
                    delay(delaySeconds * 1000L)
                }

                _status.value = "Extracting binary..."
                val binaryFile = extractBinary()

                val prefs = AppPreferences(this@CloudflaredService)
                val token = prefs.token.trim()

                if (token.isBlank()) {
                    _status.value = "Error: Token is empty"
                    isRunning = false
                    updateNotification("Error: Token is empty")
                    return@launch
                }

                _status.value = "Connecting..."
                updateNotification("Connected")

                // Execute cloudflared via root from /data/local/tmp
                // Execute cloudflared via root from /data/local/tmp
                // We patched the cloudflared binary to look for /data/local/resv instead of /etc/resolv.conf
                Runtime.getRuntime().exec(arrayOf("su", "-c", "echo nameserver 1.1.1.1 > /data/local/resv && echo nameserver 8.8.8.8 >> /data/local/resv && chmod 644 /data/local/resv")).waitFor()

                
                // Global flags: --no-autoupdate, --edge-ip-version 4, --protocol http2
                // Command flags: run --token $token
                val command = arrayOf("su", "-c", "${binaryFile.absolutePath} tunnel --no-autoupdate --edge-ip-version 4 --protocol http2 run --token $token")
                val processBuilder = ProcessBuilder(*command)
                processBuilder.redirectErrorStream(true)
                
                process = processBuilder.start()

                var lastLine = ""
                // Read output to keep process buffers clear
                process?.inputStream?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d("CloudflaredDaemon", line ?: "")
                        if (line?.isNotBlank() == true) {
                            lastLine = line!!
                        }
                        if (line?.contains("Registered tunnel connection") == true) {
                            _status.value = "Connected"
                            updateNotification("Connected")
                        } else if (line?.contains("failed to connect") == true) {
                            _status.value = "Connection Failed"
                            updateNotification("Connection Failed")
                        }
                    }
                }

                val exitCode = process?.waitFor() ?: -1
                if (_status.value != "Connected" && _status.value != "Disconnected" && _status.value != "Disconnecting...") {
                    // It failed to connect or exited prematurely
                    val shortFormat = lastLine.takeLast(40)
                    _status.value = "Exited ($exitCode): $shortFormat"
                    updateNotification("Exited ($exitCode)")
                } else {
                    _status.value = "Disconnected"
                    updateNotification("Disconnected")
                }
                isRunning = false

            } catch (e: Exception) {
                e.printStackTrace()
                _status.value = "Error: ${e.message}"
                isRunning = false
                updateNotification("Error: ${e.message}")
            }
        }
    }

    private fun stopDaemon() {
        if (!isRunning) return
        _status.value = "Disconnecting..."
        serviceScope.launch {
            try {
                // Kill the cloudflared process via root
                Runtime.getRuntime().exec(arrayOf("su", "-c", "killall cloudflared-linux-arm64")).waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            process?.destroy()
            process = null
            isRunning = false
            _status.value = "Disconnected"
        }
    }

    private suspend fun extractBinary(): File {
        val destFile = File(filesDir, "cloudflared-linux-arm64")
        if (!destFile.exists()) {
            assets.open("cloudflared-linux-arm64").use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        val tmpDirFile = File("/data/local/tmp/cloudflared-linux-arm64")
        // Copy to tmp and make executable
        Runtime.getRuntime().exec(arrayOf("su", "-c", "cp ${destFile.absolutePath} ${tmpDirFile.absolutePath}")).waitFor()
        Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod +x ${tmpDirFile.absolutePath}")).waitFor()
        return tmpDirFile
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Cloudflared Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        // Use mutability flags appropriate for API 28+
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cloudflared")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_secure) // Default built-in system icon for now
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDaemon()
        serviceScope.launch {
            _status.value = "Disconnected"
        }
    }
}
