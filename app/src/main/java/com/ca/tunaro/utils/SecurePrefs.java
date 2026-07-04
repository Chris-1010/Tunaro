package com.ca.tunaro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.util.Map;
import java.util.Set;

/**
 * AES-256 encrypted replacement for {@link Context#getSharedPreferences}, providing
 * data-at-rest protection for passwords, JWTs, API keys and OAuth tokens.
 *
 * <p>The encrypted store lives under a distinct file ("{name}_secure"). On first
 * access, any legacy plaintext file of the given name is copied in and then
 * deleted, so existing installs migrate transparently and no cleartext copy of a
 * secret is left behind.
 */
public final class SecurePrefs {

    private static final String TAG = "SecurePrefs";
    private static final String ENCRYPTED_SUFFIX = "_secure";

    private SecurePrefs() {}

    /**
     * Returns the encrypted store for {@code name}, migrating a legacy plaintext
     * file of the same name on first access. Call sites use this exactly like
     * {@code getSharedPreferences(name, MODE_PRIVATE)}.
     */
    public static SharedPreferences get(Context context, String name) {
        Context app = context.getApplicationContext();
        try {
            SharedPreferences encrypted = create(app, name);
            migrateIfNeeded(app, name, encrypted);
            return encrypted;
        } catch (Exception e) {
            // Keystore corruption (e.g. InvalidKeyException after a key reset or a
            // backup restore onto different hardware): drop the unreadable
            // encrypted file and retry once with a fresh key. Stored secrets are
            // lost, so the user re-authenticates — preferable to a crash loop.
            Log.w(TAG, "Encrypted prefs unavailable for " + name + ", resetting", e);
            app.deleteSharedPreferences(name + ENCRYPTED_SUFFIX);
            try {
                return create(app, name);
            } catch (Exception fatal) {
                throw new RuntimeException("Unable to open secure prefs: " + name, fatal);
            }
        }
    }

    private static SharedPreferences create(Context context, String name) throws Exception {
        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        return EncryptedSharedPreferences.create(
                name + ENCRYPTED_SUFFIX,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    @SuppressWarnings("unchecked")
    private static void migrateIfNeeded(Context context, String name, SharedPreferences encrypted) {
        SharedPreferences legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        Map<String, ?> all = legacy.getAll();
        if (all.isEmpty()) {
            return;
        }
        SharedPreferences.Editor editor = encrypted.edit();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Set) {
                editor.putStringSet(key, (Set<String>) value);
            }
        }
        editor.apply();
        // Purge the plaintext copy so the secret no longer sits in cleartext.
        legacy.edit().clear().apply();
        context.deleteSharedPreferences(name);
    }
}
