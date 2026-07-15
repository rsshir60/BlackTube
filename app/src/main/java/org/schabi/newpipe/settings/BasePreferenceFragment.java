package org.schabi.newpipe.settings;

import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.Objects;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    protected static final boolean DEBUG = MainActivity.DEBUG;

    SharedPreferences defaultPreferences;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        super.onCreate(savedInstanceState);
    }

    protected void addPreferencesFromResourceRegistry() {
        addPreferencesFromResource(
                SettingsResourceRegistry.getInstance().getPreferencesResId(this.getClass()));
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        setDivider(null);
        tintSwitchPreferences();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
    }

    @NonNull
    public final <T extends Preference> T requirePreference(@StringRes final int resId) {
        final T preference = findPreference(getString(resId));
        Objects.requireNonNull(preference);
        return preference;
    }

    private void tintSwitchPreferences() {
        final ColorStateList thumbTint = AppCompatResources.getColorStateList(
                requireContext(), R.color.bt_switch_thumb_selector);
        final ColorStateList trackTint = AppCompatResources.getColorStateList(
                requireContext(), R.color.bt_switch_track_selector);
        final RecyclerView listView = getListView();

        listView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull final View view) {
                tintSwitchesInView(view, thumbTint, trackTint);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull final View view) {
                // No-op.
            }
        });

        for (int i = 0; i < listView.getChildCount(); i++) {
            tintSwitchesInView(listView.getChildAt(i), thumbTint, trackTint);
        }
    }

    private void tintSwitchesInView(@NonNull final View view,
                                    @NonNull final ColorStateList thumbTint,
                                    @NonNull final ColorStateList trackTint) {
        if (view instanceof SwitchCompat) {
            final SwitchCompat switchView = (SwitchCompat) view;
            switchView.setThumbTintList(thumbTint);
            switchView.setTrackTintList(trackTint);
            return;
        }

        if (!(view instanceof ViewGroup)) {
            return;
        }

        final ViewGroup viewGroup = (ViewGroup) view;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            tintSwitchesInView(viewGroup.getChildAt(i), thumbTint, trackTint);
        }
    }
}
