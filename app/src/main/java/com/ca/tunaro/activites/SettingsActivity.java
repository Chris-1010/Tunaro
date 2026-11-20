package com.ca.tunaro.activites;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.services.AutomaticFetcher;
import com.ca.tunaro.utils.AppExecutors;
import com.ca.tunaro.utils.DeviceChooser;
import com.ca.tunaro.utils.PlaylistCache;
import com.ca.tunaro.utils.SongCache;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";

    private SwitchCompat deviceCheckSwitch;
    private EditText deviceNameInput;
    private TextView deviceNameLabel;
    private Button discoverDevicesButton;
    private TextView availableDevicesText;
    private SharedPreferences prefs;
    private final AutomaticFetcher.FetchCompletionListener fetchListener = this::onFetchCompleted;
    private Button importButton;
    private Button exportButton;
    private Button restoreBackupButton;
    private View dataProgress;
    private boolean dataJobRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("TunaroPrefs", MODE_PRIVATE);

        setupBackButton();
        setupImportExportButtons();

        setupSnippetPlaybackSettings();

        setupEndOfQueueSettings();

        setupDeviceChooser();
        setupDeviceCheckSettings();
      
        setupAutomaticFetcherSettings();
    }

    private void setupBackButton() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
    }

    //#region Import/Export Option

    private void setupImportExportButtons() {
        importButton = findViewById(R.id.import_button);
        exportButton = findViewById(R.id.export_button);
        Button clearCacheButton = findViewById(R.id.clear_cache_button);
        restoreBackupButton = findViewById(R.id.restore_backup_button);
        dataProgress = findViewById(R.id.data_progress);
        clearCacheButton.setOnClickListener(v -> clearAllCaches());

        importButton.setOnClickListener(v -> importData());
        exportButton.setOnClickListener(v -> exportData());
        restoreBackupButton.setOnClickListener(v -> startActivity(
                new android.content.Intent(this, BackupRestoreActivity.class)));
    }

    // Handle the import button click
    public void importData() {
        importLauncher.launch(new String[]{"application/json"});
    }

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) return;
                android.content.Context appContext = getApplicationContext();
                runDataJob(importButton, "Importing\u2026", () -> {
                    try {
                        DatabaseHelper.ImportStats stats = DatabaseHelper.importFromUri(appContext, uri);
                        return stats.getSummary();
                    } catch (Exception e) {
                        Log.e(TAG, "Import failed", e);
                        return "Import failed. Check that the file is a Tunaro backup.";
                    }
                });
            }
    );

    // Handle the export button click
    public void exportData() {
        String fileName = "tunaro_backup_" +
                new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date()) +
                ".json";
        exportLauncher.launch(fileName);
    }

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri == null) return;
                android.content.Context appContext = getApplicationContext();
                runDataJob(exportButton, "Exporting\u2026", () -> {
                    try {
                        DatabaseHelper.writeExportToUri(appContext, uri);
                        return "Data exported";
                    } catch (Exception e) {
                        Log.e(TAG, "Export failed", e);
                        return "Export failed. Try again or pick another location.";
                    }
                });
            }
    );

    /**
     * Runs an import/export on the I/O pool with inline progress: the data buttons are disabled,
     * {@code busyButton} shows {@code busyLabel}, and the job's result message is shown when done.
     */
    private void runDataJob(Button busyButton, String busyLabel,
                            java.util.concurrent.Callable<String> job) {
        if (dataJobRunning) return;
        dataJobRunning = true;
        CharSequence idleLabel = busyButton.getText();
        setDataButtonsEnabled(false);
        busyButton.setText(busyLabel);
        dataProgress.setVisibility(View.VISIBLE);

        AppExecutors.io().execute(() -> {
            String message;
            try {
                message = job.call();
            } catch (Exception e) {
                Log.e(TAG, "Data job failed", e);
                message = "Something went wrong. Try again.";
            }
            String result = message;
            runOnUiThread(() -> {
                dataJobRunning = false;
                if (isDestroyed()) return;
                busyButton.setText(idleLabel);
                setDataButtonsEnabled(true);
                dataProgress.setVisibility(View.GONE);
                showMessage(result);
            });
        });
    }

    private void setDataButtonsEnabled(boolean enabled) {
        importButton.setEnabled(enabled);
        exportButton.setEnabled(enabled);
        restoreBackupButton.setEnabled(enabled);
    }

    //#endregion

    //#region Snippet Playback Option

    private void setupSnippetPlaybackSettings() {
        android.widget.RadioGroup group = findViewById(R.id.snippet_default_mode_group);

        // Reflect the saved default in the radio selection.
        switch (PlaybackManager.getDefaultSnippetEndMode(this)) {
            case LOOP:
                group.check(R.id.snippet_mode_loop);
                break;
            case DETACH:
                group.check(R.id.snippet_mode_detach);
                break;
            case STOP:
            default:
                group.check(R.id.snippet_mode_stop);
                break;
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            PlaybackManager.SnippetEndMode mode;
            if (checkedId == R.id.snippet_mode_loop) {
                mode = PlaybackManager.SnippetEndMode.LOOP;
            } else if (checkedId == R.id.snippet_mode_detach) {
                mode = PlaybackManager.SnippetEndMode.DETACH;
            } else {
                mode = PlaybackManager.SnippetEndMode.STOP;
            }
            prefs.edit().putString(
                    PlaybackManager.PREF_SNIPPET_DEFAULT_MODE, mode.name()).apply();
        });
    }
  
    //#endregion

    //#region End of Queue Option

    // The end-of-queue boundary policy (ADR-0002). Default false = Recommendations
    // (let Spotify autoplay continue); true = Stop (pause at the boundary). Read by
    // PlaybackManager.currentBoundaryMode() from the same TunaroPrefs key.
    private void setupEndOfQueueSettings() {
        SwitchCompat stopAtQueueEndSwitch = findViewById(R.id.stop_at_queue_end_switch);
        stopAtQueueEndSwitch.setChecked(prefs.getBoolean("stop_at_queue_end", false));
        stopAtQueueEndSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("stop_at_queue_end", isChecked).apply());
    }

    //#endregion

    //#region Device Chooser Option

    private void setupDeviceChooser() {
        Button chooseDeviceButton = findViewById(R.id.choose_device_button);
        chooseDeviceButton.setOnClickListener(v ->
                DeviceChooser.showDeviceChooser(this, MainActivity.getInstance()));
    }

    //#endregion

    //#region Device Check Option

    private void setupDeviceCheckSettings() {
        deviceCheckSwitch = findViewById(R.id.device_check_switch);
        deviceNameInput = findViewById(R.id.device_name_input);
        deviceNameLabel = findViewById(R.id.device_name_label);
        discoverDevicesButton = findViewById(R.id.discover_devices_button);
        availableDevicesText = findViewById(R.id.available_devices_text);

        // Load saved preferences
        boolean isDeviceCheckEnabled = prefs.getBoolean("device_check_enabled", false);
        String savedDeviceName = prefs.getString("device_name", "");

        deviceCheckSwitch.setChecked(isDeviceCheckEnabled);
        deviceNameInput.setText(savedDeviceName);
        updateDeviceCheckVisibility(isDeviceCheckEnabled);

        // Set up listeners
        deviceCheckSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("device_check_enabled", isChecked).apply();
            updateDeviceCheckVisibility(isChecked);
        });

        deviceNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                prefs.edit().putString("device_name", s.toString()).apply();
            }
        });

        discoverDevicesButton.setOnClickListener(v -> discoverAvailableDevices());
    }

    private void updateDeviceCheckVisibility(boolean isEnabled) {
        int visibility = isEnabled ? View.VISIBLE : View.GONE;
        deviceNameLabel.setVisibility(visibility);
        deviceNameInput.setVisibility(visibility);
        discoverDevicesButton.setVisibility(visibility);
        availableDevicesText.setVisibility(visibility);
    }

    private void discoverAvailableDevices() {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null) {
            showMessage("Not connected to Spotify yet");
            return;
        }
        if (mainActivity.getSpotifyApi() == null) {
            showMessage("Not connected to Spotify yet");
            return;
        }

        Log.d(TAG, "API: getUsersAvailableDevices");
        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi().getUsersAvailableDevices().build())
                .thenAccept(devices -> {
                    runOnUiThread(() -> {
                        StringBuilder deviceList = new StringBuilder("Available devices:\n");
                        for (se.michaelthelin.spotify.model_objects.miscellaneous.Device device : devices) {
                            deviceList.append("• ").append(device.getName());
                            if (device.getIs_active()) {
                                deviceList.append(" (Active)");
                            }
                            deviceList.append("\n");
                        }
                        availableDevicesText.setText(deviceList.toString());
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to list devices", throwable);
                    runOnUiThread(() -> showMessage("Couldn't load devices",
                            R.string.retry, this::discoverAvailableDevices));
                    return null;
                });
    }

    //#endregion

    //#region Automatic Fetcher Settings

    private AutomaticFetcher automaticFetcher;

    private void setupAutomaticFetcherSettings() {
        automaticFetcher = new AutomaticFetcher(this);

        Button registerButton = findViewById(R.id.register_fetcher_button);
        TextView statisticsText = findViewById(R.id.fetcher_statistics);
        SwitchCompat enableSwitch = findViewById(R.id.fetcher_enable_switch);
        LinearLayout enableLayout = findViewById(R.id.fetcher_enable_layout);
        Button deregisterButton = findViewById(R.id.deregister_fetcher_button);
        Button importSpotifyHistoryButton = findViewById(R.id.import_spotify_history_button);

        // Setup import Spotify history button
        importSpotifyHistoryButton.setOnClickListener(v -> openSpotifyHistoryImport());

        // Check registration status and update UI
        AutomaticFetcher.FetcherCredentials creds = automaticFetcher.getStoredCredentials();
        boolean isRegistered = creds != null && creds.isRegistered();
        updateRegistrationStatus(isRegistered);

        // Check if a fetch is already in progress (from MainActivity launch)
        if (isRegistered && AutomaticFetcher.isFetchInProgress()) {
            showFetchProgress(true);
            // Register to be notified when fetch completes
            AutomaticFetcher.setFetchCompletionListener(fetchListener);
        }

        // Setup registration button
        registerButton.setOnClickListener(v -> {
            automaticFetcher.registerAutomaticFetcher(new AutomaticFetcher.RegistrationCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> updateRegistrationStatus(true));
                }

                @Override
                public void onError(String error) {
                    // Message is shown by AutomaticFetcher
                }
            });
        });

        // Setup enable/disable toggle
        if (creds != null) {
            enableSwitch.setChecked(creds.isEnabled());
        }

        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            automaticFetcher.setEnabled(isChecked);

            if (isChecked) {
                // Perform immediate fetch
                performFetch();
            }
        });

        // Setup deregistration button
        deregisterButton.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Deregister Automatic Fetcher")
                    .setMessage("This will stop automatic syncing. Your local listening history will be preserved.")
                    .setPositiveButton("Deregister", (dialog, which) -> {
                        automaticFetcher.deregisterFetcher(new AutomaticFetcher.DeregistrationCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> updateRegistrationStatus(false));
                            }

                            @Override
                            public void onError(String error) {
                                // Message is shown by AutomaticFetcher
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateRegistrationStatus(boolean registered) {
        Button registerButton = findViewById(R.id.register_fetcher_button);
        TextView usernameText = findViewById(R.id.fetcher_username);
        TextView statisticsText = findViewById(R.id.fetcher_statistics);
        SwitchCompat enableSwitch = findViewById(R.id.fetcher_enable_switch);
        LinearLayout enableLayout = findViewById(R.id.fetcher_enable_layout);
        Button deregisterButton = findViewById(R.id.deregister_fetcher_button);

        if (registered) {
            registerButton.setVisibility(View.GONE);

            AutomaticFetcher.FetcherCredentials creds = automaticFetcher.getStoredCredentials();
            if (creds != null) {
                usernameText.setText("Registered as: " + creds.getUsername());
                usernameText.setVisibility(View.VISIBLE);
                enableSwitch.setChecked(creds.isEnabled());
            }

            statisticsText.setText(automaticFetcher.getStatisticsDisplay());
            statisticsText.setVisibility(View.VISIBLE);
            enableLayout.setVisibility(View.VISIBLE);
            deregisterButton.setVisibility(View.VISIBLE);
        } else {
            registerButton.setVisibility(View.VISIBLE);
            usernameText.setVisibility(View.GONE);
            statisticsText.setVisibility(View.GONE);
            enableLayout.setVisibility(View.GONE);
            deregisterButton.setVisibility(View.GONE);
        }
    }

    private void performFetch() {
        showFetchProgress(true);

        automaticFetcher.performFetchOnLaunch(new AutomaticFetcher.FetchCallback() {
            @Override
            public void onSuccess(int importedCount) {
                runOnUiThread(() -> {
                    showFetchProgress(false);
                    // Message is shown by AutomaticFetcher
                    // Update statistics display
                    TextView statisticsText = findViewById(R.id.fetcher_statistics);
                    statisticsText.setText(automaticFetcher.getStatisticsDisplay());
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showFetchProgress(false);
                    showMessage("Couldn't sync listening history",
                            R.string.retry, SettingsActivity.this::performFetch);
                });
            }
        });
    }

    private void showFetchProgress(boolean show) {
        LinearLayout progressLayout = findViewById(R.id.fetcher_progress_layout);
        if (progressLayout != null) {
            progressLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // Delivered on the main thread by AutomaticFetcher.
    private void onFetchCompleted(int importedCount) {
        if (isDestroyed()) return;
        showFetchProgress(false);
        // Message is shown by AutomaticFetcher
        TextView statisticsText = findViewById(R.id.fetcher_statistics);
        if (statisticsText != null && automaticFetcher != null) {
            statisticsText.setText(automaticFetcher.getStatisticsDisplay());
        }
    }

    //#endregion

    //#region Cache

    private void openSpotifyHistoryImport() {
        android.content.Intent intent = new android.content.Intent(this, SpotifyHistoryImportActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_up_in, R.anim.no_animation);
    }

    private void clearAllCaches() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Cache")
                .setMessage("This will clear all cached playlists and songs. Are you sure?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    // SongCache.clearCache waits on the cache's locks, which an in-flight flush
                    // or load can hold for hundreds of ms: keep that off the main thread.
                    android.content.Context appContext = getApplicationContext();
                    AppExecutors.io().execute(() -> {
                        new PlaylistCache(appContext).clearCache();
                        SongCache.getInstance(appContext).clearCache();
                        runOnUiThread(() -> {
                            if (!isDestroyed()) showMessage("Cache cleared");
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    //#endregion

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_down_out);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // The fetcher holds the listener statically: drop it so this screen is not leaked.
        AutomaticFetcher.clearFetchCompletionListener(fetchListener);
    }

}