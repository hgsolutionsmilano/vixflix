package com.example.data.local.simkl

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.local.DEFAULT_PRIMARY_PROFILE_ID
import com.example.data.remote.simkl.SimklConnectionState

/**
 * Metadati non segreti dello stato di collegamento/sincronizzazione Simkl per singolo profilo utente.
 * NON contiene mai access_token o refresh_token (conservati esclusivamente in SimklSecureTokenStore).
 */
@Immutable
@Entity(
    tableName = "simkl_profile_sync_meta",
    indices = [Index(value = ["updatedAtMs"])]
)
data class SimklProfileSyncMetaEntity(
    @PrimaryKey val profileId: String = DEFAULT_PRIMARY_PROFILE_ID,
    val connectionState: String = SimklConnectionState.NOT_CONNECTED.name,
    val accountDisplayName: String? = null,
    val accountId: String? = null,
    val lastActivitiesAll: String? = null,
    val lastMoviesPlaybackTs: String? = null,
    val lastShowsPlaybackTs: String? = null,
    val lastMoviesWatchedTs: String? = null,
    val lastShowsWatchedTs: String? = null,
    val lastSyncAtMs: Long? = null,
    val lastErrorMessage: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Baseline per singolo contenuto (film o episodio) e singolo profilo, usata per la risoluzione
 * prudente dei conflitti tra progressi locali VixFlix e progressi remoti Simkl.
 */
@Immutable
@Entity(
    tableName = "simkl_playback_baseline",
    primaryKeys = ["profileId", "contentKey"],
    indices = [
        Index(value = ["profileId", "tmdbId", "mediaType"]),
        Index(value = ["profileId", "localDirty"])
    ]
)
data class SimklPlaybackBaselineEntity(
    val profileId: String = DEFAULT_PRIMARY_PROFILE_ID,
    val contentKey: String, // "movie_{tmdbId}" o "tv_{tmdbId}_s{season}_e{episode}"
    val tmdbId: Int,
    val mediaType: String, // "movie" | "tv"
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val simklPlaybackId: Long? = null,
    val lastSyncedProgressPercent: Double = 0.0,
    val lastRemotePausedAtMs: Long = 0L,
    val lastLocalUpdatedAtMs: Long = 0L,
    val localDirty: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Coda offline per eventi di scrobbling VixFlix -> Simkl in caso di rete assente o errori temporanei.
 * Ogni riga è coalesced per `(profileId, contentKey)` così da inviare lo stato più recente senza duplicati.
 */
@Immutable
@Entity(
    tableName = "simkl_pending_scrobble",
    primaryKeys = ["profileId", "contentKey"],
    indices = [
        Index(value = ["profileId", "nextRetryAtMs"]),
        Index(value = ["profileId", "updatedAtMs"])
    ]
)
data class SimklPendingScrobbleEntity(
    val profileId: String = DEFAULT_PRIMARY_PROFILE_ID,
    val contentKey: String, // "movie_{tmdbId}" o "tv_{tmdbId}_s{season}_e{episode}"
    val tmdbId: Int,
    val mediaType: String, // "movie" | "tv"
    val title: String,
    val releaseYear: String = "",
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val action: String, // "start" | "pause" | "stop" | "delete_playback"
    val progressPercent: Double,
    val minProgress: Int = 96,
    val simklPlaybackId: Long? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val nextRetryAtMs: Long = 0L
)
