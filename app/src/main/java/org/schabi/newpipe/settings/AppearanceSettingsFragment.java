package org.schabi.newpipe.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.FontHelper;
import org.schabi.newpipe.util.ThemeHelper;

public class AppearanceSettingsFragment extends BasePreferenceFragment {

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
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void applyThemeChange(final String startNightThemeKey,
                                  final String nightThemeKey,
                                  final Object newValue) {
        defaultPreferences.edit()
                .putString(nightThemeKey, newValue.toString())
                .apply();

        if (!startNightThemeKey.equals(newValue.toString()) && getActivity() != null) {
            ThemeHelper.setDayNightMode(requireContext());
            ActivityCompat.recreate(getActivity());
        }
    }
}
