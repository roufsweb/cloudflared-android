package com.rouf.cloudflared

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPreferences(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFF6821F), // Cloudflare Orange
                    background = Color(0xFF1E1E1E),
                    surface = Color(0xFF2D2D2D)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CloudflaredApp(prefs, onStartService = {
                        val intent = Intent(this, CloudflaredService::class.java)
                        intent.action = CloudflaredService.ACTION_START
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }, onStopService = {
                        val intent = Intent(this, CloudflaredService::class.java)
                        intent.action = CloudflaredService.ACTION_STOP
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudflaredApp(prefs: AppPreferences, onStartService: () -> Unit, onStopService: () -> Unit) {
    val context = LocalContext.current
    var token by remember { mutableStateOf(prefs.token) }
    var autoStart by remember { mutableStateOf(prefs.autoStart) }
    var startDelay by remember { mutableStateOf(prefs.startDelaySeconds.toString()) }
    
    val status by CloudflaredService.status.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Cloudflared",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Status", color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                val statusColor = when {
                    status == "Connected" -> Color(0xFF4CAF50)
                    status.startsWith("Error") -> Color(0xFFF44336)
                    status == "Disconnected" -> Color.Gray
                    else -> Color(0xFFFFC107)
                }
                Text(
                    text = status,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { 
                // Auto-extract token if they paste the full command
                val extractedToken = it.replace("sudo cloudflared service install ", "")
                  .replace("cloudflared.exe service install ", "")
                  .trim()
                token = extractedToken
                prefs.token = extractedToken
            },
            label = { Text("Tunnel Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Start on Boot")
            Switch(
                checked = autoStart,
                onCheckedChange = {
                    autoStart = it
                    prefs.autoStart = it
                    updateMagiskScript(context, it, prefs.startDelaySeconds)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = startDelay,
            onValueChange = { 
                startDelay = it
                val delayNum = it.toIntOrNull() ?: 0
                prefs.startDelaySeconds = delayNum
                if (autoStart) {
                    updateMagiskScript(context, true, delayNum)
                }
            },
            label = { Text("Boot Delay (seconds)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        val isConnected = status == "Connected" || status.startsWith("Waiting") || status.startsWith("Extracting") || status.startsWith("Connecting")
        
        Button(
            onClick = {
                if (isConnected) onStopService() else onStartService()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isConnected) "Stop Service" else "Connect Tunnel",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

fun updateMagiskScript(context: android.content.Context, enabled: Boolean, delay: Int) {
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
