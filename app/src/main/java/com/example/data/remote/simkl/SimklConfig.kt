package com.example.data.remote.simkl

import com.example.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Configurazione centralizzata per l'integrazione ufficiale Simkl API & AUTH V2.
 * Non contiene alcun `client_secret`: il flusso TV utilizza esclusivamente Device Flow pubblico + `client_id`
 * configurabile dal pannello Secrets di AI Studio (`SIMKL_CLIENT_ID`).
 */
data class SimklConfig(
    val apiBaseUrl: String = SIMKL_API_BASE_URL,
    val authBaseUrl: String = SIMKL_AUTH_BASE_URL,
    val appName: String = SIMKL_APP_NAME,
    val appVersion: String = SIMKL_APP_VERSION,
    val overrideClientId: String? = null
) {
    val clientId: String
        get() {
            val raw = overrideClientId ?: runCatching { BuildConfig.SIMKL_CLIENT_ID }.getOrDefault("")
            val trimmed = raw.trim()
            return if (trimmed == PLACEHOLDER_CLIENT_ID) "" else trimmed
        }

    val isClientIdConfigured: Boolean
        get() = clientId.isNotBlank()

    fun isTrustedVerificationUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.startsWith("https://simkl.com/", ignoreCase = true) ||
            trimmed == "https://simkl.com" ||
            (authBaseUrl.startsWith("http://localhost", ignoreCase = true) && trimmed.startsWith(authBaseUrl, ignoreCase = true)) ||
            (authBaseUrl.startsWith("http://127.0.0.1", ignoreCase = true) && trimmed.startsWith(authBaseUrl, ignoreCase = true))
    }

    fun normalizeVerificationUrl(rawUri: String?): String? {
        val trimmed = rawUri?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val resolved = when {
            trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("/") -> "${authBaseUrl.trimEnd('/')}$trimmed"
            else -> "${authBaseUrl.trimEnd('/')}/$trimmed"
        }
        return resolved.takeIf { isTrustedVerificationUrl(it) }
    }

    companion object {
        const val SIMKL_API_BASE_URL = "https://api.simkl.com/"
        const val SIMKL_AUTH_BASE_URL = "https://simkl.com"
        const val SIMKL_APP_NAME = "VixFlix"
        val SIMKL_APP_VERSION: String get() = BuildConfig.VERSION_NAME
        const val PLACEHOLDER_CLIENT_ID = "MY_SIMKL_CLIENT_ID"

        const val DEFAULT_SCOPES = "media:read media:write"
        const val PROFILE_CREATION_PENDING_ID = "__simkl_profile_creation__"
        const val GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code"
        const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"
        const val DEFAULT_MIN_PROGRESS_STOP = 96
        const val COMPLETE_MIN_PROGRESS_STOP = 100
        const val TOKEN_REFRESH_LEAD_TIME_MS = 6L * 3600L * 1000L
        const val MIN_POST_INTERVAL_MS = 1_050L
    }
}

/**
 * Generatore PKCE RFC-7636 (S256) con SecureRandom crittografico.
 */
object SimklPkceHelper {
    private val secureRandom = SecureRandom()
    private val unreservedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~".toCharArray()

    data class PkcePair(
        val codeVerifier: String,
        val codeChallenge: String,
        val method: String = "S256"
    )

    fun generate(length: Int = 64): PkcePair {
        val safeLen = length.coerceIn(43, 128)
        val verifierChars = CharArray(safeLen) {
            unreservedChars[secureRandom.nextInt(unreservedChars.size)]
        }
        val verifier = String(verifierChars)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = encodeBase64UrlNoPadding(digest)
        return PkcePair(
            codeVerifier = verifier,
            codeChallenge = challenge,
            method = "S256"
        )
    }

    private fun encodeBase64UrlNoPadding(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val hasB2 = i + 2 < bytes.size
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0

            out.append(alphabet[b0 ushr 2])
            out.append(alphabet[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            if (hasB1) {
                out.append(alphabet[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
            }
            if (hasB2) {
                out.append(alphabet[b2 and 0x3F])
            }
            i += 3
        }
        return out.toString()
    }
}
