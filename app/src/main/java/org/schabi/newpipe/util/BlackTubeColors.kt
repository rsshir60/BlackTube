package org.schabi.newpipe.util

import android.graphics.Color

object BlackTubeColors {
    @JvmField val BACKGROUND       = Color.parseColor("#000000") // Pure AMOLED black
    @JvmField val SURFACE          = Color.parseColor("#0A0A0A") // Elevated cards & containers
    @JvmField val SURFACE_VARIANT  = Color.parseColor("#1E1E24") // Active chips, buttons & inputs
    @JvmField val ON_SURFACE       = Color.parseColor("#FFFFFF") // 100% pure white primary text
    @JvmField val ON_SURFACE_MUTED = Color.parseColor("#B3FFFFFF") // 70% secondary text
    @JvmField val OUTLINE          = Color.parseColor("#33FFFFFF") // 20% subtle borders
    @JvmField val ACCENT           = Color.parseColor("#E50914") // Flagship Crimson Red
    @JvmField val ACCENT_SOFT      = Color.parseColor("#B20710") // Gradient soft red
    @JvmField val SKELETON_BLOCK   = Color.parseColor("#1A1A1A") // Skeleton placeholder block
    @JvmField val SUCCESS          = Color.parseColor("#4CAF50") // Success / Download complete
    @JvmField val WARNING          = Color.parseColor("#FFC107") // Warning
    @JvmField val ERROR            = Color.parseColor("#EF5350") // Error / Rejections
}
