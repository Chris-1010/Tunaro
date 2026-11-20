package com.ca.tunaro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide executors for background I/O. Blocking network and disk work runs here instead of
 * the common ForkJoinPool, so it cannot starve the pool that CompletableFuture chains and UI-facing
 * loads depend on.
 */
public final class AppExecutors {
    private static final int IO_THREADS = 3;
    private static final String PREFS_NAME = "TunaroPrefs";

    private static final ExecutorService IO = createIoExecutor();
    private static final ScheduledExecutorService SCHEDULER = createScheduler();

    private AppExecutors() {
    }

    /** Bounded pool for blocking network / database work. */
    public static ExecutorService io() {
        return IO;
    }

    /** Runs {@code task} on the I/O pool after {@code delayMs}. */
    public static void ioDelayed(Runnable task, long delayMs) {
        SCHEDULER.schedule(() -> IO.execute(task), delayMs, TimeUnit.MILLISECONDS);
    }

    /** True when a job last run at {@code lastRunMs} is due again at {@code nowMs}. */
    public static boolean isDue(long lastRunMs, long nowMs, long intervalMs) {
        if (lastRunMs <= 0 || lastRunMs > nowMs) return true; // never run, or clock moved back
        return nowMs - lastRunMs >= intervalMs;
    }

    /** Whether the throttled job stored under {@code key} is due, per {@link #isDue}. */
    public static boolean isJobDue(Context context, String key, long intervalMs) {
        long last = prefs(context).getLong(key, 0L);
        return isDue(last, System.currentTimeMillis(), intervalMs);
    }

    /** Records that the throttled job stored under {@code key} has just completed. */
    public static void markJobRun(Context context, String key) {
        prefs(context).edit().putLong(key, System.currentTimeMillis()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static ExecutorService createIoExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                IO_THREADS, IO_THREADS, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), namedFactory("tunaro-io"));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static ScheduledExecutorService createScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, namedFactory("tunaro-sched"));
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger count = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + count.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
    }
}
