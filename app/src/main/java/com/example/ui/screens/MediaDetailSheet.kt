package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.EpisodeProgressEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.remote.MediaItem
import com.example.data.remote.TmdbCastMemberDto
import com.example.data.remote.TmdbEpisodeDto
import com.example.ui.DetailUiState
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
    onClose: () -> Unit,
    onPlayMain: (restartFromZero: Boolean) -> Unit,
    onPlayEpisode: (season: Int, episode: Int, title: String?) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onToggleWatchlist: () -> Unit,
    onSelectSimilar: (MediaItem) -> Unit
) {
    BackHandler(onBack = onClose)

    val item = state.mediaItem
    val details = state.details

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

            // Info principali + Pulsanti Riproduci / Riprendi
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
