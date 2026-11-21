package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.diagnostics.DiagnosticLog;
import androidx.preference.Preference;
import android.widget.Toast;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ReleaseVersionUtil;

public class MainSettingsFragment extends BasePreferenceFragment {
    public static final boolean DEBUG = MainActivity.DEBUG;

    private SettingsActivity settingsActivity;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        setHasOptionsMenu(true); // Otherwise onCreateOptionsMenu is not called

        // Check if the app is updatable
//        if (!ReleaseVersionUtil.isReleaseApk()) {
//            getPreferenceScreen().removePreference(
//                    findPreference(getString(R.string.update_pref_screen_key)));
//
//            defaultPreferences.edit().putBoolean(getString(R.string.update_app_key), false).apply();
//        }

        final Preference export = findPreference("pipe_export_diagnostics_main");
        if (export != null) {
            export.setOnPreferenceClickListener(preference -> {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q
                        && androidx.core.content.ContextCompat.checkSelfPermission(
                        requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 8401);
                    Toast.makeText(requireContext(),
                            "Allow storage access, then tap Export again", Toast.LENGTH_LONG).show();
                    return true;
                }
                final android.content.Context appContext =
                        requireContext().getApplicationContext();
                new Thread(() -> {
                    try {
                        final String fileName = DiagnosticLog.saveToDownloads(appContext);
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(),
                                            "Saved to Downloads/" + fileName,
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    } catch (final Exception error) {
                        DiagnosticLog.error("DIAGNOSTICS", "EXPORT_FAILED", error,
                                "direct Downloads save");
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(),
                                            "Failed to save diagnostics to Downloads",
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }, "PipeDiagnosticExport").start();
                return true;
            });
        }

        // Keep potentially dangerous debug-only controls out of release builds.
        if (!DEBUG) {
            getPreferenceScreen().removePreference(
                    findPreference(getString(R.string.debug_pref_screen_key)));
        }
    }

    @Override
    public void onCreateOptionsMenu(
            @NonNull final Menu menu,
            @NonNull final MenuInflater inflater
    ) {
        super.onCreateOptionsMenu(menu, inflater);

        // -- Link settings activity and register menu --
        settingsActivity = (SettingsActivity) getActivity();

        inflater.inflate(R.menu.menu_settings_main_fragment, menu);

        final MenuItem menuSearchItem = menu.getItem(0);

        settingsActivity.setMenuSearchItem(menuSearchItem);

        menuSearchItem.setOnMenuItemClickListener(ev -> {
            settingsActivity.setSearchActive(true);
            return true;
        });
    }

    @Override
    public void onDestroy() {
        // Unlink activity so that we don't get memory problems
        if (settingsActivity != null) {
            settingsActivity.setMenuSearchItem(null);
            settingsActivity = null;
        }
        super.onDestroy();
    }
}
