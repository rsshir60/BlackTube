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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private lateinit var chipEngineSelector: AppCompatButton

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

            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
            val engineMode = prefs.getString("pref_key_ai_engine_mode", "auto") ?: "auto"
            val isLocalDownloaded = org.schabi.newpipe.ai.LocalModelEngine.isModelDownloaded(ctx)

            val useLocal = when (engineMode) {
                "local" -> true
                "gemini" -> false
                else -> isLocalDownloaded
            }

            val result = if (useLocal) {
                if (isLocalDownloaded) {
                    val prompt = "Summarize video: ${info.name ?: ""}\nDescription: ${info.description?.content ?: ""}"
                    val initialized = org.schabi.newpipe.ai.LocalModelEngine.initialize(ctx)
                    if (initialized) {
                        val localText = org.schabi.newpipe.ai.LocalModelEngine.generateSummary(prompt)
                        GeminiSummarizer.SummaryResult.Markdown(localText)
                    } else if (GeminiSummarizer.isConfigured()) {
                        GeminiSummarizer.summarize(ctx, info, forceRefresh)
                    } else {
                        GeminiSummarizer.SummaryResult.Error("Local AI engine is preparing. Please tap 1-Click Download or check device RAM.")
                    }
                } else {
                    GeminiSummarizer.SummaryResult.Error("Local AI Engine selected in Settings, but model is not downloaded yet. Tap 1-Click Download Phi-5 AI below.")
                }
            } else if (GeminiSummarizer.isConfigured()) {
                GeminiSummarizer.summarize(ctx, info, forceRefresh)
            } else {
                GeminiSummarizer.SummaryResult.Error("No AI engine available. Please download the 1-Click Local Model or add a Gemini API key.")
            }

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

    private fun runCustomQuestion(userQuery: String) {
        val info = streamInfo ?: return
        summarizeJob?.cancel()
        summarizeJob = lifecycleScope.launch {
            if (!isAdded || context == null) return@launch
            showState(stateLoading)
            val ctx = requireContext()
            val prompt = "Question about video '${info.name ?: ""}': $userQuery"

            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
            val engineMode = prefs.getString("pref_key_ai_engine_mode", "auto") ?: "auto"
            val isLocalDownloaded = org.schabi.newpipe.ai.LocalModelEngine.isModelDownloaded(ctx)

            val useLocal = when (engineMode) {
                "local" -> true
                "gemini" -> false
                else -> isLocalDownloaded
            }

            val result = if (useLocal) {
                if (isLocalDownloaded) {
                    org.schabi.newpipe.ai.LocalModelEngine.initialize(ctx)
                    val ans = org.schabi.newpipe.ai.LocalModelEngine.generateSummary(prompt)
                    GeminiSummarizer.SummaryResult.Markdown("💬 **Q: $userQuery**\n\n$ans")
                } else {
                    GeminiSummarizer.SummaryResult.Error("Local AI model is not downloaded yet. Please tap 1-Click Download below.")
                }
            } else if (GeminiSummarizer.isConfigured()) {
                GeminiSummarizer.summarize(ctx, info, forceRefresh = true)
            } else {
                GeminiSummarizer.SummaryResult.Error("No AI engine configured for custom questions.")
            }

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
        chipEngineSelector = view.findViewById(R.id.chip_engine_selector)

        // Prompt chip → opens Prompt Library
        chipActivePrompt.setOnClickListener {
            promptLibraryLauncher.launch(PromptLibraryActivity.createIntent(requireContext()))
        }

        // Engine chip → opens 1-tap Engine Selector Popup
        chipEngineSelector.setOnClickListener {
            showEngineSelectorPopupMenu(it)
        }

        view.findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java).apply {
                putExtra("show_fragment", "org.schabi.newpipe.settings.AiFeaturesSettingsFragment")
            }
            startActivity(intent)
        }

        view.findViewById<Button>(R.id.btn_open_settings)?.let { btn ->
            val activeModel = org.schabi.newpipe.ai.UniversalModelRegistry.getActiveModel(requireContext())
            if (!org.schabi.newpipe.ai.LocalModelEngine.isModelDownloaded(requireContext())) {
                btn.text = "⚡ 1-Click Download ${activeModel.name} (${activeModel.fileSizeMB} MB)"
                btn.setOnClickListener {
                    val downloadId = org.schabi.newpipe.ai.ModelDownloaderManager.startModelDownload(requireContext(), activeModel)
                    btn.isEnabled = false

                    // Launch real-time progress polling coroutine
                    lifecycleScope.launch {
                        while (isActive) {
                            val progress = org.schabi.newpipe.ai.ModelDownloaderManager.getDownloadProgress(requireContext(), downloadId)
                            if (progress.status == android.app.DownloadManager.STATUS_RUNNING) {
                                val downloadedMb = progress.bytesDownloaded / (1024 * 1024)
                                val totalMb = if (progress.totalBytes > 0) progress.totalBytes / (1024 * 1024) else activeModel.fileSizeMB.toLong()
                                btn.text = "⏬ Downloading ${activeModel.name}: ${progress.progressPercent}% (${downloadedMb} MB / ${totalMb} MB)"
                            } else if (progress.status == android.app.DownloadManager.STATUS_SUCCESSFUL || org.schabi.newpipe.ai.LocalModelEngine.isModelDownloaded(requireContext())) {
                                btn.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                btn.text = "✅ ${activeModel.name} Downloaded!"
                                delay(1000)
                                checkStateAndLoad()
                                break
                            } else if (progress.status == android.app.DownloadManager.STATUS_FAILED) {
                                btn.isEnabled = true
                                btn.text = "⚡ Retry 1-Click Download (${activeModel.fileSizeMB} MB)"
                                break
                            }
                            delay(800)
                        }
                    }
                }
            }
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

        view.findViewById<AppCompatButton>(R.id.btn_send_ask)?.setOnClickListener { btn ->
            val etQuery = view.findViewById<android.widget.EditText>(R.id.et_ask_video)
            val queryText = etQuery?.text?.toString()?.trim() ?: ""
            if (queryText.isNotEmpty()) {
                animateAndHaptic(btn) {
                    etQuery?.setText("")
                    runCustomQuestion(queryText)
                }
            }
        }

        refreshPromptChip()
        refreshEngineChip()
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

    private fun refreshEngineChip() {
        val ctx = context ?: return
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        val engineMode = prefs.getString("pref_key_ai_engine_mode", "auto") ?: "auto"
        val isLocalDownloaded = org.schabi.newpipe.ai.LocalModelEngine.isModelDownloaded(ctx)

        chipEngineSelector.text = when (engineMode) {
            "local" -> if (isLocalDownloaded) "🔒 Local AI ▾" else "⚠️ Local AI ▾"
            "gemini" -> if (GeminiSummarizer.isConfigured()) "☁️ Gemini ▾" else "⚠️ Gemini ▾"
            else -> if (isLocalDownloaded) "✨ Auto (Local) ▾" else "✨ Auto (Gemini) ▾"
        }
    }

    private fun showEngineSelectorPopupMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        val ctx = requireContext()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)

        popup.menu.add(0, 1, 0, "✨ Auto-Select (Prefer Local, Fallback to Gemini)")
        popup.menu.add(0, 2, 1, "🔒 Local AI Engine (On-Device GGUF)")
        popup.menu.add(0, 3, 2, "☁️ Cloud Gemini Engine (Google AI API)")
        popup.menu.add(0, 4, 3, "⚙️ Configure Engine Settings…")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> prefs.edit().putString("pref_key_ai_engine_mode", "auto").apply()
                2 -> prefs.edit().putString("pref_key_ai_engine_mode", "local").apply()
                3 -> prefs.edit().putString("pref_key_ai_engine_mode", "gemini").apply()
                4 -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
            refreshEngineChip()
            runSummarize(forceRefresh = true)
            true
        }
        popup.show()
    }

    private fun checkStateAndLoad() {
        if (streamInfo == null || streamInfo.id.isNullOrEmpty()) {
            showState(stateReady)
            return
        }

        val isLocalReady = org.schabi.newpipe.ai.LocalModelEngine.isModelDownloaded(requireContext())
        if (!isLocalReady && !GeminiSummarizer.isConfigured()) {
            showState(stateNoKey)
            return
        }

        val activePrompt = PromptLibrary.getActivePrompt(requireContext())
        val promptId = activePrompt?.id ?: PromptLibrary.DEFAULT_PROMPT_ID
        val isCached = GeminiSummarizer.hasCachedSummary(streamInfo.id, promptId)

        if (isCached || isLocalReady) {
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
}
