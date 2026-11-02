package com.rouf.cloudflared

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val prefs = AppPreferences(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CloudflaredApp(
                        prefs = prefs,
                        onStartService = {
                            val intent = Intent(this, CloudflaredService::class.java).apply {
                                action = CloudflaredService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                        },
                        onStopService = {
                            val intent = Intent(this, CloudflaredService::class.java).apply {
                                action = CloudflaredService.ACTION_STOP
                            }
                            startService(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CloudflaredApp(prefs: AppPreferences, onStartService: () -> Unit, onStopService: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var showInstructions by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(prefs.token) }
    val tokenInfo = remember(token) { TunnelTokenParser.parseTokenInfo(token) }

    var autoStart by remember { mutableStateOf(prefs.autoStart) }
    var startDelay by remember { mutableStateOf(prefs.startDelaySeconds.toString()) }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionType by remember { mutableStateOf("") } // "notification" or "battery"

    val status by CloudflaredService.status.collectAsState()
    val updateStatus by UpdateManager.updateStatus.collectAsState()

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(if (permissionType == "notification") "Notification Permission" else "Battery Optimization") },
            text = { 
                Text(
                    if (permissionType == "notification") 
                        "Cloudflared needs notification permission to stay active in the background and show the current tunnel status."
                    else 
                        "To ensure the tunnel starts reliably after boot and doesn't get killed, please exclude Cloudflared from battery optimizations."
                ) 
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    if (permissionType == "notification") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                             val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                 putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                             }
                             context.startActivity(intent)
                        }
                    } else {
                        FlavorUtils.requestBatteryOptimization(context)
                    }
                }) {
                    Text("Grant")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Cloudflared",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Update Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Engine: ${prefs.binaryVersion}", fontSize = 12.sp, color = Color.LightGray)
                    val statusText = when (val s = updateStatus) {
                        is UpdateManager.UpdateStatus.Checking -> "Checking for updates..."
                        is UpdateManager.UpdateStatus.NewVersionAvailable -> "New version available: ${s.version}"
                        is UpdateManager.UpdateStatus.Downloading -> "Downloading..."
                        is UpdateManager.UpdateStatus.Success -> "Updated! Restart service to apply."
                        is UpdateManager.UpdateStatus.Error -> "Error: ${s.message}"
                        is UpdateManager.UpdateStatus.UpToDate -> "Up to date"
                        else -> "Last check: ${prefs.binaryVersion}"
                    }
                    Text(statusText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                
                if (updateStatus is UpdateManager.UpdateStatus.NewVersionAvailable) {
                    IconButton(onClick = { 
                        val version = (updateStatus as UpdateManager.UpdateStatus.NewVersionAvailable).version
                        scope.launch { UpdateManager.downloadUpdate(context, version) }
                    }) {
                        Icon(Icons.Default.ArrowCircleDown, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { scope.launch { UpdateManager.checkForUpdates(context) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check", tint = Color.Gray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tunnel Status", color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                val statusColor = when {
                    status == "Connected" -> Color(0xFF4CAF50)
                    status.startsWith("Error") || status.startsWith("Exited") -> Color(0xFFF44336)
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

        Spacer(modifier = Modifier.height(16.dp))

        // How to Use Instructions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Instructions",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "How to Use",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                    IconButton(
                        onClick = { showInstructions = !showInstructions },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (showInstructions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showInstructions) "Collapse" else "Expand"
                        )
                    }
                }

                if (showInstructions) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. Open one.dash.cloudflare.com > Networks > Tunnels\n" +
                               "2. Click 'Add a tunnel', choose Cloudflared, and name it\n" +
                               "3. Copy the installation command from any environment (Windows, Linux, or Docker)\n" +
                               "4. Paste it directly below - the token will be extracted automatically!\n" +
                               "5. Tap 'Connect Tunnel' and map your public hostname to localhost:8080",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Smart Tunnel Token Input
        OutlinedTextField(
            value = token,
            onValueChange = { raw -> 
                val extracted = TunnelTokenParser.extractToken(raw)
                token = extracted
                prefs.token = extracted
            },
            label = { Text("Tunnel Token") },
            placeholder = { Text("Paste token or full command...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Token Key",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (token.isNotEmpty()) {
                    IconButton(onClick = {
                        token = ""
                        prefs.token = ""
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Token"
                        )
                    }
                } else {
                    IconButton(onClick = {
                        val clipText = clipboardManager.getText()?.text
                        if (!clipText.isNullOrBlank()) {
                            val extracted = TunnelTokenParser.extractToken(clipText)
                            token = extracted
                            prefs.token = extracted
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste from Clipboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Token Validation & Format Helper
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (token.isEmpty()) {
                Text(
                    text = "Supports pure token or any command (Windows, Linux, Docker)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            } else if (tokenInfo != null) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Valid",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Valid Token • ${tokenInfo.summary}",
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Unverified",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Custom / Unverified token format (${token.length} chars)",
                    fontSize = 12.sp,
                    color = Color(0xFFFF9800)
                )
            }
        }

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
                    if (it) {
                        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            permissionType = "battery"
                            showPermissionDialog = true
                        }
                    }
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
            },
            label = { Text("Boot Delay (seconds)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

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
