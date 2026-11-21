package org.schabi.newpipe.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.diagnostics.DiagnosticLog;
import android.widget.Toast;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.local.feed.notifications.NotificationWorker;
import org.schabi.newpipe.util.PicassoHelper;

import java.util.Optional;

public class DebugSettingsFragment extends BasePreferenceFragment {
    private static final String DUMMY = "Dummy";

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final Preference exportDiagnostics = findPreference("pipe_export_diagnostics");
        final Preference clearDiagnostics = findPreference("pipe_clear_diagnostics");
        if (exportDiagnostics != null) {
            exportDiagnostics.setOnPreferenceClickListener(preference -> {
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
        if (clearDiagnostics != null) {
            clearDiagnostics.setOnPreferenceClickListener(preference -> {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Clear diagnostic history?")
                        .setMessage("Remove the locally stored diagnostic events?")
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Clear", (dialog, which) -> {
                            DiagnosticLog.clear(requireContext());
                            Toast.makeText(requireContext(),
                                    "Diagnostic history cleared", Toast.LENGTH_SHORT).show();
                        }).show();
                return true;
            });
        }

        final Preference allowHeapDumpingPreference =
                findPreference(getString(R.string.allow_heap_dumping_key));
        final Preference showMemoryLeaksPreference =
                findPreference(getString(R.string.show_memory_leaks_key));
        final Preference showImageIndicatorsPreference =
                findPreference(getString(R.string.show_image_indicators_key));
        final Preference checkNewStreamsPreference =
                findPreference(getString(R.string.check_new_streams_key));
        final Preference crashTheAppPreference =
                findPreference(getString(R.string.crash_the_app_key));
        final Preference showErrorSnackbarPreference =
                findPreference(getString(R.string.show_error_snackbar_key));
        final Preference createErrorNotificationPreference =
                findPreference(getString(R.string.create_error_notification_key));
        final Preference visitorDataPreference =
                findPreference(getString(R.string.youtube_visitor_data));

        assert allowHeapDumpingPreference != null;
        assert showMemoryLeaksPreference != null;
        assert showImageIndicatorsPreference != null;
        assert checkNewStreamsPreference != null;
        assert crashTheAppPreference != null;
        assert showErrorSnackbarPreference != null;
        assert createErrorNotificationPreference != null;
        assert visitorDataPreference != null;

        final Optional<DebugSettingsBVDLeakCanaryAPI> optBVLeakCanary = getBVDLeakCanary();

        allowHeapDumpingPreference.setEnabled(optBVLeakCanary.isPresent());
        showMemoryLeaksPreference.setEnabled(optBVLeakCanary.isPresent());

        if (optBVLeakCanary.isPresent()) {
            final DebugSettingsBVDLeakCanaryAPI pdLeakCanary = optBVLeakCanary.get();

            showMemoryLeaksPreference.setOnPreferenceClickListener(preference -> {
                startActivity(pdLeakCanary.getNewLeakDisplayActivityIntent());
                return true;
            });
        } else {
            allowHeapDumpingPreference.setSummary(R.string.leak_canary_not_available);
            showMemoryLeaksPreference.setSummary(R.string.leak_canary_not_available);
        }

        showImageIndicatorsPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            PicassoHelper.setIndicatorsEnabled((Boolean) newValue);
            return true;
        });

        checkNewStreamsPreference.setOnPreferenceClickListener(preference -> {
            NotificationWorker.runNow(preference.getContext());
            return true;
        });

        crashTheAppPreference.setOnPreferenceClickListener(preference -> {
            throw new RuntimeException(DUMMY);
        });

        showErrorSnackbarPreference.setOnPreferenceClickListener(preference -> {
            ErrorUtil.showUiErrorSnackbar(DebugSettingsFragment.this,
                    DUMMY, new RuntimeException(DUMMY));
            return true;
        });

        createErrorNotificationPreference.setOnPreferenceClickListener(preference -> {
            ErrorUtil.createNotification(requireContext(),
                    new ErrorInfo(new RuntimeException(DUMMY), UserAction.UI_ERROR, DUMMY));
            return true;
        });
    }

    /**
     * Tries to find the {@link DebugSettingsBVDLeakCanaryAPI#IMPL_CLASS} and loads it if available.
     * @return An {@link Optional} which is empty if the implementation class couldn't be loaded.
     */
    private Optional<DebugSettingsBVDLeakCanaryAPI> getBVDLeakCanary() {
        try {
            // Try to find the implementation of the LeakCanary API
            return Optional.of((DebugSettingsBVDLeakCanaryAPI)
                    Class.forName(DebugSettingsBVDLeakCanaryAPI.IMPL_CLASS)
                            .getDeclaredConstructor()
                            .newInstance());
        } catch (final Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Build variant dependent (BVD) leak canary API for this fragment.
     * Why is LeakCanary not used directly? Because it can't be assured
     */
    public interface DebugSettingsBVDLeakCanaryAPI {
        String IMPL_CLASS =
                "org.schabi.newpipe.settings.DebugSettingsBVDLeakCanary";

        Intent getNewLeakDisplayActivityIntent();
    }
}
