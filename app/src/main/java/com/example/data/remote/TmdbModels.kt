package com.example.data.remote

import androidx.compose.runtime.Immutable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbPagedResponse<T>(
    @param:Json(name = "page") val page: Int = 1,
    @param:Json(name = "results") val results: List<T> = emptyList(),
    @param:Json(name = "total_pages") val totalPages: Int = 1,
    @param:Json(name = "total_results") val totalResults: Int = 0
)

@JsonClass(generateAdapter = true)
data class TmdbMediaItemDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "media_type") val mediaType: String? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "original_title") val originalTitle: String? = null,
    @param:Json(name = "original_name") val originalName: String? = null,
    @param:Json(name = "overview") val overview: String? = null,
    @param:Json(name = "poster_path") val posterPath: String? = null,
    @param:Json(name = "backdrop_path") val backdropPath: String? = null,
    @param:Json(name = "vote_average") val voteAverage: Double? = null,
    @param:Json(name = "vote_count") val voteCount: Int? = null,
    @param:Json(name = "release_date") val releaseDate: String? = null,
    @param:Json(name = "first_air_date") val firstAirDate: String? = null,
    @param:Json(name = "genre_ids") val genreIds: List<Int>? = null,
    @param:Json(name = "popularity") val popularity: Double? = null,
    @param:Json(name = "department") val department: String? = null,
    @param:Json(name = "job") val job: String? = null,
    @param:Json(name = "known_for_department") val knownForDepartment: String? = null,
    @param:Json(name = "known_for") val knownFor: List<TmdbMediaItemDto>? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class MediaItem(
    val id: Int,
    val mediaType: String, // "movie" or "tv"
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseYear: String,
    val genreIds: List<Int> = emptyList()
) {
    val uniqueKey: String get() = "${mediaType}_${id}"

    val posterUrl: String?
        get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            ?: backdropPath?.let { "https://image.tmdb.org/t/p/w500$it" }

    val backdropUrl: String?
        get() = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
            ?: posterPath?.let { "https://image.tmdb.org/t/p/w780$it" }

    val matchPercentage: Int
        get() = ((voteAverage * 10).toInt()).coerceIn(68, 99)

    val isSeries: Boolean
        get() = mediaType == "tv"
}

fun TmdbMediaItemDto.toDomain(defaultMediaType: String = "movie"): MediaItem? {
    val resolvedType = when (mediaType) {
        "movie", "tv" -> mediaType
        "person" -> return null
        else -> if (!firstAirDate.isNullOrBlank() || (!name.isNullOrBlank() && title.isNullOrBlank())) "tv" else defaultMediaType
    }
    val resolvedTitle = (title ?: name ?: originalTitle ?: originalName)?.trim().orEmpty()
    if (resolvedTitle.isBlank() || (posterPath == null && backdropPath == null)) return null
    val rawDate = releaseDate ?: firstAirDate ?: ""
    val year = if (rawDate.length >= 4) rawDate.take(4) else ""
    return MediaItem(
        id = id,
        mediaType = resolvedType,
        title = resolvedTitle,
        overview = overview?.trim().orEmpty(),
        posterPath = posterPath,
        backdropPath = backdropPath,
        voteAverage = voteAverage ?: 0.0,
        releaseYear = year,
        genreIds = genreIds ?: emptyList()
    )
}

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbGenreDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbGenresResponse(
    @param:Json(name = "genres") val genres: List<TmdbGenreDto> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbCastMemberDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "name") val name: String,
    @param:Json(name = "character") val character: String? = null,
    @param:Json(name = "profile_path") val profilePath: String? = null
) {
    val profileUrl: String?
        get() = profilePath?.let { "https://image.tmdb.org/t/p/w300$it" }
}

