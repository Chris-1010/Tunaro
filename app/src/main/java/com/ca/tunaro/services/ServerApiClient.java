package com.ca.tunaro.services;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ServerApiClient {
    private static final String TAG = "ServerApiClient";
    private static final String BASE_URL = "https://api.server-chris.com";
    private static final int TIMEOUT_MS = 30000; // 30 seconds

    //#region Response Models
    public static class LoginResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;

        public LoginResponse(String accessToken, String refreshToken, String tokenType) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.tokenType = tokenType;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public String getTokenType() { return tokenType; }
    }

    public static class ApiKeyResponse {
        private String apiKey;
        private String message;

        public ApiKeyResponse(String apiKey, String message) {
            this.apiKey = apiKey;
            this.message = message;
        }

        public String getApiKey() { return apiKey; }
        public String getMessage() { return message; }
    }

    public static class SpotifyStatusResponse {
        private boolean connected;
        private String spotifyUsername;
        private String tokenExpiresAt;

        public SpotifyStatusResponse(boolean connected, String spotifyUsername, String tokenExpiresAt) {
            this.connected = connected;
            this.spotifyUsername = spotifyUsername;
            this.tokenExpiresAt = tokenExpiresAt;
        }

        public boolean isConnected() { return connected; }
        public String getSpotifyUsername() { return spotifyUsername; }
        public String getTokenExpiresAt() { return tokenExpiresAt; }
    }

    public static class Listen {
        private String userId;
        private String trackId;
        private String playedAt;
        private int trackDuration;
        private String trackName;

        public Listen(String userId, String trackId, String playedAt,
                     int trackDuration, String trackName) {
            this.userId = userId;
            this.trackId = trackId;
            this.playedAt = playedAt;
            this.trackDuration = trackDuration;
            this.trackName = trackName;
        }

        public String getUserId() { return userId; }
        public String getTrackId() { return trackId; }
        public String getPlayedAt() { return playedAt; }
        public int getTrackDuration() { return trackDuration; }
        public String getTrackName() { return trackName; }
    }

    public static class ListensResponse {
        private List<Listen> listens;

        public ListensResponse(List<Listen> listens) {
            this.listens = listens;
        }

        public List<Listen> getListens() { return listens; }
    }
    //#endregion

    //#region Authentication Endpoints
    public void register(String username, String password) throws Exception {
        String endpoint = BASE_URL + "/api/v1/auth/register";

        JSONObject requestBody = new JSONObject();
        requestBody.put("username", username);
        requestBody.put("password", password);

        String response = makeRequest(endpoint, "POST", requestBody.toString(), null);
        Log.d(TAG, "Registration successful: " + response);
    }

    public LoginResponse login(String username, String password) throws Exception {
        String endpoint = BASE_URL + "/api/v1/auth/login";

        JSONObject requestBody = new JSONObject();
        requestBody.put("username", username);
        requestBody.put("password", password);

        String response = makeRequest(endpoint, "POST", requestBody.toString(), null);
        JSONObject jsonResponse = new JSONObject(response);

        return new LoginResponse(
            jsonResponse.getString("access_token"),
            jsonResponse.getString("refresh_token"),
            jsonResponse.getString("token_type")
        );
    }

    public ApiKeyResponse generateApiKey(String jwtToken) throws Exception {
        String endpoint = BASE_URL + "/api/v1/auth/api-key/generate";

        String response = makeRequest(endpoint, "POST", "", jwtToken);
        JSONObject jsonResponse = new JSONObject(response);

        return new ApiKeyResponse(
            jsonResponse.getString("api_key"),
            jsonResponse.getString("message")
        );
    }

    public void revokeApiKey(String jwtToken) throws Exception {
        String endpoint = BASE_URL + "/api/v1/auth/api-key/revoke";

        String response = makeRequest(endpoint, "DELETE", null, jwtToken);
        Log.d(TAG, "API key revoked: " + response);
    }
    //#endregion

    //#region Spotify Endpoints
    public SpotifyStatusResponse checkSpotifyStatus(String jwtToken) throws Exception {
        String endpoint = BASE_URL + "/api/v1/spotify/status";

        String response = makeRequest(endpoint, "GET", null, jwtToken);
        JSONObject jsonResponse = new JSONObject(response);

        boolean connected = jsonResponse.getBoolean("connected");
        String username = jsonResponse.optString("spotify_username", null);
        String expiresAt = jsonResponse.optString("token_expires_at", null);

        return new SpotifyStatusResponse(connected, username, expiresAt);
    }

    public void importSpotifyTokens(String jwtToken, String accessToken,
                                   String refreshToken, int expiresIn) throws Exception {
        String endpoint = BASE_URL + "/api/v1/spotify/token/import";

        JSONObject requestBody = new JSONObject();
        requestBody.put("access_token", accessToken);
        requestBody.put("refresh_token", refreshToken);
        requestBody.put("expires_in", expiresIn);

        String response = makeRequest(endpoint, "POST", requestBody.toString(), jwtToken);
        Log.d(TAG, "Spotify tokens imported: " + response);
    }

    public ListensResponse fetchListens(String jwtToken) throws Exception {
        String endpoint = BASE_URL + "/api/v1/spotify/listens";

        String response = makeRequest(endpoint, "GET", null, jwtToken);
        JSONObject jsonResponse = new JSONObject(response);
        JSONArray listensArray = jsonResponse.getJSONArray("listens");

        List<Listen> listens = new ArrayList<>();
        for (int i = 0; i < listensArray.length(); i++) {
            JSONObject listenObj = listensArray.getJSONObject(i);
            listens.add(new Listen(
                listenObj.optString("user_id", ""),
                listenObj.getString("track_id"),
                listenObj.getString("played_at"),
                listenObj.getInt("track_duration"),
                listenObj.optString("track_name", null)
            ));
        }

        return new ListensResponse(listens);
    }

    public void acknowledgeListens(String jwtToken, String status,
                                  int importedCount, List<String> trackIds) throws Exception {
        String endpoint = BASE_URL + "/api/v1/spotify/listens/acknowledge";

        JSONObject requestBody = new JSONObject();
        requestBody.put("status", status);

        if (!status.equals("All")) {
            requestBody.put("imported_count", importedCount);
            requestBody.put("track_ids", new JSONArray(trackIds));
        }

        String response = makeRequest(endpoint, "POST", requestBody.toString(), jwtToken);
        Log.d(TAG, "Listens acknowledged: " + response);
    }
    //#endregion

    //#region HTTP Request Helper
    private String makeRequest(String urlString, String method, String body, String jwtToken)
            throws Exception {

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json");

            if (jwtToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            }

            if (body != null && !body.isEmpty() &&
                (method.equals("POST") || method.equals("PUT"))) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int responseCode = connection.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                // Success
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    return response.toString();
                }
            } else {
                // Error
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }

                    // Try to extract error message from JSON
                    try {
                        JSONObject errorJson = new JSONObject(errorResponse.toString());
                        String errorMessage = errorJson.optString("detail", errorResponse.toString());
                        throw new Exception("Server error (" + responseCode + "): " + errorMessage);
                    } catch (Exception e) {
                        throw new Exception("Server error (" + responseCode + "): " + errorResponse.toString());
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }
    //#endregion
}
