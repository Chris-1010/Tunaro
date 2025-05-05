package com.ca.tunaro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.types.Track;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.requests.data.users_profile.GetCurrentUsersProfileRequest;

public class MainActivity extends BaseActivity {
    private String CLIENT_ID;
    private String CLIENT_SECRET;
    private URI REDIRECT_URI;
    final int REQUEST_CODE = 1337;
    private SpotifyApi spotifyApi;
    private String userID;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    private CompletableFuture<Void> authenticationFuture;

    private SpotifyAppRemote mSpotifyAppRemote;

    // Tabs (fragments) at bottom
    // BottomNavigationView bottomNavigationView;
    TabLayout tabLayout;
    ViewPager2 viewPager2;
    ViewPagerAdapter viewPagerAdapter;
    LibraryFragment libraryFragment;
    RankingsFragment rankingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize these here
        CLIENT_ID = getString(R.string.client_id);
        CLIENT_SECRET = getString(R.string.client_secret);
        REDIRECT_URI = SpotifyHttpManager.makeUri(getString(R.string.redirect_uri));

        // Initialize the CompletableFuture
        authenticationFuture = new CompletableFuture<>();

        // Initialize PlaybackManager
        playbackManager.initialize(this, CLIENT_ID, REDIRECT_URI.toString());

        viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager2 = findViewById(R.id.view_pager);
        viewPager2.setAdapter(viewPagerAdapter);
        // Initialize the PlaylistSetup Class
        PlaylistSetup.initialize(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (spotifyApi == null) {
            authenticateSpotify()
                    .thenRunAsync(() -> {
                        runOnUiThread(this::prepareFragments);
                    }, executor)
                    .exceptionally(throwable -> {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                        });
                        return null;
                    });
        }
    }

    public void prepareFragments() {
        preparePlayFragment();

        runOnUiThread(this::onFragmentsPrepared);
    }

    public void preparePlayFragment() {
        if (userID == null) {
            System.out.println("userID null");
            return;
        }

        System.out.println(userID);
        PlaylistSetup.getPlaylistData(userID, spotifyApi)
                .thenAccept(playlists -> {
                    System.out.println(playlists);
                    runOnUiThread(() -> {
                        // Add a small delay to ensure fragment is ready
                        viewPager2.post(() -> {
                            PlayFragment playFragment = (PlayFragment) viewPagerAdapter.getFragment(0);
                            if (playFragment != null && playFragment.isAdded()) {
                                Log.d("preparePlayFragment", "Fragment is ready. Playlist Size: " + playlists.size());
                                TextView playlistCountIndicator = findViewById(R.id.playlistCount);
                                playlistCountIndicator.setText(getString(R.string.playlist_count, playlists.size()));
                                playFragment.updatePlaylists(playlists);
                            } else {
                                // If fragment isn't ready, retry after a short delay
                                viewPager2.postDelayed(() -> {
                                    Log.d("preparePlayFragment", "Fragment is not ready, Retrying. Playlist Size: " + playlists.size());
                                    PlayFragment retryFragment = (PlayFragment) viewPagerAdapter.getFragment(0);
                                    if (retryFragment != null && retryFragment.isAdded()) {
                                        Log.d("preparePlayFragment", "After retrying, Fragment is ready. Playlist Size: " + playlists.size());
                                        TextView playlistCountIndicator = findViewById(R.id.playlistCount);
                                        playlistCountIndicator.setText(getString(R.string.playlist_count, playlists.size()));
                                        retryFragment.updatePlaylists(playlists);
                                    }
                                }, 100); // 100ms delay
                            }
                        });
                    });
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error loading playlists: " + throwable.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    public void onFragmentsPrepared() {

        // Tabs (fragments) at bottom
        tabLayout = findViewById(R.id.tab_layout);
        viewPager2 = findViewById(R.id.view_pager);
        viewPager2.setAdapter(viewPagerAdapter);

        tabLayout.setVisibility(View.VISIBLE);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager2.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Objects.requireNonNull(tabLayout.getTabAt(position)).select();
            }
        });
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

        // Complete this future in onActivityResult
        this.authenticationFuture = authFuture;

        // Return a new CompletableFuture that chains the authentication and user profile fetch
        return authFuture.thenCompose(aVoid -> {
            spotifyApi.setAccessToken(getAccessToken());
            return getCurrentUsersProfile_Async();
        }).thenRunAsync(() -> runOnUiThread(() -> playbackManager.connectSpotify(this, null)), executor);
    }

    public void start(View v) {
        disable(v);

        //Subscribe to PlayerState
        mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerState()
                .setEventCallback(playerState -> {
                    final Track track = playerState.track;
                    if (track != null) {
                        Log.d("MainActivity", track.name + " by " + track.artist.name);
//                        TextView trackDisplay = findViewById(R.id.trackDisplay);
//                        TextView artistDisplay = findViewById(R.id.artistDisplay);
//                        ImageView songCover = findViewById(R.id.songCover);
//                        trackDisplay.setText(track.name);
//                        artistDisplay.setText(track.artist.name);
//                        Uri imageURI = Uri.parse(track.imageUri.raw);
//                        songCover.setImageURI(imageURI);
                    }
                });
    }

    private CompletableFuture<Void> getCurrentUsersProfile_Async() {
        final GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi.getCurrentUsersProfile()
                .build();
        return getCurrentUsersProfileRequest.executeAsync()
                .thenAccept(user -> {
                    userID = user.getId();
                    String displayName = user.getDisplayName();

                    runOnUiThread(() -> {
                        PlayFragment playFragment = (PlayFragment) viewPagerAdapter.getFragment(0);
                        TextView s = playFragment.requireView().findViewById(R.id.displayName);
                        s.setText(getString(R.string.logged_in_as, displayName));
                        Toast.makeText(this, "Logged in as " + displayName, Toast.LENGTH_SHORT).show();
                    });
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    /**
     * Disconnect from Spotify remote.
     * Note: only disconnect from Spotify when the app is actually closing,
     * not during navigation between activities. This ensures the connection
     * remains active when playing songs from other screens.
     */
    @Override
    protected void onStop() {
        // Only disconnect if the app is actually closing
        if (isFinishing()) {
            SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        }
        super.onStop();
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
                    updateUILoggedIn();
                    // Complete the future to signal that authentication is done
                    if (authenticationFuture != null) {
                        authenticationFuture.complete(null);
                    }
                    break;
                case ERROR:
                    // Authentication failed
                    String errorMessage = response.getError();
                    showErrorToUser("Authorization failed: " + errorMessage);
                    updateUILoggedOut();
                    // Complete the future exceptionally to signal authentication failure
                    if (authenticationFuture != null) {
                        authenticationFuture.completeExceptionally(new Exception(errorMessage));
                    }
                    break;
                default:
                    showMessageToUser("Spotify login was cancelled");
                    updateUILoggedOut();
                    // Complete the future exceptionally to signal authentication cancellation
                    if (authenticationFuture != null) {
                        authenticationFuture.completeExceptionally(new Exception("Authentication cancelled"));
                    }
                    break;
            }
        }
    }

    private void onSpotifyRemoteConnected() {
        // Enable UI elements that require Spotify connection
        Toast.makeText(this, "Connected to Spotify", Toast.LENGTH_SHORT).show();
    }

    private void updateUILoggedIn() {
        // Update your UI elements to reflect logged-in state
        // For example:
//        findViewById(R.id.loginButton).setVisibility(View.GONE);
//        findViewById(R.id.logoutButton).setVisibility(View.VISIBLE);
//        Switch s = (Switch) findViewById(R.id.authSwitch);
//        s.setChecked(true);
        // Enable Spotify-related features in your UI
    }

    private void updateUILoggedOut() {
        // Update your UI elements to reflect logged-out state
        // For example:
//        findViewById(R.id.loginButton).setVisibility(View.VISIBLE);
//        findViewById(R.id.logoutButton).setVisibility(View.GONE);
//        Switch s = (Switch) findViewById(R.id.authSwitch);
//        s.setChecked(false);
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

    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
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

    public String getUserID() {
        return userID;
    }

    public void disable(View v) {
        v.setEnabled(false);
    }

//    public String getStringValue(String key) {
//        SharedPreferences prefs = getSharedPreferences("MyStrings", MODE_PRIVATE);
//        return prefs.getString(key, null);
//    }
//
//    public void updateStringValue(String key, String newValue) {
//        SharedPreferences prefs = getSharedPreferences("MyStrings", MODE_PRIVATE);
//        SharedPreferences.Editor editor = prefs.edit();
//        editor.putString(key, newValue);
//        editor.apply();
//    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            playbackManager.disconnect();
        }
        super.onDestroy();
    }
}