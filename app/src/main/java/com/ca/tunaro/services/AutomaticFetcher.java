package com.ca.tunaro.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.database.DatabaseHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import se.michaelthelin.spotify.SpotifyApi;

public class AutomaticFetcher {
    private static final String TAG = "AutomaticFetcher";
    private static final String PREFS_NAME = "AutoFetcherPrefs";
    private static final String PREF_USERNAME = "fetcher_username";
    private static final String PREF_PASSWORD = "fetcher_password";
    private static final String PREF_JWT_TOKEN = "fetcher_jwt";
    private static final String PREF_API_KEY = "fetcher_api_key";
    private static final String PREF_REGISTERED = "fetcher_registered";
    private static final String PREF_ENABLED = "fetcher_enabled";
    private static final String PREF_TOTAL_IMPORTED = "fetcher_total_imported";
    private static final String PREF_LAST_FETCH_TIME = "fetcher_last_fetch_time";
    private static final String PREF_TOTAL_FETCH_COUNT = "fetcher_total_fetch_count";

    private final Context context;
    private final ServerApiClient apiClient;

    // Static fetch state tracking for cross-activity visibility
    private static volatile boolean fetchInProgress = false;
    private static FetchCompletionListener completionListener = null;

    public interface FetchCompletionListener {
        void onFetchCompleted(int importedCount);
    }

    public static boolean isFetchInProgress() {
        return fetchInProgress;
    }

    public static void setFetchCompletionListener(FetchCompletionListener listener) {
        completionListener = listener;
    }

    private static void notifyFetchCompleted(int importedCount) {
        fetchInProgress = false;
        if (completionListener != null) {
            completionListener.onFetchCompleted(importedCount);
            completionListener = null; // Clear after notifying
        }
    }

    public AutomaticFetcher(Context context) {
        this.context = context;
        this.apiClient = new ServerApiClient();
    }

    //#region Credentials Model and Storage
    public static class FetcherCredentials {
        private String username;
        private String password;
        private String jwtToken;
        private String apiKey;
        private boolean registered;
        private boolean enabled;

