package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.FontHelper;
import org.schabi.newpipe.util.ThemeHelper;

public class AppearanceSettingsFragment extends BasePreferenceFragment {

    private final ActivityResultLauncher<Intent> fontPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    final Uri uri = result.getData().getData();
                    if (uri != null && getContext() != null) {
                        final boolean success = FontHelper.importCustomFont(getContext(), uri);
                        if (success) {
                            Toast.makeText(getContext(), R.string.custom_font_imported_success, Toast.LENGTH_LONG).show();
                            if (getActivity() != null) {
                                ActivityCompat.recreate(getActivity());
                            }
                        } else {
                            Toast.makeText(getContext(), R.string.custom_font_import_failed, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final String nightThemeKey = getString(R.string.night_theme_key);
        final String startNightThemeKey = defaultPreferences
                .getString(nightThemeKey, getString(R.string.default_night_theme_value));
        final Preference nightThemePreference = findPreference(nightThemeKey);
        if (nightThemePreference != null) {
            nightThemePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                applyThemeChange(startNightThemeKey, nightThemeKey, newValue);
                return false;
            });
        }

        final String showKiosksKey = getString(R.string.show_kiosks_key);
        final Preference showKiosksPref = findPreference(showKiosksKey);
        if (showKiosksPref != null) {
            showKiosksPref.setOnPreferenceChangeListener((preference, newValue) -> {
                defaultPreferences.edit()
                        .putBoolean(Constants.KEY_DRAWER_CHANGE, true)
                        .apply();
                return true;
            });
        }

        final String fontTypeKey = getString(R.string.pref_key_app_font_type);
        final Preference fontTypePref = findPreference(fontTypeKey);
        if (fontTypePref != null) {
            fontTypePref.setOnPreferenceChangeListener((preference, newValue) -> {
                FontHelper.resetFontCache();
                if (FontHelper.FONT_TYPE_CUSTOM.equals(newValue) && getContext() != null) {
                    final boolean fontExists = FontHelper.getCustomFontFile(getContext()).exists();
                    if (!fontExists) {
                        launchFontPicker();
                        return true;
                    }
                }
                if (getActivity() != null) {
                    ActivityCompat.recreate(getActivity());
                }
                return true;
            });
        }
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (getString(R.string.caption_settings_key).equals(preference.getKey())) {
            try {
                startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
            } catch (final ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.general_error, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (getString(R.string.pref_key_import_custom_font).equals(preference.getKey())) {
            launchFontPicker();
            return true;
        }

        return super.onPreferenceTreeClick(preference);
    }

    private void launchFontPicker() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        final String[] mimeTypes = {"font/ttf", "font/otf", "font/opentype", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        try {
            fontPickerLauncher.launch(intent);
        } catch (final Exception e) {
            Toast.makeText(getContext(), R.string.custom_font_import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void applyThemeChange(final String beginningThemeKey,
                                  final String themeKey,
                                  final Object newValue) {
        defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
        defaultPreferences.edit().putString(themeKey, newValue.toString()).apply();

        ThemeHelper.setDayNightMode(requireContext(), newValue.toString());

        if (!newValue.equals(beginningThemeKey) && getActivity() != null) {
            // if it's not the current theme
            ActivityCompat.recreate(getActivity());
        }
    }
}
