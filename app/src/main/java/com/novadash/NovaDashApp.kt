package com.novadash

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class NovaDashApp : Application(), ImageLoaderFactory {

    /**
     * Coil loader tuned for camera thumbnails. The camera serves them with
     * `Cache-Control: no-store`, but a clip's first-frame thumbnail is immutable, so we
     * disable [respectCacheHeaders] and keep a persistent disk cache keyed by the file URL.
     * Each thumbnail is then fetched once and loads instantly (and offline) thereafter.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(coil.decode.VideoFrameDecoder.Factory()) } // local-clip thumbnails
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "thumbnails"))
                    .maxSizeBytes(200L * 1024 * 1024) // 200 MB
                    .build()
            }
            .build()
}
