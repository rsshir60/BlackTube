package com.blacktube.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

data class AiSummaryChapter(
    val startSeconds: Int,
    val endSeconds: Int,
    val summary: String,
    val emoji: String
)

data class AiSummaryData(
    val title: String,
    val channel: String,
    val category: String,
    val categoryEmoji: String,
    val corePurpose: String,
    val chapters: List<AiSummaryChapter>,
    val culturalImpact: String,
    val vibeEmoji: String
)

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
                responseMimeType = "application/json"
            }
        )
    }

    @JvmStatic
    fun isConfigured(): Boolean = model != null

    sealed class SummaryResult {
        data class Structured(val data: AiSummaryData, val cachedAt: Long = 0) : SummaryResult()
        data class Fallback(val markdown: String, val cachedAt: Long = 0) : SummaryResult()
        data class Error(val message: String) : SummaryResult()
    }

    @JvmStatic
    suspend fun summarize(video: StreamInfo): SummaryResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext SummaryResult.Error("Summarizer not initialized")
        }

        val cached = getCachedSummary(video.id)
        if (cached != null) {
            Log.d(TAG, "Cache hit for ${video.id}")
            return@withContext cached
        }

        val currentModel = model
            ?: return@withContext SummaryResult.Error("Gemini API key not configured. Add your key in Settings > AI Features.")

        try {
            val transcript = buildTranscriptText(video)
            val prompt = buildPrompt(video, transcript)

            val response = currentModel.generateContent(prompt)
            val text = response.text ?: "No summary available."

            val result = parseJsonResponse(text)
            cacheSummary(video.id, text)
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Summarization failed", e)
            SummaryResult.Error("Failed to summarize: ${e.localizedMessage}")
        }
    }

    private fun parseJsonResponse(text: String): SummaryResult {
        return try {
            val obj = JSONObject(text)
            val title = obj.optString("title", "")
            val channel = obj.optString("channel", "")
            val category = obj.optString("category", "")
            val categoryEmoji = obj.optString("categoryEmoji", "🎯")
            val corePurpose = obj.optString("corePurpose", "")
            val culturalImpact = obj.optString("culturalImpact", "")
            val vibeEmoji = obj.optString("vibeEmoji", "✨")
            
            val chapters = mutableListOf<AiSummaryChapter>()
            val chaptersArray = obj.optJSONArray("chapters")
            if (chaptersArray != null) {
                for (i in 0 until chaptersArray.length()) {
                    val chapObj = chaptersArray.getJSONObject(i)
                    chapters.add(
                        AiSummaryChapter(
                            startSeconds = chapObj.optInt("startSeconds", 0),
                            endSeconds = chapObj.optInt("endSeconds", 0),
                            summary = chapObj.optString("summary", ""),
                            emoji = chapObj.optString("emoji", "⏱️")
                        )
                    )
                }
            }

            val data = AiSummaryData(
                title = title,
                channel = channel,
                category = category,
                categoryEmoji = categoryEmoji,
                corePurpose = corePurpose,
                chapters = chapters,
                culturalImpact = culturalImpact,
                vibeEmoji = vibeEmoji
            )
            SummaryResult.Structured(data, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON, falling back", e)
            // Build markdown fallback manually
            val fallbackMarkdown = buildFallbackMarkdown(text)
            SummaryResult.Fallback(fallbackMarkdown, System.currentTimeMillis())
        }
    }
    
    private fun buildFallbackMarkdown(text: String): String {
        try {
            val obj = JSONObject(text)
            val sb = java.lang.StringBuilder()
            sb.append("## 🎯 ").append(obj.optString("category", "Video")).append("\n")
            sb.append(obj.optString("corePurpose", "")).append("\n\n")
            sb.append("## ⏱️ Chapters\n")
            val chaptersArray = obj.optJSONArray("chapters")
            if (chaptersArray != null) {
                for (i in 0 until chaptersArray.length()) {
                    val chapObj = chaptersArray.getJSONObject(i)
                    val emoji = chapObj.optString("emoji", "⏱️")
                    sb.append("• ").append(emoji).append(" ").append(chapObj.optString("summary", "")).append("\n")
                }
            }
            sb.append("\n## 🌍 Impact\n")
            sb.append(obj.optString("culturalImpact", ""))
            return sb.toString()
        } catch(e: Exception) {
            return text
        }
    }

    private fun getCachedSummary(videoId: String): SummaryResult? {
        val json = prefs.getString("summary_${videoId}_v3", null) ?: return null
        return try {
            val obj = JSONObject(json)
            val timestamp = obj.getLong("timestamp")
            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
                prefs.edit().remove("summary_${videoId}_v3").apply()
                return null
            }
            val content = obj.getString("content")
            val result = parseJsonResponse(content)
            when (result) {
                is SummaryResult.Structured -> result.copy(cachedAt = timestamp)
                is SummaryResult.Fallback -> result.copy(cachedAt = timestamp)
                is SummaryResult.Error -> null
            }
        } catch (e: Exception) {
            prefs.edit().remove("summary_${videoId}_v3").apply()
            null
        }
    }

    private fun cacheSummary(videoId: String, content: String) {
        if (defaultPrefs.getBoolean("enable_incognito", false)) {
            return
        }
        val obj = JSONObject()
        obj.put("timestamp", System.currentTimeMillis())
        obj.put("content", content)
        prefs.edit().putString("summary_${videoId}_v3", obj.toString()).apply()
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

    private fun buildPrompt(video: StreamInfo, transcript: String): String {
        val desc = video.description?.content?.take(500) ?: ""
        val title = video.name ?: "Untitled"
        val uploader = video.uploaderName ?: "Unknown"

        val transcriptSection = if (transcript.isNotEmpty()) {
            "\nTranscript excerpt:\n${transcript.take(MAX_TRANSCRIPT_LENGTH)}"
        } else {
            ""
        }

        return """You are an expert content analyst. Extract maximum value from this video content.
Return STRICT JSON ONLY. Do not wrap in markdown ```json blocks. Just the raw JSON object.

Schema required:
{
  "title": "string",
  "channel": "string",
  "category": "string",
  "categoryEmoji": "string (single emoji from vocabulary)",
  "corePurpose": "string",
  "chapters": [
    {
      "startSeconds": int,
      "endSeconds": int,
      "summary": "string",
      "emoji": "string (single emoji from vocabulary)"
    }
  ],
  "culturalImpact": "string",
  "vibeEmoji": "string (single emoji from vocabulary)"
}

Vocabulary constraints for emojis:
- 🎯 for Core Purpose / Identity
- ⏱️ for Chapters / Timeline
- 🌍 for Cultural Impact
- 🎵 for Music / Audio
- ✨ for Key Insights / Highlights
- 💬 for Lyrics / Dialogue

Title: $title
Channel: $uploader
Description: $desc$transcriptSection""".trimIndent()
    }
}

