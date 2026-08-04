package com.example.comicreader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

/**
 * 应用入口：自定义 Coil ImageLoader
 * 限制内存缓存大小（默认是设备内存 25%，对长条漫画数百页会累积导致 OOM）
 * 128MB 足够保留视口附近页面的快速回显，又不会无限累积
 */
class ComicReaderApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(128 * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
