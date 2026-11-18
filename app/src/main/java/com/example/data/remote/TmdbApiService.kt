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
}
