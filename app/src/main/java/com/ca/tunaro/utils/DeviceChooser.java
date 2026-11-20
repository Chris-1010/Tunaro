package com.ca.tunaro.utils;

import android.app.Activity;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.ca.tunaro.R;
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

    // Transferring playback occasionally fails on the first attempt; retry a couple of times before giving up.
    private static final int MAX_TRANSFER_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 600;

    public static void showDeviceChooser(Activity activity, MainActivity mainActivity) {
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            UiFeedback.show(activity, "Not connected to Spotify yet");
            return;
        }

        Log.d(TAG, "API: getUsersAvailableDevices");
        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi().getUsersAvailableDevices().build())
                .thenAccept(devices -> runIfAlive(activity, () -> showDeviceDialog(activity, mainActivity, devices)))
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to list devices", throwable);
                    runIfAlive(activity, () -> UiFeedback.show(activity, "Couldn't load devices",
                            R.string.retry, () -> showDeviceChooser(activity, mainActivity)));
                    return null;
                });
    }

    // Runs the action on the UI thread only if the Activity is still alive, avoiding
    // BadTokenException when an async callback returns after the screen is gone.
    private static void runIfAlive(Activity activity, Runnable action) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                action.run();
            }
        });
    }

    private static void showDeviceDialog(Activity activity, MainActivity mainActivity, Device[] devices) {
        if (devices == null || devices.length == 0) {
            UiFeedback.show(activity, "No devices found. Open Spotify on a device and try again.");
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
            UiFeedback.show(activity, "Already playing on " + device.getName());
            return;
        }

        String deviceId = device.getId();
        if (deviceId == null) {
            UiFeedback.show(activity, "Couldn't switch to that device");
            return;
        }

        JsonArray deviceIds = new JsonArray();
        deviceIds.add(deviceId);

        attemptTransfer(activity, mainActivity, device, deviceIds, 1);
    }

    private static void attemptTransfer(Activity activity, MainActivity mainActivity, Device device,
                                        JsonArray deviceIds, int attempt) {
        Log.d(TAG, "API: transferUsersPlayback -> " + device.getName() + " (attempt " + attempt + "/" + MAX_TRANSFER_ATTEMPTS + ")");
        mainActivity.executeWithTokenRefresh(
                () -> mainActivity.getSpotifyApi().transferUsersPlayback(deviceIds).play(true).build())
                .thenRun(() -> runIfAlive(activity, () ->
                        UiFeedback.show(activity, "Playing on " + device.getName())))
                .exceptionally(throwable -> {
                    Log.w(TAG, "Transfer attempt " + attempt + " failed: " + throwable.getMessage());
                    if (attempt < MAX_TRANSFER_ATTEMPTS) {
                        runIfAlive(activity, () -> new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> attemptTransfer(activity, mainActivity, device, deviceIds, attempt + 1),
                                        RETRY_DELAY_MS));
                    } else {
                        Log.e(TAG, "Transfer failed", throwable);
                        runIfAlive(activity, () -> UiFeedback.show(activity,
                                "Couldn't switch to " + device.getName(), R.string.retry,
                                () -> attemptTransfer(activity, mainActivity, device, deviceIds, 1)));
                    }
                    return null;
                });
    }
}
