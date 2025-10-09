package org.misqzy.playerGrowth.growth;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.misqzy.playerGrowth.config.ConfigManager;

import java.util.Objects;

public class GrowthManager {
    private ConfigManager config;
    private final Plugin plugin;
    private final GrowthUpdater growthUpdater;

    private double minScale;
    private double maxScale;
    private int growTimeMinutes;


    public GrowthManager(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        growthUpdater = new GrowthUpdater(this.plugin, this, this.config);
        updateParameters();
    }

    private void updateParameters() {
        this.minScale = config.getMinScale();
        this.maxScale = config.getMaxScale();
        this.growTimeMinutes = config.getGrowTimeMinutes();

        if (minScale < 0.01) {
            minScale = 0.01;
        }

        if (minScale > maxScale) {
            minScale = maxScale;
        }

        if (maxScale < minScale) {
            maxScale = minScale;
        }

        if(config.getIsAutoGrowth()) {
            growthUpdater.startAutoGrowthTimer();
        }
        else {
            growthUpdater.stopAutoGrowthTimer();
        }
    }

    public Double getPlayerHeight(Player player)
    {
        return Objects.requireNonNull(player.getAttribute(Attribute.SCALE)).getBaseValue();
    }

    public void updateConfig(ConfigManager newConfig) {
        this.config = newConfig;
        updateParameters();
    }

    public void updatePlayerScale(Player player) {
        AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
        if (scaleAttribute == null) {
            return;
        }

        double growTimeSeconds = calculateGrowthTimeSeconds(player);
        if (growTimeSeconds <= 0) {
            scaleAttribute.setBaseValue(maxScale);
            return;
        }

        double progress = calculateProgress(calculatePlayedSeconds(player), growTimeSeconds);
        double newScale = minScale + (maxScale - minScale) * progress;

        scaleAttribute.setBaseValue(newScale);
    }

    public void updateAllPlayersScale()
    {
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            updatePlayerScale(player);
        }
    }

    private double calculateProgress(double playedSeconds, double growTimeSeconds) {
        if (playedSeconds <= 0) {
            return 0.0;
        }
        return Math.min(1.0, playedSeconds / growTimeSeconds);
    }

    public long calculateGrowthTimeSeconds(Player player)
    {
        return (long) (growTimeMinutes * 60.0);
    }

    public long calculatePlayedSeconds(Player player)
    {
        long playedTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return (long) (playedTicks / 20.0);
    }

    public long calculateUntilGrowthEndTimeSeconds(Player player)
    {
        long secondsToFullGrowth = calculateGrowthTimeSeconds(player);
        long playedSeconds = calculatePlayedSeconds(player);
        return secondsToFullGrowth - playedSeconds;
    }
}
