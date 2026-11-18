package com.example

import android.app.ActivityManager
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * Application class dedicata alla configurazione globale delle prestazioni di VixFlix:
 * - ImageLoader Coil ottimizzato con cache RAM a due livelli (strong + weak references),
 *   cache disco dedicata da 150 MB, bitmap HARDWARE su API 26+ (con fallback RGB_565),
 *   parallelismo controllato dei decoder per evitare contention sulla UI/RenderThread e
 *   pool HTTP dedicato per caricare rapidamente locandine e backdrop durante lo scorrimento.
 */
class VixFlixApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        val isLowRamDevice = activityManager?.isLowRamDevice == true

        val imageOkHttpClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 32
                    maxRequestsPerHost = 12
                }
            )
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRamDevice) 0.20 else 0.30)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("vixflix_image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .bitmapFactoryMaxParallelism(4)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
