package com.example.data.remote

import android.util.Log
import androidx.compose.runtime.Immutable
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Fasi discrete della pipeline di risoluzione e riproduzione VixSrc.
 */
enum class VixSrcResolutionPhase(val label: String) {
    CATALOG_CHECK("Verifica catalogo VixSrc"),
    EMBED_PAGE("Caricamento pagina VixSrc"),
    IFRAME_EMBED("Risoluzione iframe player"),
    API_LOOKUP("Richiesta API VixSrc"),
    PLAYLIST_EXTRACTION("Estrazione sorgente HLS"),
    PLAYLIST_VERIFICATION("Verifica manifest HLS (.m3u8)"),
    PLAYER_PLAYBACK("Riproduzione stream ExoPlayer")
}

/**
 * Classificazione granulare degli errori VixSrc per distinguere errori HTTP,
 * errori applicativi/parsing, errori di rete ed errori del player.
 */
enum class VixSrcErrorCategory {
    HTTP_BAD_REQUEST,
    HTTP_UNAUTHORIZED,
    HTTP_FORBIDDEN,
    HTTP_NOT_FOUND,
    HTTP_RATE_LIMITED,
    HTTP_SERVER_ERROR,
    HTTP_OTHER,
    INVALID_CONTENT_TYPE,
    JSON_PARSE_ERROR,
    PLAYLIST_EXTRACTION_ERROR,
    INVALID_PLAYLIST_MANIFEST,
    NETWORK_TIMEOUT,
    NETWORK_ERROR,
    PLAYER_PLAYBACK_ERROR
}

/**
 * Evento diagnostico privo di credenziali, cookie o token sensibili.
 */
