package com.blacktube.app.player

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player

object SleepTimerManager {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    var minutesRemaining = 0
        private set

    fun showTimerDialog(context: Context, player: Player) {
        val options = arrayOf(
            "Off",
            "15 minutes",
            "30 minutes",
            "45 minutes",
            "60 minutes",
            "Custom..."
        )

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.sleep_timer_dialog_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cancelTimer(context)
                    1 -> startTimer(context, player, 15)
                    2 -> startTimer(context, player, 30)
                    3 -> startTimer(context, player, 45)
                    4 -> startTimer(context, player, 60)
                    5 -> showCustomTimerDialog(context, player)
                }
            }
            .show()
    }

    private fun showCustomTimerDialog(context: Context, player: Player) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter minutes"
        }

        AlertDialog.Builder(context)
            .setTitle("Custom Sleep Timer")
            .setView(input)
            .setPositiveButton("Start") { _, _ ->
                val text = input.text.toString()
                val mins = text.toIntOrNull()
                if (mins != null && mins > 0) {
                    startTimer(context, player, mins)
                } else {
                    Toast.makeText(context, context.getString(R.string.sleep_timer_invalid_duration), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun startTimer(context: Context, player: Player, minutes: Int) {
        cancelRunnable()
        minutesRemaining = minutes
        
        val startMsg = context.getString(R.string.sleep_timer_toast_start, minutes.toString())
        Toast.makeText(context, startMsg, Toast.LENGTH_SHORT).show()

        val r = object : Runnable {
            override fun run() {
                if (minutesRemaining <= 1) {
                    player.pause()
                    Toast.makeText(context, context.getString(R.string.sleep_timer_toast_finish), Toast.LENGTH_LONG).show()
                    minutesRemaining = 0
                } else {
                    minutesRemaining--
                    handler.postDelayed(this, 60 * 1000)
                }
            }
        }
        runnable = r
        handler.postDelayed(r, 60 * 1000)
    }

    fun cancelTimer(context: Context) {
        if (runnable != null) {
            cancelRunnable()
            Toast.makeText(context, "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelRunnable() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        minutesRemaining = 0
    }
}
