package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {
    @GET("trending/all/day")
    suspend fun getTrendingAllDay(
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("trending/movie/week")
    suspend fun getTrendingMoviesWeek(
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("trending/tv/week")
    suspend fun getTrendingTvWeek(
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("tv/popular")
    suspend fun getPopularTv(
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("tv/top_rated")
    suspend fun getTopRatedTv(
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("genre/movie/list")
    suspend fun getMovieGenres(
        @Query("language") language: String = "it-IT"
    ): TmdbGenresResponse

    @GET("discover/movie")
    suspend fun discoverMoviesByGenre(
        @Query("with_genres") genreId: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("discover/tv")
    suspend fun discoverTvByGenre(
        @Query("with_genres") genreId: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = "it-IT",
        @Query("append_to_response") appendToResponse: String = "credits,similar,videos",
        @Query("include_video_language") includeVideoLanguage: String = "it,en,null"
    ): TmdbMovieDetailsDto

    @GET("collection/{collection_id}")
    suspend fun getCollectionDetails(
        @Path("collection_id") collectionId: Int,
        @Query("language") language: String = "it-IT"
    ): TmdbCollectionDetailsDto

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("language") language: String = "it-IT",
        @Query("append_to_response") appendToResponse: String = "credits,similar,videos",
        @Query("include_video_language") includeVideoLanguage: String = "it,en,null"
    ): TmdbTvDetailsDto

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = "it-IT",
        @Query("include_video_language") includeVideoLanguage: String = "it,en,null"
    ): TmdbVideosResponse

    @GET("tv/{tv_id}/videos")
    suspend fun getTvVideos(
        @Path("tv_id") tvId: Int,
        @Query("language") language: String = "it-IT",
        @Query("include_video_language") includeVideoLanguage: String = "it,en,null"
    ): TmdbVideosResponse

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("language") language: String = "it-IT"
    ): TmdbSeasonDetailsDto

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = "it-IT",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("search/person")
    suspend fun searchPerson(
        @Query("query") query: String,
        @Query("language") language: String = "it-IT",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbPersonSearchDto>

    @GET("person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Query("language") language: String = "it-IT"
    ): TmdbPersonDetailsDto

    @GET("person/{person_id}/combined_credits")
    suspend fun getPersonCombinedCredits(
        @Path("person_id") personId: Int,
        @Query("language") language: String = "it-IT"
    ): TmdbPersonCombinedCreditsDto

    @GET("search/keyword")
    suspend fun searchKeywords(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbKeywordDto>

    @GET("search/company")
    suspend fun searchCompanies(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbCompanyDto>

    @GET("discover/movie")
    suspend fun discoverMoviesExtended(
        @Query("with_genres") genreIds: String? = null,
        @Query("with_keywords") keywordIds: String? = null,
        @Query("with_companies") companyIds: String? = null,
        @Query("primary_release_year") releaseYear: Int? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("discover/tv")
    suspend fun discoverTvExtended(
        @Query("with_genres") genreIds: String? = null,
        @Query("with_keywords") keywordIds: String? = null,
        @Query("with_companies") companyIds: String? = null,
        @Query("first_air_date_year") firstAirDateYear: Int? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("watch/providers/movie")
    suspend fun getMovieWatchProviders(
        @Query("language") language: String = "it-IT",
        @Query("watch_region") watchRegion: String = "IT"
    ): TmdbWatchProvidersResponse

    @GET("watch/providers/tv")
    suspend fun getTvWatchProviders(
        @Query("language") language: String = "it-IT",
        @Query("watch_region") watchRegion: String = "IT"
    ): TmdbWatchProvidersResponse

    @GET("discover/movie")
    suspend fun discoverMoviesByProvider(
        @Query("with_watch_providers") providerIds: String,
        @Query("watch_region") watchRegion: String = "IT",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>

    @GET("discover/tv")
    suspend fun discoverTvByProvider(
        @Query("with_watch_providers") providerIds: String,
        @Query("watch_region") watchRegion: String = "IT",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItemDto>
}

