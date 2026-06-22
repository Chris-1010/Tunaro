package com.ca.tunaro.utils;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ca.tunaro.activites.MainActivity;
import com.google.gson.JsonArray;

import se.michaelthelin.spotify.model_objects.miscellaneous.Device;

/**
 * Lets the user pick which Spotify device playback should run on. Devices come from the
 * Web API's "available devices" endpoint; selecting one transfers playback there via the
 * Web API's transfer-playback endpoint.
 */
public class DeviceChooser {
    private static final String TAG = "DeviceChooser";

    public static void showDeviceChooser(Activity activity, MainActivity mainActivity) {
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            Toast.makeText(activity, "Spotify API not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "API: getUsersAvailableDevices");
        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi().getUsersAvailableDevices().build())
                .thenAccept(devices -> activity.runOnUiThread(() -> showDeviceDialog(activity, mainActivity, devices)))
                .exceptionally(throwable -> {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Error getting devices: " + throwable.getMessage(), Toast.LENGTH_SHORT).show());
                    return null;
                });
    }

    private static void showDeviceDialog(Activity activity, MainActivity mainActivity, Device[] devices) {
        if (devices == null || devices.length == 0) {
            Toast.makeText(activity, "No available devices found. Open Spotify on a device and try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] deviceLabels = new String[devices.length];
        int activeIndex = -1;
        for (int i = 0; i < devices.length; i++) {
            deviceLabels[i] = devices[i].getName();
            if (devices[i].getIs_active()) {
                deviceLabels[i] += " (Active)";
                activeIndex = i;
            }
        }

        new AlertDialog.Builder(activity)
                .setTitle("Choose Playback Device")
                .setSingleChoiceItems(deviceLabels, activeIndex, (dialog, which) -> {
                    transferPlayback(activity, mainActivity, devices[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void transferPlayback(Activity activity, MainActivity mainActivity, Device device) {
        if (device.getIs_active()) {
            Toast.makeText(activity, "Already playing on " + device.getName(), Toast.LENGTH_SHORT).show();
            return;
        }

        JsonArray deviceIds = new JsonArray();
        deviceIds.add(device.getId());

        Log.d(TAG, "API: transferUsersPlayback -> " + device.getName());
        mainActivity.executeWithTokenRefresh(
                () -> mainActivity.getSpotifyApi().transferUsersPlayback(deviceIds).build())
                .thenRun(() -> activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Switched playback to " + device.getName(), Toast.LENGTH_SHORT).show()))
                .exceptionally(throwable -> {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Couldn't switch device: " + throwable.getMessage(), Toast.LENGTH_SHORT).show());
                    return null;
                });
    }
}
