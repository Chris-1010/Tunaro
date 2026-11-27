package com.ca.tunaro.utils;

/**
 * Pure decision logic for whether a fresh MainActivity instance should step aside
 * so an existing task resumes in place (#120).
 *
 * Android can stack a new MainActivity on top of a live task when the launcher or
 * the media notification fires an intent that does not match the task's root
 * intent. Re-running the splash/router there would discard the user's screen, so
 * the new instance finishes immediately and the existing task surfaces as-is.
 */
public final class LaunchRouting {

    /** Extra set on the media notification's content intent. */
    public static final String EXTRA_FROM_NOTIFICATION = "com.ca.tunaro.extra.FROM_NOTIFICATION";

    private LaunchRouting() {
    }

    /**
     * @param isTaskRoot       whether the new MainActivity is the root of its task
     * @param isLauncherIntent intent is ACTION_MAIN with CATEGORY_LAUNCHER
     * @param fromNotification intent carries {@link #EXTRA_FROM_NOTIFICATION}
     * @param forceReauth      intent explicitly asks for the consent flow
     * @param liveSession      another MainActivity holds a restored Spotify session
     * @return true when the new instance should finish without running the splash
     */
    public static boolean shouldResumeInPlace(boolean isTaskRoot,
                                              boolean isLauncherIntent,
                                              boolean fromNotification,
                                              boolean forceReauth,
                                              boolean liveSession) {
        if (isTaskRoot || forceReauth || !liveSession) return false;
        return isLauncherIntent || fromNotification;
    }
}