@JsonClass(generateAdapter = true)
data class TmdbCreditsDto(
    @param:Json(name = "cast") val cast: List<TmdbCastMemberDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbPersonSearchDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "name") val name: String = "",
    @param:Json(name = "known_for_department") val knownForDepartment: String? = null,
    @param:Json(name = "popularity") val popularity: Double? = null,
    @param:Json(name = "profile_path") val profilePath: String? = null,
    @param:Json(name = "known_for") val knownFor: List<TmdbMediaItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbPersonCombinedCreditsDto(
    @param:Json(name = "id") val id: Int? = null,
    @param:Json(name = "cast") val cast: List<TmdbMediaItemDto> = emptyList(),
    @param:Json(name = "crew") val crew: List<TmdbMediaItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbKeywordDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "name") val name: String = ""
)

@JsonClass(generateAdapter = true)
data class TmdbCompanyDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "name") val name: String = ""
)

@Immutable
data class ExtendedSearchResult(
    val items: List<MediaItem> = emptyList(),
    val matchedCategories: List<String> = emptyList()
)

@Immutable
data class MatchedSearchGenre(
    val displayName: String,
    val movieGenreId: Int?,
    val tvGenreId: Int?,
    val matchedAlias: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbSeasonSummaryDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "season_number") val seasonNumber: Int,
    @param:Json(name = "name") val name: String,
    @param:Json(name = "episode_count") val episodeCount: Int = 0,
    @param:Json(name = "poster_path") val posterPath: String? = null,
    @param:Json(name = "air_date") val airDate: String? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbVideoDto(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "key") val key: String,
    @param:Json(name = "name") val name: String,
    @param:Json(name = "site") val site: String = "YouTube",
    @param:Json(name = "type") val type: String = "Trailer",
    @param:Json(name = "official") val official: Boolean = false,
    @param:Json(name = "iso_639_1") val languageCode: String? = null
) {
    val isYouTube: Boolean
        get() = site.equals("YouTube", ignoreCase = true) && key.isNotBlank()

    val thumbnailUrl: String
        get() = "https://i.ytimg.com/vi/$key/hqdefault.jpg"

    val typeBadge: String
        get() = when (type.lowercase()) {
            "trailer" -> if (languageCode.equals("it", ignoreCase = true)) "TRAILER ITA" else "TRAILER UFFICIALE"
            "teaser" -> "TEASER"
            "clip" -> "CLIP VIDEO"
            "behind the scenes" -> "BACKSTAGE"
            "featurette" -> "FEATURETTE"
            else -> type.uppercase()
        }
}

@JsonClass(generateAdapter = true)
data class TmdbVideosResponse(
    @param:Json(name = "id") val id: Int? = null,
    @param:Json(name = "results") val results: List<TmdbVideoDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbMovieDetailsDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "overview") val overview: String? = null,
    @param:Json(name = "poster_path") val posterPath: String? = null,
    @param:Json(name = "backdrop_path") val backdropPath: String? = null,
    @param:Json(name = "vote_average") val voteAverage: Double? = null,
    @param:Json(name = "release_date") val releaseDate: String? = null,
    @param:Json(name = "runtime") val runtime: Int? = null,
    @param:Json(name = "tagline") val tagline: String? = null,
    @param:Json(name = "genres") val genres: List<TmdbGenreDto> = emptyList(),
    @param:Json(name = "credits") val credits: TmdbCreditsDto? = null,
    @param:Json(name = "similar") val similar: TmdbPagedResponse<TmdbMediaItemDto>? = null,
    @param:Json(name = "videos") val videos: TmdbVideosResponse? = null
)

