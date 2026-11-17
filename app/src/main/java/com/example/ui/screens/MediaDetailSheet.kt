package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbCastMemberDto
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbVideoDto
import com.example.data.remote.VixSrcStreamResolver
import com.example.ui.DetailUiState
import com.example.ui.TrailerPlaybackState
import com.example.ui.components.MediaCatalogRow
import com.example.ui.components.formatSecondsToMmSs
import com.example.ui.theme.NetflixBlack
import com.example.ui.theme.NetflixCardSurface
import com.example.ui.theme.NetflixDarkSurface
import com.example.ui.theme.NetflixElevatedSurface
import com.example.ui.theme.NetflixGold
import com.example.ui.theme.NetflixLightGray
import com.example.ui.theme.NetflixMatchGreen
import com.example.ui.theme.NetflixMutedGray
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.NetflixWhite

@Composable
fun MediaDetailScreen(
    state: DetailUiState,
    savedProgress: WatchProgressEntity?,
    isInWatchlist: Boolean,
    vixSrcResolver: VixSrcStreamResolver,
    onClose: () -> Unit,
    onPlayMain: (restartFromZero: Boolean) -> Unit,
    onPlayEpisode: (season: Int, episode: Int, title: String?) -> Unit,
    onPlayTrailer: (TmdbVideoDto) -> Unit,
    onCloseTrailer: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onToggleWatchlist: () -> Unit,
    onSelectSimilar: (MediaItem) -> Unit
) {
    BackHandler {
        if (state.activeTrailer != null) {
            onCloseTrailer()
        } else {
            onClose()
        }
    }

    val item = state.mediaItem
    val details = state.details
    val trailers = details?.trailers.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("media_detail_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // Header con Backdrop
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    AsyncImage(
                        model = item.backdropUrl ?: item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to NetflixBlack.copy(alpha = 0.5f),
                                    0.5f to Color.Transparent,
                                    1f to NetflixBlack
                                )
                            )
                    )

                    // Icona Play grande al centro dell'anteprima
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(NetflixRed.copy(alpha = 0.9f))
                            .clickable { onPlayMain(false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Avvia Streaming",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Pulsante Chiudi in alto a destra
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NetflixBlack.copy(alpha = 0.75f))
                            .testTag("detail_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Chiudi",
                            tint = NetflixWhite
                        )
                    }
                }
            }

            // Info principali + Pulsanti Riproduci / Riprendi / Guarda Trailer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = NetflixRed,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "VIXSRC STREAM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${item.matchPercentage}% Compatibile",
                            style = MaterialTheme.typography.labelLarge,
                            color = NetflixMatchGreen
                        )
                        if (item.releaseYear.isNotBlank()) {
                            Text(
                                text = item.releaseYear,
                                style = MaterialTheme.typography.bodySmall,
                                color = NetflixLightGray
                            )
                        }
                        Surface(
                            color = NetflixElevatedSurface,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "FHD • HLS",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixWhite,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (details?.runtimeMinutes != null && details.runtimeMinutes > 0) {
                            Text(
                                text = "${details.runtimeMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = NetflixLightGray
                            )
                        } else if (details != null && details.numberOfSeasons > 0) {
                            Text(
                                text = "${details.numberOfSeasons} Stagioni",
                                style = MaterialTheme.typography.bodySmall,
                                color = NetflixLightGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = NetflixWhite
                    )

                    if (!details?.tagline.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"${details?.tagline}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NetflixLightGray
                        )
                    }

                    // Barra di avanzamento se presente in Continua a guardare (Room DB)
                    if (savedProgress != null && savedProgress.startSeconds > 0L) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NetflixCardSurface)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val epBadge = if (savedProgress.seasonNumber != null && savedProgress.episodeNumber != null) {
                                    "S${savedProgress.seasonNumber}:E${savedProgress.episodeNumber} • "
                                } else ""
                                Text(
                                    text = "${epBadge}Salvato su Room al minuto ${savedProgress.startMinutes} (${formatSecondsToMmSs(savedProgress.startSeconds)})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NetflixWhite
                                )
                                Text(
                                    text = "startAt=${savedProgress.startSeconds}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixRed
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { savedProgress.progressFraction },
                                color = NetflixRed,
                                trackColor = NetflixElevatedSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Pulsante primario Riproduci / Riprendi (con startAt passato a vixsrc.to)
                    Button(
                        onClick = { onPlayMain(false) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixWhite,
                            contentColor = NetflixBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("detail_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val playLabel = if (savedProgress != null && savedProgress.startSeconds > 0L) {
                            val epPrefix = if (savedProgress.seasonNumber != null && savedProgress.episodeNumber != null) {
                                "S${savedProgress.seasonNumber}:E${savedProgress.episodeNumber} "
                            } else ""
                            "Riprendi ${epPrefix}da ${savedProgress.startMinutes} min (${formatSecondsToMmSs(savedProgress.startSeconds)})"
                        } else if (item.isSeries) {
                            "Riproduci S1:E1"
                        } else {
                            "Riproduci Ora"
                        }
                        Text(
                            text = playLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Pulsante rapido per avviare il Trailer Ufficiale direttamente in-app
                    if (trailers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onPlayTrailer(trailers.first()) },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NetflixRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("detail_watch_trailer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartDisplay,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Guarda Trailer Ufficiale in App (${trailers.first().name})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (savedProgress != null && savedProgress.startSeconds > 0L) {
                            OutlinedButton(
                                onClick = { onPlayMain(true) },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, NetflixElevatedSurface),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = NetflixCardSurface,
                                    contentColor = NetflixWhite
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("detail_restart_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Dall'inizio",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onToggleWatchlist,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, NetflixElevatedSurface),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = NetflixCardSurface,
                                contentColor = NetflixWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("detail_watchlist_button")
                        ) {
                            Icon(
                                imageVector = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isInWatchlist) "Nella Mia Lista" else "Aggiungi alla Lista",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (item.overview.isNotBlank()) {
                        Text(
                            text = item.overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NetflixWhite
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (!details?.genres.isNullOrEmpty()) {
                        Text(
                            text = "Generi: ${details?.genres?.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "TMDB ID: ${item.id} • Endpoint VixSrc: https://vixsrc.to/${item.mediaType}/${item.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixMutedGray
                    )
                }
            }

            // SEZIONE DEDICATA: Trailer & Video Ufficiali da TMDB (/movie/{id}/videos o /tv/{id}/videos)
            if (trailers.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tmdb_trailers_section")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = NetflixRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Trailer & Anteprime Ufficiali (TMDB)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NetflixWhite
                                )
                            }
                            Text(
                                text = "${trailers.size} video",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixMatchGreen
                            )
                        }

                        Text(
                            text = "Seleziona un trailer per guardarlo direttamente in-app prima di avviare lo streaming completo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixLightGray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(trailers, key = { it.id }) { video ->
                                TmdbTrailerCard(
                                    video = video,
                                    fallbackBackdropUrl = item.backdropUrl,
                                    onClick = { onPlayTrailer(video) }
                                )
                            }
                        }
                    }
                }
            }

            // Cast TMDB
            if (!details?.cast.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Cast Principale",
                        style = MaterialTheme.typography.titleMedium,
                        color = NetflixWhite,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(details?.cast.orEmpty(), key = { it.id }) { actor ->
                            CastCard(actor = actor)
                        }
                    }
                }
            }

            // Selettore Stagioni ed Episodi per Serie TV
            if (item.isSeries) {
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Episodi (Streaming diretto VixSrc)",
                            style = MaterialTheme.typography.titleLarge,
                            color = NetflixWhite
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val seasons = details?.seasons.orEmpty()
                        if (seasons.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                seasons.forEach { season ->
                                    val isSelected = state.selectedSeason == season.seasonNumber
                                    Surface(
                                        color = if (isSelected) NetflixRed else NetflixCardSurface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) NetflixRed else NetflixElevatedSurface
                                        ),
                                        modifier = Modifier.clickable {
                                            onSelectSeason(season.seasonNumber)
                                        }
                                    ) {
                                        Text(
                                            text = "Stagione ${season.seasonNumber} (${season.episodeCount})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = NetflixWhite,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (state.isLoadingEpisodes) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NetflixRed)
                        }
                    }
                } else {
                    items(state.episodes, key = { "ep_${it.seasonNumber}_${it.episodeNumber}" }) { ep ->
                        EpisodeRowItem(
                            episode = ep,
                            savedEpProgress = state.episodeProgressMap[ep.episodeNumber],
                            fallbackBackdropUrl = item.backdropUrl,
                            onPlayEpisode = {
                                onPlayEpisode(ep.seasonNumber, ep.episodeNumber, ep.name)
                            }
                        )
                    }
                }
            }

            // Contenuti simili da TMDB
            if (!details?.similar.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    MediaCatalogRow(
                        title = "Titoli Simili su TMDB",
                        items = details?.similar.orEmpty(),
                        onItemClick = onSelectSimilar
                    )
                }
            }
        }

        // Player Trailer In-App (Media3 ExoPlayer) mostrato in sovrimpressione quando l'utente seleziona un trailer
        val currentTrailerState = state.activeTrailer
        if (currentTrailerState != null) {
            InAppTrailerPlayerOverlay(
                mediaItem = item,
                trailerState = currentTrailerState,
                allTrailers = trailers,
                vixSrcResolver = vixSrcResolver,
                onSelectTrailer = onPlayTrailer,
                onStartMainStream = {
                    onCloseTrailer()
                    onPlayMain(false)
                },
                onClose = onCloseTrailer
            )
        }
    }
}

