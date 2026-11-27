package com.ca.tunaro.utils;

import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Protects a long-running job on its screen: keeps the display awake while the job runs and asks
 * for confirmation before Back/Up leaves the screen, so the progress UI is not abandoned silently.
 * Call {@link #start()} and {@link #stop()} on the main thread.
 */
public final class RunningJobGuard {
    private final AppCompatActivity activity;
    private final String title;
    private final String message;
    private final OnBackPressedCallback backCallback;

    public RunningJobGuard(AppCompatActivity activity, String title, String message) {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                confirmLeave();
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, backCallback);
    }

    public void start() {
        backCallback.setEnabled(true);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void stop() {
        backCallback.setEnabled(false);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public boolean isRunning() {
        return backCallback.isEnabled();
    }

    /** Routes an Up press through the same confirmation as Back. */
    public void navigateUp() {
        activity.getOnBackPressedDispatcher().onBackPressed();
    }

    private void confirmLeave() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Keep waiting", null)
                .setNegativeButton("Leave", (dialog, which) -> {
                    stop();
                    activity.finish();
                })
                .show();
    }
}
