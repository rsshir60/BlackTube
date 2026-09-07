package com.blacktube.app.ai

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.util.Localization
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

object SummaryExportHelper {
    private const val TAG = "SummaryExportHelper"

    enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
        PDF(".pdf", "application/pdf", "📄 PDF Document (.pdf)"),
        MARKDOWN(".md", "text/markdown", "📝 Markdown File (.md)"),
        TXT(".txt", "text/plain", "📜 Plain Text (.txt)")
    }

    @JvmStatic
    fun getDownloadsDirectory(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "summaries")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    @JvmStatic
    fun exportSummary(
        context: Context,
        info: StreamInfo?,
        summaryText: String,
        format: ExportFormat,
        onSuccess: ((File) -> Unit)? = null
    ): File? {
        val videoTitle = info?.name ?: "Video_Summary"
        val sanitizedTitle = videoTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "${sanitizedTitle}_Summary${format.extension}"
        val targetDir = getDownloadsDirectory(context)
        val targetFile = File(targetDir, fileName)

        try {
            when (format) {
                ExportFormat.PDF -> {
                    val pdfData = buildPdfData(info, videoTitle, summaryText)
                    SummaryPdfRenderer().render(pdfData, targetFile)
                }
                ExportFormat.MARKDOWN -> {
                    FileWriter(targetFile).use { writer ->
                        writer.write("# $videoTitle\n\n$summaryText")
                    }
                }
                ExportFormat.TXT -> {
                    FileWriter(targetFile).use { writer ->
                        writer.write("$videoTitle\n\n$summaryText")
                    }
                }
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(format.mimeType),
                null
            )

            onSuccess?.invoke(targetFile)
            return targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export summary as ${format.name}", e)
            Toast.makeText(context, "⚠️ Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    @JvmStatic
    fun openFile(context: Context, file: File, mimeType: String) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open exported summary file", e)
            Toast.makeText(context, "⚠️ No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun shareSummary(context: Context, info: StreamInfo?, summaryText: String) {
        try {
            val videoTitle = info?.name ?: "Video Summary"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AI Summary: $videoTitle")
                putExtra(Intent.EXTRA_TEXT, "✨ AI Summary for $videoTitle\n\n$summaryText")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Summary via..."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share summary", e)
        }
    }

    @JvmStatic
    fun buildPdfData(info: StreamInfo?, title: String, content: String): SummaryPdfData {
        val channelName = info?.uploaderName ?: "Unknown Channel"
        val durationStr = if (info != null && info.duration > 0) Localization.getDurationString(info.duration) else ""
        val videoUrl = info?.url ?: ""
        val dateStr = SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault()).format(Date())

        var category = "Video Intelligence"
        var categoryEmoji = "🎓"
        var corePurpose = content
        var vibeEmoji = "🎬 Analytical"
        var culturalImpact = ""
        val chapters = ArrayList<PdfChapter>()

        try {
            val json = JSONObject(content)
            if (json.has("corePurpose")) corePurpose = json.optString("corePurpose", content)
            if (json.has("category")) category = json.optString("category", category)
            if (json.has("categoryEmoji")) categoryEmoji = json.optString("categoryEmoji", categoryEmoji)
            if (json.has("vibeEmoji")) vibeEmoji = json.optString("vibeEmoji", vibeEmoji)
            if (json.has("culturalImpact")) culturalImpact = json.optString("culturalImpact", "")

            val chaptersArray = json.optJSONArray("chapters")
            if (chaptersArray != null) {
                for (i in 0 until chaptersArray.length()) {
                    val ch = chaptersArray.getJSONObject(i)
                    chapters.add(
                        PdfChapter(
                            ch.optInt("startSeconds", 0),
                            ch.optInt("endSeconds", 0),
                            ch.optString("summary", ""),
                            ch.optString("emoji", "📌")
                        )
                    )
                }
            }
        } catch (ignored: Exception) {
            // Raw text fallback
        }

        return SummaryPdfData(
            title,
            channelName,
            category,
            categoryEmoji,
            corePurpose,
            vibeEmoji,
            culturalImpact,
            chapters,
            dateStr,
            "Gemini 3.1 Flash-Lite (Cloud)",
            videoUrl,
            durationStr
        )
    }
}
