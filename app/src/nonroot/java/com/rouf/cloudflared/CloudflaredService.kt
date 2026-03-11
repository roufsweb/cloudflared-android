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

    private var wakeLock: PowerManager.WakeLock? = null

    private fun startDaemon(delaySeconds: Int) {
        if (isRunning) return
        isRunning = true

        serviceScope.launch {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Cloudflared:DaemonLock")
                wakeLock?.acquire(5 * 60 * 1000L) // Lock for 5 minutes max during startup

                if (delaySeconds > 0) {
                    _status.value = "Waiting ${delaySeconds}s..."
                    delay(delaySeconds * 1000L)
                }

                val prefs = AppPreferences(this@CloudflaredService)
                val token = prefs.token.trim()

                if (token.isBlank()) {
                    _status.value = "Error: Token is empty"
                    isRunning = false
                    updateNotification("Error: Token is empty")
                    wakeLock?.release()
                    return@launch
                }

                _status.value = "Connecting..."
                updateNotification("Connected")

                // Write custom dns to local app dir for the patched binary
                val dnsFile = File(filesDir, "r.conf")
                dnsFile.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
                
                // Locate library
                val nativeDir = applicationInfo.nativeLibraryDir
                val binaryFile = File(nativeDir, "libcloudflared.so")
                
                if (!binaryFile.exists()) {
                     _status.value = "Error: Binary not found at $nativeDir"
                     isRunning = false
                     updateNotification("Error: Binary not found")
                     wakeLock?.release()
                     return@launch
                }

                Log.d("CloudflaredDaemon", "Starting binary: ${binaryFile.absolutePath}")
                val command = arrayOf(binaryFile.absolutePath, "tunnel", "--no-autoupdate", "--edge-ip-version", "4", "--protocol", "http2", "run", "--token", token)
                val processBuilder = ProcessBuilder(*command)
                processBuilder.redirectInput(dnsFile) // Pass r.conf via stdin for //proc/self/fd/0
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
                            // We are connected, can release wakelock early if we want, 
                            // but usually it's better to keep it a bit or let system handle it.
                            if (wakeLock?.isHeld == true) wakeLock?.release()
                        } else if (line?.contains("failed to connect") == true) {
                            _status.value = "Connection Failed"
                            updateNotification("Connection Failed")
                        }
                    }
                }

                val exitCode = process?.waitFor() ?: -1
                Log.d("CloudflaredDaemon", "Process exited with $exitCode")
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
                Log.e("CloudflaredDaemon", "Error: ${e.message}")
                _status.value = "Error: ${e.message}"
                isRunning = false
                updateNotification("Error: ${e.message}")
            } finally {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            }
        }
    }

    private fun stopDaemon() {
        if (!isRunning) return
        _status.value = "Disconnecting..."
        serviceScope.launch {
            process?.destroy()
            process = null
            isRunning = false
            _status.value = "Disconnected"
        }
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
