package com.example

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application class dedicata alla configurazione globale delle prestazioni di VixFlix:
 * - ImageLoader Coil centralizzato con cache RAM/Disco dimensionate, RGB_565 per ridurre
 *   l'uso di memoria GPU del 50% sulle locandine opache e crossfade disabilitato per azzerare
 *   gli scatti durante lo scorrimento veloce di liste e caroselli su Smartphone e Android TV.
 */
class VixFlixApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("vixflix_image_cache"))
                    .maxSizeBytes(120L * 1024L * 1024L)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowRgb565(true)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
