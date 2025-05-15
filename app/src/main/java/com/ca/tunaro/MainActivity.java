package com.ca.tunaro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.requests.data.users_profile.GetCurrentUsersProfileRequest;

public class MainActivity extends AppCompatActivity {
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

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_splash);

        // Set the singleton instance
        instance = this;

        // Initialize these here
        CLIENT_ID = getString(R.string.client_id);
        CLIENT_SECRET = getString(R.string.client_secret);
        REDIRECT_URI = SpotifyHttpManager.makeUri(getString(R.string.redirect_uri));

        // Initialize the CompletableFuture
        authenticationFuture = new CompletableFuture<>();

        // Initialize the PlaylistSetup Class
        PlaylistSetup.initialize(this);

        PlaybackManager.getInstance().initialize(
                getApplicationContext(),
                CLIENT_ID,
                REDIRECT_URI.toString()
        );

        // Start authentication
        if (spotifyApi == null) {
            authenticateSpotify()
                    .thenRunAsync(() -> {
                        // Launch HomeActivity after authentication
                        runOnUiThread(() -> {
                            Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                            startActivity(intent);
                        });
                    }, executor)
                    .exceptionally(throwable -> {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                            finish(); // Close app if authentication fails
                        });
                        return null;
                    });
        }
    }

    private CompletableFuture<Void> authenticateSpotify() {
        // Start the authentication process
        AuthorizationRequest.Builder builder =
                new AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI.toString());
        builder.setScopes(new String[]{"app-remote-control", "streaming", "playlist-read-private", "playlist-modify-private"});
        AuthorizationRequest request = builder.build();

        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request);

        // update spotifyAPI
        spotifyApi = new SpotifyApi.Builder()
                .setClientId(CLIENT_ID)
                .setClientSecret(CLIENT_SECRET)
                .setRedirectUri(REDIRECT_URI)
                .setAccessToken(getAccessToken())
                .build();

        // Create a new CompletableFuture for this authentication process
        CompletableFuture<Void> authFuture = new CompletableFuture<>();

        // Store this future so we can complete it in onActivityResult
        this.authenticationFuture = authFuture;

        // Return a new CompletableFuture that chains the authentication and user profile fetch
        return authFuture.thenCompose(aVoid -> {
            spotifyApi.setAccessToken(getAccessToken());
            return getCurrentUsersProfile_Async();
        }).thenRunAsync(() -> {
            runOnUiThread(this::connectSpotifyAppRemote);
        }, executor);
    }

    public void connectSpotifyAppRemote() {
        // Connect to Spotify App Remote here
        ConnectionParams connectionParams =
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI.toString())
                        .showAuthView(true)
                        .build();

        SpotifyAppRemote.connect(this, connectionParams,
                new Connector.ConnectionListener() {
                    public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                        mSpotifyAppRemote = spotifyAppRemote;
                        Log.d("MainActivity", "Connected to remote");
                        Toast.makeText(MainActivity.this, "Connected to Spotify", Toast.LENGTH_SHORT).show();
                    }

                    public void onFailure(Throwable throwable) {
                        Log.e("MainActivity", "Remote connection failed: " + throwable.getMessage(), throwable);
                        // Log the full stack trace
                        StringWriter sw = new StringWriter();
                        throwable.printStackTrace(new PrintWriter(sw));
                        Log.e("MainActivity", "Stack trace: " + sw.toString());

                        Toast.makeText(MainActivity.this,
                                "Failed to connect to Spotify. Error: " + throwable.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private CompletableFuture<Void> getCurrentUsersProfile_Async() {
        final GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi.getCurrentUsersProfile()
                .build();
        return getCurrentUsersProfileRequest.executeAsync()
                .thenAccept(user -> {
                    userID = user.getId();
                    userDisplayName = user.getDisplayName();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Logged in as " + userDisplayName, Toast.LENGTH_SHORT).show();
                    });
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        // We don't disconnect on stop anymore, since we're just a background activity
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect when the app is closing
        if (mSpotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        }
        instance = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_CODE) {
            AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, intent);
            switch (response.getType()) {
                case TOKEN:
                    // Authentication successful
                    String token = response.getAccessToken();
                    saveAccessToken(token);

                    spotifyApi = new SpotifyApi.Builder()
                            .setClientId(CLIENT_ID)
                            .setClientSecret(CLIENT_SECRET)
                            .setRedirectUri(REDIRECT_URI)
                            .setAccessToken(token) // Use the token directly
                            .build();

                    // Complete the future to signal that authentication is done
                    if (authenticationFuture != null) {
                        authenticationFuture.complete(null);
                    }
                    break;
                case ERROR:
                    // Authentication failed
                    String errorMessage = response.getError();
                    Toast.makeText(this, "Authorization failed: " + errorMessage, Toast.LENGTH_LONG).show();
                    // Complete the future exceptionally to signal authentication failure
                    if (authenticationFuture != null) {
                        authenticationFuture.completeExceptionally(new Exception(errorMessage));
                    }
                    break;
                default:
                    Toast.makeText(this, "Spotify login was cancelled", Toast.LENGTH_SHORT).show();
                    // Complete the future exceptionally to signal authentication cancellation
                    if (authenticationFuture != null) {
                        authenticationFuture.completeExceptionally(new Exception("Authentication cancelled"));
                    }
                    break;
            }
        }
    }

    private void saveAccessToken(String token) {
        // Save token to SharedPreferences or secure storage
        SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
        prefs.edit().putString("spotify_access_token", token).apply();
    }

    private String getAccessToken() {
        SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
        return prefs.getString("spotify_access_token", null); // Returns null if the token is not found
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

    public String getUserDisplayName() {
        return userDisplayName;
    }
}