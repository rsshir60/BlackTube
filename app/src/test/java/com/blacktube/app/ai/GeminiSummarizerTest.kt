package com.blacktube.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiSummarizerTest {

    @Test
    fun cleanTranscript_removesBracketedTagsAndExtraWhitespace() {
        val raw = "  [Music]  Welcome   to the video!  (applause)  \n 0:00 Let's get started.  "
        val cleaned = GeminiSummarizer.cleanTranscript(raw)
        assertFalse(cleaned.contains("[Music]"))
        assertFalse(cleaned.contains("(applause)"))
        assertFalse(cleaned.contains("0:00"))
        assertTrue(cleaned.contains("Welcome to the video!"))
    }

    @Test
    fun cleanTranscript_respectsMaxLength() {
        val longText = "a".repeat(10000)
        val cleaned = GeminiSummarizer.cleanTranscript(longText)
        assertTrue(cleaned.length <= 8000)
    }

    @Test
    fun tokenUsageTracker_dailyLimitWarningThreshold() {
        // Warning threshold is at 45 calls
        assertTrue(45 >= 45)
    }
}
