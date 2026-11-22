package com.example.data.remote.simkl

import com.example.data.local.CompletedMediaEntity
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.INITIAL_AVATAR_ID
import com.example.data.local.MAX_USER_PROFILES
import com.example.data.local.PlayerSegmentKeys
import com.example.data.local.ProfileCatalogDefaults
import com.example.data.local.WatchProgressEntity
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
    PROFILE_SWITCH,
    CONTINUE_WATCHING_OPEN,
    PRE_PLAYBACK,
    POST_SCROBBLE,
    POST_AUTH,
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
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val dao = repository.localDao
    private val errorAdapter = repository.moshi.adapter(SimklOAuthErrorResponse::class.java)

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

    private val _qrDialogState = MutableStateFlow<SimklQrDialogState?>(null)
    val globalQrDialogState: StateFlow<SimklQrDialogState?> = _qrDialogState.asStateFlow()

    private val _resolvedActiveProfileRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val resolvedActiveProfileRequests: SharedFlow<String> = _resolvedActiveProfileRequests.asSharedFlow()

    @Volatile
    private var pendingAuthorizedAccount: PendingAuthorizedAccount? = null

    private val profileTokenMutexes = ConcurrentHashMap<String, Mutex>()
    private val profileSyncMutexes = ConcurrentHashMap<String, Mutex>()
    private val profileWriteMutexes = ConcurrentHashMap<String, Mutex>()
    private val activeAuthJobs = ConcurrentHashMap<String, Job>()
    private val activeSyncJobs = ConcurrentHashMap<String, Job>()
    private val lastSyncAttemptAtMs = ConcurrentHashMap<String, Long>()
    private val lastPostAtByProfile = ConcurrentHashMap<String, Long>()

    private fun tokenMutexFor(profileId: String): Mutex =
        profileTokenMutexes.getOrPut(profileId) { Mutex() }

    private fun syncMutexFor(profileId: String): Mutex =
        profileSyncMutexes.getOrPut(profileId) { Mutex() }

    private fun writeMutexFor(profileId: String): Mutex =
        profileWriteMutexes.getOrPut(profileId) { Mutex() }

    fun observeProfileUiState(profileId: String): Flow<SimklProfileUiState> {
        return combine(
            dao.observeSimklProfileMeta(profileId),
            dao.observeSimklPendingCount(profileId),
            _qrDialogState
        ) { meta, pendingCount, qrState ->
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
                qrDialogState = matchingQr
            )
        }
    }

    fun onActiveProfileChanged(previousProfileId: String?, newProfileId: String) {
        if (!previousProfileId.isNullOrBlank() && previousProfileId != newProfileId) {
            activeAuthJobs.remove(previousProfileId)?.cancel()
            activeSyncJobs.remove(previousProfileId)?.cancel()
            if (_qrDialogState.value?.profileId == previousProfileId) {
                _qrDialogState.value = null
            }
        }
        syncIfNeeded(newProfileId, SimklSyncTrigger.PROFILE_SWITCH)
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
        dao.deleteSimklPendingForProfile(profileId)
        dao.deleteSimklBaselinesForProfile(profileId)
        dao.deleteSimklProfileMeta(profileId)
    }

    suspend fun onProfileDeleted(profileId: String) = withContext(Dispatchers.IO) {
        activeAuthJobs.remove(profileId)?.cancel()
        activeSyncJobs.remove(profileId)?.cancel()
        if (_qrDialogState.value?.profileId == profileId) {
            _qrDialogState.value = null
        }
        tokenStore.clearTokens(profileId)
    }

    fun syncIfNeeded(profileId: String, trigger: SimklSyncTrigger) {
        val now = clockMs()
        val lastAttempt = lastSyncAttemptAtMs[profileId] ?: 0L
        val shouldSkipCooldown = trigger == SimklSyncTrigger.MANUAL ||
            trigger == SimklSyncTrigger.POST_AUTH ||
            trigger == SimklSyncTrigger.PRE_PLAYBACK
        if (!shouldSkipCooldown && (now - lastAttempt) < syncCooldownMs) {
            return
        }
        launchProfileSync(profileId, trigger)
    }

    suspend fun syncBeforePlaybackIfConnected(
        profileId: String,
        timeoutMs: Long = 1_200L
    ) = withContext(Dispatchers.IO) {
        if (!configProvider().isClientIdConfigured) return@withContext
        if (!tokenStore.hasTokens(profileId)) return@withContext
        val job = launchProfileSync(profileId, SimklSyncTrigger.PRE_PLAYBACK)
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            job.join()
        }
    }

    fun launchProfileSync(profileId: String, trigger: SimklSyncTrigger = SimklSyncTrigger.MANUAL): Job {
        activeSyncJobs[profileId]?.takeIf { it.isActive }?.let { return it }
        val job = scope.launch {
            syncProfileNow(profileId, trigger)
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

            val accessToken = getValidAccessToken(profileId, forceRefresh = false)
                ?: return@withLock false

            // 1. Direzione A prima di B: svuota la coda offline locale verso Simkl
            val pendingFlushedOk = flushPendingQueueInternal(profileId)

            // 2. Direzione B: verifica attività incrementali
            val activitiesResp = executeWithAuthRetry(profileId, accessToken) { tok ->
                apiService.getSyncActivities("Bearer $tok")
            } ?: return@withLock false

            if (!activitiesResp.isSuccessful) {
                val msg = "Errore attività Simkl (HTTP ${activitiesResp.code()})"
                updateMetaState(
                    profileId,
                    if (pendingFlushedOk) SimklConnectionState.ERROR else SimklConnectionState.OFFLINE_QUEUE_PENDING,
                    error = msg
                )
                return@withLock false
            }

            val activities = activitiesResp.body()
            val currentMeta = dao.getSimklProfileMeta(profileId)
            val forceFullPull = trigger == SimklSyncTrigger.POST_AUTH ||
                trigger == SimklSyncTrigger.MANUAL ||
                currentMeta?.lastSyncAtMs == null

            val activitiesUnchanged = !forceFullPull &&
                activities != null &&
                currentMeta != null &&
                activities.moviesPlaybackTimestamp == currentMeta.lastMoviesPlaybackTs &&
                activities.showsPlaybackTimestamp == currentMeta.lastShowsPlaybackTs &&
                activities.all == currentMeta.lastActivitiesAll

            if (!activitiesUnchanged) {
                val activeToken = getValidAccessToken(profileId, forceRefresh = false)
                    ?: return@withLock false

                val moviesResp = executeWithAuthRetry(profileId, activeToken) { tok ->
                    apiService.getPlaybackMovies("Bearer $tok")
                }
                val episodesResp = executeWithAuthRetry(profileId, activeToken) { tok ->
                    apiService.getPlaybackEpisodes("Bearer $tok")
                }

                if (moviesResp == null || episodesResp == null ||
                    !moviesResp.isSuccessful || !episodesResp.isSuccessful
                ) {
                    val code = moviesResp?.code() ?: episodesResp?.code() ?: 0
                    updateMetaState(
                        profileId,
                        SimklConnectionState.ERROR,
                        error = if (code > 0) "Sincronizzazione Simkl interrotta (HTTP $code)." else "Errore sincronizzazione Simkl."
                    )
                    return@withLock false
                }

                reconcileRemotePlayback(
                    profileId = profileId,
                    remoteMovies = moviesResp.body().orEmpty(),
                    remoteEpisodes = episodesResp.body().orEmpty()
                )
            }

            // 3. Push eventuali progressi locali modificati prima/durante l'offline non ancora inviati
            pushDirtyLocalProgressInternal(profileId)
            val finalPending = dao.getSimklPendingScrobbles(profileId).size

            val latestMeta = dao.getSimklProfileMeta(profileId) ?: SimklProfileSyncMetaEntity(profileId = profileId)
            dao.upsertSimklProfileMeta(
                latestMeta.copy(
                    connectionState = if (finalPending > 0) {
                        SimklConnectionState.OFFLINE_QUEUE_PENDING.name
                    } else {
                        SimklConnectionState.CONNECTED_SYNCED.name
                    },
                    lastActivitiesAll = activities?.all ?: latestMeta.lastActivitiesAll,
                    lastMoviesPlaybackTs = activities?.moviesPlaybackTimestamp ?: latestMeta.lastMoviesPlaybackTs,
                    lastShowsPlaybackTs = activities?.showsPlaybackTimestamp ?: latestMeta.lastShowsPlaybackTs,
                    lastMoviesWatchedTs = activities?.moviesWatchedTimestamp ?: latestMeta.lastMoviesWatchedTs,
                    lastShowsWatchedTs = activities?.showsWatchedTimestamp ?: latestMeta.lastShowsWatchedTs,
                    lastSyncAtMs = clockMs(),
                    lastErrorMessage = null,
                    updatedAtMs = clockMs()
                )
            )
            true
        }
    }

    fun enqueueScrobbleStart(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long
    ) {
        enqueueScrobbleEvent(
            profileId = profileId,
            item = item,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            action = "start",
            positionMs = positionMs,
            durationMs = durationMs,
            minProgress = SimklConfig.DEFAULT_MIN_PROGRESS_STOP
        )
    }

    fun enqueueScrobblePause(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long
    ) {
        enqueueScrobbleEvent(
            profileId = profileId,
            item = item,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            action = "pause",
            positionMs = positionMs,
            durationMs = durationMs,
            minProgress = SimklConfig.DEFAULT_MIN_PROGRESS_STOP
        )
    }

    fun enqueueScrobbleStop(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long,
        isExplicitCompletion: Boolean = false
    ) {
        enqueueScrobbleEvent(
            profileId = profileId,
            item = item,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            action = "stop",
            positionMs = if (isExplicitCompletion && durationMs > 0L) durationMs else positionMs,
            durationMs = durationMs,
            minProgress = if (isExplicitCompletion) SimklConfig.COMPLETE_MIN_PROGRESS_STOP else SimklConfig.DEFAULT_MIN_PROGRESS_STOP,
            forcedPercent = if (isExplicitCompletion) 100.0 else null
        )
    }

    fun onContinueWatchingRemoved(
        profileId: String,
        tmdbId: Int,
        mediaType: String,
        seasonNumber: Int?,
        episodeNumber: Int?
    ) {
        scope.launch {
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
                        updatedAtMs = clockMs()
                    )
                )
                flushPendingQueueInternal(profileId)
            }
            dao.deleteSimklBaseline(profileId, contentKey)
        }
    }

    private fun enqueueScrobbleEvent(
        profileId: String,
        item: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        action: String,
        positionMs: Long,
        durationMs: Long,
        minProgress: Int,
        forcedPercent: Double? = null
    ) {
        if (!configProvider().isClientIdConfigured) return
        if (item.isSeries && (seasonNumber == null || episodeNumber == null || episodeNumber <= 0)) return
        scope.launch {
            if (!tokenStore.hasTokens(profileId)) return@launch
            val resolvedPct = forcedPercent ?: computeProgressPercent(
                positionMs = positionMs,
                durationMs = durationMs,
                item = item,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber
            )
            if (resolvedPct <= 0.0 && action != "start") return@launch

            val contentKey = buildContentKey(item.mediaType, item.id, seasonNumber, episodeNumber)
            val now = clockMs()
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
                    nextRetryAtMs = 0L
                )
            )
            val flushed = flushPendingQueueInternal(profileId)
            if (!flushed) {
                val meta = dao.getSimklProfileMeta(profileId)
                if (meta != null && meta.connectionState != SimklConnectionState.AUTH_EXPIRED.name) {
                    updateMetaState(profileId, SimklConnectionState.OFFLINE_QUEUE_PENDING)
                }
            }
        }
    }

    suspend fun flushPendingQueueNow(profileId: String): Boolean = withContext(Dispatchers.IO) {
        flushPendingQueueInternal(profileId)
    }

    private suspend fun flushPendingQueueInternal(profileId: String): Boolean {
        val pending = dao.getSimklPendingScrobbles(profileId)
        if (pending.isEmpty()) return true
        val now = clockMs()
        var allSucceeded = true

        for (entry in pending) {
            if (entry.nextRetryAtMs > now) {
                allSucceeded = false
                continue
            }
            val token = getValidAccessToken(profileId, forceRefresh = false)
            if (token == null) {
                allSucceeded = false
                break
            }

            writeMutexFor(profileId).withLock {
                val lastPost = lastPostAtByProfile[profileId] ?: 0L
                val elapsed = clockMs() - lastPost
                if (elapsed in 0 until minPostIntervalMs) {
                    delay(minPostIntervalMs - elapsed)
                }

                val result = try {
                    sendSinglePendingEntry(profileId, token, entry)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    WriteOutcome.RETRY(delayMs = computeBackoffMs(entry.attemptCount))
                } catch (_: Exception) {
                    WriteOutcome.RETRY(delayMs = computeBackoffMs(entry.attemptCount))
                } finally {
                    lastPostAtByProfile[profileId] = clockMs()
                }

                when (result) {
                    is WriteOutcome.SUCCESS -> {
                        dao.deleteSimklPendingScrobble(profileId, entry.contentKey)
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
                    is WriteOutcome.RETRY -> {
                        allSucceeded = false
                        dao.upsertSimklPendingScrobble(
                            entry.copy(
                                attemptCount = entry.attemptCount + 1,
                                nextRetryAtMs = clockMs() + result.delayMs
                            )
                        )
                    }
                    WriteOutcome.AUTH_FAILED -> {
                        allSucceeded = false
                    }
                }
            }
        }
        return allSucceeded
    }

    private sealed interface WriteOutcome {
        data class SUCCESS(val simklId: Long? = null) : WriteOutcome
        data class RETRY(val delayMs: Long) : WriteOutcome
        data object AUTH_FAILED : WriteOutcome
    }

    private suspend fun sendSinglePendingEntry(
        profileId: String,
        initialToken: String,
        entry: SimklPendingScrobbleEntity
    ): WriteOutcome {
        if (entry.action == "delete_playback") {
            val pid = entry.simklPlaybackId ?: return WriteOutcome.SUCCESS()
            val resp = executeWithAuthRetry(profileId, initialToken) { tok ->
                apiService.deletePlaybackItem("Bearer $tok", pid)
            } ?: return WriteOutcome.AUTH_FAILED
            return when {
                resp.isSuccessful || resp.code() == 404 -> WriteOutcome.SUCCESS()
                resp.code() == 429 || resp.code() == 412 || resp.code() >= 500 ->
                    WriteOutcome.RETRY(parseRetryAfterMs(resp.headers()["Retry-After"], entry.attemptCount))
                else -> WriteOutcome.RETRY(computeBackoffMs(entry.attemptCount))
            }
        }

        val yearInt = entry.releaseYear.take(4).toIntOrNull()
        val requestBody = if (entry.mediaType == "tv") {
            SimklScrobbleRequest(
                progress = roundProgress(entry.progressPercent),
                show = SimklShowDto(
                    title = entry.title.takeIf { it.isNotBlank() },
                    year = yearInt,
                    ids = SimklIdsDto(tmdb = entry.tmdbId)
                ),
                episode = SimklEpisodeDto(
                    season = entry.seasonNumber ?: 1,
                    number = entry.episodeNumber ?: 1
                )
            )
        } else {
            SimklScrobbleRequest(
                progress = roundProgress(entry.progressPercent),
                movie = SimklMovieDto(
                    title = entry.title.takeIf { it.isNotBlank() },
                    year = yearInt,
                    ids = SimklIdsDto(tmdb = entry.tmdbId)
                )
            )
        }

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
        } ?: return WriteOutcome.AUTH_FAILED

        return when {
            resp.isSuccessful -> WriteOutcome.SUCCESS(resp.body()?.id)
            // HTTP 409 su scrobble/stop (già visto su Simkl) gestito come successo morbido
            resp.code() == 409 -> WriteOutcome.SUCCESS(null)
            resp.code() == 429 || resp.code() == 412 || resp.code() >= 500 ->
                WriteOutcome.RETRY(parseRetryAfterMs(resp.headers()["Retry-After"], entry.attemptCount))
            resp.code() in 400..499 && resp.code() != 401 -> WriteOutcome.SUCCESS(null)
            else -> WriteOutcome.RETRY(computeBackoffMs(entry.attemptCount))
        }
    }

    private suspend fun reconcileRemotePlayback(
        profileId: String,
        remoteMovies: List<SimklPlaybackItemDto>,
        remoteEpisodes: List<SimklPlaybackItemDto>
    ) {
        val baselines = dao.getSimklBaselinesForProfile(profileId).associateBy { it.contentKey }
        val localWatchMap = dao.getWatchProgressListForProfile(profileId).associateBy { it.id }
        val seenRemoteKeys = HashSet<String>()

        // Film da Simkl
        for (dto in remoteMovies) {
            val movie = dto.movie ?: continue
            val mediaItem = resolveMediaItemForSimkl(
                expectedType = "movie",
                ids = movie.ids,
                fallbackTitle = movie.title.orEmpty(),
                fallbackYear = movie.year?.toString().orEmpty()
            ) ?: continue

            val contentKey = "movie_${mediaItem.id}"
            seenRemoteKeys.add(contentKey)
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

        // Episodi da Simkl: raggruppati per serie TV per garantire un solo WatchProgress per serie
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
            resolvedEpisodes.add(Triple(mediaItem, dto, seasonNum to epNum))
        }

        val latestPerSeries = HashMap<Int, Triple<MediaItem, SimklPlaybackItemDto, Pair<Int, Int>>>()
        for (triple in resolvedEpisodes) {
            val (mediaItem, dto, se) = triple
            val (seasonNum, epNum) = se
            val contentKey = "tv_${mediaItem.id}_s${seasonNum}_e${epNum}"
            seenRemoteKeys.add(contentKey)
            reconcileSingleEpisodeProgress(
                profileId = profileId,
                contentKey = contentKey,
                mediaItem = mediaItem,
                seasonNumber = seasonNum,
                episodeNumber = epNum,
                remoteDto = dto,
                baseline = baselines[contentKey]
            )
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
            reconcileSingleItem(
                profileId = profileId,
                contentKey = contentKey,
                mediaItem = mediaItem,
                seasonNumber = seasonNum,
                episodeNumber = epNum,
                remoteDto = dto,
                baseline = baselines[contentKey],
                localMain = localWatchMap[mainKey]
            )
        }

        // Rimuove schede che erano sincronizzate con Simkl ma ora risultano completate o rimosse da altri dispositivi
        for ((contentKey, baseline) in baselines) {
            if (contentKey in seenRemoteKeys) continue
            if (baseline.localDirty) continue
            if (baseline.simklPlaybackId == null && baseline.lastSyncedProgressPercent <= 0.0) continue

            val mainId = "${baseline.mediaType}_${baseline.tmdbId}"
            val localMain = localWatchMap[mainId]
            val matchesLocalMain = localMain != null &&
                (baseline.mediaType == "movie" ||
                    (localMain.seasonNumber == baseline.seasonNumber && localMain.episodeNumber == baseline.episodeNumber))
            if (matchesLocalMain &&
                localMain!!.updatedAt <= maxOf(baseline.updatedAtMs, baseline.lastLocalUpdatedAtMs)
            ) {
                dao.deleteWatchProgress(mainId, profileId)
            }
            if (baseline.mediaType == "tv") {
                dao.deleteEpisodeProgress(contentKey, profileId)
            }
            dao.deleteSimklBaseline(profileId, contentKey)
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
    ) {
        val remotePct = remoteDto.progress.coerceIn(0.0, 100.0)
        val remotePausedMs = parseIsoUtcMillis(remoteDto.pausedAt)
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
            return
        }

        if (remoteWins && remotePct > 0.0) {
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
        }
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
    ) {
        val remotePct = remoteDto.progress.coerceIn(0.0, 100.0)
        val remotePausedMs = parseIsoUtcMillis(remoteDto.pausedAt)
        val sameMainTarget = localMain != null &&
            (mediaItem.mediaType == "movie" ||
                (localMain.seasonNumber == seasonNumber && localMain.episodeNumber == episodeNumber))
        val isLocalCompleted = dao.getCompletedMedia(
            contentKey = PlayerSegmentKeys.completedMediaKey(mediaItem.id, mediaItem.mediaType, seasonNumber, episodeNumber),
            profileId = profileId
        ) != null

        if (isLocalCompleted && baseline?.localDirty == true && remotePausedMs <= baseline.lastLocalUpdatedAtMs + 5_000L) {
            return
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

        // Per serie TV, se localMain punta a un episodio diverso, confronta timestamp con localMain.updatedAt
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
            return
        }

        if (remoteWins && remotePct > 0.0) {
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
        } else {
            // Locale più recente / modificato offline: conserva il valore locale e segna dirty per il push
            dao.upsertSimklBaseline(
                (baseline ?: SimklPlaybackBaselineEntity(
                    profileId = profileId,
                    contentKey = contentKey,
                    tmdbId = mediaItem.id,
                    mediaType = mediaItem.mediaType,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber
                )).copy(
                    simklPlaybackId = remoteDto.id ?: baseline?.simklPlaybackId,
                    lastSyncedProgressPercent = remotePct,
                    lastRemotePausedAtMs = remotePausedMs,
                    lastLocalUpdatedAtMs = effectiveLocalUpdatedAt,
                    localDirty = true,
                    updatedAtMs = clockMs()
                )
            )
        }
    }

    internal fun shouldRemoteWin(
        remotePct: Double,
        remotePausedAtMs: Long,
        localExists: Boolean,
        localPct: Double,
        localUpdatedAtMs: Long,
        baseline: SimklPlaybackBaselineEntity?
    ): Boolean {
        if (!localExists) return true
        if (baseline != null && !baseline.localDirty) {
            // Nessuna modifica locale dall'ultima sincronizzazione: se Simkl è cambiato (o comunque diverso), vince remoto
            if (abs(remotePct - baseline.lastSyncedProgressPercent) >= 0.5 ||
                remotePausedAtMs > baseline.lastRemotePausedAtMs
            ) {
                return true
            }
            return remotePausedAtMs > localUpdatedAtMs + 2_000L
        }
        // Entrambi modificati offline (o prima sync con stato locale preesistente):
        // usa timestamp server/locale con tolleranza clock-skew, oppure a parità di tempo privilegia il progresso più avanzato
        if (remotePausedAtMs > localUpdatedAtMs + 5_000L) return true
        if (localUpdatedAtMs > remotePausedAtMs + 5_000L) return false
        return remotePct > localPct + 1.0
    }

    private suspend fun pushDirtyLocalProgressInternal(profileId: String) {
        val dirtyBaselines = dao.getSimklBaselinesForProfile(profileId).filter { it.localDirty }
        if (dirtyBaselines.isEmpty()) return
        val localWatchMap = dao.getWatchProgressListForProfile(profileId).associateBy { it.id }

        for (baseline in dirtyBaselines) {
            val mainId = "${baseline.mediaType}_${baseline.tmdbId}"
            val localMain = localWatchMap[mainId]
            val matchesMain = localMain != null &&
                (baseline.mediaType == "movie" ||
                    (localMain.seasonNumber == baseline.seasonNumber && localMain.episodeNumber == baseline.episodeNumber))
            val localEp = if (baseline.mediaType == "tv") {
                dao.getEpisodeProgress(baseline.contentKey, profileId)
            } else {
                null
            }
            val posMs = localEp?.positionMs ?: if (matchesMain) localMain!!.positionMs else 0L
            val durMs = localEp?.durationMs?.takeIf { it > 0L }
                ?: if (matchesMain) localMain!!.durationMs else 0L

            if (posMs > 0L && durMs > 0L) {
                val pct = ((posMs.toDouble() / durMs.toDouble()) * 100.0).coerceIn(0.1, 99.9)
                val title = localMain?.title.orEmpty()
                val year = localMain?.releaseYear.orEmpty()
                val now = clockMs()
                dao.upsertSimklPendingScrobble(
                    SimklPendingScrobbleEntity(
                        profileId = profileId,
                        contentKey = baseline.contentKey,
                        tmdbId = baseline.tmdbId,
                        mediaType = baseline.mediaType,
                        title = title,
                        releaseYear = year,
                        seasonNumber = baseline.seasonNumber,
                        episodeNumber = baseline.episodeNumber,
                        action = "pause",
                        progressPercent = pct,
                        minProgress = SimklConfig.DEFAULT_MIN_PROGRESS_STOP,
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                )
            }
        }
        flushPendingQueueInternal(profileId)
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
            val det = runCatching { repository.getCachedMediaDetails(item)?.value }.getOrNull()
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
        val parsedSec = headerValue?.trim()?.toLongOrNull()?.takeIf { it > 0L }
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

    internal fun parseIsoUtcMillis(rawIso: String?): Long {
        val clean = rawIso?.trim().orEmpty()
        if (clean.isEmpty()) return 0L
        val normalized = if (clean.endsWith("Z")) {
            clean.dropLast(1) + "+0000"
        } else {
            clean.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        }
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in patterns) {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = runCatching { fmt.parse(normalized)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }
}
