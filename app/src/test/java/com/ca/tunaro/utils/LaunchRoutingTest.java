package com.ca.tunaro.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LaunchRoutingTest {

    @Test
    public void launcherOnTopOfLiveTaskResumesInPlace() {
        assertTrue(LaunchRouting.shouldResumeInPlace(false, true, false, false, true));
    }

    @Test
    public void notificationOnTopOfLiveTaskResumesInPlace() {
        assertTrue(LaunchRouting.shouldResumeInPlace(false, false, true, false, true));
    }

    @Test
    public void taskRootRunsSplash() {
        // Fresh task (e.g. process killed): normal splash path keeps recovery working.
        assertFalse(LaunchRouting.shouldResumeInPlace(true, true, true, false, true));
    }

    @Test
    public void noLiveSessionRunsSplash() {
        assertFalse(LaunchRouting.shouldResumeInPlace(false, true, true, false, false));
    }

    @Test
    public void forcedReauthAlwaysRunsSplash() {
        assertFalse(LaunchRouting.shouldResumeInPlace(false, true, true, true, true));
    }

    @Test
    public void unrelatedInternalLaunchRunsSplash() {
        assertFalse(LaunchRouting.shouldResumeInPlace(false, false, false, false, true));
    }
}
