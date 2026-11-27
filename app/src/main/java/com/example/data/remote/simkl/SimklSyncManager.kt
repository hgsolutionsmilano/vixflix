package com.example.data.remote.simkl

import com.example.data.local.CompletedMediaEntity
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.INITIAL_AVATAR_ID
import com.example.data.local.MAX_USER_PROFILES
import com.example.data.local.PlayerSegmentKeys
import com.example.data.local.ProfileCatalogDefaults
import com.example.data.local.WatchProgressEntity
import com.example.data.local.simkl.SimklEpisodeStateEntity
import com.example.data.local.simkl.SimklLibraryItemEntity
import com.example.data.local.simkl.SimklPendingScrobbleEntity
import com.example.data.local.simkl.SimklPlaybackBaselineEntity
import com.example.data.local.simkl.SimklProfileSyncMetaEntity
import com.example.data.local.simkl.SimklSecureTokenStore
import com.example.data.local.simkl.SimklTokenBundle
import com.example.data.remote.MediaItem
import com.example.data.repository.VixFlixRepository
import com.example.ui.components.SimklQrGenerator
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SimklSyncTrigger {
    STARTUP,
    FOREGROUND,
    PERIODIC,
    PROFILE_SWITCH,
    CONTINUE_WATCHING_OPEN,
    DETAIL_OPEN,
    PRE_PLAYBACK,
    POST_SCROBBLE,
    POST_AUTH,
    FORCE_FULL_RECONCILE,
    MANUAL
}