@JsonClass(generateAdapter = true)
data class TmdbTvDetailsDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "overview") val overview: String? = null,
    @param:Json(name = "poster_path") val posterPath: String? = null,
    @param:Json(name = "backdrop_path") val backdropPath: String? = null,
    @param:Json(name = "vote_average") val voteAverage: Double? = null,
    @param:Json(name = "first_air_date") val firstAirDate: String? = null,
    @param:Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @param:Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @param:Json(name = "tagline") val tagline: String? = null,
    @param:Json(name = "genres") val genres: List<TmdbGenreDto> = emptyList(),
    @param:Json(name = "seasons") val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    @param:Json(name = "credits") val credits: TmdbCreditsDto? = null,
    @param:Json(name = "similar") val similar: TmdbPagedResponse<TmdbMediaItemDto>? = null,
    @param:Json(name = "videos") val videos: TmdbVideosResponse? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbEpisodeDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "episode_number") val episodeNumber: Int,
    @param:Json(name = "season_number") val seasonNumber: Int,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "overview") val overview: String? = null,
    @param:Json(name = "still_path") val stillPath: String? = null,
    @param:Json(name = "runtime") val runtime: Int? = null,
    @param:Json(name = "air_date") val airDate: String? = null,
    @param:Json(name = "vote_average") val voteAverage: Double? = null
) {
    val stillUrl: String?
        get() = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetailsDto(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "season_number") val seasonNumber: Int,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "episodes") val episodes: List<TmdbEpisodeDto> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class MediaDetails(
    val item: MediaItem,
    val tagline: String,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val cast: List<TmdbCastMemberDto>,
    val similar: List<MediaItem>,
    val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    val numberOfSeasons: Int = 0,
    val trailers: List<TmdbVideoDto> = emptyList()
)

object TmdbGenreMap {
    val defaultMovieGenres: List<TmdbGenreDto> = listOf(
        TmdbGenreDto(28, "Azione"),
        TmdbGenreDto(12, "Avventura"),
        TmdbGenreDto(16, "Animazione"),
        TmdbGenreDto(35, "Commedia"),
        TmdbGenreDto(80, "Crime"),
        TmdbGenreDto(99, "Documentario"),
        TmdbGenreDto(18, "Dramma"),
        TmdbGenreDto(10751, "Famiglia"),
        TmdbGenreDto(14, "Fantasy"),
        TmdbGenreDto(36, "Storia"),
        TmdbGenreDto(27, "Horror"),
        TmdbGenreDto(10402, "Musica"),
        TmdbGenreDto(9648, "Mistero"),
        TmdbGenreDto(10749, "Romantico"),
        TmdbGenreDto(878, "Fantascienza"),
        TmdbGenreDto(53, "Thriller"),
        TmdbGenreDto(10752, "Guerra"),
        TmdbGenreDto(37, "Western")
    )

    private val genres = defaultMovieGenres.associate { it.id to it.name } + mapOf(
        10759 to "Action & Adventure",
        10765 to "Sci-Fi & Fantasy",
        10768 to "War & Politics"
    )

    private data class GenreSearchDefinition(
        val displayName: String,
        val movieGenreId: Int?,
        val tvGenreId: Int?,
        val aliases: List<String>
    )

    private val searchGenreDefinitions: List<GenreSearchDefinition> = listOf(
        GenreSearchDefinition("Azione", 28, 10759, listOf("azione", "action")),
        GenreSearchDefinition("Avventura", 12, 10759, listOf("avventura", "adventure")),
        GenreSearchDefinition("Animazione", 16, 16, listOf("animazione", "animation", "anime", "cartoni", "cartone")),
        GenreSearchDefinition("Commedia", 35, 35, listOf("commedia", "commedie", "comedy", "comico", "comici")),
        GenreSearchDefinition("Crime", 80, 80, listOf("crime", "crimine", "poliziesco", "polizieschi", "gangster", "mafia")),
        GenreSearchDefinition("Documentario", 99, 99, listOf("documentario", "documentari", "documentary")),
        GenreSearchDefinition("Dramma", 18, 18, listOf("dramma", "drammatico", "drammatici", "drama")),
        GenreSearchDefinition("Famiglia", 10751, 10751, listOf("famiglia", "family")),
        GenreSearchDefinition("Fantasy", 14, 10765, listOf("fantasy", "fantastico", "fantastici")),
        GenreSearchDefinition("Storia", 36, null, listOf("storia", "storico", "storici", "history", "historical")),
        GenreSearchDefinition("Horror", 27, null, listOf("horror", "orrore", "terrore", "paura")),
        GenreSearchDefinition("Musica", 10402, null, listOf("musica", "musicale", "musicali", "musical", "music")),
        GenreSearchDefinition("Mistero", 9648, 9648, listOf("mistero", "giallo", "gialli", "mystery")),
        GenreSearchDefinition("Romantico", 10749, null, listOf("romantico", "romantici", "romance", "amore", "sentimentale")),
        GenreSearchDefinition("Fantascienza", 878, 10765, listOf("fantascienza", "sci-fi", "scifi", "science fiction")),
        GenreSearchDefinition("Thriller", 53, null, listOf("thriller", "suspense")),
        GenreSearchDefinition("Guerra", 10752, 10768, listOf("guerra", "war", "bellico", "bellici")),
        GenreSearchDefinition("Western", 37, 37, listOf("western", "cowboy"))
    )

