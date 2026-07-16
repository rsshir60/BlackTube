package com.blacktube.app.ai

import android.content.Context
import org.schabi.newpipe.extractor.stream.StreamInfo

object GeminiSummarizer {
    
    fun init(context: Context) {
        // No-op for F-Droid
    }

    @JvmStatic
    fun configure(apiKey: String) {
        // No-op for F-Droid
    }

    @JvmStatic
    fun isConfigured(): Boolean = false

    @JvmStatic
    fun hasCachedSummary(videoId: String, promptId: String): Boolean = false

    sealed class SummaryResult {
        data class Markdown(val text: String, val cachedAt: Long = 0) : SummaryResult()
        data class Error(val message: String) : SummaryResult()
    }

    @JvmStatic
    suspend fun summarize(context: Context, video: StreamInfo, forceRefresh: Boolean = false): SummaryResult {
        return SummaryResult.Error("AI Features are not available in the F-Droid build.")
    }
}
