package com.ca.tunaro.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;

public class ScreenTransitionsTest {

    private static final String APP = "com.ca.tunaro";

    @Test
    public void inAppNavigationSlides() {
        assertTrue(ScreenTransitions.isInAppForward(APP, APP, 0));
        assertTrue(ScreenTransitions.isInAppForward(APP, APP, Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Test
    public void otherAppsAndImplicitIntentsKeepSystemAnimation() {
        assertFalse(ScreenTransitions.isInAppForward(APP, "com.spotify.music", 0));
        assertFalse(ScreenTransitions.isInAppForward(APP, null, 0));
    }

    @Test
    public void taskResetsKeepSystemAnimation() {
        assertFalse(ScreenTransitions.isInAppForward(APP, APP, Intent.FLAG_ACTIVITY_NEW_TASK));
        assertFalse(ScreenTransitions.isInAppForward(APP, APP,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
}