    fun namesFor(ids: List<Int>): List<String> = ids.mapNotNull { genres[it] }.take(3)

    /**
     * Riconosce generi cinematografici e televisivi (IT/EN/sinonimi) all'interno di una query di ricerca libera,
     * es. "fantascienza", "horror", "azione 2024", "sci-fi", "anime".
     */
    fun resolveSearchGenres(rawQuery: String): List<MatchedSearchGenre> {
        val normalized = rawQuery.lowercase().trim()
        if (normalized.length < 3) return emptyList()
        val tokens = normalized
            .split(Regex("[\\s,;:/]+"))
            .filter { it.isNotBlank() }
            .toSet()

        val matched = mutableListOf<MatchedSearchGenre>()
        for (def in searchGenreDefinitions) {
            val alias = def.aliases.firstOrNull { candidate ->
                if (candidate.contains(' ')) {
                    normalized.contains(candidate)
                } else {
                    candidate in tokens || normalized == candidate
                }
            }
            if (alias != null) {
                matched.add(
                    MatchedSearchGenre(
                        displayName = def.displayName,
                        movieGenreId = def.movieGenreId,
                        tvGenreId = def.tvGenreId,
                        matchedAlias = alias
                    )
                )
            }
        }
        return matched
    }
}

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbWatchProviderDto(
    @param:Json(name = "provider_id") val providerId: Int,
    @param:Json(name = "provider_name") val providerName: String,
    @param:Json(name = "logo_path") val logoPath: String? = null,
    @param:Json(name = "display_priority") val displayPriority: Int = 999
) {
    val logoUrl: String?
        get() = logoPath?.takeIf { it.isNotBlank() }?.let { path ->
            if (path.startsWith("http")) path else "https://image.tmdb.org/t/p/w300$path"
        }

    val displayName: String
        get() = TmdbWatchProviderMap.cleanDisplayName(providerId, providerName)

    val shortBadge: String
        get() = TmdbWatchProviderMap.shortBadgeFor(providerId, displayName)

    val brandColorHex: Long
        get() = TmdbWatchProviderMap.brandColorFor(providerId)
}

@JsonClass(generateAdapter = true)
data class TmdbWatchProvidersResponse(
    @param:Json(name = "results") val results: List<TmdbWatchProviderDto> = emptyList()
)

object TmdbWatchProviderMap {
    const val ID_DISNEY_PLUS = 337
    const val ID_PARAMOUNT_PLUS = 531
    const val ID_PRIME_VIDEO = 119
    const val ID_NETFLIX = 8
    const val ID_APPLE_TV_PLUS = 350
    const val ID_NOW = 39
    const val ID_CRUNCHYROLL = 283
    const val ID_RAKUTEN_TV = 35
    const val ID_MEDIASET_INFINITY = 359
    const val ID_MUBI = 11

