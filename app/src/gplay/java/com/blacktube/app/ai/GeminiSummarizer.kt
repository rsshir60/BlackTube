package com.blacktube.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfo

// Application context cached at init — used for PromptLibrary lookup
private var _appContext: Context? = null

object GeminiSummarizer {
    private const val TAG = "GeminiSummarizer"
    private const val PREFS_NAME = "blacktube_ai_cache"
    private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    private const val MAX_TRANSCRIPT_LENGTH = 8000

    private var model: GenerativeModel? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var defaultPrefs: SharedPreferences

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        _appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        isInitialized = true
    }

    @JvmStatic
    fun configure(apiKey: String) {
        if (apiKey.isBlank()) {
            model = null
            return
        }
        model = GenerativeModel(
            modelName = "gemini-3.1-flash-lite",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.2f
                maxOutputTokens = 4096
                // Removed responseMimeType = "application/json" to allow raw markdown
            }
        )
    }

    @JvmStatic
    fun isConfigured(): Boolean = model != null

    @JvmStatic
    fun hasCachedSummary(videoId: String, promptId: String): Boolean {
        if (!::prefs.isInitialized) return false
        val cacheKey = buildCacheKey(videoId, promptId)
        return prefs.contains(cacheKey)
    }

    sealed class SummaryResult {
        data class Markdown(val text: String, val cachedAt: Long = 0) : SummaryResult()
        data class Error(val message: String) : SummaryResult()
    }

    @JvmStatic
    suspend fun summarize(context: Context, video: StreamInfo, forceRefresh: Boolean = false): SummaryResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext SummaryResult.Error("Summarizer not initialized")
        }

        val summaryPrompt = PromptLibrary.getSummaryPrompt(context)
        val cacheKey = buildCacheKey(video.id, summaryPrompt.id)
        
        if (!forceRefresh) {
            val cached = getCachedSummary(cacheKey)
            if (cached != null) {
                Log.d(TAG, "Cache hit for ${video.id} using ${summaryPrompt.id}")
                return@withContext cached
            }
        }

        val currentModel = model
            ?: return@withContext SummaryResult.Error("Gemini API key not configured. Add your key in Settings > AI Features.")

        try {
            val transcript = buildTranscriptText(video)
            val prompt = buildPrompt(video, transcript, summaryPrompt)

            val response = currentModel.generateContent(prompt)
            val text = response.text ?: "No summary available."

            val result = SummaryResult.Markdown(text, System.currentTimeMillis())
            cacheSummary(cacheKey, text)
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Summarization failed", e)
            SummaryResult.Error("Failed to summarize: ${e.localizedMessage}")
        }
    }

    private fun buildCacheKey(videoId: String, promptId: String): String =
        "summary_${videoId}_${promptId}_v${PromptLibrary.PROMPT_CONTRACT_VERSION}"

    private fun getCachedSummary(cacheKey: String): SummaryResult? {
        val json = prefs.getString(cacheKey, null) ?: return null
        return try {
            val obj = JSONObject(json)
            val timestamp = obj.getLong("timestamp")
            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
                prefs.edit().remove(cacheKey).apply()
                return null
            }
            val content = obj.getString("content")
            SummaryResult.Markdown(content, timestamp)
        } catch (e: Exception) {
            prefs.edit().remove(cacheKey).apply()
            null
        }
    }

    private fun cacheSummary(cacheKey: String, content: String) {
        if (defaultPrefs.getBoolean("enable_incognito", false)) {
            return
        }
        val obj = JSONObject()
        obj.put("timestamp", System.currentTimeMillis())
        obj.put("content", content)
        prefs.edit().putString(cacheKey, obj.toString()).apply()
    }

    private suspend fun buildTranscriptText(video: StreamInfo): String = withContext(Dispatchers.IO) {
        val subtitles = video.subtitles
        if (subtitles.isNullOrEmpty()) {
            return@withContext ""
        }
        val subtitle = subtitles.find { it.languageTag.startsWith("en") } ?: subtitles[0]
        return@withContext try {
            val downloader = org.schabi.newpipe.extractor.NewPipe.getDownloader()
            val response = downloader.get(subtitle.content)
            val rawText = response.responseBody()
            rawText.take(MAX_TRANSCRIPT_LENGTH)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download subtitle", e)
            ""
        }
    }

    private fun buildPrompt(video: StreamInfo, transcript: String, summaryPrompt: BuiltInPrompt): String {
        val desc = video.description?.content?.take(500) ?: ""
        val title = video.name ?: "Untitled"
        val uploader = video.uploaderName ?: "Unknown"

        val transcriptSection = if (transcript.isNotEmpty()) {
            "\nTranscript excerpt:\n${transcript.take(MAX_TRANSCRIPT_LENGTH)}"
        } else {
            ""
        }

        Log.d(TAG, "Using summary prompt: ${summaryPrompt.title}")

        return """You are an expert content analyst. Extract maximum value from this video content.
Apply this selected Prompt Library style:
${summaryPrompt.promptText}

Ensure your output is clearly formatted in Markdown. Do not include any meta-commentary, just the content requested by the prompt.

Title: $title
Channel: $uploader
Description: $desc$transcriptSection""".trimIndent()
    }
}
