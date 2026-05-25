package com.metrolist.music.discordrpc

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

internal object ArtworkCache {
    private val cache = ConcurrentHashMap<String, String>()
    private const val TAG = "DiscordArtworkCache"

    suspend fun getOrFetch(key: String, fetch: suspend () -> String?): String? {
        Timber.tag(TAG).d("getOrFetch: key=${key.takeLast(50)}")

        val cached = cache[key]
        if (cached != null) {
            Timber.tag(TAG).d("getOrFetch: cache HIT for key=${key.takeLast(50)} -> ${cached.takeLast(30)}")
            return cached
        }

        Timber.tag(TAG).d("getOrFetch: cache MISS for key=${key.takeLast(50)}, invoking fetch...")
        val startTime = System.currentTimeMillis()
        val result = fetch()
        val elapsed = System.currentTimeMillis() - startTime
        Timber.tag(TAG).d("getOrFetch: fetch completed in ${elapsed}ms, result=${result?.takeLast(30) ?: "null"}")

        if (result != null) {
            Timber.tag(TAG).d("getOrFetch: caching result for key=${key.takeLast(50)}")
            cache[key] = result
            return result
        }

        Timber.tag(TAG).w("getOrFetch: fetch returned null for key=${key.takeLast(50)}")
        return null
    }

    fun clear() {
        val size = cache.size
        Timber.tag(TAG).d("clear: clearing cache (size=$size)")
        cache.clear()
        Timber.tag(TAG).d("clear: cache cleared")
    }
}