class SimklSyncManager(
    private val repository: VixFlixRepository,
    private val tokenStore: SimklSecureTokenStore,
    private val configProvider: () -> SimklConfig = { SimklConfig() },
    apiServiceOverride: SimklApiService? = null,
    private val minPostIntervalMs: Long = SimklConfig.MIN_POST_INTERVAL_MS,
    private val authPollMinIntervalMs: Long = 1_000L,
    private val syncCooldownMs: Long = 20_000L,
    private val periodicSyncIntervalMs: Long = DEFAULT_PERIODIC_SYNC_INTERVAL_MS,
    private val fullReconcileIntervalMs: Long = DEFAULT_FULL_RECONCILE_INTERVAL_MS,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val dao = repository.localDao
    private val errorAdapter = repository.moshi.adapter(SimklOAuthErrorResponse::class.java)
    private val activitiesAdapter = repository.moshi.adapter(SimklActivitiesResponse::class.java)

    @Volatile
    private var cachedApiKey: String? = null

    @Volatile
    private var cachedApiService: SimklApiService? = apiServiceOverride

    private val apiService: SimklApiService
        get() {
            cachedApiService?.let { return it }
            val cfg = configProvider()
            val key = "${cfg.apiBaseUrl}|${cfg.clientId}"
            val current = cachedApiService
            if (current != null && cachedApiKey == key) return current
            val okHttp = SimklApiService.createOkHttpClient(repository.sharedHttpClient, configProvider)
            return SimklApiService.create(okHttp, repository.moshi, cfg.apiBaseUrl).also {
                cachedApiKey = key
                cachedApiService = it
            }
        }

    private data class PendingAuthorizedAccount(
        val requestedProfileId: String,
        val flowMode: SimklAuthFlowMode,
        val bundle: SimklTokenBundle,
        val simklAccountId: String?,
        val simklDisplayName: String
    )

    data class ActivePlaybackTarget(
        val profileId: String,
        val mediaType: String,
        val tmdbId: Int,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null
    )

    private val _qrDialogState = MutableStateFlow<SimklQrDialogState?>(null)
    val globalQrDialogState: StateFlow<SimklQrDialogState?> = _qrDialogState.asStateFlow()

    private val _profileSyncProgress = MutableStateFlow<Map<String, SimklSyncProgress>>(emptyMap())
    private val _profileSyncSummary = MutableStateFlow<Map<String, SimklSyncSummary>>(emptyMap())
    private val _diagnosticLog = MutableStateFlow<List<SimklDiagnosticEntry>>(emptyList())
    val diagnosticLog: StateFlow<List<SimklDiagnosticEntry>> = _diagnosticLog.asStateFlow()
    private val _apiMetrics = MutableStateFlow(SimklApiMetricsSnapshot())
    val apiMetrics: StateFlow<SimklApiMetricsSnapshot> = _apiMetrics.asStateFlow()

    fun resetApiMetrics() {
        _apiMetrics.value = SimklApiMetricsSnapshot()
    }

    private val _resolvedActiveProfileRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val resolvedActiveProfileRequests: SharedFlow<String> = _resolvedActiveProfileRequests.asSharedFlow()

    private val _syncRevision = MutableStateFlow(0L)
    val syncRevision: StateFlow<Long> = _syncRevision.asStateFlow()

    @Volatile
    private var pendingAuthorizedAccount: PendingAuthorizedAccount? = null

    @Volatile
    private var activePlaybackTarget: ActivePlaybackTarget? = null

    private val periodicSyncLock = Any()
    @Volatile
    private var isAppInForeground: Boolean = false
    @Volatile
    private var periodicSyncProfileId: String? = null
    private var periodicSyncJob: Job? = null

    private val profileTokenMutexes = ConcurrentHashMap<String, Mutex>()
    private val profileSyncMutexes = ConcurrentHashMap<String, Mutex>()
    private val profileWriteMutexes = ConcurrentHashMap<String, Mutex>()
    private val activeAuthJobs = ConcurrentHashMap<String, Job>()
    private val activeSyncJobs = ConcurrentHashMap<String, Job>()
    private val activeWriteJobsByProfile = ConcurrentHashMap<String, MutableSet<Job>>()
    private val pendingFollowUpTriggers = ConcurrentHashMap<String, SimklSyncTrigger>()
    private val postScrobbleSyncJobs = ConcurrentHashMap<String, Job>()
    private val lastSyncAttemptAtMs = ConcurrentHashMap<String, Long>()
    private val lastPostAtByProfile = ConcurrentHashMap<String, Long>()
    private val consecutiveUnchangedPeriodicByProfile = ConcurrentHashMap<String, Int>()
    private val consecutiveErrorPeriodicByProfile = ConcurrentHashMap<String, Int>()
    private val rateLimitUntilMsByProfile = ConcurrentHashMap<String, Long>()
    private val deferredPlaybackMoviesProfiles = ConcurrentHashMap.newKeySet<String>()
    private val deferredPlaybackEpisodesProfiles = ConcurrentHashMap.newKeySet<String>()
    private val deferredLibrarySeriesByProfile = ConcurrentHashMap<String, MutableSet<Int>>()
    private val lastSentScrobbleByProfileKey = ConcurrentHashMap<String, Triple<String, Double, Long>>()
    private val excludedContinueWatchingStatuses = setOf(
        SimklLibraryJsonParser.STATUS_HOLD,
        SimklLibraryJsonParser.STATUS_DROPPED,
        SimklLibraryJsonParser.STATUS_COMPLETED,
        SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH
    )

    private fun trackWriteJob(profileId: String, job: Job) {
        val set = activeWriteJobsByProfile.getOrPut(profileId) {
            java.util.Collections.newSetFromMap(ConcurrentHashMap<Job, Boolean>())
        }
        set.add(job)
        job.invokeOnCompletion { set.remove(job) }
    }

    private fun cancelWriteJobsForProfile(profileId: String) {
        postScrobbleSyncJobs.remove(profileId)?.cancel()
        val jobs = activeWriteJobsByProfile.remove(profileId)
        jobs?.forEach { it.cancel() }
    }

    private fun triggerPriority(trigger: SimklSyncTrigger): Int = when (trigger) {
        SimklSyncTrigger.POST_AUTH -> 100
        SimklSyncTrigger.FORCE_FULL_RECONCILE -> 95
        SimklSyncTrigger.MANUAL -> 90
        SimklSyncTrigger.PRE_PLAYBACK -> 80
        SimklSyncTrigger.POST_SCROBBLE -> 70
        SimklSyncTrigger.FOREGROUND -> 60
        SimklSyncTrigger.PROFILE_SWITCH -> 55
        SimklSyncTrigger.CONTINUE_WATCHING_OPEN -> 50
        SimklSyncTrigger.DETAIL_OPEN -> 45
        SimklSyncTrigger.STARTUP -> 40
        SimklSyncTrigger.PERIODIC -> 30
    }

    private fun scheduleFollowUpTriggerIfHigher(profileId: String, trigger: SimklSyncTrigger) {
        if (trigger == SimklSyncTrigger.PERIODIC) return
        pendingFollowUpTriggers.compute(profileId) { _, existing ->
            if (existing == null || triggerPriority(trigger) >= triggerPriority(existing)) trigger else existing
        }
    }

    private fun scheduleDebouncedPostScrobbleSync(profileId: String) {
        if (activePlaybackTarget?.profileId == profileId) return
        postScrobbleSyncJobs.remove(profileId)?.cancel()
        val job = scope.launch {
            delay(1_500L)
            if (activePlaybackTarget?.profileId == profileId) return@launch
            if (!tokenStore.hasTokens(profileId)) return@launch
            launchProfileSync(profileId, SimklSyncTrigger.POST_SCROBBLE)
        }
        postScrobbleSyncJobs[profileId] = job
    }

    fun setActivePlaybackTarget(
        profileId: String,
        mediaType: String,
        tmdbId: Int,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        activePlaybackTarget = ActivePlaybackTarget(
            profileId = profileId,
            mediaType = mediaType,
            tmdbId = tmdbId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    fun clearActivePlaybackTarget() {
        val prev = activePlaybackTarget
        activePlaybackTarget = null
        if (prev != null && prev.mediaType == "tv") {
            val wasDeferred = deferredLibrarySeriesByProfile[prev.profileId]?.remove(prev.tmdbId) == true
            if (wasDeferred) {
                scope.launch {
                    reconcileTvContinueWatchingInvariant(prev.profileId, setOf(prev.tmdbId))
                    _syncRevision.update { it + 1L }
                }
            }
        }
    }

    private fun markDeferredLibrarySeries(profileId: String, tmdbId: Int) {
        if (tmdbId <= 0) return
        val set = deferredLibrarySeriesByProfile.getOrPut(profileId) {
            java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
        }
        set.add(tmdbId)
    }

    internal fun isActivePlaybackTarget(
        profileId: String,
        mediaType: String,
        tmdbId: Int,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Boolean {
        val target = activePlaybackTarget ?: return false
        if (target.profileId != profileId || target.mediaType != mediaType || target.tmdbId != tmdbId) {
            return false
        }
        if (mediaType != "tv") return true
        return seasonNumber == null || episodeNumber == null ||
            (target.seasonNumber == seasonNumber && target.episodeNumber == episodeNumber)
    }

    internal fun isActivePlaybackSeries(profileId: String, tmdbId: Int): Boolean {
        val target = activePlaybackTarget ?: return false
        return target.profileId == profileId && target.mediaType == "tv" && target.tmdbId == tmdbId
    }

    fun getRecentDiagnostics(profileId: String? = null): List<SimklDiagnosticEntry> {
        val all = _diagnosticLog.value
        return if (profileId.isNullOrBlank()) all else all.filter { it.profileId == profileId }
    }

    fun clearDiagnostics() {
        _diagnosticLog.value = emptyList()
    }

    private fun recordApiCallMetric(
        endpointKey: String,
        httpCode: Int? = null,
        isMaxItems400: Boolean = false,
        isRetry: Boolean = false
    ) {
        _apiMetrics.update { cur ->
            val updatedMap = cur.callsByEndpoint.toMutableMap()
            updatedMap[endpointKey] = (updatedMap[endpointKey] ?: 0) + 1
            cur.copy(
                totalCallsSession = cur.totalCallsSession + 1,
                callsByEndpoint = updatedMap,
                rateLimit429Count = cur.rateLimit429Count + if (httpCode == 429) 1 else 0,
                maxItems400Count = cur.maxItems400Count + if (isMaxItems400) 1 else 0,
                retriesCount = cur.retriesCount + if (isRetry) 1 else 0
            )
        }
    }

    private fun isMaxItemsError(httpCode: Int, errorBody: String?): Boolean {
        if (httpCode == 413) return true
        if (httpCode != 400) return false
        val body = errorBody?.lowercase().orEmpty()
        return body.contains("max_items") ||
            body.contains("too many") ||
            body.contains("max items") ||
            body.contains("item limit")
    }

    private fun parseStoredActivitiesSnapshot(rawJson: String?): Pair<SimklActivitiesResponse?, Boolean> {
        if (rawJson.isNullOrBlank()) return null to false
        val parsed = runCatching { activitiesAdapter.fromJson(rawJson) }.getOrNull()
        return if (parsed == null) {
            null to true
        } else {
            parsed to false
        }
    }

    private data class ActivitiesFetchResult(
        val activities: SimklActivitiesResponse? = null,
        val rawJson: String? = null,
        val httpCode: Int? = null,
        val authLost: Boolean = false,
        val isRateLimited: Boolean = false,
        val errorEntry: SimklSyncErrorEntry? = null
    )

    private suspend fun fetchAndParseActivities(
        profileId: String,
        initialToken: String,
        triggerEvent: String
    ): ActivitiesFetchResult {
        val startedMs = clockMs()
        val resp = try {
            executeWithAuthRetry(profileId, initialToken) { tok ->
                apiService.getSyncActivities("Bearer $tok")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val dur = (clockMs() - startedMs).coerceAtLeast(0L)
            recordApiCallMetric("GET /sync/activities")
            recordDiagnostic(
                endpoint = "GET /sync/activities",
                contentType = "activities",
                profileId = profileId,
                triggerEvent = triggerEvent,
                outcome = "NETWORK_ERROR",
                errorDetail = e.javaClass.simpleName,
                operationType = "ACTIVITIES",
                durationMs = dur
            )
            return ActivitiesFetchResult(
                errorEntry = SimklSyncErrorEntry(
                    source = "/sync/activities",
                    errorType = e.javaClass.simpleName,
                    message = "Errore di rete su /sync/activities."
                )
            )
        } ?: run {
            val dur = (clockMs() - startedMs).coerceAtLeast(0L)
            recordApiCallMetric("GET /sync/activities", httpCode = 401)
            recordDiagnostic(
                endpoint = "GET /sync/activities",
                contentType = "activities",
                profileId = profileId,
                triggerEvent = triggerEvent,
                httpCode = 401,
                outcome = "AUTH_FAILED",
                operationType = "ACTIVITIES",
                durationMs = dur
            )
            return ActivitiesFetchResult(
                httpCode = 401,
                authLost = true,
                errorEntry = SimklSyncErrorEntry(
                    source = "/sync/activities",
                    httpCode = 401,
                    errorType = "AUTH",
                    message = "Autorizzazione Simkl non valida o scaduta."
                )
            )
        }

        val dur = (clockMs() - startedMs).coerceAtLeast(0L)
        val code = resp.code()
        recordApiCallMetric("GET /sync/activities", httpCode = code)

        if (!resp.isSuccessful) {
            val errRaw = runCatching { resp.errorBody()?.string() }.getOrNull()
            if (code == 429) {
                val retryAfterMs = parseRetryAfterMs(resp.headers()["Retry-After"], 0)
                rateLimitUntilMsByProfile[profileId] = clockMs() + retryAfterMs
            }
            recordDiagnostic(
                endpoint = "GET /sync/activities",
                contentType = "activities",
                profileId = profileId,
                triggerEvent = triggerEvent,
                httpCode = code,
                rawResponse = errRaw,
                outcome = if (code == 429) "RATE_LIMITED_429" else "HTTP_ERROR",
                errorDetail = "Errore attività Simkl (HTTP $code)",
                operationType = "ACTIVITIES",
                durationMs = dur
            )
            return ActivitiesFetchResult(
                httpCode = code,
                isRateLimited = code == 429,
                errorEntry = SimklSyncErrorEntry(
                    source = "/sync/activities",
                    httpCode = code,
                    errorType = "HTTP_$code",
                    message = "Errore attività Simkl (HTTP $code)"
                )
            )
        }

        val rawJson = runCatching { resp.body()?.use { it.string() } }.getOrNull()
        val respBytes = rawJson?.toByteArray()?.size?.toLong()
        val parsed = if (!rawJson.isNullOrBlank()) {
            runCatching { activitiesAdapter.fromJson(rawJson) }.getOrNull()
        } else {
            null
        }
        if (parsed == null) {
            recordDiagnostic(
                endpoint = "GET /sync/activities",
                contentType = "activities",
                profileId = profileId,
                triggerEvent = triggerEvent,
                httpCode = code,
                rawResponse = rawJson,
                outcome = "PARSE_ERROR",
                errorDetail = "Risposta /sync/activities non valida o vuota.",
                operationType = "ACTIVITIES",
                durationMs = dur,
                responseBytes = respBytes
            )
            return ActivitiesFetchResult(
                httpCode = code,
                errorEntry = SimklSyncErrorEntry(
                    source = "/sync/activities",
                    httpCode = code,
                    errorType = "PARSE_ERROR",
                    message = "Risposta /sync/activities non valida."
                )
            )
        }

        recordDiagnostic(
            endpoint = "GET /sync/activities",
            contentType = "activities",
            profileId = profileId,
            triggerEvent = triggerEvent,
            httpCode = code,
            rawResponse = "all=${parsed.all},shows=${parsed.showsAllTimestamp},movies=${parsed.moviesAllTimestamp},anime=${parsed.animeAllTimestamp}",
            outcome = "OK",
            operationType = "ACTIVITIES",
            durationMs = dur,
            responseBytes = respBytes
        )
        return ActivitiesFetchResult(
            activities = parsed,
            rawJson = rawJson,
            httpCode = code
        )
    }

    private fun recordDiagnostic(
        endpoint: String,
        contentType: String,
        profileId: String,
        triggerEvent: String,
        outcome: String,
        simklId: Long? = null,
        tmdbId: Int? = null,
        httpCode: Int? = null,
        rawResponse: String? = null,
        errorDetail: String? = null,
        operationType: String = "",
        durationMs: Long = 0L,
        retryCount: Int = 0,
        responseBytes: Long? = null,
        itemsCount: Int? = null
    ) {
        val entry = SimklDiagnosticEntry(
            timestampMs = clockMs(),
            endpoint = endpoint,
            contentType = contentType,
            simklId = simklId,
            tmdbId = tmdbId,
            profileId = profileId,
            triggerEvent = triggerEvent,
            httpCode = httpCode,
            sanitizedResponse = sanitizeResponseForDiagnostic(rawResponse),
            outcome = outcome,
            errorDetail = sanitizeResponseForDiagnostic(errorDetail),
            operationType = operationType,
            durationMs = durationMs,
            retryCount = retryCount,
            responseBytes = responseBytes,
            itemsCount = itemsCount
        )
        _diagnosticLog.update { current ->
            val updated = if (current.size >= 150) current.drop(current.size - 149) + entry else current + entry
            updated
        }
    }

    internal fun sanitizeResponseForDiagnostic(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val redacted = trimmed
            .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [REDACTED]")
            .replace(Regex("(?i)\"(access_token|refresh_token|client_id|client_secret|token)\"\\s*:\\s*\"[^\"]+\""), "\"$1\":\"[REDACTED]\"")
            .replace(Regex("(?i)(client_id|access_token|refresh_token)=[^&\\s]+"), "$1=[REDACTED]")
        return if (redacted.length > 220) redacted.take(220) + "…" else redacted
    }

    private fun tokenMutexFor(profileId: String): Mutex =
        profileTokenMutexes.getOrPut(profileId) { Mutex() }

    private fun syncMutexFor(profileId: String): Mutex =
        profileSyncMutexes.getOrPut(profileId) { Mutex() }

    private fun writeMutexFor(profileId: String): Mutex =
        profileWriteMutexes.getOrPut(profileId) { Mutex() }

    private fun updateSyncProgress(profileId: String, progress: SimklSyncProgress?) {
        _profileSyncProgress.update { map ->
            if (progress == null) map - profileId else map + (profileId to progress)
        }
    }

    private fun updateSyncSummary(profileId: String, summary: SimklSyncSummary?) {
        _profileSyncSummary.update { map ->
            if (summary == null) map - profileId else map + (profileId to summary)
        }
    }

    fun observeProfileUiState(profileId: String): Flow<SimklProfileUiState> {
        val baseFlow = combine(
            dao.observeSimklProfileMeta(profileId),
            dao.observeSimklPendingCount(profileId),
            _qrDialogState,
            _profileSyncProgress,
            _profileSyncSummary
        ) { meta, pendingCount, qrState, progressMap, summaryMap ->
            val cfg = configProvider()
            val matchingQr = qrState?.takeIf { it.profileId == profileId }
            val rawState = meta?.connectionState?.let { raw ->
                runCatching { SimklConnectionState.valueOf(raw) }.getOrNull()
            } ?: SimklConnectionState.NOT_CONNECTED

            val resolvedState = when {
                matchingQr != null && !matchingQr.isCompleted && matchingQr.errorMessage == null && !matchingQr.isExpired -> {
                    if (matchingQr.userCode != null) {
                        SimklConnectionState.AUTHORIZING_POLLING
                    } else {
                        SimklConnectionState.AUTHORIZATION_WAITING
                    }
                }
                rawState == SimklConnectionState.CONNECTED_SYNCED && pendingCount > 0 ->
                    SimklConnectionState.OFFLINE_QUEUE_PENDING
                else -> rawState
            }

            SimklProfileUiState(
                profileId = profileId,
                connectionState = resolvedState,
                accountDisplayName = meta?.accountDisplayName,
                accountId = meta?.accountId,
                lastSyncAtMs = meta?.lastSyncAtMs,
                pendingQueueCount = pendingCount,
                errorMessage = meta?.lastErrorMessage,
                isClientIdConfigured = cfg.isClientIdConfigured,
                qrDialogState = matchingQr,
                syncProgress = progressMap[profileId],
                lastSyncSummary = summaryMap[profileId]
            )
        }
        return combine(baseFlow, _apiMetrics) { uiState, metrics ->
            uiState.copy(apiMetrics = metrics)
        }
    }

    fun onActiveProfileChanged(previousProfileId: String?, newProfileId: String) {
        if (!previousProfileId.isNullOrBlank() && previousProfileId != newProfileId) {
            activeAuthJobs.remove(previousProfileId)?.cancel()
            activeSyncJobs.remove(previousProfileId)?.cancel()
            pendingFollowUpTriggers.remove(previousProfileId)
            cancelWriteJobsForProfile(previousProfileId)
            if (_qrDialogState.value?.profileId == previousProfileId) {
                _qrDialogState.value = null
            }
            if (activePlaybackTarget?.profileId == previousProfileId) {
                activePlaybackTarget = null
            }
        }
        syncIfNeeded(newProfileId, SimklSyncTrigger.PROFILE_SWITCH)
        retargetPeriodicLoopIfForeground(newProfileId, runImmediateCatchUp = false)
    }

    /**
     * Avvia o riprende la sincronizzazione automatica periodica Simkl (ogni 5 minuti / 300s)
     * finché VixFlix rimane in primo piano, indipendentemente dalla schermata corrente.
     * - Evita timer duplicati se già attivo per lo stesso profilo.
     * - Al ritorno dal background verifica gli aggiornamenti mancati ed esegue un catch-up immediato se necessario.
     */
    fun startForegroundPeriodicSync(profileId: String) {
        if (profileId.isBlank()) return
        synchronized(periodicSyncLock) {
            val alreadyRunningForProfile = isAppInForeground &&
                periodicSyncProfileId == profileId &&
                periodicSyncJob?.isActive == true
            if (alreadyRunningForProfile) {
                return
            }
            isAppInForeground = true
            periodicSyncProfileId = profileId
            periodicSyncJob?.cancel()
            periodicSyncJob = launchPeriodicSyncLoopLocked(
                profileId = profileId,
                runImmediateCatchUp = true
            )
        }
    }

    /**
     * Aggiorna il profilo monitorato dal polling automatico solo se l'app è attualmente in primo piano,
     * senza avviare timer in background o creare loop duplicati.
     */
    fun updatePeriodicSyncProfileIfForeground(profileId: String) {
        if (profileId.isBlank()) return
        retargetPeriodicLoopIfForeground(profileId, runImmediateCatchUp = true)
    }

    /**
     * Sospende il polling periodico quando VixFlix passa in background.
     */
    fun pauseForegroundPeriodicSync() {
        synchronized(periodicSyncLock) {
            isAppInForeground = false
            periodicSyncJob?.cancel()
            periodicSyncJob = null
        }
    }

    /**
     * Arresta definitivamente il polling periodico (es. chiusura ViewModel).
     */
    fun stopPeriodicSync() {
        synchronized(periodicSyncLock) {
            isAppInForeground = false
            periodicSyncProfileId = null
            periodicSyncJob?.cancel()
            periodicSyncJob = null
        }
    }

    internal fun isPeriodicSyncActiveForTest(): Boolean = synchronized(periodicSyncLock) {
        isAppInForeground && periodicSyncJob?.isActive == true
    }

    private fun retargetPeriodicLoopIfForeground(profileId: String, runImmediateCatchUp: Boolean) {
        synchronized(periodicSyncLock) {
            if (!isAppInForeground) return
            if (periodicSyncProfileId == profileId && periodicSyncJob?.isActive == true) {
                return
            }
            periodicSyncProfileId = profileId
            periodicSyncJob?.cancel()
            periodicSyncJob = launchPeriodicSyncLoopLocked(
                profileId = profileId,
                runImmediateCatchUp = runImmediateCatchUp
            )
        }
    }

    private fun computeAdaptivePeriodicDelayMs(profileId: String): Long {
        val base = periodicSyncIntervalMs.coerceAtLeast(100L)
        val now = clockMs()
        val rateLimitWait = ((rateLimitUntilMsByProfile[profileId] ?: 0L) - now).coerceAtLeast(0L)
        val errCount = consecutiveErrorPeriodicByProfile[profileId] ?: 0
        if (errCount > 0) {
            val expDelay = (base * (1L shl errCount.coerceAtMost(3)))
                .coerceAtMost(maxOf(base * 4L, 15L * 60_000L))
            return maxOf(expDelay, rateLimitWait)
        }
        val unchangedCount = consecutiveUnchangedPeriodicByProfile[profileId] ?: 0
        val adaptiveDelay = when {
            unchangedCount <= 0 -> base
            unchangedCount == 1 -> (base * 3L) / 2L
            unchangedCount == 2 -> base * 2L
            else -> (base * 3L).coerceAtMost(maxOf(base * 3L, 15L * 60_000L))
        }
        return maxOf(adaptiveDelay, rateLimitWait)
    }

    private fun launchPeriodicSyncLoopLocked(
        profileId: String,
        runImmediateCatchUp: Boolean
    ): Job {
        return scope.launch {
            if (runImmediateCatchUp) {
                runPeriodicCycleSafe(profileId, SimklSyncTrigger.FOREGROUND, checkCatchUp = true)
            }
            while (isAppInForeground && periodicSyncProfileId == profileId) {
                val nextDelayMs = computeAdaptivePeriodicDelayMs(profileId)
                delay(nextDelayMs)
                if (!isAppInForeground || periodicSyncProfileId != profileId) break
                runPeriodicCycleSafe(profileId, SimklSyncTrigger.PERIODIC, checkCatchUp = false)
            }
        }
    }

    private suspend fun runPeriodicCycleSafe(
        profileId: String,
        trigger: SimklSyncTrigger,
        checkCatchUp: Boolean
    ) {
        try {
            if (!configProvider().isClientIdConfigured) return
            if (!tokenStore.hasTokens(profileId)) return
            val now = clockMs()
            val rateLimitUntil = rateLimitUntilMsByProfile[profileId] ?: 0L
            if (rateLimitUntil > now) return
            // Evita sincronizzazioni concorrenti se è già in corso un sync per lo stesso profilo
            if (activeSyncJobs[profileId]?.isActive == true) return

            if (checkCatchUp) {
                val meta = dao.getSimklProfileMeta(profileId)
                val lastAttempt = lastSyncAttemptAtMs[profileId] ?: 0L
                val lastSynced = meta?.lastSyncAtMs ?: 0L
                val needsInitialImport = meta == null || !meta.initialFullImportDone
                val periodicOverdue = lastSynced <= 0L || (now - lastSynced) >= periodicSyncIntervalMs
                val foregroundCooldown = if (syncCooldownMs > 0L) minOf(syncCooldownMs, 15_000L) else 0L
                val foregroundCooldownElapsed = (now - lastAttempt) >= foregroundCooldown
                if (!needsInitialImport && !periodicOverdue && !foregroundCooldownElapsed) {
                    return
                }
            }

            syncProfileNow(profileId, trigger)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            consecutiveErrorPeriodicByProfile.compute(profileId) { _, cur -> (cur ?: 0) + 1 }
            recordDiagnostic(
                endpoint = "/sync/activities",
                contentType = "periodic",
                profileId = profileId,
                triggerEvent = trigger.name,
                outcome = "PERIODIC_ERROR_WILL_RETRY",
                errorDetail = e.localizedMessage ?: e::class.java.simpleName,
                operationType = "PERIODIC"
            )
        }
    }

    fun startDeviceAuthorization(
        profileId: String,
        flowMode: SimklAuthFlowMode = SimklAuthFlowMode.LINK_ACTIVE_PROFILE
    ) {
        val cfg = configProvider()
        pendingAuthorizedAccount = null
        activeAuthJobs.values.forEach { it.cancel() }
        activeAuthJobs.clear()

        if (!cfg.isClientIdConfigured) {
            _qrDialogState.value = SimklQrDialogState(
                profileId = profileId,
                flowMode = flowMode,
                isLoading = false,
                isClientIdMissing = true,
                statusMessage = "Credenziale applicazione SIMKL_CLIENT_ID non configurata.",
                errorMessage = "Il client ID pubblico di VixFlix (SIMKL_CLIENT_ID) deve essere configurato internamente nei Secrets di build. Non inserire codici o ID personali qui."
            )
            return
        }

        val isProfileScoped = flowMode == SimklAuthFlowMode.LINK_ACTIVE_PROFILE &&
            profileId != SimklConfig.PROFILE_CREATION_PENDING_ID

        _qrDialogState.value = SimklQrDialogState(
            profileId = profileId,
            flowMode = flowMode,
            isLoading = true,
            statusMessage = "Richiesta codice di autorizzazione Simkl…"
        )

        val job = scope.launch {
            if (isProfileScoped) {
                updateMetaState(profileId, SimklConnectionState.AUTHORIZATION_WAITING, clearError = true)
            }
            val deviceResp = try {
                apiService.requestDeviceCode(
                    clientId = cfg.clientId,
                    scope = SimklConfig.DEFAULT_SCOPES
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "Impossibile contattare Simkl (${e.localizedMessage ?: "errore di rete"})."
                _qrDialogState.value = SimklQrDialogState(
                    profileId = profileId,
                    flowMode = flowMode,
                    isLoading = false,
                    statusMessage = "Errore di connessione",
                    errorMessage = msg
                )
                if (isProfileScoped) {
                    updateMetaState(profileId, SimklConnectionState.ERROR, error = msg)
                }
                return@launch
            }

            if (!deviceResp.isSuccessful || deviceResp.body() == null) {
                val errBody = parseOAuthError(deviceResp.errorBody()?.string())
                val msg = errBody?.errorDescription?.takeIf { it.isNotBlank() }
                    ?: "Simkl ha rifiutato l'avvio del collegamento (HTTP ${deviceResp.code()})."
                _qrDialogState.value = SimklQrDialogState(
                    profileId = profileId,
                    flowMode = flowMode,
                    isLoading = false,
                    statusMessage = "Autorizzazione non disponibile",
                    errorMessage = msg
                )
                if (isProfileScoped) {
                    updateMetaState(profileId, SimklConnectionState.ERROR, error = msg)
                }
                return@launch
            }

            val dto = deviceResp.body()!!
            val userCode = dto.userCode.trim()
            val normalizedComplete = cfg.normalizeVerificationUrl(dto.verificationUriComplete)
            val normalizedVerification = cfg.normalizeVerificationUrl(dto.resolvedVerificationUri)
            val qrTargetUrl = normalizedComplete ?: normalizedVerification
            val displayVerificationUrl = normalizedVerification ?: normalizedComplete

            if (dto.deviceCode.isBlank() || userCode.isBlank() || qrTargetUrl == null) {
                val msg = "Risposta Simkl non valida o URL di verifica non sicuro."
                _qrDialogState.value = SimklQrDialogState(
                    profileId = profileId,
                    flowMode = flowMode,
                    isLoading = false,
                    statusMessage = "Risposta non valida",
                    errorMessage = msg
                )
                if (isProfileScoped) {
                    updateMetaState(profileId, SimklConnectionState.ERROR, error = msg)
                }
                return@launch
            }

            val qrMatrix = runCatching { SimklQrGenerator.generate(qrTargetUrl) }.getOrNull()
                ?: displayVerificationUrl?.let { fallbackUrl ->
                    runCatching { SimklQrGenerator.generate(fallbackUrl) }.getOrNull()
                }
            if (qrMatrix == null) {
                val msg = "Impossibile generare il QR Code per l'URL Simkl."
                _qrDialogState.value = SimklQrDialogState(
                    profileId = profileId,
                    flowMode = flowMode,
                    isLoading = false,
                    verificationUrl = displayVerificationUrl ?: qrTargetUrl,
                    hasCompleteVerificationUri = normalizedComplete != null,
                    userCode = userCode,
                    statusMessage = "Errore generazione QR",
                    errorMessage = msg
                )
                if (isProfileScoped) {
                    updateMetaState(profileId, SimklConnectionState.ERROR, error = msg)
                }
                return@launch
            }

            val expiresInSec = dto.expiresIn.coerceAtLeast(30)
            var pollIntervalSec = dto.interval.coerceAtLeast(1)
            val expiresAt = clockMs() + (expiresInSec * 1000L)

            _qrDialogState.value = SimklQrDialogState(
                profileId = profileId,
                flowMode = flowMode,
                isLoading = false,
                verificationUrl = displayVerificationUrl ?: qrTargetUrl,
                hasCompleteVerificationUri = normalizedComplete != null,
                userCode = userCode,
                expiresAtMs = expiresAt,
                remainingSeconds = expiresInSec,
                pollIntervalSeconds = pollIntervalSec,
                statusMessage = "In attesa di autorizzazione dallo smartphone…",
                qrMatrix = qrMatrix
            )
            if (isProfileScoped) {
                updateMetaState(profileId, SimklConnectionState.AUTHORIZING_POLLING, clearError = true)
            }

            // Loop countdown + polling secondo RFC 8628 / Simkl AUTH V2 (`interval` / `slow_down` / HTTP 429)
            var nextPollAtMs = clockMs() + maxOf(authPollMinIntervalMs, pollIntervalSec * 1000L)
            while (true) {
                val now = clockMs()
                val remaining = (((expiresAt - now) + 999L) / 1000L).toInt().coerceAtLeast(0)
                if (remaining <= 0) {
                    _qrDialogState.update { cur ->
                        cur?.takeIf { it.profileId == profileId }?.copy(
                            remainingSeconds = 0,
                            isExpired = true,
                            statusMessage = "Codice scaduto",
                            errorMessage = "Il codice QR è scaduto. Premi \"Genera nuovo codice\" per continuare."
                        )
                    }
                    if (isProfileScoped) revertToPreAuthMeta(profileId)
                    return@launch
                }

                _qrDialogState.update { cur ->
                    cur?.takeIf { it.profileId == profileId }?.copy(remainingSeconds = remaining)
                }

                if (now >= nextPollAtMs) {
                    val tokenResp = try {
                        apiService.pollDeviceToken(
                            grantType = SimklConfig.GRANT_TYPE_DEVICE_CODE,
                            clientId = cfg.clientId,
                            deviceCode = dto.deviceCode
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        nextPollAtMs = clockMs() + maxOf(authPollMinIntervalMs, pollIntervalSec * 1000L)
                        delay(minOf(1_000L, maxOf(100L, authPollMinIntervalMs)))
                        continue
                    }

                    if (tokenResp.isSuccessful) {
                        val tok = tokenResp.body()
                        if (tok == null || tok.accessToken.isBlank()) {
                            _qrDialogState.update { cur ->
                                cur?.takeIf { it.profileId == profileId }?.copy(
                                    statusMessage = "Risposta non valida",
                                    errorMessage = "Simkl ha restituito un token non valido. Genera un nuovo codice."
                                )
                            }
                            if (isProfileScoped) revertToPreAuthMeta(profileId)
                            return@launch
                        }

                        val expMs = tok.expiresIn?.takeIf { it > 0L }?.let { clockMs() + it * 1000L }
                        val bundle = SimklTokenBundle(
                            accessToken = tok.accessToken.trim(),
                            refreshToken = tok.refreshToken?.trim()?.takeIf { it.isNotBlank() },
                            expiresAtMs = expMs,
                            tokenType = tok.tokenType?.ifBlank { "Bearer" } ?: "Bearer",
                            scope = tok.scope,
                            savedAtMs = clockMs()
                        )

                        val userSettingsResp = runCatching {
                            apiService.getUserSettings("Bearer ${bundle.accessToken}")
                        }.getOrNull()
                        if (userSettingsResp != null && userSettingsResp.code() == 401) {
                            _qrDialogState.update { cur ->
                                cur?.takeIf { it.profileId == profileId }?.copy(
                                    statusMessage = "Token non valido",
                                    errorMessage = "Token di autorizzazione Simkl non valido. Genera un nuovo codice."
                                )
                            }
                            if (isProfileScoped) revertToPreAuthMeta(profileId)
                            return@launch
                        }

                        val profileInfo = userSettingsResp?.body()
                        val displayName = profileInfo?.user?.name?.trim()?.takeIf { it.isNotBlank() }
                            ?: "Utente Simkl"
                        val accountId = profileInfo?.account?.id?.takeIf { it > 0L }?.toString()

                        handleAuthorizedSimklAccount(
                            requestedProfileId = profileId,
                            flowMode = flowMode,
                            bundle = bundle,
                            simklAccountId = accountId,
                            simklDisplayName = displayName
                        )
                        return@launch
                    } else {
                        val err = parseOAuthError(tokenResp.errorBody()?.string())
                        val errCode = err?.error?.lowercase()
                        when {
                            errCode == "authorization_pending" -> {
                                nextPollAtMs = clockMs() + maxOf(authPollMinIntervalMs, pollIntervalSec * 1000L)
                            }
                            errCode == "slow_down" || tokenResp.code() == 429 -> {
                                val retryAfterSec = tokenResp.headers()["Retry-After"]
                                    ?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 0
                                pollIntervalSec = maxOf(pollIntervalSec + 5, retryAfterSec)
                                _qrDialogState.update { cur ->
                                    cur?.takeIf { it.profileId == profileId }?.copy(
                                        pollIntervalSeconds = pollIntervalSec,
                                        isSlowDownActive = true,
                                        statusMessage = "Rallentamento richiesto da Simkl (${pollIntervalSec}s)…"
                                    )
                                }
                                nextPollAtMs = clockMs() + maxOf(authPollMinIntervalMs, pollIntervalSec * 1000L)
                            }
                            errCode == "access_denied" -> {
                                _qrDialogState.update { cur ->
                                    cur?.takeIf { it.profileId == profileId }?.copy(
                                        isDenied = true,
                                        statusMessage = "Autorizzazione negata",
                                        errorMessage = "Autorizzazione rifiutata su Simkl."
                                    )
                                }
                                if (isProfileScoped) revertToPreAuthMeta(profileId)
                                return@launch
                            }
                            errCode == "expired_token" -> {
                                _qrDialogState.update { cur ->
                                    cur?.takeIf { it.profileId == profileId }?.copy(
                                        isExpired = true,
                                        statusMessage = "Codice scaduto",
                                        errorMessage = "Il codice di autorizzazione è scaduto. Premi \"Genera nuovo codice\"."
                                    )
                                }
                                if (isProfileScoped) revertToPreAuthMeta(profileId)
                                return@launch
                            }
                            errCode in setOf("invalid_token", "invalid_grant", "invalid_client", "unauthorized_client") ||
                                tokenResp.code() in 400..499 -> {
                                val msg = err?.errorDescription?.takeIf { it.isNotBlank() }
                                    ?: "Errore OAuth Simkl (HTTP ${tokenResp.code()})."
                                _qrDialogState.update { cur ->
                                    cur?.takeIf { it.profileId == profileId }?.copy(
                                        statusMessage = "Errore collegamento",
                                        errorMessage = msg
                                    )
                                }
                                if (isProfileScoped) revertToPreAuthMeta(profileId)
                                return@launch
                            }
                            else -> {
                                nextPollAtMs = clockMs() + maxOf(authPollMinIntervalMs, pollIntervalSec * 1000L)
                            }
                        }
                    }
                }
                delay(minOf(1_000L, maxOf(100L, authPollMinIntervalMs)))
            }
        }
        activeAuthJobs[profileId] = job
    }

    private suspend fun handleAuthorizedSimklAccount(
        requestedProfileId: String,
        flowMode: SimklAuthFlowMode,
        bundle: SimklTokenBundle,
        simklAccountId: String?,
        simklDisplayName: String
    ) {
        val allProfiles = dao.getAllProfiles()
        val existingProfileIds = allProfiles.map { it.id }.toSet()
        val existingLinkedMeta = if (!simklAccountId.isNullOrBlank()) {
            dao.getSimklProfileMetasByAccountId(simklAccountId).firstOrNull { meta ->
                meta.profileId in existingProfileIds &&
                    (meta.connectionState != SimklConnectionState.NOT_CONNECTED.name || tokenStore.hasTokens(meta.profileId))
            }
        } else {
            null
        }

        val targetProfile = allProfiles.firstOrNull { it.id == requestedProfileId }

        if (flowMode == SimklAuthFlowMode.AUTHENTICATE_OR_CREATE_PROFILE || targetProfile == null) {
            if (existingLinkedMeta != null) {
                val existingName = allProfiles.firstOrNull { it.id == existingLinkedMeta.profileId }?.name
                    ?: simklDisplayName
                bindTokensAndSync(
                    targetProfileId = existingLinkedMeta.profileId,
                    dialogKeyProfileId = requestedProfileId,
                    bundle = bundle,
                    simklAccountId = simklAccountId,
                    simklDisplayName = simklDisplayName,
                    completionStatus = "Profilo \"$existingName\" riconosciuto e sincronizzato!",
                    emitResolvedProfile = true
                )
                return
            }

            val resolvedName = resolveAvailableSimklProfileName(simklDisplayName, allProfiles)
            val accentColor = ProfileCatalogDefaults.accentOptions[
                allProfiles.size % ProfileCatalogDefaults.accentOptions.size
            ].colorHex
            val createResult = repository.createProfile(
                name = resolvedName,
                avatarId = INITIAL_AVATAR_ID,
                accentColorHex = accentColor
            )
            val created = createResult.getOrNull()
            if (created == null) {
                _qrDialogState.update { cur ->
                    cur?.takeIf { it.profileId == requestedProfileId }?.copy(
                        statusMessage = "Limite profili raggiunto",
                        errorMessage = createResult.exceptionOrNull()?.localizedMessage
                            ?: "Limite massimo di $MAX_USER_PROFILES profili raggiunto. Seleziona un profilo esistente dalle Impostazioni."
                    )
                }
                return
            }
            bindTokensAndSync(
                targetProfileId = created.id,
                dialogKeyProfileId = requestedProfileId,
                bundle = bundle,
                simklAccountId = simklAccountId,
                simklDisplayName = simklDisplayName,
                completionStatus = "Profilo \"${created.name}\" creato e collegato a Simkl!",
                emitResolvedProfile = true
            )
            return
        }

        // LINK_ACTIVE_PROFILE con profilo locale esistente:
        // 1. Impedisci di collegare lo stesso account Simkl a due profili VixFlix sullo stesso dispositivo
        if (existingLinkedMeta != null && existingLinkedMeta.profileId != requestedProfileId) {
            val otherProfileName = allProfiles.firstOrNull { it.id == existingLinkedMeta.profileId }?.name
                ?: "Altro profilo"
            pendingAuthorizedAccount = PendingAuthorizedAccount(
                requestedProfileId = requestedProfileId,
                flowMode = flowMode,
                bundle = bundle,
                simklAccountId = simklAccountId,
                simklDisplayName = simklDisplayName
            )
            revertToPreAuthMeta(requestedProfileId)
            _qrDialogState.update { cur ->
                cur?.takeIf { it.profileId == requestedProfileId }?.copy(
                    statusMessage = "Account già associato a \"$otherProfileName\"",
                    errorMessage = null,
                    resolutionPrompt = SimklQrResolutionPrompt.AlreadyLinkedToOtherProfile(
                        existingProfileId = existingLinkedMeta.profileId,
                        existingProfileName = otherProfileName,
                        simklAccountId = simklAccountId.orEmpty(),
                        simklDisplayName = simklDisplayName
                    )
                )
            }
            return
        }

        // 2. Non sovrascrivere un profilo già collegato a un account Simkl diverso senza conferma esplicita
        val currentMeta = dao.getSimklProfileMeta(requestedProfileId)
        val hasDifferentExistingAccount = currentMeta != null &&
            !currentMeta.accountId.isNullOrBlank() &&
            !simklAccountId.isNullOrBlank() &&
            currentMeta.accountId != simklAccountId &&
            (tokenStore.hasTokens(requestedProfileId) ||
                currentMeta.connectionState == SimklConnectionState.CONNECTED_SYNCED.name ||
                currentMeta.connectionState == SimklConnectionState.AUTH_EXPIRED.name ||
                currentMeta.connectionState == SimklConnectionState.OFFLINE_QUEUE_PENDING.name)

        if (hasDifferentExistingAccount) {
            pendingAuthorizedAccount = PendingAuthorizedAccount(
                requestedProfileId = requestedProfileId,
                flowMode = flowMode,
                bundle = bundle,
                simklAccountId = simklAccountId,
                simklDisplayName = simklDisplayName
            )
            revertToPreAuthMeta(requestedProfileId)
            _qrDialogState.update { cur ->
                cur?.takeIf { it.profileId == requestedProfileId }?.copy(
                    statusMessage = "Conferma sostituzione account Simkl",
                    errorMessage = null,
                    resolutionPrompt = SimklQrResolutionPrompt.ConfirmReplaceExistingSimkl(
                        targetProfileId = requestedProfileId,
                        targetProfileName = targetProfile.name,
                        currentAccountName = currentMeta?.accountDisplayName,
                        newAccountId = simklAccountId.orEmpty(),
                        newAccountName = simklDisplayName
                    )
                )
            }
            return
        }

        bindTokensAndSync(
            targetProfileId = requestedProfileId,
            dialogKeyProfileId = requestedProfileId,
            bundle = bundle,
            simklAccountId = simklAccountId,
            simklDisplayName = simklDisplayName,
            completionStatus = "Collegato come $simklDisplayName!",
            emitResolvedProfile = false
        )
    }

    suspend fun confirmSwitchToExistingSimklProfile(): String? = withContext(Dispatchers.IO) {
        val pending = pendingAuthorizedAccount ?: return@withContext null
        val prompt = _qrDialogState.value?.resolutionPrompt as? SimklQrResolutionPrompt.AlreadyLinkedToOtherProfile
            ?: return@withContext null
        pendingAuthorizedAccount = null
        bindTokensAndSync(
            targetProfileId = prompt.existingProfileId,
            dialogKeyProfileId = pending.requestedProfileId,
            bundle = pending.bundle,
            simklAccountId = pending.simklAccountId,
            simklDisplayName = pending.simklDisplayName,
            completionStatus = "Passato al profilo \"${prompt.existingProfileName}\" (${pending.simklDisplayName})!",
            emitResolvedProfile = true
        )
        prompt.existingProfileId
    }

    suspend fun confirmReplaceExistingSimklAccount(): Boolean = withContext(Dispatchers.IO) {
        val pending = pendingAuthorizedAccount ?: return@withContext false
        val prompt = _qrDialogState.value?.resolutionPrompt as? SimklQrResolutionPrompt.ConfirmReplaceExistingSimkl
            ?: return@withContext false
        pendingAuthorizedAccount = null
        // Rimuove solo i token e i baseline remoti precedenti: i dati locali (preferiti e progressi) restano intatti
        val cfg = configProvider()
        val prevBundle = tokenStore.readTokens(prompt.targetProfileId)
        if (prevBundle != null && cfg.isClientIdConfigured) {
            prevBundle.refreshToken?.let { runCatching { apiService.revokeToken(it, cfg.clientId) } }
            runCatching { apiService.revokeToken(prevBundle.accessToken, cfg.clientId) }
        }
        tokenStore.clearTokens(prompt.targetProfileId)
        dao.deleteSimklPendingForProfile(prompt.targetProfileId)
        dao.deleteSimklBaselinesForProfile(prompt.targetProfileId)
        dao.deleteSimklLibraryForProfile(prompt.targetProfileId)
        updateSyncProgress(prompt.targetProfileId, null)
        updateSyncSummary(prompt.targetProfileId, null)

        bindTokensAndSync(
            targetProfileId = prompt.targetProfileId,
            dialogKeyProfileId = pending.requestedProfileId,
            bundle = pending.bundle,
            simklAccountId = pending.simklAccountId,
            simklDisplayName = pending.simklDisplayName,
            completionStatus = "Collegato come ${pending.simklDisplayName}!",
            emitResolvedProfile = false
        )
        true
    }

    private suspend fun bindTokensAndSync(
        targetProfileId: String,
        dialogKeyProfileId: String,
        bundle: SimklTokenBundle,
        simklAccountId: String?,
        simklDisplayName: String,
        completionStatus: String,
        emitResolvedProfile: Boolean
    ) {
        tokenStore.saveTokens(targetProfileId, bundle)
        val currentMeta = dao.getSimklProfileMeta(targetProfileId)
        dao.upsertSimklProfileMeta(
            (currentMeta ?: SimklProfileSyncMetaEntity(profileId = targetProfileId)).copy(
                connectionState = SimklConnectionState.SYNCING.name,
                accountDisplayName = simklDisplayName,
                accountId = simklAccountId ?: currentMeta?.accountId,
                lastErrorMessage = null,
                updatedAtMs = clockMs()
            )
        )
        if (emitResolvedProfile) {
            _resolvedActiveProfileRequests.tryEmit(targetProfileId)
        }
        _qrDialogState.update { cur ->
            cur?.takeIf { it.profileId == dialogKeyProfileId }?.copy(
                isCompleted = true,
                errorMessage = null,
                resolutionPrompt = null,
                statusMessage = completionStatus
            )
        }
        syncProfileNow(targetProfileId, SimklSyncTrigger.POST_AUTH)
        delay(600L)
        if (_qrDialogState.value?.profileId == dialogKeyProfileId && _qrDialogState.value?.isCompleted == true) {
            _qrDialogState.value = null
        }
    }

    private fun resolveAvailableSimklProfileName(
        rawDisplayName: String,
        existingProfiles: List<com.example.data.local.UserProfileEntity>
    ): String {
        val base = rawDisplayName.trim().ifBlank { "Profilo Simkl" }.take(20)
        val used = existingProfiles.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in used) return base
        for (i in 2..9) {
            val candidate = "$base $i".take(24)
            if (candidate.lowercase() !in used) return candidate
        }
        return "Simkl ${existingProfiles.size + 1}".take(24)
    }

    fun dismissDeviceAuthorization(profileId: String? = null) {
        val targetProfile = profileId ?: _qrDialogState.value?.profileId
        pendingAuthorizedAccount = null
        if (!targetProfile.isNullOrBlank()) {
            activeAuthJobs.remove(targetProfile)?.cancel()
            if (targetProfile != SimklConfig.PROFILE_CREATION_PENDING_ID) {
                scope.launch { revertToPreAuthMeta(targetProfile) }
            }
        }
        _qrDialogState.value = null
    }

    suspend fun unlinkAccount(profileId: String) = withContext(Dispatchers.IO) {
        pendingAuthorizedAccount = null
        activeAuthJobs.remove(profileId)?.cancel()
        activeSyncJobs.remove(profileId)?.cancel()
        pendingFollowUpTriggers.remove(profileId)
        cancelWriteJobsForProfile(profileId)
        if (_qrDialogState.value?.profileId == profileId) {
            _qrDialogState.value = null
        }

        val cfg = configProvider()
        val existingBundle = tokenStore.readTokens(profileId)
        if (existingBundle != null && cfg.isClientIdConfigured) {
            existingBundle.refreshToken?.let { refreshTok ->
                runCatching { apiService.revokeToken(token = refreshTok, clientId = cfg.clientId) }
            }
            runCatching { apiService.revokeToken(token = existingBundle.accessToken, clientId = cfg.clientId) }
        }

        tokenStore.clearTokens(profileId)
        consecutiveUnchangedPeriodicByProfile.remove(profileId)
        consecutiveErrorPeriodicByProfile.remove(profileId)
        rateLimitUntilMsByProfile.remove(profileId)
        deferredPlaybackMoviesProfiles.remove(profileId)
        deferredPlaybackEpisodesProfiles.remove(profileId)
        deferredLibrarySeriesByProfile.remove(profileId)
        lastSentScrobbleByProfileKey.keys.removeIf { it.startsWith("$profileId|") }
        dao.deleteSimklPendingForProfile(profileId)
        dao.deleteSimklBaselinesForProfile(profileId)
        dao.deleteSimklEpisodeStatesForProfile(profileId)
        dao.deleteSimklLibraryForProfile(profileId)
        dao.deleteSimklProfileMeta(profileId)
        updateSyncProgress(profileId, null)
        updateSyncSummary(profileId, null)
    }

    suspend fun onProfileDeleted(profileId: String) = withContext(Dispatchers.IO) {
        activeAuthJobs.remove(profileId)?.cancel()
        activeSyncJobs.remove(profileId)?.cancel()
        pendingFollowUpTriggers.remove(profileId)
        cancelWriteJobsForProfile(profileId)
        consecutiveUnchangedPeriodicByProfile.remove(profileId)
        consecutiveErrorPeriodicByProfile.remove(profileId)
        rateLimitUntilMsByProfile.remove(profileId)
        deferredPlaybackMoviesProfiles.remove(profileId)
        deferredPlaybackEpisodesProfiles.remove(profileId)
        deferredLibrarySeriesByProfile.remove(profileId)
        lastSentScrobbleByProfileKey.keys.removeIf { it.startsWith("$profileId|") }
        if (_qrDialogState.value?.profileId == profileId) {
            _qrDialogState.value = null
        }
        if (activePlaybackTarget?.profileId == profileId) {
            activePlaybackTarget = null
        }
        tokenStore.clearTokens(profileId)
        updateSyncProgress(profileId, null)
        updateSyncSummary(profileId, null)
    }

    fun syncIfNeeded(profileId: String, trigger: SimklSyncTrigger) {
        if (activeSyncJobs[profileId]?.isActive == true) {
            scheduleFollowUpTriggerIfHigher(profileId, trigger)
            return
        }
        val now = clockMs()
        val isForced = trigger == SimklSyncTrigger.MANUAL ||
            trigger == SimklSyncTrigger.POST_AUTH ||
            trigger == SimklSyncTrigger.FORCE_FULL_RECONCILE
        val rateLimitUntil = rateLimitUntilMsByProfile[profileId] ?: 0L
        if (!isForced && rateLimitUntil > now) {
            return
        }
        val lastAttempt = lastSyncAttemptAtMs[profileId] ?: 0L
        val effectiveCooldown = when (trigger) {
            SimklSyncTrigger.MANUAL,
            SimklSyncTrigger.POST_AUTH,
            SimklSyncTrigger.FORCE_FULL_RECONCILE,
            SimklSyncTrigger.PRE_PLAYBACK,
            SimklSyncTrigger.POST_SCROBBLE,
            SimklSyncTrigger.PERIODIC -> 0L
            SimklSyncTrigger.DETAIL_OPEN,
            SimklSyncTrigger.CONTINUE_WATCHING_OPEN ->
                if (syncCooldownMs > 0L) maxOf(syncCooldownMs, 60_000L) else 0L
            SimklSyncTrigger.FOREGROUND ->
                if (syncCooldownMs > 0L) minOf(syncCooldownMs, 15_000L) else 0L
            else -> syncCooldownMs
        }
        if (effectiveCooldown > 0L && (now - lastAttempt) < effectiveCooldown) {
            return
        }
        launchProfileSync(profileId, trigger)
    }

    /**
     * Fast-path pre-playback: prima di avviare il player verifica rapidamente /sync/activities
     * e interroga SOLO gli endpoint /sync/playback/{movies|episodes} i cui timestamp sono realmente cambiati
     * (oppure differiti durante una riproduzione attiva precedente), evitando chiamate superflue.
     */
    suspend fun syncBeforePlaybackIfConnected(
        profileId: String,
        timeoutMs: Long = 1_200L
    ) = withContext(Dispatchers.IO) {
        if (!configProvider().isClientIdConfigured) return@withContext
        if (!tokenStore.hasTokens(profileId)) return@withContext
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            syncPlaybackFastPathNow(profileId)
        }
    }

    internal suspend fun syncPlaybackFastPathNow(profileId: String): Boolean = withContext(Dispatchers.IO) {
        if (!configProvider().isClientIdConfigured) return@withContext false
        if (!tokenStore.hasTokens(profileId)) return@withContext false
        syncMutexFor(profileId).withLock {
            val accessToken = getValidAccessToken(profileId, forceRefresh = false)
                ?: return@withLock false
            val actResult = fetchAndParseActivities(
                profileId = profileId,
                initialToken = accessToken,
                triggerEvent = SimklSyncTrigger.PRE_PLAYBACK.name
            )
            val activities = actResult.activities ?: return@withLock false
            val currentMeta = dao.getSimklProfileMeta(profileId)
            val (prevSnapshot, _) = parseStoredActivitiesSnapshot(currentMeta?.activitiesSnapshotJson)
            val prevMoviesPlaybackTs = prevSnapshot?.moviesPlaybackTimestamp ?: currentMeta?.lastMoviesPlaybackTs
            val prevShowsPlaybackTs = prevSnapshot?.showsPlaybackTimestamp ?: currentMeta?.lastShowsPlaybackTs
            val prevAnimePlaybackTs = prevSnapshot?.animePlaybackTimestamp ?: currentMeta?.lastAnimePlaybackTs

            val moviesPlaybackChanged = currentMeta == null ||
                !currentMeta.initialFullImportDone ||
                activities.moviesPlaybackTimestamp != prevMoviesPlaybackTs ||
                profileId in deferredPlaybackMoviesProfiles
            val episodesPlaybackChanged = currentMeta == null ||
                !currentMeta.initialFullImportDone ||
                activities.showsPlaybackTimestamp != prevShowsPlaybackTs ||
                activities.animePlaybackTimestamp != prevAnimePlaybackTs ||
                profileId in deferredPlaybackEpisodesProfiles

            if (!moviesPlaybackChanged && !episodesPlaybackChanged) {
                _apiMetrics.update { it.copy(callsAvoidedUnchangedActivities = it.callsAvoidedUnchangedActivities + 1) }
                recordDiagnostic(
                    endpoint = "GET /sync/activities",
                    contentType = "playback",
                    profileId = profileId,
                    triggerEvent = SimklSyncTrigger.PRE_PLAYBACK.name,
                    outcome = "SKIPPED_UNCHANGED_PLAYBACK",
                    operationType = "PRE_PLAYBACK_SKIP"
                )
                return@withLock true
            }

            var moviesOk = true
            var episodesOk = true
            var fetchedMovies: List<SimklPlaybackItemDto> = emptyList()
            var fetchedEpisodes: List<SimklPlaybackItemDto> = emptyList()

            if (moviesPlaybackChanged) {
                val startMs = clockMs()
                val moviesResp = try {
                    executeWithAuthRetry(profileId, accessToken) { tok ->
                        apiService.getPlaybackMovies("Bearer $tok")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                val durMs = (clockMs() - startMs).coerceAtLeast(0L)
                recordApiCallMetric("GET /sync/playback/movies", httpCode = moviesResp?.code())
                if (moviesResp?.code() == 429) {
                    rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(moviesResp.headers()["Retry-After"], 0)
                }
                if (moviesResp?.isSuccessful == true) {
                    fetchedMovies = moviesResp.body().orEmpty()
                    recordDiagnostic(
                        endpoint = "GET /sync/playback/movies",
                        contentType = "movie",
                        profileId = profileId,
                        triggerEvent = SimklSyncTrigger.PRE_PLAYBACK.name,
                        httpCode = moviesResp.code(),
                        outcome = "OK",
                        operationType = "PLAYBACK_FAST_PATH",
                        durationMs = durMs,
                        itemsCount = fetchedMovies.size
                    )
                } else {
                    moviesOk = false
                }
            }

            if (episodesPlaybackChanged) {
                val startMs = clockMs()
                val episodesResp = try {
                    executeWithAuthRetry(profileId, accessToken) { tok ->
                        apiService.getPlaybackEpisodes("Bearer $tok")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                val durMs = (clockMs() - startMs).coerceAtLeast(0L)
                recordApiCallMetric("GET /sync/playback/episodes", httpCode = episodesResp?.code())
                if (episodesResp?.code() == 429) {
                    rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(episodesResp.headers()["Retry-After"], 0)
                }
                if (episodesResp?.isSuccessful == true) {
                    fetchedEpisodes = episodesResp.body().orEmpty()
                    recordDiagnostic(
                        endpoint = "GET /sync/playback/episodes",
                        contentType = "tv",
                        profileId = profileId,
                        triggerEvent = SimklSyncTrigger.PRE_PLAYBACK.name,
                        httpCode = episodesResp.code(),
                        outcome = "OK",
                        operationType = "PLAYBACK_FAST_PATH",
                        durationMs = durMs,
                        itemsCount = fetchedEpisodes.size
                    )
                } else {
                    episodesOk = false
                }
            }

            if (moviesOk && episodesOk) {
                reconcileRemotePlayback(
                    profileId = profileId,
                    remoteMovies = fetchedMovies,
                    remoteEpisodes = fetchedEpisodes,
                    reconcileMovies = moviesPlaybackChanged,
                    reconcileEpisodes = episodesPlaybackChanged
                )
                val latestMeta = dao.getSimklProfileMeta(profileId)
                if (latestMeta != null) {
                    dao.upsertSimklProfileMeta(
                        latestMeta.copy(
                            lastMoviesPlaybackTs = if (moviesPlaybackChanged) {
                                activities.moviesPlaybackTimestamp ?: latestMeta.lastMoviesPlaybackTs
                            } else {
                                latestMeta.lastMoviesPlaybackTs
                            },
                            lastShowsPlaybackTs = if (episodesPlaybackChanged) {
                                activities.showsPlaybackTimestamp ?: latestMeta.lastShowsPlaybackTs
                            } else {
                                latestMeta.lastShowsPlaybackTs
                            },
                            lastAnimePlaybackTs = if (episodesPlaybackChanged) {
                                activities.animePlaybackTimestamp ?: latestMeta.lastAnimePlaybackTs
                            } else {
                                latestMeta.lastAnimePlaybackTs
                            },
                            updatedAtMs = clockMs()
                        )
                    )
                }
                _syncRevision.update { it + 1L }
                return@withLock true
            }
            false
        }
    }

    fun launchProfileSync(profileId: String, trigger: SimklSyncTrigger = SimklSyncTrigger.MANUAL): Job {
        activeSyncJobs[profileId]?.takeIf { it.isActive }?.let { activeJob ->
            scheduleFollowUpTriggerIfHigher(profileId, trigger)
            return activeJob
        }
        val job = scope.launch {
            var currentTrigger: SimklSyncTrigger? = trigger
            var guardPasses = 0
            while (currentTrigger != null && guardPasses < 3) {
                guardPasses++
                pendingFollowUpTriggers.remove(profileId)
                syncProfileNow(profileId, currentTrigger)
                currentTrigger = pendingFollowUpTriggers.remove(profileId)
            }
        }
        activeSyncJobs[profileId] = job
        return job
    }

    suspend fun syncProfileNow(
        profileId: String,
        trigger: SimklSyncTrigger = SimklSyncTrigger.MANUAL
    ): Boolean = withContext(Dispatchers.IO) {
        val cfg = configProvider()
        if (!cfg.isClientIdConfigured) return@withContext false

        syncMutexFor(profileId).withLock {
            val tokens = tokenStore.readTokens(profileId)
            val meta = dao.getSimklProfileMeta(profileId)
            if (tokens == null) {
                if (meta != null && meta.connectionState != SimklConnectionState.AUTH_EXPIRED.name &&
                    meta.connectionState != SimklConnectionState.NOT_CONNECTED.name
                ) {
                    updateMetaState(profileId, SimklConnectionState.AUTH_EXPIRED, error = "Token Simkl assente o scaduto.")
                }
                return@withLock false
            }

            lastSyncAttemptAtMs[profileId] = clockMs()
            updateMetaState(profileId, SimklConnectionState.SYNCING, clearError = true)
            updateSyncProgress(
                profileId,
                SimklSyncProgress(currentPhaseLabel = "Verifica attività Simkl…")
            )

            try {
                val accessToken = getValidAccessToken(profileId, forceRefresh = false)
                    ?: return@withLock false

                var apiRequestsCompleted = 0
                var apiRequestsFailed = 0
                val syncErrors = ArrayList<SimklSyncErrorEntry>()

                // 1. Direzione B PRIMA dei conflitti offline: recupera /sync/activities e cattura il JSON raw completo
                val actResult = fetchAndParseActivities(
                    profileId = profileId,
                    initialToken = accessToken,
                    triggerEvent = trigger.name
                )
                if (actResult.activities == null || actResult.rawJson == null) {
                    apiRequestsFailed++
                    _apiMetrics.update { it.copy(incompleteSyncsCount = it.incompleteSyncsCount + 1) }
                    consecutiveErrorPeriodicByProfile.compute(profileId) { _, cur -> (cur ?: 0) + 1 }
                    actResult.errorEntry?.let { syncErrors.add(it) }
                    val hasPending = dao.getSimklPendingScrobbles(profileId).isNotEmpty()
                    updateSyncSummary(
                        profileId,
                        SimklSyncSummary(
                            apiRequestsCompleted = apiRequestsCompleted,
                            apiRequestsFailed = apiRequestsFailed,
                            errors = syncErrors
                        )
                    )
                    if (!actResult.authLost) {
                        updateMetaState(
                            profileId,
                            if (hasPending) SimklConnectionState.OFFLINE_QUEUE_PENDING else SimklConnectionState.ERROR,
                            error = syncErrors.firstOrNull()?.message ?: "Errore attività Simkl."
                        )
                    }
                    return@withLock false
                }
                apiRequestsCompleted++
                val activities = actResult.activities
                val rawActivitiesJson = actResult.rawJson

                val currentMeta = dao.getSimklProfileMeta(profileId)
                val (prevSnapshot, snapshotCorrupted) = parseStoredActivitiesSnapshot(currentMeta?.activitiesSnapshotJson)
                if (snapshotCorrupted) {
                    recordDiagnostic(
                        endpoint = "LOCAL_SNAPSHOT",
                        contentType = "activities",
                        profileId = profileId,
                        triggerEvent = trigger.name,
                        outcome = "CORRUPTED_SNAPSHOT_FALLBACK_FULL",
                        operationType = "SNAPSHOT_RECOVERY"
                    )
                }

                val isInitialFullImport = currentMeta == null ||
                    !currentMeta.initialFullImportDone ||
                    currentMeta.lastActivitiesAll.isNullOrBlank() ||
                    snapshotCorrupted ||
                    trigger == SimklSyncTrigger.POST_AUTH ||
                    trigger == SimklSyncTrigger.FORCE_FULL_RECONCILE
                val nowSyncMs = clockMs()
                val isPeriodicFullReconcileDue = !isInitialFullImport &&
                    currentMeta?.lastFullReconcileAtMs != null &&
                    (nowSyncMs - currentMeta.lastFullReconcileAtMs) >= fullReconcileIntervalMs
                val runAuthoritativeFullReconcile = isInitialFullImport || isPeriodicFullReconcileDue

                var allLibraryRequestsSucceeded = true
                val pulledShows = ArrayList<SimklLibraryParsedItem>()
                val pulledMovies = ArrayList<SimklLibraryParsedItem>()
                val pulledAnime = ArrayList<SimklLibraryParsedItem>()
                var removedCount = 0
                var fullShowsSnapshot = false
                var fullMoviesSnapshot = false
                var fullAnimeSnapshot = false

                // Valutazione precisa delle differenze rispetto allo snapshot precedente (Fase 2)
                var fetchMoviesPlayback = runAuthoritativeFullReconcile
                var fetchEpisodesPlayback = runAuthoritativeFullReconcile
                var showsRemovedChanged = false
                var moviesRemovedChanged = false
                var animeRemovedChanged = false

                val activeToken = getValidAccessToken(profileId, forceRefresh = false)
                    ?: return@withLock false

                if (runAuthoritativeFullReconcile) {
                    // FASE 1 / FULL RECONCILE PERIODICO: scaricamento completo sequenziale (shows -> movies -> anime)
                    updateSyncProgress(
                        profileId,
                        SimklSyncProgress(
                            currentPhaseLabel = if (isInitialFullImport) {
                                "Scaricamento libreria Simkl completa…"
                            } else {
                                "Riconciliazione completa libreria Simkl…"
                            }
                        )
                    )

                    val categories = listOf(
                        Triple(SimklLibraryJsonParser.CATEGORY_SHOWS, "yes", "yes"),
                        Triple(SimklLibraryJsonParser.CATEGORY_MOVIES, null, null),
                        Triple(SimklLibraryJsonParser.CATEGORY_ANIME, "yes", "yes")
                    )
                    for ((category, epWatched, includeAllEps) in categories) {
                        val fetchOutcome = fetchFullCategoryWithFallback(
                            profileId = profileId,
                            initialToken = activeToken,
                            category = category,
                            extended = if (category == SimklLibraryJsonParser.CATEGORY_MOVIES) null else "full",
                            episodeWatchedAt = epWatched,
                            includeAllEpisodes = includeAllEps,
                            triggerEvent = if (isPeriodicFullReconcileDue) "${trigger.name}_PERIODIC_FULL" else trigger.name
                        )
                        apiRequestsCompleted += fetchOutcome.requestsCompleted
                        apiRequestsFailed += fetchOutcome.requestsFailed
                        syncErrors.addAll(fetchOutcome.errors)
                        if (fetchOutcome.authLost) return@withLock false
                        if (!fetchOutcome.succeeded) {
                            allLibraryRequestsSucceeded = false
                            if (fetchOutcome.rateLimited) break
                        } else {
                            when (category) {
                                SimklLibraryJsonParser.CATEGORY_SHOWS -> {
                                    pulledShows.addAll(fetchOutcome.shows)
                                    fullShowsSnapshot = true
                                }
                                SimklLibraryJsonParser.CATEGORY_MOVIES -> {
                                    pulledMovies.addAll(fetchOutcome.movies)
                                    fullMoviesSnapshot = true
                                }
                                SimklLibraryJsonParser.CATEGORY_ANIME -> {
                                    pulledAnime.addAll(fetchOutcome.anime)
                                    fullAnimeSnapshot = true
                                }
                            }
                        }
                    }
                } else {
                    // FASE 2: modello ufficiale Simkl con confronto snapshot e delta esatto `date_from`
                    val prevAllTs = currentMeta!!.lastActivitiesAll!!
                    val prevMoviesPlaybackTs = prevSnapshot?.moviesPlaybackTimestamp ?: currentMeta.lastMoviesPlaybackTs
                    val prevShowsPlaybackTs = prevSnapshot?.showsPlaybackTimestamp ?: currentMeta.lastShowsPlaybackTs
                    val prevAnimePlaybackTs = prevSnapshot?.animePlaybackTimestamp ?: currentMeta.lastAnimePlaybackTs
                    val prevShowsAllTs = prevSnapshot?.showsAllTimestamp ?: currentMeta.lastShowsAllTs
                    val prevShowsWatchedTs = prevSnapshot?.showsWatchedTimestamp ?: currentMeta.lastShowsWatchedTs
                    val prevMoviesAllTs = prevSnapshot?.moviesAllTimestamp ?: currentMeta.lastMoviesAllTs
                    val prevMoviesWatchedTs = prevSnapshot?.moviesWatchedTimestamp ?: currentMeta.lastMoviesWatchedTs
                    val prevAnimeAllTs = prevSnapshot?.animeAllTimestamp ?: currentMeta.lastAnimeAllTs
                    val prevAnimeWatchedTs = prevSnapshot?.animeWatchedTimestamp ?: currentMeta.lastAnimeWatchedTs
                    val prevRemovedShowsTs = prevSnapshot?.showsRemovedTimestamp ?: currentMeta.lastRemovedShowsTs
                    val prevRemovedMoviesTs = prevSnapshot?.moviesRemovedTimestamp ?: currentMeta.lastRemovedMoviesTs
                    val prevRemovedAnimeTs = prevSnapshot?.animeRemovedTimestamp ?: currentMeta.lastRemovedAnimeTs

                    showsRemovedChanged = !activities.showsRemovedTimestamp.isNullOrBlank() &&
                        activities.showsRemovedTimestamp != prevRemovedShowsTs
                    moviesRemovedChanged = !activities.moviesRemovedTimestamp.isNullOrBlank() &&
                        activities.moviesRemovedTimestamp != prevRemovedMoviesTs
                    animeRemovedChanged = !activities.animeRemovedTimestamp.isNullOrBlank() &&
                        activities.animeRemovedTimestamp != prevRemovedAnimeTs

                    val showsListOrWatchedChanged =
                        (activities.showsAllTimestamp != null && activities.showsAllTimestamp != prevShowsAllTs) ||
                            (activities.showsWatchedTimestamp != null && activities.showsWatchedTimestamp != prevShowsWatchedTs)
                    val moviesListOrWatchedChanged =
                        (activities.moviesAllTimestamp != null && activities.moviesAllTimestamp != prevMoviesAllTs) ||
                            (activities.moviesWatchedTimestamp != null && activities.moviesWatchedTimestamp != prevMoviesWatchedTs)
                    val animeListOrWatchedChanged =
                        (activities.animeAllTimestamp != null && activities.animeAllTimestamp != prevAnimeAllTs) ||
                            (activities.animeWatchedTimestamp != null && activities.animeWatchedTimestamp != prevAnimeWatchedTs)

                    fetchMoviesPlayback = activities.moviesPlaybackTimestamp != prevMoviesPlaybackTs ||
                        profileId in deferredPlaybackMoviesProfiles
                    fetchEpisodesPlayback = activities.showsPlaybackTimestamp != prevShowsPlaybackTs ||
                        activities.animePlaybackTimestamp != prevAnimePlaybackTs ||
                        profileId in deferredPlaybackEpisodesProfiles

                    val anyRemovalChanged = showsRemovedChanged || moviesRemovedChanged || animeRemovedChanged
                    val anyPlaybackChanged = fetchMoviesPlayback || fetchEpisodesPlayback
                    val allChanged = !activities.all.isNullOrBlank() && activities.all != prevAllTs
                    val ratingsOnlyChanged = allChanged &&
                        !showsListOrWatchedChanged && !moviesListOrWatchedChanged && !animeListOrWatchedChanged &&
                        !anyRemovalChanged && !anyPlaybackChanged &&
                        prevSnapshot != null &&
                        (activities.showsRatedAtTimestamp != prevSnapshot.showsRatedAtTimestamp ||
                            activities.moviesRatedAtTimestamp != prevSnapshot.moviesRatedAtTimestamp ||
                            activities.animeRatedAtTimestamp != prevSnapshot.animeRatedAtTimestamp)

                    val activitiesLibraryChanged = showsListOrWatchedChanged ||
                        moviesListOrWatchedChanged ||
                        animeListOrWatchedChanged ||
                        (allChanged && !anyPlaybackChanged && !anyRemovalChanged && !ratingsOnlyChanged)

                    // Nessuna variazione rilevante -> STOP immediato (solo 1 chiamata: GET /sync/activities)
                    if (!activitiesLibraryChanged && !anyRemovalChanged && !anyPlaybackChanged) {
                        _apiMetrics.update {
                            it.copy(
                                callsAvoidedUnchangedActivities = it.callsAvoidedUnchangedActivities + 1,
                                incrementalSyncsCount = it.incrementalSyncsCount + 1
                            )
                        }
                        consecutiveUnchangedPeriodicByProfile.compute(profileId) { _, cur -> (cur ?: 0) + 1 }
                        consecutiveErrorPeriodicByProfile[profileId] = 0
                        recordDiagnostic(
                            endpoint = "GET /sync/activities",
                            contentType = "activities",
                            profileId = profileId,
                            triggerEvent = trigger.name,
                            outcome = if (ratingsOnlyChanged) "SKIPPED_RATINGS_ONLY" else "NO_CHANGES_SHORT_CIRCUIT",
                            operationType = "INCREMENTAL_NOOP"
                        )
                        flushPendingQueueInternal(profileId)
                        val finalPending = dao.getSimklPendingScrobbles(profileId).size
                        val committedAtMs = clockMs()
                        val latestMeta = dao.getSimklProfileMeta(profileId) ?: currentMeta
                        dao.upsertSimklProfileMeta(
                            latestMeta.copy(
                                connectionState = if (finalPending > 0) {
                                    SimklConnectionState.OFFLINE_QUEUE_PENDING.name
                                } else {
                                    SimklConnectionState.CONNECTED_SYNCED.name
                                },
                                lastActivitiesAll = if (ratingsOnlyChanged) (activities.all ?: latestMeta.lastActivitiesAll) else latestMeta.lastActivitiesAll,
                                activitiesSnapshotJson = if (ratingsOnlyChanged || latestMeta.activitiesSnapshotJson.isNullOrBlank()) {
                                    rawActivitiesJson
                                } else {
                                    latestMeta.activitiesSnapshotJson
                                },
                                lastSyncAtMs = committedAtMs,
                                lastErrorMessage = null,
                                updatedAtMs = committedAtMs
                            )
                        )
                        updateSyncSummary(
                            profileId,
                            SimklSyncSummary(
                                apiRequestsCompleted = apiRequestsCompleted,
                                apiRequestsFailed = 0,
                                isIncremental = true
                            )
                        )
                        return@withLock true
                    }

                    if (activitiesLibraryChanged) {
                        updateSyncProgress(
                            profileId,
                            SimklSyncProgress(currentPhaseLabel = "Aggiornamento incrementale Simkl…")
                        )
                        val changedCategories = buildList {
                            if (showsListOrWatchedChanged) add(SimklLibraryJsonParser.CATEGORY_SHOWS)
                            if (moviesListOrWatchedChanged) add(SimklLibraryJsonParser.CATEGORY_MOVIES)
                            if (animeListOrWatchedChanged) add(SimklLibraryJsonParser.CATEGORY_ANIME)
                        }.ifEmpty {
                            listOf(
                                SimklLibraryJsonParser.CATEGORY_SHOWS,
                                SimklLibraryJsonParser.CATEGORY_MOVIES,
                                SimklLibraryJsonParser.CATEGORY_ANIME
                            )
                        }
                        val needsEpisodeDetail = showsListOrWatchedChanged ||
                            animeListOrWatchedChanged ||
                            (!moviesListOrWatchedChanged && allChanged)
                        val completedOrDroppedChanged = prevSnapshot == null ||
                            activities.showsCompletedOrDroppedTimestamp != prevSnapshot.showsCompletedOrDroppedTimestamp ||
                            activities.animeCompletedOrDroppedTimestamp != prevSnapshot.animeCompletedOrDroppedTimestamp
                        val deltaExtended = if (needsEpisodeDetail) "full" else null
                        val deltaEpisodeWatchedAt = if (needsEpisodeDetail) "yes" else null
                        val deltaIncludeAllEpisodes = if (needsEpisodeDetail && completedOrDroppedChanged) "yes" else null

                        val deltaOutcome = fetchIncrementalDelta(
                            profileId = profileId,
                            initialToken = activeToken,
                            dateFrom = prevAllTs,
                            extended = deltaExtended,
                            episodeWatchedAt = deltaEpisodeWatchedAt,
                            includeAllEpisodes = deltaIncludeAllEpisodes,
                            changedCategories = changedCategories,
                            triggerEvent = trigger.name
                        )
                        apiRequestsCompleted += deltaOutcome.requestsCompleted
                        apiRequestsFailed += deltaOutcome.requestsFailed
                        syncErrors.addAll(deltaOutcome.errors)
                        if (deltaOutcome.authLost) return@withLock false
                        if (!deltaOutcome.succeeded) {
                            allLibraryRequestsSucceeded = false
                        } else {
                            pulledShows.addAll(deltaOutcome.shows)
                            pulledMovies.addAll(deltaOutcome.movies)
                            pulledAnime.addAll(deltaOutcome.anime)

                            // Rete di sicurezza mirata per unwatch che arretra `last_watched_at` prima di `date_from`:
                            // scarica la singola categoria SOLO se il suo timestamp è cambiato ma `date_from` non ha restituito
                            // alcun elemento per quella categoria (evitando download ridondanti nel 99% dei delta normali).
                            if (showsListOrWatchedChanged &&
                                deltaOutcome.shows.isEmpty() &&
                                dao.getSimklLibraryByCategory(profileId, SimklLibraryJsonParser.CATEGORY_SHOWS).isNotEmpty()
                            ) {
                                val fallbackShows = fetchFullCategoryWithFallback(
                                    profileId = profileId,
                                    initialToken = activeToken,
                                    category = SimklLibraryJsonParser.CATEGORY_SHOWS,
                                    extended = "full",
                                    episodeWatchedAt = "yes",
                                    includeAllEpisodes = "yes",
                                    triggerEvent = "${trigger.name}_UNWATCH_FALLBACK"
                                )
                                apiRequestsCompleted += fallbackShows.requestsCompleted
                                apiRequestsFailed += fallbackShows.requestsFailed
                                syncErrors.addAll(fallbackShows.errors)
                                if (fallbackShows.authLost) return@withLock false
                                if (fallbackShows.succeeded) {
                                    pulledShows.addAll(fallbackShows.shows)
                                    fullShowsSnapshot = true
                                } else {
                                    allLibraryRequestsSucceeded = false
                                }
                            }
                            if (allLibraryRequestsSucceeded &&
                                moviesListOrWatchedChanged &&
                                deltaOutcome.movies.isEmpty() &&
                                dao.getSimklLibraryByCategory(profileId, SimklLibraryJsonParser.CATEGORY_MOVIES).isNotEmpty()
                            ) {
                                val fallbackMovies = fetchFullCategoryWithFallback(
                                    profileId = profileId,
                                    initialToken = activeToken,
                                    category = SimklLibraryJsonParser.CATEGORY_MOVIES,
                                    extended = null,
                                    episodeWatchedAt = null,
                                    includeAllEpisodes = null,
                                    triggerEvent = "${trigger.name}_UNWATCH_FALLBACK"
                                )
                                apiRequestsCompleted += fallbackMovies.requestsCompleted
                                apiRequestsFailed += fallbackMovies.requestsFailed
                                syncErrors.addAll(fallbackMovies.errors)
                                if (fallbackMovies.authLost) return@withLock false
                                if (fallbackMovies.succeeded) {
                                    pulledMovies.addAll(fallbackMovies.movies)
                                    fullMoviesSnapshot = true
                                } else {
                                    allLibraryRequestsSucceeded = false
                                }
                            }
                            if (allLibraryRequestsSucceeded &&
                                animeListOrWatchedChanged &&
                                deltaOutcome.anime.isEmpty() &&
                                dao.getSimklLibraryByCategory(profileId, SimklLibraryJsonParser.CATEGORY_ANIME).isNotEmpty()
                            ) {
                                val fallbackAnime = fetchFullCategoryWithFallback(
                                    profileId = profileId,
                                    initialToken = activeToken,
                                    category = SimklLibraryJsonParser.CATEGORY_ANIME,
                                    extended = "full",
                                    episodeWatchedAt = "yes",
                                    includeAllEpisodes = "yes",
                                    triggerEvent = "${trigger.name}_UNWATCH_FALLBACK"
                                )
                                apiRequestsCompleted += fallbackAnime.requestsCompleted
                                apiRequestsFailed += fallbackAnime.requestsFailed
                                syncErrors.addAll(fallbackAnime.errors)
                                if (fallbackAnime.authLost) return@withLock false
                                if (fallbackAnime.succeeded) {
                                    pulledAnime.addAll(fallbackAnime.anime)
                                    fullAnimeSnapshot = true
                                } else {
                                    allLibraryRequestsSucceeded = false
                                }
                            }
                        }
                    }
                }

                // 2. Scarica SOLO gli endpoint /sync/playback/* effettivamente cambiati (separati da libreria/watchlist)
                var playbackRequestsOk = true
                var fetchedRemoteMovies: List<SimklPlaybackItemDto> = emptyList()
                var fetchedRemoteEpisodes: List<SimklPlaybackItemDto> = emptyList()
                if (allLibraryRequestsSucceeded && (fetchMoviesPlayback || fetchEpisodesPlayback)) {
                    val playbackToken = getValidAccessToken(profileId, forceRefresh = false)
                        ?: return@withLock false

                    if (fetchMoviesPlayback) {
                        val startMs = clockMs()
                        val moviesResp = try {
                            executeWithAuthRetry(profileId, playbackToken) { tok ->
                                apiService.getPlaybackMovies("Bearer $tok")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            recordApiCallMetric("GET /sync/playback/movies")
                            recordDiagnostic(
                                endpoint = "GET /sync/playback/movies",
                                contentType = "movie",
                                profileId = profileId,
                                triggerEvent = trigger.name,
                                outcome = "NETWORK_OR_PARSE_ERROR",
                                errorDetail = e.javaClass.simpleName,
                                operationType = "PLAYBACK",
                                durationMs = (clockMs() - startMs).coerceAtLeast(0L)
                            )
                            null
                        }
                        val durMs = (clockMs() - startMs).coerceAtLeast(0L)
                        if (moviesResp != null) {
                            recordApiCallMetric("GET /sync/playback/movies", httpCode = moviesResp.code())
                            if (moviesResp.code() == 429) {
                                rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(moviesResp.headers()["Retry-After"], 0)
                            }
                        }
                        if (moviesResp != null && moviesResp.isSuccessful) {
                            apiRequestsCompleted++
                            fetchedRemoteMovies = moviesResp.body().orEmpty()
                            recordDiagnostic(
                                endpoint = "GET /sync/playback/movies",
                                contentType = "movie",
                                profileId = profileId,
                                triggerEvent = trigger.name,
                                httpCode = moviesResp.code(),
                                outcome = "OK",
                                operationType = "PLAYBACK",
                                durationMs = durMs,
                                itemsCount = fetchedRemoteMovies.size
                            )
                        } else {
                            apiRequestsFailed++
                            playbackRequestsOk = false
                            val code = moviesResp?.code()
                            syncErrors.add(
                                SimklSyncErrorEntry(
                                    source = "/sync/playback/movies",
                                    httpCode = code,
                                    errorType = if (code != null) "HTTP_$code" else "NETWORK",
                                    message = if (code != null) "Errore playback film (HTTP $code)" else "Errore rete playback film"
                                )
                            )
                        }
                    }

                    if (fetchEpisodesPlayback && playbackRequestsOk) {
                        val startMs = clockMs()
                        val episodesResp = try {
                            executeWithAuthRetry(profileId, playbackToken) { tok ->
                                apiService.getPlaybackEpisodes("Bearer $tok")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            recordApiCallMetric("GET /sync/playback/episodes")
                            recordDiagnostic(
                                endpoint = "GET /sync/playback/episodes",
                                contentType = "tv",
                                profileId = profileId,
                                triggerEvent = trigger.name,
                                outcome = "NETWORK_OR_PARSE_ERROR",
                                errorDetail = e.javaClass.simpleName,
                                operationType = "PLAYBACK",
                                durationMs = (clockMs() - startMs).coerceAtLeast(0L)
                            )
                            null
                        }
                        val durMs = (clockMs() - startMs).coerceAtLeast(0L)
                        if (episodesResp != null) {
                            recordApiCallMetric("GET /sync/playback/episodes", httpCode = episodesResp.code())
                            if (episodesResp.code() == 429) {
                                rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(episodesResp.headers()["Retry-After"], 0)
                            }
                        }
                        if (episodesResp != null && episodesResp.isSuccessful) {
                            apiRequestsCompleted++
                            fetchedRemoteEpisodes = episodesResp.body().orEmpty()
                            recordDiagnostic(
                                endpoint = "GET /sync/playback/episodes",
                                contentType = "tv",
                                profileId = profileId,
                                triggerEvent = trigger.name,
                                httpCode = episodesResp.code(),
                                outcome = "OK",
                                operationType = "PLAYBACK",
                                durationMs = durMs,
                                itemsCount = fetchedRemoteEpisodes.size
                            )
                        } else {
                            apiRequestsFailed++
                            playbackRequestsOk = false
                            val code = episodesResp?.code()
                            syncErrors.add(
                                SimklSyncErrorEntry(
                                    source = "/sync/playback/episodes",
                                    httpCode = code,
                                    errorType = if (code != null) "HTTP_$code" else "NETWORK",
                                    message = if (code != null) "Errore playback episodi (HTTP $code)" else "Errore rete playback episodi"
                                )
                            )
                        }
                    }
                }

                // Se una richiesta obbligatoria di libreria o playback ha fallito (o JSON troncato),
                // interrompe PRIMA di mutare lo snapshot o avanzare i watermark.
                val allRequestsSucceeded = allLibraryRequestsSucceeded && playbackRequestsOk
                if (!allRequestsSucceeded) {
                    _apiMetrics.update { it.copy(incompleteSyncsCount = it.incompleteSyncsCount + 1) }
                    consecutiveErrorPeriodicByProfile.compute(profileId) { _, cur -> (cur ?: 0) + 1 }
                    val primaryErr = syncErrors.firstOrNull()?.message
                        ?: "Alcune richieste Simkl hanno fallito: snapshot precedente conservato."
                    updateSyncSummary(
                        profileId,
                        SimklSyncSummary(
                            showsFound = pulledShows.size,
                            moviesFound = pulledMovies.size,
                            animeFound = pulledAnime.size,
                            apiRequestsCompleted = apiRequestsCompleted,
                            apiRequestsFailed = apiRequestsFailed,
                            isIncremental = !runAuthoritativeFullReconcile,
                            removedCount = removedCount,
                            errors = syncErrors.take(12)
                        )
                    )
                    val latestMeta = dao.getSimklProfileMeta(profileId) ?: SimklProfileSyncMetaEntity(profileId = profileId)
                    dao.upsertSimklProfileMeta(
                        latestMeta.copy(
                            connectionState = SimklConnectionState.ERROR.name,
                            lastErrorMessage = primaryErr,
                            updatedAtMs = clockMs()
                        )
                    )
                    return@withLock false
                }

                // 3. Regola confermata "Simkl prevale sempre come fonte di verità":
                // filtra eventuali scritture offline locali in conflitto con lo stato remoto appena scaricato
                // PRIMA di persistere libreria/playback e PRIMA di inviare la coda offline residua.
                discardConflictingPendingWritesInFavorOfSimkl(
                    profileId = profileId,
                    remoteLibraryItems = pulledShows + pulledMovies + pulledAnime,
                    remoteMoviesPlayback = fetchedRemoteMovies,
                    remoteEpisodesPlayback = fetchedRemoteEpisodes,
                    isAuthoritativeFullReconcile = runAuthoritativeFullReconcile,
                    triggerEvent = trigger.name
                )

                // 4. Persiste libreria importata e stato per singolo episodio
                val persistStats = persistParsedLibraryItems(
                    profileId = profileId,
                    shows = pulledShows,
                    movies = pulledMovies,
                    anime = pulledAnime,
                    existingErrorsCount = syncErrors.size,
                    isFullShowsSnapshot = fullShowsSnapshot,
                    isFullMoviesSnapshot = fullMoviesSnapshot,
                    isFullAnimeSnapshot = fullAnimeSnapshot
                )

                // 4b. Pruning autorevole su full reconcile periodico (rimuove elementi spariti da Simkl
                // anche qualora removed_from_list non fosse stato rilevato da un sync incrementale)
                if (isPeriodicFullReconcileDue) {
                    if (fullShowsSnapshot) {
                        removedCount += pruneMissingItemsForFullCategorySnapshot(
                            profileId = profileId,
                            category = SimklLibraryJsonParser.CATEGORY_SHOWS,
                            remoteKeys = pulledShows.map { it.stableKey }.toSet()
                        )
                    }
                    if (fullMoviesSnapshot) {
                        removedCount += pruneMissingItemsForFullCategorySnapshot(
                            profileId = profileId,
                            category = SimklLibraryJsonParser.CATEGORY_MOVIES,
                            remoteKeys = pulledMovies.map { it.stableKey }.toSet()
                        )
                    }
                    if (fullAnimeSnapshot) {
                        removedCount += pruneMissingItemsForFullCategorySnapshot(
                            profileId = profileId,
                            category = SimklLibraryJsonParser.CATEGORY_ANIME,
                            remoteKeys = pulledAnime.map { it.stableKey }.toSet()
                        )
                    }
                }

                // 4c. Verifica esplicita delle rimozioni (`extended=simkl_ids_only`) SOLO se `removed_from_list` è cambiato
                if (!runAuthoritativeFullReconcile) {
                    val removalCategoriesWithLocal = LinkedHashMap<String, List<SimklLibraryItemEntity>>()
                    if (showsRemovedChanged) {
                        val localShows = dao.getSimklLibraryByCategory(profileId, SimklLibraryJsonParser.CATEGORY_SHOWS)
                        if (localShows.isNotEmpty()) removalCategoriesWithLocal[SimklLibraryJsonParser.CATEGORY_SHOWS] = localShows
                    }
                    if (moviesRemovedChanged) {
                        val localMovies = dao.getSimklLibraryByCategory(profileId, SimklLibraryJsonParser.CATEGORY_MOVIES)
                        if (localMovies.isNotEmpty()) removalCategoriesWithLocal[SimklLibraryJsonParser.CATEGORY_MOVIES] = localMovies
                    }
                    if (animeRemovedChanged) {
                        val localAnime = dao.getSimklLibraryByCategory(profileId, SimklLibraryJsonParser.CATEGORY_ANIME)
                        if (localAnime.isNotEmpty()) removalCategoriesWithLocal[SimklLibraryJsonParser.CATEGORY_ANIME] = localAnime
                    }

                    if (removalCategoriesWithLocal.size >= 2) {
                        val aggOutcome = reconcileAggregatedRemovals(
                            profileId = profileId,
                            initialToken = activeToken,
                            localByCategory = removalCategoriesWithLocal,
                            triggerEvent = trigger.name
                        )
                        apiRequestsCompleted += aggOutcome.requestsCompleted
                        apiRequestsFailed += aggOutcome.requestsFailed
                        syncErrors.addAll(aggOutcome.errors)
                        if (aggOutcome.authLost) return@withLock false
                        if (!aggOutcome.succeeded) {
                            allLibraryRequestsSucceeded = false
                        } else {
                            removedCount += aggOutcome.removedCount
                        }
                    } else if (removalCategoriesWithLocal.size == 1) {
                        val (category, localCategoryItems) = removalCategoriesWithLocal.entries.first()
                        val remOutcome = reconcileCategoryRemovals(
                            profileId = profileId,
                            initialToken = activeToken,
                            category = category,
                            localItems = localCategoryItems,
                            triggerEvent = trigger.name
                        )
                        apiRequestsCompleted += remOutcome.requestsCompleted
                        apiRequestsFailed += remOutcome.requestsFailed
                        syncErrors.addAll(remOutcome.errors)
                        if (remOutcome.authLost) return@withLock false
                        if (!remOutcome.succeeded) {
                            allLibraryRequestsSucceeded = false
                        } else {
                            removedCount += remOutcome.removedCount
                        }
                    }
                }

                if (!allLibraryRequestsSucceeded) {
                    _apiMetrics.update { it.copy(incompleteSyncsCount = it.incompleteSyncsCount + 1) }
                    consecutiveErrorPeriodicByProfile.compute(profileId) { _, cur -> (cur ?: 0) + 1 }
                    val primaryErr = syncErrors.firstOrNull()?.message
                        ?: "Errore durante verifica rimozioni Simkl: snapshot precedente conservato."
                    val latestMeta = dao.getSimklProfileMeta(profileId) ?: SimklProfileSyncMetaEntity(profileId = profileId)
                    dao.upsertSimklProfileMeta(
                        latestMeta.copy(
                            connectionState = SimklConnectionState.ERROR.name,
                            lastErrorMessage = primaryErr,
                            updatedAtMs = clockMs()
                        )
                    )
                    return@withLock false
                }

                // 5. Riconcilia /sync/playback/* DOPO libreria (solo per i tipi effettivamente scaricati)
                if (fetchMoviesPlayback || fetchEpisodesPlayback) {
                    reconcileRemotePlayback(
                        profileId = profileId,
                        remoteMovies = fetchedRemoteMovies,
                        remoteEpisodes = fetchedRemoteEpisodes,
                        reconcileMovies = fetchMoviesPlayback,
                        reconcileEpisodes = fetchEpisodesPlayback
                    )
                }

                // 5b. Rete di sicurezza finale: garantisce che nessuna scheda serie TV in "Continua a guardare"
                // punti a un episodio ormai marcato VISTO o a una serie in stato escluso.
                reconcileTvContinueWatchingInvariant(profileId)

                // 6. Invia verso Simkl SOLO la coda offline residua non in conflitto
                flushPendingQueueInternal(profileId)
                val finalPending = dao.getSimklPendingScrobbles(profileId).size

                val summary = SimklSyncSummary(
                    showsFound = pulledShows.size,
                    moviesFound = pulledMovies.size,
                    animeFound = pulledAnime.size,
                    showsImported = persistStats.showsImported,
                    moviesImported = persistStats.moviesImported,
                    animeImported = persistStats.animeImported,
                    insertedCount = persistStats.insertedCount,
                    updatedCount = persistStats.updatedCount,
                    alreadyPresentCount = persistStats.alreadyPresentCount,
                    tmdbUnrecognizedCount = persistStats.tmdbUnrecognizedCount,
                    itemErrorsCount = persistStats.itemErrorsCount,
                    apiRequestsCompleted = apiRequestsCompleted,
                    apiRequestsFailed = apiRequestsFailed,
                    isIncremental = !runAuthoritativeFullReconcile,
                    removedCount = removedCount,
                    errors = syncErrors.take(12)
                )
                updateSyncSummary(profileId, summary)

                // Commit transazionale dello snapshot raw JSON e di tutti i watermark SOLO al 100% successo
                val latestMeta = dao.getSimklProfileMeta(profileId) ?: SimklProfileSyncMetaEntity(profileId = profileId)
                val committedAtMs = clockMs()
                dao.upsertSimklProfileMeta(
                    latestMeta.copy(
                        connectionState = if (finalPending > 0) {
                            SimklConnectionState.OFFLINE_QUEUE_PENDING.name
                        } else {
                            SimklConnectionState.CONNECTED_SYNCED.name
                        },
                        lastActivitiesAll = activities.all ?: latestMeta.lastActivitiesAll,
                        activitiesSnapshotJson = rawActivitiesJson,
                        lastMoviesPlaybackTs = activities.moviesPlaybackTimestamp ?: latestMeta.lastMoviesPlaybackTs,
                        lastShowsPlaybackTs = activities.showsPlaybackTimestamp ?: latestMeta.lastShowsPlaybackTs,
                        lastAnimePlaybackTs = activities.animePlaybackTimestamp ?: latestMeta.lastAnimePlaybackTs,
                        lastMoviesWatchedTs = activities.moviesWatchedTimestamp ?: latestMeta.lastMoviesWatchedTs,
                        lastShowsWatchedTs = activities.showsWatchedTimestamp ?: latestMeta.lastShowsWatchedTs,
                        lastAnimeWatchedTs = activities.animeWatchedTimestamp ?: latestMeta.lastAnimeWatchedTs,
                        lastShowsAllTs = activities.showsAllTimestamp ?: latestMeta.lastShowsAllTs,
                        lastMoviesAllTs = activities.moviesAllTimestamp ?: latestMeta.lastMoviesAllTs,
                        lastAnimeAllTs = activities.animeAllTimestamp ?: latestMeta.lastAnimeAllTs,
                        lastRemovedShowsTs = activities.showsRemovedTimestamp ?: latestMeta.lastRemovedShowsTs,
                        lastRemovedMoviesTs = activities.moviesRemovedTimestamp ?: latestMeta.lastRemovedMoviesTs,
                        lastRemovedAnimeTs = activities.animeRemovedTimestamp ?: latestMeta.lastRemovedAnimeTs,
                        initialFullImportDone = true,
                        lastFullReconcileAtMs = if (runAuthoritativeFullReconcile || latestMeta.lastFullReconcileAtMs == null) {
                            committedAtMs
                        } else {
                            latestMeta.lastFullReconcileAtMs
                        },
                        lastSyncAtMs = committedAtMs,
                        lastErrorMessage = null,
                        updatedAtMs = committedAtMs
                    )
                )
                consecutiveErrorPeriodicByProfile[profileId] = 0
                consecutiveUnchangedPeriodicByProfile[profileId] = 0
                _apiMetrics.update {
                    if (runAuthoritativeFullReconcile) {
                        it.copy(fullSyncsCount = it.fullSyncsCount + 1)
                    } else {
                        it.copy(incrementalSyncsCount = it.incrementalSyncsCount + 1)
                    }
                }
                _syncRevision.update { it + 1L }
                true
            } finally {
                updateSyncProgress(profileId, null)
            }
        }
    }

    /**
     * Registra un checkpoint locale di riproduzione durante lo streaming (es. ogni 60s) senza inviare
     * traffico di rete continuo: se l'app crasha o la Fire TV si spegne all'improvviso, il checkpoint
     * resta nella coda persistente e verrà sincronizzato su Simkl al riavvio successivo (salvo conflitto
     * con uno stato più autorevole su Simkl).
     */
    fun recordPlaybackCrashCheckpoint(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long,
        episodeTitle: String? = null
    ) {
        if (!configProvider().isClientIdConfigured) return
        if (positionMs < 5_000L || durationMs <= 0L) return
        val allowZeroEp = seasonNumber == 0
        if (item.isSeries && (seasonNumber == null || episodeNumber == null || (if (allowZeroEp) episodeNumber < 0 else episodeNumber <= 0))) {
            return
        }
        val job = scope.launch {
            if (!tokenStore.hasTokens(profileId)) return@launch
            val pct = ((positionMs.toDouble() / durationMs.toDouble()) * 100.0).coerceIn(0.5, 95.0)
            val contentKey = buildContentKey(item.mediaType, item.id, seasonNumber, episodeNumber)
            val existingPending = dao.getSimklPendingScrobbles(profileId)
            val existingForKey = existingPending.firstOrNull { it.contentKey == contentKey }
            // Non sovrascrivere mai uno stop/completamento già accodato
            if (existingForKey != null && existingForKey.action == "stop" && existingForKey.progressPercent >= existingForKey.minProgress.toDouble()) {
                return@launch
            }
            val libItem = dao.getSimklLibraryByTmdb(profileId, item.id, item.mediaType).firstOrNull()
            val maxExistingTs = existingPending.maxOfOrNull { it.updatedAtMs } ?: 0L
            val now = maxOf(clockMs(), maxExistingTs + 1L)
            dao.upsertSimklPendingScrobble(
                SimklPendingScrobbleEntity(
                    profileId = profileId,
                    contentKey = contentKey,
                    tmdbId = item.id,
                    mediaType = item.mediaType,
                    title = item.title,
                    releaseYear = item.releaseYear,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    action = "pause",
                    progressPercent = pct,
                    minProgress = SimklConfig.DEFAULT_MIN_PROGRESS_STOP,
                    createdAtMs = now,
                    updatedAtMs = now,
                    attemptCount = 0,
                    nextRetryAtMs = 0L, // Bloccato finche la riproduzione e attiva, pronto al flush immediato al riavvio post-crash
                    episodeTitle = episodeTitle?.takeIf { it.isNotBlank() },
                    simklId = libItem?.simklId?.takeIf { it > 0L },
                    imdbId = libItem?.imdbId,
                    tvdbId = libItem?.tvdbId,
                    triggerEvent = "player_crash_checkpoint"
                )
            )
        }
        trackWriteJob(profileId, job)
    }

    /**
     * Applica la politica "Simkl prevale sempre come fonte di verità":
     * se un elemento nella coda offline locale entra in conflitto con uno stato remoto aggiornato su Simkl
     * (titolo completato/hold/dropped/plantowatch, rimosso, episodio modificato o progresso playback su Simkl),
     * scarta la scrittura offline obsoleta e lascia prevalere Simkl.
     */
    private suspend fun discardConflictingPendingWritesInFavorOfSimkl(
        profileId: String,
        remoteLibraryItems: List<SimklLibraryParsedItem>,
        remoteMoviesPlayback: List<SimklPlaybackItemDto>,
        remoteEpisodesPlayback: List<SimklPlaybackItemDto>,
        isAuthoritativeFullReconcile: Boolean,
        triggerEvent: String
    ) {
        val pending = dao.getSimklPendingScrobbles(profileId)
        if (pending.isEmpty()) return

        val existingLibByTmdb = dao.getSimklLibraryForProfile(profileId)
            .filter { it.tmdbId != null }
            .associateBy { it.tmdbMediaType to it.tmdbId!! }

        val remoteLibByTmdb = HashMap<Pair<String, Int>, SimklLibraryParsedItem>()
        for (item in remoteLibraryItems) {
            val tmdb = item.tmdbId?.takeIf { it > 0 }
                ?: dao.getSimklLibraryForProfile(profileId).firstOrNull { it.stableKey == item.stableKey }?.tmdbId
            if (tmdb != null && tmdb > 0) {
                remoteLibByTmdb[item.tmdbMediaType to tmdb] = item
            }
        }

        val remotePlaybackKeys = HashSet<String>()
        for (dto in remoteMoviesPlayback) {
            val tmdb = dto.movie?.ids?.tmdb?.takeIf { it > 0 }
                ?: dto.movie?.ids?.simkl?.let { sid ->
                    dao.getSimklLibraryForProfile(profileId).firstOrNull { it.simklId == sid && it.tmdbMediaType == "movie" }?.tmdbId
                }
            if (tmdb != null && tmdb > 0) {
                remotePlaybackKeys.add("movie_$tmdb")
            }
        }
        for (dto in remoteEpisodesPlayback) {
            val epNum = dto.episode?.resolvedEpisodeNumber ?: continue
            val seasonNum = dto.episode.season ?: 1
            val tmdb = dto.show?.ids?.tmdb?.takeIf { it > 0 }
                ?: dto.show?.ids?.simkl?.let { sid ->
                    dao.getSimklLibraryForProfile(profileId).firstOrNull { it.simklId == sid && it.tmdbMediaType == "tv" }?.tmdbId
                }
            if (tmdb != null && tmdb > 0) {
                remotePlaybackKeys.add("tv_${tmdb}_s${seasonNum}_e${epNum}")
            }
        }

        for (entry in pending) {
            // Non scartare mai un evento relativo al contenuto attualmente in riproduzione attiva
            if (isActivePlaybackTarget(profileId, entry.mediaType, entry.tmdbId, entry.seasonNumber, entry.episodeNumber)) {
                continue
            }

            val remoteLib = remoteLibByTmdb[entry.mediaType to entry.tmdbId]
            val existingLib = existingLibByTmdb[entry.mediaType to entry.tmdbId]
            val isPlaybackAction = entry.action in setOf("start", "pause", "stop", "delete_playback")
            val isHistoryAction = entry.action in setOf("history_add", "history_remove")
            val isListAction = entry.action in setOf("list_add", "list_remove_ptw")

            var conflictReason: String? = null

            if (remoteLib != null) {
                val remoteStatusTerminal = remoteLib.status in setOf(
                    SimklLibraryJsonParser.STATUS_COMPLETED,
                    SimklLibraryJsonParser.STATUS_HOLD,
                    SimklLibraryJsonParser.STATUS_DROPPED,
                    SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH
                )
                val statusChangedOnSimkl = existingLib == null || existingLib.status != remoteLib.status
                val remoteLastWatchedMs = parseIsoUtcMillis(remoteLib.lastWatchedAt)
                val remoteNewerOrDifferent = remoteLastWatchedMs >= entry.updatedAtMs || statusChangedOnSimkl

                if (isPlaybackAction) {
                    val isCompletionStop = entry.action == "stop" && entry.progressPercent >= entry.minProgress.toDouble()
                    if (remoteStatusTerminal && (remoteLib.status != SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH || statusChangedOnSimkl)) {
                        conflictReason = "remote_status_${remoteLib.status}"
                    } else if (entry.mediaType == "tv" && entry.seasonNumber != null && entry.episodeNumber != null && !isCompletionStop) {
                        val entryPair = entry.seasonNumber to entry.episodeNumber
                        if (remoteLib.hasSeasonsField) {
                            val remoteEp = remoteLib.seasons
                                .firstOrNull { it.seasonNumber == entry.seasonNumber }
                                ?.episodes
                                ?.firstOrNull { it.episodeNumber == entry.episodeNumber }
                            val hasNewerRemoteEpWatched = entry.seasonNumber > 0 && remoteLib.seasons.any { s ->
                                s.seasonNumber > 0 && s.episodes.any { ep ->
                                    ep.watched && compareEpisodePairs(entryPair, s.seasonNumber to ep.episodeNumber) < 0
                                }
                            }
                            if (remoteEp?.watched == true) {
                                conflictReason = "remote_episode_already_watched"
                            } else if (hasNewerRemoteEpWatched && remoteLastWatchedMs >= entry.updatedAtMs) {
                                conflictReason = "remote_episode_superseded"
                            }
                        } else {
                            val remoteNext = if (remoteLib.nextToWatchSeason != null && remoteLib.nextToWatchEpisode != null) {
                                remoteLib.nextToWatchSeason to remoteLib.nextToWatchEpisode
                            } else {
                                parseEpisodeCode(remoteLib.nextToWatch)
                            }
                            val remoteLast = parseEpisodeCode(remoteLib.lastWatchedEpisodeCode)
                            val supersededByRemoteCode = entry.seasonNumber > 0 && remoteLastWatchedMs >= entry.updatedAtMs && (
                                (remoteNext != null && remoteNext.first > 0 && compareEpisodePairs(entryPair, remoteNext) < 0) ||
                                    (remoteLast != null && remoteLast.first > 0 && compareEpisodePairs(entryPair, remoteLast) <= 0)
                                )
                            val alreadyWatchedLocally = (
                                dao.getSimklEpisodeState(profileId, entry.tmdbId, entry.seasonNumber, entry.episodeNumber)?.watched == true ||
                                    dao.getCompletedMedia(
                                        PlayerSegmentKeys.completedMediaKey(entry.tmdbId, "tv", entry.seasonNumber, entry.episodeNumber),
                                        profileId
                                    ) != null
                                ) && remoteLastWatchedMs >= entry.updatedAtMs
                            if (supersededByRemoteCode || alreadyWatchedLocally) {
                                conflictReason = "remote_episode_superseded"
                            }
                        }
                        if (conflictReason == null && entry.contentKey in remotePlaybackKeys) {
                            conflictReason = "remote_playback_authoritative"
                        }
                    } else if (entry.contentKey in remotePlaybackKeys) {
                        conflictReason = "remote_playback_authoritative"
                    }
                } else if (isHistoryAction && remoteNewerOrDifferent) {
                    if (remoteStatusTerminal || remoteLib.hasSeasonsField) {
                        conflictReason = "remote_history_authoritative_${remoteLib.status}"
                    }
                } else if (isListAction && statusChangedOnSimkl) {
                    conflictReason = "remote_list_authoritative_${remoteLib.status}"
                }
            } else if (isPlaybackAction && entry.contentKey in remotePlaybackKeys) {
                conflictReason = "remote_playback_authoritative"
            } else if (isAuthoritativeFullReconcile && existingLib != null && isPlaybackAction) {
                // Il titolo esisteva localmente ma è stato completamente rimosso da Simkl
                conflictReason = "remote_removed_from_simkl"
            }

            if (conflictReason != null) {
                dao.deleteSimklPendingScrobble(profileId, entry.contentKey)
                dao.deleteSimklBaseline(profileId, entry.contentKey)
                recordDiagnostic(
                    endpoint = endpointLabelForAction(entry.action),
                    contentType = entry.mediaType,
                    profileId = profileId,
                    triggerEvent = triggerEvent,
                    outcome = "CONFLICT_SIMKL_AUTHORITATIVE_DISCARDED",
                    simklId = entry.simklId,
                    tmdbId = entry.tmdbId,
                    errorDetail = conflictReason
                )
            }
        }
    }

    private suspend fun pruneMissingItemsForFullCategorySnapshot(
        profileId: String,
        category: String,
        remoteKeys: Set<String>
    ): Int {
        val localItems = dao.getSimklLibraryByCategory(profileId, category)
        val removedEntities = localItems.filter { it.stableKey !in remoteKeys }
        if (removedEntities.isEmpty()) return 0
        val keysToRemove = removedEntities.map { it.stableKey }
        keysToRemove.chunked(200).forEach { chunk ->
            dao.deleteSimklLibraryItemsByKeys(profileId, chunk)
            dao.deleteSimklEpisodeStatesBySeriesKeys(profileId, chunk)
        }
        for (removed in removedEntities) {
            val tmdbId = removed.tmdbId ?: continue
            if (!isActivePlaybackTarget(profileId, removed.tmdbMediaType, tmdbId)) {
                dao.deleteWatchProgress("${removed.tmdbMediaType}_$tmdbId", profileId)
                dao.deleteSimklBaselinesForMedia(profileId, tmdbId, removed.tmdbMediaType)
            }
            if (removed.tmdbMediaType == "tv") {
                dao.deleteCompletedEpisodesForSeries(tmdbId, profileId)
                dao.deleteEpisodeProgressForSeries(tmdbId, profileId)
            } else {
                dao.deleteCompletedMedia(
                    PlayerSegmentKeys.completedMediaKey(tmdbId, "movie"),
                    profileId
                )
            }
        }
        recordDiagnostic(
            endpoint = "FULL_RECONCILE/$category",
            contentType = category,
            profileId = profileId,
            triggerEvent = "PERIODIC_FULL_PRUNE",
            outcome = "OK_PRUNED_${keysToRemove.size}"
        )
        return keysToRemove.size
    }

    fun enqueueScrobbleStart(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long,
        episodeTitle: String? = null
    ) {
        enqueueScrobbleEvent(
            profileId = profileId,
            item = item,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            action = "start",
            positionMs = positionMs,
            durationMs = durationMs,
            minProgress = SimklConfig.DEFAULT_MIN_PROGRESS_STOP,
            triggerEvent = "player_start"
        )
    }

    fun enqueueScrobblePause(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long,
        episodeTitle: String? = null
    ) {
        enqueueScrobbleEvent(
            profileId = profileId,
            item = item,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            action = "pause",
            positionMs = positionMs,
            durationMs = durationMs,
            minProgress = SimklConfig.DEFAULT_MIN_PROGRESS_STOP,
            triggerEvent = "player_pause"
        )
    }

    fun enqueueScrobbleStop(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long,
        isExplicitCompletion: Boolean = false,
        episodeTitle: String? = null
    ) {
        enqueueScrobbleEvent(
            profileId = profileId,
            item = item,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            action = "stop",
            positionMs = if (isExplicitCompletion && durationMs > 0L) durationMs else positionMs,
            durationMs = durationMs,
            minProgress = if (isExplicitCompletion) SimklConfig.COMPLETE_MIN_PROGRESS_STOP else SimklConfig.DEFAULT_MIN_PROGRESS_STOP,
            forcedPercent = if (isExplicitCompletion) 100.0 else null,
            triggerEvent = if (isExplicitCompletion) "player_complete" else "player_stop"
        )
    }

    /**
     * Sincronizza verso Simkl (`POST /sync/history`) uno o più episodi marcati visti manualmente
     * (singolo episodio o intera stagione in un unico batch atomico).
     */
    fun enqueueEpisodesWatched(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int,
        episodeNumbers: List<Int>,
        triggerEvent: String = "manual_mark_watched"
    ) {
        if (!configProvider().isClientIdConfigured) return
        if (!item.isSeries || seasonNumber < 0) return
        val distinctEps = episodeNumbers
            .filter { if (seasonNumber == 0) it >= 0 else it > 0 }
            .distinct()
            .sorted()
        if (distinctEps.isEmpty()) return

        val job = scope.launch {
            if (!tokenStore.hasTokens(profileId)) return@launch
            val libItem = dao.getSimklLibraryByTmdb(profileId, item.id, "tv").firstOrNull()
            val now = clockMs()
            val contentKey = "history_add_tv_${item.id}_s$seasonNumber"
            val removeKey = "history_remove_tv_${item.id}_s$seasonNumber"

            val existingPending = dao.getSimklPendingScrobbles(profileId)
            val existingAddPairs = existingPending
                .firstOrNull { it.contentKey == contentKey }
                ?.let { SimklLibraryJsonParser.deserializeBatchEpisodes(it.batchEpisodesJson) }
                .orEmpty()
            val mergedPairs = (existingAddPairs + distinctEps.map { seasonNumber to it }).distinct()

            // Se nella coda c'è una rimozione pendente per gli stessi episodi, rimuovili da quel batch
            existingPending.firstOrNull { it.contentKey == removeKey }?.let { pendingRem ->
                val remainingRem = SimklLibraryJsonParser.deserializeBatchEpisodes(pendingRem.batchEpisodesJson)
                    .filterNot { (s, e) -> s == seasonNumber && e in distinctEps }
                if (remainingRem.isEmpty()) {
                    dao.deleteSimklPendingScrobble(profileId, removeKey)
                } else {
                    dao.upsertSimklPendingScrobble(
                        pendingRem.copy(
                            batchEpisodesJson = SimklLibraryJsonParser.serializeBatchEpisodes(remainingRem),
                            updatedAtMs = now
                        )
                    )
                }
            }

            dao.upsertSimklPendingScrobble(
                SimklPendingScrobbleEntity(
                    profileId = profileId,
                    contentKey = contentKey,
                    tmdbId = item.id,
                    mediaType = "tv",
                    title = item.title,
                    releaseYear = item.releaseYear,
                    seasonNumber = seasonNumber,
                    episodeNumber = distinctEps.first(),
                    action = "history_add",
                    progressPercent = 100.0,
                    minProgress = SimklConfig.COMPLETE_MIN_PROGRESS_STOP,
                    createdAtMs = now,
                    updatedAtMs = now,
                    attemptCount = 0,
                    nextRetryAtMs = 0L,
                    simklId = libItem?.simklId?.takeIf { it > 0L },
                    imdbId = libItem?.imdbId,
                    tvdbId = libItem?.tvdbId,
                    batchEpisodesJson = SimklLibraryJsonParser.serializeBatchEpisodes(mergedPairs),
                    triggerEvent = triggerEvent
                )
            )
            updateLocalSimklEpisodeStatesOptimistically(
                profileId = profileId,
                tmdbId = item.id,
                libItem = libItem,
                episodes = distinctEps.map { seasonNumber to it },
                watched = true,
                nowMs = now
            )
            flushAndUpdateQueueState(profileId)
        }
        trackWriteJob(profileId, job)
    }

    /**
     * Sincronizza verso Simkl (`POST /sync/history/remove`) la rimozione dello stato visto
     * per uno o più episodi deselezionati localmente su VixFlix.
     */
    fun enqueueEpisodesUnwatched(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int,
        episodeNumbers: List<Int>,
        triggerEvent: String = "manual_unmark_watched"
    ) {
        if (!configProvider().isClientIdConfigured) return
        if (!item.isSeries || seasonNumber < 0) return
        val distinctEps = episodeNumbers
            .filter { if (seasonNumber == 0) it >= 0 else it > 0 }
            .distinct()
            .sorted()
        if (distinctEps.isEmpty()) return

        val job = scope.launch {
            if (!tokenStore.hasTokens(profileId)) return@launch
            val libItem = dao.getSimklLibraryByTmdb(profileId, item.id, "tv").firstOrNull()
            val now = clockMs()
            val contentKey = "history_remove_tv_${item.id}_s$seasonNumber"
            val addKey = "history_add_tv_${item.id}_s$seasonNumber"

            val existingPending = dao.getSimklPendingScrobbles(profileId)
            val existingRemPairs = existingPending
                .firstOrNull { it.contentKey == contentKey }
                ?.let { SimklLibraryJsonParser.deserializeBatchEpisodes(it.batchEpisodesJson) }
                .orEmpty()
            val mergedPairs = (existingRemPairs + distinctEps.map { seasonNumber to it }).distinct()

            existingPending.firstOrNull { it.contentKey == addKey }?.let { pendingAdd ->
                val remainingAdd = SimklLibraryJsonParser.deserializeBatchEpisodes(pendingAdd.batchEpisodesJson)
                    .filterNot { (s, e) -> s == seasonNumber && e in distinctEps }
                if (remainingAdd.isEmpty()) {
                    dao.deleteSimklPendingScrobble(profileId, addKey)
                } else {
                    dao.upsertSimklPendingScrobble(
                        pendingAdd.copy(
                            batchEpisodesJson = SimklLibraryJsonParser.serializeBatchEpisodes(remainingAdd),
                            updatedAtMs = now
                        )
                    )
                }
            }

            dao.upsertSimklPendingScrobble(
                SimklPendingScrobbleEntity(
                    profileId = profileId,
                    contentKey = contentKey,
                    tmdbId = item.id,
                    mediaType = "tv",
                    title = item.title,
                    releaseYear = item.releaseYear,
                    seasonNumber = seasonNumber,
                    episodeNumber = distinctEps.first(),
                    action = "history_remove",
                    progressPercent = 0.0,
                    minProgress = SimklConfig.DEFAULT_MIN_PROGRESS_STOP,
                    createdAtMs = now,
                    updatedAtMs = now,
                    attemptCount = 0,
                    nextRetryAtMs = 0L,
                    simklId = libItem?.simklId?.takeIf { it > 0L },
                    imdbId = libItem?.imdbId,
                    tvdbId = libItem?.tvdbId,
                    batchEpisodesJson = SimklLibraryJsonParser.serializeBatchEpisodes(mergedPairs),
                    triggerEvent = triggerEvent
                )
            )
            updateLocalSimklEpisodeStatesOptimistically(
                profileId = profileId,
                tmdbId = item.id,
                libItem = libItem,
                episodes = distinctEps.map { seasonNumber to it },
                watched = false,
                nowMs = now
            )
            flushAndUpdateQueueState(profileId)
        }
        trackWriteJob(profileId, job)
    }

    /**
     * Sincronizza verso Simkl (`POST /sync/history`) il completamento esplicito di un film.
     */
    fun enqueueMovieWatched(
        profileId: String,
        item: MediaItem,
        triggerEvent: String = "manual_movie_watched"
    ) {
        if (!configProvider().isClientIdConfigured || item.isSeries) return
        val job = scope.launch {
            if (!tokenStore.hasTokens(profileId)) return@launch
            val libItem = dao.getSimklLibraryByTmdb(profileId, item.id, "movie").firstOrNull()
            val now = clockMs()
            dao.upsertSimklPendingScrobble(
                SimklPendingScrobbleEntity(
                    profileId = profileId,
                    contentKey = "history_add_movie_${item.id}",
                    tmdbId = item.id,
                    mediaType = "movie",
                    title = item.title,
                    releaseYear = item.releaseYear,
                    action = "history_add",
                    progressPercent = 100.0,
                    minProgress = SimklConfig.COMPLETE_MIN_PROGRESS_STOP,
                    createdAtMs = now,
                    updatedAtMs = now,
                    simklId = libItem?.simklId?.takeIf { it > 0L },
                    imdbId = libItem?.imdbId,
                    tvdbId = libItem?.tvdbId,
                    triggerEvent = triggerEvent
                )
            )
            flushAndUpdateQueueState(profileId)
        }
        trackWriteJob(profileId, job)
    }

    /**
     * Sincronizza verso Simkl (`POST /sync/add-to-list`) l'aggiunta di un film o serie TV
     * alla lista (default: `plantowatch` = "La mia lista" / Da guardare).
     */
    fun enqueueAddToList(
        profileId: String,
        item: MediaItem,
        listStatus: String = "plantowatch",
        triggerEvent: String = "watchlist_add_$listStatus"
    ) {
        if (!configProvider().isClientIdConfigured) return
        val normalizedStatus = listStatus.trim().lowercase().ifBlank { "plantowatch" }
        val job = scope.launch {
            if (!tokenStore.hasTokens(profileId)) return@launch
            val libItem = dao.getSimklLibraryByTmdb(profileId, item.id, item.mediaType).firstOrNull()
            val now = clockMs()
            dao.upsertSimklPendingScrobble(
                SimklPendingScrobbleEntity(
                    profileId = profileId,
                    contentKey = "list_sync_${item.mediaType}_${item.id}",
                    tmdbId = item.id,
                    mediaType = item.mediaType,
                    title = item.title,
                    releaseYear = item.releaseYear,
                    action = "list_add",
                    progressPercent = 0.0,
                    minProgress = SimklConfig.DEFAULT_MIN_PROGRESS_STOP,
                    createdAtMs = now,
                    updatedAtMs = now,
                    episodeTitle = normalizedStatus,
                    simklId = libItem?.simklId?.takeIf { it > 0L },
                    imdbId = libItem?.imdbId,
                    tvdbId = libItem?.tvdbId,
                    triggerEvent = triggerEvent
                )
            )
            flushAndUpdateQueueState(profileId)
        }
        trackWriteJob(profileId, job)
    }

    /**
     * Rimuove da Simkl (`POST /sync/history/remove`) un titolo rimosso da "La mia lista"
     * SOLO se su Simkl risulta in stato `plantowatch` (o non ha cronologia locale di visione),
     * evitando di cancellare per errore la cronologia `watching` o `completed`.
     */
    fun enqueueRemoveFromPlanToWatch(
        profileId: String,
        item: MediaItem,
        triggerEvent: String = "watchlist_remove_plantowatch"
    ) {
        if (!configProvider().isClientIdConfigured) return
        val job = scope.launch {
            if (!tokenStore.hasTokens(profileId)) return@launch
            val libItem = dao.getSimklLibraryByTmdb(profileId, item.id, item.mediaType).firstOrNull()
            if (libItem != null && libItem.status != "plantowatch") {
                return@launch
            }
            if (libItem != null) {
                dao.deleteSimklLibraryItemsByKeys(profileId, listOf(libItem.stableKey))
            }
            val now = clockMs()
            dao.upsertSimklPendingScrobble(
                SimklPendingScrobbleEntity(
                    profileId = profileId,
                    contentKey = "list_sync_${item.mediaType}_${item.id}",
                    tmdbId = item.id,
                    mediaType = item.mediaType,
                    title = item.title,
                    releaseYear = item.releaseYear,
                    action = "list_remove_ptw",
                    progressPercent = 0.0,
                    minProgress = SimklConfig.DEFAULT_MIN_PROGRESS_STOP,
                    createdAtMs = now,
                    updatedAtMs = now,
                    simklId = libItem?.simklId?.takeIf { it > 0L },
                    imdbId = libItem?.imdbId,
                    tvdbId = libItem?.tvdbId,
                    triggerEvent = triggerEvent
                )
            )
            flushAndUpdateQueueState(profileId)
        }
        trackWriteJob(profileId, job)
    }

    private suspend fun updateLocalSimklEpisodeStatesOptimistically(
        profileId: String,
        tmdbId: Int,
        libItem: SimklLibraryItemEntity?,
        episodes: List<Pair<Int, Int>>,
        watched: Boolean,
        nowMs: Long
    ) {
        val seriesKey = libItem?.stableKey ?: "ext_shows_tmdb_$tmdbId"
        val existingStates = dao.getSimklEpisodeStatesForSeries(profileId, seriesKey)
            .associateBy { it.seasonNumber to it.episodeNumber }
        val toUpsert = episodes.map { (s, e) ->
            val prev = existingStates[s to e]
            SimklEpisodeStateEntity(
                profileId = profileId,
                seriesStableKey = seriesKey,
                simklSeriesId = libItem?.simklId ?: prev?.simklSeriesId ?: 0L,
                tmdbSeriesId = tmdbId,
                seasonNumber = s,
                episodeNumber = e,
                simklEpisodeId = prev?.simklEpisodeId,
                watched = watched,
                watchedAt = if (watched) formatIsoUtcNow(nowMs) else null,
                watchedAtMs = if (watched) nowMs else 0L,
                updatedAtMs = nowMs
            )
        }
        if (toUpsert.isNotEmpty()) {
            dao.upsertSimklEpisodeStates(toUpsert)
        }
    }

    fun onContinueWatchingRemoved(
        profileId: String,
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int?,
        episodeNumber: Int?
    ) {
        val job = scope.launch {
            if (!tokenStore.hasTokens(profileId)) return@launch
            val contentKey = buildContentKey(mediaType, tmdbId, seasonNumber, episodeNumber)
            val baseline = dao.getSimklBaseline(profileId, contentKey)
            val playbackId = baseline?.simklPlaybackId
            if (playbackId != null && playbackId > 0L) {
                dao.upsertSimklPendingScrobble(
                    SimklPendingScrobbleEntity(
                        profileId = profileId,
                        contentKey = contentKey,
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        title = "",
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        action = "delete_playback",
                        progressPercent = 0.0,
                        simklPlaybackId = playbackId,
                        updatedAtMs = clockMs(),
                        triggerEvent = "remove_continue_watching"
                    )
                )
                flushPendingQueueInternal(profileId)
            }
            dao.deleteSimklBaseline(profileId, contentKey)
        }
        trackWriteJob(profileId, job)
    }

    private fun enqueueScrobbleEvent(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeTitle: String? = null,
        action: String,
        positionMs: Long,
        durationMs: Long,
        minProgress: Int,
        forcedPercent: Double? = null,
        triggerEvent: String = "player_$action"
    ) {
        if (!configProvider().isClientIdConfigured) return
        val allowZeroEp = seasonNumber == 0
        if (item.isSeries && (seasonNumber == null || episodeNumber == null || (if (allowZeroEp) episodeNumber < 0 else episodeNumber <= 0))) {
            return
        }
        val job = scope.launch {
            if (!tokenStore.hasTokens(profileId)) return@launch
            val computedPct = forcedPercent ?: computeProgressPercent(
                positionMs = positionMs,
                durationMs = durationMs,
                item = item,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber
            )
            // Non scartare mai silenziosamente pause/stop se il player non ha ancora notificato la durata:
            // uno stop a 0% o 0.5% chiude comunque la sessione "Watching now" su Simkl.
            val resolvedPct = when {
                computedPct > 0.0 -> computedPct
                action == "start" -> 0.0
                action == "stop" -> if (positionMs > 0L) 0.5 else 0.0
                action == "pause" && positionMs > 0L -> 0.5
                else -> return@launch
            }

            val libItem = dao.getSimklLibraryByTmdb(profileId, item.id, item.mediaType).firstOrNull()
            val epState = if (item.isSeries && seasonNumber != null && episodeNumber != null) {
                dao.getSimklEpisodeState(profileId, item.id, seasonNumber, episodeNumber)
            } else {
                null
            }

            val contentKey = buildContentKey(item.mediaType, item.id, seasonNumber, episodeNumber)
            val allPending = dao.getSimklPendingScrobbles(profileId)
            val existingEntry = allPending.firstOrNull { it.contentKey == contentKey }
            val maxExistingTs = allPending.maxOfOrNull { it.updatedAtMs } ?: 0L
            val now = maxOf(clockMs(), maxExistingTs + 1L)

            // Deduplicazione di eventi scrobble ridondanti (start duplicati o pause <1% entro 15s);
            // non sopprime MAI stop o completamento esplicito.
            if (action != "stop" && existingEntry == null) {
                val lastSent = lastSentScrobbleByProfileKey["$profileId|$contentKey"]
                if (lastSent != null && (now - lastSent.third) in 0L..15_000L) {
                    val isDupStart = action == "start" && lastSent.first == "start"
                    val isMicroPause = action == "pause" && lastSent.first == "pause" && abs(resolvedPct - lastSent.second) < 1.0
                    if (isDupStart || isMicroPause) {
                        return@launch
                    }
                }
            }

            // Non consentire mai a un nuovo `start` a bassa percentuale di sovrascrivere in coda offline
            // un completamento (`stop` >= minProgress) non ancora inviato: promuovi il completamento a `history_add`.
            if (action == "start" &&
                existingEntry != null &&
                existingEntry.action == "stop" &&
                existingEntry.progressPercent >= existingEntry.minProgress.toDouble()
            ) {
                val historyKey = if (item.isSeries && seasonNumber != null) {
                    "history_add_tv_${item.id}_s$seasonNumber"
                } else {
                    "history_add_movie_${item.id}"
                }
                val batchJson = if (item.isSeries && seasonNumber != null && episodeNumber != null) {
                    val existingAddPairs = allPending
                        .firstOrNull { it.contentKey == historyKey }
                        ?.let { SimklLibraryJsonParser.deserializeBatchEpisodes(it.batchEpisodesJson) }
                        .orEmpty()
                    SimklLibraryJsonParser.serializeBatchEpisodes((existingAddPairs + (seasonNumber to episodeNumber)).distinct())
                } else {
                    null
                }
                dao.upsertSimklPendingScrobble(
                    existingEntry.copy(
                        contentKey = historyKey,
                        action = "history_add",
                        progressPercent = 100.0,
                        batchEpisodesJson = batchJson,
                        updatedAtMs = now
                    )
                )
            }

            dao.upsertSimklPendingScrobble(
                SimklPendingScrobbleEntity(
                    profileId = profileId,
                    contentKey = contentKey,
                    tmdbId = item.id,
                    mediaType = item.mediaType,
                    title = item.title,
                    releaseYear = item.releaseYear,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    action = action,
                    progressPercent = resolvedPct.coerceIn(0.0, 100.0),
                    minProgress = minProgress,
                    createdAtMs = now,
                    updatedAtMs = now,
                    attemptCount = 0,
                    nextRetryAtMs = 0L,
                    episodeTitle = episodeTitle?.takeIf { it.isNotBlank() },
                    simklId = libItem?.simklId?.takeIf { it > 0L },
                    imdbId = libItem?.imdbId,
                    tvdbId = libItem?.tvdbId,
                    simklEpisodeId = epState?.simklEpisodeId?.takeIf { it > 0L },
                    triggerEvent = triggerEvent
                )
            )
            if (action == "start") {
                transitionLibraryStatusOnPlayback(
                    profileId = profileId,
                    tmdbId = item.id,
                    tmdbMediaType = item.mediaType,
                    action = action,
                    progressPercent = resolvedPct,
                    minProgress = minProgress
                )
            }
            if (action == "stop" && resolvedPct >= minProgress.toDouble() && item.isSeries && seasonNumber != null && episodeNumber != null) {
                updateLocalSimklEpisodeStatesOptimistically(
                    profileId = profileId,
                    tmdbId = item.id,
                    libItem = libItem,
                    episodes = listOf(seasonNumber to episodeNumber),
                    watched = true,
                    nowMs = now
                )
            }
            flushAndUpdateQueueState(profileId)
        }
        trackWriteJob(profileId, job)
    }

    private suspend fun flushAndUpdateQueueState(profileId: String) {
        val flushed = flushPendingQueueInternal(profileId)
        val meta = dao.getSimklProfileMeta(profileId)
        if (!flushed) {
            if (meta != null &&
                meta.connectionState != SimklConnectionState.AUTH_EXPIRED.name &&
                meta.connectionState != SimklConnectionState.ERROR.name
            ) {
                updateMetaState(profileId, SimklConnectionState.OFFLINE_QUEUE_PENDING)
            }
        } else if (meta != null &&
            (meta.connectionState == SimklConnectionState.OFFLINE_QUEUE_PENDING.name ||
                meta.connectionState == SimklConnectionState.ERROR.name)
        ) {
            if (dao.getSimklPendingScrobbles(profileId).isEmpty()) {
                updateMetaState(profileId, SimklConnectionState.CONNECTED_SYNCED, clearError = true)
            }
        }
    }

    suspend fun flushPendingQueueNow(profileId: String): Boolean = withContext(Dispatchers.IO) {
        flushPendingQueueInternal(profileId)
    }

    private suspend fun flushPendingQueueInternal(profileId: String): Boolean {
        return writeMutexFor(profileId).withLock {
            var allSucceeded = true
            var safetyPass = 0
            while (safetyPass < 6) {
                safetyPass++
                val pending = dao.getSimklPendingScrobbles(profileId)
                if (pending.isEmpty()) break

                val now = clockMs()
                var sentOrRetriedAnyInPass = false
                var newerArrivalDetected = false
                allSucceeded = true

                for (entry in pending) {
                    if (entry.triggerEvent == "player_crash_checkpoint" &&
                        isActivePlaybackTarget(profileId, entry.mediaType, entry.tmdbId, entry.seasonNumber, entry.episodeNumber)
                    ) {
                        continue
                    }
                    if (entry.nextRetryAtMs > now) {
                        allSucceeded = false
                        continue
                    }
                    val token = getValidAccessToken(profileId, forceRefresh = false)
                    if (token == null) {
                        allSucceeded = false
                        break
                    }

                    val lastPost = lastPostAtByProfile[profileId] ?: 0L
                    val elapsed = clockMs() - lastPost
                    if (elapsed in 0 until minPostIntervalMs) {
                        delay(minPostIntervalMs - elapsed)
                    }

                    val result = try {
                        sendSinglePendingEntry(profileId, token, entry)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        recordDiagnostic(
                            endpoint = endpointLabelForAction(entry.action),
                            contentType = entry.mediaType,
                            profileId = profileId,
                            triggerEvent = entry.triggerEvent.ifBlank { entry.action },
                            outcome = "NETWORK_RETRY",
                            simklId = entry.simklId,
                            tmdbId = entry.tmdbId,
                            errorDetail = e.javaClass.simpleName
                        )
                        WriteOutcome.RETRY(delayMs = computeBackoffMs(entry.attemptCount))
                    } catch (e: Exception) {
                        recordDiagnostic(
                            endpoint = endpointLabelForAction(entry.action),
                            contentType = entry.mediaType,
                            profileId = profileId,
                            triggerEvent = entry.triggerEvent.ifBlank { entry.action },
                            outcome = "ERROR_RETRY",
                            simklId = entry.simklId,
                            tmdbId = entry.tmdbId,
                            errorDetail = e.javaClass.simpleName
                        )
                        WriteOutcome.RETRY(delayMs = computeBackoffMs(entry.attemptCount))
                    } finally {
                        lastPostAtByProfile[profileId] = clockMs()
                    }

                    when (result) {
                        is WriteOutcome.SUCCESS -> {
                            sentOrRetriedAnyInPass = true
                            val deletedCount = dao.deleteSimklPendingScrobbleIfUnchanged(
                                profileId = profileId,
                                contentKey = entry.contentKey,
                                expectedUpdatedAtMs = entry.updatedAtMs,
                                expectedAction = entry.action
                            )
                            if (deletedCount == 0) {
                                // Un evento più recente (es. pause/stop subito dopo start) ha sovrascritto la riga mentre la HTTP era in volo:
                                // NON cancellare il nuovo evento e riesegui subito un altro passaggio.
                                newerArrivalDetected = true
                                allSucceeded = false
                            }
                            if (entry.action == "start" || entry.action == "pause" || entry.action == "stop") {
                                transitionLibraryStatusOnPlayback(
                                    profileId = profileId,
                                    tmdbId = entry.tmdbId,
                                    tmdbMediaType = entry.mediaType,
                                    action = entry.action,
                                    progressPercent = entry.progressPercent,
                                    minProgress = entry.minProgress
                                )
                            } else if (entry.action == "history_add" && entry.mediaType == "movie") {
                                transitionLibraryStatusOnPlayback(
                                    profileId = profileId,
                                    tmdbId = entry.tmdbId,
                                    tmdbMediaType = "movie",
                                    action = "stop",
                                    progressPercent = 100.0,
                                    minProgress = 100
                                )
                            }
                            if (entry.action == "start" || entry.action == "pause" || entry.action == "stop" || entry.action == "delete_playback") {
                                val existingBaseline = dao.getSimklBaseline(profileId, entry.contentKey)
                                if (entry.action == "delete_playback" || (entry.action == "stop" && entry.progressPercent >= entry.minProgress.toDouble())) {
                                    dao.deleteSimklBaseline(profileId, entry.contentKey)
                                } else {
                                    val nowSaved = clockMs()
                                    dao.upsertSimklBaseline(
                                        (existingBaseline ?: SimklPlaybackBaselineEntity(
                                            profileId = profileId,
                                            contentKey = entry.contentKey,
                                            tmdbId = entry.tmdbId,
                                            mediaType = entry.mediaType,
                                            seasonNumber = entry.seasonNumber,
                                            episodeNumber = entry.episodeNumber
                                        )).copy(
                                            simklPlaybackId = result.simklId ?: existingBaseline?.simklPlaybackId,
                                            lastSyncedProgressPercent = entry.progressPercent,
                                            lastLocalUpdatedAtMs = maxOf(entry.updatedAtMs, existingBaseline?.lastLocalUpdatedAtMs ?: 0L),
                                            localDirty = false,
                                            updatedAtMs = nowSaved
                                        )
                                    )
                                }
                            }
                            val shouldSchedulePostScrobble =
                                (entry.action == "stop" && entry.progressPercent >= entry.minProgress.toDouble()) ||
                                    entry.action == "history_add" ||
                                    entry.action == "history_remove"
                            if (shouldSchedulePostScrobble) {
                                scheduleDebouncedPostScrobbleSync(profileId)
                            }
                        }
                        is WriteOutcome.RETRY -> {
                            sentOrRetriedAnyInPass = true
                            allSucceeded = false
                            val updatedCount = dao.updateSimklPendingScrobbleRetryIfUnchanged(
                                profileId = profileId,
                                contentKey = entry.contentKey,
                                expectedUpdatedAtMs = entry.updatedAtMs,
                                expectedAction = entry.action,
                                newAttemptCount = entry.attemptCount + 1,
                                nextRetryAtMs = clockMs() + result.delayMs
                            )
                            if (updatedCount == 0) {
                                newerArrivalDetected = true
                            }
                        }
                        WriteOutcome.AUTH_FAILED -> {
                            allSucceeded = false
                        }
                        WriteOutcome.PERMISSION_DENIED -> {
                            allSucceeded = false
                            updateMetaState(
                                profileId,
                                SimklConnectionState.ERROR,
                                error = "Permessi Simkl insufficienti (HTTP 403). Verifica o ricollega l'account."
                            )
                            dao.updateSimklPendingScrobbleRetryIfUnchanged(
                                profileId = profileId,
                                contentKey = entry.contentKey,
                                expectedUpdatedAtMs = entry.updatedAtMs,
                                expectedAction = entry.action,
                                newAttemptCount = entry.attemptCount + 1,
                                nextRetryAtMs = clockMs() + 300_000L
                            )
                        }
                    }
                }

                if (!newerArrivalDetected || !sentOrRetriedAnyInPass) {
                    break
                }
            }
            allSucceeded
        }
    }

    private fun endpointLabelForAction(action: String): String = when (action) {
        "start" -> "POST /scrobble/start"
        "pause" -> "POST /scrobble/pause"
        "stop" -> "POST /scrobble/stop"
        "delete_playback" -> "DELETE /sync/playback/{id}"
        "history_add" -> "POST /sync/history"
        "history_remove" -> "POST /sync/history/remove"
        "list_add" -> "POST /sync/add-to-list"
        "list_remove_ptw" -> "POST /sync/history/remove"
        else -> "POST /scrobble/$action"
    }

    private sealed interface WriteOutcome {
        data class SUCCESS(val simklId: Long? = null) : WriteOutcome
        data class RETRY(val delayMs: Long) : WriteOutcome
        data object AUTH_FAILED : WriteOutcome
        data object PERMISSION_DENIED : WriteOutcome
    }

    private suspend fun sendSinglePendingEntry(
        profileId: String,
        initialToken: String,
        entry: SimklPendingScrobbleEntity
    ): WriteOutcome {
        val triggerLabel = entry.triggerEvent.ifBlank { entry.action }
        if (entry.action == "delete_playback") {
            val pid = entry.simklPlaybackId ?: return WriteOutcome.SUCCESS()
            val resp = executeWithAuthRetry(profileId, initialToken) { tok ->
                apiService.deletePlaybackItem("Bearer $tok", pid)
            } ?: return WriteOutcome.AUTH_FAILED
            val code = resp.code()
            val outcomeLabel = when {
                resp.isSuccessful -> "OK"
                code == 404 -> "ALREADY_DELETED"
                code == 403 -> "FORBIDDEN_403"
                else -> "RETRY"
            }
            recordDiagnostic(
                endpoint = "DELETE /sync/playback/$pid",
                contentType = entry.mediaType,
                profileId = profileId,
                triggerEvent = triggerLabel,
                outcome = outcomeLabel,
                simklId = entry.simklId,
                tmdbId = entry.tmdbId,
                httpCode = code
            )
            return when {
                resp.isSuccessful || code == 404 -> WriteOutcome.SUCCESS()
                code == 403 -> WriteOutcome.PERMISSION_DENIED
                code == 429 || code == 412 || code >= 500 ->
                    WriteOutcome.RETRY(parseRetryAfterMs(resp.headers()["Retry-After"], entry.attemptCount))
                else -> WriteOutcome.RETRY(computeBackoffMs(entry.attemptCount))
            }
        }

        // Arricchisce SEMPRE con tutti gli ID noti dalla libreria Simkl senza mai confondere TMDB ID e Simkl ID
        val libItem = dao.getSimklLibraryByTmdb(profileId, entry.tmdbId, entry.mediaType).firstOrNull()
        val resolvedSimklId = entry.simklId?.takeIf { it > 0L } ?: libItem?.simklId?.takeIf { it > 0L }
        val resolvedImdbId = entry.imdbId?.takeIf { it.isNotBlank() } ?: libItem?.imdbId?.takeIf { it.isNotBlank() }
        val resolvedTvdbId = entry.tvdbId?.takeIf { it > 0 } ?: libItem?.tvdbId?.takeIf { it > 0 }
        val resolvedSlug = libItem?.slug?.takeIf { it.isNotBlank() }
        val resolvedTitle = entry.title.takeIf { it.isNotBlank() } ?: libItem?.title?.takeIf { it.isNotBlank() }
        val yearInt = entry.releaseYear.take(4).toIntOrNull() ?: libItem?.year
        val enrichedIds = SimklIdsDto(
            simkl = resolvedSimklId,
            tmdb = entry.tmdbId.takeIf { it > 0 },
            imdb = resolvedImdbId,
            tvdb = resolvedTvdbId,
            slug = resolvedSlug
        )

        if (entry.action == "list_add") {
            val targetList = entry.episodeTitle?.takeIf { it.isNotBlank() } ?: "plantowatch"
            val requestBody = if (entry.mediaType == "tv") {
                SimklAddToListRequest(
                    shows = listOf(
                        SimklAddToListShowDto(
                            to = targetList,
                            title = resolvedTitle,
                            year = yearInt,
                            ids = enrichedIds
                        )
                    )
                )
            } else {
                SimklAddToListRequest(
                    movies = listOf(
                        SimklAddToListMovieDto(
                            to = targetList,
                            title = resolvedTitle,
                            year = yearInt,
                            ids = enrichedIds
                        )
                    )
                )
            }
            val resp = executeWithAuthRetry(profileId, initialToken) { tok ->
                apiService.addToList("Bearer $tok", requestBody)
            } ?: run {
                recordDiagnostic(
                    endpoint = "POST /sync/add-to-list",
                    contentType = entry.mediaType,
                    profileId = profileId,
                    triggerEvent = triggerLabel,
                    outcome = "AUTH_FAILED",
                    simklId = resolvedSimklId,
                    tmdbId = entry.tmdbId,
                    httpCode = 401
                )
                return WriteOutcome.AUTH_FAILED
            }
            val code = resp.code()
            val outcome = when {
                resp.isSuccessful -> "OK"
                code == 409 -> "CONFLICT_ALREADY_IN_LIST"
                code == 403 -> "FORBIDDEN_403"
                code == 429 || code == 412 || code >= 500 -> "RETRY"
                code in 400..499 -> "CLIENT_ERROR_DROPPED"
                else -> "RETRY"
            }
            recordDiagnostic(
                endpoint = "POST /sync/add-to-list",
                contentType = entry.mediaType,
                profileId = profileId,
                triggerEvent = triggerLabel,
                outcome = outcome,
                simklId = resolvedSimklId,
                tmdbId = entry.tmdbId,
                httpCode = code,
                rawResponse = if (resp.isSuccessful) {
                    val b = resp.body()
                    "added=(m=${b?.added?.movies},s=${b?.added?.shows})"
                } else {
                    runCatching { resp.errorBody()?.string() }.getOrNull()
                },
                errorDetail = if (!resp.isSuccessful && code != 409) "HTTP $code" else null
            )
            return when {
                resp.isSuccessful || code == 409 -> WriteOutcome.SUCCESS(null)
                code == 403 -> WriteOutcome.PERMISSION_DENIED
                code == 429 || code == 412 || code >= 500 ->
                    WriteOutcome.RETRY(parseRetryAfterMs(resp.headers()["Retry-After"], entry.attemptCount))
                code in 400..499 && code != 401 -> WriteOutcome.SUCCESS(null)
                else -> WriteOutcome.RETRY(computeBackoffMs(entry.attemptCount))
            }
        }

        if (entry.action == "list_remove_ptw") {
            val requestBody = if (entry.mediaType == "tv") {
                SimklHistorySyncRequest(
                    shows = listOf(
                        SimklHistoryShowDto(
                            title = resolvedTitle,
                            year = yearInt,
                            ids = enrichedIds,
                            seasons = null
                        )
                    )
                )
            } else {
                SimklHistorySyncRequest(
                    movies = listOf(
                        SimklHistoryMovieDto(
                            title = resolvedTitle,
                            year = yearInt,
                            ids = enrichedIds
                        )
                    )
                )
            }
            val resp = executeWithAuthRetry(profileId, initialToken) { tok ->
                apiService.removeFromHistory("Bearer $tok", requestBody)
            } ?: run {
                recordDiagnostic(
                    endpoint = "POST /sync/history/remove",
                    contentType = entry.mediaType,
                    profileId = profileId,
                    triggerEvent = triggerLabel,
                    outcome = "AUTH_FAILED",
                    simklId = resolvedSimklId,
                    tmdbId = entry.tmdbId,
                    httpCode = 401
                )
                return WriteOutcome.AUTH_FAILED
            }
            val code = resp.code()
            val outcome = when {
                resp.isSuccessful -> "OK"
                code == 404 || code == 409 -> "ALREADY_REMOVED"
                code == 403 -> "FORBIDDEN_403"
                code == 429 || code == 412 || code >= 500 -> "RETRY"
                code in 400..499 -> "CLIENT_ERROR_DROPPED"
                else -> "RETRY"
            }
            recordDiagnostic(
                endpoint = "POST /sync/history/remove",
                contentType = entry.mediaType,
                profileId = profileId,
                triggerEvent = triggerLabel,
                outcome = outcome,
                simklId = resolvedSimklId,
                tmdbId = entry.tmdbId,
                httpCode = code,
                rawResponse = if (resp.isSuccessful) {
                    val b = resp.body()
                    "deleted=(m=${b?.deleted?.movies},s=${b?.deleted?.shows})"
                } else {
                    runCatching { resp.errorBody()?.string() }.getOrNull()
                },
                errorDetail = if (!resp.isSuccessful && code != 404 && code != 409) "HTTP $code" else null
            )
            return when {
                resp.isSuccessful || code == 404 || code == 409 -> WriteOutcome.SUCCESS(null)
                code == 403 -> WriteOutcome.PERMISSION_DENIED
                code == 429 || code == 412 || code >= 500 ->
                    WriteOutcome.RETRY(parseRetryAfterMs(resp.headers()["Retry-After"], entry.attemptCount))
                code in 400..499 && code != 401 -> WriteOutcome.SUCCESS(null)
                else -> WriteOutcome.RETRY(computeBackoffMs(entry.attemptCount))
            }
        }

        if (entry.action == "history_add" || entry.action == "history_remove") {
            val isRemove = entry.action == "history_remove"
            val endpoint = if (isRemove) "POST /sync/history/remove" else "POST /sync/history"
            val requestBody = if (entry.mediaType == "tv") {
                val pairs = SimklLibraryJsonParser.deserializeBatchEpisodes(entry.batchEpisodesJson)
                    .ifEmpty {
                        val s = entry.seasonNumber
                        val e = entry.episodeNumber
                        if (s != null && e != null) listOf(s to e) else emptyList()
                    }
                if (pairs.isEmpty()) return WriteOutcome.SUCCESS()
                val groupedSeasons = pairs.groupBy({ it.first }, { it.second })
                    .toSortedMap()
                    .map { (seasonNum, eps) ->
                        SimklHistorySeasonDto(
                            number = seasonNum,
                            episodes = eps.distinct().sorted().map { epNum ->
                                SimklHistoryEpisodeItemDto(number = epNum)
                            }
                        )
                    }
                SimklHistorySyncRequest(
                    shows = listOf(
                        SimklHistoryShowDto(
                            title = resolvedTitle,
                            year = yearInt,
                            ids = enrichedIds,
                            seasons = groupedSeasons
                        )
                    )
                )
            } else {
                SimklHistorySyncRequest(
                    movies = listOf(
                        SimklHistoryMovieDto(
                            title = resolvedTitle,
                            year = yearInt,
                            ids = enrichedIds
                        )
                    )
                )
            }

            val resp = executeWithAuthRetry(profileId, initialToken) { tok ->
                if (isRemove) {
                    apiService.removeFromHistory("Bearer $tok", requestBody)
                } else {
                    apiService.addToHistory("Bearer $tok", requestBody)
                }
            } ?: run {
                recordDiagnostic(
                    endpoint = endpoint,
                    contentType = entry.mediaType,
                    profileId = profileId,
                    triggerEvent = triggerLabel,
                    outcome = "AUTH_FAILED",
                    simklId = resolvedSimklId,
                    tmdbId = entry.tmdbId,
                    httpCode = 401
                )
                return WriteOutcome.AUTH_FAILED
            }

            val code = resp.code()
            val bodySummary = if (resp.isSuccessful) {
                val b = resp.body()
                "added=(m=${b?.added?.movies},s=${b?.added?.shows},e=${b?.added?.episodes}),deleted=(m=${b?.deleted?.movies},s=${b?.deleted?.shows},e=${b?.deleted?.episodes})"
            } else {
                runCatching { resp.errorBody()?.string() }.getOrNull()
            }
            val outcome = when {
                resp.isSuccessful -> "OK"
                code == 409 -> "CONFLICT_ALREADY_SYNCED"
                code == 403 -> "FORBIDDEN_403"
                code == 429 || code == 412 || code >= 500 -> "RETRY"
                code in 400..499 -> "CLIENT_ERROR_DROPPED"
                else -> "RETRY"
            }
            recordDiagnostic(
                endpoint = endpoint,
                contentType = entry.mediaType,
                profileId = profileId,
                triggerEvent = triggerLabel,
                outcome = outcome,
                simklId = resolvedSimklId,
                tmdbId = entry.tmdbId,
                httpCode = code,
                rawResponse = bodySummary,
                errorDetail = if (!resp.isSuccessful && code != 409) "HTTP $code" else null
            )
            return when {
                resp.isSuccessful || code == 409 -> WriteOutcome.SUCCESS(null)
                code == 403 -> WriteOutcome.PERMISSION_DENIED
                code == 429 || code == 412 || code >= 500 ->
                    WriteOutcome.RETRY(parseRetryAfterMs(resp.headers()["Retry-After"], entry.attemptCount))
                code in 400..499 && code != 401 -> WriteOutcome.SUCCESS(null)
                else -> WriteOutcome.RETRY(computeBackoffMs(entry.attemptCount))
            }
        }

        val epSimklId = if (entry.mediaType == "tv" && entry.seasonNumber != null && entry.episodeNumber != null) {
            entry.simklEpisodeId?.takeIf { it > 0L }
                ?: dao.getSimklEpisodeState(profileId, entry.tmdbId, entry.seasonNumber, entry.episodeNumber)
                    ?.simklEpisodeId
                    ?.takeIf { it > 0L }
        } else {
            null
        }

        val requestBody = if (entry.mediaType == "tv") {
            SimklScrobbleRequest(
                progress = roundProgress(entry.progressPercent),
                show = SimklShowDto(
                    title = resolvedTitle,
                    year = yearInt,
                    ids = enrichedIds
                ),
                episode = SimklEpisodeDto(
                    season = entry.seasonNumber ?: 1,
                    number = entry.episodeNumber ?: 1,
                    title = entry.episodeTitle?.takeIf { it.isNotBlank() },
                    ids = epSimklId?.let { SimklIdsDto(simkl = it) }
                )
            )
        } else {
            SimklScrobbleRequest(
                progress = roundProgress(entry.progressPercent),
                movie = SimklMovieDto(
                    title = resolvedTitle,
                    year = yearInt,
                    ids = enrichedIds
                )
            )
        }

        val endpoint = endpointLabelForAction(entry.action)
        val resp = executeWithAuthRetry(profileId, initialToken) { tok ->
            when (entry.action) {
                "start" -> apiService.scrobbleStart("Bearer $tok", requestBody)
                "pause" -> apiService.scrobblePause("Bearer $tok", requestBody)
                else -> apiService.scrobbleStop(
                    bearerToken = "Bearer $tok",
                    minProgress = entry.minProgress.coerceIn(50, 100),
                    body = requestBody
                )
            }
        } ?: run {
            recordDiagnostic(
                endpoint = endpoint,
                contentType = entry.mediaType,
                profileId = profileId,
                triggerEvent = triggerLabel,
                outcome = "AUTH_FAILED",
                simklId = resolvedSimklId,
                tmdbId = entry.tmdbId,
                httpCode = 401
            )
            return WriteOutcome.AUTH_FAILED
        }

        val code = resp.code()
        val body = resp.body()
        val rawSummary = if (resp.isSuccessful) {
            "id=${body?.id},action=${body?.action},progress=${body?.progress}"
        } else {
            runCatching { resp.errorBody()?.string() }.getOrNull()
        }
        val outcomeLabel = when {
            resp.isSuccessful -> "OK_${body?.action ?: entry.action}"
            code == 409 -> "CONFLICT_ALREADY_WATCHED"
            code == 403 -> "FORBIDDEN_403"
            code == 429 || code == 412 || code >= 500 -> "RETRY"
            code in 400..499 -> "CLIENT_ERROR_DROPPED"
            else -> "RETRY"
        }
        recordDiagnostic(
            endpoint = endpoint,
            contentType = entry.mediaType,
            profileId = profileId,
            triggerEvent = triggerLabel,
            outcome = outcomeLabel,
            simklId = resolvedSimklId ?: body?.id,
            tmdbId = entry.tmdbId,
            httpCode = code,
            rawResponse = rawSummary,
            errorDetail = if (!resp.isSuccessful && code != 409) "HTTP $code su $endpoint" else null
        )

        recordApiCallMetric(endpoint, httpCode = code)
        if (code == 429) {
            rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(resp.headers()["Retry-After"], entry.attemptCount)
        }
        return when {
            resp.isSuccessful -> {
                lastSentScrobbleByProfileKey["$profileId|${entry.contentKey}"] = Triple(entry.action, entry.progressPercent, clockMs())
                WriteOutcome.SUCCESS(body?.id)
            }
            // HTTP 409 su scrobble/stop (già visto su Simkl) gestito come successo morbido tracciato in diagnostica
            code == 409 -> WriteOutcome.SUCCESS(null)
            code == 403 -> WriteOutcome.PERMISSION_DENIED
            code == 429 || code == 412 || code >= 500 ->
                WriteOutcome.RETRY(parseRetryAfterMs(resp.headers()["Retry-After"], entry.attemptCount))
            code in 400..499 && code != 401 -> WriteOutcome.SUCCESS(null)
            else -> WriteOutcome.RETRY(computeBackoffMs(entry.attemptCount))
        }
    }

    private suspend fun reconcileRemotePlayback(
        profileId: String,
        remoteMovies: List<SimklPlaybackItemDto>,
        remoteEpisodes: List<SimklPlaybackItemDto>,
        reconcileMovies: Boolean = true,
        reconcileEpisodes: Boolean = true
    ) {
        val baselines = dao.getSimklBaselinesForProfile(profileId).associateBy { it.contentKey }
        val localWatchMap = dao.getWatchProgressListForProfile(profileId).associateBy { it.id }
        val libraryByMedia = dao.getSimklLibraryForProfile(profileId)
            .filter { it.tmdbId != null && it.tmdbId > 0 }
            .associateBy { it.tmdbMediaType to it.tmdbId!! }
        val seenRemoteKeys = HashSet<String>()
        var deferredMovieUpdate = false
        var deferredEpisodeUpdate = false

        // Film da Simkl
        if (reconcileMovies) {
            for (dto in remoteMovies) {
                val movie = dto.movie ?: continue
                val mediaItem = resolveMediaItemForSimkl(
                    expectedType = "movie",
                    ids = movie.ids,
                    fallbackTitle = movie.title.orEmpty(),
                    fallbackYear = movie.year?.toString().orEmpty()
                ) ?: continue

                val contentKey = "movie_${mediaItem.id}"
                val libItem = libraryByMedia["movie" to mediaItem.id]
                if (libItem?.status in excludedContinueWatchingStatuses) {
                    if (!isActivePlaybackTarget(profileId, "movie", mediaItem.id)) {
                        dao.deleteWatchProgress(contentKey, profileId)
                        dao.deleteSimklBaseline(profileId, contentKey)
                    } else {
                        deferredMovieUpdate = true
                    }
                    seenRemoteKeys.add(contentKey)
                    continue
                }

                seenRemoteKeys.add(contentKey)
                if (isActivePlaybackTarget(profileId, "movie", mediaItem.id)) {
                    deferredMovieUpdate = true
                }
                reconcileSingleItem(
                    profileId = profileId,
                    contentKey = contentKey,
                    mediaItem = mediaItem,
                    seasonNumber = null,
                    episodeNumber = null,
                    remoteDto = dto,
                    baseline = baselines[contentKey],
                    localMain = localWatchMap[contentKey]
                )
            }
            if (deferredMovieUpdate) {
                deferredPlaybackMoviesProfiles.add(profileId)
            } else {
                deferredPlaybackMoviesProfiles.remove(profileId)
            }
        }

        // Episodi da Simkl: esclude serie in Hold/Dropped/Completed/PlanToWatch ed episodi già visti prima di scegliere il più recente
        val seriesNeedingContinueWatchingCheck = LinkedHashSet<Int>()
        if (reconcileEpisodes) {
            val episodeStatesBySeries = HashMap<Int, List<SimklEpisodeStateEntity>>()
            val completedPairsBySeries = HashMap<Int, Set<Pair<Int, Int>>>()
            val resolvedEpisodes = ArrayList<Triple<MediaItem, SimklPlaybackItemDto, Pair<Int, Int>>>()
            for (dto in remoteEpisodes) {
                val show = dto.show ?: continue
                val ep = dto.episode ?: continue
                val epNum = ep.resolvedEpisodeNumber ?: continue
                val seasonNum = ep.season ?: 1
                val mediaItem = resolveMediaItemForSimkl(
                    expectedType = "tv",
                    ids = show.ids,
                    fallbackTitle = show.title.orEmpty(),
                    fallbackYear = show.year?.toString().orEmpty()
                ) ?: continue

                val epKey = "tv_${mediaItem.id}_s${seasonNum}_e${epNum}"
                val libItem = libraryByMedia["tv" to mediaItem.id]
                if (libItem?.status in excludedContinueWatchingStatuses) {
                    if (!isActivePlaybackSeries(profileId, mediaItem.id)) {
                        dao.deleteWatchProgress("tv_${mediaItem.id}", profileId)
                        dao.deleteEpisodeProgress(epKey, profileId)
                        dao.deleteSimklBaseline(profileId, epKey)
                    } else {
                        deferredEpisodeUpdate = true
                        markDeferredLibrarySeries(profileId, mediaItem.id)
                    }
                    seenRemoteKeys.add(epKey)
                    continue
                }

                val remotePausedMs = parseIsoUtcMillis(dto.pausedAt)
                val seriesStates = episodeStatesBySeries.getOrPut(mediaItem.id) {
                    dao.getSimklEpisodeStatesByTmdb(profileId, mediaItem.id)
                }
                val seriesCompletedPairs = completedPairsBySeries.getOrPut(mediaItem.id) {
                    dao.getCompletedEpisodesForSeries(mediaItem.id, profileId)
                        .map { it.seasonNumber to it.episodeNumber }
                        .toSet()
                }
                val epState = seriesStates.firstOrNull { it.seasonNumber == seasonNum && it.episodeNumber == epNum }
                val completedEp = if ((seasonNum to epNum) in seriesCompletedPairs) {
                    dao.getCompletedMedia(
                        contentKey = PlayerSegmentKeys.completedMediaKey(mediaItem.id, "tv", seasonNum, epNum),
                        profileId = profileId
                    )
                } else {
                    null
                }
                val isEpWatched = epState?.watched == true || completedEp != null
                val hasNewerWatchedEpisode = seasonNum > 0 && (
                    seriesStates.any { it.watched && it.seasonNumber > 0 && compareEpisodePairs(seasonNum to epNum, it.seasonNumber to it.episodeNumber) < 0 } ||
                        seriesCompletedPairs.any { it.first > 0 && compareEpisodePairs(seasonNum to epNum, it) < 0 }
                    )
                val libNextPair = parseEpisodeCode(libItem?.nextToWatch)
                val isBehindLibNext = seasonNum > 0 && libNextPair != null && libNextPair.first > 0 &&
                    compareEpisodePairs(seasonNum to epNum, libNextPair) < 0
                val maxSeriesWatchedMs = maxOf(
                    epState?.watchedAtMs ?: 0L,
                    completedEp?.completedAtMs ?: 0L,
                    libItem?.lastWatchedAtMs ?: 0L,
                    seriesStates.filter { it.watched }.maxOfOrNull { it.watchedAtMs } ?: 0L
                )
                val isGenuineRewatch = isEpWatched &&
                    !hasNewerWatchedEpisode &&
                    !isBehindLibNext &&
                    remotePausedMs > 0L &&
                    maxSeriesWatchedMs > 0L &&
                    remotePausedMs > maxSeriesWatchedMs + 60_000L
                val alreadyWatchedOnSimkl = isEpWatched && !isGenuineRewatch
                if (alreadyWatchedOnSimkl) {
                    if (!isActivePlaybackTarget(profileId, "tv", mediaItem.id, seasonNum, epNum)) {
                        dao.deleteEpisodeProgress(epKey, profileId)
                        dao.deleteSimklBaseline(profileId, epKey)
                        seriesNeedingContinueWatchingCheck.add(mediaItem.id)
                    } else {
                        deferredEpisodeUpdate = true
                        markDeferredLibrarySeries(profileId, mediaItem.id)
                    }
                    seenRemoteKeys.add(epKey)
                    continue
                }

                resolvedEpisodes.add(Triple(mediaItem, dto, seasonNum to epNum))
            }

            val latestPerSeries = HashMap<Int, Triple<MediaItem, SimklPlaybackItemDto, Pair<Int, Int>>>()
            for (triple in resolvedEpisodes) {
                val (mediaItem, dto, se) = triple
                val (seasonNum, epNum) = se
                val contentKey = "tv_${mediaItem.id}_s${seasonNum}_e${epNum}"
                seenRemoteKeys.add(contentKey)
                if (isActivePlaybackTarget(profileId, "tv", mediaItem.id, seasonNum, epNum) ||
                    isActivePlaybackSeries(profileId, mediaItem.id)
                ) {
                    deferredEpisodeUpdate = true
                    markDeferredLibrarySeries(profileId, mediaItem.id)
                }
                val epCompletedViaRemote = reconcileSingleEpisodeProgress(
                    profileId = profileId,
                    contentKey = contentKey,
                    mediaItem = mediaItem,
                    seasonNumber = seasonNum,
                    episodeNumber = epNum,
                    remoteDto = dto,
                    baseline = baselines[contentKey]
                )
                if (epCompletedViaRemote) {
                    seriesNeedingContinueWatchingCheck.add(mediaItem.id)
                }
                val pausedMs = parseIsoUtcMillis(dto.pausedAt)
                val prev = latestPerSeries[mediaItem.id]
                if (prev == null || pausedMs >= parseIsoUtcMillis(prev.second.pausedAt)) {
                    latestPerSeries[mediaItem.id] = triple
                }
            }

            // Aggiorna la scheda serie principale "Continua a guardare" sull'episodio più recente non completato
            for ((seriesId, triple) in latestPerSeries) {
                val (mediaItem, dto, se) = triple
                val (seasonNum, epNum) = se
                val contentKey = "tv_${seriesId}_s${seasonNum}_e${epNum}"
                val mainKey = "tv_${seriesId}"
                val localMain = dao.getWatchProgress(mainKey, profileId) ?: localWatchMap[mainKey]
                val libItem = libraryByMedia["tv" to seriesId]
                val remotePausedMs = parseIsoUtcMillis(dto.pausedAt)
                val seriesStates = episodeStatesBySeries[seriesId].orEmpty()
                val seriesCompletedPairs = completedPairsBySeries[seriesId].orEmpty()
                val maxSeriesWatchedMs = maxOf(
                    libItem?.lastWatchedAtMs ?: 0L,
                    seriesStates.filter { it.watched }.maxOfOrNull { it.watchedAtMs } ?: 0L
                )
                val hasNewerWatchedEp = seasonNum > 0 && (
                    seriesStates.any { it.watched && it.seasonNumber > 0 && compareEpisodePairs(se, it.seasonNumber to it.episodeNumber) < 0 } ||
                        seriesCompletedPairs.any { it.first > 0 && compareEpisodePairs(se, it) < 0 }
                    )
                val isStalePausedBehindNewerWatched = (libItem != null &&
                    libItem.lastWatchedAtMs > 0L &&
                    remotePausedMs > 0L &&
                    libItem.lastWatchedAtMs > remotePausedMs + 2_000L &&
                    (localMain == null || localMain.seasonNumber != seasonNum || localMain.episodeNumber != epNum)) ||
                    (hasNewerWatchedEp && (remotePausedMs <= 0L || maxSeriesWatchedMs <= 0L || remotePausedMs <= maxSeriesWatchedMs + 60_000L))
                if (isStalePausedBehindNewerWatched) {
                    if (!isActivePlaybackTarget(profileId, "tv", seriesId, seasonNum, epNum)) {
                        dao.deleteSimklBaseline(profileId, contentKey)
                    }
                    seriesNeedingContinueWatchingCheck.add(seriesId)
                    continue
                }
                val itemCompletedViaRemote = reconcileSingleItem(
                    profileId = profileId,
                    contentKey = contentKey,
                    mediaItem = mediaItem,
                    seasonNumber = seasonNum,
                    episodeNumber = epNum,
                    remoteDto = dto,
                    baseline = baselines[contentKey],
                    localMain = localMain
                )
                if (itemCompletedViaRemote) {
                    seriesNeedingContinueWatchingCheck.add(seriesId)
                }
            }
            if (deferredEpisodeUpdate) {
                deferredPlaybackEpisodesProfiles.add(profileId)
            } else {
                deferredPlaybackEpisodesProfiles.remove(profileId)
            }
        }

        // Rimuove schede SOLO per i tipi riconciliati se il contenuto risulta completato o terminato in libreria Simkl.
        // Non elimina MAI un progresso locale valido soltanto perché un playback temporaneo Simkl è scaduto o assente.
        for ((contentKey, baseline) in baselines) {
            if (baseline.mediaType == "movie" && !reconcileMovies) continue
            if (baseline.mediaType == "tv" && !reconcileEpisodes) continue
            if (contentKey in seenRemoteKeys) continue
            if (baseline.simklPlaybackId == null && baseline.lastSyncedProgressPercent <= 0.0) continue
            if (isActivePlaybackTarget(profileId, baseline.mediaType, baseline.tmdbId, baseline.seasonNumber, baseline.episodeNumber)) {
                if (baseline.mediaType == "movie") deferredPlaybackMoviesProfiles.add(profileId)
                else {
                    deferredPlaybackEpisodesProfiles.add(profileId)
                    markDeferredLibrarySeries(profileId, baseline.tmdbId)
                }
                continue
            }

            val completedKey = PlayerSegmentKeys.completedMediaKey(
                tmdbId = baseline.tmdbId,
                mediaType = baseline.mediaType,
                seasonNumber = baseline.seasonNumber,
                episodeNumber = baseline.episodeNumber
            )
            val isNowCompleted = dao.getCompletedMedia(completedKey, profileId) != null
            val libItem = libraryByMedia[baseline.mediaType to baseline.tmdbId]
                ?: dao.getSimklLibraryByTmdb(profileId, baseline.tmdbId, baseline.mediaType).firstOrNull()
            val isStatusTerminal = libItem != null && libItem.status in excludedContinueWatchingStatuses
            if (!isNowCompleted && !isStatusTerminal) {
                recordDiagnostic(
                    endpoint = "GET /sync/playback",
                    contentType = baseline.mediaType,
                    profileId = profileId,
                    triggerEvent = "reconcile_playback",
                    outcome = "PRESERVED_LOCAL_ON_TEMP_PLAYBACK_EXPIRED",
                    tmdbId = baseline.tmdbId
                )
                continue
            }

            val mainId = "${baseline.mediaType}_${baseline.tmdbId}"
            val localMain = dao.getWatchProgress(mainId, profileId) ?: localWatchMap[mainId]
            val matchesLocalMain = localMain != null &&
                (baseline.mediaType == "movie" ||
                    isStatusTerminal ||
                    (localMain.seasonNumber == baseline.seasonNumber && localMain.episodeNumber == baseline.episodeNumber))
            if (baseline.mediaType == "tv") {
                if (isStatusTerminal && matchesLocalMain) {
                    dao.deleteWatchProgress(mainId, profileId)
                } else if (isNowCompleted) {
                    seriesNeedingContinueWatchingCheck.add(baseline.tmdbId)
                }
                dao.deleteEpisodeProgress(contentKey, profileId)
            } else if (matchesLocalMain) {
                dao.deleteWatchProgress(mainId, profileId)
            }
            dao.deleteSimklBaseline(profileId, contentKey)
        }

        if (seriesNeedingContinueWatchingCheck.isNotEmpty()) {
            reconcileTvContinueWatchingInvariant(profileId, seriesNeedingContinueWatchingCheck)
        }
    }

    private suspend fun reconcileSingleEpisodeProgress(
        profileId: String,
        contentKey: String,
        mediaItem: MediaItem,
        seasonNumber: Int,
        episodeNumber: Int,
        remoteDto: SimklPlaybackItemDto,
        baseline: SimklPlaybackBaselineEntity?
    ): Boolean {
        if (isActivePlaybackTarget(profileId, "tv", mediaItem.id, seasonNumber, episodeNumber)) {
            return false
        }
        val remotePct = remoteDto.progress.coerceIn(0.0, 100.0)
        val remotePausedMs = parseIsoUtcMillis(remoteDto.pausedAt)
        val existingCompleted = dao.getCompletedMedia(
            contentKey = PlayerSegmentKeys.completedMediaKey(mediaItem.id, "tv", seasonNumber, episodeNumber),
            profileId = profileId
        )
        if (existingCompleted != null && (remotePausedMs <= 0L || existingCompleted.completedAtMs >= remotePausedMs)) {
            return false
        }
        val localEp = dao.getEpisodeProgress(contentKey, profileId)
        val epDurationMs = localEp?.durationMs?.takeIf { it > 0L }
            ?: resolveEpisodeDurationMs(mediaItem.id, seasonNumber, episodeNumber)
        val localPct = if (localEp != null && epDurationMs > 0L) {
            ((localEp.positionMs.toDouble() / epDurationMs.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        val remoteWins = shouldRemoteWin(
            remotePct = remotePct,
            remotePausedAtMs = remotePausedMs,
            localExists = localEp != null,
            localPct = localPct,
            localUpdatedAtMs = localEp?.updatedAt ?: 0L,
            baseline = baseline
        )

        if (localEp != null && abs(remotePct - localPct) >= 2.0) {
            recordDiagnostic(
                endpoint = "GET /sync/playback/episodes",
                contentType = "tv",
                profileId = profileId,
                triggerEvent = "reconcile_episode_conflict",
                outcome = if (remoteWins) "CONFLICT_SIMKL_AUTHORITATIVE" else "CONFLICT_LOCAL_KEPT",
                tmdbId = mediaItem.id,
                rawResponse = "S${seasonNumber}E${episodeNumber} remote=${roundProgress(remotePct)}% local=${roundProgress(localPct)}%"
            )
        }

        if (remotePct >= 96.0 && remoteWins) {
            dao.upsertCompletedMedia(
                CompletedMediaEntity(
                    profileId = profileId,
                    contentKey = PlayerSegmentKeys.completedMediaKey(mediaItem.id, "tv", seasonNumber, episodeNumber),
                    tmdbId = mediaItem.id,
                    mediaType = "tv",
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    completedAtMs = if (remotePausedMs > 0L) remotePausedMs else clockMs()
                )
            )
            dao.deleteEpisodeProgress(contentKey, profileId)
            dao.deleteSimklBaseline(profileId, contentKey)
            return true
        }

        if (remoteWins && remotePct > 0.0) {
            val epDtoForAirDate = runCatching {
                val eps = repository.getCachedSeasonEpisodes(mediaItem.id, seasonNumber)?.value
                    ?: repository.fetchSeasonEpisodes(mediaItem.id, seasonNumber).getOrNull()
                eps?.firstOrNull { it.episodeNumber == episodeNumber }
            }.getOrNull()
            if (com.example.ui.UpcomingEpisodeBadgeFormatter.isUpcomingAirDate(epDtoForAirDate?.airDate)) {
                return false
            }
            val posMs = if (epDurationMs > 0L) {
                ((epDurationMs * remotePct) / 100.0).roundToLong().coerceAtLeast(3_000L)
            } else {
                // Posizione minima non-zero finché l'arricchimento background TMDB non risolve la durata
                localEp?.positionMs?.takeIf { it >= 3_000L } ?: 30_000L
            }
            val updatedAt = maxOf(remotePausedMs, 1L)
            dao.upsertEpisodeProgress(
                EpisodeProgressEntity(
                    episodeKey = contentKey,
                    tmdbId = mediaItem.id,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    positionMs = posMs,
                    durationMs = epDurationMs,
                    updatedAt = updatedAt,
                    profileId = profileId
                )
            )
            dao.upsertSimklBaseline(
                SimklPlaybackBaselineEntity(
                    profileId = profileId,
                    contentKey = contentKey,
                    tmdbId = mediaItem.id,
                    mediaType = "tv",
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    simklPlaybackId = remoteDto.id ?: baseline?.simklPlaybackId,
                    lastSyncedProgressPercent = remotePct,
                    lastRemotePausedAtMs = remotePausedMs,
                    lastLocalUpdatedAtMs = updatedAt,
                    localDirty = false,
                    updatedAtMs = maxOf(clockMs(), updatedAt)
                )
            )
        }
        return false
    }

    private suspend fun reconcileSingleItem(
        profileId: String,
        contentKey: String,
        mediaItem: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        remoteDto: SimklPlaybackItemDto,
        baseline: SimklPlaybackBaselineEntity?,
        localMain: WatchProgressEntity?
    ): Boolean {
        if (isActivePlaybackTarget(profileId, mediaItem.mediaType, mediaItem.id, seasonNumber, episodeNumber) ||
            (mediaItem.isSeries && isActivePlaybackSeries(profileId, mediaItem.id))
        ) {
            return false
        }
        val remotePct = remoteDto.progress.coerceIn(0.0, 100.0)
        val remotePausedMs = parseIsoUtcMillis(remoteDto.pausedAt)
        val sameMainTarget = localMain != null &&
            (mediaItem.mediaType == "movie" ||
                (localMain.seasonNumber == seasonNumber && localMain.episodeNumber == episodeNumber))
        val existingCompleted = dao.getCompletedMedia(
            contentKey = PlayerSegmentKeys.completedMediaKey(mediaItem.id, mediaItem.mediaType, seasonNumber, episodeNumber),
            profileId = profileId
        )
        if (existingCompleted != null && (remotePausedMs <= 0L || existingCompleted.completedAtMs >= remotePausedMs)) {
            return false
        }

        val resolvedDetails = runCatching {
            repository.getCachedMediaDetails(mediaItem)?.value
                ?: repository.fetchMediaDetails(mediaItem).getOrNull()
        }.getOrNull()

        val enrichedItem = resolvedDetails?.item ?: mediaItem
        val resolvedEpDto = if (mediaItem.isSeries && seasonNumber != null && episodeNumber != null) {
            val eps = runCatching {
                repository.getCachedSeasonEpisodes(mediaItem.id, seasonNumber)?.value
                    ?: repository.fetchSeasonEpisodes(mediaItem.id, seasonNumber).getOrNull()
            }.getOrNull().orEmpty()
            eps.firstOrNull { it.episodeNumber == episodeNumber }
        } else {
            null
        }

        val resolvedDurationMs = when {
            sameMainTarget && (localMain?.durationMs ?: 0L) > 0L -> localMain!!.durationMs
            mediaItem.isSeries -> ((resolvedEpDto?.runtime?.takeIf { it in 1..600 }?.toLong() ?: 0L) * 60_000L)
            else -> ((resolvedDetails?.runtimeMinutes?.takeIf { it in 1..600 }?.toLong() ?: 0L) * 60_000L)
        }

        val localPct = if (sameMainTarget && localMain != null && localMain.durationMs > 0L) {
            ((localMain.positionMs.toDouble() / localMain.durationMs.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        val effectiveLocalUpdatedAt = localMain?.updatedAt ?: 0L
        val effectiveLocalExists = localMain != null
        val remoteWins = shouldRemoteWin(
            remotePct = remotePct,
            remotePausedAtMs = remotePausedMs,
            localExists = effectiveLocalExists,
            localPct = localPct,
            localUpdatedAtMs = effectiveLocalUpdatedAt,
            baseline = baseline
        )

        if (localMain != null && abs(remotePct - localPct) >= 2.0) {
            recordDiagnostic(
                endpoint = if (mediaItem.isSeries) "GET /sync/playback/episodes" else "GET /sync/playback/movies",
                contentType = mediaItem.mediaType,
                profileId = profileId,
                triggerEvent = "reconcile_item_conflict",
                outcome = if (remoteWins) "CONFLICT_SIMKL_AUTHORITATIVE" else "CONFLICT_LOCAL_KEPT",
                tmdbId = mediaItem.id,
                rawResponse = "remote=${roundProgress(remotePct)}% local=${roundProgress(localPct)}%"
            )
        }

        if (remotePct >= 96.0 && remoteWins) {
            val mainId = "${mediaItem.mediaType}_${mediaItem.id}"
            if (sameMainTarget || localMain == null) {
                dao.deleteWatchProgress(mainId, profileId)
            }
            if (!mediaItem.isSeries) {
                dao.upsertCompletedMedia(
                    CompletedMediaEntity(
                        profileId = profileId,
                        contentKey = PlayerSegmentKeys.completedMediaKey(mediaItem.id, "movie"),
                        tmdbId = mediaItem.id,
                        mediaType = "movie",
                        completedAtMs = if (remotePausedMs > 0L) remotePausedMs else clockMs()
                    )
                )
            }
            dao.deleteSimklBaseline(profileId, contentKey)
            return mediaItem.isSeries
        }

        val isUpcomingEpisode = mediaItem.isSeries &&
            com.example.ui.UpcomingEpisodeBadgeFormatter.isUpcomingAirDate(resolvedEpDto?.airDate)
        if (remoteWins && remotePct > 0.0 && !isUpcomingEpisode) {
            val posMs = if (resolvedDurationMs > 0L) {
                ((resolvedDurationMs * remotePct) / 100.0).roundToLong().coerceAtLeast(3_000L)
            } else {
                localMain?.takeIf { sameMainTarget && it.positionMs >= 3_000L }?.positionMs ?: 30_000L
            }
            val updatedAt = maxOf(remotePausedMs, 1L)
            dao.upsertWatchProgress(
                WatchProgressEntity(
                    id = "${enrichedItem.mediaType}_${enrichedItem.id}",
                    tmdbId = enrichedItem.id,
                    mediaType = enrichedItem.mediaType,
                    title = enrichedItem.title.ifBlank { mediaItem.title },
                    overview = enrichedItem.overview,
                    posterPath = enrichedItem.posterPath,
                    backdropPath = enrichedItem.backdropPath,
                    voteAverage = enrichedItem.voteAverage,
                    releaseYear = enrichedItem.releaseYear,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeTitle = resolvedEpDto?.name?.takeIf { it.isNotBlank() } ?: remoteDto.episode?.title,
                    positionMs = posMs,
                    durationMs = resolvedDurationMs,
                    updatedAt = updatedAt,
                    profileId = profileId
                )
            )
            dao.upsertSimklBaseline(
                SimklPlaybackBaselineEntity(
                    profileId = profileId,
                    contentKey = contentKey,
                    tmdbId = enrichedItem.id,
                    mediaType = enrichedItem.mediaType,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    simklPlaybackId = remoteDto.id,
                    lastSyncedProgressPercent = remotePct,
                    lastRemotePausedAtMs = remotePausedMs,
                    lastLocalUpdatedAtMs = updatedAt,
                    localDirty = false,
                    updatedAtMs = maxOf(clockMs(), updatedAt)
                )
            )
        }
        return false
    }

    /**
     * Politica di riconciliazione: Simkl prevale sempre come fonte di verità autorevole
     * per tutti i dispositivi collegati allo stesso account.
     */
    internal fun shouldRemoteWin(
        remotePct: Double,
        remotePausedAtMs: Long,
        localExists: Boolean,
        localPct: Double,
        localUpdatedAtMs: Long,
        baseline: SimklPlaybackBaselineEntity?
    ): Boolean {
        if (!localExists) return true
        return remotePct > 0.0 || remotePausedAtMs > 0L
    }

    private suspend fun resolveMediaItemForSimkl(
        expectedType: String,
        ids: SimklIdsDto?,
        fallbackTitle: String,
        fallbackYear: String
    ): MediaItem? {
        val directTmdb = ids?.tmdb?.takeIf { it > 0 }
        if (directTmdb != null) {
            return MediaItem(
                id = directTmdb,
                mediaType = expectedType,
                title = fallbackTitle.ifBlank { "Titolo #$directTmdb" },
                overview = "",
                posterPath = null,
                backdropPath = null,
                voteAverage = 0.0,
                releaseYear = fallbackYear
            )
        }
        return repository.findTmdbMediaByExternalId(
            imdbId = ids?.imdb,
            tvdbId = ids?.tvdb,
            expectedType = expectedType
        )
    }

    private suspend fun resolveEpisodeDurationMs(
        tmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ): Long {
        val eps = runCatching {
            repository.getCachedSeasonEpisodes(tmdbId, seasonNumber)?.value
                ?: repository.fetchSeasonEpisodes(tmdbId, seasonNumber).getOrNull()
        }.getOrNull().orEmpty()
        val runtime = eps.firstOrNull { it.episodeNumber == episodeNumber }?.runtime?.takeIf { it in 1..600 }
            ?: return 0L
        return runtime * 60_000L
    }

    private suspend fun computeProgressPercent(
        positionMs: Long,
        durationMs: Long,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Double {
        if (positionMs <= 0L) return 0.0
        val resolvedDur = if (durationMs > 0L) {
            durationMs
        } else if (item.isSeries && seasonNumber != null && episodeNumber != null) {
            resolveEpisodeDurationMs(item.id, seasonNumber, episodeNumber)
        } else {
            val det = runCatching {
                repository.getCachedMediaDetails(item)?.value
                    ?: repository.fetchMediaDetails(item).getOrNull()
            }.getOrNull()
            ((det?.runtimeMinutes?.takeIf { it in 1..600 }?.toLong() ?: 0L) * 60_000L)
        }
        if (resolvedDur <= 0L) return 0.0
        return ((positionMs.toDouble() / resolvedDur.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    }

    private suspend fun <T> executeWithAuthRetry(
        profileId: String,
        initialToken: String,
        block: suspend (String) -> retrofit2.Response<T>
    ): retrofit2.Response<T>? {
        val firstResp = block(initialToken)
        if (firstResp.code() != 401) return firstResp

        val refreshedToken = getValidAccessToken(profileId, forceRefresh = true)
            ?: return null
        val secondResp = block(refreshedToken)
        if (secondResp.code() == 401) {
            markAuthExpired(profileId, "Sessione Simkl scaduta. Ricollega il tuo account Simkl.")
            return null
        }
        return secondResp
    }

    private suspend fun getValidAccessToken(
        profileId: String,
        forceRefresh: Boolean
    ): String? = tokenMutexFor(profileId).withLock {
        val bundle = tokenStore.readTokens(profileId) ?: run {
            markAuthExpired(profileId, "Credenziali Simkl mancanti. Ricollega l'account.")
            return@withLock null
        }
        val now = clockMs()
        val needsRefresh = forceRefresh || bundle.isExpired(now) ||
            bundle.isExpiringSoon(SimklConfig.TOKEN_REFRESH_LEAD_TIME_MS, now)

        if (!needsRefresh) {
            return@withLock bundle.accessToken
        }

        val refreshTok = bundle.refreshToken
        if (refreshTok.isNullOrBlank()) {
            if (forceRefresh || bundle.isExpired(now)) {
                markAuthExpired(profileId, "Token Simkl scaduto e privo di refresh token. Ricollega l'account.")
                return@withLock null
            }
            return@withLock bundle.accessToken
        }

        val cfg = configProvider()
        val resp = try {
            apiService.refreshToken(
                grantType = SimklConfig.GRANT_TYPE_REFRESH_TOKEN,
                clientId = cfg.clientId,
                refreshToken = refreshTok
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Se non è ancora scaduto ma la rete fallisce, prova a usare l'access token esistente
            return@withLock if (!forceRefresh && !bundle.isExpired(now)) bundle.accessToken else null
        }

        if (resp.isSuccessful && resp.body()?.accessToken?.isNotBlank() == true) {
            val fresh = resp.body()!!
            val updatedBundle = SimklTokenBundle(
                accessToken = fresh.accessToken.trim(),
                refreshToken = fresh.refreshToken?.trim()?.takeIf { it.isNotBlank() } ?: refreshTok,
                expiresAtMs = fresh.expiresIn?.takeIf { it > 0L }?.let { clockMs() + it * 1000L } ?: bundle.expiresAtMs,
                tokenType = fresh.tokenType?.ifBlank { "Bearer" } ?: bundle.tokenType,
                scope = fresh.scope ?: bundle.scope,
                savedAtMs = clockMs()
            )
            tokenStore.saveTokens(profileId, updatedBundle)
            return@withLock updatedBundle.accessToken
        }

        if (resp.code() in listOf(400, 401, 403)) {
            markAuthExpired(profileId, "Sessione Simkl revocata o scaduta. Ricollega il tuo account.")
            return@withLock null
        }
        return@withLock if (!forceRefresh && !bundle.isExpired(now)) bundle.accessToken else null
    }

    private suspend fun markAuthExpired(profileId: String, message: String) {
        tokenStore.clearTokens(profileId)
        val existing = dao.getSimklProfileMeta(profileId) ?: SimklProfileSyncMetaEntity(profileId = profileId)
        dao.upsertSimklProfileMeta(
            existing.copy(
                connectionState = SimklConnectionState.AUTH_EXPIRED.name,
                lastErrorMessage = message,
                updatedAtMs = clockMs()
            )
        )
    }

    private suspend fun revertToPreAuthMeta(profileId: String) {
        val hasTokens = tokenStore.hasTokens(profileId)
        val pendingCount = dao.getSimklPendingScrobbles(profileId).size
        val nextState = when {
            hasTokens && pendingCount > 0 -> SimklConnectionState.OFFLINE_QUEUE_PENDING
            hasTokens -> SimklConnectionState.CONNECTED_SYNCED
            else -> SimklConnectionState.NOT_CONNECTED
        }
        val existing = dao.getSimklProfileMeta(profileId)
        if (existing != null) {
            dao.upsertSimklProfileMeta(
                existing.copy(
                    connectionState = nextState.name,
                    updatedAtMs = clockMs()
                )
            )
        }
    }

    private suspend fun updateMetaState(
        profileId: String,
        state: SimklConnectionState,
        error: String? = null,
        clearError: Boolean = false
    ) {
        val existing = dao.getSimklProfileMeta(profileId) ?: SimklProfileSyncMetaEntity(profileId = profileId)
        dao.upsertSimklProfileMeta(
            existing.copy(
                connectionState = state.name,
                lastErrorMessage = when {
                    clearError -> null
                    error != null -> error
                    else -> existing.lastErrorMessage
                },
                updatedAtMs = clockMs()
            )
        )
    }

    private fun parseOAuthError(rawJson: String?): SimklOAuthErrorResponse? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching { errorAdapter.fromJson(rawJson) }.getOrNull()
    }

    private fun parseRetryAfterMs(headerValue: String?, attemptCount: Int): Long {
        val parsedSec = headerValue?.trim()?.toLongOrNull()?.takeIf { it >= 0L }
        return if (parsedSec != null) {
            (parsedSec * 1000L).coerceAtMost(300_000L)
        } else {
            computeBackoffMs(attemptCount)
        }
    }

    private fun computeBackoffMs(attemptCount: Int): Long {
        val shift = attemptCount.coerceIn(0, 5)
        return (5_000L shl shift).coerceAtMost(300_000L)
    }

    private fun roundProgress(value: Double): Double {
        val clamped = value.coerceIn(0.0, 100.0)
        return (clamped * 100.0).roundToLong() / 100.0
    }

    private fun buildContentKey(
        mediaType: String,
        tmdbId: Int,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): String {
        return if (mediaType == "tv" && seasonNumber != null && episodeNumber != null) {
            "tv_${tmdbId}_s${seasonNumber}_e${episodeNumber}"
        } else {
            "${mediaType}_${tmdbId}"
        }
    }

    private data class LibraryFetchOutcome(
        val succeeded: Boolean,
        val authLost: Boolean = false,
        val rateLimited: Boolean = false,
        val requestsCompleted: Int = 0,
        val requestsFailed: Int = 0,
        val shows: List<SimklLibraryParsedItem> = emptyList(),
        val movies: List<SimklLibraryParsedItem> = emptyList(),
        val anime: List<SimklLibraryParsedItem> = emptyList(),
        val errors: List<SimklSyncErrorEntry> = emptyList()
    )

    private data class RemovalCheckOutcome(
        val succeeded: Boolean,
        val authLost: Boolean = false,
        val requestsCompleted: Int = 0,
        val requestsFailed: Int = 0,
        val removedCount: Int = 0,
        val errors: List<SimklSyncErrorEntry> = emptyList()
    )

    private data class PersistLibraryStats(
        val showsImported: Int = 0,
        val moviesImported: Int = 0,
        val animeImported: Int = 0,
        val insertedCount: Int = 0,
        val updatedCount: Int = 0,
        val alreadyPresentCount: Int = 0,
        val tmdbUnrecognizedCount: Int = 0,
        val itemErrorsCount: Int = 0
    )

    private suspend fun fetchFullCategoryWithFallback(
        profileId: String,
        initialToken: String,
        category: String,
        extended: String? = if (category == SimklLibraryJsonParser.CATEGORY_MOVIES) null else "full",
        episodeWatchedAt: String?,
        includeAllEpisodes: String?,
        dateFrom: String? = null,
        triggerEvent: String = "SYNC"
    ): LibraryFetchOutcome {
        var reqCompleted = 0
        var reqFailed = 0
        val collectedErrors = ArrayList<SimklSyncErrorEntry>()
        val startedMs = clockMs()

        val primaryResp = try {
            executeWithAuthRetry(profileId, initialToken) { tok ->
                apiService.getAllItemsByType(
                    bearerToken = "Bearer $tok",
                    type = category,
                    dateFrom = dateFrom,
                    extended = extended,
                    episodeWatchedAt = episodeWatchedAt,
                    includeAllEpisodes = includeAllEpisodes
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            reqFailed++
            recordApiCallMetric("GET /sync/all-items/$category")
            collectedErrors.add(
                SimklSyncErrorEntry(
                    source = "/sync/all-items/$category",
                    errorType = e.javaClass.simpleName,
                    message = "Timeout/rete su /sync/all-items/$category, tentativo suddiviso per stato."
                )
            )
            null
        } catch (e: Exception) {
            reqFailed++
            recordApiCallMetric("GET /sync/all-items/$category")
            collectedErrors.add(
                SimklSyncErrorEntry(
                    source = "/sync/all-items/$category",
                    errorType = e.javaClass.simpleName,
                    message = "Errore richiesta /sync/all-items/$category."
                )
            )
            null
        }

        val primaryDurMs = (clockMs() - startedMs).coerceAtLeast(0L)
        if (primaryResp != null) {
            val code = primaryResp.code()
            if (primaryResp.isSuccessful) {
                reqCompleted++
                recordApiCallMetric("GET /sync/all-items/$category", httpCode = code)
                val rawBody = runCatching { primaryResp.body()?.use { it.string() } }.getOrNull()
                val respBytes = rawBody?.toByteArray()?.size?.toLong()
                val parsed = SimklLibraryJsonParser.parseAllItemsPayload(
                    rawJson = rawBody,
                    defaultCategory = category
                )
                collectedErrors.addAll(parsed.errors)
                if (parsed.isFatalPayloadError) {
                    reqFailed++
                    recordDiagnostic(
                        endpoint = "GET /sync/all-items/$category",
                        contentType = category,
                        profileId = profileId,
                        triggerEvent = triggerEvent,
                        outcome = "FATAL_PARSE_ERROR",
                        httpCode = code,
                        errorDetail = parsed.errors.firstOrNull()?.message ?: "Payload JSON non valido o troncato",
                        operationType = if (dateFrom != null) "DELTA_CATEGORY" else "FULL_CATEGORY",
                        durationMs = primaryDurMs,
                        responseBytes = respBytes
                    )
                    return LibraryFetchOutcome(
                        succeeded = false,
                        requestsCompleted = reqCompleted,
                        requestsFailed = reqFailed,
                        errors = collectedErrors
                    )
                }
                val itemCount = parsed.shows.size + parsed.movies.size + parsed.anime.size
                recordDiagnostic(
                    endpoint = "GET /sync/all-items/$category",
                    contentType = category,
                    profileId = profileId,
                    triggerEvent = triggerEvent,
                    outcome = if (parsed.errors.isEmpty()) "OK" else "OK_WITH_PARSE_WARNINGS",
                    httpCode = code,
                    rawResponse = "shows=${parsed.shows.size},movies=${parsed.movies.size},anime=${parsed.anime.size}",
                    errorDetail = parsed.errors.firstOrNull()?.message,
                    operationType = if (dateFrom != null) "DELTA_CATEGORY" else "FULL_CATEGORY",
                    durationMs = primaryDurMs,
                    responseBytes = respBytes,
                    itemsCount = itemCount
                )
                return LibraryFetchOutcome(
                    succeeded = true,
                    requestsCompleted = reqCompleted,
                    requestsFailed = reqFailed,
                    shows = deduplicateParsedItems(parsed.shows),
                    movies = deduplicateParsedItems(parsed.movies),
                    anime = deduplicateParsedItems(parsed.anime),
                    errors = collectedErrors
                )
            } else {
                val errRaw = runCatching { primaryResp.errorBody()?.string() }.getOrNull()
                val maxItemsHit = isMaxItemsError(code, errRaw)
                recordApiCallMetric("GET /sync/all-items/$category", httpCode = code, isMaxItems400 = maxItemsHit)
                if (code == 429) {
                    rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(primaryResp.headers()["Retry-After"], 0)
                }
                recordDiagnostic(
                    endpoint = "GET /sync/all-items/$category",
                    contentType = category,
                    profileId = profileId,
                    triggerEvent = triggerEvent,
                    outcome = when {
                        code == 429 -> "RATE_LIMITED_429"
                        maxItemsHit -> "MAX_ITEMS_SPLIT_BY_STATUS"
                        else -> "HTTP_ERROR"
                    },
                    httpCode = code,
                    rawResponse = errRaw,
                    errorDetail = "HTTP $code su /sync/all-items/$category",
                    operationType = if (dateFrom != null) "DELTA_CATEGORY" else "FULL_CATEGORY",
                    durationMs = primaryDurMs
                )
                reqFailed++
                // Su 429 non moltiplica MAI le chiamate in 5 status: rispetta Retry-After e interrompe pulitamente.
                if (code == 429) {
                    collectedErrors.add(
                        SimklSyncErrorEntry(
                            source = "/sync/all-items/$category",
                            httpCode = code,
                            errorType = "HTTP_429",
                            message = "Rate limit Simkl (HTTP 429) su /sync/all-items/$category: Retry-After applicato."
                        )
                    )
                    return LibraryFetchOutcome(
                        succeeded = false,
                        rateLimited = true,
                        requestsCompleted = reqCompleted,
                        requestsFailed = reqFailed,
                        errors = collectedErrors
                    )
                }
                val shouldChunkByStatus = maxItemsHit || code >= 500
                collectedErrors.add(
                    SimklSyncErrorEntry(
                        source = "/sync/all-items/$category",
                        httpCode = code,
                        errorType = if (maxItemsHit) "MAX_ITEMS" else "HTTP_$code",
                        message = if (shouldChunkByStatus) {
                            "Limite API Simkl su /sync/all-items/$category (HTTP $code): suddivisione per stato."
                        } else {
                            "Errore API Simkl su /sync/all-items/$category (HTTP $code)."
                        }
                    )
                )
                if (!shouldChunkByStatus) {
                    return LibraryFetchOutcome(
                        succeeded = false,
                        requestsCompleted = reqCompleted,
                        requestsFailed = reqFailed,
                        errors = collectedErrors
                    )
                }
            }
        } else if (tokenStore.readTokens(profileId) == null) {
            return LibraryFetchOutcome(
                succeeded = false,
                authLost = true,
                requestsCompleted = reqCompleted,
                requestsFailed = reqFailed,
                errors = collectedErrors
            )
        }

        // Escalation sequenziale per singolo stato su `400 max_items` / 413 / 5xx / timeout
        val statuses = if (category == SimklLibraryJsonParser.CATEGORY_MOVIES) {
            SimklLibraryJsonParser.ALL_MOVIE_STATUSES
        } else {
            SimklLibraryJsonParser.ALL_SHOW_ANIME_STATUSES
        }

        val chunkShows = ArrayList<SimklLibraryParsedItem>()
        val chunkMovies = ArrayList<SimklLibraryParsedItem>()
        val chunkAnime = ArrayList<SimklLibraryParsedItem>()
        var allChunksSucceeded = true
        var hitRateLimit = false

        for (status in statuses) {
            val tok = getValidAccessToken(profileId, forceRefresh = false)
                ?: return LibraryFetchOutcome(
                    succeeded = false,
                    authLost = true,
                    requestsCompleted = reqCompleted,
                    requestsFailed = reqFailed,
                    errors = collectedErrors
                )
            var currentIncludeAll = includeAllEpisodes
            var statusDone = false
            var attemptForStatus = 0
            while (!statusDone && attemptForStatus < 2) {
                attemptForStatus++
                val isFallbackOriginal = attemptForStatus == 2
                val statusStartMs = clockMs()
                val statusResp = try {
                    executeWithAuthRetry(profileId, tok) { authTok ->
                        apiService.getAllItemsByTypeAndStatus(
                            bearerToken = "Bearer $authTok",
                            type = category,
                            status = status,
                            dateFrom = dateFrom,
                            extended = extended,
                            episodeWatchedAt = episodeWatchedAt,
                            includeAllEpisodes = currentIncludeAll
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reqFailed++
                    recordApiCallMetric("GET /sync/all-items/$category/$status", isRetry = isFallbackOriginal)
                    allChunksSucceeded = false
                    collectedErrors.add(
                        SimklSyncErrorEntry(
                            source = "/sync/all-items/$category/$status",
                            errorType = e.javaClass.simpleName,
                            message = "Errore rete su /sync/all-items/$category/$status."
                        )
                    )
                    statusDone = true
                    null
                }

                if (statusResp == null) {
                    if (tokenStore.readTokens(profileId) == null) {
                        return LibraryFetchOutcome(
                            succeeded = false,
                            authLost = true,
                            requestsCompleted = reqCompleted,
                            requestsFailed = reqFailed,
                            errors = collectedErrors
                        )
                    }
                    allChunksSucceeded = false
                    break
                }

                val statusDurMs = (clockMs() - statusStartMs).coerceAtLeast(0L)
                val code = statusResp.code()
                if (statusResp.isSuccessful) {
                    reqCompleted++
                    recordApiCallMetric("GET /sync/all-items/$category/$status", httpCode = code, isRetry = isFallbackOriginal)
                    val rawBody = runCatching { statusResp.body()?.use { it.string() } }.getOrNull()
                    val respBytes = rawBody?.toByteArray()?.size?.toLong()
                    val parsed = SimklLibraryJsonParser.parseAllItemsPayload(
                        rawJson = rawBody,
                        defaultCategory = category,
                        forcedStatus = status
                    )
                    collectedErrors.addAll(parsed.errors)
                    if (parsed.isFatalPayloadError) {
                        reqFailed++
                        allChunksSucceeded = false
                    } else {
                        chunkShows.addAll(parsed.shows)
                        chunkMovies.addAll(parsed.movies)
                        chunkAnime.addAll(parsed.anime)
                        recordDiagnostic(
                            endpoint = "GET /sync/all-items/$category/$status",
                            contentType = category,
                            profileId = profileId,
                            triggerEvent = triggerEvent,
                            httpCode = code,
                            outcome = if (isFallbackOriginal) "OK_ORIGINAL_EPISODES_FALLBACK" else "OK",
                            operationType = "STATUS_SPLIT",
                            durationMs = statusDurMs,
                            retryCount = if (isFallbackOriginal) 1 else 0,
                            responseBytes = respBytes,
                            itemsCount = parsed.shows.size + parsed.movies.size + parsed.anime.size
                        )
                    }
                    statusDone = true
                } else {
                    val errRaw = runCatching { statusResp.errorBody()?.string() }.getOrNull()
                    val statusMaxItems = isMaxItemsError(code, errRaw)
                    recordApiCallMetric(
                        "GET /sync/all-items/$category/$status",
                        httpCode = code,
                        isMaxItems400 = statusMaxItems,
                        isRetry = isFallbackOriginal
                    )
                    reqFailed++
                    if (code == 429) {
                        rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(statusResp.headers()["Retry-After"], 0)
                        hitRateLimit = true
                        allChunksSucceeded = false
                        collectedErrors.add(
                            SimklSyncErrorEntry(
                                source = "/sync/all-items/$category/$status",
                                httpCode = 429,
                                errorType = "HTTP_429",
                                message = "Rate limit Simkl (HTTP 429) su /sync/all-items/$category/$status."
                            )
                        )
                        statusDone = true
                    } else if (statusMaxItems && currentIncludeAll == "yes" && attemptForStatus == 1) {
                        // Secondo documentazione Simkl: se uno stato con include_all_episodes=yes supera max_items,
                        // riprova una sola volta quello stato con include_all_episodes=original (mai richiesta identica).
                        recordDiagnostic(
                            endpoint = "GET /sync/all-items/$category/$status",
                            contentType = category,
                            profileId = profileId,
                            triggerEvent = triggerEvent,
                            httpCode = code,
                            rawResponse = errRaw,
                            outcome = "MAX_ITEMS_FALLBACK_ORIGINAL_EPISODES",
                            operationType = "STATUS_SPLIT",
                            durationMs = statusDurMs
                        )
                        currentIncludeAll = "original"
                    } else {
                        allChunksSucceeded = false
                        collectedErrors.add(
                            SimklSyncErrorEntry(
                                source = "/sync/all-items/$category/$status",
                                httpCode = code,
                                errorType = if (statusMaxItems) "MAX_ITEMS" else "HTTP_$code",
                                message = "Errore Simkl /sync/all-items/$category/$status (HTTP $code)."
                            )
                        )
                        statusDone = true
                    }
                }
            }
            if (hitRateLimit) break
        }

        return LibraryFetchOutcome(
            succeeded = allChunksSucceeded,
            rateLimited = hitRateLimit,
            requestsCompleted = reqCompleted,
            requestsFailed = reqFailed,
            shows = deduplicateParsedItems(chunkShows),
            movies = deduplicateParsedItems(chunkMovies),
            anime = deduplicateParsedItems(chunkAnime),
            errors = collectedErrors
        )
    }

    private suspend fun fetchIncrementalDelta(
        profileId: String,
        initialToken: String,
        dateFrom: String,
        extended: String?,
        episodeWatchedAt: String?,
        includeAllEpisodes: String?,
        changedCategories: List<String>,
        triggerEvent: String = "SYNC_INCREMENTAL"
    ): LibraryFetchOutcome {
        var reqCompleted = 0
        var reqFailed = 0
        val collectedErrors = ArrayList<SimklSyncErrorEntry>()
        val startedMs = clockMs()

        val resp = try {
            executeWithAuthRetry(profileId, initialToken) { tok ->
                apiService.getAllItems(
                    bearerToken = "Bearer $tok",
                    dateFrom = dateFrom,
                    extended = extended,
                    episodeWatchedAt = episodeWatchedAt,
                    includeAllEpisodes = includeAllEpisodes
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reqFailed++
            recordApiCallMetric("GET /sync/all-items")
            recordDiagnostic(
                endpoint = "GET /sync/all-items?date_from=…",
                contentType = "all",
                profileId = profileId,
                triggerEvent = triggerEvent,
                outcome = "NETWORK_ERROR",
                errorDetail = e.javaClass.simpleName,
                operationType = "DELTA",
                durationMs = (clockMs() - startedMs).coerceAtLeast(0L)
            )
            collectedErrors.add(
                SimklSyncErrorEntry(
                    source = "/sync/all-items?date_from=…",
                    errorType = e.javaClass.simpleName,
                    message = "Errore di rete su sync incrementale /sync/all-items."
                )
            )
            return LibraryFetchOutcome(
                succeeded = false,
                requestsCompleted = reqCompleted,
                requestsFailed = reqFailed,
                errors = collectedErrors
            )
        } ?: return LibraryFetchOutcome(
            succeeded = false,
            authLost = tokenStore.readTokens(profileId) == null,
            requestsCompleted = reqCompleted,
            requestsFailed = reqFailed + 1,
            errors = collectedErrors
        )

        val durMs = (clockMs() - startedMs).coerceAtLeast(0L)
        val code = resp.code()

        if (!resp.isSuccessful) {
            val errRaw = runCatching { resp.errorBody()?.string() }.getOrNull()
            val maxItemsHit = isMaxItemsError(code, errRaw)
            recordApiCallMetric("GET /sync/all-items", httpCode = code, isMaxItems400 = maxItemsHit)
            reqFailed++
            if (code == 429) {
                rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(resp.headers()["Retry-After"], 0)
            }
            recordDiagnostic(
                endpoint = "GET /sync/all-items?date_from=…",
                contentType = "all",
                profileId = profileId,
                triggerEvent = triggerEvent,
                outcome = when {
                    code == 429 -> "RATE_LIMITED_429"
                    maxItemsHit -> "MAX_ITEMS_SPLIT_BY_CATEGORY"
                    else -> "HTTP_ERROR"
                },
                httpCode = code,
                rawResponse = errRaw,
                errorDetail = "HTTP $code su sync incrementale",
                operationType = "DELTA",
                durationMs = durMs
            )

            // Escalation ufficiale Simkl per 400 max_items sul delta aggregato:
            // suddivide per singola categoria cambiata (e se necessario per stato e include_all_episodes=original).
            if (maxItemsHit) {
                val splitShows = ArrayList<SimklLibraryParsedItem>()
                val splitMovies = ArrayList<SimklLibraryParsedItem>()
                val splitAnime = ArrayList<SimklLibraryParsedItem>()
                var allSplitOk = true
                var hitRateLimit = false
                for (category in changedCategories) {
                    val isMovie = category == SimklLibraryJsonParser.CATEGORY_MOVIES
                    val catOutcome = fetchFullCategoryWithFallback(
                        profileId = profileId,
                        initialToken = initialToken,
                        category = category,
                        extended = if (isMovie) null else (extended ?: "full"),
                        episodeWatchedAt = if (isMovie) null else (episodeWatchedAt ?: "yes"),
                        includeAllEpisodes = if (isMovie) null else includeAllEpisodes,
                        dateFrom = dateFrom,
                        triggerEvent = "${triggerEvent}_MAX_ITEMS_SPLIT"
                    )
                    reqCompleted += catOutcome.requestsCompleted
                    reqFailed += catOutcome.requestsFailed
                    collectedErrors.addAll(catOutcome.errors)
                    if (catOutcome.authLost) {
                        return LibraryFetchOutcome(
                            succeeded = false,
                            authLost = true,
                            requestsCompleted = reqCompleted,
                            requestsFailed = reqFailed,
                            errors = collectedErrors
                        )
                    }
                    if (!catOutcome.succeeded) {
                        allSplitOk = false
                        if (catOutcome.rateLimited) {
                            hitRateLimit = true
                            break
                        }
                    } else {
                        splitShows.addAll(catOutcome.shows)
                        splitMovies.addAll(catOutcome.movies)
                        splitAnime.addAll(catOutcome.anime)
                    }
                }
                return LibraryFetchOutcome(
                    succeeded = allSplitOk,
                    rateLimited = hitRateLimit,
                    requestsCompleted = reqCompleted,
                    requestsFailed = reqFailed,
                    shows = deduplicateParsedItems(splitShows),
                    movies = deduplicateParsedItems(splitMovies),
                    anime = deduplicateParsedItems(splitAnime),
                    errors = collectedErrors
                )
            }

            collectedErrors.add(
                SimklSyncErrorEntry(
                    source = "/sync/all-items?date_from=…",
                    httpCode = code,
                    errorType = "HTTP_$code",
                    message = "Errore sync incrementale Simkl (HTTP $code)."
                )
            )
            return LibraryFetchOutcome(
                succeeded = false,
                rateLimited = code == 429,
                requestsCompleted = reqCompleted,
                requestsFailed = reqFailed,
                errors = collectedErrors
            )
        }

        reqCompleted++
        recordApiCallMetric("GET /sync/all-items", httpCode = code)
        val rawBody = runCatching { resp.body()?.use { it.string() } }.getOrNull()
        val respBytes = rawBody?.toByteArray()?.size?.toLong()
        val parsed = SimklLibraryJsonParser.parseAllItemsPayload(rawJson = rawBody)
        collectedErrors.addAll(parsed.errors)
        if (parsed.isFatalPayloadError) {
            reqFailed++
            recordDiagnostic(
                endpoint = "GET /sync/all-items?date_from=…",
                contentType = "all",
                profileId = profileId,
                triggerEvent = triggerEvent,
                outcome = "FATAL_PARSE_ERROR",
                httpCode = code,
                errorDetail = parsed.errors.firstOrNull()?.message ?: "Payload JSON delta non valido o troncato",
                operationType = "DELTA",
                durationMs = durMs,
                responseBytes = respBytes
            )
            return LibraryFetchOutcome(
                succeeded = false,
                requestsCompleted = reqCompleted,
                requestsFailed = reqFailed,
                errors = collectedErrors
            )
        }
        val itemCount = parsed.shows.size + parsed.movies.size + parsed.anime.size
        recordDiagnostic(
            endpoint = "GET /sync/all-items?date_from=…",
            contentType = "all",
            profileId = profileId,
            triggerEvent = triggerEvent,
            outcome = if (parsed.errors.isEmpty()) "OK" else "OK_WITH_PARSE_WARNINGS",
            httpCode = code,
            rawResponse = "shows=${parsed.shows.size},movies=${parsed.movies.size},anime=${parsed.anime.size}",
            errorDetail = parsed.errors.firstOrNull()?.message,
            operationType = "DELTA",
            durationMs = durMs,
            responseBytes = respBytes,
            itemsCount = itemCount
        )

        return LibraryFetchOutcome(
            succeeded = true,
            requestsCompleted = reqCompleted,
            requestsFailed = reqFailed,
            shows = deduplicateParsedItems(parsed.shows),
            movies = deduplicateParsedItems(parsed.movies),
            anime = deduplicateParsedItems(parsed.anime),
            errors = collectedErrors
        )
    }

    private suspend fun reconcileAggregatedRemovals(
        profileId: String,
        initialToken: String,
        localByCategory: Map<String, List<SimklLibraryItemEntity>>,
        triggerEvent: String = "SYNC_REMOVAL"
    ): RemovalCheckOutcome {
        val collectedErrors = ArrayList<SimklSyncErrorEntry>()
        val startedMs = clockMs()
        val resp = try {
            executeWithAuthRetry(profileId, initialToken) { tok ->
                apiService.getAllItems(
                    bearerToken = "Bearer $tok",
                    extended = "simkl_ids_only"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordApiCallMetric("GET /sync/all-items?extended=simkl_ids_only")
            collectedErrors.add(
                SimklSyncErrorEntry(
                    source = "/sync/all-items?extended=simkl_ids_only",
                    errorType = e.javaClass.simpleName,
                    message = "Errore rete verifica rimozioni aggregate."
                )
            )
            return RemovalCheckOutcome(succeeded = false, requestsFailed = 1, errors = collectedErrors)
        } ?: return RemovalCheckOutcome(
            succeeded = false,
            authLost = tokenStore.readTokens(profileId) == null,
            requestsFailed = 1
        )

        val durMs = (clockMs() - startedMs).coerceAtLeast(0L)
        val code = resp.code()
        if (!resp.isSuccessful) {
            val errRaw = runCatching { resp.errorBody()?.string() }.getOrNull()
            val maxItemsHit = isMaxItemsError(code, errRaw)
            recordApiCallMetric("GET /sync/all-items?extended=simkl_ids_only", httpCode = code, isMaxItems400 = maxItemsHit)
            if (code == 429) {
                rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(resp.headers()["Retry-After"], 0)
            }
            if (maxItemsHit) {
                // Escalation: verifica rimozioni categoria per categoria
                var reqCompleted = 0
                var reqFailed = 1
                var totalRemoved = 0
                var allOk = true
                for ((category, localItems) in localByCategory) {
                    val catOut = reconcileCategoryRemovals(
                        profileId = profileId,
                        initialToken = initialToken,
                        category = category,
                        localItems = localItems,
                        triggerEvent = "${triggerEvent}_SPLIT"
                    )
                    reqCompleted += catOut.requestsCompleted
                    reqFailed += catOut.requestsFailed
                    totalRemoved += catOut.removedCount
                    collectedErrors.addAll(catOut.errors)
                    if (catOut.authLost) return RemovalCheckOutcome(false, authLost = true, requestsCompleted = reqCompleted, requestsFailed = reqFailed, errors = collectedErrors)
                    if (!catOut.succeeded) allOk = false
                }
                return RemovalCheckOutcome(
                    succeeded = allOk,
                    requestsCompleted = reqCompleted,
                    requestsFailed = reqFailed,
                    removedCount = totalRemoved,
                    errors = collectedErrors
                )
            }
            collectedErrors.add(
                SimklSyncErrorEntry(
                    source = "/sync/all-items?extended=simkl_ids_only",
                    httpCode = code,
                    errorType = "HTTP_$code",
                    message = "Errore verifica rimozioni aggregate (HTTP $code)."
                )
            )
            return RemovalCheckOutcome(succeeded = false, requestsFailed = 1, errors = collectedErrors)
        }

        recordApiCallMetric("GET /sync/all-items?extended=simkl_ids_only", httpCode = code)
        val rawBody = runCatching { resp.body()?.use { it.string() } }.getOrNull()
        val respBytes = rawBody?.toByteArray()?.size?.toLong()
        var totalRemoved = 0
        for ((category, localItems) in localByCategory) {
            val remoteKeys = SimklLibraryJsonParser.extractKeysForRemoval(rawBody, category)
            if (remoteKeys == null) {
                collectedErrors.add(
                    SimklSyncErrorEntry(
                        source = "/sync/all-items?extended=simkl_ids_only",
                        errorType = "PARSE_REMOVAL_IDS",
                        message = "Risposta simkl_ids_only aggregata non interpretabile per $category."
                    )
                )
                return RemovalCheckOutcome(succeeded = false, requestsCompleted = 1, errors = collectedErrors)
            }
            totalRemoved += applyCategoryRemovals(profileId, localItems, remoteKeys)
        }
        recordDiagnostic(
            endpoint = "GET /sync/all-items?extended=simkl_ids_only",
            contentType = "all",
            profileId = profileId,
            triggerEvent = triggerEvent,
            outcome = "OK_REMOVED_$totalRemoved",
            httpCode = code,
            operationType = "REMOVALS_AGGREGATED",
            durationMs = durMs,
            responseBytes = respBytes,
            itemsCount = totalRemoved
        )
        return RemovalCheckOutcome(
            succeeded = true,
            requestsCompleted = 1,
            removedCount = totalRemoved
        )
    }

    private suspend fun applyCategoryRemovals(
        profileId: String,
        localItems: List<SimklLibraryItemEntity>,
        remoteKeys: Set<String>
    ): Int {
        val removedEntities = localItems.filter { it.stableKey !in remoteKeys }
        val keysToRemove = removedEntities.map { it.stableKey }
        if (keysToRemove.isNotEmpty()) {
            keysToRemove.chunked(200).forEach { chunk ->
                dao.deleteSimklLibraryItemsByKeys(profileId, chunk)
                dao.deleteSimklEpisodeStatesBySeriesKeys(profileId, chunk)
            }
            for (removed in removedEntities) {
                val tmdbId = removed.tmdbId ?: continue
                if (!isActivePlaybackTarget(profileId, removed.tmdbMediaType, tmdbId)) {
                    dao.deleteWatchProgress("${removed.tmdbMediaType}_$tmdbId", profileId)
                }
                dao.deleteSimklBaselinesForMedia(profileId, tmdbId, removed.tmdbMediaType)
                if (removed.tmdbMediaType == "tv") {
                    dao.deleteCompletedEpisodesForSeries(tmdbId, profileId)
                    dao.deleteEpisodeProgressForSeries(tmdbId, profileId)
                } else {
                    dao.deleteCompletedMedia(
                        PlayerSegmentKeys.completedMediaKey(tmdbId, "movie"),
                        profileId
                    )
                }
            }
        }
        return keysToRemove.size
    }

    private suspend fun reconcileCategoryRemovals(
        profileId: String,
        initialToken: String,
        category: String,
        localItems: List<SimklLibraryItemEntity>,
        triggerEvent: String = "SYNC_REMOVAL"
    ): RemovalCheckOutcome {
        val collectedErrors = ArrayList<SimklSyncErrorEntry>()
        val startedMs = clockMs()
        val resp = try {
            executeWithAuthRetry(profileId, initialToken) { tok ->
                apiService.getAllItemsByType(
                    bearerToken = "Bearer $tok",
                    type = category,
                    extended = "simkl_ids_only"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordApiCallMetric("GET /sync/all-items/$category?extended=simkl_ids_only")
            recordDiagnostic(
                endpoint = "GET /sync/all-items/$category?extended=simkl_ids_only",
                contentType = category,
                profileId = profileId,
                triggerEvent = triggerEvent,
                outcome = "NETWORK_ERROR",
                errorDetail = e.javaClass.simpleName,
                operationType = "REMOVALS",
                durationMs = (clockMs() - startedMs).coerceAtLeast(0L)
            )
            collectedErrors.add(
                SimklSyncErrorEntry(
                    source = "/sync/all-items/$category?extended=simkl_ids_only",
                    errorType = e.javaClass.simpleName,
                    message = "Errore rete verifica rimozioni $category."
                )
            )
            return RemovalCheckOutcome(
                succeeded = false,
                requestsFailed = 1,
                errors = collectedErrors
            )
        } ?: return RemovalCheckOutcome(
            succeeded = false,
            authLost = tokenStore.readTokens(profileId) == null,
            requestsFailed = 1
        )

        val durMs = (clockMs() - startedMs).coerceAtLeast(0L)
        val code = resp.code()
        if (!resp.isSuccessful) {
            runCatching { resp.errorBody()?.close() }
            recordApiCallMetric("GET /sync/all-items/$category?extended=simkl_ids_only", httpCode = code)
            if (code == 429) {
                rateLimitUntilMsByProfile[profileId] = clockMs() + parseRetryAfterMs(resp.headers()["Retry-After"], 0)
            }
            recordDiagnostic(
                endpoint = "GET /sync/all-items/$category?extended=simkl_ids_only",
                contentType = category,
                profileId = profileId,
                triggerEvent = triggerEvent,
                outcome = if (code == 429) "RATE_LIMITED_429" else "HTTP_ERROR",
                httpCode = code,
                operationType = "REMOVALS",
                durationMs = durMs
            )
            collectedErrors.add(
                SimklSyncErrorEntry(
                    source = "/sync/all-items/$category?extended=simkl_ids_only",
                    httpCode = code,
                    errorType = "HTTP_$code",
                    message = "Errore verifica rimozioni $category (HTTP $code)."
                )
            )
            return RemovalCheckOutcome(
                succeeded = false,
                requestsFailed = 1,
                errors = collectedErrors
            )
        }

        recordApiCallMetric("GET /sync/all-items/$category?extended=simkl_ids_only", httpCode = code)
        val rawBody = runCatching { resp.body()?.use { it.string() } }.getOrNull()
        val respBytes = rawBody?.toByteArray()?.size?.toLong()
        val remoteKeys = SimklLibraryJsonParser.extractKeysForRemoval(rawBody, category)
        if (remoteKeys == null) {
            collectedErrors.add(
                SimklSyncErrorEntry(
                    source = "/sync/all-items/$category?extended=simkl_ids_only",
                    errorType = "PARSE_REMOVAL_IDS",
                    message = "Risposta simkl_ids_only non interpretabile per $category."
                )
            )
            return RemovalCheckOutcome(
                succeeded = false,
                requestsCompleted = 1,
                requestsFailed = 0,
                errors = collectedErrors
            )
        }

        val removedCount = applyCategoryRemovals(profileId, localItems, remoteKeys)
        recordDiagnostic(
            endpoint = "GET /sync/all-items/$category?extended=simkl_ids_only",
            contentType = category,
            profileId = profileId,
            triggerEvent = triggerEvent,
            outcome = "OK_REMOVED_$removedCount",
            httpCode = code,
            operationType = "REMOVALS",
            durationMs = durMs,
            responseBytes = respBytes,
            itemsCount = removedCount
        )
        return RemovalCheckOutcome(
            succeeded = true,
            requestsCompleted = 1,
            removedCount = removedCount
        )
    }

    private suspend fun persistParsedLibraryItems(
        profileId: String,
        shows: List<SimklLibraryParsedItem>,
        movies: List<SimklLibraryParsedItem>,
        anime: List<SimklLibraryParsedItem>,
        existingErrorsCount: Int,
        isFullShowsSnapshot: Boolean = false,
        isFullMoviesSnapshot: Boolean = false,
        isFullAnimeSnapshot: Boolean = false
    ): PersistLibraryStats {
        val totalShows = shows.size
        val totalMovies = movies.size
        val totalAnime = anime.size

        if (totalShows == 0 && totalMovies == 0 && totalAnime == 0) {
            return PersistLibraryStats(itemErrorsCount = existingErrorsCount)
        }

        val existingMap = dao.getSimklLibraryForProfile(profileId).associateBy { it.stableKey }
        val pendingEntries = dao.getSimklPendingScrobbles(profileId)

        var showsProcessed = 0
        var moviesProcessed = 0
        var animeProcessed = 0
        var showsImported = 0
        var moviesImported = 0
        var animeImported = 0
        var insertedCount = 0
        var updatedCount = 0
        var alreadyPresentCount = 0
        var tmdbUnrecognizedCount = 0
        var itemErrorsCount = existingErrorsCount

        fun publishProgress(phaseLabel: String) {
            updateSyncProgress(
                profileId,
                SimklSyncProgress(
                    showsProcessed = showsProcessed,
                    showsTotal = totalShows,
                    moviesProcessed = moviesProcessed,
                    moviesTotal = totalMovies,
                    animeProcessed = animeProcessed,
                    animeTotal = totalAnime,
                    currentPhaseLabel = phaseLabel
                )
            )
        }

        publishProgress("Importazione libreria Simkl…")

        val entitiesToUpsert = ArrayList<SimklLibraryItemEntity>(totalShows + totalMovies + totalAnime)
        val combined = shows + movies + anime
        for (item in combined) {
            try {
                val existing = existingMap[item.stableKey]
                val resolvedTmdbId = resolveTmdbIdForLibraryItem(item, existing)
                if (resolvedTmdbId == null) {
                    tmdbUnrecognizedCount++
                }

                var enrichedPoster = existing?.takeIf { it.tmdbId == resolvedTmdbId }?.posterPath
                var enrichedBackdrop = existing?.takeIf { it.tmdbId == resolvedTmdbId }?.backdropPath
                var enrichedOverview = existing?.takeIf { it.tmdbId == resolvedTmdbId }?.overview.orEmpty()
                var enrichedVoteAverage = existing?.takeIf { it.tmdbId == resolvedTmdbId }?.voteAverage ?: 0.0
                var enrichedYear = item.year

                if (resolvedTmdbId != null && (enrichedPoster == null || enrichedOverview.isBlank())) {
                    val probeMedia = MediaItem(
                        id = resolvedTmdbId,
                        mediaType = item.tmdbMediaType,
                        title = item.title,
                        overview = enrichedOverview,
                        posterPath = enrichedPoster,
                        backdropPath = enrichedBackdrop,
                        voteAverage = enrichedVoteAverage,
                        releaseYear = item.year?.toString().orEmpty()
                    )
                    val cachedDetails = runCatching { repository.getCachedMediaDetails(probeMedia)?.value }.getOrNull()
                    val shouldFetchDetailsRemotely = item.status == SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH ||
                        item.status == SimklLibraryJsonParser.STATUS_WATCHING ||
                        item.status == SimklLibraryJsonParser.STATUS_SEEN
                    val tmdbDetails = cachedDetails ?: if (shouldFetchDetailsRemotely) {
                        runCatching { repository.fetchMediaDetails(probeMedia).getOrNull() }.getOrNull()
                    } else {
                        null
                    }
                    if (tmdbDetails != null) {
                        enrichedPoster = tmdbDetails.item.posterPath ?: enrichedPoster
                        enrichedBackdrop = tmdbDetails.item.backdropPath ?: enrichedBackdrop
                        enrichedOverview = tmdbDetails.item.overview.takeIf { it.isNotBlank() } ?: enrichedOverview
                        if (tmdbDetails.item.voteAverage > 0.0) {
                            enrichedVoteAverage = tmdbDetails.item.voteAverage
                        }
                        if (enrichedYear == null) {
                            enrichedYear = tmdbDetails.item.releaseYear.take(4).toIntOrNull()
                        }
                    }
                }

                val isFullCategory = when (item.mediaCategory) {
                    SimklLibraryJsonParser.CATEGORY_SHOWS -> isFullShowsSnapshot
                    SimklLibraryJsonParser.CATEGORY_MOVIES -> isFullMoviesSnapshot
                    else -> isFullAnimeSnapshot
                }

                // Merge deterministico per singolo episodio su simkl_episode_state (aggiunte + rimozioni + episodi speciali)
                val episodeMerge = if (item.tmdbMediaType == "tv") {
                    mergeAndPersistEpisodeStates(
                        profileId = profileId,
                        item = item,
                        existing = existing,
                        resolvedTmdbId = resolvedTmdbId,
                        isFullCategorySnapshot = isFullCategory,
                        pendingEntries = pendingEntries
                    )
                } else {
                    EpisodeMergeResult(
                        mergedSeasons = emptyList(),
                        seasonsJson = null,
                        watchedEpisodesCount = item.watchedEpisodesCount,
                        totalEpisodesCount = item.totalEpisodesCount,
                        watchedPairs = emptySet(),
                        unwatchedPairs = emptySet(),
                        pendingAddPairs = emptySet()
                    )
                }

                val resolvedLastWatchedEpisodeCode = recomputeLastWatchedEpisodeCode(item, episodeMerge)
                val resolvedNextToWatch = recomputeNextToWatchCode(item, episodeMerge)

                val lastWatchedMs = parseIsoUtcMillis(item.lastWatchedAt)
                val entity = SimklLibraryItemEntity(
                    profileId = profileId,
                    stableKey = item.stableKey,
                    simklId = item.simklId,
                    mediaCategory = item.mediaCategory,
                    tmdbMediaType = item.tmdbMediaType,
                    tmdbId = resolvedTmdbId,
                    imdbId = item.imdbId ?: existing?.imdbId,
                    tvdbId = item.tvdbId ?: existing?.tvdbId,
                    slug = item.slug ?: existing?.slug,
                    title = item.title,
                    year = enrichedYear,
                    status = item.status,
                    watchedEpisodesCount = episodeMerge.watchedEpisodesCount,
                    totalEpisodesCount = episodeMerge.totalEpisodesCount,
                    lastWatchedAt = item.lastWatchedAt ?: existing?.lastWatchedAt,
                    lastWatchedAtMs = if (lastWatchedMs > 0L) lastWatchedMs else (existing?.lastWatchedAtMs ?: 0L),
                    nextToWatch = resolvedNextToWatch,
                    lastWatchedEpisodeCode = resolvedLastWatchedEpisodeCode,
                    seasonsJson = episodeMerge.seasonsJson,
                    tmdbResolved = resolvedTmdbId != null,
                    posterPath = enrichedPoster,
                    backdropPath = enrichedBackdrop,
                    overview = enrichedOverview,
                    voteAverage = enrichedVoteAverage,
                    updatedAtMs = clockMs()
                )
                entitiesToUpsert.add(entity)

                when {
                    existing == null -> insertedCount++
                    hasLibraryItemChanged(existing, entity) -> updatedCount++
                    else -> alreadyPresentCount++
                }

                if (resolvedTmdbId != null) {
                    propagateLibraryItemToLocalProgress(
                        profileId = profileId,
                        item = item.copy(
                            lastWatchedEpisodeCode = resolvedLastWatchedEpisodeCode,
                            nextToWatch = resolvedNextToWatch
                        ),
                        existing = existing,
                        tmdbId = resolvedTmdbId,
                        lastWatchedMs = entity.lastWatchedAtMs,
                        episodeMerge = episodeMerge,
                        pendingEntries = pendingEntries
                    )
                }

                when (item.mediaCategory) {
                    SimklLibraryJsonParser.CATEGORY_SHOWS -> showsImported++
                    SimklLibraryJsonParser.CATEGORY_MOVIES -> moviesImported++
                    SimklLibraryJsonParser.CATEGORY_ANIME -> animeImported++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                itemErrorsCount++
            } finally {
                when (item.mediaCategory) {
                    SimklLibraryJsonParser.CATEGORY_SHOWS -> showsProcessed++
                    SimklLibraryJsonParser.CATEGORY_MOVIES -> moviesProcessed++
                    SimklLibraryJsonParser.CATEGORY_ANIME -> animeProcessed++
                }
                publishProgress("Importazione libreria Simkl…")
            }
        }

        if (entitiesToUpsert.isNotEmpty()) {
            entitiesToUpsert.chunked(100).forEach { chunk ->
                dao.upsertSimklLibraryItems(chunk)
            }
        }

        return PersistLibraryStats(
            showsImported = showsImported,
            moviesImported = moviesImported,
            animeImported = animeImported,
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            alreadyPresentCount = alreadyPresentCount,
            tmdbUnrecognizedCount = tmdbUnrecognizedCount,
            itemErrorsCount = itemErrorsCount
        )
    }

    private data class EpisodeMergeResult(
        val mergedSeasons: List<SimklParsedSeason>,
        val seasonsJson: String?,
        val watchedEpisodesCount: Int,
        val totalEpisodesCount: Int,
        val watchedPairs: Set<Pair<Int, Int>>,
        val unwatchedPairs: Set<Pair<Int, Int>>,
        val pendingAddPairs: Set<Pair<Int, Int>>,
        val watchedAtMsByPair: Map<Pair<Int, Int>, Long> = emptyMap()
    )

    private suspend fun mergeAndPersistEpisodeStates(
        profileId: String,
        item: SimklLibraryParsedItem,
        existing: SimklLibraryItemEntity?,
        resolvedTmdbId: Int?,
        isFullCategorySnapshot: Boolean,
        pendingEntries: List<SimklPendingScrobbleEntity>
    ): EpisodeMergeResult {
        val nowMs = clockMs()
        val existingStates = dao.getSimklEpisodeStatesForSeries(profileId, item.stableKey)
            .ifEmpty {
                // Migrazione trasparente da seasonsJson legacy a simkl_episode_state se tabella vuota
                SimklLibraryJsonParser.deserializeSeasonsJson(existing?.seasonsJson).flatMap { s ->
                    s.episodes.map { ep ->
                        SimklEpisodeStateEntity(
                            profileId = profileId,
                            seriesStableKey = item.stableKey,
                            simklSeriesId = item.simklId,
                            tmdbSeriesId = resolvedTmdbId ?: existing?.tmdbId,
                            seasonNumber = s.seasonNumber,
                            episodeNumber = ep.episodeNumber,
                            simklEpisodeId = ep.simklEpisodeId,
                            watched = ep.watched,
                            watchedAt = ep.watchedAt,
                            watchedAtMs = parseIsoUtcMillis(ep.watchedAt),
                            updatedAtMs = existing?.updatedAtMs ?: nowMs
                        )
                    }
                }
            }

        val stateMap = LinkedHashMap<Pair<Int, Int>, SimklEpisodeStateEntity>(existingStates.size + 16)
        for (st in existingStates) {
            stateMap[st.seasonNumber to st.episodeNumber] = st
        }
        if (resolvedTmdbId != null) {
            // Consolida eventuali righe ottimistiche/orfane (es. "ext_shows_tmdb_<id>") nella chiave canonica `item.stableKey`
            val byTmdbStates = dao.getSimklEpisodeStatesByTmdb(profileId, resolvedTmdbId)
            val orphanKeys = HashSet<String>()
            for (st in byTmdbStates) {
                if (st.seriesStableKey != item.stableKey) {
                    orphanKeys.add(st.seriesStableKey)
                    val pair = st.seasonNumber to st.episodeNumber
                    if (pair !in stateMap) {
                        stateMap[pair] = st.copy(
                            seriesStableKey = item.stableKey,
                            simklSeriesId = item.simklId,
                            tmdbSeriesId = resolvedTmdbId
                        )
                    }
                }
            }
            if (orphanKeys.isNotEmpty()) {
                dao.deleteSimklEpisodeStatesBySeriesKeys(profileId, orphanKeys.toList())
            }
        }
        if (resolvedTmdbId != null) {
            // Include anche eventuali episodi completati localmente in completed_media così che l'unwatch
            // da Simkl li riallinei correttamente anche se non erano stati popolati in simkl_episode_state.
            val localCompleted = dao.getCompletedEpisodesForSeries(resolvedTmdbId, profileId)
            for (tuple in localCompleted) {
                val pair = tuple.seasonNumber to tuple.episodeNumber
                val prev = stateMap[pair]
                if (prev == null) {
                    stateMap[pair] = SimklEpisodeStateEntity(
                        profileId = profileId,
                        seriesStableKey = item.stableKey,
                        simklSeriesId = item.simklId,
                        tmdbSeriesId = resolvedTmdbId,
                        seasonNumber = tuple.seasonNumber,
                        episodeNumber = tuple.episodeNumber,
                        watched = true,
                        updatedAtMs = nowMs
                    )
                } else if (!prev.watched && !item.hasSeasonsField) {
                    stateMap[pair] = prev.copy(watched = true, updatedAtMs = nowMs)
                }
            }
        }

        // Episodi con scritture locali offline non ancora inviate: non devono essere sovrascritti da uno snapshot remoto precedente
        val pendingAddEpisodes = HashSet<Pair<Int, Int>>()
        val pendingRemoveEpisodes = HashSet<Pair<Int, Int>>()
        if (resolvedTmdbId != null) {
            for (p in pendingEntries) {
                if (p.mediaType != "tv" || p.tmdbId != resolvedTmdbId) continue
                when (p.action) {
                    "history_add" -> {
                        val batch = SimklLibraryJsonParser.deserializeBatchEpisodes(p.batchEpisodesJson)
                        if (batch.isNotEmpty()) pendingAddEpisodes.addAll(batch)
                        else if (p.seasonNumber != null && p.episodeNumber != null) {
                            pendingAddEpisodes.add(p.seasonNumber to p.episodeNumber)
                        }
                    }
                    "history_remove" -> {
                        val batch = SimklLibraryJsonParser.deserializeBatchEpisodes(p.batchEpisodesJson)
                        if (batch.isNotEmpty()) pendingRemoveEpisodes.addAll(batch)
                        else if (p.seasonNumber != null && p.episodeNumber != null) {
                            pendingRemoveEpisodes.add(p.seasonNumber to p.episodeNumber)
                        }
                    }
                    "stop" -> {
                        if (p.progressPercent >= p.minProgress.toDouble() && p.seasonNumber != null && p.episodeNumber != null) {
                            pendingAddEpisodes.add(p.seasonNumber to p.episodeNumber)
                        }
                    }
                }
            }
        }

        val unwatchedPairs = LinkedHashSet<Pair<Int, Int>>()

        if (!item.hasSeasonsField) {
            // Se Simkl dichiara esplicitamente 0 episodi visti (o stato "seen" con 0 episodi / "plantowatch"),
            // annulla eventuali episodi precedentemente marcati come visti localmente.
            val explicitZeroWatched = (item.hasExplicitWatchedCount && item.watchedEpisodesCount == 0) ||
                (item.status == SimklLibraryJsonParser.STATUS_SEEN && item.watchedEpisodesCount == 0) ||
                (item.status == SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH &&
                    existing != null &&
                    existing.status != SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH &&
                    item.watchedEpisodesCount == 0)
            if (explicitZeroWatched) {
                for ((key, prev) in stateMap.toList()) {
                    if (prev.watched && key !in pendingAddEpisodes) {
                        stateMap[key] = prev.copy(watched = false, watchedAt = null, watchedAtMs = 0L, updatedAtMs = nowMs)
                        unwatchedPairs.add(key)
                    }
                }
            }
        } else {
            val incomingKeys = HashSet<Pair<Int, Int>>()

            for (season in item.seasons) {
                for (ep in season.episodes) {
                    val pair = season.seasonNumber to ep.episodeNumber
                    incomingKeys.add(pair)
                    val prev = stateMap[pair]
                    val remoteWatchedAtMs = parseIsoUtcMillis(ep.watchedAt)
                    val effectiveWatched = when {
                        pair in pendingAddEpisodes -> true
                        pair in pendingRemoveEpisodes -> false
                        else -> ep.watched
                    }
                    if (prev?.watched == true && !effectiveWatched) {
                        unwatchedPairs.add(pair)
                    }
                    stateMap[pair] = SimklEpisodeStateEntity(
                        profileId = profileId,
                        seriesStableKey = item.stableKey,
                        simklSeriesId = item.simklId,
                        tmdbSeriesId = resolvedTmdbId ?: prev?.tmdbSeriesId,
                        seasonNumber = season.seasonNumber,
                        episodeNumber = ep.episodeNumber,
                        simklEpisodeId = ep.simklEpisodeId ?: prev?.simklEpisodeId,
                        watched = effectiveWatched,
                        watchedAt = if (effectiveWatched) (ep.watchedAt ?: prev?.watchedAt) else null,
                        watchedAtMs = if (effectiveWatched) maxOf(remoteWatchedAtMs, prev?.watchedAtMs ?: 0L) else 0L,
                        updatedAtMs = nowMs
                    )
                }
            }

            // Quando Simkl include il campo `seasons`, quello rappresenta l'elenco autorevole degli episodi della serie:
            // qualunque episodio precedentemente visto ma assente dal payload remoto (o con watched=false)
            // viene riportato a NON visto, gestendo correttamente unwatch parziali, netti invariati e intere serie.
            val canInferUnwatchByOmission = isFullCategorySnapshot || item.hasSeasonsField
            if (canInferUnwatchByOmission) {
                for ((pair, prev) in stateMap.toList()) {
                    if (prev.watched && pair !in incomingKeys && pair !in pendingAddEpisodes) {
                        stateMap[pair] = prev.copy(
                            watched = false,
                            watchedAt = null,
                            watchedAtMs = 0L,
                            updatedAtMs = nowMs
                        )
                        unwatchedPairs.add(pair)
                    }
                }
            }
        }

        val allStates = stateMap.values.sortedWith(compareBy<SimklEpisodeStateEntity> { it.seasonNumber }.thenBy { it.episodeNumber })
        if (allStates.isNotEmpty()) {
            allStates.chunked(200).forEach { chunk ->
                dao.upsertSimklEpisodeStates(chunk)
            }
        }

        val mergedSeasons = allStates.groupBy { it.seasonNumber }
            .toSortedMap()
            .map { (seasonNum, eps) ->
                SimklParsedSeason(
                    seasonNumber = seasonNum,
                    episodes = eps.map { st ->
                        SimklParsedEpisode(
                            seasonNumber = st.seasonNumber,
                            episodeNumber = st.episodeNumber,
                            watched = st.watched,
                            watchedAt = st.watchedAt,
                            simklEpisodeId = st.simklEpisodeId
                        )
                    }
                )
            }

        val watchedPairs = allStates.filter { it.watched }.map { it.seasonNumber to it.episodeNumber }.toSet()
        val isAuthoritativeCount = item.hasSeasonsField ||
            (item.hasExplicitWatchedCount && item.watchedEpisodesCount == 0) ||
            (item.status == SimklLibraryJsonParser.STATUS_SEEN && item.watchedEpisodesCount == 0)
        val finalWatchedCount = if (isAuthoritativeCount) {
            watchedPairs.size
        } else {
            maxOf(item.watchedEpisodesCount, watchedPairs.size)
        }
        val finalTotalCount = maxOf(item.totalEpisodesCount, allStates.size, finalWatchedCount)

        val serializedSeasons = if (mergedSeasons.isEmpty() && (item.hasSeasonsField || unwatchedPairs.isNotEmpty())) {
            null
        } else {
            SimklLibraryJsonParser.serializeSeasonsJson(mergedSeasons) ?: existing?.seasonsJson
        }

        val watchedAtMsByPair = allStates
            .filter { it.watched && it.watchedAtMs > 0L }
            .associate { (it.seasonNumber to it.episodeNumber) to it.watchedAtMs }

        return EpisodeMergeResult(
            mergedSeasons = mergedSeasons,
            seasonsJson = serializedSeasons,
            watchedEpisodesCount = finalWatchedCount,
            totalEpisodesCount = finalTotalCount,
            watchedPairs = watchedPairs,
            unwatchedPairs = unwatchedPairs,
            pendingAddPairs = pendingAddEpisodes,
            watchedAtMsByPair = watchedAtMsByPair
        )
    }

    private suspend fun resolveTmdbIdForLibraryItem(
        item: SimklLibraryParsedItem,
        existing: SimklLibraryItemEntity?
    ): Int? {
        item.tmdbId?.takeIf { it > 0 }?.let { return it }
        existing?.tmdbId?.takeIf { it > 0 }?.let { return it }
        if (item.imdbId.isNullOrBlank() && (item.tvdbId == null || item.tvdbId <= 0)) {
            return null
        }
        return runCatching {
            repository.findTmdbMediaByExternalId(
                imdbId = item.imdbId,
                tvdbId = item.tvdbId,
                expectedType = item.tmdbMediaType
            )?.id?.takeIf { it > 0 }
        }.getOrNull()
    }

    private suspend fun transitionLibraryStatusOnPlayback(
        profileId: String,
        tmdbId: Int,
        tmdbMediaType: String,
        action: String,
        progressPercent: Double,
        minProgress: Int
    ) {
        if (tmdbId <= 0) return
        val normalizedType = if (tmdbMediaType == "tv") "tv" else "movie"
        val matchingItems = dao.getSimklLibraryByTmdb(profileId, tmdbId, normalizedType)
        if (matchingItems.isEmpty()) return
        val now = clockMs()
        for (libraryItem in matchingItems) {
            val targetStatus = when {
                normalizedType == "movie" &&
                    action == "stop" &&
                    progressPercent >= minProgress.toDouble() &&
                    libraryItem.status != SimklLibraryJsonParser.STATUS_COMPLETED ->
                    SimklLibraryJsonParser.STATUS_COMPLETED
                libraryItem.status == SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH ->
                    SimklLibraryJsonParser.STATUS_WATCHING
                else -> null
            }
            if (targetStatus != null) {
                dao.updateSimklLibraryItemStatus(
                    profileId = profileId,
                    stableKey = libraryItem.stableKey,
                    newStatus = targetStatus,
                    updatedAtMs = now
                )
            }
        }
    }

    private suspend fun propagateLibraryItemToLocalProgress(
        profileId: String,
        item: SimklLibraryParsedItem,
        existing: SimklLibraryItemEntity?,
        tmdbId: Int,
        lastWatchedMs: Long,
        episodeMerge: EpisodeMergeResult,
        pendingEntries: List<SimklPendingScrobbleEntity>
    ) {
        val completedTimestamp = if (lastWatchedMs > 0L) lastWatchedMs else clockMs()
        val hasPendingForMedia = pendingEntries.any { it.tmdbId == tmdbId && it.mediaType == item.tmdbMediaType }

        if (item.tmdbMediaType == "movie") {
            val movieKey = PlayerSegmentKeys.completedMediaKey(tmdbId, "movie")
            val mainKey = "movie_$tmdbId"
            when (item.status) {
                SimklLibraryJsonParser.STATUS_COMPLETED -> {
                    if (dao.getCompletedMedia(movieKey, profileId) == null) {
                        dao.upsertCompletedMedia(
                            CompletedMediaEntity(
                                profileId = profileId,
                                contentKey = movieKey,
                                tmdbId = tmdbId,
                                mediaType = "movie",
                                completedAtMs = completedTimestamp
                            )
                        )
                    }
                    if (!isActivePlaybackTarget(profileId, "movie", tmdbId) && !hasPendingForMedia) {
                        dao.deleteWatchProgress(mainKey, profileId)
                        dao.deleteSimklBaseline(profileId, mainKey)
                    }
                }
                SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH,
                SimklLibraryJsonParser.STATUS_HOLD,
                SimklLibraryJsonParser.STATUS_DROPPED -> {
                    // Stati non completati / esclusi da Continua a guardare: Simkl è fonte di verità
                    if (!hasPendingForMedia) {
                        dao.deleteCompletedMedia(movieKey, profileId)
                    }
                    if (!isActivePlaybackTarget(profileId, "movie", tmdbId) && !hasPendingForMedia) {
                        dao.deleteWatchProgress(mainKey, profileId)
                        dao.deleteSimklBaseline(profileId, mainKey)
                    }
                }
                SimklLibraryJsonParser.STATUS_WATCHING,
                SimklLibraryJsonParser.STATUS_SEEN -> {
                    if (existing?.status == SimklLibraryJsonParser.STATUS_COMPLETED && !hasPendingForMedia) {
                        dao.deleteCompletedMedia(movieKey, profileId)
                    }
                }
            }
            return
        }

        // Serie TV e Anime:
        // 1) Propaga episodi visti in completed_media con timestamp proprio dell'episodio e ripulisci episode_progress superati
        val existingCompleted = dao.getCompletedEpisodesForSeries(tmdbId, profileId)
            .map { it.seasonNumber to it.episodeNumber }
            .toSet()

        val newCompletedEntities = episodeMerge.watchedPairs
            .filter { it !in existingCompleted }
            .map { (seasonNum, epNum) ->
                CompletedMediaEntity(
                    profileId = profileId,
                    contentKey = PlayerSegmentKeys.completedMediaKey(tmdbId, "tv", seasonNum, epNum),
                    tmdbId = tmdbId,
                    mediaType = "tv",
                    seasonNumber = seasonNum,
                    episodeNumber = epNum,
                    completedAtMs = episodeMerge.watchedAtMsByPair[seasonNum to epNum] ?: completedTimestamp
                )
            }
        if (newCompletedEntities.isNotEmpty()) {
            dao.upsertCompletedMediaList(newCompletedEntities)
        }

        // 2) Propaga episodi segnati come NON visti (unwatch) su Simkl rimuovendoli da completed_media,
        // episode_progress e simkl_playback_baseline, così che lo stato locale sia totalmente allineato.
        val authoritativeWatchedReset = item.hasSeasonsField ||
            (item.hasExplicitWatchedCount && item.watchedEpisodesCount == 0) ||
            (item.status == SimklLibraryJsonParser.STATUS_SEEN && item.watchedEpisodesCount == 0)
        val completedToDelete = LinkedHashSet<Pair<Int, Int>>(episodeMerge.unwatchedPairs)
        if (authoritativeWatchedReset) {
            for (pair in existingCompleted) {
                if (pair !in episodeMerge.watchedPairs && pair !in episodeMerge.pendingAddPairs) {
                    completedToDelete.add(pair)
                }
            }
        }
        for ((seasonNum, epNum) in completedToDelete) {
            if (!isActivePlaybackTarget(profileId, "tv", tmdbId, seasonNum, epNum)) {
                val completedKey = PlayerSegmentKeys.completedMediaKey(tmdbId, "tv", seasonNum, epNum)
                val epKey = "tv_${tmdbId}_s${seasonNum}_e${epNum}"
                dao.deleteCompletedMedia(completedKey, profileId)
                dao.deleteEpisodeProgress(epKey, profileId)
                dao.deleteSimklBaseline(profileId, epKey)
            }
        }

        val currentCompletedAfterUpdate = dao.getCompletedEpisodesForSeries(tmdbId, profileId)
            .map { it.seasonNumber to it.episodeNumber }
            .toSet()
        val allWatchedPairs = (episodeMerge.watchedPairs + currentCompletedAfterUpdate) - completedToDelete

        for ((seasonNum, epNum) in allWatchedPairs) {
            if (!isActivePlaybackTarget(profileId, "tv", tmdbId, seasonNum, epNum)) {
                val epKey = "tv_${tmdbId}_s${seasonNum}_e${epNum}"
                dao.deleteEpisodeProgress(epKey, profileId)
                dao.deleteSimklBaseline(profileId, epKey)
            }
        }

        // 3) Aggiorna "Continua a guardare" (watch_progress) in modo autorevole da Simkl.
        // Mai interferire con il player se l'utente sta guardando attivamente questa serie: differisci a fine riproduzione.
        if (isActivePlaybackSeries(profileId, tmdbId)) {
            markDeferredLibrarySeries(profileId, tmdbId)
            return
        }

        val mainKey = "tv_$tmdbId"
        val existingMain = dao.getWatchProgress(mainKey, profileId)

        if (item.status == SimklLibraryJsonParser.STATUS_PLAN_TO_WATCH ||
            item.status == SimklLibraryJsonParser.STATUS_COMPLETED ||
            item.status == SimklLibraryJsonParser.STATUS_HOLD ||
            item.status == SimklLibraryJsonParser.STATUS_DROPPED
        ) {
            // Stati esclusi da Continua a guardare: rimozione incondizionata (salvo scritture pendenti non conflittuali)
            if (!hasPendingForMedia) {
                dao.deleteWatchProgress(mainKey, profileId)
                dao.deleteSimklBaselinesForMedia(profileId, tmdbId, "tv")
            }
            return
        }

        if (item.status == SimklLibraryJsonParser.STATUS_WATCHING ||
            item.status == SimklLibraryJsonParser.STATUS_SEEN
        ) {
            val nextTarget = resolveAuthoritativeNextSeriesEpisode(
                profileId = profileId,
                tmdbId = tmdbId,
                title = item.title,
                year = item.year,
                mergedSeasons = episodeMerge.mergedSeasons,
                allWatchedPairs = allWatchedPairs,
                unwatchedPairs = completedToDelete,
                rawNextSeason = item.nextToWatchSeason,
                rawNextEpisode = item.nextToWatchEpisode,
                rawNextCode = item.nextToWatch,
                lastWatchedCode = item.lastWatchedEpisodeCode,
                existingMain = existingMain
            )

            val existingMainPair = if (existingMain?.seasonNumber != null && existingMain.episodeNumber != null) {
                existingMain.seasonNumber to existingMain.episodeNumber
            } else {
                null
            }
            val existingMainIsWatched = existingMainPair != null && existingMainPair in allWatchedPairs

            if (nextTarget == null) {
                // Tutti gli episodi noti sono già stati visti (stagione/serie conclusa senza episodio successivo):
                // rimuove la scheda se punta a un episodio ormai completato (senza mai lasciare un episodio già visto).
                if (existingMain != null && (existingMainIsWatched || !hasPendingForMedia)) {
                    if (existingMainPair == null || existingMainPair in allWatchedPairs) {
                        dao.deleteWatchProgress(mainKey, profileId)
                    }
                }
                return
            }

            val (nextSeason, nextEp) = nextTarget
            val seriesPending = pendingEntries.filter { it.tmdbId == tmdbId && it.mediaType == "tv" }
            val hasPendingForExistingMainEp = existingMainPair != null && seriesPending.any {
                it.action in setOf("start", "pause", "stop") &&
                    it.seasonNumber == existingMainPair.first &&
                    it.episodeNumber == existingMainPair.second
            }
            val hasPendingPlaybackOrHistory = seriesPending.any {
                it.action in setOf("start", "pause", "stop", "history_add", "history_remove")
            }

            val existingMainWasUnwatched = existingMainPair != null && existingMainPair in completedToDelete
            val existingMainWasUnwatchedAndMovedBack = existingMainPair != null &&
                existingMainPair != nextTarget &&
                completedToDelete.isNotEmpty()
            val existingPointsToDifferentEp = existingMainPair != nextTarget

            val shouldUpdateMain = when {
                existingMain == null -> true
                existingMainIsWatched -> true
                !hasPendingForExistingMainEp && existingMainWasUnwatched && existingMain.positionMs > 0L -> true
                !hasPendingForExistingMainEp && existingMainWasUnwatchedAndMovedBack -> true
                !hasPendingPlaybackOrHistory && existingPointsToDifferentEp -> true
                else -> false
            }

            if (shouldUpdateMain) {
                if (existingMainIsWatched && existingMainPair != null) {
                    val staleEpKey = "tv_${tmdbId}_s${existingMainPair.first}_e${existingMainPair.second}"
                    dao.deleteEpisodeProgress(staleEpKey, profileId)
                    dao.deleteSimklBaseline(profileId, staleEpKey)
                }
                upsertSeriesContinueWatchingTarget(
                    profileId = profileId,
                    tmdbId = tmdbId,
                    title = item.title,
                    year = item.year,
                    nextSeason = nextSeason,
                    nextEp = nextEp,
                    existingMain = existingMain,
                    resetPosition = (nextSeason to nextEp) in completedToDelete,
                    updatedAtFloorMs = maxOf(
                        completedTimestamp,
                        if (existingMain != null && (existingMainIsWatched || existingMainWasUnwatchedAndMovedBack || existingMainWasUnwatched || existingPointsToDifferentEp)) {
                            existingMain.updatedAt + 1L
                        } else {
                            0L
                        }
                    )
                )
            }
        }
    }

    internal suspend fun reconcileTvContinueWatchingInvariant(
        profileId: String,
        candidateSeriesIds: Set<Int>? = null
    ) {
        val tvWatchEntries = dao.getWatchProgressListForProfile(profileId)
            .filter { it.mediaType == "tv" && (candidateSeriesIds == null || it.tmdbId in candidateSeriesIds) }
        if (tvWatchEntries.isEmpty()) return

        val pendingEntries = dao.getSimklPendingScrobbles(profileId)
        for (entry in tvWatchEntries) {
            if (isActivePlaybackSeries(profileId, entry.tmdbId)) {
                markDeferredLibrarySeries(profileId, entry.tmdbId)
                continue
            }
            val libItem = dao.getSimklLibraryByTmdb(profileId, entry.tmdbId, "tv").firstOrNull()
            if (libItem != null && libItem.status in excludedContinueWatchingStatuses) {
                val hasPending = pendingEntries.any {
                    it.tmdbId == entry.tmdbId && it.mediaType == "tv" &&
                        it.action in setOf("start", "pause", "stop")
                }
                if (!hasPending) {
                    dao.deleteWatchProgress(entry.id, profileId)
                    dao.deleteSimklBaselinesForMedia(profileId, entry.tmdbId, "tv")
                }
                continue
            }

            val completedPairs = dao.getCompletedEpisodesForSeries(entry.tmdbId, profileId)
                .map { it.seasonNumber to it.episodeNumber }
                .toSet()
            val epStates = dao.getSimklEpisodeStatesByTmdb(profileId, entry.tmdbId)
            val epWatchedPairs = epStates.filter { it.watched }.map { it.seasonNumber to it.episodeNumber }.toSet()
            val allWatchedPairs = completedPairs + epWatchedPairs

            val curPair = if (entry.seasonNumber != null && entry.episodeNumber != null) {
                entry.seasonNumber to entry.episodeNumber
            } else {
                null
            }
            val curEpIsWatched = curPair != null && curPair in allWatchedPairs
            val isExplicitCandidate = candidateSeriesIds != null && entry.tmdbId in candidateSeriesIds
            if (!curEpIsWatched && !isExplicitCandidate) {
                continue
            }

            // Ripulisci qualsiasi progresso episodico obsoleto relativo a episodi ormai completati
            for ((s, e) in allWatchedPairs) {
                if (!isActivePlaybackTarget(profileId, "tv", entry.tmdbId, s, e)) {
                    val staleEpKey = "tv_${entry.tmdbId}_s${s}_e${e}"
                    dao.deleteEpisodeProgress(staleEpKey, profileId)
                    dao.deleteSimklBaseline(profileId, staleEpKey)
                }
            }

            val mergedSeasons = if (epStates.isNotEmpty()) {
                epStates.groupBy { it.seasonNumber }
                    .toSortedMap()
                    .map { (seasonNum, eps) ->
                        SimklParsedSeason(
                            seasonNumber = seasonNum,
                            episodes = eps.map { st ->
                                SimklParsedEpisode(
                                    seasonNumber = st.seasonNumber,
                                    episodeNumber = st.episodeNumber,
                                    watched = st.watched || (st.seasonNumber to st.episodeNumber) in allWatchedPairs,
                                    watchedAt = st.watchedAt,
                                    simklEpisodeId = st.simklEpisodeId
                                )
                            }
                        )
                    }
            } else {
                SimklLibraryJsonParser.deserializeSeasonsJson(libItem?.seasonsJson)
            }

            val parsedLibNext = parseEpisodeCode(libItem?.nextToWatch)
            val nextTarget = resolveAuthoritativeNextSeriesEpisode(
                profileId = profileId,
                tmdbId = entry.tmdbId,
                title = libItem?.title ?: entry.title,
                year = libItem?.year ?: entry.releaseYear.take(4).toIntOrNull(),
                mergedSeasons = mergedSeasons,
                allWatchedPairs = allWatchedPairs,
                unwatchedPairs = emptySet(),
                rawNextSeason = parsedLibNext?.first,
                rawNextEpisode = parsedLibNext?.second,
                rawNextCode = libItem?.nextToWatch,
                lastWatchedCode = libItem?.lastWatchedEpisodeCode,
                existingMain = entry,
                anchorHint = curPair?.takeIf { curEpIsWatched }
            )

            if (nextTarget == null) {
                if (curPair == null || curEpIsWatched) {
                    dao.deleteWatchProgress(entry.id, profileId)
                }
                continue
            }

            if (nextTarget != curPair || curEpIsWatched) {
                upsertSeriesContinueWatchingTarget(
                    profileId = profileId,
                    tmdbId = entry.tmdbId,
                    title = libItem?.title ?: entry.title,
                    year = libItem?.year ?: entry.releaseYear.take(4).toIntOrNull(),
                    nextSeason = nextTarget.first,
                    nextEp = nextTarget.second,
                    existingMain = entry,
                    resetPosition = false,
                    updatedAtFloorMs = maxOf(entry.updatedAt + 1L, libItem?.lastWatchedAtMs ?: 0L, clockMs())
                )
            }
        }
    }

    private suspend fun resolveAuthoritativeNextSeriesEpisode(
        profileId: String,
        tmdbId: Int,
        title: String,
        year: Int?,
        mergedSeasons: List<SimklParsedSeason>,
        allWatchedPairs: Set<Pair<Int, Int>>,
        unwatchedPairs: Set<Pair<Int, Int>>,
        rawNextSeason: Int?,
        rawNextEpisode: Int?,
        rawNextCode: String?,
        lastWatchedCode: String?,
        existingMain: WatchProgressEntity?,
        anchorHint: Pair<Int, Int>? = null
    ): Pair<Int, Int>? {
        val checkedWatchedPairs = HashSet(allWatchedPairs)
        val maxWatchedRegularPair = checkedWatchedPairs
            .filter { it.first > 0 }
            .maxWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })

        val explicitNextTarget = if (rawNextSeason != null && rawNextEpisode != null) {
            rawNextSeason to rawNextEpisode
        } else {
            parseEpisodeCode(rawNextCode)
        }?.takeIf { target ->
            target !in checkedWatchedPairs &&
                (unwatchedPairs.isNotEmpty() ||
                    maxWatchedRegularPair == null ||
                    target.first == 0 ||
                    compareEpisodePairs(target, maxWatchedRegularPair) > 0)
        }

        val seasonsNextTarget = findNextUnwatchedEpisodeFromSeasons(mergedSeasons, checkedWatchedPairs)
            ?.takeIf { target ->
                unwatchedPairs.isNotEmpty() ||
                    maxWatchedRegularPair == null ||
                    target.first == 0 ||
                    compareEpisodePairs(target, maxWatchedRegularPair) > 0
            }

        val existingValidNextTarget = existingMain
            ?.takeIf { it.seasonNumber != null && it.episodeNumber != null }
            ?.let { it.seasonNumber!! to it.episodeNumber!! }
            ?.takeIf { target ->
                target !in checkedWatchedPairs &&
                    (maxWatchedRegularPair == null ||
                        target.first == 0 ||
                        compareEpisodePairs(target, maxWatchedRegularPair) > 0)
            }

        var candidate = when {
            unwatchedPairs.isNotEmpty() && seasonsNextTarget != null -> seasonsNextTarget
            else -> explicitNextTarget
                ?: seasonsNextTarget
                ?: inferNextEpisodeAfterWatched(tmdbId, title, year, checkedWatchedPairs, lastWatchedCode, anchorHint)
                ?: existingValidNextTarget
                ?: if (checkedWatchedPairs.isEmpty()) {
                    resolveFirstAvailableEpisodeFromTmdb(
                        tmdbId = tmdbId,
                        title = title,
                        year = year,
                        existingMain = existingMain
                    )
                } else {
                    null
                }
        }

        // Mai accettare un target già completato in completed_media o simkl_episode_state
        var guard = 0
        while (candidate != null && guard < 8) {
            guard++
            val candidateKey = PlayerSegmentKeys.completedMediaKey(tmdbId, "tv", candidate.first, candidate.second)
            if (candidate !in checkedWatchedPairs && dao.getCompletedMedia(candidateKey, profileId) == null) {
                return candidate
            }
            checkedWatchedPairs.add(candidate)
            candidate = findNextUnwatchedEpisodeFromSeasons(mergedSeasons, checkedWatchedPairs)
                ?: inferNextEpisodeAfterWatched(tmdbId, title, year, checkedWatchedPairs, lastWatchedCode, candidate)
        }
        return null
    }

    private suspend fun upsertSeriesContinueWatchingTarget(
        profileId: String,
        tmdbId: Int,
        title: String,
        year: Int?,
        nextSeason: Int,
        nextEp: Int,
        existingMain: WatchProgressEntity?,
        resetPosition: Boolean,
        updatedAtFloorMs: Long
    ) {
        val mainKey = "tv_$tmdbId"
        val baseMedia = MediaItem(
            id = tmdbId,
            mediaType = "tv",
            title = title,
            overview = existingMain?.overview.orEmpty(),
            posterPath = existingMain?.posterPath,
            backdropPath = existingMain?.backdropPath,
            voteAverage = existingMain?.voteAverage ?: 0.0,
            releaseYear = year?.toString().orEmpty()
        )
        val enrichedItem = runCatching {
            repository.getCachedMediaDetails(baseMedia)?.value?.item
                ?: repository.fetchMediaDetails(baseMedia).getOrNull()?.item
        }.getOrNull() ?: baseMedia
        val epDto = runCatching {
            val eps = repository.getCachedSeasonEpisodes(tmdbId, nextSeason)?.value
                ?: repository.fetchSeasonEpisodes(tmdbId, nextSeason).getOrNull()
            eps?.firstOrNull { it.episodeNumber == nextEp }
        }.getOrNull()
        val epKey = "tv_${tmdbId}_s${nextSeason}_e${nextEp}"
        val savedNextEpProgress = dao.getEpisodeProgress(epKey, profileId)
            ?.takeIf { it.seasonNumber == nextSeason && it.episodeNumber == nextEp }
        val epDurationMs = savedNextEpProgress?.durationMs?.takeIf { it > 0L }
            ?: ((epDto?.runtime?.takeIf { it in 1..600 }?.toLong() ?: 0L) * 60_000L)
        val isUpcomingNextEp = com.example.ui.UpcomingEpisodeBadgeFormatter.isUpcomingAirDate(epDto?.airDate)
        val preservedPositionMs = if (isUpcomingNextEp || resetPosition) {
            0L
        } else {
            savedNextEpProgress?.positionMs?.coerceAtLeast(0L) ?: 0L
        }
        dao.upsertWatchProgress(
            WatchProgressEntity(
                id = mainKey,
                tmdbId = tmdbId,
                mediaType = "tv",
                title = enrichedItem.title.ifBlank { title },
                overview = enrichedItem.overview,
                posterPath = enrichedItem.posterPath ?: existingMain?.posterPath,
                backdropPath = enrichedItem.backdropPath ?: existingMain?.backdropPath,
                voteAverage = if (enrichedItem.voteAverage > 0.0) enrichedItem.voteAverage else (existingMain?.voteAverage ?: 0.0),
                releaseYear = enrichedItem.releaseYear.ifBlank { year?.toString().orEmpty() },
                seasonNumber = nextSeason,
                episodeNumber = nextEp,
                episodeTitle = epDto?.name?.takeIf { it.isNotBlank() },
                positionMs = preservedPositionMs,
                durationMs = epDurationMs,
                updatedAt = updatedAtFloorMs,
                profileId = profileId
            )
        )
    }

    private suspend fun inferNextEpisodeAfterWatched(
        tmdbId: Int,
        title: String,
        year: Int?,
        watchedPairs: Set<Pair<Int, Int>>,
        lastWatchedCode: String?,
        anchorHint: Pair<Int, Int>? = null
    ): Pair<Int, Int>? {
        val regularWatched = watchedPairs.filter { it.first > 0 }
        val candidateAnchors = buildList {
            addAll(regularWatched)
            parseEpisodeCode(lastWatchedCode)?.takeIf { it.first > 0 }?.let { add(it) }
            anchorHint?.takeIf { it.first > 0 }?.let { add(it) }
        }
        val anchor = candidateAnchors.maxWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            ?: parseEpisodeCode(lastWatchedCode)
            ?: anchorHint
            ?: return null
        val (anchorSeason, anchorEp) = anchor
        val seasonEps = runCatching {
            repository.getCachedSeasonEpisodes(tmdbId, anchorSeason)?.value
                ?: repository.fetchSeasonEpisodes(tmdbId, anchorSeason).getOrNull()
        }.getOrNull().orEmpty()
        if (seasonEps.isNotEmpty()) {
            val nextInSeason = seasonEps
                .map { it.episodeNumber }
                .filter { it > anchorEp && (anchorSeason to it) !in watchedPairs }
                .minOrNull()
            if (nextInSeason != null) {
                return anchorSeason to nextInSeason
            }
            val probeMedia = MediaItem(
                id = tmdbId,
                mediaType = "tv",
                title = title,
                overview = "",
                posterPath = null,
                backdropPath = null,
                voteAverage = 0.0,
                releaseYear = year?.toString().orEmpty()
            )
            val details = runCatching {
                repository.getCachedMediaDetails(probeMedia)?.value
                    ?: repository.fetchMediaDetails(probeMedia).getOrNull()
            }.getOrNull()
            val candidateNextSeasons = details?.seasons.orEmpty()
                .filter { it.seasonNumber > anchorSeason && it.episodeCount > 0 }
                .map { it.seasonNumber }
                .distinct()
                .sorted()
                .ifEmpty { listOf(anchorSeason + 1) }

            for (nextSeasonNum in candidateNextSeasons) {
                val nextSeasonEps = runCatching {
                    repository.getCachedSeasonEpisodes(tmdbId, nextSeasonNum)?.value
                        ?: repository.fetchSeasonEpisodes(tmdbId, nextSeasonNum).getOrNull()
                }.getOrNull().orEmpty()
                val firstNextSeason = nextSeasonEps
                    .map { it.episodeNumber }
                    .filter { it > 0 && (nextSeasonNum to it) !in watchedPairs }
                    .minOrNull()
                if (firstNextSeason != null) {
                    return nextSeasonNum to firstNextSeason
                }
            }
            return null
        }
        return null
    }

    private fun hasLibraryItemChanged(
        oldEntity: SimklLibraryItemEntity,
        newEntity: SimklLibraryItemEntity
    ): Boolean {
        return oldEntity.simklId != newEntity.simklId ||
            oldEntity.tmdbId != newEntity.tmdbId ||
            oldEntity.imdbId != newEntity.imdbId ||
            oldEntity.tvdbId != newEntity.tvdbId ||
            oldEntity.title != newEntity.title ||
            oldEntity.year != newEntity.year ||
            oldEntity.status != newEntity.status ||
            oldEntity.watchedEpisodesCount != newEntity.watchedEpisodesCount ||
            oldEntity.totalEpisodesCount != newEntity.totalEpisodesCount ||
            oldEntity.lastWatchedAt != newEntity.lastWatchedAt ||
            oldEntity.nextToWatch != newEntity.nextToWatch ||
            oldEntity.lastWatchedEpisodeCode != newEntity.lastWatchedEpisodeCode ||
            oldEntity.seasonsJson != newEntity.seasonsJson ||
            oldEntity.tmdbResolved != newEntity.tmdbResolved ||
            oldEntity.posterPath != newEntity.posterPath
    }

    /**
     * Per serie in stato `seen` o `watching` su Simkl con 0 episodi visti, risolve da TMDB il primo
     * episodio reale disponibile (includendo anche eventuali episodi futuri non ancora trasmessi).
     * Non inventa mai episodi o date fittizie se TMDB non restituisce dati reali.
     */
    private suspend fun resolveFirstAvailableEpisodeFromTmdb(
        tmdbId: Int,
        title: String,
        year: Int?,
        existingMain: WatchProgressEntity?
    ): Pair<Int, Int>? {
        val probeMedia = MediaItem(
            id = tmdbId,
            mediaType = "tv",
            title = title,
            overview = existingMain?.overview.orEmpty(),
            posterPath = existingMain?.posterPath,
            backdropPath = existingMain?.backdropPath,
            voteAverage = existingMain?.voteAverage ?: 0.0,
            releaseYear = year?.toString().orEmpty()
        )
        val details = runCatching {
            repository.getCachedMediaDetails(probeMedia)?.value
                ?: repository.fetchMediaDetails(probeMedia).getOrNull()
        }.getOrNull()

        val regularSeasons = details?.seasons.orEmpty()
            .filter { it.seasonNumber > 0 && (it.episodeCount ?: 1) > 0 }
            .map { it.seasonNumber }
            .distinct()
            .sorted()
        val specialSeasons = details?.seasons.orEmpty()
            .filter { it.seasonNumber == 0 && (it.episodeCount ?: 1) > 0 }
            .map { it.seasonNumber }
            .distinct()
        val candidateSeasons = when {
            regularSeasons.isNotEmpty() -> regularSeasons + specialSeasons
            specialSeasons.isNotEmpty() -> specialSeasons
            else -> listOf(1)
        }

        for (seasonNum in candidateSeasons) {
            val eps = runCatching {
                repository.getCachedSeasonEpisodes(tmdbId, seasonNum)?.value
                    ?: repository.fetchSeasonEpisodes(tmdbId, seasonNum).getOrNull()
            }.getOrNull().orEmpty()
            val firstRealEp = eps
                .filter { if (seasonNum == 0) it.episodeNumber >= 0 else it.episodeNumber > 0 }
                .minByOrNull { it.episodeNumber }
            if (firstRealEp != null) {
                return seasonNum to firstRealEp.episodeNumber
            }
        }
        return null
    }

    private fun recomputeLastWatchedEpisodeCode(
        item: SimklLibraryParsedItem,
        episodeMerge: EpisodeMergeResult
    ): String? {
        val authoritativeReset = item.hasSeasonsField ||
            episodeMerge.unwatchedPairs.isNotEmpty() ||
            (item.hasExplicitWatchedCount && item.watchedEpisodesCount == 0) ||
            (item.status == SimklLibraryJsonParser.STATUS_SEEN && item.watchedEpisodesCount == 0)
        if (!authoritativeReset) {
            return item.lastWatchedEpisodeCode
        }
        if (episodeMerge.watchedPairs.isEmpty()) {
            return null
        }
        val rawCode = item.lastWatchedEpisodeCode
        val parsedRaw = parseEpisodeCode(rawCode)
        if (parsedRaw != null && parsedRaw in episodeMerge.watchedPairs) {
            return rawCode
        }
        val bestWatched = episodeMerge.watchedPairs
            .filter { it.first > 0 }
            .maxWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            ?: episodeMerge.watchedPairs
                .maxWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            ?: return null
        return formatEpisodeCode(bestWatched.first, bestWatched.second)
    }

    private fun recomputeNextToWatchCode(
        item: SimklLibraryParsedItem,
        episodeMerge: EpisodeMergeResult
    ): String? {
        val authoritativeReset = item.hasSeasonsField ||
            episodeMerge.unwatchedPairs.isNotEmpty() ||
            (item.hasExplicitWatchedCount && item.watchedEpisodesCount == 0) ||
            (item.status == SimklLibraryJsonParser.STATUS_SEEN && item.watchedEpisodesCount == 0)
        val explicitNext = if (item.nextToWatchSeason != null && item.nextToWatchEpisode != null) {
            item.nextToWatchSeason to item.nextToWatchEpisode
        } else {
            parseEpisodeCode(item.nextToWatch)
        }
        if (!authoritativeReset) {
            return item.nextToWatch
        }
        val computed = findNextUnwatchedEpisodeFromSeasons(episodeMerge.mergedSeasons)
        if (episodeMerge.unwatchedPairs.isNotEmpty() && computed != null) {
            return formatEpisodeCode(computed.first, computed.second)
        }
        if (explicitNext != null && explicitNext !in episodeMerge.watchedPairs) {
            return item.nextToWatch ?: formatEpisodeCode(explicitNext.first, explicitNext.second)
        }
        return computed?.let { formatEpisodeCode(it.first, it.second) }
    }

    private fun formatEpisodeCode(seasonNumber: Int, episodeNumber: Int): String {
        return "S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"
    }

    private fun mergeFullAndDeltaCategoryItems(
        fullItems: List<SimklLibraryParsedItem>,
        deltaItems: List<SimklLibraryParsedItem>
    ): List<SimklLibraryParsedItem> {
        if (deltaItems.isEmpty()) return deduplicateParsedItems(fullItems)
        if (fullItems.isEmpty()) return deduplicateParsedItems(deltaItems)
        val merged = LinkedHashMap<String, SimklLibraryParsedItem>(fullItems.size + deltaItems.size)
        for (item in fullItems) {
            merged[item.stableKey] = item
        }
        for (delta in deltaItems) {
            merged[delta.stableKey] = delta
        }
        return merged.values.toList()
    }

    private fun deduplicateParsedItems(
        items: List<SimklLibraryParsedItem>
    ): List<SimklLibraryParsedItem> {
        if (items.size <= 1) return items
        val byKey = LinkedHashMap<String, SimklLibraryParsedItem>(items.size)
        for (item in items) {
            val prev = byKey[item.stableKey]
            byKey[item.stableKey] = if (prev == null || item.seasons.size >= prev.seasons.size) {
                item
            } else {
                prev
            }
        }
        return byKey.values.toList()
    }

    internal fun parseEpisodeCode(rawCode: String?): Pair<Int, Int>? {
        val clean = rawCode?.trim().orEmpty()
        if (clean.isEmpty()) return null
        val match = Regex("(?i)S(\\d+)\\s*E(\\d+)|(\\d+)\\s*x\\s*(\\d+)").find(clean) ?: return null
        val sStr = match.groupValues[1].ifEmpty { match.groupValues[3] }
        val eStr = match.groupValues[2].ifEmpty { match.groupValues[4] }
        val season = sStr.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val episode = eStr.toIntOrNull()?.takeIf { if (season == 0) it >= 0 else it > 0 } ?: return null
        return season to episode
    }

    private fun compareEpisodePairs(a: Pair<Int, Int>, b: Pair<Int, Int>): Int {
        if (a.first != b.first) return a.first.compareTo(b.first)
        return a.second.compareTo(b.second)
    }

    private fun findNextUnwatchedEpisodeFromSeasons(
        seasons: List<SimklParsedSeason>,
        excludedPairs: Set<Pair<Int, Int>> = emptySet()
    ): Pair<Int, Int>? {
        // Privilegia le stagioni regolari (> 0) prima degli speciali (stagione 0)
        val ordered = seasons.sortedWith(compareBy<SimklParsedSeason> { it.seasonNumber == 0 }.thenBy { it.seasonNumber })
        for (season in ordered) {
            val firstUnwatched = season.episodes
                .sortedBy { it.episodeNumber }
                .firstOrNull { !it.watched && (season.seasonNumber to it.episodeNumber) !in excludedPairs }
            if (firstUnwatched != null) {
                return season.seasonNumber to firstUnwatched.episodeNumber
            }
        }
        return null
    }

    private fun formatIsoUtcNow(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(java.util.Date(epochMs))
    }

    internal fun parseIsoUtcMillis(rawIso: String?): Long {
        val clean = rawIso?.trim()?.replace(' ', 'T').orEmpty()
        if (clean.isEmpty()) return 0L
        // Normalizza eventuali frazioni di secondo (1..9 cifre) in millisecondi esatti a 3 cifre
        val withNormalizedFraction = Regex("\\.(\\d+)").replace(clean) { match ->
            val digits = match.groupValues[1]
            val millis = when {
                digits.length >= 3 -> digits.substring(0, 3)
                else -> digits.padEnd(3, '0')
            }
            ".$millis"
        }
        val normalized = when {
            withNormalizedFraction.endsWith("Z", ignoreCase = true) ->
                withNormalizedFraction.dropLast(1) + "+0000"
            else ->
                withNormalizedFraction.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        }
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in patterns) {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            val parsed = runCatching { fmt.parse(normalized)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }

    companion object {
        const val DEFAULT_PERIODIC_SYNC_INTERVAL_MS: Long = 5L * 60L * 1_000L
        const val DEFAULT_FULL_RECONCILE_INTERVAL_MS: Long = 24L * 60L * 60L * 1_000L
    }
}
