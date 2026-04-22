package com.rouf.cloudflared

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null

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
        
        // Collect status and update notification in real-time
        serviceScope.launch {
            _status.collect { newStatus ->
                updateNotification(newStatus)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, createNotification("Starting..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification("Starting..."))
                }
                startDaemon(delaySeconds = 0)
            }
            ACTION_START_WITH_DELAY -> {
                val prefs = AppPreferences(this)
                val delaySeconds = prefs.startDelaySeconds
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, createNotification("Waiting ${delaySeconds}s..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification("Waiting ${delaySeconds}s to start..."))
                }
                startDaemon(delaySeconds)
            }
            ACTION_STOP -> {
                stopDaemon()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // If we were started with startForegroundService, we MUST call startForeground
                    // even if we are about to stop, to avoid a crash on some Android versions.
                    startForeground(NOTIFICATION_ID, createNotification("Stopping..."))
                }
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
                    wakeLock?.release()
                    return@launch
                }

                _status.value = "Connecting..."

                val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    createDeviceProtectedStorageContext()
                } else {
                    this@CloudflaredService
                }

                // Write custom dns to local app dir
                val dnsFile = File(storageContext.filesDir, "resolv.conf")
                dnsFile.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
                
                val binaryPath = UpdateManager.getExecutablePath(storageContext)
                Log.d("CloudflaredDaemon", "Starting binary: $binaryPath")
                
                val command = arrayOf(binaryPath, "tunnel", "--no-autoupdate", "--edge-ip-version", "4", "--protocol", "http2", "run", "--token", token)
                val processBuilder = ProcessBuilder(*command)
                val env = processBuilder.environment()
                env["GODEBUG"] = "netdns=go"
                processBuilder.redirectInput(dnsFile)
                processBuilder.redirectErrorStream(true)
                
                process = processBuilder.start()

                var lastLine = ""
                process?.inputStream?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d("CloudflaredDaemon", line ?: "")
                        if (line?.isNotBlank() == true) {
                            lastLine = line!!
                        }
                        if (line?.contains("Registered tunnel connection") == true) {
                            _status.value = "Connected"
                            // Ensure we have a wakelock while connected
                            if (wakeLock?.isHeld == false) wakeLock?.acquire()
                        } else if (line?.contains("failed to connect") == true) {
                            _status.value = "Connection Failed"
                        }
                    }
                }

                val exitCode = process?.waitFor() ?: -1
                Log.d("CloudflaredDaemon", "Process exited with $exitCode")
                
                // Only update status and notification if we weren't intentionally stopping
                if (isRunning) {
                    if (lastLine.contains("interrupted") || lastLine.contains("closed")) {
                        _status.value = "Cloudflared Stopped"
                        updateNotification("Cloudflared Stopped")
                    } else if (_status.value != "Connected" && _status.value != "Disconnected" && _status.value != "Disconnecting...") {
                        val shortFormat = lastLine.takeLast(40)
                        _status.value = "Exited ($exitCode): $shortFormat"
                        updateNotification("Exited ($exitCode)")
                    } else {
                        _status.value = "Cloudflared Stopped"
                        updateNotification("Cloudflared Stopped")
                    }
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
        isRunning = false // Set this first to prevent the loop from updating status
        _status.value = "Disconnecting..."
        serviceScope.launch {
            process?.destroy()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process?.destroyForcibly()
            }
            process = null
            _status.value = "Disconnected"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Cloudflared Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cloudflared")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
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
