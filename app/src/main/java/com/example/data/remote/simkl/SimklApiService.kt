package com.example.data.remote.simkl

import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SimklApiService {
    @FormUrlEncoded
    @POST("oauth2/device")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String = SimklConfig.DEFAULT_SCOPES
    ): Response<SimklDeviceCodeResponse>

    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun pollDeviceToken(
        @Field("grant_type") grantType: String = SimklConfig.GRANT_TYPE_DEVICE_CODE,
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String
    ): Response<SimklTokenResponse>

    @FormUrlEncoded
    @POST("oauth2/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = SimklConfig.GRANT_TYPE_REFRESH_TOKEN,
        @Field("client_id") clientId: String,
        @Field("refresh_token") refreshToken: String
    ): Response<SimklTokenResponse>

    @FormUrlEncoded
    @POST("oauth2/revoke")
    suspend fun revokeToken(
        @Field("token") token: String,
        @Field("client_id") clientId: String
    ): Response<ResponseBody>

    @GET("users/settings")
    suspend fun getUserSettings(
        @Header("Authorization") bearerToken: String
    ): Response<SimklUserSettingsResponse>

    @GET("sync/activities")
    suspend fun getSyncActivities(
        @Header("Authorization") bearerToken: String
    ): Response<SimklActivitiesResponse>

    @GET("sync/all-items")
    suspend fun getAllItems(
        @Header("Authorization") bearerToken: String,
        @Query("date_from") dateFrom: String? = null,
        @Query("extended") extended: String = "full",
        @Query("episode_watched_at") episodeWatchedAt: String? = "yes",
        @Query("include_all_episodes") includeAllEpisodes: String? = "yes"
    ): Response<ResponseBody>

    @GET("sync/all-items/{type}")
    suspend fun getAllItemsByType(
        @Header("Authorization") bearerToken: String,
        @Path("type") type: String,
        @Query("date_from") dateFrom: String? = null,
        @Query("extended") extended: String = "full",
        @Query("episode_watched_at") episodeWatchedAt: String? = null,
        @Query("include_all_episodes") includeAllEpisodes: String? = null
    ): Response<ResponseBody>

    @GET("sync/all-items/{type}/{status}")
    suspend fun getAllItemsByTypeAndStatus(
        @Header("Authorization") bearerToken: String,
        @Path("type") type: String,
        @Path("status") status: String,
        @Query("date_from") dateFrom: String? = null,
        @Query("extended") extended: String = "full",
        @Query("episode_watched_at") episodeWatchedAt: String? = null,
        @Query("include_all_episodes") includeAllEpisodes: String? = null
    ): Response<ResponseBody>

    @GET("sync/playback/movies")
    suspend fun getPlaybackMovies(
        @Header("Authorization") bearerToken: String
    ): Response<List<SimklPlaybackItemDto>>

    @GET("sync/playback/episodes")
    suspend fun getPlaybackEpisodes(
        @Header("Authorization") bearerToken: String
    ): Response<List<SimklPlaybackItemDto>>

    @DELETE("sync/playback/{id}")
    suspend fun deletePlaybackItem(
        @Header("Authorization") bearerToken: String,
        @Path("id") playbackId: Long
    ): Response<ResponseBody>

    @POST("scrobble/start")
    suspend fun scrobbleStart(
        @Header("Authorization") bearerToken: String,
        @Body body: SimklScrobbleRequest
    ): Response<SimklScrobbleResponse>

    @POST("scrobble/pause")
    suspend fun scrobblePause(
        @Header("Authorization") bearerToken: String,
        @Body body: SimklScrobbleRequest
    ): Response<SimklScrobbleResponse>

    @POST("scrobble/stop")
    suspend fun scrobbleStop(
        @Header("Authorization") bearerToken: String,
        @Query("min_progress") minProgress: Int,
        @Body body: SimklScrobbleRequest
    ): Response<SimklScrobbleResponse>

    companion object {
        fun createOkHttpClient(
            baseHttpClient: OkHttpClient,
            configProvider: () -> SimklConfig
        ): OkHttpClient {
            return baseHttpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val cfg = configProvider()
                    val original = chain.request()
                    val path = original.url.encodedPath
                    val isOAuthEndpoint = path.contains("/oauth2/")
                    val newUrlBuilder = original.url.newBuilder()
                    if (!isOAuthEndpoint && cfg.clientId.isNotBlank()) {
                        if (original.url.queryParameter("client_id") == null) {
                            newUrlBuilder.addQueryParameter("client_id", cfg.clientId)
                        }
                        if (original.url.queryParameter("app-name") == null) {
                            newUrlBuilder.addQueryParameter("app-name", cfg.appName)
                        }
                        if (original.url.queryParameter("app-version") == null) {
                            newUrlBuilder.addQueryParameter("app-version", cfg.appVersion)
                        }
                    }
                    val reqBuilder = original.newBuilder()
                        .url(newUrlBuilder.build())
                        .header("Accept", "application/json")
                        .header("User-Agent", "${cfg.appName}/${cfg.appVersion}")
                    if (!isOAuthEndpoint && cfg.clientId.isNotBlank()) {
                        reqBuilder.header("simkl-api-key", cfg.clientId)
                    }
                    chain.proceed(reqBuilder.build())
                }
                .build()
        }

        fun create(
            okHttpClient: OkHttpClient,
            moshi: Moshi,
            baseUrl: String
        ): SimklApiService {
            val safeBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            return Retrofit.Builder()
                .baseUrl(safeBase)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SimklApiService::class.java)
        }
    }
}
