package com.ca.tunaro.activites;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.R;
import com.ca.tunaro.utils.AppExecutors;
import com.ca.tunaro.utils.UiFeedback;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.utils.SecurePrefs;

public class AccountMismatchActivity extends AppCompatActivity {
    private static final String TAG = "AccountMismatchActivity";

    public static final String EXTRA_EXPECTED_USER = "expected_user";
    public static final String EXTRA_ACTUAL_USER = "actual_user";

    private Button backupButton;
    private Button resetButton;
    private Button exitButton;
    private View exportProgress;

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

        backupButton = findViewById(R.id.backup_button);
        resetButton = findViewById(R.id.reset_button);
        exitButton = findViewById(R.id.exit_button);
        exportProgress = findViewById(R.id.export_progress);

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
                if (uri != null) runExport(uri, false);
            }
    );

    /**
     * Exports on the I/O pool with inline progress (buttons disabled, indicator shown), then
     * either reports the result or, when {@code resetAfter} is set and the export succeeded,
     * resets the app.
     */
    private void runExport(android.net.Uri uri, boolean resetAfter) {
        setBusy(true);
        android.content.Context appContext = getApplicationContext();
        AppExecutors.io().execute(() -> {
            boolean ok;
            try {
                DatabaseHelper.writeExportToUri(appContext, uri);
                ok = true;
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                ok = false;
            }
            boolean success = ok;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                setBusy(false);
                if (!success) {
                    UiFeedback.show(this, "Export failed. Try again or pick another location.");
                } else if (resetAfter) {
                    // Toast: this screen is torn down by the reset straight after.
                    Toast.makeText(this, "Data exported", Toast.LENGTH_SHORT).show();
                    performReset();
                } else {
                    UiFeedback.show(this, "Data exported");
                }
            });
        });
    }

    private void setBusy(boolean busy) {
        backupButton.setEnabled(!busy);
        resetButton.setEnabled(!busy);
        exitButton.setEnabled(!busy);
        exportProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

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
                if (uri != null) runExport(uri, true);
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
        // Release the shared connection so the file can be deleted cleanly.
        DatabaseHelper.closeSharedInstance();
        deleteDatabase("TunaroDB");

        // Clear all SharedPreferences
        SecurePrefs.get(getApplicationContext(), "SpotifyPrefs").edit().clear().apply();
        getSharedPreferences("TunaroPrefs", MODE_PRIVATE).edit().clear().apply();
        SecurePrefs.get(getApplicationContext(), "AutoFetcherPrefs").edit().clear().apply();

        Log.d(TAG, "App data reset complete");
        Toast.makeText(this, "App data has been reset", Toast.LENGTH_SHORT).show();

        // Restart app from MainActivity (fresh start with current account)
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onBackPressed() {
        // Deliberately does not call super: back must not return to the splash screen
        // or to whatever screen was showing under the mismatch prompt.
        // Ignore Back while an export runs, so a pending reset is not dropped silently.
        if (exportProgress.getVisibility() == View.VISIBLE) return;
        finishAffinity();
    }
}