    /**
     * Ordine prioritario richiesto nella Home:
     * Disney+, Paramount+, Prime Video, Netflix, seguiti dalle altre piattaforme principali TMDB Italia.
     */
    val priorityOrderIds: List<Int> = listOf(
        ID_DISNEY_PLUS,
        ID_PARAMOUNT_PLUS,
        ID_PRIME_VIDEO,
        ID_NETFLIX,
        ID_APPLE_TV_PLUS,
        ID_NOW,
        ID_CRUNCHYROLL,
        ID_RAKUTEN_TV,
        ID_MEDIASET_INFINITY,
        ID_MUBI
    )

    val defaultProviders: List<TmdbWatchProviderDto> = listOf(
        TmdbWatchProviderDto(
            providerId = ID_DISNEY_PLUS,
            providerName = "Disney+",
            logoPath = "/97yvRBw1GzX7fXprcF80er19ot.jpg",
            displayPriority = 1
        ),
        TmdbWatchProviderDto(
            providerId = ID_PARAMOUNT_PLUS,
            providerName = "Paramount+",
            logoPath = "/h5DcR0J2EESLitnhR8xLG1QymTE.jpg",
            displayPriority = 2
        ),
        TmdbWatchProviderDto(
            providerId = ID_PRIME_VIDEO,
            providerName = "Prime Video",
            logoPath = "/dQeAar5H991VYporEjUspolDarG.jpg",
            displayPriority = 3
        ),
        TmdbWatchProviderDto(
            providerId = ID_NETFLIX,
            providerName = "Netflix",
            logoPath = "/pbpMk2JmcoNnQwx5JGpXngfoWtp.jpg",
            displayPriority = 4
        ),
        TmdbWatchProviderDto(
            providerId = ID_APPLE_TV_PLUS,
            providerName = "Apple TV+",
            logoPath = "/2E03IAZsX4ZaUqM7tXlctEPMGWS.jpg",
            displayPriority = 5
        ),
        TmdbWatchProviderDto(
            providerId = ID_NOW,
            providerName = "NOW",
            logoPath = "/g0E9h3JAeIwmdvxlT73jiEuxdNj.jpg",
            displayPriority = 6
        ),
        TmdbWatchProviderDto(
            providerId = ID_CRUNCHYROLL,
            providerName = "Crunchyroll",
            logoPath = "/mXeC4TrcgdU6ltE9bCBCEORwSQR.jpg",
            displayPriority = 7
        ),
        TmdbWatchProviderDto(
            providerId = ID_RAKUTEN_TV,
            providerName = "Rakuten TV",
            logoPath = "/bZvc9dXrXNly7cA0V4D9pR8yJwm.jpg",
            displayPriority = 8
        ),
        TmdbWatchProviderDto(
            providerId = ID_MEDIASET_INFINITY,
            providerName = "Mediaset Infinity",
            logoPath = "/cDzkhg6Sr4GW2aRdV22uDuRrQqZ.jpg",
            displayPriority = 9
        ),
        TmdbWatchProviderDto(
            providerId = ID_MUBI,
            providerName = "MUBI",
            logoPath = "/fj9Y8i4Qr3TF4A1L96t7b29sW5M.jpg",
            displayPriority = 10
        )
    )

    fun cleanDisplayName(providerId: Int, rawName: String): String = when (providerId) {
        ID_DISNEY_PLUS -> "Disney+"
        ID_PARAMOUNT_PLUS -> "Paramount+"
        ID_PRIME_VIDEO, 9 -> "Prime Video"
        ID_NETFLIX -> "Netflix"
        ID_APPLE_TV_PLUS, 2 -> "Apple TV+"
        ID_NOW, 29 -> "NOW"
        ID_CRUNCHYROLL -> "Crunchyroll"
        ID_RAKUTEN_TV -> "Rakuten TV"
        ID_MEDIASET_INFINITY -> "Infinity+"
        ID_MUBI -> "MUBI"
        else -> rawName
            .replace("Amazon Prime Video", "Prime Video", ignoreCase = true)
            .replace("Disney Plus", "Disney+", ignoreCase = true)
            .replace("Paramount Plus", "Paramount+", ignoreCase = true)
            .replace("Apple TV Plus", "Apple TV+", ignoreCase = true)
            .trim()
    }

