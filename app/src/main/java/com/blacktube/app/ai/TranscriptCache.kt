package com.blacktube.app.ai

import androidx.collection.LruCache

object TranscriptCache {
    private val cache = LruCache<String, String>(20) // Cache up to 20 transcripts

    @JvmStatic
    fun put(videoId: String, transcript: String) {
        if (videoId.isNotEmpty() && transcript.isNotEmpty()) {
            cache.put(videoId, transcript)
        }
    }

    @JvmStatic
    fun get(videoId: String): String? {
        return if (videoId.isNotEmpty()) cache.get(videoId) else null
    }

    @JvmStatic
    fun clear() {
        cache.evictAll()
    }
}
