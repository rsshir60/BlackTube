package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ai.LocalModelEngine;
import org.schabi.newpipe.ai.LocalModelInfo;
import org.schabi.newpipe.ai.ModelDownloaderManager;
import org.schabi.newpipe.ai.UniversalModelRegistry;

import java.io.File;

public class LocalAiModelSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.local_ai_model_settings);

        final ListPreference modelSelectPref = findPreference(UniversalModelRegistry.PREF_ACTIVE_MODEL_ID);
        final Preference downloadPref = findPreference("pref_key_local_ai_download");
        final Preference statusPref = findPreference("pref_key_local_ai_status");

        if (modelSelectPref != null) {
            final LocalModelInfo activeModel = UniversalModelRegistry.INSTANCE.getActiveModel(requireContext());
            modelSelectPref.setValue(activeModel.getId());
            modelSelectPref.setSummary(activeModel.getName() + " — " + activeModel.getDescription());

            modelSelectPref.setOnPreferenceChangeListener((preference, newValue) -> {
                final String selectedId = (String) newValue;
                UniversalModelRegistry.INSTANCE.setActiveModel(requireContext(), selectedId);
                LocalModelEngine.INSTANCE.release();
                
                final LocalModelInfo newActive = UniversalModelRegistry.INSTANCE.getActiveModel(requireContext());
                modelSelectPref.setSummary(newActive.getName() + " — " + newActive.getDescription());
                updateStatus(statusPref, downloadPref);
                return true;
            });
        }

        updateStatus(statusPref, downloadPref);

        if (downloadPref != null) {
            downloadPref.setOnPreferenceClickListener(p -> {
                final LocalModelInfo activeModel = UniversalModelRegistry.INSTANCE.getActiveModel(requireContext());
                final boolean isDownloaded = UniversalModelRegistry.INSTANCE.isModelDownloaded(requireContext(), activeModel);
                if (isDownloaded) {
                    final File modelFile = UniversalModelRegistry.INSTANCE.getModelFile(requireContext(), activeModel);
                    if (modelFile.exists()) {
                        modelFile.delete();
                    }
                    Toast.makeText(requireContext(), activeModel.getName() + " deleted from storage", Toast.LENGTH_SHORT).show();
                    updateStatus(statusPref, downloadPref);
                } else {
                    ModelDownloaderManager.INSTANCE.startModelDownload(requireContext(), activeModel);
                    Toast.makeText(requireContext(), "Downloading " + activeModel.getName() + " in background...", Toast.LENGTH_SHORT).show();
                    updateStatus(statusPref, downloadPref);
                }
                return true;
            });
        }
    }

    private void updateStatus(@Nullable final Preference statusPref, @Nullable final Preference downloadPref) {
        final LocalModelInfo activeModel = UniversalModelRegistry.INSTANCE.getActiveModel(requireContext());
        final boolean isDownloaded = UniversalModelRegistry.INSTANCE.isModelDownloaded(requireContext(), activeModel);

        if (statusPref != null) {
            if (isDownloaded) {
                statusPref.setSummary("Active Model: " + activeModel.getName() + " is ready for 100% offline summaries.");
            } else {
                statusPref.setSummary("Active Model: " + activeModel.getName() + " (" + activeModel.getFileSizeMB() + " MB) is not downloaded.");
            }
        }
        if (downloadPref != null) {
            if (isDownloaded) {
                downloadPref.setTitle("Delete Active Model (" + activeModel.getFileSizeMB() + " MB)");
                downloadPref.setSummary("Remove " + activeModel.getName() + " from device storage.");
            } else {
                downloadPref.setTitle("Download Active Model (" + activeModel.getFileSizeMB() + " MB)");
                downloadPref.setSummary("Download " + activeModel.getName() + " from HuggingFace.");
            }
        }
    }
}
