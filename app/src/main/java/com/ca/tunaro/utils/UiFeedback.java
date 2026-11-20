package com.ca.tunaro.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.ca.tunaro.R;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;

/**
 * Single entry point for short user-facing messages.
 *
 * <p>Messages show as a Material {@link Snackbar} anchored above the persistent playback bar
 * (when it is on screen). A Toast is used only when no live Activity is available, e.g. when the
 * caller is about to finish its Activity or the message comes from a background component while
 * the app is not in the foreground. Safe to call from any thread.</p>
 */
public final class UiFeedback {
    private static final String TAG = "UiFeedback";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> foreground = new WeakReference<>(null);

    private UiFeedback() {}

    /** Record the resumed Activity so background components can reach it. */
    public static void setForegroundActivity(@NonNull Activity activity) {
        foreground = new WeakReference<>(activity);
    }

    /** Forget the Activity if it is the one currently recorded. */
    public static void clearForegroundActivity(@NonNull Activity activity) {
        if (foreground.get() == activity) {
            foreground = new WeakReference<>(null);
        }
    }

    public static void show(@Nullable Context context, @NonNull CharSequence message) {
        show(context, message, 0, null);
    }

    public static void show(@Nullable Context context, @StringRes int message) {
        if (context == null) return;
        show(context, context.getString(message), 0, null);
    }

    /**
     * Show a message, optionally with an action button (e.g. Retry). When the context is not an
     * Activity, the current foreground Activity is used instead; failing that, a Toast is shown
     * and the action is dropped.
     */
    public static void show(@Nullable Context context, @NonNull CharSequence message,
                            @StringRes int actionLabel, @Nullable Runnable action) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> show(context, message, actionLabel, action));
            return;
        }
        Log.v(TAG, "feedback: " + message);

        Activity activity = findActivity(context);
        if (!isUsable(activity)) activity = foreground.get();

        if (isUsable(activity)) {
            View root = activity.findViewById(android.R.id.content);
            if (root != null) {
                showSnackbar(activity, root, message, actionLabel, action);
                return;
            }
        }

        Context toastContext = activity != null ? activity : context;
        if (toastContext != null) {
            Toast.makeText(toastContext.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private static void showSnackbar(Activity activity, View root, CharSequence message,
                                     @StringRes int actionLabel, @Nullable Runnable action) {
        boolean hasAction = actionLabel != 0 && action != null;
        Snackbar snackbar = Snackbar.make(root, message,
                hasAction ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT);

        // Anchor above the playback bar only while it is laid out on screen; a GONE anchor would
        // pin the Snackbar to the top of the window.
        View bar = activity.findViewById(R.id.playback_bar);
        if (bar != null && bar.isShown()) {
            snackbar.setAnchorView(bar);
        }

        if (hasAction) {
            snackbar.setAction(actionLabel, v -> action.run());
            snackbar.setActionTextColor(ContextCompat.getColor(activity, R.color.accent));
        }
        snackbar.show();
    }

    /** True when the Activity is on screen and not on its way out. */
    private static boolean isUsable(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        if (activity instanceof LifecycleOwner) {
            return ((LifecycleOwner) activity).getLifecycle().getCurrentState()
                    .isAtLeast(Lifecycle.State.STARTED);
        }
        return true;
    }

    @Nullable
    private static Activity findActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
