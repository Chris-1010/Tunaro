package com.ca.tunaro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.ca.tunaro.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.types.Track;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import java.io.IOException;
import java.net.URI;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.User;
import se.michaelthelin.spotify.requests.data.users_profile.GetCurrentUsersProfileRequest;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private String CLIENT_ID;
    private String CLIENT_SECRET;
    private URI REDIRECT_URI;
    final int REQUEST_CODE = 1337;
    private SpotifyAppRemote mSpotifyAppRemote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize these here
        CLIENT_ID = getString(R.string.client_id);
        CLIENT_SECRET = getString(R.string.client_secret);
        REDIRECT_URI = SpotifyHttpManager.makeUri(getString(R.string.redirect_uri));

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
    }

////    @Override
////    protected void onStart() {
    public void start(View v) {
//        super.onStart();
        disable(v);

        AuthorizationRequest.Builder builder =
                new AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI.toString());

        builder.setScopes(new String[]{"streaming"});
        AuthorizationRequest request = builder.build();

        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request);

        // Should now be logged in
        connectSpotifyAppRemote();

        // Link with WebAPI
        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setClientId(CLIENT_ID)
                .setClientSecret(CLIENT_SECRET)
                .setRedirectUri(REDIRECT_URI)
                .setAccessToken(getAccessToken())
                .build();

        // Now retrieve user's profile
        GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi.getCurrentUsersProfile()
                .build();
        try {
            final User user = getCurrentUsersProfileRequest.execute();
            String name = user.getDisplayName();
            System.out.println("Display name: " + name);
            TextView s = (TextView) findViewById(R.id.displayName);
            s.setText(name);
        } catch (IOException | SpotifyWebApiException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
    }

    private void connected() {
        // Play a playlist
        mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DX2sUQwD7tbmL");

        // Subscribe to PlayerState
        mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerState()
                .setEventCallback(playerState -> {
                    final Track track = playerState.track;
                    if (track != null) {
                        Log.d("MainActivity", track.name + " by " + track.artist.name);
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, intent);
            switch (response.getType()) {
                // Response was successful and contains auth token
                case TOKEN:
                    // Handle successful response
                    String token = response.getAccessToken();
                    // Save the token for later use
                    saveAccessToken(token);
                    // Update UI to show logged-in state
                    updateUILoggedIn();
                    break;

                // Auth flow returned an error
                case ERROR:
                    // Handle error response
                    String errorMessage = response.getError();
                    // Show error message to user
                    showErrorToUser("Authorization failed: " + errorMessage);
                    // Update UI to show not logged in state
                    updateUILoggedOut();
                    break;

                // Most likely auth flow was cancelled
                default:
                    // Handle other cases
                    showMessageToUser("Spotify login was cancelled");
                    updateUILoggedOut();
                    break;
            }
        }
    }

    private void saveAccessToken(String token) {
        // Save token to SharedPreferences or secure storage
        SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
        prefs.edit().putString("spotify_access_token", token).apply();
    }

    private void updateUILoggedIn() {
        // Update your UI elements to reflect logged-in state
        // For example:
        findViewById(R.id.loginButton).setVisibility(View.GONE);
        findViewById(R.id.logoutButton).setVisibility(View.VISIBLE);
        Switch s = (Switch) findViewById(R.id.authSwitch);
        s.setChecked(true);
        // Enable Spotify-related features in your UI
    }

    private void updateUILoggedOut() {
        // Update your UI elements to reflect logged-out state
        // For example:
        findViewById(R.id.loginButton).setVisibility(View.VISIBLE);
        findViewById(R.id.logoutButton).setVisibility(View.GONE);
        Switch s = (Switch) findViewById(R.id.authSwitch);
        s.setChecked(false);
        // Disable Spotify-related features in your UI
    }

    private void showErrorToUser(String message) {
        // Show error message to user, e.g., using a Toast or AlertDialog
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showMessageToUser(String message) {
        // Show message to user, e.g., using a Toast
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void connectSpotifyAppRemote() {
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
                        Log.d("MainActivity", "Connected! Yay!");
                        Switch s = (Switch) findViewById(R.id.remoteSwitch);
                        s.setChecked(true);

                        // Now you can start interacting with App Remote
                        connected();

                    }

                    public void onFailure(Throwable throwable) {
                        Log.e("MyActivity", throwable.getMessage(), throwable);

                        // Something went wrong when attempting to connect! Handle errors here
                    }
                });
    }

    private String getAccessToken() {
        SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
        return prefs.getString("spotify_access_token", null); // Returns null if the token is not found
    }

    public void disable(View v) {
        v.setEnabled(false);
        Toast.makeText(this, "Starting", Toast.LENGTH_LONG).show();
    }
}