        public FetcherCredentials(String username, String password, String jwtToken,
                                  String apiKey, boolean registered, boolean enabled) {
            this.username = username;
            this.password = password;
            this.jwtToken = jwtToken;
            this.apiKey = apiKey;
            this.registered = registered;
            this.enabled = enabled;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getJwtToken() {
            return jwtToken;
        }

        public String getApiKey() {
            return apiKey;
        }

        public boolean isRegistered() {
            return registered;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    public FetcherCredentials getStoredCredentials() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String username = prefs.getString(PREF_USERNAME, null);
        if (username == null) return null;

        String password = prefs.getString(PREF_PASSWORD, null);
        String jwt = prefs.getString(PREF_JWT_TOKEN, null);
        String apiKey = prefs.getString(PREF_API_KEY, null);
        boolean registered = prefs.getBoolean(PREF_REGISTERED, false);
        boolean enabled = prefs.getBoolean(PREF_ENABLED, true);

        return new FetcherCredentials(username, password, jwt, apiKey, registered, enabled);
    }

    private void storeCredentials(String username, String password, String jwt, String apiKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(PREF_USERNAME, username)
                .putString(PREF_PASSWORD, password)
                .putString(PREF_JWT_TOKEN, jwt)
                .putString(PREF_API_KEY, apiKey)
                .putBoolean(PREF_REGISTERED, true)
                .putBoolean(PREF_ENABLED, true);
        editor.apply();
    }

    public void setEnabled(boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    public void markAsDeregistered() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(PREF_REGISTERED, false)
                .putBoolean(PREF_ENABLED, false)
                .apply();
    }

    private String getStoredSpotifyRefreshToken() {
        SharedPreferences prefs = context.getSharedPreferences("SpotifyPrefs", Context.MODE_PRIVATE);
        return prefs.getString("spotify_refresh_token", null);
    }
    //#endregion

    //#region Registration Flow
    public interface RegistrationCallback {
        void onSuccess();

        void onError(String error);
    }

    public void registerAutomaticFetcher(RegistrationCallback callback) {
        showToast("Registering for automatic fetching...");

        CompletableFuture.runAsync(() -> {
            try {
                MainActivity mainActivity = MainActivity.getInstance();
                if (mainActivity == null) {
                    showToast("Registration failed: MainActivity not available");
                    callback.onError("MainActivity not available");
                    return;
                }

                String spotifyUserId = mainActivity.getUserID();
                if (spotifyUserId == null) {
                    showToast("Registration failed: Spotify user ID not available");
                    callback.onError("Spotify user ID not available");
                    return;
                }

                FetcherCredentials existingCreds = getStoredCredentials();

                if (existingCreds != null && existingCreds.getPassword() != null && !existingCreds.getPassword().isEmpty()) {
                    // Re-registration with existing credentials
                    try {
                        registerWithExistingCredentials(existingCreds);
                        showToast("Automatic fetching registered successfully");
                        callback.onSuccess();
                    } catch (Exception e) {
                        Log.w(TAG, "Re-registration failed, trying new registration", e);
                        // Fall back to new registration with fresh credentials
                        registerWithNewCredentials(spotifyUserId, UUID.randomUUID().toString(), callback);
                    }
                } else {
                    // First-time registration
                    String password = UUID.randomUUID().toString();
                    registerWithNewCredentials(spotifyUserId, password, callback);
                }

            } catch (Exception e) {
                Log.e(TAG, "Registration error", e);
                showToast("Registration failed: " + e.getMessage());
                callback.onError("Registration failed: " + e.getMessage());
            }
        });
    }

    private void registerWithNewCredentials(String username, String password, RegistrationCallback callback) {
        try {
            // Step 1: Register user on server-api
            apiClient.register(username, password);

            // Step 2: Login to get JWT tokens
            ServerApiClient.LoginResponse loginResponse = apiClient.login(username, password);
            String jwtToken = loginResponse.getAccessToken();

            // Step 3: Import Spotify tokens to server-api
            importSpotifyTokensToServer(jwtToken);

            // Step 4: Generate API key for n8n
            ServerApiClient.ApiKeyResponse apiKeyResponse = apiClient.generateApiKey(jwtToken);
            String apiKey = apiKeyResponse.getApiKey();

            // Step 5: Store credentials locally
            storeCredentials(username, password, jwtToken, apiKey);

            // Step 6: Initial fetch to import any existing listens
            performInitialFetch(jwtToken);

            showToast("Automatic fetching registered successfully");
            callback.onSuccess();

        } catch (Exception e) {
            Log.e(TAG, "Registration with new credentials failed", e);
            String errorMessage = e.getMessage();

            if (errorMessage != null && errorMessage.contains("Username already registered")) {
                // Try adding number suffix
                retryWithModifiedUsername(username, callback);
            } else {
                showToast("Registration failed: " + (errorMessage != null ? errorMessage : "Unknown error"));
                callback.onError(errorMessage != null ? errorMessage : "Unknown error");
            }
        }
    }

    private void registerWithExistingCredentials(FetcherCredentials creds) throws Exception {
        // Login with existing credentials
        ServerApiClient.LoginResponse loginResponse = apiClient.login(
                creds.getUsername(),
                creds.getPassword()
        );
        String jwtToken = loginResponse.getAccessToken();

        // Generate new API key (old one was revoked)
        ServerApiClient.ApiKeyResponse apiKeyResponse = apiClient.generateApiKey(jwtToken);
        String newApiKey = apiKeyResponse.getApiKey();

        // Update stored API key and JWT
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(PREF_API_KEY, newApiKey)
                .putString(PREF_JWT_TOKEN, jwtToken)
                .putBoolean(PREF_REGISTERED, true)
                .putBoolean(PREF_ENABLED, true)
                .apply();

        // Import current Spotify tokens to server
        importSpotifyTokensToServer(jwtToken);

        // Initial fetch
        performInitialFetch(jwtToken);
    }

    private void retryWithModifiedUsername(String baseUsername, RegistrationCallback callback) {
        String modifiedUsername = baseUsername + "1";
        String password = UUID.randomUUID().toString();
        registerWithNewCredentials(modifiedUsername, password, callback);
    }

    private void importSpotifyTokensToServer(String jwtToken) throws Exception {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null) {
            throw new Exception("MainActivity not available");
        }

        SpotifyApi spotifyApi = mainActivity.getSpotifyApi();
        if (spotifyApi == null) {
            throw new Exception("Spotify API not available");
        }

        // Get current access token (may trigger refresh if expired)
        String spotifyAccessToken;
        try {
            spotifyAccessToken = mainActivity.getValidAccessToken().get();
        } catch (Exception e) {
            // Fallback to stored token
            spotifyAccessToken = spotifyApi.getAccessToken();
        }

        String spotifyRefreshToken = getStoredSpotifyRefreshToken();

        if (spotifyRefreshToken == null) {
            throw new Exception("No refresh token available");
        }

        // Get expires_in from SharedPreferences (defaults to 3600 seconds / 1 hour)
        SharedPreferences prefs = context.getSharedPreferences("SpotifyPrefs", Context.MODE_PRIVATE);
        int expiresIn = prefs.getInt("spotify_expires_in", 3600);

        apiClient.importSpotifyTokens(
                jwtToken,
                spotifyAccessToken,
                spotifyRefreshToken,
                expiresIn
        );
    }

