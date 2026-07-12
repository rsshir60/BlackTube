package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import com.blacktube.app.ai.GeminiSummarizer;

import org.schabi.newpipe.R;

/**
 * BlackTube AI Features Settings Fragment.
 * Allows users to configure the Gemini AI summarizer.
 */
public class AiFeaturesSettingsFragment extends BasePreferenceFragment {

    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        final Uri uri = result.getData().getData();
                        if (uri != null) {
                            readAndSaveCustomPrompt(uri);
                        }
                    }
                }
        );
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.ai_features_settings);

        final SwitchPreferenceCompat enablePref =
                findPreference(getString(R.string.gemini_enable_key));
        final EditTextPreference apiKeyPref =
                findPreference(getString(R.string.gemini_api_key_key));
        final Preference clearCachePref =
                findPreference(getString(R.string.clear_ai_cache_key));

        if (apiKeyPref != null) {
            apiKeyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                final String key = (String) newValue;
                GeminiSummarizer.configure(key);
                return true;
            });
        }

        if (clearCachePref != null) {
            clearCachePref.setOnPreferenceClickListener(preference -> {
                // GeminiSummarizer.clearCache();
                Toast.makeText(requireContext(), R.string.ai_cache_cleared,
                        Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        final Preference importPromptPref = findPreference(getString(R.string.import_ai_prompt_key));
        if (importPromptPref != null) {
            importPromptPref.setOnPreferenceClickListener(preference -> {
                final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*"); // Android file picker often struggles with text/markdown
                filePickerLauncher.launch(intent);
                return true;
            });
        }
    }

    private void readAndSaveCustomPrompt(final Uri uri) {
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            final String promptContent = reader.lines().collect(Collectors.joining("\n"));
            
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            prefs.edit().putString(getString(R.string.custom_ai_prompt_value_key), promptContent).apply();
            
            Toast.makeText(requireContext(), "Custom prompt loaded successfully!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to read prompt file", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}

