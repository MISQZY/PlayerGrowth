package org.misqzy.playergrowth.paper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.misqzy.playergrowth.common.platform.ScheduledTask;
import org.misqzy.playergrowth.common.platform.Scheduler;

/**
 * Bukkit-scheduler-backed {@link Scheduler}.
 *
 * <p><b>Folia note:</b> this uses the classic {@code BukkitScheduler}, which
 * Folia does not support for entity-affecting work. Running on Folia would
 * require per-entity region-scheduler calls in {@link PaperPlayerAdapter}
 * instead of a single global timer; that rewrite is out of scope here and
 * is called out explicitly in the README rather than silently pretended to
 * work.</p>
 */
public final class PaperScheduler implements Scheduler {

    private final JavaPlugin plugin;

    public PaperScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public ScheduledTask runSyncTimer(Runnable task, long delayMillis, long periodMillis) {
        long delayTicks = Math.max(0L, delayMillis / 50L);
        long periodTicks = Math.max(1L, periodMillis / 50L);
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }
}