    private void performInitialFetch(String jwtToken) {
        try {
            ServerApiClient.ListensResponse listensResponse = apiClient.fetchListens(jwtToken);
            List<ServerApiClient.Listen> listens = listensResponse.getListens();

            if (listens.isEmpty()) {
                Log.d(TAG, "No listens to import on initial fetch");
                return;
            }

            ImportResults importResults = importListens(listens);
            acknowledgeImport(jwtToken, importResults);
            updateStatistics(importResults);

            Log.d(TAG, "Initial import: " + importResults.getSuccessCount() + " listens");

        } catch (Exception e) {
            Log.e(TAG, "Initial fetch failed", e);
        }
    }
    //#endregion

    //#region Fetch-on-Launch Flow
    public interface FetchCallback {
        void onSuccess(int importedCount);

        void onError(String error);
    }

    private String refreshJwtToken(FetcherCredentials creds) throws Exception {
        // Login to get a fresh JWT token (JWTs expire after 15-30 minutes typically)
        ServerApiClient.LoginResponse loginResponse = apiClient.login(
                creds.getUsername(),
                creds.getPassword()
        );

        String freshJwtToken = loginResponse.getAccessToken();

        // Update stored JWT token
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_JWT_TOKEN, freshJwtToken).apply();

        Log.d(TAG, "Refreshed JWT token for user: " + creds.getUsername());
        return freshJwtToken;
    }

