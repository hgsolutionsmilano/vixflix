package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * Application class dedicata alla configurazione globale delle prestazioni di VixFlix
 * con ottimizzazioni specifiche per Fire TV Stick e Android TV entry-level:
 * - Unico `sharedBaseOkHttpClient` con ConnectionPool e Dispatcher dimensionati per TV
 *   (evita moltiplicazione di thread pool tra Coil, TMDB, GitHub e trailer).
 * - ImageLoader Coil adattivo alla RAM (RGB_565 opaco, parallelismo decoder ridotto su low-RAM).
 * - Gestione attiva di `onTrimMemory` / `onLowMemory` per liberare RAM su pressione di sistema.
 */
class VixFlixApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val LOW_RAM_THRESHOLD_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB

        fun isLowRamDevice(context: Context): Boolean {
            val activityManager = context.getSystemService(ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            if (activityManager.isLowRamDevice) return true
            val memInfo = ActivityManager.MemoryInfo()
            return runCatching {
                activityManager.getMemoryInfo(memInfo)
                memInfo.totalMem in 1L..LOW_RAM_THRESHOLD_BYTES
            }.getOrDefault(false)
        }
    }

    private val memoryTrimListeners = CopyOnWriteArrayList<WeakReference<(Int) -> Unit>>()

    val isLowRamHardware: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        isLowRamDevice(this)
    }

    /**
     * Client OkHttp base condiviso dall'intera app (Coil + Repository TMDB/GitHub/Trailer).
     * I client specializzati derivano tramite `.newBuilder()` condividendo pool socket e thread.
     */
    val sharedBaseOkHttpClient: OkHttpClient by lazy {
        val lowRam = isLowRamHardware
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(if (lowRam) 6 else 10, 5, TimeUnit.MINUTES))
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = if (lowRam) 24 else 40
                    maxRequestsPerHost = if (lowRam) 8 else 12
                }
            )
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun registerMemoryTrimListener(listener: (Int) -> Unit) {
        memoryTrimListeners.removeAll { it.get() == null || it.get() === listener }
        memoryTrimListeners.add(WeakReference(listener))
    }

    override fun newImageLoader(): ImageLoader {
        val lowRam = isLowRamHardware

        val imageOkHttpClient = sharedBaseOkHttpClient.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRam) 0.18 else 0.26)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("vixflix_image_cache"))
                    .maxSizeBytes(if (lowRam) 100L * 1024L * 1024L else 150L * 1024L * 1024L)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .bitmapFactoryMaxParallelism(if (lowRam) 2 else 4)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            runCatching {
                Coil.imageLoader(this).memoryCache?.trimMemory(level)
            }
            notifyMemoryTrimListeners(level)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        runCatching {
            Coil.imageLoader(this).memoryCache?.clear()
        }
        notifyMemoryTrimListeners(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    private fun notifyMemoryTrimListeners(level: Int) {
        val stale = mutableListOf<WeakReference<(Int) -> Unit>>()
        for (ref in memoryTrimListeners) {
            val cb = ref.get()
            if (cb == null) {
                stale.add(ref)
            } else {
                runCatching { cb(level) }
            }
        }
        if (stale.isNotEmpty()) {
            memoryTrimListeners.removeAll(stale.toSet())
        }
    }
}
