package com.ca.tunaro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.ca.tunaro.activites.MainActivity;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;

public class DeviceChecker {
    private static final String TAG = "DeviceChecker";
    private static final String PREFS_NAME = "TunaroPrefs";
    private static final String KEY_DEVICE_CHECK_ENABLED = "device_check_enabled";
    private static final String KEY_DEVICE_NAME = "device_name";

    public interface DeviceCheckCallback {
        void onDeviceCheckResult(boolean isCorrectDevice, String message);
        default void onDeviceWarningStateChanged(boolean showWarning) {}
    }

    public static void checkPlaybackDevice(Context context, SpotifyApi spotifyApi, DeviceCheckCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (!prefs.getBoolean(KEY_DEVICE_CHECK_ENABLED, false)) {
            // Device check is disabled, always return true
            callback.onDeviceCheckResult(true, "Device check disabled");
            callback.onDeviceWarningStateChanged(false);
            return;
        }

        String expectedDeviceName = prefs.getString(KEY_DEVICE_NAME, "").trim();
        if (expectedDeviceName.isEmpty()) {
            callback.onDeviceCheckResult(false, "No device name configured");
            callback.onDeviceWarningStateChanged(false);
            return;
        }

        if (spotifyApi == null) {
            callback.onDeviceCheckResult(false, "Spotify API not available");
            callback.onDeviceWarningStateChanged(false);
            return;
        }

        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            mainActivity.refreshAccessToken()
                    .exceptionally(e -> {
                        Log.w(TAG, "Token refresh failed before device check, proceeding with existing token");
                        return null;
                    })
                    .thenRun(() -> doDeviceCheck(spotifyApi, expectedDeviceName, callback));
        } else {
            doDeviceCheck(spotifyApi, expectedDeviceName, callback);
        }
    }

    private static void doDeviceCheck(SpotifyApi spotifyApi, String expectedDeviceName, DeviceCheckCallback callback) {
        Log.d(TAG, "API: getUsersAvailableDevices");
        spotifyApi.getUsersAvailableDevices()
                .build()
                .executeAsync()
                .thenAccept(devices -> {
                    Device activeDevice = null;
                    for (Device device : devices) {
                        if (device.getIs_active()) {
                            activeDevice = device;
                            break;
                        }
                    }

                    if (activeDevice == null) {
                        // Use Handler to post to main thread
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            callback.onDeviceCheckResult(true, "No active device found");
                            callback.onDeviceWarningStateChanged(false);
                        });
                        return;
                    }

                    boolean isCorrectDevice = activeDevice.getName().equalsIgnoreCase(expectedDeviceName);
                    String message = isCorrectDevice
                            ? "Playing on correct device: " + activeDevice.getName()
                            : "Playing on " + activeDevice.getName();

                    Log.d(TAG, message);

                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onDeviceCheckResult(isCorrectDevice, message);
                        callback.onDeviceWarningStateChanged(!isCorrectDevice);
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error checking devices: " + throwable.getMessage());
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onDeviceCheckResult(false, "Error checking devices: " + throwable.getMessage());
                        callback.onDeviceWarningStateChanged(false);
                    });
                    return null;
                });
    }

    public static boolean isDeviceCheckEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DEVICE_CHECK_ENABLED, false);
    }
}