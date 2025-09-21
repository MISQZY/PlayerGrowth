package org.misqzy.playerGrowth.growth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.misqzy.playerGrowth.config.ConfigManager;

public class GrowthUpdater {
    private final Plugin plugin;
    private final GrowthManager growthManager;
    private final ConfigManager configManager;

    private int taskId = -1;

    public GrowthUpdater(Plugin plugin, GrowthManager growthManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.growthManager = growthManager;
        this.configManager = configManager;
    }

    BukkitScheduler growthUpdateTimer = Bukkit.getServer().getScheduler();

    public void startAutoGrowthTimer()
    {
        if (taskId != -1) {
            growthUpdateTimer.cancelTask(taskId);
        }

        taskId = growthUpdateTimer.runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                    growthManager.updatePlayerScale(player);
                }
            }
        }, 0L,
                (long) configManager.getGrowthUpdateTime() * 20L).getTaskId();
    }

    public void stopAutoGrowthTimer() {
        if (taskId != -1) {
            growthUpdateTimer.cancelTask(taskId);
            taskId = -1;
        }
    }
}
