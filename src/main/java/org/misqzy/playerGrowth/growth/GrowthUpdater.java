package org.misqzy.playerGrowth.growth;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.misqzy.playerGrowth.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

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

        stopAutoGrowthTimer();

        taskId = growthUpdateTimer.runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                    if(!isGrowthBlocked(player) && !growthManager.isPlayerMaxGrowth(player))
                    {
                        growthManager.updatePlayerScale(player);
                    }
                }
            }
        }, 0L,
                (long) configManager.getGrowthUpdateTime() * 20L).getTaskId();
    }

    private boolean isAutoGrowthTimerEnable()
    {
        return taskId != -1;
    }

    public void stopAutoGrowthTimer() {
        if (isAutoGrowthTimerEnable()) {
            growthUpdateTimer.cancelTask(taskId);
            taskId = -1;
        }
    }

    public boolean isGrowthBlocked(Player player) {
        Location loc = player.getLocation();
        int baseX = loc.getBlockX();
        int baseY = loc.getBlockY();
        int baseZ = loc.getBlockZ();

        Block blockAbove = player.getWorld().getBlockAt(baseX, baseY + 1, baseZ);
        if (isBlockingBlock(blockAbove)) {
            return true;
        }

        List<Block> blocksToCheck = new ArrayList<>();
        for (int y = baseY; y <= baseY + 1; y++) {
            for (int x = baseX - 1; x <= baseX + 1; x++) {
                for (int z = baseZ - 1; z <= baseZ + 1; z++) {
                    if (x == baseX && y == baseY && z == baseZ) {
                        continue;
                    }
                    blocksToCheck.add(player.getWorld().getBlockAt(x, y, z));
                }
            }
        }

        for (Block block : blocksToCheck) {
            if (isBlockingBlock(block)) {
                return true;
            }
        }

        return false;
    }

    public boolean isBlockingBlock(Block block) {
        Material mat = block.getType();

        return !mat.isAir() && mat.isSolid();
    }
}
