package com.ca.tunaro.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ca.tunaro.R;
import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.utils.DeveloperHistory;
import com.ca.tunaro.utils.DarkListDialog;
import com.ca.tunaro.utils.JsonHighlighter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApiRequestFragment extends Fragment {

    private static final String BASE_URL = "https://api.spotify.com/v1";

    // Setting more text than this into the results TextView stalls the main
    // thread long enough to trigger an ANR
    private static final int MAX_DISPLAY_LENGTH = 100_000;

    private static final String[] SEARCH_TYPES =
            {"track", "album", "artist", "playlist", "show", "episode", "audiobook"};

    // Search fields that get combined into the q parameter
    private static final String FIELD_KEYWORDS = "keywords";
    private static final List<String> SEARCH_FILTERS =
            Arrays.asList("artist", "track", "album", "year", "genre", "isrc");

    private static class Param {
        final String key;
        final String hint;
        final boolean required;
        final boolean inPath;
        final String defaultValue;

        Param(String key, String hint, boolean required, boolean inPath) {
            this(key, hint, required, inPath, null);
        }

        Param(String key, String hint, boolean required, boolean inPath, String defaultValue) {
            this.key = key;
            this.hint = hint;
            this.required = required;
            this.inPath = inPath;
            this.defaultValue = defaultValue;
        }
    }

    private static class Endpoint {
        final String label;
        final String path;
        final Param[] params;
        final boolean isSearch;

        Endpoint(String label, String path, Param[] params, boolean isSearch) {
            this.label = label;
            this.path = path;
            this.params = params;
            this.isSearch = isSearch;
        }
    }

    private static final Endpoint[] ENDPOINTS = {
            new Endpoint("Get Currently Playing Track", "/me/player/currently-playing", new Param[]{
                    new Param("market", "e.g. IE", false, false, "IE"),
                    new Param("additional_types", "track,episode", false, false)
            }, false),
            new Endpoint("Get Playlist", "/playlists/{playlist_id}", new Param[]{
                    new Param("playlist_id", "e.g. 3cEYpjA9oz9GiPac4AsH4n", true, true),
                    new Param("market", "e.g. IE", false, false, "IE"),
                    new Param("fields", "e.g. items(track(name,href))", false, false),
                    new Param("additional_types", "track,episode", false, false)
            }, false),
            new Endpoint("Get Track", "/tracks/{id}", new Param[]{
                    new Param("id", "e.g. 11dFghVXANMlKmJXsNCbNl", true, true),
                    new Param("market", "e.g. IE", false, false, "IE")
            }, false),
            new Endpoint("Search", "/search", new Param[]{
                    new Param(FIELD_KEYWORDS, "free text", false, false),
                    new Param("artist", "e.g. Miles Davis", false, false),
                    new Param("track", "e.g. Doxy", false, false),
                    new Param("album", "", false, false),
                    new Param("year", "e.g. 1955-1960", false, false),
                    new Param("genre", "", false, false),
                    new Param("isrc", "", false, false)
            }, true)
    };

    private Spinner endpointSpinner;
    private LinearLayout paramsContainer;
    private TextView statusText;
    private TextView resultsText;
    private Button sendButton;

    private final LinkedHashMap<String, EditText> fields = new LinkedHashMap<>();
    private Spinner typeSpinner;
    private Map<String, String> pendingValues;

    private final OkHttpClient client = new OkHttpClient();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_api_request, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        endpointSpinner = view.findViewById(R.id.endpoint_spinner);
        paramsContainer = view.findViewById(R.id.params_container);
        statusText = view.findViewById(R.id.status_text);
        resultsText = view.findViewById(R.id.results_text);
        sendButton = view.findViewById(R.id.send_button);

        String[] labels = new String[ENDPOINTS.length];
        for (int i = 0; i < ENDPOINTS.length; i++) labels[i] = ENDPOINTS[i].label;
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dark, labels);
        adapter.setDropDownViewResource(R.layout.item_spinner_dark);
        endpointSpinner.setAdapter(adapter);

        // Prefill from the most recently executed request
        List<DeveloperHistory.ApiEntry> history = DeveloperHistory.getApiRequests(requireContext());
        if (!history.isEmpty()) {
            DeveloperHistory.ApiEntry latest = history.get(0);
            int index = endpointIndex(latest.endpoint);
            if (index >= 0) {
                pendingValues = latest.values;
                endpointSpinner.setSelection(index, false);
            }
        }

        endpointSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                buildForm(ENDPOINTS[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        buildForm(ENDPOINTS[endpointSpinner.getSelectedItemPosition()]);

        sendButton.setOnClickListener(v -> send());
        view.findViewById(R.id.history_button).setOnClickListener(v -> showHistory());
    }

    private int endpointIndex(String label) {
        for (int i = 0; i < ENDPOINTS.length; i++) {
            if (ENDPOINTS[i].label.equals(label)) return i;
        }
        return -1;
    }

    private void buildForm(Endpoint endpoint) {
        paramsContainer.removeAllViews();
        fields.clear();
        typeSpinner = null;

        for (Param param : endpoint.params) {
            EditText input = new EditText(requireContext());
            input.setBackgroundColor(0x1AFFFFFF);
            input.setTextColor(Color.WHITE);
            input.setHintTextColor(0x66FFFFFF);
            input.setTextSize(13);
            input.setHint(param.hint);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            int pad = dp(8);
            input.setPadding(pad, pad, pad, pad);

            // History refills set every field from the saved values, so only
            // apply defaults when building a fresh form
            if (pendingValues == null && param.defaultValue != null) {
                input.setText(param.defaultValue);
            }

            addRow(param.required ? param.key + " *" : param.key, input);
            fields.put(param.key, input);
        }

        if (endpoint.isSearch) {
            typeSpinner = new Spinner(requireContext());
            ArrayAdapter<String> typeAdapter =
                    new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dark, SEARCH_TYPES);
            typeAdapter.setDropDownViewResource(R.layout.item_spinner_dark);
            typeSpinner.setAdapter(typeAdapter);
            typeSpinner.setBackground(new ColorDrawable(0x1AFFFFFF));
            typeSpinner.setPopupBackgroundDrawable(new ColorDrawable(0xFF222222));
            addRow("type *", typeSpinner);
        }

        if (pendingValues != null) {
            for (Map.Entry<String, String> entry : pendingValues.entrySet()) {
                EditText input = fields.get(entry.getKey());
                if (input != null) input.setText(entry.getValue());
            }
            if (typeSpinner != null && pendingValues.containsKey("type")) {
                int index = Arrays.asList(SEARCH_TYPES).indexOf(pendingValues.get("type"));
                if (index >= 0) typeSpinner.setSelection(index);
            }
            pendingValues = null;
        }
    }

    private void addRow(String label, View input) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(6);
        row.setLayoutParams(rowParams);

        TextView labelView = new TextView(requireContext());
        labelView.setText(label);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(13);
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                dp(120), ViewGroup.LayoutParams.WRAP_CONTENT));

        input.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(labelView);
        row.addView(input);
        paramsContainer.addView(row);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void send() {
        Endpoint endpoint = ENDPOINTS[endpointSpinner.getSelectedItemPosition()];

        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, EditText> entry : fields.entrySet()) {
            String value = entry.getValue().getText().toString().trim();
            if (!value.isEmpty()) values.put(entry.getKey(), value);
        }
        if (typeSpinner != null) {
            values.put("type", (String) typeSpinner.getSelectedItem());
        }

        for (Param param : endpoint.params) {
            if (param.required && !values.containsKey(param.key)) {
                statusText.setText(param.key + " is required");
                return;
            }
        }
        if (endpoint.isSearch && buildSearchQuery(values).isEmpty()) {
            statusText.setText("Enter keywords or at least one filter");
            return;
        }

        String url = buildUrl(endpoint, values);

        String token = getAccessToken();
        if (token == null) {
            statusText.setText("No Spotify access token available");
            return;
        }

        DeveloperHistory.addApiRequest(requireContext(),
                new DeveloperHistory.ApiEntry(endpoint.label, url, values));

        sendButton.setEnabled(false);
        statusText.setText("Sending…");
        resultsText.setText("");

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .build();

        long startTime = System.currentTimeMillis();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postResult("Error: " + e.getMessage(), "");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                long elapsed = System.currentTimeMillis() - startTime;
                String body = response.body() != null ? response.body().string() : "";
                String status = "HTTP " + response.code() + " · " + elapsed + " ms";

                CharSequence display;
                if (body.isEmpty()) {
                    display = "(no content)";
                } else {
                    try {
                        display = JsonHighlighter.highlight(body);
                    } catch (Exception e) {
                        display = body;
                    }
                    if (display.length() > MAX_DISPLAY_LENGTH) {
                        display = display.subSequence(0, MAX_DISPLAY_LENGTH)
                                + "\n\n… output truncated (" + display.length()
                                + " characters total)";
                    }
                }
                postResult(status, display);
            }
        });
    }

    private void postResult(String status, CharSequence body) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            statusText.setText(status);
            resultsText.setText(body);
            sendButton.setEnabled(true);
        });
    }

    private String buildUrl(Endpoint endpoint, Map<String, String> values) {
        String path = endpoint.path;
        for (Param param : endpoint.params) {
            if (param.inPath && values.containsKey(param.key)) {
                path = path.replace("{" + param.key + "}", Uri.encode(values.get(param.key)));
            }
        }

        HttpUrl base = HttpUrl.parse(BASE_URL + path);
        if (base == null) throw new IllegalStateException("Invalid URL: " + path);
        HttpUrl.Builder builder = base.newBuilder();

        if (endpoint.isSearch) {
            builder.addQueryParameter("q", buildSearchQuery(values));
            builder.addQueryParameter("type", values.get("type"));
        } else {
            for (Param param : endpoint.params) {
                if (!param.inPath && values.containsKey(param.key)) {
                    builder.addQueryParameter(param.key, values.get(param.key));
                }
            }
        }
        return builder.build().toString();
    }

    private String buildSearchQuery(Map<String, String> values) {
        StringBuilder q = new StringBuilder();
        String keywords = values.get(FIELD_KEYWORDS);
        if (keywords != null) q.append(keywords);
        for (String filter : SEARCH_FILTERS) {
            String value = values.get(filter);
            if (value != null) {
                if (q.length() > 0) q.append(' ');
                q.append(filter).append(':').append(value);
            }
        }
        return q.toString();
    }

    private String getAccessToken() {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null && mainActivity.getSpotifyApi() != null) {
            String token = mainActivity.getSpotifyApi().getAccessToken();
            if (token != null) return token;
        }
        SharedPreferences prefs =
                requireContext().getSharedPreferences("SpotifyPrefs", Context.MODE_PRIVATE);
        return prefs.getString("spotify_access_token", null);
    }

    private void showHistory() {
        List<DeveloperHistory.ApiEntry> history = DeveloperHistory.getApiRequests(requireContext());
        if (history.isEmpty()) {
            Toast.makeText(requireContext(), "No request history yet", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> items = new ArrayList<>();
        for (DeveloperHistory.ApiEntry entry : history) {
            items.add(entry.endpoint + "\n" + entry.url);
        }

        DarkListDialog.show(requireContext(), "Request History", items, position -> {
            DeveloperHistory.ApiEntry entry = history.get(position);
            ClipboardManager clipboard =
                    (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("API request", entry.url));

            int index = endpointIndex(entry.endpoint);
            if (index < 0) return;
            pendingValues = entry.values;
            if (endpointSpinner.getSelectedItemPosition() == index) {
                buildForm(ENDPOINTS[index]);
            } else {
                endpointSpinner.setSelection(index);
            }
        });
    }
}
