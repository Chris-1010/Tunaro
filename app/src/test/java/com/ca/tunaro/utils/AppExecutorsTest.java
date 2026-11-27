package com.ca.tunaro.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AppExecutorsTest {
    private static final long HOUR = 60L * 60L * 1000L;

    @Test
    public void neverRunJobIsDue() {
        assertTrue(AppExecutors.isDue(0L, 10 * HOUR, 6 * HOUR));
    }

    @Test
    public void recentJobIsNotDue() {
        assertFalse(AppExecutors.isDue(10 * HOUR, 12 * HOUR, 6 * HOUR));
    }

    @Test
    public void jobIsDueOnceIntervalElapses() {
        assertTrue(AppExecutors.isDue(10 * HOUR, 16 * HOUR, 6 * HOUR));
    }

    @Test
    public void clockMovingBackwardsMakesJobDue() {
        assertTrue(AppExecutors.isDue(20 * HOUR, 10 * HOUR, 6 * HOUR));
    }

    @Test
    public void delayedTaskRunsOnIoThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AppExecutors.ioDelayed(() -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        }, 10);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("tunaro-io-"));
    }
}
