package com.example.data.remote.simkl

import androidx.compose.runtime.Immutable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- DTO ufficiali Simkl API & AUTH V2 (KSP codegen, zero-reflection) ---

@JsonClass(generateAdapter = true)
data class SimklDeviceCodeResponse(
    @param:Json(name = "device_code") val deviceCode: String = "",
    @param:Json(name = "user_code") val userCode: String = "",
    @param:Json(name = "verification_uri") val verificationUri: String? = null,
    @param:Json(name = "verification_url") val verificationUrl: String? = null,
    @param:Json(name = "verification_uri_complete") val verificationUriComplete: String? = null,
    @param:Json(name = "expires_in") val expiresIn: Int = 900,
    @param:Json(name = "interval") val interval: Int = 5
) {
    val resolvedVerificationUri: String?
        get() = verificationUri?.takeIf { it.isNotBlank() }
            ?: verificationUrl?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class SimklTokenResponse(
    @param:Json(name = "access_token") val accessToken: String = "",
    @param:Json(name = "refresh_token") val refreshToken: String? = null,
    @param:Json(name = "token_type") val tokenType: String? = null,
    @param:Json(name = "expires_in") val expiresIn: Long? = null,
    @param:Json(name = "scope") val scope: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklOAuthErrorResponse(
    @param:Json(name = "error") val error: String? = null,
    @param:Json(name = "error_description") val errorDescription: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklUserSettingsResponse(
    @param:Json(name = "user") val user: SimklUserDto? = null,
    @param:Json(name = "account") val account: SimklAccountDto? = null
)

@JsonClass(generateAdapter = true)
data class SimklUserDto(
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "joined_at") val joinedAt: String? = null,
    @param:Json(name = "bio") val bio: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklAccountDto(
    @param:Json(name = "id") val id: Long? = null,
    @param:Json(name = "type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklActivitiesResponse(
    @param:Json(name = "all") val all: String? = null,
    @param:Json(name = "movies") val movies: SimklActivityBucketDto? = null,
    @param:Json(name = "shows") val shows: SimklActivityBucketDto? = null,
    @param:Json(name = "tv_shows") val tvShows: SimklActivityBucketDto? = null
) {
    val moviesPlaybackTimestamp: String?
        get() = movies?.playback ?: movies?.all
    val showsPlaybackTimestamp: String?
        get() = shows?.playback ?: tvShows?.playback ?: shows?.all ?: tvShows?.all
    val moviesWatchedTimestamp: String?
        get() = movies?.watchedAt ?: movies?.completed ?: movies?.all
    val showsWatchedTimestamp: String?
        get() = shows?.watchedAt ?: shows?.completed ?: tvShows?.watchedAt ?: shows?.all
}

@JsonClass(generateAdapter = true)
data class SimklActivityBucketDto(
    @param:Json(name = "all") val all: String? = null,
    @param:Json(name = "playback") val playback: String? = null,
    @param:Json(name = "watched_at") val watchedAt: String? = null,
    @param:Json(name = "completed") val completed: String? = null,
    @param:Json(name = "removed_from_list") val removedFromList: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklIdsDto(
    @param:Json(name = "simkl") val simkl: Long? = null,
    @param:Json(name = "tmdb") val tmdb: Int? = null,
    @param:Json(name = "imdb") val imdb: String? = null,
    @param:Json(name = "tvdb") val tvdb: Int? = null,
    @param:Json(name = "slug") val slug: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklMovieDto(
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "year") val year: Int? = null,
    @param:Json(name = "ids") val ids: SimklIdsDto? = null
)

@JsonClass(generateAdapter = true)
data class SimklShowDto(
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "year") val year: Int? = null,
    @param:Json(name = "ids") val ids: SimklIdsDto? = null
)

@JsonClass(generateAdapter = true)
data class SimklEpisodeDto(
    @param:Json(name = "season") val season: Int? = null,
    @param:Json(name = "number") val number: Int? = null,
    @param:Json(name = "episode") val episodeAlt: Int? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "ids") val ids: SimklIdsDto? = null
) {
    val resolvedEpisodeNumber: Int?
        get() = number?.takeIf { it > 0 } ?: episodeAlt?.takeIf { it > 0 }
}

@JsonClass(generateAdapter = true)
data class SimklPlaybackItemDto(
    @param:Json(name = "id") val id: Long? = null,
    @param:Json(name = "progress") val progress: Double = 0.0,
    @param:Json(name = "paused_at") val pausedAt: String? = null,
    @param:Json(name = "type") val type: String? = null,
    @param:Json(name = "movie") val movie: SimklMovieDto? = null,
    @param:Json(name = "show") val show: SimklShowDto? = null,
    @param:Json(name = "episode") val episode: SimklEpisodeDto? = null
)

@JsonClass(generateAdapter = true)
data class SimklScrobbleRequest(
    @param:Json(name = "progress") val progress: Double,
    @param:Json(name = "movie") val movie: SimklMovieDto? = null,
    @param:Json(name = "show") val show: SimklShowDto? = null,
    @param:Json(name = "episode") val episode: SimklEpisodeDto? = null
)

@JsonClass(generateAdapter = true)
data class SimklScrobbleResponse(
    @param:Json(name = "id") val id: Long? = null,
    @param:Json(name = "action") val action: String? = null,
    @param:Json(name = "progress") val progress: Double? = null
)

// --- Modelli di stato dominio / UI ---

enum class SimklConnectionState {
    NOT_CONNECTED,
    AUTHORIZATION_WAITING,
    AUTHORIZING_POLLING,
    CONNECTED_SYNCED,
    SYNCING,
    OFFLINE_QUEUE_PENDING,
    AUTH_EXPIRED,
    ERROR
}

@Immutable
data class SimklQrMatrix(
    val size: Int,
    val modules: List<Boolean>
) {
    operator fun get(row: Int, col: Int): Boolean {
        if (row !in 0 until size || col !in 0 until size) return false
        return modules[row * size + col]
    }
}

@Immutable
data class SimklQrDialogState(
    val profileId: String,
    val isLoading: Boolean = true,
    val verificationUrl: String? = null,
    val userCode: String? = null,
    val expiresAtMs: Long = 0L,
    val remainingSeconds: Int = 0,
    val pollIntervalSeconds: Int = 5,
    val statusMessage: String = "Preparazione codice Simkl…",
    val errorMessage: String? = null,
    val isSlowDownActive: Boolean = false,
    val isExpired: Boolean = false,
    val isDenied: Boolean = false,
    val isCompleted: Boolean = false,
    val qrMatrix: SimklQrMatrix? = null
) {
    val canRegenerate: Boolean
        get() = !isLoading && !isCompleted
}

@Immutable
data class SimklProfileUiState(
    val profileId: String = "",
    val connectionState: SimklConnectionState = SimklConnectionState.NOT_CONNECTED,
    val accountDisplayName: String? = null,
    val accountId: String? = null,
    val lastSyncAtMs: Long? = null,
    val pendingQueueCount: Int = 0,
    val errorMessage: String? = null,
    val isClientIdConfigured: Boolean = false,
    val qrDialogState: SimklQrDialogState? = null
) {
    val isConnected: Boolean
        get() = connectionState == SimklConnectionState.CONNECTED_SYNCED ||
            connectionState == SimklConnectionState.SYNCING ||
            connectionState == SimklConnectionState.OFFLINE_QUEUE_PENDING
}
