package org.schabi.newpipe.fragments.detail

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.blacktube.app.ai.GeminiSummarizer
import com.blacktube.app.ai.PromptLibrary
import com.blacktube.app.ai.PromptLibraryActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.settings.SettingsActivity

class AiSummaryFragment(private val streamInfo: StreamInfo?) : BottomSheetDialogFragment() {

    constructor() : this(null)

    private lateinit var stateNoKey: LinearLayout
    private lateinit var stateReady: LinearLayout
    private lateinit var stateLoading: LinearLayout
    private lateinit var stateError: LinearLayout
    private lateinit var stateSuccess: LinearLayout

    private lateinit var tvErrorMessage: TextView
    private lateinit var tvSummaryContent: TextView
    private lateinit var chipActivePrompt: AppCompatButton

    private lateinit var promptLibraryLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        promptLibraryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                refreshPromptChip()
                // Auto-refresh summary with forceRefresh=true when returning from Prompt Library with a new prompt
                runSummarize(forceRefresh = true)
            }
        }
    }

    private var summarizeJob: kotlinx.coroutines.Job? = null

    private fun runSummarize(forceRefresh: Boolean = false) {
        val info = streamInfo ?: run {
            tvErrorMessage.text = "Video metadata is loading. Please try again in a moment."
            showState(stateError)
            return
        }

        summarizeJob?.cancel()
        summarizeJob = lifecycleScope.launch {
            if (!isAdded || context == null) return@launch
            showState(stateLoading)
            val ctx = requireContext()

            if (!GeminiSummarizer.isConfigured()) {
                showState(stateNoKey)
                return@launch
            }

            val result = GeminiSummarizer.summarize(ctx, info, forceRefresh)

            if (!isAdded || context == null) return@launch
            when (result) {
                is GeminiSummarizer.SummaryResult.Markdown -> {
                    io.noties.markwon.Markwon.create(requireContext()).setMarkdown(tvSummaryContent, result.text)
                    showState(stateSuccess)
                }
                is GeminiSummarizer.SummaryResult.Error -> {
                    tvErrorMessage.text = result.message
                    showState(stateError)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai_summary, container, false)

        stateNoKey = view.findViewById(R.id.state_no_key)
        stateReady = view.findViewById(R.id.state_ready)
        stateLoading = view.findViewById(R.id.state_loading)
        stateError = view.findViewById(R.id.state_error)
        stateSuccess = view.findViewById(R.id.state_success)

        tvErrorMessage = view.findViewById(R.id.tv_error_message)
        tvSummaryContent = view.findViewById(R.id.tv_summary_content)
        chipActivePrompt = view.findViewById(R.id.chip_active_prompt)

        // Prompt chip → opens Prompt Library
        chipActivePrompt.setOnClickListener {
            promptLibraryLauncher.launch(PromptLibraryActivity.createIntent(requireContext()))
        }

        view.findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java).apply {
                putExtra("show_fragment", "org.schabi.newpipe.settings.AiFeaturesSettingsFragment")
            }
            startActivity(intent)
        }

        fun animateAndHaptic(v: View, action: () -> Unit) {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(80).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()
            action()
        }

        view.findViewById<Button>(R.id.btn_summarize).setOnClickListener { animateAndHaptic(it) { runSummarize(forceRefresh = false) } }
        view.findViewById<Button>(R.id.btn_retry).setOnClickListener { animateAndHaptic(it) { runSummarize(forceRefresh = false) } }
        view.findViewById<Button>(R.id.btn_re_summarize).setOnClickListener { animateAndHaptic(it) { runSummarize(forceRefresh = true) } }
        view.findViewById<Button>(R.id.btn_copy_summary)?.setOnClickListener { btn ->
            animateAndHaptic(btn) {
                val summaryText = tvSummaryContent.text?.toString() ?: ""
                if (summaryText.isNotEmpty()) {
                    val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("AI Summary", summaryText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "📋 Summary copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val etQuery = view.findViewById<android.widget.EditText>(R.id.et_ask_video)
        val btnAsk = view.findViewById<AppCompatButton>(R.id.btn_send_ask)

        val triggerAsk = {
            val queryText = etQuery?.text?.toString()?.trim() ?: ""
            if (queryText.isNotEmpty()) {
                if (btnAsk != null) animateAndHaptic(btnAsk) { etQuery?.setText("") }
                else etQuery?.setText("")
                runCustomQuestion(queryText)
            }
        }

        btnAsk?.setOnClickListener { triggerAsk() }
        etQuery?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                triggerAsk()
                true
            } else false
        }

        refreshPromptChip()
        checkStateAndLoad()

        return view
    }

    private fun refreshPromptChip() {
        val active = PromptLibrary.getActivePrompt(requireContext())
        if (active != null) {
            chipActivePrompt.text = active.title
        } else {
            chipActivePrompt.text = getString(R.string.prompt_library_default)
        }
    }

    private fun checkStateAndLoad() {
        if (streamInfo == null || streamInfo.id.isNullOrEmpty()) {
            showState(stateReady)
            return
        }

        if (!GeminiSummarizer.isConfigured()) {
            showState(stateNoKey)
            return
        }

        val activePrompt = PromptLibrary.getActivePrompt(requireContext())
        val promptId = activePrompt?.id ?: PromptLibrary.DEFAULT_PROMPT_ID
        val isCached = GeminiSummarizer.hasCachedSummary(streamInfo.id, promptId)

        if (isCached) {
            runSummarize(forceRefresh = false)
        } else {
            showState(stateReady)
        }
    }

    private fun showState(visibleState: View) {
        stateNoKey.visibility = View.GONE
        stateReady.visibility = View.GONE
        stateLoading.visibility = View.GONE
        stateError.visibility = View.GONE
        stateSuccess.visibility = View.GONE
        visibleState.visibility = View.VISIBLE
        view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.ai_summary_scroll_view)?.smoothScrollTo(0, 0)
    }

    private fun runCustomQuestion(userQuery: String) {
        val info = streamInfo ?: return
        showState(stateLoading)

        lifecycleScope.launch {
            try {
                val ctx = requireContext()
                val geminiRes = GeminiSummarizer.askQuestion(ctx, info, userQuery)
                val answer: String = when (geminiRes) {
                    is GeminiSummarizer.SummaryResult.Markdown -> geminiRes.text
                    is GeminiSummarizer.SummaryResult.Error -> "⚠️ Could not answer question: ${geminiRes.message}"
                }

                showState(stateSuccess)
                val existingText = tvSummaryContent.text?.toString() ?: ""
                val qnaBlock = "\n\n---\n💬 **Question**: $userQuery\n\n$answer"
                tvSummaryContent.text = if (existingText.isNotEmpty()) existingText + qnaBlock else answer.trim()

                delay(100)
                view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.ai_summary_scroll_view)?.fullScroll(View.FOCUS_DOWN)
            } catch (e: Exception) {
                showState(stateSuccess)
                Toast.makeText(requireContext(), "⚠️ Q&A response: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
