package org.misqzy.playerGrowth.growth;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.misqzy.playerGrowth.config.ConfigManager;
import org.misqzy.playerGrowth.storage.Storage;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class GrowthManager {
    private ConfigManager config;
    private final Plugin plugin;
    private final GrowthUpdater growthUpdater;
    private Storage storage;
    
    private final ConcurrentHashMap<UUID, Double> customScalesCache = new ConcurrentHashMap<>();

    private double minScale;
    private double maxScale;
    private int growTimeMinutes;


    public GrowthManager(Plugin plugin, ConfigManager config, Storage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
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
    
    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public void updatePlayerScale(Player player) {
        AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
        if (scaleAttribute == null) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        
        Double customScale = customScalesCache.get(playerUuid);
        if (customScale != null) {
            scaleAttribute.setBaseValue(customScale);
            return;
        }
        
        storage.hasCustomScale(playerUuid).thenAccept(hasCustom -> {
            if (hasCustom) {
                storage.getCustomScale(playerUuid).thenAccept(scale -> {
                    if (scale != null) {
                        customScalesCache.put(playerUuid, scale);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline()) {
                                    AttributeInstance attr = player.getAttribute(Attribute.SCALE);
                                    if (attr != null) {
                                        attr.setBaseValue(scale);
                                    }
                                }
                            }
                        }.runTask(plugin);
                    }
                });
            } else {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            double growTimeSeconds = calculateGrowthTimeSeconds(player);
                            if (growTimeSeconds <= 0) {
                                scaleAttribute.setBaseValue(maxScale);
                                return;
                            }

                            double progress = calculateProgress(calculatePlayedSeconds(player), growTimeSeconds);
                            double newScale = minScale + (maxScale - minScale) * progress;

                            scaleAttribute.setBaseValue(newScale);
                        }
                    }
                }.runTask(plugin);
            }
        });
    }
    
    public void loadPlayerCustomScale(Player player) {
        UUID playerUuid = player.getUniqueId();
        storage.getCustomScale(playerUuid).thenAccept(scale -> {
            if (scale != null) {
                customScalesCache.put(playerUuid, scale);
            }
        });
    }
    
    public void unloadPlayerCustomScale(Player player) {
        customScalesCache.remove(player.getUniqueId());
    }
    
    public CompletableFuture<Boolean> setCustomScale(Player player, double scale) {
        if (scale < minScale || scale > maxScale) {
            return CompletableFuture.completedFuture(false);
        }
        
        UUID playerUuid = player.getUniqueId();
        return storage.setCustomScale(playerUuid, scale).thenApply(success -> {
            if (success) {
                customScalesCache.put(playerUuid, scale);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
                            if (scaleAttribute != null) {
                                scaleAttribute.setBaseValue(scale);
                            }
                        }
                    }
                }.runTask(plugin);
            }
            return success;
        });
    }
    
    public CompletableFuture<Boolean> removeCustomScale(Player player) {
        UUID playerUuid = player.getUniqueId();
        return storage.removeCustomScale(playerUuid).thenApply(success -> {
            if (success) {
                customScalesCache.remove(playerUuid);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            updatePlayerScale(player);
                        }
                    }
                }.runTask(plugin);
            }
            return success;
        });
    }
    
    public double getMinScale() {
        return minScale;
    }
    
    public double getMaxScale() {
        return maxScale;
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
    
    public boolean isPlayerMaxGrowth(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        Double customScale = customScalesCache.get(playerUuid);
        if (customScale != null) {
            return false;
        }
        
        double currentScale = Objects.requireNonNull(player.getAttribute(Attribute.SCALE)).getBaseValue();
        double growTimeSeconds = calculateGrowthTimeSeconds(player);
        
        if (growTimeSeconds <= 0) {
            return currentScale >= maxScale;
        }
        
        double playedSeconds = calculatePlayedSeconds(player);
        double progress = calculateProgress(playedSeconds, growTimeSeconds);
        
        return currentScale >= maxScale && progress >= 1.0;
    }
}
