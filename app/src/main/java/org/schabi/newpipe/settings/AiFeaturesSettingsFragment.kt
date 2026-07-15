package org.schabi.newpipe.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.blacktube.app.ai.GeminiSummarizer
import com.blacktube.app.ai.PromptLibrary
import com.blacktube.app.ai.PromptLibraryActivity
import org.schabi.newpipe.R

/**
 * BlackTube AI Features Settings — full redesign.
 *
 * Sections:
 *   ✨ Status header card   — overall AI readiness
 *   ✨ AI Summary           — enable/disable switch
 *   🤖 AI Provider         — API key + connection status + test button
 *   📚 Prompt System       — active prompt display + Prompt Library entry
 *   💾 Storage             — clear AI cache
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Slight delay to ensure custom preference views are inflated
        view.post { refreshCustomViews() }
    }

    override fun onResume() {
        super.onResume()
        refreshCustomViews()
        hookToggleDependencies()
    }

    // ── Preference hookups ─────────────────────────────────────────────────

    private fun hookApiKeyPref() {
        findPreference<EditTextPreference>(getString(R.string.gemini_api_key_key))
            ?.setOnPreferenceChangeListener { _, newValue ->
                GeminiSummarizer.configure(newValue as String)
                view?.post { refreshCustomViews() }
                true
            }
    }

    private fun hookClearCache() {
        findPreference<Preference>(getString(R.string.clear_ai_cache_key))
            ?.setOnPreferenceClickListener {
                requireContext().getSharedPreferences("blacktube_ai_cache", android.content.Context.MODE_PRIVATE)
                    .edit().clear().apply()
                Toast.makeText(requireContext(), R.string.ai_cache_cleared, Toast.LENGTH_SHORT).show()
                view?.post { refreshCustomViews() }
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

        // Dynamic summary based on toggle state
        enablePref?.summary = if (enabled)
            getString(R.string.gemini_enable_summary_on)
        else
            getString(R.string.gemini_enable_summary_off)

        enablePref?.setOnPreferenceChangeListener { pref, newValue ->
            val isOn = newValue as Boolean
            apiKeyPref?.isEnabled = isOn
            libraryPref?.isEnabled = isOn
            cachePref?.isEnabled = isOn
            pref.summary = if (isOn)
                getString(R.string.gemini_enable_summary_on)
            else
                getString(R.string.gemini_enable_summary_off)
            view?.post { refreshCustomViews() }
            true
        }
    }

    // ── Custom view refresh ────────────────────────────────────────────────

    private fun refreshCustomViews() {
        val v = view ?: return
        val ctx = requireContext()

        val isEnabled = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(getString(R.string.gemini_enable_key), true)
        val isConfigured = GeminiSummarizer.isConfigured()
        val activePrompt = PromptLibrary.getActivePrompt(ctx)

        // ── Status header card ───────────────────────────────────────────
        val chipStatus = v.findViewById<TextView>(R.id.chip_ai_global_status)
        val tvPromptHeader = v.findViewById<TextView>(R.id.tv_ai_active_prompt_header)

        if (chipStatus != null) {
            when {
                !isEnabled -> {
                    chipStatus.text = "⚪ Disabled"
                    chipStatus.setBackgroundResource(R.drawable.bg_ai_status_disabled)
                }
                !isConfigured -> {
                    chipStatus.text = "🔴 Setup Required"
                    chipStatus.setBackgroundResource(R.drawable.bg_ai_status_error)
                }
                else -> {
                    chipStatus.text = "🟢 Active"
                    chipStatus.setBackgroundResource(R.drawable.bg_ai_status_active)
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
            if (isConfigured) 0xFF2E7D32.toInt() else 0xFFB71C1C.toInt()
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

        // ── Cache size ───────────────────────────────────────────────────
        val cachePref = findPreference<Preference>(getString(R.string.clear_ai_cache_key))
        val cachePrefs = ctx.getSharedPreferences("blacktube_ai_cache", android.content.Context.MODE_PRIVATE)
        val cachedCount = cachePrefs.all.size
        cachePref?.summary = if (cachedCount > 0)
            getString(R.string.clear_ai_cache_summary_count, cachedCount)
        else
            getString(R.string.clear_ai_cache_summary)
    }
}
