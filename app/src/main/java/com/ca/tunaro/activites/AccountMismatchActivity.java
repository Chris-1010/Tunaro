package com.ca.tunaro.activites;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;

public class AccountMismatchActivity extends AppCompatActivity {
    private static final String TAG = "AccountMismatchActivity";

    public static final String EXTRA_EXPECTED_USER = "expected_user";
    public static final String EXTRA_ACTUAL_USER = "actual_user";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_mismatch);

        String expectedUser = getIntent().getStringExtra(EXTRA_EXPECTED_USER);
        String actualUser = getIntent().getStringExtra(EXTRA_ACTUAL_USER);

        TextView accountInfo = findViewById(R.id.account_info);
        if (expectedUser != null && actualUser != null) {
            accountInfo.setText("Expected: " + expectedUser + "\nSigned in as: " + actualUser);
        }

        Button backupButton = findViewById(R.id.backup_button);
        Button resetButton = findViewById(R.id.reset_button);
        Button exitButton = findViewById(R.id.exit_button);

        backupButton.setOnClickListener(v -> exportData());

        resetButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Reset App Data")
                    .setMessage("Would you like to export your data before resetting?")
                    .setPositiveButton("Export First", (dialog, which) -> {
                        exportDataThenReset();
                    })
                    .setNegativeButton("Reset Now", (dialog, which) -> {
                        confirmReset();
                    })
                    .setNeutralButton("Cancel", null)
                    .show();
        });

        exitButton.setOnClickListener(v -> {
            finishAffinity();
        });
    }

    private void exportData() {
        String fileName = "tunaro_backup_" +
                new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date()) +
                ".json";
        exportLauncher.launch(fileName);
    }

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Exporting data...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    new Thread(() -> {
                        try {
                            DatabaseHelper.writeExportToUri(this, uri);
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, "Data exported successfully!", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
                }
            }
    );

    private boolean pendingReset = false;

    private void exportDataThenReset() {
        pendingReset = true;
        String fileName = "tunaro_backup_" +
                new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date()) +
                ".json";
        exportThenResetLauncher.launch(fileName);
    }

    private final ActivityResultLauncher<String> exportThenResetLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Exporting data...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    new Thread(() -> {
                        try {
                            DatabaseHelper.writeExportToUri(this, uri);
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, "Data exported successfully!", Toast.LENGTH_SHORT).show();
                                performReset();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
                }
            }
    );

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Are you sure?")
                .setMessage("This will permanently delete all app data including notes, snippets, and listening history.")
                .setPositiveButton("Reset", (dialog, which) -> performReset())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performReset() {
        // Delete the database
        deleteDatabase("TunaroDB");

        // Clear all SharedPreferences
        getSharedPreferences("SpotifyPrefs", MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("TunaroPrefs", MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("AutoFetcherPrefs", MODE_PRIVATE).edit().clear().apply();

        Log.d(TAG, "App data reset complete");
        Toast.makeText(this, "App data has been reset", Toast.LENGTH_SHORT).show();

        // Restart app from MainActivity (fresh start with current account)
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Prevent going back to the splash screen
        finishAffinity();
    }
}
