package com.ca.tunaro.activites;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongNote;
import com.ca.tunaro.models.SongSnippet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;

public class BackupRestoreActivity extends AppCompatActivity {
    private static final String TAG = "BackupRestoreActivity";
    private static final int BATCH_SIZE = 50;

    private Button selectFileButton;
    private Button startButton;
    private TextView selectedFileText;
    private View warningContainer;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView detailsText;

    private Uri selectedFileUri;
    private DatabaseHelper dbHelper;
    private ExecutorService executor;

    private final ActivityResultLauncher<String[]> filePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    selectedFileUri = uri;
                    selectedFileText.setText(uri.getLastPathSegment());
                    selectedFileText.setVisibility(View.VISIBLE);
                    startButton.setEnabled(true);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_restore);

        dbHelper = new DatabaseHelper(this);
        executor = Executors.newSingleThreadExecutor();

        selectFileButton = findViewById(R.id.select_file_button);
        startButton = findViewById(R.id.start_button);
        selectedFileText = findViewById(R.id.selected_file_text);
        warningContainer = findViewById(R.id.warning_container);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        detailsText = findViewById(R.id.details_text);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Restore Backup");
        }

        selectFileButton.setOnClickListener(v -> filePicker.launch(new String[]{"application/json"}));
        startButton.setOnClickListener(v -> startRestore());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void startRestore() {
        SpotifyApi spotifyApi = MainActivity.getInstance() != null
                ? MainActivity.getInstance().getSpotifyApi() : null;
        if (spotifyApi == null || spotifyApi.getAccessToken() == null) {
            statusText.setText("Spotify not connected. Return to the home screen first.");
            return;
        }

        selectFileButton.setEnabled(false);
        startButton.setEnabled(false);
        warningContainer.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        detailsText.setVisibility(View.VISIBLE);

        executor.execute(() -> runRestore(spotifyApi));
    }

    private void runRestore(SpotifyApi spotifyApi) {
        try {
            status("Parsing backup file...");

            JsonObject root;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getContentResolver().openInputStream(selectedFileUri)))) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }

            JsonArray listenArray   = root.has("listenHistory")      ? root.getAsJsonArray("listenHistory")      : new JsonArray();
            JsonArray notesArray    = root.has("notes")              ? root.getAsJsonArray("notes")              : new JsonArray();
            JsonArray snippetsArray = root.has("snippets")           ? root.getAsJsonArray("snippets")           : new JsonArray();
            JsonArray archivedArray = root.has("archivedPlaylists")  ? root.getAsJsonArray("archivedPlaylists")  : new JsonArray();
            String lastSyncCursor   = root.has("lastSyncCursor")     ? root.get("lastSyncCursor").getAsString()  : null;

            // Collect every unique track ID across all sections
            Set<String> allTrackIds = new LinkedHashSet<>();
            for (JsonElement e : listenArray)   allTrackIds.add(e.getAsJsonObject().get("songId").getAsString());
            for (JsonElement e : notesArray)    allTrackIds.add(e.getAsJsonObject().get("songId").getAsString());
            for (JsonElement e : snippetsArray) allTrackIds.add(e.getAsJsonObject().get("songId").getAsString());

            // Filter to valid track IDs — invalid ones (local files, podcasts) skip the API call
            Set<String> validTrackIds = new LinkedHashSet<>();
            for (String id : allTrackIds) {
                if (isValidSpotifyTrackId(id)) validTrackIds.add(id);
            }

            int totalTracks = validTrackIds.size();
            int totalBatches = (totalTracks + BATCH_SIZE - 1) / BATCH_SIZE;
            status("Fetching metadata for " + totalTracks + " unique tracks via Spotify API...");
            details("Tracks to fetch: " + totalTracks + "\nAPI calls needed: " + totalBatches);

            setProgressMax(totalBatches);
            setProgressIndeterminate(false);

            // Batch-fetch track metadata and upsert to DB; song ID is always spotify:track:<trackId>
            List<String> trackIdList = new ArrayList<>(validTrackIds);
            int resolved = 0;
            int unresolvable = allTrackIds.size() - validTrackIds.size(); // pre-filtered invalid IDs
            int batchsDone = 0;

            for (int i = 0; i < trackIdList.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, trackIdList.size());
                List<String> batch = trackIdList.subList(i, end);

                try {
                    Log.d(TAG, "API: getSeveralTracks count=" + batch.size() + " batchOffset=" + i);
                    Track[] tracks = spotifyApi
                            .getSeveralTracks(String.join(",", batch))
                            .setQueryParameter("market", "from_token")
                            .build()
                            .execute();

                    for (int j = 0; j < tracks.length; j++) {
                        Track track = tracks[j];
                        String trackId = batch.get(j);

                        if (track == null) {
                            unresolvable++;
                            continue;
                        }

                        upsertTrackToDb(track, SongModel.SPOTIFY_TRACK_URI_PREFIX + trackId);
                        resolved++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Batch failed at offset " + i, e);
                    unresolvable += batch.size();
                }

                batchsDone++;
                final int resolvedSnap = resolved;
                final int unresolvableSnap = unresolvable;
                final int batchSnap = batchsDone;
                runOnUiThread(() -> {
                    progressBar.setProgress(batchSnap);
                    details("Resolved: " + resolvedSnap +
                            "\nUnresolvable (placeholder): " + unresolvableSnap +
                            "\nBatches: " + batchSnap + "/" + totalBatches);
                });
            }

            // Import listen history in batched transactions
            status("Importing listen history (" + listenArray.size() + " entries)...");
            setProgressMax(listenArray.size());
            progressBar.setProgress(0);

            int listensAdded = 0;
            int rowCount = 0;

            DatabaseHelper.ListenImportBatch listenBatch = dbHelper.beginListenImportBatch();
            for (JsonElement e : listenArray) {
                JsonObject entry = e.getAsJsonObject();
                String trackId = entry.get("songId").getAsString();
                String timestamp = entry.get("listenTimestamp").getAsString();
                String songId = SongModel.SPOTIFY_TRACK_URI_PREFIX + trackId;
                String uuid = entry.has("uuid") ? entry.get("uuid").getAsString() : null;

                dbHelper.addListenToBatch(listenBatch, uuid, songId, timestamp);
                listensAdded++;

                rowCount++;
                if (rowCount % 5000 == 0) {
                    dbHelper.flushListenBatch(listenBatch);
                    final int addedSnap = listensAdded;
                    final int rowSnap = rowCount;
                    runOnUiThread(() -> {
                        progressBar.setProgress(rowSnap);
                        details("Listens imported: " + addedSnap);
                    });
                }
            }
            dbHelper.flushListenBatch(listenBatch);

            // Import notes
            status("Importing notes (" + notesArray.size() + " entries)...");
            int notesAdded = 0;
            for (JsonElement e : notesArray) {
                JsonObject obj = e.getAsJsonObject();
                String trackId = obj.get("songId").getAsString();
                String songId = SongModel.SPOTIFY_TRACK_URI_PREFIX + trackId;
                String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
                String noteType = obj.has("noteType") ? obj.get("noteType").getAsString() : "General";
                String content = obj.has("content") ? obj.get("content").getAsString() : "";
                if (dbHelper.addNote(new SongNote(uuid, songId, noteType, content)) != -1) notesAdded++;
            }

            // Import snippets
            status("Importing snippets (" + snippetsArray.size() + " entries)...");
            int snippetsAdded = 0;
            for (JsonElement e : snippetsArray) {
                JsonObject obj = e.getAsJsonObject();
                String trackId = obj.get("songId").getAsString();
                String songId = SongModel.SPOTIFY_TRACK_URI_PREFIX + trackId;
                String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
                long snippetNo = obj.has("snippetNo") ? obj.get("snippetNo").getAsLong() : 1;
                String title = obj.has("title") ? obj.get("title").getAsString() : "";
                long startTime = obj.has("startTime") ? obj.get("startTime").getAsLong() : 0;
                long endTime = obj.has("endTime") ? obj.get("endTime").getAsLong() : 0;
                boolean includeInRankings = obj.has("includeInRankings") && obj.get("includeInRankings").getAsBoolean();
                if (dbHelper.addSnippet(new SongSnippet(uuid, songId, snippetNo, title, startTime, endTime, includeInRankings)) != -1) snippetsAdded++;
            }

            // Import archived playlists — insert stubs so the flag survives the playlist sync
            status("Importing archived playlists...");
            int playlistsArchived = 0;
            for (JsonElement e : archivedArray) {
                dbHelper.upsertPlaylistStub(e.getAsString(), true, false);
                playlistsArchived++;
            }

            // Restore sync cursor
            if (lastSyncCursor != null) {
                dbHelper.saveLastSyncCursor(this, lastSyncCursor);
            }

            final int finalResolved = resolved;
            final int finalUnresolvable = unresolvable;
            final int finalListensAdded = listensAdded;
            final int finalNotesAdded = notesAdded;
            final int finalSnippetsAdded = snippetsAdded;
            final int finalPlaylistsArchived = playlistsArchived;

            runOnUiThread(() -> {
                warningContainer.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                statusText.setText("Restore complete!");
                details(
                        "Tracks resolved: " + finalResolved + "\n" +
                        "Unresolvable (placeholder): " + finalUnresolvable + "\n\n" +
                        "Listen history imported: " + finalListensAdded + "\n\n" +
                        "Notes imported: " + finalNotesAdded + "\n" +
                        "Snippets imported: " + finalSnippetsAdded + "\n" +
                        "Playlists archived: " + finalPlaylistsArchived
                );
                selectFileButton.setEnabled(true);
            });

        } catch (Exception e) {
            Log.e(TAG, "Restore failed", e);
            runOnUiThread(() -> {
                warningContainer.setVisibility(View.GONE);
                statusText.setText("Restore failed: " + e.getMessage());
                selectFileButton.setEnabled(true);
                startButton.setEnabled(true);
            });
        }
    }

    private static boolean isValidSpotifyTrackId(String id) {
        return id != null && id.length() == 22 && id.matches("[0-9A-Za-z]+");
    }

    private void upsertTrackToDb(Track track, String songId) {
        try {
            ArtistSimplified[] artists = track.getArtists();
            se.michaelthelin.spotify.model_objects.specification.AlbumSimplified trackAlbum = track.getAlbum();
            Image[] images = trackAlbum != null ? trackAlbum.getImages() : null;
            String imageUrl = images != null && images.length > 0 ? images[0].getUrl() : null;

            SongModel.Album album = trackAlbum != null ? new SongModel.Album(
                    trackAlbum.getId(),
                    trackAlbum.getName(),
                    trackAlbum.getAlbumType() != null ? trackAlbum.getAlbumType().getType() : null,
                    trackAlbum.getReleaseDate(),
                    imageUrl
            ) : null;

            String isrc = null;
            if (track.getExternalIds() != null && track.getExternalIds().getExternalIds() != null) {
                isrc = track.getExternalIds().getExternalIds().get("isrc");
            }

            Boolean playable = track.getIsPlayable();
            dbHelper.upsertFullTrack(track, new SongModel(
                    songId, track.getName(), artists, track.getDurationMs(),
                    track.getUri(), track.getPopularity(), album, isrc, null,
                    playable == null || playable
            ));
        } catch (Exception e) {
            Log.w(TAG, "Failed to upsert track " + songId, e);
        }
    }

    private void status(String msg) {
        runOnUiThread(() -> statusText.setText(msg));
    }

    private void details(String msg) {
        runOnUiThread(() -> detailsText.setText(msg));
    }

    private void setProgressMax(int max) {
        runOnUiThread(() -> progressBar.setMax(max));
    }

    private void setProgressIndeterminate(boolean indeterminate) {
        runOnUiThread(() -> progressBar.setIndeterminate(indeterminate));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) executor.shutdown();
    }
}
