package com.ca.tunaro.activites;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.utils.PlaylistCache;
import com.ca.tunaro.utils.SongCache;
import com.ca.tunaro.utils.SpotifyHistoryFetcher;

import java.util.concurrent.TimeUnit;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";

    private SwitchCompat deviceCheckSwitch;
    private EditText deviceNameInput;
    private TextView deviceNameLabel;
    private Button discoverDevicesButton;
    private TextView availableDevicesText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("TunaroPrefs", MODE_PRIVATE);

        setupBackButton();
        setupImportExportButtons();
        setupDeviceCheckSettings();
        setupHistorySyncSettings();
        setupBackgroundSyncSettings();
    }

    private void setupBackButton() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
    }

    //#region Import/Export Option

    private void setupImportExportButtons() {
        Button importButton = findViewById(R.id.import_button);
        Button exportButton = findViewById(R.id.export_button);
        Button clearCacheButton = findViewById(R.id.clear_cache_button);
        clearCacheButton.setOnClickListener(v -> clearAllCaches());

        importButton.setOnClickListener(v -> importData());
        exportButton.setOnClickListener(v -> exportData());
    }

    // Handle the import button click
    public void importData() {
        importLauncher.launch(new String[]{"application/json"});
    }

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        DatabaseHelper.ImportStats stats = DatabaseHelper.importFromUri(this, uri);
                        showToast(stats.getSummary());
                    } catch (Exception e) {
                        showToast("Import failed: " + e.getMessage());
                    }
                }
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
                if (uri != null) {
                    // Show progress dialog to keep user on screen
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Exporting data...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    // Run export on background thread to avoid freezing UI
                    new Thread(() -> {
                        try {
                            DatabaseHelper.writeExportToUri(this, uri);
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                showToast("Data exported successfully!");
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                showToast("Export failed: " + e.getMessage());
                            });
                        }
                    }).start();
                }
            }
    );

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
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

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
            showToast("MainActivity not available");
            return;
        }
        if (mainActivity.getSpotifyApi() == null) {
            showToast("Spotify API not available");
            return;
        }

        mainActivity.getSpotifyApi().getUsersAvailableDevices()
                .build()
                .executeAsync()
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
                    runOnUiThread(() -> {
                        showToast("Error getting devices: " + throwable.getMessage());
                    });
                    return null;
                });
    }

    //#endregion

    //#region History Sync Section

    private Button startHistorySyncButton;
    private ProgressBar historySyncProgress;
    private TextView historySyncStatus;
    private SpotifyHistoryFetcher historyFetcher;

    private void setupHistorySyncSettings() {
        startHistorySyncButton = findViewById(R.id.start_history_sync_button);
        historySyncProgress = findViewById(R.id.history_sync_progress);
        historySyncStatus = findViewById(R.id.history_sync_status);

        Button importSpotifyHistoryButton = findViewById(R.id.import_spotify_history_button);
        importSpotifyHistoryButton.setOnClickListener(v -> openSpotifyHistoryImport());

        startHistorySyncButton.setOnClickListener(v -> {
            if (historyFetcher != null && !historyFetcher.isCancelled()) {
                // Currently syncing - cancel it
                historyFetcher.cancel();
                updateSyncUI(false);
                historySyncStatus.setText("Sync cancelled");
                historySyncStatus.setTextColor(getResources().getColor(android.R.color.secondary_text_light));
            } else {
                // Start new sync
                startHistorySync();
            }
        });

        // Initial UI state
        updateSyncUI(false);
    }

    private void startHistorySync() {
        // Cancel any existing sync first
        if (historyFetcher != null) {
            historyFetcher.cancel();
        }

        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            showToast("Spotify API not available");
            return;
        }

        // Initialize fetcher
        historyFetcher = new SpotifyHistoryFetcher(this, mainActivity.getSpotifyApi());

        // Update UI for sync start
        updateSyncUI(true);
        historySyncStatus.setText("Initializing...");

        // Start the sync
        historyFetcher.fetchHistory(new SpotifyHistoryFetcher.ProgressCallback() {
            @Override
            public void onProgress(int processed, int added) {
                runOnUiThread(() -> {
                    String status = String.format("Processed: %d | Added: %d", processed, added);
                    historySyncStatus.setText(status);
                });
            }

            @Override
            public void onComplete(int totalProcessed, int totalAdded) {
                runOnUiThread(() -> {
                    updateSyncUI(false);
                    String result = String.format("✓ Sync complete! Processed %d tracks, added %d new listens",
                            totalProcessed, totalAdded);
                    historySyncStatus.setText(result);
                    historySyncStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));

                    // Save stats for display
                    updateLastSyncStats(SettingsActivity.this, totalProcessed, totalAdded);

                    // Refresh the background sync status display
                    CheckBox enableBackgroundSync = findViewById(R.id.enable_background_sync);
                    if (enableBackgroundSync.isChecked()) {
                        TextView backgroundSyncStatus = findViewById(R.id.background_sync_status);
                        TextView backgroundSyncDetails = findViewById(R.id.background_sync_details);
                        updateBackgroundSyncStatus(backgroundSyncStatus, backgroundSyncDetails, true);
                    }

                    showToast("Added " + totalAdded + " new listen records");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    updateSyncUI(false);
                    historySyncStatus.setText("✗ Error: " + error);
                    historySyncStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    showToast("Sync failed: " + error);
                });
            }
        });
    }


    private void updateSyncUI(boolean isSyncing) {
        historySyncProgress.setVisibility(isSyncing ? View.VISIBLE : View.GONE);

        if (isSyncing) {
            startHistorySyncButton.setText("Cancel Sync");
            historySyncStatus.setTextColor(getResources().getColor(android.R.color.primary_text_light));
        } else {
            startHistorySyncButton.setText("Start Manual Sync");
        }
    }

    //#endregion

    //#region Background Sync Settings

    private void setupBackgroundSyncSettings() {
        CheckBox enableBackgroundSync = findViewById(R.id.enable_background_sync);
        TextView backgroundSyncStatus = findViewById(R.id.background_sync_status);
        TextView backgroundSyncDetails = findViewById(R.id.background_sync_details);

        // Load current state
        boolean isEnabled = prefs.getBoolean("background_sync_enabled", false);
        enableBackgroundSync.setChecked(isEnabled);
        updateBackgroundSyncStatus(backgroundSyncStatus, backgroundSyncDetails, isEnabled);

        enableBackgroundSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("background_sync_enabled", isChecked).apply();

            if (isChecked) {
                scheduleBackgroundSync();
                showToast("Background sync enabled - will run every 2 days");
            } else {
                cancelBackgroundSync();
                showToast("Background sync disabled");
            }

            updateBackgroundSyncStatus(backgroundSyncStatus, backgroundSyncDetails, isChecked);
        });
    }

    private void scheduleBackgroundSync() {
        PeriodicWorkRequest syncWork =
                new PeriodicWorkRequest.Builder(
                        com.ca.tunaro.workers.HistorySyncWorker.class,
                        2, TimeUnit.DAYS)
                        .setConstraints(
                                new Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                        .setRequiresBatteryNotLow(true)
                                        .build())
                        .build();

        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        "history_sync",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        syncWork);
    }

    private void cancelBackgroundSync() {
        WorkManager.getInstance(this)
                .cancelUniqueWork("history_sync");
    }

    private void updateBackgroundSyncStatus(TextView statusView, TextView detailsView, boolean enabled) {
        if (enabled) {
            statusView.setText("✓ Automatic sync enabled");
            statusView.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_green_light));

            // Get last sync stats and next sync time
            String lastSyncStats = getLastSyncStats();
            String nextSyncTime = getNextSyncTime();

            StringBuilder details = new StringBuilder();
            if (!lastSyncStats.isEmpty()) {
                details.append(lastSyncStats).append("\n");
            }
            if (!nextSyncTime.isEmpty()) {
                details.append(nextSyncTime);
            }

            if (details.length() > 0) {
                detailsView.setText(details.toString());
                detailsView.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
                detailsView.setVisibility(View.VISIBLE);
            } else {
                detailsView.setVisibility(View.GONE);
            }
        } else {
            statusView.setText("Manual sync only");
            statusView.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_orange_light));
            detailsView.setVisibility(View.GONE);
        }
    }

    private String getLastSyncStats() {
        int lastProcessed = prefs.getInt("last_sync_processed", -1);
        int lastAdded = prefs.getInt("last_sync_added", -1);
        long lastSyncTime = prefs.getLong("last_sync_time", -1);

        if (lastProcessed >= 0 && lastAdded >= 0 && lastSyncTime > 0) {
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(
                    "MMM dd 'at' HH:mm", java.util.Locale.getDefault());
            String dateStr = formatter.format(new java.util.Date(lastSyncTime));
            return String.format("Last sync (%s): %d processed, %d added", dateStr, lastProcessed, lastAdded);
        }
        return "";
    }

    private String getNextSyncTime() {
        try {
            androidx.work.WorkInfo workInfo = androidx.work.WorkManager.getInstance(this)
                    .getWorkInfosForUniqueWork("history_sync")
                    .get()
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (workInfo != null && workInfo.getState() == androidx.work.WorkInfo.State.ENQUEUED) {
                long lastSyncTime = prefs.getLong("last_sync_time", -1);

                if (lastSyncTime > 0) {
                    // Next sync is 2 days after the last sync
                    long nextRun = lastSyncTime + (2 * 24 * 60 * 60 * 1000L);

                    // If that time has already passed, it means sync is overdue
                    if (nextRun <= System.currentTimeMillis()) {
                        return "Next sync: Overdue (will run soon)";
                    }

                    java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(
                            "MMM dd 'at' HH:mm", java.util.Locale.getDefault());
                    String dateStr = formatter.format(new java.util.Date(nextRun));
                    return "Next sync: " + dateStr;
                } else {
                    // No previous sync recorded
                    return "Next sync: (no previous sync found)";
                }
            }
        } catch (Exception e) {
            Log.e("SettingsActivity", "Error getting next sync time", e);
        }
        return "";
    }

    public static void updateLastSyncStats(Context context, int processed, int added) {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("TunaroPrefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("last_sync_processed", processed)
                .putInt("last_sync_added", added)
                .putLong("last_sync_time", System.currentTimeMillis())
                .apply();
    }

    //#endregion

    //#region Cache

    private void openSpotifyHistoryImport() {
        android.content.Intent intent = new android.content.Intent(this, SpotifyHistoryImportActivity.class);
        startActivity(intent);
    }

    private void clearAllCaches() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Cache")
                .setMessage("This will clear all cached playlists and songs. Are you sure?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    // Clear playlist cache
                    PlaylistCache playlistCache = new PlaylistCache(this);
                    playlistCache.clearCache();

                    // Clear song cache
                    SongCache songCache = new SongCache(this);
                    songCache.clearCache();

                    Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    //#endregion

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (historyFetcher != null) {
            historyFetcher.cancel();
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}