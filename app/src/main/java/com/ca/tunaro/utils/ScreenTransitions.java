package com.ca.tunaro.utils;

import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * Decides when the shared forward/back screen transition applies. Kept free of
 * Activity state so the rule can be unit-tested on the JVM.
 */
public final class ScreenTransitions {

    private static final int NEW_TASK_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;

    private ScreenTransitions() {}

    /**
     * The forward slide is for in-app navigation only: other apps (Spotify, the
     * browser) and task resets (re-auth, recovery) keep the system animation.
     *
     * @param ownPackage    this app's package name
     * @param targetPackage package of the explicit target component, or null when implicit
     * @param flags         the launching intent's flags
     */
    public static boolean isInAppForward(String ownPackage, @Nullable String targetPackage, int flags) {
        if (targetPackage == null || !targetPackage.equals(ownPackage)) return false;
        return (flags & NEW_TASK_FLAGS) == 0;
    }
}