@Immutable
data class VixSrcDiagnosticEvent(
    val phase: VixSrcResolutionPhase,
    val method: String,
    val sanitizedUrl: String,
    val httpStatusCode: Int? = null,
    val responseTimeMs: Long = 0L,
    val redirectCount: Int = 0,
    val redirectChain: List<String> = emptyList(),
    val contentType: String? = null,
    val category: VixSrcErrorCategory? = null,
    val errorDetail: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Eccezione strutturata emessa dal resolver VixSrc con diagnostica tecnica sicura
 * e messaggio utente leggibile che riflette la causa reale dell'errore.
 */
class VixSrcException(
    val diagnostic: VixSrcDiagnosticEvent,
    val userMessage: String,
    cause: Throwable? = null
) : IOException(userMessage, cause) {
    val httpStatusCode: Int? get() = diagnostic.httpStatusCode
    val category: VixSrcErrorCategory? get() = diagnostic.category
    val phase: VixSrcResolutionPhase get() = diagnostic.phase
}

/**
 * Utility di sanitizzazione e formattazione diagnostica VixSrc.
 * Garantisce che token, chiavi, firme, cookie e credenziali non vengano mai registrati nei log.
 */
object VixSrcDiagnostics {
    private const val TAG = "VixSrcDiagnostics"

    private val SENSITIVE_QUERY_KEYS = setOf(
        "token",
        "key",
        "sig",
        "signature",
        "auth",
        "authorization",
        "cookie",
        "session",
        "session_id",
        "phpsessid",
        "xsrf",
        "csrf",
        "secret",
        "pass",
        "password",
        "access_token",
        "refresh_token",
        "api_key",
        "apikey",
        "asn"
    )

    /**
     * Restituisce un URL sicuro per i log/diagnostica oscurando i valori dei parametri sensibili.
     */
    fun sanitizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed.substringBefore('?')
        if (parsed.querySize == 0) return parsed.toString()

        val builder = parsed.newBuilder().query(null)
        for (i in 0 until parsed.querySize) {
            val name = parsed.queryParameterName(i)
            val value = parsed.queryParameterValue(i)
            val lowerName = name.lowercase(Locale.US)
            val isSensitive = lowerName in SENSITIVE_QUERY_KEYS ||
                lowerName.contains("token") ||
                lowerName.contains("secret") ||
                lowerName.contains("auth") ||
                lowerName.contains("cookie") ||
                lowerName.contains("key") ||
                lowerName.contains("sig")
            if (isSensitive) {
                builder.addQueryParameter(name, "<redacted>")
            } else if (value != null) {
                builder.addQueryParameter(name, value)
            }
        }
        return builder.build().toString()
    }

    /**
     * Valida che un URL estratto utilizzi esclusivamente schema `http` o `https`
     * (blocca `javascript:`, `data:`, `file:`, ecc.).
     */
    fun isSafeHttpUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return false
        return parsed.scheme.equals("https", ignoreCase = true) ||
            parsed.scheme.equals("http", ignoreCase = true)
    }

    /**
     * Estrae `scheme://host[:port]` da un URL HTTP valido per derivare dinamicamente `Origin`.
     */
    fun extractOrigin(rawUrl: String?, fallback: String = "https://vixsrc.to"): String {
        val parsed = rawUrl?.trim()?.toHttpUrlOrNull() ?: return fallback
        val isDefaultPort = (parsed.scheme == "https" && parsed.port == 443) ||
            (parsed.scheme == "http" && parsed.port == 80)
        return buildString {
            append(parsed.scheme).append("://").append(parsed.host)
            if (!isDefaultPort) {
                append(':').append(parsed.port)
            }
        }
    }

    fun classifyHttpStatus(code: Int): VixSrcErrorCategory = when (code) {
        400 -> VixSrcErrorCategory.HTTP_BAD_REQUEST
        401 -> VixSrcErrorCategory.HTTP_UNAUTHORIZED
        403 -> VixSrcErrorCategory.HTTP_FORBIDDEN
        404, 410 -> VixSrcErrorCategory.HTTP_NOT_FOUND
        429 -> VixSrcErrorCategory.HTTP_RATE_LIMITED
        in 500..599 -> VixSrcErrorCategory.HTTP_SERVER_ERROR
        else -> VixSrcErrorCategory.HTTP_OTHER
    }

    fun formatHttpUserMessage(code: Int, phase: VixSrcResolutionPhase): String = when (code) {
        400 -> "Richiesta non valida verso VixSrc (HTTP 400) durante: ${phase.label}."
        401 -> "Autorizzazione richiesta da VixSrc (HTTP 401) durante: ${phase.label}."
        403 -> "Accesso negato dal server VixSrc (HTTP 403) durante: ${phase.label}. Il token o l'accesso potrebbe essere limitato."
        404 -> when (phase) {
            VixSrcResolutionPhase.EMBED_PAGE, VixSrcResolutionPhase.API_LOOKUP ->
                "Contenuto non disponibile su VixSrc (HTTP 404)."
            else ->
                "Risorsa streaming non trovata su VixSrc (HTTP 404) durante: ${phase.label}."
        }
        410 -> "Link streaming VixSrc scaduto (HTTP 410). Premi Riprova per rigenerarlo."
        429 -> "Troppe richieste verso VixSrc (HTTP 429). Attendi qualche secondo e riprova."
        in 500..599 -> "Errore temporaneo del server VixSrc (HTTP $code) durante: ${phase.label}."
        else -> "Risposta HTTP inattesa da VixSrc (HTTP $code) durante: ${phase.label}."
    }

    fun classifyThrowable(throwable: Throwable): VixSrcErrorCategory = when (throwable) {
        is VixSrcException -> throwable.category ?: VixSrcErrorCategory.NETWORK_ERROR
        is SocketTimeoutException -> VixSrcErrorCategory.NETWORK_TIMEOUT
        is UnknownHostException, is IOException -> VixSrcErrorCategory.NETWORK_ERROR
        else -> VixSrcErrorCategory.PLAYLIST_EXTRACTION_ERROR
    }

    fun formatNetworkUserMessage(throwable: Throwable, phase: VixSrcResolutionPhase): String = when (throwable) {
        is VixSrcException -> throwable.userMessage
        is SocketTimeoutException ->
            "Timeout di connessione verso VixSrc durante: ${phase.label}. Verifica la rete e riprova."
        is UnknownHostException ->
            "Impossibile raggiungere vixsrc.to (errore DNS/rete) durante: ${phase.label}."
        else ->
            "Errore di rete verso VixSrc durante: ${phase.label} (${throwable.javaClass.simpleName})."
    }

    fun logEvent(event: VixSrcDiagnosticEvent) {
        val message = buildString {
            append("[phase=").append(event.phase.name)
            append("] ").append(event.method).append(' ').append(event.sanitizedUrl)
            event.httpStatusCode?.let { append(" -> HTTP ").append(it) }
            append(" (").append(event.responseTimeMs).append("ms")
            if (event.redirectCount > 0) {
                append(", redirects=").append(event.redirectCount)
            }
            if (!event.contentType.isNullOrBlank()) {
                append(", contentType=").append(event.contentType)
            }
            event.category?.let { append(", category=").append(it.name) }
            append(')')
            if (!event.errorDetail.isNullOrBlank()) {
                append(" | ").append(event.errorDetail)
            }
        }
        runCatching {
            if (event.category != null) {
                Log.w(TAG, message)
            } else {
                Log.d(TAG, message)
            }
        }
    }
}
