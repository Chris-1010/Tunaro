package com.ca.tunaro.activites;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;

public class SettingsActivity extends AppCompatActivity {
    private SwitchCompat deviceCheckSwitch;
    private EditText deviceNameInput;
    private TextView deviceNameLabel;
    private Button discoverDevicesButton;
    private TextView availableDevicesText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("TunaroPrefs", MODE_PRIVATE);

        setupBackButton();
        setupImportExportButtons();
        setupDeviceCheckSettings();
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
                        Toast.makeText(this, stats.getSummary(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                    try {
                        String jsonData = DatabaseHelper.generateExportJson(this);
                        DatabaseHelper.writeExportToUri(this, uri, jsonData);
                        Toast.makeText(this, "Data exported successfully!", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
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
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            Toast.makeText(this, "Spotify API not available", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "Error getting devices: " + throwable.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    //#endregion

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}