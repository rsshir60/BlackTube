package com.blacktube.app.ai

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TokenUsageTracker {
    private const val PREFS_NAME = "blacktube_token_usage"
    private const val KEY_DAILY_COUNT = "daily_api_calls"
    private const val KEY_LAST_RESET = "last_reset_date"

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    @JvmStatic
    fun recordApiCall(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val lastReset = prefs.getString(KEY_LAST_RESET, "")

        if (lastReset != today) {
            prefs.edit()
                .putInt(KEY_DAILY_COUNT, 1)
                .putString(KEY_LAST_RESET, today)
                .apply()
        } else {
            val count = prefs.getInt(KEY_DAILY_COUNT, 0)
            prefs.edit().putInt(KEY_DAILY_COUNT, count + 1).apply()
        }
    }

    @JvmStatic
    fun getDailyCallCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val lastReset = prefs.getString(KEY_LAST_RESET, "")
        return if (lastReset == today) prefs.getInt(KEY_DAILY_COUNT, 0) else 0
    }

    @JvmStatic
    fun shouldWarnUser(context: Context): Boolean {
        val count = getDailyCallCount(context)
        return count >= 45 // Warn at 45 calls (approaching standard free tier rate limits)
    }
}