    fun shortBadgeFor(providerId: Int, displayName: String): String = when (providerId) {
        ID_DISNEY_PLUS -> "D+"
        ID_PARAMOUNT_PLUS -> "P+"
        ID_PRIME_VIDEO, 9 -> "PV"
        ID_NETFLIX -> "N"
        ID_APPLE_TV_PLUS, 2 -> "TV+"
        ID_NOW -> "NOW"
        ID_CRUNCHYROLL -> "CR"
        ID_RAKUTEN_TV -> "R"
        ID_MEDIASET_INFINITY -> "INF"
        ID_MUBI -> "M"
        else -> displayName.take(2).uppercase()
    }

    fun brandColorFor(providerId: Int): Long = when (providerId) {
        ID_DISNEY_PLUS -> 0xFF0063E5L
        ID_PARAMOUNT_PLUS -> 0xFF0064FFL
        ID_PRIME_VIDEO, 9 -> 0xFF00A8E1L
        ID_NETFLIX -> 0xFFE50914L
        ID_APPLE_TV_PLUS, 2 -> 0xFF8E8E93L
        ID_NOW -> 0xFF00D69EL
        ID_CRUNCHYROLL -> 0xFFF47521L
        ID_RAKUTEN_TV -> 0xFFBF0000L
        ID_MEDIASET_INFINITY -> 0xFF7B2CBFL
        ID_MUBI -> 0xFF0038A8L
        else -> 0xFFE50914L
    }

    /**
     * Restituisce gli ID provider TMDB da interrogare su `/discover/movie` e `/discover/tv`
     * (includendo eventuali alias regionali TMDB separati da pipe OR `|`).
     */
    fun providerQueryExpression(providerId: Int): String = when (providerId) {
        ID_PRIME_VIDEO, 9 -> "119|9"
        ID_PARAMOUNT_PLUS -> "531|582"
        ID_APPLE_TV_PLUS -> "350|2"
        ID_NOW -> "39|29"
        else -> providerId.toString()
    }

    /**
     * Unisce i provider live restituiti da TMDB con le piattaforme principali, aggiornando
     * i `logo_path` ufficiali TMDB e mantenendo in testa Disney+, Paramount+, Prime Video e Netflix.
     */
    fun mergeAndSortProviders(liveProviders: List<TmdbWatchProviderDto>): List<TmdbWatchProviderDto> {
        if (liveProviders.isEmpty()) return defaultProviders

        val liveById = liveProviders
            .filter { it.providerId > 0 && it.providerName.isNotBlank() }
            .associateBy { provider ->
                if (provider.providerId == 9) ID_PRIME_VIDEO else provider.providerId
            }

        val prioritized = defaultProviders.map { fallback ->
            val live = liveById[fallback.providerId]
            if (live != null) {
                fallback.copy(
                    providerName = cleanDisplayName(fallback.providerId, live.providerName),
                    logoPath = live.logoPath?.takeIf { it.isNotBlank() } ?: fallback.logoPath
                )
            } else {
                fallback
            }
        }

        val prioritizedIds = prioritized.map { it.providerId }.toSet()
        val extraFromTmdb = liveProviders
            .filter { candidate ->
                val normId = if (candidate.providerId == 9) ID_PRIME_VIDEO else candidate.providerId
                normId !in prioritizedIds &&
                    !candidate.providerName.contains("Channel", ignoreCase = true) &&
                    !candidate.providerName.contains("With Ads", ignoreCase = true) &&
                    !candidate.logoPath.isNullOrBlank()
            }
            .sortedBy { it.displayPriority }
            .distinctBy { cleanDisplayName(it.providerId, it.providerName).lowercase() }
            .take(6)

        return prioritized + extraFromTmdb
    }
}

