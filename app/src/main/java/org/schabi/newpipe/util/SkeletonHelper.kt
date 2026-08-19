package org.schabi.newpipe.util

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View

object SkeletonHelper {
    @JvmStatic
    fun startPulseAnimation(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 0.35f, 0.75f, 0.35f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }
}
