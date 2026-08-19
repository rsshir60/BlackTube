package com.blacktube.app.ai

data class SummaryPdfData(
    val videoTitle: String,
    val channelName: String,
    val category: String,
    val categoryEmoji: String,
    val corePurpose: String,
    val vibeEmoji: String,
    val culturalImpact: String,
    val chapters: List<PdfChapter>,
    val generationDate: String,
    val engineUsed: String, // "Gemini 3.1 Flash-Lite" or "Local Phi-4 Mini"
    val videoUrl: String,
    val videoDuration: String
)

data class PdfChapter(
    val startSeconds: Int,
    val endSeconds: Int,
    val summary: String,
    val emoji: String
) {
    fun formattedTime(): String {
        val startMin = startSeconds / 60
        val startSec = startSeconds % 60
        val endMin = endSeconds / 60
        val endSec = endSeconds % 60
        return String.format("%d:%02d – %d:%02d", startMin, startSec, endMin, endSec)
    }
}
