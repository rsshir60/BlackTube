package org.schabi.newpipe.util

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FontHelper {
    private const val TAG = "FontHelper"

    const val KEY_APP_FONT_TYPE = "pref_key_app_font_type"
    const val KEY_CUSTOM_FONT_PATH = "pref_key_custom_font_path"

    const val FONT_TYPE_JETBRAINS = "jetbrains_mono"
    const val FONT_TYPE_SYSTEM = "system"
    const val FONT_TYPE_CUSTOM = "custom"

    private var cachedTypeface: Typeface? = null
    private var currentFontType: String? = null

    @JvmStatic
    fun getTypeface(context: Context): Typeface {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val fontType = prefs.getString(KEY_APP_FONT_TYPE, FONT_TYPE_JETBRAINS) ?: FONT_TYPE_JETBRAINS

        if (cachedTypeface != null && fontType == currentFontType) {
            return cachedTypeface!!
        }

        val typeface: Typeface = when (fontType) {
            FONT_TYPE_SYSTEM -> Typeface.DEFAULT
            FONT_TYPE_CUSTOM -> {
                val customFile = getCustomFontFile(context)
                if (customFile.exists() && customFile.length() > 0) {
                    try {
                        Typeface.createFromFile(customFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load custom font, fallback to JetBrains Mono", e)
                        loadDefaultJetBrainsMono(context)
                    }
                } else {
                    loadDefaultJetBrainsMono(context)
                }
            }
            else -> loadDefaultJetBrainsMono(context)
        }

        cachedTypeface = typeface
        currentFontType = fontType
        return typeface
    }

    private fun loadDefaultJetBrainsMono(context: Context): Typeface {
        return try {
            ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }
    }

    @JvmStatic
    fun getCustomFontFile(context: Context): File {
        return File(context.filesDir, "custom_app_font.ttf")
    }

    @JvmStatic
    fun importCustomFont(context: Context, uri: Uri): Boolean {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val targetFile = getCustomFontFile(context)
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                cachedTypeface = null // reset cache
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(KEY_APP_FONT_TYPE, FONT_TYPE_CUSTOM)
                    .putString(KEY_CUSTOM_FONT_PATH, targetFile.absolutePath)
                    .apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import custom font from URI: $uri", e)
            false
        }
    }

    @JvmStatic
    fun resetFontCache() {
        cachedTypeface = null
        currentFontType = null
    }

    @JvmStatic
    fun removeCustomFont(context: Context): Boolean {
        return try {
            val customFile = getCustomFontFile(context)
            if (customFile.exists()) {
                customFile.delete()
            }
            resetFontCache()
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KEY_APP_FONT_TYPE, FONT_TYPE_JETBRAINS)
                .remove(KEY_CUSTOM_FONT_PATH)
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove custom font", e)
            false
        }
    }

    @JvmStatic
    fun applyFontToViewTree(view: View?, typeface: Typeface) {
        if (view == null) return
        if (view is TextView) {
            view.typeface = typeface
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFontToViewTree(view.getChildAt(i), typeface)
            }
        }
    }

    @JvmStatic
    fun applyFontToActivity(activity: Activity?) {
        if (activity == null) return
        try {
            val typeface = getTypeface(activity)
            val rootView = activity.window?.decorView
            applyFontToViewTree(rootView, typeface)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying font to activity", e)
        }
    }
}
