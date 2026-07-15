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
        findPreference(showKiosksKey).setOnPreferenceChangeListener((preference, newValue) -> {
            defaultPreferences.edit()
                    .putBoolean(Constants.KEY_DRAWER_CHANGE, true)
                    .apply();
            return true;
        });
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (getString(R.string.caption_settings_key).equals(preference.getKey())) {
            try {
                startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
            } catch (final ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.general_error, Toast.LENGTH_SHORT).show();
            }
        }

        return super.onPreferenceTreeClick(preference);
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
