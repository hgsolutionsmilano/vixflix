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
    val lastAnimePlaybackTs: String? = null,
    val lastMoviesWatchedTs: String? = null,
    val lastShowsWatchedTs: String? = null,
    val lastAnimeWatchedTs: String? = null,
    val lastShowsAllTs: String? = null,
    val lastMoviesAllTs: String? = null,
    val lastAnimeAllTs: String? = null,
    val lastRemovedShowsTs: String? = null,
    val lastRemovedMoviesTs: String? = null,
    val lastRemovedAnimeTs: String? = null,
    val activitiesSnapshotJson: String? = null,
    val initialFullImportDone: Boolean = false,
    val lastFullReconcileAtMs: Long? = null,
    val lastSyncAtMs: Long? = null,
    val lastErrorMessage: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Elemento della libreria Simkl (serie TV, film o anime) persistito localmente per profilo.
 * Indicizzato con chiave stabile basata sull'ID Simkl per garantire upsert privo di duplicati
 * anche per titoli privi di corrispondenza TMDB.
 */
@Immutable
@Entity(
    tableName = "simkl_library_item",
    primaryKeys = ["profileId", "stableKey"],
    indices = [
        Index(value = ["profileId", "simklId"]),
        Index(value = ["profileId", "mediaCategory", "status"]),
        Index(value = ["profileId", "tmdbId"])
    ]
)
data class SimklLibraryItemEntity(
    val profileId: String = DEFAULT_PRIMARY_PROFILE_ID,
    val stableKey: String, // "simkl_{category}_{simklId}" oppure fallback esterno stabile
    val simklId: Long = 0L,
    val mediaCategory: String, // "shows" | "movies" | "anime"
    val tmdbMediaType: String, // "tv" | "movie"
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val slug: String? = null,
    val title: String,
    val year: Int? = null,
    val status: String, // "watching" | "plantowatch" | "hold" | "completed" | "dropped"
    val watchedEpisodesCount: Int = 0,
    val totalEpisodesCount: Int = 0,
    val lastWatchedAt: String? = null,
    val lastWatchedAtMs: Long = 0L,
    val nextToWatch: String? = null,
    val lastWatchedEpisodeCode: String? = null,
    val seasonsJson: String? = null,
    val tmdbResolved: Boolean = false,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String = "",
    val voteAverage: Double = 0.0,
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
    val contentKey: String, // "movie_{tmdbId}", "tv_{tmdbId}_s{season}_e{episode}" o chiave batch history
    val tmdbId: Int,
    val mediaType: String, // "movie" | "tv"
    val title: String,
    val releaseYear: String = "",
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val action: String, // "start" | "pause" | "stop" | "delete_playback" | "history_add" | "history_remove"
    val progressPercent: Double,
    val minProgress: Int = 96,
    val simklPlaybackId: Long? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val nextRetryAtMs: Long = 0L,
    val episodeTitle: String? = null,
    val simklId: Long? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val simklEpisodeId: Long? = null,
    val batchEpisodesJson: String? = null,
    val triggerEvent: String = ""
)

/**
 * Stato persistente per singolo episodio sincronizzato con Simkl per singolo profilo utente.
 * Consente aggiornamenti incrementali, rimozione dello stato visto (unwatch), episodi speciali
 * (stagione 0) e ricalcolo deterministico del progresso per stagione e serie.
 */
@Immutable
@Entity(
    tableName = "simkl_episode_state",
    primaryKeys = ["profileId", "seriesStableKey", "seasonNumber", "episodeNumber"],
    indices = [
        Index(value = ["profileId", "tmdbSeriesId"]),
        Index(value = ["profileId", "simklSeriesId"]),
        Index(value = ["profileId", "seriesStableKey"])
    ]
)
data class SimklEpisodeStateEntity(
    val profileId: String = DEFAULT_PRIMARY_PROFILE_ID,
    val seriesStableKey: String,
    val simklSeriesId: Long = 0L,
    val tmdbSeriesId: Int? = null,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val simklEpisodeId: Long? = null,
    val watched: Boolean = false,
    val watchedAt: String? = null,
    val watchedAtMs: Long = 0L,
    val updatedAtMs: Long = System.currentTimeMillis()
)
