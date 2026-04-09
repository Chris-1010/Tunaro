package com.ca.tunaro.activites;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.services.AutomaticFetcher;
import com.ca.tunaro.utils.DeviceChecker;
import com.ca.tunaro.utils.PlaylistSetup;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
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
            performInitialDeviceCheck();
            performAutomaticFetch();
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
        builder.setScopes(new String[]{"app-remote-control", "streaming", "playlist-read-private", "playlist-modify-private", "user-read-playback-state", "user-read-recently-played", "user-read-private"});
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
        DeviceChecker.checkPlaybackDevice(this, spotifyApi, (isCorrectDevice, message) -> runOnUiThread(() -> {
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
                if (spotifyApi != null && spotifyApi.getAccessToken() != null) {
                    // Try using current token first
                    return spotifyApi.getAccessToken();
                } else {
                    // Need to refresh
                    return refreshAccessToken().get();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting valid access token", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private void saveTokens(String accessToken, String refreshToken, int expiresIn) {
        SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
        prefs.edit()
            .putString("spotify_access_token", accessToken)
            .putString("spotify_refresh_token", refreshToken)
            .putInt("spotify_expires_in", expiresIn)
            .apply();

        Log.d(TAG, "Saved access token and refresh token to SharedPreferences (expires in " + expiresIn + " seconds)");
    }

    // Overload for backward compatibility when expires_in is not provided
    private void saveTokens(String accessToken, String refreshToken) {
        saveTokens(accessToken, refreshToken, 3600); // Default to 1 hour
    }

    // Getters for important objects
    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
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