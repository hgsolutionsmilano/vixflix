package com.example.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay

/**
 * Identifica il livello di navigazione o overlay modale attualmente attivo per il D-Pad.
 */
enum class DpadFocusScope {
    SPLASH,
    PROFILE_SELECTOR,
    MAIN_NAVIGATION,
    MODAL_QUICK_ACTION,
    DETAIL_SHEET,
    MODAL_TRAILER_POPUP,
    VIDEO_PLAYER
}

/**
 * Tipologia di evento registrato dal Global D-Pad Focus Observer.
 */
enum class DpadFocusEventType {
    FOCUS_GAINED,
    FOCUS_LOST,
    SCOPE_TRANSITION,
    STUCK_FOCUS_RECOVERED
}

/**
 * Evento strutturato di diagnostica focus D-Pad.
 */
data class DpadFocusEvent(
    val type: DpadFocusEventType,
    val scope: DpadFocusScope,
    val previousScope: DpadFocusScope? = null,
    val targetTag: String,
    val reason: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

val LocalDpadFocusScope = compositionLocalOf { DpadFocusScope.MAIN_NAVIGATION }

/**
 * Osservatore globale centralizzato del focus D-Pad per Android TV, Google TV, Fire Stick e Tablet Landscape.
 * - Registra tutti i cambi di focus (`FOCUS_GAINED`, `FOCUS_LOST`), le transizioni tra viste/modali (`SCOPE_TRANSITION`)
 *   e i recuperi automatici da stati di focus bloccato o orfano (`STUCK_FOCUS_RECOVERED`) su Logcat (`DpadFocusObserver`)
 *   e in un buffer circolare interrogabile nei test.
 * - Previene stati di focus "stuck" quando si passa dalla navigazione principale a overlay modali
 *   (come il popup dei trailer YouTube, la scheda dettaglio, i dialoghi di azione rapida o il player video).
 */
object DpadFocusObserverRegistry {
    private const val LOG_TAG = "DpadFocusObserver"
    private const val MAX_LOG_EVENTS = 300

    private val lock = Any()
    private val eventBuffer = ArrayDeque<DpadFocusEvent>(MAX_LOG_EVENTS)
    private val scopeRecoveryHandlers = mutableMapOf<DpadFocusScope, () -> String?>()

    @Volatile
    var activeScope: DpadFocusScope = DpadFocusScope.MAIN_NAVIGATION
        private set

    @Volatile
    var currentFocusedScope: DpadFocusScope? = null
        private set

    @Volatile
    var currentFocusedTag: String? = null
        private set

    @Volatile
    var rootHasFocus: Boolean = false
        private set

    @Volatile
    var focusTransitionCount: Int = 0
        private set

    @Volatile
    var scopeTransitionCount: Int = 0
        private set

    @Volatile
    var stuckRecoveryCount: Int = 0
        private set

    fun getEventsSnapshot(): List<DpadFocusEvent> = synchronized(lock) {
        eventBuffer.toList()
    }

    fun clearHistoryForTesting() = synchronized(lock) {
        eventBuffer.clear()
        focusTransitionCount = 0
        scopeTransitionCount = 0
        stuckRecoveryCount = 0
        currentFocusedScope = null
        currentFocusedTag = null
        rootHasFocus = false
    }

    fun registerScopeRecoveryHandler(
        scope: DpadFocusScope,
        handler: () -> String?
    ) {
        synchronized(lock) {
            scopeRecoveryHandlers[scope] = handler
        }
    }

    fun unregisterScopeRecoveryHandler(
        scope: DpadFocusScope,
        handler: () -> String?
    ) {
        synchronized(lock) {
            if (scopeRecoveryHandlers[scope] === handler) {
                scopeRecoveryHandlers.remove(scope)
            }
        }
    }

    fun onRootFocusChanged(hasFocus: Boolean) {
        val lostAllFocus: Boolean
        val currentActive: DpadFocusScope
        synchronized(lock) {
            val wasFocused = rootHasFocus
            rootHasFocus = hasFocus
            if (!hasFocus && activeScope != DpadFocusScope.MODAL_QUICK_ACTION) {
                currentFocusedScope = null
                currentFocusedTag = null
            }
            lostAllFocus = wasFocused && !hasFocus
            currentActive = activeScope
        }
        if (lostAllFocus) {
            safeLog("Root hierarchy lost D-Pad focus while activeScope=$currentActive")
        }
    }

    fun onScopeTransition(newScope: DpadFocusScope, reason: String = "layer_change") {
        val oldScope: DpadFocusScope
        synchronized(lock) {
            oldScope = activeScope
            if (oldScope == newScope) return
            activeScope = newScope
            scopeTransitionCount++
            // Se il focus corrente apparteneva allo scope precedente ormai disattivato, invalidiamolo
            if (currentFocusedScope != null && currentFocusedScope != newScope) {
                currentFocusedScope = null
                currentFocusedTag = null
            }
            recordEventLocked(
                DpadFocusEvent(
                    type = DpadFocusEventType.SCOPE_TRANSITION,
                    scope = newScope,
                    previousScope = oldScope,
                    targetTag = currentFocusedTag ?: "none",
                    reason = reason
                )
            )
        }
        safeLog("[SCOPE_TRANSITION] $oldScope -> $newScope (reason=$reason)")
    }

    fun onNodeFocusChanged(
        scope: DpadFocusScope,
        tag: String,
        isFocused: Boolean
    ) {
        var shouldRecoverLeak = false
        var activeAtEvent: DpadFocusScope = scope
        synchronized(lock) {
            activeAtEvent = activeScope
            if (isFocused) {
                val changed = currentFocusedScope != scope || currentFocusedTag != tag
                currentFocusedScope = scope
                currentFocusedTag = tag
                rootHasFocus = true
                if (changed) {
                    focusTransitionCount++
                    recordEventLocked(
                        DpadFocusEvent(
                            type = DpadFocusEventType.FOCUS_GAINED,
                            scope = scope,
                            targetTag = tag,
                            reason = if (scope == activeScope) "active_scope" else "inactive_scope_leak"
                        )
                    )
                }
                if (scope != activeScope && activeScope != DpadFocusScope.SPLASH) {
                    shouldRecoverLeak = true
                }
            } else {
                if (currentFocusedScope == scope && currentFocusedTag == tag) {
                    currentFocusedScope = null
                    currentFocusedTag = null
                    recordEventLocked(
                        DpadFocusEvent(
                            type = DpadFocusEventType.FOCUS_LOST,
                            scope = scope,
                            targetTag = tag
                        )
                    )
                }
            }
        }
        if (isFocused) {
            safeLog("[FOCUS_GAINED] scope=$scope tag=$tag (activeScope=$activeAtEvent)")
            if (shouldRecoverLeak) {
                safeLog("[FOCUS_LEAK_DETECTED] node '$tag' in background scope $scope gained focus while $activeAtEvent is active; recovering...")
                recoverFocusForScope(
                    scope = activeAtEvent,
                    reason = "background_focus_leak_from_${scope.name}_$tag"
                )
            }
        }
    }

    /**
     * Verifica se lo scope attualmente attivo possiede un nodo focalizzato valido.
     */
    fun isFocusStuckOrOrphaned(expectedScope: DpadFocusScope = activeScope): Boolean {
        if (expectedScope == DpadFocusScope.SPLASH) return false
        return synchronized(lock) {
            !rootHasFocus ||
                currentFocusedScope != expectedScope ||
                currentFocusedTag.isNullOrBlank()
        }
    }

    /**
     * Forza il recupero deterministico del focus D-Pad sullo scope attivo invocando l'handler registrato.
     */
    fun recoverFocusForScope(
        scope: DpadFocusScope = activeScope,
        reason: String
    ): Boolean {
        if (scope == DpadFocusScope.SPLASH) return false
        val handler = synchronized(lock) { scopeRecoveryHandlers[scope] } ?: return false
        val recoveredTarget = runCatching { handler.invoke() }.getOrNull()
        if (!recoveredTarget.isNullOrBlank()) {
            synchronized(lock) {
                currentFocusedScope = scope
                currentFocusedTag = recoveredTarget
                rootHasFocus = true
                stuckRecoveryCount++
                recordEventLocked(
                    DpadFocusEvent(
                        type = DpadFocusEventType.STUCK_FOCUS_RECOVERED,
                        scope = scope,
                        targetTag = recoveredTarget,
                        reason = reason
                    )
                )
            }
            safeLog("[STUCK_FOCUS_RECOVERED] scope=$scope target=$recoveredTarget reason=$reason")
            return true
        }
        return false
    }

    private fun recordEventLocked(event: DpadFocusEvent) {
        if (eventBuffer.size >= MAX_LOG_EVENTS) {
            eventBuffer.removeFirst()
        }
        eventBuffer.addLast(event)
    }

    private fun safeLog(message: String) {
        runCatching { Log.d(LOG_TAG, message) }
    }
}

/**
 * Registra un callback di recupero focus per uno specifico [DpadFocusScope] finché il Composable è montato.
 * Fornisce inoltre il [LocalDpadFocusScope] ai figli nella gerarchia.
 */
@Composable
fun DpadFocusScopeProvider(
    scope: DpadFocusScope,
    onRecoverFocus: () -> String?,
    content: @Composable () -> Unit
) {
    val currentRecover by rememberUpdatedState(onRecoverFocus)
    DisposableEffect(scope) {
        val wrapper: () -> String? = { currentRecover() }
        DpadFocusObserverRegistry.registerScopeRecoveryHandler(scope, wrapper)
        onDispose {
            DpadFocusObserverRegistry.unregisterScopeRecoveryHandler(scope, wrapper)
        }
    }
    CompositionLocalProvider(LocalDpadFocusScope provides scope) {
        content()
    }
}

/**
 * Contenitore radice dell'osservatore globale D-Pad:
 * 1. Traccia lo scope attivo ([activeScope]) e registra ogni transizione tra navigazione principale e overlay modali.
 * 2. Esegue un watchdog multi-frame a ogni cambio di scope (es. apertura/chiusura popup trailer, apertura/chiusura dettagli,
 *    dialoghi rapidi o player) per prevenire stati di focus perso o rimasto bloccato nel layer sottostante.
 * 3. Intercetta i tasti direzionali/OK del telecomando (`onPreviewKeyEvent`) e, se rileva uno stato di focus orfano o bloccato,
 *    ripristina istantaneamente il focus sul controllo primario dello scope attivo.
 */
@Composable
fun GlobalDpadFocusObserverHost(
    activeScope: DpadFocusScope,
    isTvOrDpadActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val focusManager: FocusManager = LocalFocusManager.current
    var previousScope by remember { mutableStateOf(activeScope) }

    // Watchdog su transizione tra navigazione principale e overlay modali (es. Trailer Popup <-> Detail Sheet <-> Main Nav)
    LaunchedEffect(activeScope, isTvOrDpadActive) {
        val fromScope = previousScope
        if (fromScope != activeScope) {
            // Se stiamo uscendo da un overlay modale (es. MODAL_TRAILER_POPUP o MODAL_QUICK_ACTION)
            // e il focus è rimasto agganciato al layer in chiusura, puliamo e riassegniamo allo scope attivo.
            val focusedScopeSnapshot = DpadFocusObserverRegistry.currentFocusedScope
            if (focusedScopeSnapshot != null && focusedScopeSnapshot != activeScope) {
                runCatching { focusManager.clearFocus(force = true) }
            }
            DpadFocusObserverRegistry.onScopeTransition(
                newScope = activeScope,
                reason = "transition_${fromScope.name}_to_${activeScope.name}"
            )
            previousScope = activeScope
        } else {
            DpadFocusObserverRegistry.onScopeTransition(
                newScope = activeScope,
                reason = "initial_scope_${activeScope.name}"
            )
        }

        if (!isTvOrDpadActive || activeScope == DpadFocusScope.SPLASH) return@LaunchedEffect

        withFrameNanos { }
        val watchdogDelaysMs = longArrayOf(0L, 16L, 48L, 100L, 180L, 280L)
        for (stepDelay in watchdogDelaysMs) {
            if (stepDelay > 0L) delay(stepDelay)
            if (!DpadFocusObserverRegistry.isFocusStuckOrOrphaned(activeScope)) {
                break
            }
            val recovered = DpadFocusObserverRegistry.recoverFocusForScope(
                scope = activeScope,
                reason = "watchdog_after_${fromScope.name}_to_${activeScope.name}"
            )
            if (recovered && !DpadFocusObserverRegistry.isFocusStuckOrOrphaned(activeScope)) {
                break
            }
        }
    }

    Box(
        modifier = modifier
            .onFocusChanged { focusState ->
                DpadFocusObserverRegistry.onRootFocusChanged(focusState.hasFocus)
            }
            .onPreviewKeyEvent { keyEvent: KeyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && isDpadNavigationOrConfirmKey(keyEvent.key)) {
                    val focusedScope = DpadFocusObserverRegistry.currentFocusedScope
                    val isGenuineOrphanOrLeak = !DpadFocusObserverRegistry.rootHasFocus ||
                        (focusedScope != null && focusedScope != activeScope)
                    if (isGenuineOrphanOrLeak && DpadFocusObserverRegistry.isFocusStuckOrOrphaned(activeScope)) {
                        val recovered = DpadFocusObserverRegistry.recoverFocusForScope(
                            scope = activeScope,
                            reason = "orphan_dpad_key_${keyEvent.key}"
                        )
                        if (recovered) {
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            },
        content = content
    )
}

fun isDpadNavigationOrConfirmKey(key: Key): Boolean {
    return key == Key.DirectionUp ||
        key == Key.DirectionDown ||
        key == Key.DirectionLeft ||
        key == Key.DirectionRight ||
        key == Key.DirectionCenter ||
        key == Key.Enter ||
        key == Key.NumPadEnter
}

/**
 * Helper per tentare il focus su una catena ordinata di candidati `(tag, FocusRequester?)`
 * restituendo il `tag` del primo controllo che acquisisce con successo il focus.
 */
fun recoverFocusFromCandidates(
    vararg candidates: Pair<String, FocusRequester?>
): String? {
    for ((tag, requester) in candidates) {
        if (requester != null && requester.safeRequestFocus()) {
            return tag
        }
    }
    return null
}
