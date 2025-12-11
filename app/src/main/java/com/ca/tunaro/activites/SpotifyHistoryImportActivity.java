package com.ca.tunaro.activites;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.SpotifyExtendedHistoryEntry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpotifyHistoryImportActivity extends AppCompatActivity {

    private Button selectFilesButton;
    private Button startImportButton;
    private TextView selectedFilesLabel;
    private TextView selectedFilesText;
    private ProgressBar importProgress;
    private TextView importStatus;
    private TextView importDetails;

    private List<Uri> selectedFileUris = new ArrayList<>();
    private DatabaseHelper databaseHelper;
    private ExecutorService executorService;

    private final ActivityResultLauncher<String[]> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> {
                if (uris != null && !uris.isEmpty()) {
                    selectedFileUris = new ArrayList<>(uris);
                    updateSelectedFilesDisplay();
                    startImportButton.setEnabled(true);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spotify_history_import);

        databaseHelper = new DatabaseHelper(this);
        executorService = Executors.newSingleThreadExecutor();

        setupBackButton();
        initializeViews();
        setupClickListeners();
    }

    private void setupBackButton() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Import Spotify History");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initializeViews() {
        selectFilesButton = findViewById(R.id.select_files_button);
        startImportButton = findViewById(R.id.start_import_button);
        selectedFilesLabel = findViewById(R.id.selected_files_label);
        selectedFilesText = findViewById(R.id.selected_files_text);
        importProgress = findViewById(R.id.import_progress);
        importStatus = findViewById(R.id.import_status);
        importDetails = findViewById(R.id.import_details);
    }

    private void setupClickListeners() {
        selectFilesButton.setOnClickListener(v -> selectFiles());
        startImportButton.setOnClickListener(v -> startImport());
    }

    private void selectFiles() {
        filePickerLauncher.launch(new String[]{"application/json"});
    }

    private void updateSelectedFilesDisplay() {
        selectedFilesLabel.setVisibility(View.VISIBLE);
        selectedFilesText.setVisibility(View.VISIBLE);

        StringBuilder filesDisplay = new StringBuilder();
        for (int i = 0; i < selectedFileUris.size(); i++) {
            Uri uri = selectedFileUris.get(i);
            String fileName = getFileNameFromUri(uri);
            filesDisplay.append((i + 1)).append(". ").append(fileName).append("\n");
        }

        selectedFilesText.setText(filesDisplay.toString().trim());
        importStatus.setText(selectedFileUris.size() + " file(s) selected");
    }

    private String getFileNameFromUri(Uri uri) {
        String path = uri.getPath();
        if (path != null) {
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash != -1) {
                return path.substring(lastSlash + 1);
            }
        }
        return uri.getLastPathSegment();
    }

    private void startImport() {
        selectFilesButton.setEnabled(false);
        startImportButton.setEnabled(false);
        importProgress.setVisibility(View.VISIBLE);
        importProgress.setIndeterminate(false);
        importProgress.setMax(selectedFileUris.size());
        importProgress.setProgress(0);
        importDetails.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            int totalProcessed = 0;
            int totalAdded = 0;
            int totalSkipped = 0;
            int filesProcessed = 0;

            for (Uri uri : selectedFileUris) {
                String fileName = getFileNameFromUri(uri);

                runOnUiThread(() -> {
                    importStatus.setText("Processing: " + fileName);
                });

                ImportResult result = processFile(uri);
                totalProcessed += result.processed;
                totalAdded += result.added;
                totalSkipped += result.skipped;
                filesProcessed++;

                final int currentFile = filesProcessed;
                final int finalTotalProcessed = totalProcessed;
                final int finalTotalAdded = totalAdded;
                final int finalTotalSkipped = totalSkipped;

                runOnUiThread(() -> {
                    importProgress.setProgress(currentFile);
                    importDetails.setText(String.format(
                            "Files: %d/%d\nTotal entries: %d\nImported: %d\nSkipped: %d",
                            currentFile, selectedFileUris.size(),
                            finalTotalProcessed, finalTotalAdded, finalTotalSkipped
                    ));
                });
            }

            final int finalTotal = totalProcessed;
            final int finalAdded = totalAdded;
            final int finalSkipped = totalSkipped;

            runOnUiThread(() -> {
                importProgress.setVisibility(View.GONE);
                importStatus.setText("Import Complete!");
                importDetails.setText(String.format(
                        "Processed %d files\n\nTotal entries: %d\nSuccessfully imported: %d\nSkipped (duplicates/invalid): %d",
                        selectedFileUris.size(), finalTotal, finalAdded, finalSkipped
                ));
                selectFilesButton.setEnabled(true);
                Toast.makeText(this, "Import completed: " + finalAdded + " listens added", Toast.LENGTH_LONG).show();
            });
        });
    }

    private ImportResult processFile(Uri uri) {
        ImportResult result = new ImportResult();
        Gson gson = new Gson();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) {
            Type listType = new TypeToken<List<SpotifyExtendedHistoryEntry>>() {}.getType();
            List<SpotifyExtendedHistoryEntry> entries = gson.fromJson(reader, listType);

            if (entries == null || entries.isEmpty()) {
                return result;
            }

            for (SpotifyExtendedHistoryEntry entry : entries) {
                result.processed++;

                // Validate entry
                if (!entry.isValid()) {
                    result.skipped++;
                    continue;
                }

                String trackId = entry.extractTrackId();
                if (trackId == null) {
                    result.skipped++;
                    continue;
                }

                // Check for duplicates before adding
                if (databaseHelper.hasExactListen(trackId, entry.getTimestamp())) {
                    result.skipped++;
                } else {
                    databaseHelper.addListenRecordWithTimestamp(trackId, entry.getTimestamp());
                    result.added++;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private static class ImportResult {
        int processed = 0;
        int added = 0;
        int skipped = 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