    public CompletableFuture<Void> performFetchOnLaunch(FetchCallback callback) {
        FetcherCredentials creds = getStoredCredentials();

        if (creds == null || !creds.isRegistered() || !creds.isEnabled()) {
            Log.d(TAG, "Automatic fetching not active, skipping");
            return CompletableFuture.completedFuture(null);
        }

        // Mark fetch as in progress
        fetchInProgress = true;

        return CompletableFuture.runAsync(() -> {
            try {
                // Refresh JWT token on every launch (JWT tokens expire)
                String freshJwtToken = refreshJwtToken(creds);

                // Check Spotify connection status on server
                ServerApiClient.SpotifyStatusResponse statusResponse = apiClient.checkSpotifyStatus(
                        freshJwtToken
                );

                if (!statusResponse.isConnected()) {
                    // Server doesn't have valid Spotify tokens, import them
                    importSpotifyTokensToServer(freshJwtToken);
                }

                // Fetch unimported listens
                ServerApiClient.ListensResponse listensResponse = apiClient.fetchListens(
                        freshJwtToken
                );
                List<ServerApiClient.Listen> listens = listensResponse.getListens();

                if (listens.isEmpty()) {
                    Log.d(TAG, "No new listens to import");
                    updateLastFetchStats(0, System.currentTimeMillis());
                    notifyFetchCompleted(0);
                    callback.onSuccess(0);
                    return;
                }

                // Import listens to local database
                ImportResults importResults = importListens(listens);

                // Acknowledge import to server
                acknowledgeImport(freshJwtToken, importResults);

                // Update statistics
                updateStatistics(importResults);

                int count = importResults.getSuccessCount();
                if (count > 0) {
                    showToast("Imported " + count + " new listens");
                }
                notifyFetchCompleted(count);
                callback.onSuccess(count);

            } catch (Exception e) {
                Log.e(TAG, "Fetch failed on first attempt", e);

                // Retry once after 2 seconds
                try {
                    Thread.sleep(2000);

                    // Refresh token again for retry
                    String retryJwtToken = refreshJwtToken(creds);
                    ServerApiClient.ListensResponse listensResponse = apiClient.fetchListens(retryJwtToken);
                    ImportResults results = importListens(listensResponse.getListens());
                    acknowledgeImport(retryJwtToken, results);
                    updateStatistics(results);

                    int count = results.getSuccessCount();
                    if (count > 0) {
                        showToast("Imported " + count + " new listens");
                    }
                    notifyFetchCompleted(count);
                    callback.onSuccess(count);
                } catch (Exception retryError) {
                    Log.e(TAG, "Fetch failed on retry", retryError);
                    showToast("Failed to fetch listening history");
                    notifyFetchCompleted(0);
                    callback.onError("Failed to fetch listening history");
                }
            }
        });
    }
    //#endregion

    //#region Import Logic
    public static class ImportResults {
        private int successCount;
        private int failedCount;
        private List<String> successfulTrackIds;
        private boolean allImported;

        public ImportResults(int successCount, int failedCount,
                             List<String> successfulTrackIds, boolean allImported) {
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.successfulTrackIds = successfulTrackIds;
            this.allImported = allImported;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public List<String> getSuccessfulTrackIds() {
            return successfulTrackIds;
        }

        public boolean isAllImported() {
            return allImported;
        }
    }

    private ImportResults importListens(List<ServerApiClient.Listen> listens) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        List<String> successfulIds = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (ServerApiClient.Listen listen : listens) {
            try {
                // Use track duration from server response (no need to fetch from Spotify!)
                int trackDuration = listen.getTrackDuration();

                // Parse timestamp to milliseconds
                long playedAtMs = parseTimestampToMillis(listen.getPlayedAt());

                // Use existing duplicate detection logic
                if (!dbHelper.hasListenWithinDuration(
                        listen.getTrackId(),
                        playedAtMs,
                        trackDuration)) {

                    dbHelper.addListenRecordWithTimestamp(
                            listen.getTrackId(),
                            listen.getPlayedAt()
                    );

                    successfulIds.add(listen.getTrackId());
                    successCount++;

                    Log.d(TAG, "Imported listen: " +
                            (listen.getTrackName() != null ? listen.getTrackName() : listen.getTrackId()));
                } else {
                    Log.d(TAG, "Duplicate listen detected for " +
                            (listen.getTrackName() != null ? listen.getTrackName() : listen.getTrackId()) +
                            ", skipping");
                    // Still counts as success (handled gracefully)
                    successfulIds.add(listen.getTrackId());
                    successCount++;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error importing listen " + listen.getTrackId(), e);
                failedCount++;
            }
        }

        return new ImportResults(
                successCount,
                failedCount,
                successfulIds,
                failedCount == 0
        );
    }

