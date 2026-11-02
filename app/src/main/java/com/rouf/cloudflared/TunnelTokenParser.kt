package com.rouf.cloudflared

import android.util.Base64
import org.json.JSONObject

data class TunnelTokenInfo(
    val accountId: String,
    val tunnelId: String,
    val isValid: Boolean,
    val summary: String
)

object TunnelTokenParser {

    /**
     * Extracts the pure Cloudflare Tunnel token from any input string.
     * Supports:
     * - Pure Base64 token (starting with eyJh / eyJ)
     * - Windows CMD/PowerShell: cloudflared.exe service install <token> or .\cloudflared.exe ...
     * - Linux/macOS: sudo cloudflared service install <token>
     * - Docker: docker run ... --token <token> or --token=<token>
     * - Flags with quotes: --token "eyJh..." or --token='eyJh...'
     * - Multiline shell commands and curl install scripts
     * - Extended or future token lengths
     */
    fun extractToken(rawInput: String?): String {
        if (rawInput.isNullOrBlank()) return ""
        val trimmed = rawInput.trim()

        // 1. Primary Regex: Standard Cloudflare Tunnel tokens encode JSON {"a":"..."} -> Base64 starts with eyJh
        // Matches tokens of 30+ characters in standard Base64 or URL-safe Base64 [A-Za-z0-9+/=_-]
        val cfTokenRegex = Regex("""eyJh[A-Za-z0-9+/=_-]{30,}""")
        val cfMatch = cfTokenRegex.find(trimmed)
        if (cfMatch != null) {
            return cleanToken(cfMatch.value)
        }

        // 2. Generic JSON Base64: Matches any JSON starting with {" -> Base64 eyJ
        val genericJsonRegex = Regex("""eyJ[A-Za-z0-9+/=_-]{30,}""")
        val jsonMatch = genericJsonRegex.find(trimmed)
        if (jsonMatch != null) {
            return cleanToken(jsonMatch.value)
        }

        // 3. Command line flags: --token <val> or --token=<val> or -token <val>
        val flagRegex = Regex("""--?token[\s=]+["']?([A-Za-z0-9+/=_-]{25,})["']?""")
        val flagMatch = flagRegex.find(trimmed)
        if (flagMatch != null && flagMatch.groupValues.size > 1) {
            return cleanToken(flagMatch.groupValues[1])
        }

        // 4. Fallback: Strip common command wrappers and extract the last long token word
        val cleaned = trimmed
            .replace("sudo", "")
            .replace(".\\", "")
            .replace("./", "")
            .replace("cloudflared.exe", "")
            .replace("cloudflared", "")
            .replace("service install", "")
            .replace("tunnel run", "")
            .replace("run", "")
            .trim()

        val candidate = cleaned
            .split(Regex("""\s+"""))
            .map { cleanToken(it) }
            .lastOrNull { it.length >= 30 && it.all { c -> c.isLetterOrDigit() || c in "+/=_-." } }

        return candidate ?: cleanToken(trimmed)
    }

    private fun cleanToken(tok: String): String {
        return tok.trim().trim('"', '\'', '`', ';', ',', '(', ')', '[', ']', '{', '}')
    }

    /**
     * Validates and extracts metadata from a Cloudflare Tunnel token.
     * Tokens decode to JSON: {"a":"<accountId>","t":"<tunnelId>","s":"<secret>"}
     */
    fun parseTokenInfo(token: String): TunnelTokenInfo? {
        val clean = cleanToken(token)
        if (clean.length < 30) return null

        return try {
            // Apply Base64 padding if needed
            val padded = when (clean.length % 4) {
                2 -> "$clean=="
                3 -> "$clean="
                else -> clean
            }
            val decodedBytes = Base64.decode(padded, Base64.DEFAULT or Base64.URL_SAFE)
            val jsonStr = String(decodedBytes, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            val account = json.optString("a", "")
            val tunnel = json.optString("t", "")

            if (account.isNotEmpty() && tunnel.isNotEmpty()) {
                val shortTunnel = if (tunnel.length > 8) tunnel.take(8) + "..." else tunnel
                TunnelTokenInfo(
                    accountId = account,
                    tunnelId = tunnel,
                    isValid = true,
                    summary = "Tunnel: $shortTunnel"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

