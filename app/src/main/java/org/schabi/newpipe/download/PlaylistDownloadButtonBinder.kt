package org.schabi.newpipe.download

import android.content.Context
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R

/**
 * PlaylistDownloadButtonBinder — Live State Machine for Playlist Download Buttons.
 */
object PlaylistDownloadButtonBinder {

    sealed class BatchState {
        object Idle : BatchState()
        data class Queued(val total: Int) : BatchState()
        data class Downloading(val done: Int, val total: Int, val percent: Int) : BatchState()
        data class Paused(val done: Int, val total: Int) : BatchState()
        data class Partial(val done: Int, val total: Int) : BatchState()
        data class Complete(val total: Int) : BatchState()
        data class Failed(val error: String) : BatchState()
    }

    private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    private const val COLOR_SECONDARY = 0xB3FFFFFF.toInt()
    private const val COLOR_ACCENT = 0xFFE50914.toInt()
    private const val COLOR_WARNING = 0xFFFFC107.toInt()
    private const val COLOR_SUCCESS = 0xFF4CAF50.toInt()
    private const val COLOR_ERROR = 0xFFEF5350.toInt()

    fun bind(
        context: Context,
        state: BatchState,
        icon: ImageView,
        label: TextView,
        container: View? = null
    ) {
        when (state) {
            is BatchState.Idle -> {
                icon.setImageResource(R.drawable.ic_file_download)
                icon.setColorFilter(COLOR_WHITE)
                label.setTextColor(COLOR_WHITE)
                label.text = "Download All"
            }
            is BatchState.Queued -> {
                icon.setImageResource(R.drawable.ic_hourglass_top)
                icon.setColorFilter(COLOR_SECONDARY)
                label.setTextColor(COLOR_SECONDARY)
                label.text = "Queued…"
                container?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            is BatchState.Downloading -> {
                icon.setImageResource(R.drawable.ic_file_download)
                icon.setColorFilter(COLOR_ACCENT)
                label.setTextColor(COLOR_ACCENT)
                label.text = "${state.done}/${state.total} • ${state.percent}%"
            }
            is BatchState.Paused -> {
                icon.setImageResource(R.drawable.ic_pause)
                icon.setColorFilter(COLOR_WARNING)
                label.setTextColor(COLOR_WARNING)
                label.text = "Paused • ${state.done}/${state.total}"
            }
            is BatchState.Complete -> {
                icon.setImageResource(R.drawable.ic_done)
                icon.setColorFilter(COLOR_SUCCESS)
                label.setTextColor(COLOR_SUCCESS)
                label.text = "Downloaded"
                container?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            is BatchState.Partial -> {
                icon.setImageResource(R.drawable.ic_file_download)
                icon.setColorFilter(COLOR_WARNING)
                label.setTextColor(COLOR_WARNING)
                label.text = "${state.done}/${state.total} • Retry"
            }
            is BatchState.Failed -> {
                icon.setImageResource(R.drawable.ic_bug_report)
                icon.setColorFilter(COLOR_ERROR)
                label.setTextColor(COLOR_ERROR)
                label.text = "Failed • Retry"
                container?.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
        }
    }
}
