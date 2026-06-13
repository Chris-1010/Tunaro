package com.ca.tunaro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Persists the last executed SQL queries and Spotify API requests for the
 * Developer screen. Newest entries first, capped at {@link #MAX_ENTRIES}.
 */
public class DeveloperHistory {

    private static final String PREFS = "DeveloperHistory";
    private static final String KEY_SQL = "sql_history";
    private static final String KEY_API = "api_history";
    private static final int MAX_ENTRIES = 20;

    private static final Gson gson = new Gson();

    public static class ApiEntry {
        public String endpoint;
        public String url;
        public LinkedHashMap<String, String> values;

        public ApiEntry(String endpoint, String url, LinkedHashMap<String, String> values) {
            this.endpoint = endpoint;
            this.url = url;
            this.values = values;
        }

        public boolean sameRequest(ApiEntry other) {
            return Objects.equals(endpoint, other.endpoint) && Objects.equals(values, other.values);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void addSqlQuery(Context context, String query) {
        List<String> history = getSqlQueries(context);
        history.remove(query);
        history.add(0, query);
        trim(history);
        prefs(context).edit().putString(KEY_SQL, gson.toJson(history)).apply();
    }

    public static List<String> getSqlQueries(Context context) {
        String json = prefs(context).getString(KEY_SQL, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public static void addApiRequest(Context context, ApiEntry entry) {
        List<ApiEntry> history = getApiRequests(context);
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).sameRequest(entry)) history.remove(i);
        }
        history.add(0, entry);
        trim(history);
        prefs(context).edit().putString(KEY_API, gson.toJson(history)).apply();
    }

    public static List<ApiEntry> getApiRequests(Context context) {
        String json = prefs(context).getString(KEY_API, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<ApiEntry>>() {}.getType();
        return gson.fromJson(json, type);
    }

    private static void trim(List<?> history) {
        while (history.size() > MAX_ENTRIES) history.remove(history.size() - 1);
    }
}
