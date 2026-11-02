package com.rouf.cloudflared

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val GITHUB_API_URL = "https://api.github.com/repos/cloudflare/cloudflared/releases/latest"
    private const val BINARY_NAME = "cloudflared-linux-arm64"
    
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus = _updateStatus.asStateFlow()

    sealed class UpdateStatus {
        object Idle : UpdateStatus()
        object Checking : UpdateStatus()
        data class NewVersionAvailable(val version: String) : UpdateStatus()
        object Downloading : UpdateStatus()
        object UpToDate : UpdateStatus()
        data class Error(val message: String) : UpdateStatus()
        object Success : UpdateStatus()
    }

    suspend fun checkForUpdates(context: Context) {
        _updateStatus.value = UpdateStatus.Checking
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(GITHUB_API_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "cloudflared-android")
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val latestVersion = json.getString("tag_name")
                    val prefs = AppPreferences(context)
                    
                    if (latestVersion != prefs.binaryVersion) {
                        _updateStatus.value = UpdateStatus.NewVersionAvailable(latestVersion)
                    } else {
                        _updateStatus.value = UpdateStatus.UpToDate
                    }
                } else {
                    _updateStatus.value = UpdateStatus.Error("Failed to check updates: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                _updateStatus.value = UpdateStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun downloadUpdate(context: Context, version: String) {
        _updateStatus.value = UpdateStatus.Downloading
        withContext(Dispatchers.IO) {
            try {
                val downloadUrl = "https://github.com/cloudflare/cloudflared/releases/download/$version/$BINARY_NAME"
                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "cloudflared-android")
                
                if (connection.responseCode == 200) {
                    val storageContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        context.createDeviceProtectedStorageContext()
                    } else {
                        context
                    }
                    val destFile = File(storageContext.filesDir, "cloudflared_updated")
                    val tempFile = File(storageContext.filesDir, "cloudflared_downloading")
                    
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Patch the binary to use /proc/self/fd/0 instead of /etc/resolv.conf
                    patchBinary(tempFile)
                    
                    // Set execution permissions
                    tempFile.setExecutable(true, false)
                    
                    // Rename to final location
                    if (tempFile.renameTo(destFile)) {
                        val prefs = AppPreferences(context)
                        prefs.binaryVersion = version
                        _updateStatus.value = UpdateStatus.Success
                    } else {
                        _updateStatus.value = UpdateStatus.Error("Failed to rename binary")
                    }
                } else {
                    _updateStatus.value = UpdateStatus.Error("Download failed: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                _updateStatus.value = UpdateStatus.Error(e.message ?: "Download error")
            }
        }
    }

    private fun patchBinary(file: File) {
        try {
            val bytes = file.readBytes()
            val target = "/etc/resolv.conf".toByteArray(Charsets.US_ASCII)
            val replacement = "//proc/self/fd/0".toByteArray(Charsets.US_ASCII)
            
            var patches = 0
            for (i in 0 until bytes.size - target.size) {
                var match = true
                for (j in target.indices) {
                    if (bytes[i + j] != target[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    for (j in replacement.indices) {
                        bytes[i + j] = replacement[j]
                    }
                    patches++
                }
            }
            if (patches > 0) {
                file.writeBytes(bytes)
                Log.d("UpdateManager", "Patched $patches occurrences of resolv.conf path")
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to patch binary: ${e.message}")
        }
    }

    fun getUpdateFile(context: Context): File? {
        val file = File(context.externalCacheDir, "update.apk")
        return if (file.exists()) file else null
    }
    
    fun getExecutablePath(context: Context): String {
        val storageContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        val updatedFile = File(storageContext.filesDir, "cloudflared_updated")
        if (updatedFile.exists()) {
            updatedFile.setExecutable(true, false) // Ensure it's still executable
            return updatedFile.absolutePath
        }
        
        // Fallback to bundled library
        val nativeDir = context.applicationInfo.nativeLibraryDir
        return File(nativeDir, "libcloudflared.so").absolutePath
    }
}
