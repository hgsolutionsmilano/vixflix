# ==============================================================================
# VixFlix Release R8 / ProGuard Rules
# Ottimizzate per Android TV / Fire TV Stick (codegen zero-reflection)
# ==============================================================================

-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------------
# 1. WebView @JavascriptInterface (YouTube IFrame Trailer Bridge - CRITICO)
# ------------------------------------------------------------------------------
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.example.ui.screens.YouTubeEmbedBridge { *; }
-keepclassmembers class com.example.ui.screens.YouTubeEmbedBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# ------------------------------------------------------------------------------
# 2. Moshi KSP Codegen (Zero Reflection)
# ------------------------------------------------------------------------------
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class * extends com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}
-keep class com.example.data.remote.**JsonAdapter { *; }
-keep class com.example.data.repository.**JsonAdapter { *; }
-keep class com.example.data.remote.TmdbPagedResponse { *; }
-keep class com.example.data.remote.TmdbMediaItemDto { *; }
-keep class com.example.data.remote.MediaItem { *; }
-keep class com.example.data.remote.TmdbGenreDto { *; }
-keep class com.example.data.remote.TmdbGenresResponse { *; }
-keep class com.example.data.remote.TmdbCastMemberDto { *; }
-keep class com.example.data.remote.TmdbCreditsDto { *; }
-keep class com.example.data.remote.TmdbSeasonSummaryDto { *; }
-keep class com.example.data.remote.TmdbVideoDto { *; }
-keep class com.example.data.remote.TmdbVideosResponse { *; }
-keep class com.example.data.remote.TmdbMovieDetailsDto { *; }
-keep class com.example.data.remote.TmdbTvDetailsDto { *; }
-keep class com.example.data.remote.TmdbEpisodeDto { *; }
-keep class com.example.data.remote.TmdbSeasonDetailsDto { *; }
-keep class com.example.data.remote.MediaDetails { *; }
-keep class com.example.data.remote.TmdbWatchProviderDto { *; }
-keep class com.example.data.remote.TmdbWatchProvidersResponse { *; }
-keep class com.example.data.remote.SubtitleTrack { *; }
-keep class com.example.data.remote.simkl.**Dto { *; }
-keep class com.example.data.remote.simkl.**Response { *; }
-keep class com.example.data.remote.simkl.**Request { *; }
-keep class com.example.data.repository.CatalogFeed { *; }

# ------------------------------------------------------------------------------
# 3. Retrofit 2 & Kotlin Coroutines
# ------------------------------------------------------------------------------
-keep interface com.example.data.remote.TmdbApiService { *; }
-keep interface com.example.data.remote.simkl.SimklApiService { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation class ** extends <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.Platform$Java8
-dontwarn kotlin.Unit
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ------------------------------------------------------------------------------
# 4. Room Database (VixFlixDatabase, DAO & Entities)
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.data.local.** { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 5. Media3 ExoPlayer, HLS & OkHttpDataSource
# ------------------------------------------------------------------------------
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { *; }
-keep class androidx.media3.datasource.okhttp.OkHttpDataSource$Factory { *; }
-keepclassmembers class androidx.media3.** {
    public <init>(...);
}
-dontwarn androidx.media3.**

# ------------------------------------------------------------------------------
# 6. OkHttp / Okio / Conscrypt / Optional JVM deps
# ------------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ------------------------------------------------------------------------------
# 7. Application & Coil ImageLoaderFactory
# ------------------------------------------------------------------------------
-keep class com.example.VixFlixApplication { *; }
-keep class coil.** { *; }
-dontwarn coil.**
