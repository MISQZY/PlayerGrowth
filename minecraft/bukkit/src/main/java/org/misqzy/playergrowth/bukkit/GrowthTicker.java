package org.misqzy.playergrowth.bukkit;

import org.bukkit.Bukkit;
import org.misqzy.playergrowth.common.platform.PlatformPlayer;
import org.misqzy.playergrowth.common.platform.ScheduledTask;
import org.misqzy.playergrowth.common.platform.Scheduler;
import org.misqzy.playergrowth.common.service.GrowthEngine;

import java.util.List;

/**
 * Owns the repeating growth tick as its own object (rather than folding it
 * into {@link PlayerGrowthPlugin}) so it can be stopped and restarted on
 * {@code /playergrowth reload} without re-running the rest of plugin bootstrap.
 */
public final class GrowthTicker {

    private final Scheduler scheduler;
    private final GrowthEngine growthEngine;
    private ScheduledTask task;

    public GrowthTicker(Scheduler scheduler, GrowthEngine growthEngine) {
        this.scheduler = scheduler;
        this.growthEngine = growthEngine;
    }

    public void start() {
        if (task != null || !growthEngine.autoGrowthEnabled()) return;
        long interval = growthEngine.tickIntervalMillis();
        task = scheduler.runSyncTimer(this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    private void tick() {
        List<PlatformPlayer> online = Bukkit.getOnlinePlayers().stream()
                .<PlatformPlayer>map(BukkitPlayerAdapter::new)
                .toList();
        growthEngine.tick(online);
    }
}
