package org.misqzy.playerGrowth.growth;

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
        long playedTicks  = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        double playedSeconds = playedTicks / 20.0;

        double progress = Math.min(1.0, (double) playedSeconds / (growTimeMinutes * 60));

        double newScale = minScale + (maxScale - minScale) * progress;

        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(newScale);
        }
    }
}
