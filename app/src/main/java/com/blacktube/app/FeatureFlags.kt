package com.blacktube.app

object FeatureFlags {
    private val flags = mutableMapOf(
        "local_ai_enabled" to true,
        "batch_playlist_download_enabled" to true,
        "smart_chapters_enabled" to true,
        "amoled_pure_black" to true
    )

    @JvmStatic
    fun isEnabled(flag: String, defaultValue: Boolean = true): Boolean {
        return flags[flag] ?: defaultValue
    }

    @JvmStatic
    fun setFlag(flag: String, enabled: Boolean) {
        flags[flag] = enabled
    }
}