    private long parseTimestampToMillis(String utcTimestamp) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.getDefault()
        );
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = dateFormat.parse(utcTimestamp);
        return date.getTime();
    }

    private void acknowledgeImport(String jwtToken, ImportResults results) {
        int retryCount = 0;
        int maxRetries = 3;

        while (retryCount < maxRetries) {
            try {
                if (results.isAllImported()) {
                    apiClient.acknowledgeListens(jwtToken, "All", 0, null);
                } else {
                    apiClient.acknowledgeListens(
                            jwtToken,
                            "Partial",
                            results.getSuccessCount(),
                            results.getSuccessfulTrackIds()
                    );
                }

                Log.d(TAG, "Successfully acknowledged import");
                return;

            } catch (Exception e) {
                retryCount++;
                Log.e(TAG, "Acknowledgement failed (attempt " + retryCount + "/" + maxRetries + ")", e);

                if (retryCount >= maxRetries) {
                    Log.e(TAG, "Acknowledgement failed after " + maxRetries + " attempts, giving up");
                    return;
                } else {
                    try {
                        Thread.sleep(1000 * retryCount); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
    //#endregion

    //#region Statistics
    private void updateStatistics(ImportResults results) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        int currentTotal = prefs.getInt(PREF_TOTAL_IMPORTED, 0);
        prefs.edit()
                .putInt(PREF_TOTAL_IMPORTED, currentTotal + results.getSuccessCount())
                .putLong(PREF_LAST_FETCH_TIME, System.currentTimeMillis())
                .putInt(PREF_TOTAL_FETCH_COUNT, prefs.getInt(PREF_TOTAL_FETCH_COUNT, 0) + 1)
                .apply();
    }

    private void updateLastFetchStats(int imported, long timestamp) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (imported > 0) {
            int currentTotal = prefs.getInt(PREF_TOTAL_IMPORTED, 0);
            prefs.edit()
                    .putInt(PREF_TOTAL_IMPORTED, currentTotal + imported)
                    .apply();
        }

        prefs.edit()
                .putLong(PREF_LAST_FETCH_TIME, timestamp)
                .putInt(PREF_TOTAL_FETCH_COUNT, prefs.getInt(PREF_TOTAL_FETCH_COUNT, 0) + 1)
                .apply();
    }

    public String getStatisticsDisplay() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int totalImported = prefs.getInt(PREF_TOTAL_IMPORTED, 0);
        long lastFetchTime = prefs.getLong(PREF_LAST_FETCH_TIME, 0);
        int fetchCount = prefs.getInt(PREF_TOTAL_FETCH_COUNT, 0);

        String relativeTime;
        if (lastFetchTime > 0) {
            relativeTime = getRelativeTimeString(lastFetchTime);
        } else {
            relativeTime = "Never";
        }

        return totalImported + " total imported\n" +
                "Last fetch: " + relativeTime + "\n" +
                "Total fetches: " + fetchCount;
    }

    private String getRelativeTimeString(long timestamp) {
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - timestamp;

        long minutes = timeDiff / (1000 * 60);
        long hours = timeDiff / (1000 * 60 * 60);
        long days = timeDiff / (1000 * 60 * 60 * 24);

        if (minutes < 60) {
            if (minutes <= 1) return "1 minute ago";
            return minutes + " minutes ago";
        } else if (hours < 24) {
            if (hours == 1) return "1 hour ago";
            return hours + " hours ago";
        } else {
            if (days == 1) return "1 day ago";
            return days + " days ago";
        }
    }
    //#endregion

    //#region Deregistration
    public interface DeregistrationCallback {
        void onSuccess();

        void onError(String error);
    }

    public CompletableFuture<Void> deregisterFetcher(DeregistrationCallback callback) {
        FetcherCredentials creds = getStoredCredentials();
        if (creds == null) {
            showToast("Not registered");
            callback.onError("Not registered");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // Revoke API key on server
                apiClient.revokeApiKey(creds.getJwtToken());

                // Mark as not registered (keep credentials for re-registration)
                markAsDeregistered();

                showToast("Deregistered successfully");
                callback.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Deregistration failed", e);
                showToast("Deregistration failed: " + e.getMessage());
                callback.onError("Deregistration failed: " + e.getMessage());
            }
        });
    }
    //#endregion

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            Log.v(TAG, "showed Toast: " + message);
        });
    }
}
