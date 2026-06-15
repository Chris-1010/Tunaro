package com.ca.tunaro.activites;

import android.app.ProgressDialog;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.services.AutomaticFetcher;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("TunaroPrefs", MODE_PRIVATE);

        setupBackButton();
        setupImportExportButtons();
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
        Button importButton = findViewById(R.id.import_button);
        Button exportButton = findViewById(R.id.export_button);
        Button clearCacheButton = findViewById(R.id.clear_cache_button);
        Button restoreBackupButton = findViewById(R.id.restore_backup_button);
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
                if (uri != null) {
                    // Show progress dialog to keep user on screen
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Importing data...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    // Run import on background thread to avoid freezing UI
                    new Thread(() -> {
                        try {
                            DatabaseHelper.ImportStats stats = DatabaseHelper.importFromUri(this, uri);
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                showToast(stats.getSummary());
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                showToast("Import failed: " + e.getMessage());
                            });
                        }
                    }).start();
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
            showToast("MainActivity not available");
            return;
        }
        if (mainActivity.getSpotifyApi() == null) {
            showToast("Spotify API not available");
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
                    runOnUiThread(() -> {
                        showToast("Error getting devices: " + throwable.getMessage());
                    });
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
            AutomaticFetcher.setFetchCompletionListener(this::onFetchCompleted);
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
                    // Toast is shown by AutomaticFetcher
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
                showToast("Automatic fetching enabled");
                // Perform immediate fetch
                performFetch();
            } else {
                showToast("Automatic fetching disabled");
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
                                // Toast is shown by AutomaticFetcher
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
                    // Toast is shown by AutomaticFetcher
                    // Update statistics display
                    TextView statisticsText = findViewById(R.id.fetcher_statistics);
                    statisticsText.setText(automaticFetcher.getStatisticsDisplay());
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> showFetchProgress(false));
                // Toast is shown by AutomaticFetcher
            }
        });
    }

    private void showFetchProgress(boolean show) {
        LinearLayout progressLayout = findViewById(R.id.fetcher_progress_layout);
        if (progressLayout != null) {
            progressLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void onFetchCompleted(int importedCount) {
        runOnUiThread(() -> {
            showFetchProgress(false);
            // Toast is shown by AutomaticFetcher
            // Update statistics display
            TextView statisticsText = findViewById(R.id.fetcher_statistics);
            if (statisticsText != null && automaticFetcher != null) {
                statisticsText.setText(automaticFetcher.getStatisticsDisplay());
            }
        });
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
        // No cleanup needed for automatic fetcher
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}