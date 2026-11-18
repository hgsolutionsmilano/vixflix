package com.example.data.remote

import com.squareup.moshi.Json

data class TmdbPagedResponse<T>(
    @Json(name = "page") val page: Int = 1,
    @Json(name = "results") val results: List<T> = emptyList(),
    @Json(name = "total_pages") val totalPages: Int = 1,
    @Json(name = "total_results") val totalResults: Int = 0
)

data class TmdbMediaItemDto(
    @Json(name = "id") val id: Int,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null,
    @Json(name = "popularity") val popularity: Double? = null
)

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

data class TmdbGenreDto(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String
)

data class TmdbGenresResponse(
    @Json(name = "genres") val genres: List<TmdbGenreDto> = emptyList()
)

data class TmdbCastMemberDto(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "character") val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null
) {
    val profileUrl: String?
        get() = profilePath?.let { "https://image.tmdb.org/t/p/w300$it" }
}

data class TmdbCreditsDto(
    @Json(name = "cast") val cast: List<TmdbCastMemberDto> = emptyList()
)

data class TmdbSeasonSummaryDto(
    @Json(name = "id") val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "name") val name: String,
    @Json(name = "episode_count") val episodeCount: Int = 0,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "air_date") val airDate: String? = null
)

data class TmdbVideoDto(
    @Json(name = "id") val id: String,
    @Json(name = "key") val key: String,
    @Json(name = "name") val name: String,
    @Json(name = "site") val site: String = "YouTube",
    @Json(name = "type") val type: String = "Trailer",
    @Json(name = "official") val official: Boolean = false,
    @Json(name = "iso_639_1") val languageCode: String? = null
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

data class TmdbVideosResponse(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "results") val results: List<TmdbVideoDto> = emptyList()
)

data class TmdbMovieDetailsDto(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "runtime") val runtime: Int? = null,
    @Json(name = "tagline") val tagline: String? = null,
    @Json(name = "genres") val genres: List<TmdbGenreDto> = emptyList(),
    @Json(name = "credits") val credits: TmdbCreditsDto? = null,
    @Json(name = "similar") val similar: TmdbPagedResponse<TmdbMediaItemDto>? = null,
    @Json(name = "videos") val videos: TmdbVideosResponse? = null
)

data class TmdbTvDetailsDto(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @Json(name = "tagline") val tagline: String? = null,
    @Json(name = "genres") val genres: List<TmdbGenreDto> = emptyList(),
    @Json(name = "seasons") val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    @Json(name = "credits") val credits: TmdbCreditsDto? = null,
    @Json(name = "similar") val similar: TmdbPagedResponse<TmdbMediaItemDto>? = null,
    @Json(name = "videos") val videos: TmdbVideosResponse? = null
)

data class TmdbEpisodeDto(
    @Json(name = "id") val id: Int,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "still_path") val stillPath: String? = null,
    @Json(name = "runtime") val runtime: Int? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null
) {
    val stillUrl: String?
        get() = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

data class TmdbSeasonDetailsDto(
    @Json(name = "id") val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "episodes") val episodes: List<TmdbEpisodeDto> = emptyList()
)

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

    fun namesFor(ids: List<Int>): List<String> = ids.mapNotNull { genres[it] }.take(3)
}