@Composable
private fun TmdbTrailerCard(
    video: TmdbVideoDto,
    fallbackBackdropUrl: String?,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
        border = BorderStroke(1.dp, NetflixElevatedSurface),
        modifier = Modifier
            .width(230.dp)
            .clickable { onClick() }
            .testTag("trailer_card_${video.key}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .background(NetflixDarkSurface)
            ) {
                AsyncImage(
                    model = if (video.isYouTube) video.thumbnailUrl else fallbackBackdropUrl,
                    contentDescription = video.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NetflixBlack.copy(alpha = 0.35f))
                )
                Surface(
                    color = NetflixRed,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = video.typeBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(NetflixRed.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Riproduci Trailer in App",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NetflixWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Riproduzione In-App • ${video.site} (${video.languageCode?.uppercase() ?: "INT"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = NetflixLightGray,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun InAppTrailerPlayerOverlay(
    mediaItem: MediaItem,
    trailerState: TrailerPlaybackState,
    allTrailers: List<TmdbVideoDto>,
    vixSrcResolver: VixSrcStreamResolver,
    onSelectTrailer: (TmdbVideoDto) -> Unit,
    onStartMainStream: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val resolved = trailerState.resolvedTrailer
    var isBuffering by remember(trailerState.video.id) { mutableStateOf(true) }
    var playbackError by remember(trailerState.video.id, trailerState.errorMessage) {
        mutableStateOf(trailerState.errorMessage)
    }

    val okHttpDataSourceFactory = remember(vixSrcResolver, resolved?.referer, resolved?.userAgent) {
        val headers = mapOf(
            "Referer" to (resolved?.referer ?: "https://vixsrc.to/"),
            "Origin" to "https://vixsrc.to"
        )
        OkHttpDataSource.Factory(vixSrcResolver.exoOkHttpClient)
            .setUserAgent(resolved?.userAgent ?: "Mozilla/5.0")
            .setDefaultRequestProperties(headers)
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(resolved?.streamUrl, trailerState.isLoading) {
        if (trailerState.isLoading || resolved == null) return@LaunchedEffect
        playbackError = null
        isBuffering = true

        val uri = Uri.parse(resolved.streamUrl)
        val itemBuilder = ExoMediaItem.Builder().setUri(uri)
        if (resolved.isHls) {
            itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        val mediaSource = if (resolved.isHls) {
            HlsMediaSource.Factory(okHttpDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(itemBuilder.build())
        } else {
            ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
                .createMediaSource(itemBuilder.build())
        }

        exoPlayer.setMediaSource(mediaSource)
        if (resolved.clipStartMs > 0L) {
            exoPlayer.seekTo(resolved.clipStartMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = "Impossibile riprodurre questo trailer (${error.errorCodeName})."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack.copy(alpha = 0.94f))
            .clickable(enabled = true) { /* blocca click sottostanti */ }
            .testTag("in_app_trailer_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NetflixCardSurface),
            border = BorderStroke(1.5.dp, NetflixRed),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header del Player Trailer In-App
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = NetflixRed,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = trailerState.video.typeBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = mediaItem.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = NetflixMatchGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = trailerState.video.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = NetflixWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_trailer_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Chiudi Trailer",
                            tint = NetflixWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Area Video Media3 ExoPlayer In-App
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { view ->
                            view.player = exoPlayer
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (trailerState.isLoading || isBuffering) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NetflixBlack.copy(alpha = 0.82f))
                                .padding(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = NetflixRed,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Caricamento Trailer In-App: ${trailerState.video.name}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (!playbackError.isNullOrBlank()) {
                        Text(
                            text = playbackError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixRed,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (resolved != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = resolved.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray
                    )
                }

                // Selettore rapido altri trailer dello stesso film/serie TV
                if (allTrailers.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Altri Trailer & Video disponibili:",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixLightGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allTrailers, key = { "switch_${it.id}" }) { vid ->
                            val isSelected = vid.id == trailerState.video.id
                            Surface(
                                color = if (isSelected) NetflixRed else NetflixDarkSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) NetflixRed else NetflixElevatedSurface
                                ),
                                modifier = Modifier.clickable { onSelectTrailer(vid) }
                            ) {
                                Text(
                                    text = vid.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .width(150.dp)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pulsante per passare subito dal Trailer allo Streaming Principale
                Button(
                    onClick = onStartMainStream,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixWhite,
                        contentColor = NetflixBlack
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("start_main_stream_from_trailer_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (mediaItem.isSeries) {
                            "Avvia Streaming Serie TV Completo"
                        } else {
                            "Avvia Streaming Film Completo"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CastCard(actor: TmdbCastMemberDto) {
    Column(
        modifier = Modifier.width(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = actor.profileUrl,
            contentDescription = actor.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(NetflixCardSurface)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actor.name,
            style = MaterialTheme.typography.labelSmall,
            color = NetflixWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!actor.character.isNullOrBlank()) {
            Text(
                text = actor.character,
                style = MaterialTheme.typography.labelSmall,
                color = NetflixMutedGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EpisodeRowItem(
    episode: TmdbEpisodeDto,
    savedEpProgress: EpisodeProgressEntity?,
    fallbackBackdropUrl: String?,
    onPlayEpisode: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = NetflixDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onPlayEpisode() }
            .testTag("episode_card_${episode.seasonNumber}_${episode.episodeNumber}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(132.dp)
                        .height(76.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NetflixCardSurface)
                ) {
                    AsyncImage(
                        model = episode.stillUrl ?: fallbackBackdropUrl,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(NetflixBlack.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Riproduci episodio",
                            tint = NetflixWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (savedEpProgress != null && savedEpProgress.startSeconds > 0L) {
                        LinearProgressIndicator(
                            progress = { savedEpProgress.progressFraction },
                            color = NetflixRed,
                            trackColor = NetflixElevatedSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${episode.episodeNumber}. ${episode.name ?: "Episodio ${episode.episodeNumber}"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = NetflixWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (episode.runtime != null && episode.runtime > 0) {
                            Text(
                                text = "${episode.runtime}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixLightGray
                            )
                        }
                        if (episode.voteAverage != null && episode.voteAverage > 0.0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = NetflixGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = String.format("%.1f", episode.voteAverage),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixLightGray
                                )
                            }
                        }
                        if (savedEpProgress != null && savedEpProgress.startSeconds > 0L) {
                            Text(
                                text = "• Da ${formatSecondsToMmSs(savedEpProgress.startSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NetflixRed
                            )
                        }
                    }
                }
            }

            if (!episode.overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = NetflixLightGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
