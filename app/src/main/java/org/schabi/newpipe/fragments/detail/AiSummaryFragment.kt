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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.blacktube.app.ai.GeminiSummarizer
import com.blacktube.app.ai.PromptLibrary
import com.blacktube.app.ai.PromptLibraryActivity
import kotlinx.coroutines.launch
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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

        val runSummarize = {
            lifecycleScope.launch {
                showState(stateLoading)
                when (val result = GeminiSummarizer.summarize(streamInfo!!)) {
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

        view.findViewById<Button>(R.id.btn_summarize).setOnClickListener { runSummarize() }
        view.findViewById<Button>(R.id.btn_retry).setOnClickListener { runSummarize() }
        view.findViewById<Button>(R.id.btn_re_summarize).setOnClickListener { runSummarize() }

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
            lifecycleScope.launch {
                showState(stateLoading)
                when (val result = GeminiSummarizer.summarize(streamInfo!!)) {
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
    }
}
