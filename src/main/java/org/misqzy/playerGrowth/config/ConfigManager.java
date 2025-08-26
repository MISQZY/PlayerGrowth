package org.misqzy.playerGrowth.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final double minScale;
    private final double maxScale;
    private final int growTimeMinutes;
    private final String locale;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        this.minScale = config.getDouble("scale.min", 0.5);
        this.maxScale = config.getDouble("scale.max", 1.0);
        this.growTimeMinutes = config.getInt("scale.time-to-grow", 120);
        this.locale = config.getString("locale", "en");
    }

    public double getMinScale() {
        return minScale;
    }

    public double getMaxScale() {
        return maxScale;
    }

    public int getGrowTimeMinutes() {
        return growTimeMinutes;
    }

    public String getLanguage() {
        return locale;
    }
}
