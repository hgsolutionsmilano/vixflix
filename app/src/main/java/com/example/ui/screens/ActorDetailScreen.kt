package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.remote.MediaItem
import com.example.data.remote.formatTmdbDateItalian
import com.example.ui.ActorDetailUiState
import com.example.ui.components.DpadFocusScope
import com.example.ui.components.DpadFocusScopeProvider
import com.example.ui.components.MediaPosterCard
import com.example.ui.components.rememberIsTvDevice
import com.example.ui.components.rememberOptimizedImageRequest
import com.example.ui.components.safeFocusRequester
import com.example.ui.components.safeRequestFocus
import com.example.ui.components.takeIfAttached
import com.example.ui.components.consumeBackAndEscapeKey
import com.example.ui.components.tvFocusableControl
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ActorFilmographyFilter(val label: String, val tagSuffix: String) {
    ALL("Tutti", "all"),
    MOVIES("Film", "movies"),
    SERIES("Serie TV", "tv")
}

@Composable
fun ActorDetailScreen(
    state: ActorDetailUiState,
    detailStackDepth: Int = 2,
    previousDetailTitle: String? = null,
    isInteractive: Boolean = true,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onSelectMedia: (item: MediaItem, actorScrollIndex: Int, actorScrollOffset: Int) -> Unit
) {
    val backAction = if (detailStackDepth > 1) onBack else onClose

    BackHandler(enabled = isInteractive) {
        backAction()
    }

    val baseIsTvDevice = rememberIsTvDevice()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("actor_detail_screen")
    ) {
        val isTvLayout = baseIsTvDevice || maxWidth >= 600.dp || maxWidth > maxHeight
        val horizontalPadding = if (isTvLayout) 40.dp else 16.dp
        val posterCardWidth = 130.dp
        val posterSpacing = if (isTvLayout) 16.dp else 12.dp
        val availableGridWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(posterCardWidth * 2)
        val columns = ((availableGridWidth + posterSpacing) / (posterCardWidth + posterSpacing))
            .toInt()
            .coerceIn(2, 6)

        val details = state.details
        val allFilmography = remember(details?.filmography) {
            details?.filmography.orEmpty().distinctBy { it.uniqueKey }
        }
        val movieCount = remember(allFilmography) { allFilmography.count { !it.isSeries } }
        val seriesCount = remember(allFilmography) { allFilmography.count { it.isSeries } }
        val showFilterChips = movieCount > 0 && seriesCount > 0

        var selectedFilter by remember(state.actorId) { mutableStateOf(ActorFilmographyFilter.ALL) }
        var isBioExpanded by remember(state.actorId) { mutableStateOf(false) }

        // Se stiamo rientrando da un film/serie specifico, assicuriamoci che il filtro lo includa
        LaunchedEffect(state.actorId, state.savedFocusedMediaKey, allFilmography) {
            val savedKey = state.savedFocusedMediaKey ?: return@LaunchedEffect
            val savedVisibleInFilter = when (selectedFilter) {
                ActorFilmographyFilter.ALL -> allFilmography.any { it.uniqueKey == savedKey }
                ActorFilmographyFilter.MOVIES -> allFilmography.any { !it.isSeries && it.uniqueKey == savedKey }
                ActorFilmographyFilter.SERIES -> allFilmography.any { it.isSeries && it.uniqueKey == savedKey }
            }
            if (!savedVisibleInFilter && allFilmography.any { it.uniqueKey == savedKey }) {
                selectedFilter = ActorFilmographyFilter.ALL
            }
        }

        val displayedFilmography = remember(allFilmography, selectedFilter) {
            when (selectedFilter) {
                ActorFilmographyFilter.ALL -> allFilmography
                ActorFilmographyFilter.MOVIES -> allFilmography.filter { !it.isSeries }
                ActorFilmographyFilter.SERIES -> allFilmography.filter { it.isSeries }
            }
        }
        val filmographyRows = remember(displayedFilmography, columns) {
            displayedFilmography.chunked(columns)
        }

        val biographyText = details?.biography?.trim().orEmpty()
        val hasExpandableBio = biographyText.length > 240
        val formattedBirthday = remember(details?.birthday) { formatTmdbDateItalian(details?.birthday) }
        val formattedDeathday = remember(details?.deathday) { formatTmdbDateItalian(details?.deathday) }

        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        val backFocusRequester = remember(state.actorId) { FocusRequester() }
        val closeFocusRequester = remember(state.actorId) { FocusRequester() }
        val bioToggleFocusRequester = remember(state.actorId) { FocusRequester() }
        val retryFocusRequester = remember(state.actorId) { FocusRequester() }
        val filterFocusRequesters = remember(state.actorId) {
            ActorFilmographyFilter.entries.associateWith { FocusRequester() }
        }
        val posterFocusRequesters = remember(state.actorId, displayedFilmography) {
            displayedFilmography.associate { it.uniqueKey to FocusRequester() }
        }

        val navState = remember(state.actorId) {
            object {
                var navJob: Job? = null
                var lastFocusedMediaKey: String? = null
                var userMovedFocus: Boolean = false
            }
        }

        // Indici deterministici nel LazyColumn per scroll automatico D-Pad
        // 0 = Header Attore, 1 = Titolo sezione Filmografia (+ eventuali filtri), 2.. = Righe poster (o loading/error/empty)
        val headerItemIndex = 0
        val filmographyHeaderIndex = 1
        val filmographyStartLazyIndex = 2

        val focusAndScrollToLazyItem: (Int, FocusRequester?) -> Boolean = { lazyIndex, requester ->
            if (requester == null) {
                false
            } else {
                navState.navJob?.cancel()
                navState.userMovedFocus = true
                val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == lazyIndex }
                if (visible && requester.safeRequestFocus()) {
                    true
                } else {
                    navState.navJob = coroutineScope.launch {
                        runCatching {
                            if (lazyIndex == 0) {
                                listState.scrollToItem(0, 0)
                            } else {
                                listState.scrollToItem(lazyIndex, -90)
                            }
                        }
                        for (attempt in 1..6) {
                            withFrameNanos { }
                            if (requester.safeRequestFocus()) break
                        }
                    }
                    true
                }
            }
        }

        val firstContentBelowTopBarRequester: FocusRequester? = when {
            hasExpandableBio -> bioToggleFocusRequester
            showFilterChips -> filterFocusRequesters[selectedFilter] ?: filterFocusRequesters[ActorFilmographyFilter.ALL]
            displayedFilmography.isNotEmpty() -> posterFocusRequesters[displayedFilmography.first().uniqueKey]
            state.errorMessage != null && !state.isLoading -> retryFocusRequester
            else -> null
        }

        val firstContentBelowTopBarLazyIndex: Int = when {
            hasExpandableBio -> headerItemIndex
            showFilterChips -> filmographyHeaderIndex
            displayedFilmography.isNotEmpty() -> filmographyStartLazyIndex
            state.errorMessage != null && !state.isLoading -> filmographyStartLazyIndex
            else -> headerItemIndex
        }

        val targetBelowBioRequester: FocusRequester? = when {
            showFilterChips -> filterFocusRequesters[selectedFilter] ?: filterFocusRequesters[ActorFilmographyFilter.ALL]
            displayedFilmography.isNotEmpty() -> posterFocusRequesters[displayedFilmography.first().uniqueKey]
            state.errorMessage != null && !state.isLoading -> retryFocusRequester
            else -> null
        }
        val targetBelowBioLazyIndex: Int = when {
            showFilterChips -> filmographyHeaderIndex
            else -> filmographyStartLazyIndex
        }

        val targetAboveFilmographyRequester: FocusRequester = when {
            showFilterChips -> filterFocusRequesters[selectedFilter] ?: filterFocusRequesters[ActorFilmographyFilter.ALL] ?: backFocusRequester
            hasExpandableBio -> bioToggleFocusRequester
            else -> backFocusRequester
        }
        val targetAboveFilmographyLazyIndex: Int = when {
            showFilterChips -> filmographyHeaderIndex
            else -> headerItemIndex
        }

        // Ripristino deterministico di scroll e focus sull'esatta locandina della filmografia al ritorno con BACK,
        // oppure focus sicuro sul pulsante Indietro all'apertura iniziale (mai perdita di focus durante il caricamento).
        LaunchedEffect(
            state.actorId,
            isInteractive,
            state.isLoading,
            state.savedFocusedMediaKey,
            state.savedScrollIndex,
            state.savedScrollOffset,
            displayedFilmography.size
        ) {
            if (!isInteractive) return@LaunchedEffect
            val savedKey = state.savedFocusedMediaKey
            if (savedKey != null && displayedFilmography.isNotEmpty()) {
                val targetIndex = displayedFilmography.indexOfFirst { it.uniqueKey == savedKey }
                if (targetIndex >= 0) {
                    val targetRowIdx = targetIndex / columns
                    val targetLazyIndex = filmographyStartLazyIndex + targetRowIdx
                    val targetRequester = posterFocusRequesters[savedKey]

                    if (state.savedScrollIndex > 0 || state.savedScrollOffset > 0) {
                        runCatching { listState.scrollToItem(state.savedScrollIndex, state.savedScrollOffset) }
                    } else {
                        runCatching { listState.scrollToItem(targetLazyIndex, -90) }
                    }
                    withFrameNanos { }

                    var restored = false
                    for (attempt in 1..8) {
                        if (targetRequester.safeRequestFocus()) {
                            restored = true
                            break
                        }
                        if (attempt == 3) {
                            runCatching { listState.scrollToItem(targetLazyIndex, -90) }
                        }
                        delay(20L)
                        withFrameNanos { }
                    }
                    if (!restored) {
                        backFocusRequester.safeRequestFocus()
                    }
                    return@LaunchedEffect
                }
            }

            if (!navState.userMovedFocus) {
                val fallbackRequester = if (!state.isLoading && state.errorMessage != null && displayedFilmography.isEmpty()) {
                    retryFocusRequester.takeIfAttached() ?: backFocusRequester
                } else {
                    backFocusRequester
                }
                for (attempt in 1..6) {
                    withFrameNanos { }
                    if (fallbackRequester.safeRequestFocus()) break
                    delay(20L)
                }
            }
        }

        DpadFocusScopeProvider(
            scope = DpadFocusScope.ACTOR_DETAIL,
            onRecoverFocus = {
                if (!isInteractive) {
                    null
                } else {
                    val savedPoster = (state.savedFocusedMediaKey ?: navState.lastFocusedMediaKey)
                        ?.let { posterFocusRequesters[it] }
                    when {
                        savedPoster.safeRequestFocus() -> "actor_filmography_restored"
                        firstContentBelowTopBarRequester.safeRequestFocus() -> "actor_primary_content"
                        backFocusRequester.safeRequestFocus() -> "actor_detail_back_button"
                        closeFocusRequester.safeRequestFocus() -> "actor_detail_close_button"
                        else -> null
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeBackAndEscapeKey(
                        enabled = isInteractive,
                        onBack = backAction
                    )
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isTvLayout) Modifier else Modifier.navigationBarsPadding())
                        .testTag("actor_detail_lazy_column"),
                    contentPadding = PaddingValues(
                        top = if (isTvLayout) 78.dp else 88.dp,
                        bottom = 48.dp
                    )
                ) {
                    // 0. HEADER ATTORE: Foto, Nome, Data & Luogo di nascita, Biografia
                    item(key = "actor_header_${state.actorId}", contentType = "actor_header") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        0f to NetflixDarkSurface.copy(alpha = 0.85f),
                                        0.75f to NetflixBlack.copy(alpha = 0.92f),
                                        1f to NetflixBlack
                                    )
                                )
                                .padding(horizontal = horizontalPadding, vertical = 12.dp)
                        ) {
                            if (isTvLayout) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    ActorPortraitPhoto(
                                        profileUrl = state.resolvedProfileUrl,
                                        actorName = details?.name?.ifBlank { state.name } ?: state.name,
                                        widthDp = 156,
                                        heightDp = 220
                                    )
                                    ActorMetadataAndBiography(
                                        displayName = details?.name?.ifBlank { state.name } ?: state.name,
                                        knownForDepartment = details?.knownForDepartment,
                                        characterRole = state.characterRole,
                                        formattedBirthday = formattedBirthday,
                                        formattedDeathday = formattedDeathday,
                                        placeOfBirth = details?.placeOfBirth,
                                        movieCount = movieCount,
                                        seriesCount = seriesCount,
                                        biographyText = biographyText,
                                        isLoading = state.isLoading,
                                        isBioExpanded = isBioExpanded,
                                        hasExpandableBio = hasExpandableBio,
                                        bioToggleFocusRequester = bioToggleFocusRequester,
                                        onToggleBio = { isBioExpanded = !isBioExpanded },
                                        onUpFromBio = { focusAndScrollToLazyItem(headerItemIndex, backFocusRequester) },
                                        onDownFromBio = {
                                            targetBelowBioRequester?.let {
                                                focusAndScrollToLazyItem(targetBelowBioLazyIndex, it)
                                            } ?: true
                                        },
                                        onBioFocus = { navState.userMovedFocus = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ActorPortraitPhoto(
                                            profileUrl = state.resolvedProfileUrl,
                                            actorName = details?.name?.ifBlank { state.name } ?: state.name,
                                            widthDp = 112,
                                            heightDp = 158
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ActorHeaderCoreMeta(
                                                displayName = details?.name?.ifBlank { state.name } ?: state.name,
                                                knownForDepartment = details?.knownForDepartment,
                                                characterRole = state.characterRole,
                                                formattedBirthday = formattedBirthday,
                                                formattedDeathday = formattedDeathday,
                                                placeOfBirth = details?.placeOfBirth,
                                                movieCount = movieCount,
                                                seriesCount = seriesCount
                                            )
                                        }
                                    }
                                    ActorBiographyBlock(
                                        biographyText = biographyText,
                                        isLoading = state.isLoading,
                                        isBioExpanded = isBioExpanded,
                                        hasExpandableBio = hasExpandableBio,
                                        bioToggleFocusRequester = bioToggleFocusRequester,
                                        onToggleBio = { isBioExpanded = !isBioExpanded },
                                        onUpFromBio = { focusAndScrollToLazyItem(headerItemIndex, backFocusRequester) },
                                        onDownFromBio = {
                                            targetBelowBioRequester?.let {
                                                focusAndScrollToLazyItem(targetBelowBioLazyIndex, it)
                                            } ?: true
                                        },
                                        onBioFocus = { navState.userMovedFocus = true }
                                    )
                                }
                            }
                        }
                    }

                    // 1. INTESTAZIONE FILMOGRAFIA + FILTRI (Tutti / Film / Serie TV)
                    item(key = "actor_filmography_header_${state.actorId}", contentType = "actor_filmography_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = horizontalPadding)
                                .padding(top = 18.dp, bottom = 10.dp)
                                .testTag("actor_filmography_section")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filmografia",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NetflixWhite
                                )
                                if (displayedFilmography.isNotEmpty()) {
                                    Text(
                                        text = "${displayedFilmography.size} titoli",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NetflixLightGray
                                    )
                                }
                            }

                            if (showFilterChips) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val filters = ActorFilmographyFilter.entries
                                Row(
                                    modifier = Modifier.focusGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    filters.forEachIndexed { idx, filter ->
                                        val isSelected = selectedFilter == filter
                                        val countLabel = when (filter) {
                                            ActorFilmographyFilter.ALL -> "${allFilmography.size}"
                                            ActorFilmographyFilter.MOVIES -> "$movieCount"
                                            ActorFilmographyFilter.SERIES -> "$seriesCount"
                                        }
                                        val chipRequester = filterFocusRequesters.getValue(filter)
                                        val leftChip = filters.getOrNull(idx - 1)?.let { filterFocusRequesters[it] }
                                        val rightChip = filters.getOrNull(idx + 1)?.let { filterFocusRequesters[it] }
                                        val upTarget = if (hasExpandableBio) bioToggleFocusRequester else backFocusRequester
                                        val firstPosterReq = displayedFilmography.firstOrNull()?.let { posterFocusRequesters[it.uniqueKey] }

                                        Surface(
                                            color = if (isSelected) NetflixRed else NetflixCardSurface,
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (isSelected) NetflixRed else NetflixElevatedSurface
                                            ),
                                            modifier = Modifier
                                                .safeFocusRequester(chipRequester)
                                                .onPreviewKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        when (keyEvent.key) {
                                                            Key.DirectionLeft -> leftChip?.safeRequestFocus() ?: true
                                                            Key.DirectionRight -> rightChip?.safeRequestFocus() ?: true
                                                            Key.DirectionUp -> focusAndScrollToLazyItem(headerItemIndex, upTarget)
                                                            Key.DirectionDown -> {
                                                                if (firstPosterReq != null) {
                                                                    focusAndScrollToLazyItem(filmographyStartLazyIndex, firstPosterReq)
                                                                } else {
                                                                    true
                                                                }
                                                            }
                                                            else -> false
                                                        }
                                                    } else {
                                                        false
                                                    }
                                                }
                                                .tvFocusableControl(
                                                    shape = RoundedCornerShape(20.dp),
                                                    focusedScale = 1.06f,
                                                    focusedBorderColor = NetflixWhite,
                                                    focusTag = "actor_filter_${filter.tagSuffix}",
                                                    onFocusChange = { focused ->
                                                        if (focused) navState.userMovedFocus = true
                                                    },
                                                    onClick = { selectedFilter = filter }
                                                )
                                                .testTag("actor_filter_${filter.tagSuffix}")
                                        ) {
                                            Text(
                                                text = "${filter.label} ($countLabel)",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = NetflixWhite,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. STATO CARICAMENTO / ERRORE / GRIGLIA FILMOGRAFIA
                    if (state.isLoading && displayedFilmography.isEmpty()) {
                        item(key = "actor_loading", contentType = "actor_status") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(color = NetflixRed)
                                    Text(
                                        text = "Caricamento biografia e filmografia da TMDB…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NetflixLightGray
                                    )
                                }
                            }
                        }
                    } else if (state.errorMessage != null && displayedFilmography.isEmpty()) {
                        item(key = "actor_error", contentType = "actor_status") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding, vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.widthIn(max = 460.dp)
                                ) {
                                    Text(
                                        text = state.errorMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NetflixLightGray
                                    )
                                    Button(
                                        onClick = onRetry,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NetflixRed,
                                            contentColor = NetflixWhite
                                        ),
                                        modifier = Modifier
                                            .safeFocusRequester(retryFocusRequester)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                                    focusAndScrollToLazyItem(headerItemIndex, backFocusRequester)
                                                } else {
                                                    false
                                                }
                                            }
                                            .tvFocusableControl(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedScale = 1.06f,
                                                focusedBorderColor = NetflixWhite,
                                                focusTag = "actor_detail_retry_button",
                                                onFocusChange = { if (it) navState.userMovedFocus = true }
                                            )
                                            .testTag("actor_detail_retry_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Riprova")
                                    }
                                }
                            }
                        }
                    } else if (displayedFilmography.isEmpty()) {
                        item(key = "actor_empty", contentType = "actor_status") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding, vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nessun film o serie TV disponibile in filmografia.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NetflixLightGray,
                                    modifier = Modifier.testTag("actor_filmography_empty")
                                )
                            }
                        }
                    } else {
                        items(
                            count = filmographyRows.size,
                            key = { rowIdx ->
                                val firstKey = filmographyRows[rowIdx].firstOrNull()?.uniqueKey ?: "$rowIdx"
                                "actor_film_row_${selectedFilter.tagSuffix}_${rowIdx}_$firstKey"
                            },
                            contentType = { "actor_filmography_row" }
                        ) { rowIdx ->
                            val rowItems = filmographyRows[rowIdx]
                            val currentLazyIndex = filmographyStartLazyIndex + rowIdx

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(posterSpacing, Alignment.Start),
                                verticalAlignment = Alignment.Top
                            ) {
                                rowItems.forEachIndexed { colIdx, mediaItem ->
                                    val ownRequester = posterFocusRequesters[mediaItem.uniqueKey]
                                    val leftReq = if (colIdx > 0) {
                                        posterFocusRequesters[rowItems[colIdx - 1].uniqueKey]
                                    } else {
                                        null
                                    }
                                    val rightReq = if (colIdx < rowItems.lastIndex) {
                                        posterFocusRequesters[rowItems[colIdx + 1].uniqueKey]
                                    } else {
                                        null
                                    }
                                    val upRow = filmographyRows.getOrNull(rowIdx - 1)
                                    val upReq = upRow?.let { prevRow ->
                                        val targetItem = prevRow[colIdx.coerceAtMost(prevRow.lastIndex)]
                                        posterFocusRequesters[targetItem.uniqueKey]
                                    }
                                    val downRow = filmographyRows.getOrNull(rowIdx + 1)
                                    val downReq = downRow?.let { nextRow ->
                                        val targetItem = nextRow[colIdx.coerceAtMost(nextRow.lastIndex)]
                                        posterFocusRequesters[targetItem.uniqueKey]
                                    }

                                    Column(
                                        modifier = Modifier
                                            .width(posterCardWidth)
                                            .testTag("actor_filmography_item_${mediaItem.uniqueKey}"),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        MediaPosterCard(
                                            item = mediaItem,
                                            onClick = {
                                                navState.lastFocusedMediaKey = mediaItem.uniqueKey
                                                onSelectMedia(
                                                    mediaItem,
                                                    listState.firstVisibleItemIndex,
                                                    listState.firstVisibleItemScrollOffset
                                                )
                                            },
                                            onFocused = {
                                                navState.userMovedFocus = true
                                                navState.lastFocusedMediaKey = mediaItem.uniqueKey
                                            },
                                            modifier = Modifier
                                                .safeFocusRequester(ownRequester)
                                                .onPreviewKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        when (keyEvent.key) {
                                                            Key.DirectionLeft -> leftReq?.safeRequestFocus() ?: true
                                                            Key.DirectionRight -> rightReq?.safeRequestFocus() ?: true
                                                            Key.DirectionUp -> {
                                                                if (upReq != null) {
                                                                    focusAndScrollToLazyItem(currentLazyIndex - 1, upReq)
                                                                } else {
                                                                    focusAndScrollToLazyItem(
                                                                        targetAboveFilmographyLazyIndex,
                                                                        targetAboveFilmographyRequester
                                                                    )
                                                                }
                                                            }
                                                            Key.DirectionDown -> {
                                                                if (downReq != null) {
                                                                    focusAndScrollToLazyItem(currentLazyIndex + 1, downReq)
                                                                } else {
                                                                    true
                                                                }
                                                            }
                                                            else -> false
                                                        }
                                                    } else {
                                                        false
                                                    }
                                                }
                                                .focusProperties {
                                                    leftReq.takeIfAttached()?.let { left = it }
                                                    rightReq.takeIfAttached()?.let { right = it }
                                                    (upReq ?: targetAboveFilmographyRequester).takeIfAttached()?.let { up = it }
                                                    downReq.takeIfAttached()?.let { down = it }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Barra superiore fissa con "Torna a <Titolo>" e pulsante Chiudi
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to NetflixBlack.copy(alpha = 0.94f),
                                0.8f to NetflixBlack.copy(alpha = 0.72f),
                                1f to Color.Transparent
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .focusGroup()
                ) {
                    Surface(
                        color = NetflixBlack.copy(alpha = 0.84f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, NetflixElevatedSurface),
                        modifier = Modifier
                            .safeFocusRequester(backFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp, Key.DirectionLeft -> true
                                        Key.DirectionRight -> closeFocusRequester.safeRequestFocus()
                                        Key.DirectionDown -> {
                                            if (firstContentBelowTopBarRequester != null) {
                                                focusAndScrollToLazyItem(
                                                    firstContentBelowTopBarLazyIndex,
                                                    firstContentBelowTopBarRequester
                                                )
                                            } else {
                                                true
                                            }
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                closeFocusRequester.takeIfAttached()?.let { right = it }
                                firstContentBelowTopBarRequester.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = RoundedCornerShape(50),
                                focusedBorderColor = NetflixWhite,
                                focusedScale = 1.08f,
                                focusTag = "actor_detail_back_button",
                                onFocusChange = { focused ->
                                    if (focused) navState.userMovedFocus = true
                                },
                                onClick = backAction
                            )
                            .testTag("actor_detail_back_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Torna indietro",
                                tint = NetflixWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = previousDetailTitle?.takeIf { it.isNotBlank() }?.let { "Torna a $it" } ?: "Indietro",
                                style = MaterialTheme.typography.labelLarge,
                                color = NetflixWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 260.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(48.dp)
                            .safeFocusRequester(closeFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp, Key.DirectionRight -> true
                                        Key.DirectionLeft -> backFocusRequester.safeRequestFocus()
                                        Key.DirectionDown -> {
                                            if (firstContentBelowTopBarRequester != null) {
                                                focusAndScrollToLazyItem(
                                                    firstContentBelowTopBarLazyIndex,
                                                    firstContentBelowTopBarRequester
                                                )
                                            } else {
                                                true
                                            }
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .focusProperties {
                                backFocusRequester.takeIfAttached()?.let { left = it }
                                firstContentBelowTopBarRequester.takeIfAttached()?.let { down = it }
                            }
                            .tvFocusableControl(
                                shape = CircleShape,
                                focusedScale = 1.12f,
                                focusedBorderColor = NetflixWhite,
                                focusTag = "actor_detail_close_button",
                                onFocusChange = { focused ->
                                    if (focused) navState.userMovedFocus = true
                                }
                            )
                            .clip(CircleShape)
                            .background(NetflixBlack.copy(alpha = 0.8f))
                            .testTag("actor_detail_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Chiudi",
                            tint = NetflixWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActorPortraitPhoto(
    profileUrl: String?,
    actorName: String,
    widthDp: Int,
    heightDp: Int
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
        border = BorderStroke(1.dp, NetflixWhite.copy(alpha = 0.2f)),
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .shadow(12.dp, RoundedCornerShape(14.dp))
            .testTag("actor_detail_photo")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!profileUrl.isNullOrBlank()) {
                AsyncImage(
                    model = rememberOptimizedImageRequest(
                        url = profileUrl,
                        widthDp = widthDp.dp,
                        heightDp = heightDp.dp,
                        allowRgb565 = true,
                        crossfadeMs = 0
                    ),
                    contentDescription = actorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = actorName,
                    tint = NetflixMutedGray,
                    modifier = Modifier.size((widthDp * 0.42f).dp)
                )
            }
        }
    }
}

@Composable
private fun ActorMetadataAndBiography(
    displayName: String,
    knownForDepartment: String?,
    characterRole: String?,
    formattedBirthday: String?,
    formattedDeathday: String?,
    placeOfBirth: String?,
    movieCount: Int,
    seriesCount: Int,
    biographyText: String,
    isLoading: Boolean,
    isBioExpanded: Boolean,
    hasExpandableBio: Boolean,
    bioToggleFocusRequester: FocusRequester,
    onToggleBio: () -> Unit,
    onUpFromBio: () -> Boolean,
    onDownFromBio: () -> Boolean,
    onBioFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActorHeaderCoreMeta(
            displayName = displayName,
            knownForDepartment = knownForDepartment,
            characterRole = characterRole,
            formattedBirthday = formattedBirthday,
            formattedDeathday = formattedDeathday,
            placeOfBirth = placeOfBirth,
            movieCount = movieCount,
            seriesCount = seriesCount
        )
        Spacer(modifier = Modifier.height(2.dp))
        ActorBiographyBlock(
            biographyText = biographyText,
            isLoading = isLoading,
            isBioExpanded = isBioExpanded,
            hasExpandableBio = hasExpandableBio,
            bioToggleFocusRequester = bioToggleFocusRequester,
            onToggleBio = onToggleBio,
            onUpFromBio = onUpFromBio,
            onDownFromBio = onDownFromBio,
            onBioFocus = onBioFocus
        )
    }
}

@Composable
private fun ActorHeaderCoreMeta(
    displayName: String,
    knownForDepartment: String?,
    characterRole: String?,
    formattedBirthday: String?,
    formattedDeathday: String?,
    placeOfBirth: String?,
    movieCount: Int,
    seriesCount: Int
) {
    Text(
        text = displayName,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = NetflixWhite,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("actor_detail_name")
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val roleBadge = when (knownForDepartment?.lowercase()) {
            "acting" -> "Attore / Attrice"
            "directing" -> "Regia"
            "writing" -> "Sceneggiatura"
            "production" -> "Produzione"
            else -> knownForDepartment?.takeIf { it.isNotBlank() } ?: "Cast"
        }
        Surface(
            color = NetflixRed.copy(alpha = 0.2f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.65f))
        ) {
            Text(
                text = roleBadge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = NetflixWhite,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (movieCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = NetflixLightGray,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$movieCount film",
                    style = MaterialTheme.typography.labelMedium,
                    color = NetflixLightGray
                )
            }
        }
        if (seriesCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = NetflixLightGray,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$seriesCount serie",
                    style = MaterialTheme.typography.labelMedium,
                    color = NetflixLightGray
                )
            }
        }
    }

    if (!characterRole.isNullOrBlank()) {
        Text(
            text = "Ruolo nel titolo precedente: $characterRole",
            style = MaterialTheme.typography.bodySmall,
            color = NetflixLightGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (!formattedBirthday.isNullOrBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.testTag("actor_detail_birthday")
        ) {
            Icon(
                imageVector = Icons.Default.Cake,
                contentDescription = "Data di nascita",
                tint = NetflixLightGray,
                modifier = Modifier.size(15.dp)
            )
            val lifeText = if (!formattedDeathday.isNullOrBlank()) {
                "Nato il $formattedBirthday • Scomparso il $formattedDeathday"
            } else {
                "Data di nascita: $formattedBirthday"
            }
            Text(
                text = lifeText,
                style = MaterialTheme.typography.bodySmall,
                color = NetflixWhite.copy(alpha = 0.9f)
            )
        }
    }

    if (!placeOfBirth.isNullOrBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.testTag("actor_detail_birthplace")
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Luogo di nascita",
                tint = NetflixLightGray,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = placeOfBirth,
                style = MaterialTheme.typography.bodySmall,
                color = NetflixLightGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActorBiographyBlock(
    biographyText: String,
    isLoading: Boolean,
    isBioExpanded: Boolean,
    hasExpandableBio: Boolean,
    bioToggleFocusRequester: FocusRequester,
    onToggleBio: () -> Unit,
    onUpFromBio: () -> Boolean,
    onDownFromBio: () -> Boolean,
    onBioFocus: () -> Unit
) {
    val resolvedBio = when {
        biographyText.isNotBlank() -> biographyText
        isLoading -> "Caricamento biografia…"
        else -> "Biografia non disponibile su TMDB."
    }
    Text(
        text = resolvedBio,
        style = MaterialTheme.typography.bodyMedium,
        color = if (biographyText.isNotBlank()) NetflixWhite.copy(alpha = 0.92f) else NetflixMutedGray,
        maxLines = if (isBioExpanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("actor_detail_biography")
    )
    if (hasExpandableBio) {
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            color = NetflixCardSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NetflixElevatedSurface),
            modifier = Modifier
                .safeFocusRequester(bioToggleFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp -> onUpFromBio()
                            Key.DirectionDown -> onDownFromBio()
                            Key.DirectionLeft, Key.DirectionRight -> true
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .tvFocusableControl(
                    shape = RoundedCornerShape(16.dp),
                    focusedScale = 1.05f,
                    focusedBorderColor = NetflixWhite,
                    focusTag = "actor_bio_toggle_button",
                    onFocusChange = { if (it) onBioFocus() },
                    onClick = onToggleBio
                )
                .testTag("actor_bio_toggle_button")
        ) {
            Text(
                text = if (isBioExpanded) "Mostra meno" else "Leggi di più",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = NetflixWhite,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
