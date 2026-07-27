package org.misqzy.playergrowth.common.platform;

/**
 * Platform-agnostic scheduler contract. Paper implements this with
 * Folia-safe global/region schedulers; a proxy module implements it with
 * its own event-loop executor.
 */
public interface Scheduler {

    void runAsync(Runnable task);

    /** Runs on the platform's "main"/primary thread (e.g. Bukkit main thread). */
    void runSync(Runnable task);

    ScheduledTask runSyncTimer(Runnable task, long delayMillis, long periodMillis);
}
