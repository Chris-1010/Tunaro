package com.ca.tunaro.activites;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupBackButton();
        setupImportExportButtons();
    }

    private void setupBackButton() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
    }

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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}