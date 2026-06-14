package com.ca.tunaro.activites;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.services.AutomaticFetcher;
import com.ca.tunaro.services.SongRefreshService;
import com.ca.tunaro.utils.DeviceChecker;
import com.ca.tunaro.utils.PlaylistSetup;
import com.ca.tunaro.utils.PoolingSpotifyHttpManager;
import com.ca.tunaro.R;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;
import se.michaelthelin.spotify.requests.AbstractRequest;
import se.michaelthelin.spotify.requests.data.users_profile.GetCurrentUsersProfileRequest;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Singleton instance
    private static MainActivity instance;

    private String CLIENT_ID;
    private String CLIENT_SECRET;
    private URI REDIRECT_URI;
    final int REQUEST_CODE = 1337;

    private SpotifyApi spotifyApi;
    private SpotifyAppRemote mSpotifyAppRemote;
    private String userID;
    private String userDisplayName;

    ExecutorService executor = Executors.newSingleThreadExecutor();
    private CompletableFuture<Void> authenticationFuture;
    private ProgressDialog loadingDialog;

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_splash);

        // Set the singleton instance
        instance = this;
        Log.d(TAG, "onCreate: MainActivity instance is now " + instance);

        // Initialize these here
        CLIENT_ID = getString(R.string.spotify_client_id);
        CLIENT_SECRET = getString(R.string.spotify_client_secret);
        REDIRECT_URI = SpotifyHttpManager.makeUri(getString(R.string.redirect_uri));


        // Initialize the PlaylistSetup Class
        PlaylistSetup.initialize(this);

        PlaybackManager.getInstance().initialize(
                getApplicationContext(),
                CLIENT_ID,
                REDIRECT_URI.toString()
        );

        // Restore session from saved tokens (silent recovery)
        if (tryRestoreSession()) {
            Log.d(TAG, "Session restored from saved tokens");
            connectSpotifyAppRemote();

            // Proactively refresh the access token since the saved one may have expired
            refreshAccessToken()
                    .thenRun(() -> {
                        Log.d(TAG, "Token refreshed after session restore");
                        performInitialDeviceCheck();
                        performAutomaticFetch();
                        performBackgroundRefresh();
                        performPlaylistSongScan();
                        performOrphanedListenFetch();
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Token refresh failed after session restore", e);
                        // Still perform these even if refresh fails
                        performInitialDeviceCheck();
                        performAutomaticFetch();
                        performBackgroundRefresh();
                        performPlaylistSongScan();
                        performOrphanedListenFetch();
                        return null;
                    });

            Intent intent = new Intent(MainActivity.this, HomeActivity.class);
            startActivity(intent);
            return;
        }

        // Initialize the CompletableFuture
        authenticationFuture = new CompletableFuture<>();

        // Start authentication
        authenticateSpotify()
                .thenRunAsync(() -> {
                    // Launch HomeActivity after authentication
                    runOnUiThread(() -> {
                        if (loadingDialog != null && loadingDialog.isShowing()) {
                            loadingDialog.dismiss();
                        }
                        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                        startActivity(intent);
                    });
                }, executor)
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        if (loadingDialog != null && loadingDialog.isShowing()) {
                            loadingDialog.dismiss();
                        }
                        showToast("Error: " + throwable.getMessage());
                        finish(); // Close app if authentication fails
                    });
                    return null;
                });

    }

    @Override
    protected void onDestroy() {
        // Clear singleton reference first
        Log.d(TAG, "MainActivity onDestroy");
        instance = null;

        // Disconnect Spotify
        if (mSpotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(mSpotifyAppRemote);
            mSpotifyAppRemote = null;
        }

        // Shutdown executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        super.onDestroy();
    }

    private CompletableFuture<Void> authenticateSpotify() {
        // Start the authentication process using Authorization Code Flow
        AuthorizationRequest.Builder builder =
                new AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.CODE, REDIRECT_URI.toString());
        // Set permissions and scope
        builder.setScopes(new String[]{"app-remote-control", "streaming", "playlist-read-private", "playlist-modify-private", "playlist-modify-public", "user-read-playback-state", "user-read-recently-played", "user-read-private"});
        AuthorizationRequest request = builder.build();

        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request);

        // Create a new CompletableFuture for this authentication process
        CompletableFuture<Void> authFuture = new CompletableFuture<>();

        // Store this future so it can be completed in onActivityResult
        this.authenticationFuture = authFuture;

        // Return a new CompletableFuture that chains the authentication and user profile fetch
        return authFuture.thenCompose(aVoid -> getCurrentUsersProfile_Async()).thenRunAsync(() -> runOnUiThread(() -> {
            connectSpotifyAppRemote();
            performInitialDeviceCheck();
            performAutomaticFetch();
            performBackgroundRefresh();
            performPlaylistSongScan();
        }), executor);
    }

    private boolean tryRestoreSession() {
        SharedPreferences spotifyPrefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
        String accessToken = spotifyPrefs.getString("spotify_access_token", null);
        String refreshToken = spotifyPrefs.getString("spotify_refresh_token", null);

        SharedPreferences tunaroPrefs = getSharedPreferences("TunaroPrefs", MODE_PRIVATE);
        String savedUserId = tunaroPrefs.getString("spotify_user_id", null);
        String savedDisplayName = spotifyPrefs.getString("spotify_display_name", null);

        if (accessToken == null || refreshToken == null || savedUserId == null) {
            return false;
        }

        // Restore session state
        spotifyApi = new SpotifyApi.Builder()
                .setClientId(CLIENT_ID)
                .setClientSecret(CLIENT_SECRET)
                .setRedirectUri(REDIRECT_URI)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .build();
        userID = savedUserId;
        userDisplayName = savedDisplayName;

        Log.d(TAG, "Restored session for user: " + userDisplayName);
        return true;
    }

    public void connectSpotifyAppRemote() {
        PlaybackManager.getInstance().connectSpotify(this, null);
    }

    private CompletableFuture<Void> getCurrentUsersProfile_Async() {
        final GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi.getCurrentUsersProfile()
                .build();
        Log.d(TAG, "API: getCurrentUsersProfile");
        return getCurrentUsersProfileRequest.executeAsync()
                .thenAccept(user -> {
                    userID = user.getId();
                    userDisplayName = user.getDisplayName();

                    // Save profile info to SharedPreferences for use by other activities
                    String imageUrl = user.getImages().length > 0 ? user.getImages()[0].getUrl() : null;
                    SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
                    prefs.edit()
                            .putString("spotify_display_name", userDisplayName)
                            .putString("spotify_profile_image_url", imageUrl)
                            .apply();

                    // Save userID to SharedPreferences so it survives if this Activity is destroyed
                    SharedPreferences tunaroPrefs = getSharedPreferences("TunaroPrefs", MODE_PRIVATE);
                    tunaroPrefs.edit().putString("spotify_user_id", userID).apply();

                    // Check for account mismatch
                    String originalUserId = tunaroPrefs.getString("original_spotify_user_id", null);
                    if (originalUserId == null) {
                        // First-ever login — record this user as the original
                        tunaroPrefs.edit().putString("original_spotify_user_id", userID).apply();
                    } else if (!userID.equals(originalUserId)) {
                        // Different account signed in — show mismatch screen
                        runOnUiThread(() -> {
                            if (loadingDialog != null && loadingDialog.isShowing()) {
                                loadingDialog.dismiss();
                            }
                            Intent mismatchIntent = new Intent(MainActivity.this, AccountMismatchActivity.class);
                            mismatchIntent.putExtra(AccountMismatchActivity.EXTRA_EXPECTED_USER, originalUserId);
                            mismatchIntent.putExtra(AccountMismatchActivity.EXTRA_ACTUAL_USER, userDisplayName != null ? userDisplayName : userID);
                            mismatchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(mismatchIntent);
                            finish();
                        });
                        throw new RuntimeException("Account mismatch");
                    }

                    runOnUiThread(() -> showToast("Logged in as " + userDisplayName));
                })
                .exceptionally(throwable -> {
                    String message = throwable.getMessage();
                    // Don't show error toast for account mismatch (already handled)
                    if (message != null && message.contains("Account mismatch")) {
                        return null;
                    }
                    runOnUiThread(() -> showToast("Failed to load profile: " + message));
                    throw new RuntimeException("Failed to load profile", throwable);
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_CODE) {
            AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, intent);
            Log.d(TAG, "Auth response type: " + response.getType());
            Log.d(TAG, "Result code: " + resultCode);
            if (response.getType() == AuthorizationResponse.Type.ERROR) {
                Log.e(TAG, "Auth error: " + response.getError());
            }

            switch (response.getType()) {
                case CODE:
                    // Authentication successful - received authorization code
                    String authCode = response.getCode();

                    // Block interaction while completing authentication
                    loadingDialog = new ProgressDialog(this);
                    loadingDialog.setMessage("Connecting to Spotify...");
                    loadingDialog.setCancelable(false);
                    loadingDialog.show();

                    // Exchange authorization code for tokens
                    exchangeCodeForTokens(authCode);
                    break;
                case ERROR:
                    // Authentication failed
                    String errorMessage = response.getError();
                    showToast("Authorization failed: " + errorMessage);
                    // Complete the future exceptionally to signal authentication failure
                    if (authenticationFuture != null) {
                        authenticationFuture.completeExceptionally(new Exception(errorMessage));
                    }
                    break;
                default:
                    showToast("Spotify login was cancelled");
                    // Complete the future exceptionally to signal authentication cancellation
                    if (authenticationFuture != null) {
                        authenticationFuture.completeExceptionally(new Exception("Authentication cancelled"));
                    }
                    break;
            }
        }
    }

    private void exchangeCodeForTokens(String authCode) {
        CompletableFuture.runAsync(() -> {
            try {
                // Prepare POST request to Spotify's token endpoint
                URL url = new URL("https://accounts.spotify.com/api/token");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                // Basic authentication: Base64(client_id:client_secret)
                String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
                String encodedCredentials = android.util.Base64.encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP
                );
                connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);

                // Build POST body
                String postData = "grant_type=authorization_code" +
                    "&code=" + URLEncoder.encode(authCode, "UTF-8") +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI.toString(), "UTF-8");

                // Send request
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Read response
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        // Parse JSON response
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        String accessToken = jsonResponse.getString("access_token");
                        String refreshToken = jsonResponse.getString("refresh_token");
                        int expiresIn = jsonResponse.optInt("expires_in", 3600); // Default to 1 hour

                        // Save both tokens and expiry
                        saveTokens(accessToken, refreshToken, expiresIn);

                        // Initialize SpotifyApi with access token
                        runOnUiThread(() -> {
                            spotifyApi = new SpotifyApi.Builder()
                                    .setClientId(CLIENT_ID)
                                    .setClientSecret(CLIENT_SECRET)
                                    .setRedirectUri(REDIRECT_URI)
                                    .setAccessToken(accessToken)
                                    .setRefreshToken(refreshToken)
                                    .build();

                            // Complete the authentication future
                            if (authenticationFuture != null) {
                                authenticationFuture.complete(null);
                            }
                        });
                    }
                } else {
                    // Error response
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder errorResponse = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            errorResponse.append(responseLine.trim());
                        }
                        throw new Exception("Token exchange failed: " + errorResponse.toString());
                    }
                }

                connection.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Error exchanging code for tokens", e);
                runOnUiThread(() -> {
                    showToast("Failed to obtain tokens: " + e.getMessage());
                    if (authenticationFuture != null) {
                        authenticationFuture.completeExceptionally(e);
                    }
                });
            }
        }, executor);
    }

    private void performInitialDeviceCheck() {
        DeviceChecker.checkPlaybackDevice(this, buildFreshSpotifyApi(), (isCorrectDevice, message) -> runOnUiThread(() -> {
            if (!isCorrectDevice && PlaybackManager.getInstance().isPlaying() && DeviceChecker.isDeviceCheckEnabled(this)) {
                showToast(message);
            }
        }));
    }

    private void performAutomaticFetch() {
        AutomaticFetcher fetcher = new AutomaticFetcher(this);
        fetcher.performFetchOnLaunch(new AutomaticFetcher.FetchCallback() {
            @Override
            public void onSuccess(int importedCount) {
                if (importedCount > 0) {
                    Log.d(TAG, "Automatic fetch imported " + importedCount + " new listens");
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Automatic fetch failed: " + error);
            }
        });
    }

    private void performPlaylistSongScan() {
        if (spotifyApi == null) return;
        PlaylistSetup.scanAllPlaylistSongs(buildFreshSpotifyApi())
                .exceptionally(e -> {
                    Log.e(TAG, "Playlist song scan failed", e);
                    return null;
                });
    }

    private void performOrphanedListenFetch() {
        if (spotifyApi == null) return;
        SpotifyApi api = buildFreshSpotifyApi();
        CompletableFuture.runAsync(() -> {
            try {
                DatabaseHelper db = new DatabaseHelper(getApplicationContext());
                List<String> orphanedUris = db.getOrphanedListenUris();
                db.close();

                if (orphanedUris.isEmpty()) return;
                Log.d(TAG, "Found " + orphanedUris.size() + " orphaned listen URIs");

                List<String> validUris = new ArrayList<>();
                for (String uri : orphanedUris) {
                    if (uri != null && uri.startsWith("spotify:track:")) {
                        String id = uri.substring("spotify:track:".length());
                        if (id.matches("[A-Za-z0-9]{22}")) validUris.add(uri);
                        else Log.d(TAG, "Skipping malformed orphaned URI: " + uri);
                    } else {
                        Log.d(TAG, "Skipping non-track orphaned URI: " + uri);
                    }
                }
                if (validUris.isEmpty()) return;
                Log.d(TAG, "Fetching metadata for " + validUris.size() + " orphaned listen URIs");

                for (int i = 0; i < validUris.size(); i += 50) {
                    List<String> batch = validUris.subList(i, Math.min(i + 50, validUris.size()));
                    List<String> trackIds = new ArrayList<>(batch.size());
                    for (String uri : batch) trackIds.add(uri.substring(uri.lastIndexOf(":") + 1));

                    try {
                        se.michaelthelin.spotify.model_objects.specification.Track[] tracks =
                                api.getSeveralTracks(String.join(",", trackIds))
                                        .build().execute();

                        DatabaseHelper dbWrite = new DatabaseHelper(getApplicationContext());
                        for (int j = 0; j < tracks.length; j++) {
                            se.michaelthelin.spotify.model_objects.specification.Track track = tracks[j];
                            if (track == null) continue;

                            se.michaelthelin.spotify.model_objects.specification.AlbumSimplified album = track.getAlbum();
                            se.michaelthelin.spotify.model_objects.specification.Image[] images = album != null ? album.getImages() : null;
                            String imageUrl = images != null && images.length > 0 ? images[0].getUrl() : null;

                            com.ca.tunaro.models.SongModel.Album songAlbum = album != null ? new com.ca.tunaro.models.SongModel.Album(
                                    album.getId(), album.getName(),
                                    album.getAlbumType() != null ? album.getAlbumType().getType() : null,
                                    album.getReleaseDate(), imageUrl) : null;

                            String isrc = null;
                            if (track.getExternalIds() != null && track.getExternalIds().getExternalIds() != null) {
                                isrc = track.getExternalIds().getExternalIds().get("isrc");
                            }

                            Boolean playable = track.getIsPlayable();
                            dbWrite.upsertFullTrack(track, new com.ca.tunaro.models.SongModel(
                                    track.getUri(), track.getName(), track.getArtists(),
                                    track.getDurationMs(), track.getUri(), track.getPopularity(),
                                    songAlbum, isrc, null, playable == null || playable));
                        }
                        dbWrite.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Orphaned listen fetch batch failed at offset " + i, e);
                    }
                }
                DatabaseHelper dbCleanup = new DatabaseHelper(getApplicationContext());
                dbCleanup.deleteOrphanedListens();
                dbCleanup.close();
                Log.d(TAG, "Orphaned listen fetch complete");
            } catch (Exception e) {
                Log.e(TAG, "Orphaned listen fetch failed", e);
            }
        });
    }

    private void performBackgroundRefresh() {
        if (spotifyApi == null) return;
        new SongRefreshService(this, buildFreshSpotifyApi()).refreshStaleSongs()
                .exceptionally(e -> {
                    Log.e(TAG, "Background song refresh failed", e);
                    return null;
                });
    }

    public CompletableFuture<String> refreshAccessToken() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
                String refreshToken = prefs.getString("spotify_refresh_token", null);

                if (refreshToken == null) {
                    throw new Exception("No refresh token available");
                }

                // Prepare POST request to Spotify's token endpoint
                URL url = new URL("https://accounts.spotify.com/api/token");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                // Basic authentication: Base64(client_id:client_secret)
                String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
                String encodedCredentials = android.util.Base64.encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP
                );
                connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);

                // Build POST body for refresh
                String postData = "grant_type=refresh_token" +
                    "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8");

                // Send request
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Read response
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        // Parse JSON response
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        String newAccessToken = jsonResponse.getString("access_token");
                        int expiresIn = jsonResponse.optInt("expires_in", 3600); // Default to 1 hour

                        // Note: Refresh token response may include a new refresh token
                        if (jsonResponse.has("refresh_token")) {
                            String newRefreshToken = jsonResponse.getString("refresh_token");
                            saveTokens(newAccessToken, newRefreshToken, expiresIn);
                        } else {
                            // Only update access token, keep existing refresh token
                            prefs.edit()
                                .putString("spotify_access_token", newAccessToken)
                                .putInt("spotify_expires_in", expiresIn)
                                .putLong("spotify_expires_at", expiryTimestamp(expiresIn))
                                .apply();
                        }

                        // Update SpotifyApi with new access token
                        if (spotifyApi != null) {
                            spotifyApi.setAccessToken(newAccessToken);
                        }

                        Log.d(TAG, "Successfully refreshed access token");
                        return newAccessToken;
                    }
                } else {
                    // Error response
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder errorResponse = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            errorResponse.append(responseLine.trim());
                        }
                        throw new Exception("Token refresh failed: " + errorResponse.toString());
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error refreshing access token", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<String> getValidAccessToken() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String currentToken = spotifyApi != null ? spotifyApi.getAccessToken() : null;
                // Refresh if there's no token, or if the stored deadline has (nearly) passed.
                if (currentToken == null || isAccessTokenExpired()) {
                    return refreshAccessToken().get();
                }
                return currentToken;
            } catch (Exception e) {
                Log.e(TAG, "Error getting valid access token", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Executes a Spotify Web API request, transparently refreshing the access token when needed.
     *
     * <p>Proactive: if the stored expiry deadline has (nearly) passed, the token is refreshed
     * before the request is built. Reactive: if the request still fails with an expired-token
     * error (e.g. the token was revoked early), it refreshes and retries once.
     *
     * <p>The request is supplied as a factory rather than a pre-built request because the
     * {@code Authorization: Bearer <token>} header is baked in at build time. After a refresh we
     * must rebuild the request so it picks up the new token from {@link #spotifyApi}.
     *
     * @param requestFactory builds a fresh request using the current access token (e.g.
     *                       {@code () -> getSpotifyApi().getTrack(id).build()})
     */
    public <T> CompletableFuture<T> executeWithTokenRefresh(Supplier<? extends AbstractRequest<T>> requestFactory) {
        CompletableFuture<Void> ready = isAccessTokenExpired()
                ? refreshAccessToken().thenApply(token -> null)
                : CompletableFuture.completedFuture(null);

        return ready
                .thenCompose(ignored -> requestFactory.get().executeAsync())
                .handle((result, throwable) -> {
                    if (throwable == null) {
                        return CompletableFuture.completedFuture(result);
                    }
                    if (!isTokenExpired(throwable)) {
                        return MainActivity.<T>failedFuture(throwable);
                    }
                    Log.d(TAG, "Access token expired during API call — refreshing and retrying once");
                    // Refresh, then rebuild the request (new token) and try again.
                    return refreshAccessToken().thenCompose(token -> requestFactory.get().executeAsync());
                })
                .thenCompose(future -> future);
    }

    private static boolean isTokenExpired(Throwable throwable) {
        Throwable cause = (throwable instanceof CompletionException && throwable.getCause() != null)
                ? throwable.getCause() : throwable;
        return cause instanceof UnauthorizedException;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    private void saveTokens(String accessToken, String refreshToken, int expiresIn) {
        SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
        prefs.edit()
            .putString("spotify_access_token", accessToken)
            .putString("spotify_refresh_token", refreshToken)
            .putInt("spotify_expires_in", expiresIn)
            .putLong("spotify_expires_at", expiryTimestamp(expiresIn))
            .apply();

        Log.d(TAG, "Saved access token and refresh token to SharedPreferences (expires in " + expiresIn + " seconds)");
    }

    // Spotify returns expires_in (a duration in seconds); convert it to an absolute
    // wall-clock deadline so we can tell later whether the token is still valid.
    private static long expiryTimestamp(int expiresIn) {
        return System.currentTimeMillis() + (expiresIn * 1000L);
    }

    // Refresh a little before the real deadline to absorb clock skew and request latency.
    private static final long TOKEN_EXPIRY_SKEW_MS = 60_000L;

    private boolean isAccessTokenExpired() {
        SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
        long expiresAt = prefs.getLong("spotify_expires_at", 0L);
        // No recorded expiry (e.g. token saved by an older app version) — treat as expired
        // so we refresh once and start tracking the deadline.
        if (expiresAt == 0L) return true;
        return System.currentTimeMillis() >= (expiresAt - TOKEN_EXPIRY_SKEW_MS);
    }

    // Overload for backward compatibility when expires_in is not provided
    private void saveTokens(String accessToken, String refreshToken) {
        saveTokens(accessToken, refreshToken, 3600); // Default to 1 hour
    }

    // Getters for important objects
    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
    }

    // Returns a fresh SpotifyApi instance with its own connection manager — use this
    // when making concurrent API calls to avoid BasicHttpClientConnectionManager contention.
    public SpotifyApi buildFreshSpotifyApi() {
        if (spotifyApi == null) return null;
        return new SpotifyApi.Builder()
                .setClientId(CLIENT_ID)
                .setClientSecret(CLIENT_SECRET)
                .setRedirectUri(REDIRECT_URI)
                .setAccessToken(spotifyApi.getAccessToken())
                .setRefreshToken(spotifyApi.getRefreshToken())
                .setHttpManager(new PoolingSpotifyHttpManager())
                .build();
    }

    public SpotifyAppRemote getSpotifyAppRemote() {
        return mSpotifyAppRemote;
    }

    public String getUserID() {
        return userID;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}