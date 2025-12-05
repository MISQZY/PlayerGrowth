package org.misqzy.playerGrowth.storage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class YAMLStorage implements Storage {
    
    private final Plugin plugin;
    private File dataFile;
    private FileConfiguration dataConfig;
    
    public YAMLStorage(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean initialize() {
        try {
            File dataFolder = new File(plugin.getDataFolder(), "data");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            dataFile = new File(dataFolder, "player_scales.yml");
            if (!dataFile.exists()) {
                dataFile.createNewFile();
            }
            
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to initialize YAML storage: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean testConnection() {
        return dataFile != null && dataFile.exists() && dataFile.canWrite();
    }
    
    @Override
    public void close() {
    }
    
    @Override
    public CompletableFuture<Double> getCustomScale(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "scales." + playerUuid.toString();
            if (dataConfig.contains(path)) {
                return dataConfig.getDouble(path);
            }
            return null;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> setCustomScale(UUID playerUuid, double scale) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String path = "scales." + playerUuid.toString();
                dataConfig.set(path, scale);
                dataConfig.save(dataFile);
                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save custom scale: " + e.getMessage());
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> removeCustomScale(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String path = "scales." + playerUuid.toString();
                if (dataConfig.contains(path)) {
                    dataConfig.set(path, null);
                    dataConfig.save(dataFile);
                    return true;
                }
                return false;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to remove custom scale: " + e.getMessage());
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> hasCustomScale(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "scales." + playerUuid.toString();
            return dataConfig.contains(path);
        });
    }
}

