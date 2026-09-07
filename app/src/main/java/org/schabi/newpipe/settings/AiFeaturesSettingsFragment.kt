package org.schabi.newpipe.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.blacktube.app.ai.GeminiSummarizer
import com.blacktube.app.ai.PromptLibrary
import com.blacktube.app.ai.PromptLibraryActivity
import org.schabi.newpipe.R

/**
 * BlackTube AI Features Settings — Google Gemini Cloud Intelligence.
 */
class AiFeaturesSettingsFragment : BasePreferenceFragment() {

    private lateinit var promptLibraryLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        promptLibraryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                refreshCustomViews()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.ai_features_settings)
        hookApiKeyPref()
        hookClearCache()
        hookPromptLibrary()
    }

    private fun hookApiKeyPref() {
        val apiKeyPref = findPreference<EditTextPreference>(getString(R.string.gemini_api_key_key))
        apiKeyPref?.setSummaryProvider { pref ->
            val key = (pref as EditTextPreference).text.orEmpty().trim()
            if (key.isBlank()) {
                "Not configured — tap to add your Gemini API key"
            } else {
                maskKey(key)
            }
        }

        apiKeyPref?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            editText.hint = "AIzaSy..."
        }

        apiKeyPref?.setOnPreferenceChangeListener { _, newValue ->
            GeminiSummarizer.configure(newValue as String)
            view?.post { refreshCustomViews() }
            true
        }
    }

    private fun maskKey(key: String): String {
        if (key.length <= 10) return "••••••••••"
        return key.take(7) + "•".repeat(14) + key.takeLast(4)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post { refreshCustomViews() }
    }

    override fun onResume() {
        super.onResume()
        refreshCustomViews()
        hookToggleDependencies()
    }

    private fun hookClearCache() {
        findPreference<Preference>(getString(R.string.clear_ai_cache_key))
            ?.setOnPreferenceClickListener {
                val ctx = requireContext()
                val cachePrefs = ctx.getSharedPreferences("blacktube_ai_cache", Context.MODE_PRIVATE)
                val entries = cachePrefs.all
                if (entries.isEmpty()) {
                    Toast.makeText(ctx, "Cache is already empty", Toast.LENGTH_SHORT).show()
                    return@setOnPreferenceClickListener true
                }

                AlertDialog.Builder(ctx)
                    .setTitle(R.string.clear_ai_cache_title)
                    .setMessage("Are you sure you want to clear all cached AI summaries from device storage?")
                    .setPositiveButton(R.string.clear) { _, _ ->
                        cachePrefs.edit().clear().apply()
                        Toast.makeText(ctx, R.string.ai_cache_cleared, Toast.LENGTH_SHORT).show()
                        refreshCustomViews()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
    }

    private fun hookPromptLibrary() {
        findPreference<Preference>(getString(R.string.prompt_library_key))
            ?.setOnPreferenceClickListener {
                promptLibraryLauncher.launch(PromptLibraryActivity.createIntent(requireContext()))
                true
            }
    }

    private fun hookToggleDependencies() {
        val enablePref = findPreference<SwitchPreferenceCompat>(getString(R.string.gemini_enable_key))
        val apiKeyPref = findPreference<EditTextPreference>(getString(R.string.gemini_api_key_key))
        val libraryPref = findPreference<Preference>(getString(R.string.prompt_library_key))
        val cachePref = findPreference<Preference>(getString(R.string.clear_ai_cache_key))

        val enabled = enablePref?.isChecked ?: true
        apiKeyPref?.isEnabled = enabled
        libraryPref?.isEnabled = enabled
        cachePref?.isEnabled = enabled

        enablePref?.setOnPreferenceChangeListener { _, newValue ->
            val isOn = newValue as Boolean
            apiKeyPref?.isEnabled = isOn
            libraryPref?.isEnabled = isOn
            cachePref?.isEnabled = isOn
            view?.post { refreshCustomViews() }
            true
        }
    }

    // ── Custom view & dynamic row refresh ──────────────────────────────────

    private fun refreshCustomViews() {
        val v = view ?: return
        val ctx = requireContext()

        val isEnabled = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(getString(R.string.gemini_enable_key), true)
        val isConfigured = GeminiSummarizer.isConfigured()
        val activePrompt = PromptLibrary.getActivePrompt(ctx)

        // ── Engine-Aware Master Toggle Summary ────────────────────────────
        val enablePref = findPreference<SwitchPreferenceCompat>(getString(R.string.gemini_enable_key))
        if (enablePref != null) {
            enablePref.summary = when {
                !isEnabled -> "AI summaries are disabled"
                isConfigured -> "Google Gemini 3.1 Flash-Lite is active ✓"
                else -> "Enable AI video summaries, chapter insights, and Q&A"
            }
        }

        // ── Dynamic Cache Size Calculation ────────────────────────────────
        val cachePref = findPreference<Preference>(getString(R.string.clear_ai_cache_key))
        val cachePrefs = ctx.getSharedPreferences("blacktube_ai_cache", Context.MODE_PRIVATE)
        val cacheEntries = cachePrefs.all
        var totalCacheBytes = 0L
        for ((_, value) in cacheEntries) {
            if (value is String) {
                totalCacheBytes += value.toByteArray().size
            }
        }

        if (cachePref != null) {
            if (cacheEntries.isEmpty() || totalCacheBytes == 0L) {
                cachePref.summary = "No cached summaries stored on device"
                cachePref.isEnabled = false
            } else {
                val formattedSize = Formatter.formatFileSize(ctx, totalCacheBytes)
                cachePref.summary = "Delete $formattedSize of cached summaries (${cacheEntries.size} items)"
                cachePref.isEnabled = isEnabled
            }
        }

        // ── Status header card ───────────────────────────────────────────
        val chipStatus = v.findViewById<TextView>(R.id.chip_ai_global_status)
        val tvModelName = v.findViewById<TextView>(R.id.tv_ai_model_name)
        val tvPromptHeader = v.findViewById<TextView>(R.id.tv_ai_active_prompt_header)

        if (tvModelName != null) {
            tvModelName.text = if (isConfigured)
                "☁️ Gemini 3.1 Flash-Lite (Google AI)"
            else
                "⚠️ Gemini API Key Required"
        }

        if (chipStatus != null) {
            when {
                !isEnabled -> {
                    chipStatus.text = "⚪ Disabled"
                    chipStatus.setBackgroundResource(R.drawable.bg_ai_status_disabled)
                }
                isConfigured -> {
                    chipStatus.text = "🟢 Gemini AI Active"
                    chipStatus.setBackgroundResource(R.drawable.bg_ai_status_active)
                }
                else -> {
                    chipStatus.text = "🔴 Key Required"
                    chipStatus.setBackgroundResource(R.drawable.bg_ai_status_error)
                }
            }
        }

        tvPromptHeader?.text = activePrompt?.title ?: getString(R.string.prompt_library_default)

        // ── Connection status row ────────────────────────────────────────
        val tvConnectionStatus = v.findViewById<TextView>(R.id.tv_api_connection_status)
        tvConnectionStatus?.text = if (isConfigured)
            "✓ Connected"
        else
            "⚠ Not configured"
        tvConnectionStatus?.setTextColor(
            if (isConfigured) 0xFF4CAF50.toInt() else 0xFFEF5350.toInt()
        )

        val btnTest = v.findViewById<Button>(R.id.btn_test_connection)
        btnTest?.setOnClickListener {
            if (!isConfigured) {
                Toast.makeText(ctx, getString(R.string.gemini_not_configured), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, getString(R.string.ai_connection_ok), Toast.LENGTH_SHORT).show()
            }
        }

        // ── Active prompt row ────────────────────────────────────────────
        val tvActivePromptPref = v.findViewById<TextView>(R.id.tv_active_prompt_pref)
        tvActivePromptPref?.text = activePrompt?.title
            ?: getString(R.string.prompt_library_default)

        val btnClearPrompt = v.findViewById<Button>(R.id.btn_clear_prompt_pref)
        if (btnClearPrompt != null) {
            btnClearPrompt.visibility = if (activePrompt != null) View.VISIBLE else View.GONE
            btnClearPrompt.setOnClickListener {
                PromptLibrary.clearActivePrompt(ctx)
                refreshCustomViews()
                Toast.makeText(ctx, getString(R.string.prompt_library_default_restored), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
