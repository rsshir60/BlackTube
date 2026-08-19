package org.schabi.newpipe.util

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

object FontHelper {
    const val KEY_APP_FONT_TYPE = "pref_key_app_font_type"

    const val FONT_TYPE_JETBRAINS = "jetbrains_mono"
    const val FONT_TYPE_SYSTEM = "system"

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
    fun resetFontCache() {
        cachedTypeface = null
        currentFontType = null
    }

    @JvmStatic
    fun applyFontToActivity(activity: Activity) {
        try {
            val typeface = getTypeface(activity)
            val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            if (rootView != null) {
                applyFontToViewTree(rootView, typeface)
            }
        } catch (e: Exception) {
            // Ignore during theme transition
        }
    }

    @JvmStatic
    fun applyFontToViewTree(view: View?, typeface: Typeface) {
        if (view == null) return

        if (view is TextView) {
            val style = view.typeface?.style ?: Typeface.NORMAL
            view.setTypeface(typeface, style)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFontToViewTree(view.getChildAt(i), typeface)
            }
        }
    }
}
