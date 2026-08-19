package com.blacktube.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryPdfRendererTest {

    @Test
    fun chapterFormatting_formatsMinutesAndSecondsCorrectly() {
        val chapter = PdfChapter(
            startSeconds = 125, // 2m 5s
            endSeconds = 480,   // 8m 0s
            summary = "Chapter overview",
            emoji = "📌"
        )
        assertEquals("2:05 – 8:00", chapter.formattedTime())
    }

    @Test
    fun summaryPdfData_holdsExpectedStructure() {
        val chapter = PdfChapter(0, 60, "Intro", "🎬")
        val data = SummaryPdfData(
            videoTitle = "Test Video",
            channelName = "Test Channel",
            category = "Education",
            categoryEmoji = "🎓",
            corePurpose = "Explain concept",
            vibeEmoji = "🧠 Thoughtful",
            culturalImpact = "High",
            chapters = listOf(chapter),
            generationDate = "August 19, 2026",
            engineUsed = "Gemini 3.1 Flash-Lite",
            videoUrl = "https://youtube.com/watch?v=123",
            videoDuration = "10:00"
        )

        assertEquals("Test Video", data.videoTitle)
        assertEquals(1, data.chapters.size)
        assertEquals("0:00 – 1:00", data.chapters[0].formattedTime())
    }
}
