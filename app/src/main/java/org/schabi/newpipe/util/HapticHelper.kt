package org.schabi.newpipe.util

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {
    @JvmStatic
    fun lightTick(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    @JvmStatic
    fun heavyImpact(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    @JvmStatic
    fun successDoubleTick(view: View?) {
        if (view == null) return
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        Handler(Looper.getMainLooper()).postDelayed({
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }, 80)
    }

    @JvmStatic
    fun errorBuzz(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}
