package org.misqzy.playerGrowth.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final double minScale;
    private final double maxScale;
    private final int growTimeMinutes;
    private final String locale;
    private final double growthUpdateTime;
    private final boolean isAutoGrowth;
    
    private final String storageType;
    private final String databaseHost;
    private final int databasePort;
    private final String databaseName;
    private final String databaseUsername;
    private final String databasePassword;
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final long maxLifetime;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        this.minScale = config.getDouble("scale.min", 0.5);
        this.maxScale = config.getDouble("scale.max", 1.0);
        this.growTimeMinutes = config.getInt("scale.time-to-grow", 120);
        this.locale = config.getString("locale", "en");
        this.isAutoGrowth = config.getBoolean("growth.auto-growth", false);
        this.growthUpdateTime = config.getDouble("growth.growth-update-time", 5);
        
        this.storageType = config.getString("storage", "H2");
        this.databaseHost = config.getString("database.host", "localhost");
        this.databasePort = config.getInt("database.port", 3306);
        this.databaseName = config.getString("database.database", "playergrowth");
        this.databaseUsername = config.getString("database.username", "root");
        this.databasePassword = config.getString("database.password", "");
        this.maxPoolSize = config.getInt("database.pool.maximum-pool-size", 10);
        this.minIdle = config.getInt("database.pool.minimum-idle", 2);
        this.connectionTimeout = config.getLong("database.pool.connection-timeout", 30000);
        this.idleTimeout = config.getLong("database.pool.idle-timeout", 600000);
        this.maxLifetime = config.getLong("database.pool.max-lifetime", 1800000);
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

    public boolean getIsAutoGrowth() {
        return isAutoGrowth;
    }
    
    public double getGrowthUpdateTime() {
        return growthUpdateTime;
    }
    
    public String getStorageType() {
        return storageType;
    }
    
    public String getDatabaseHost() {
        return databaseHost;
    }
    
    public int getDatabasePort() {
        return databasePort;
    }
    
    public String getDatabaseName() {
        return databaseName;
    }
    
    public String getDatabaseUsername() {
        return databaseUsername;
    }
    
    public String getDatabasePassword() {
        return databasePassword;
    }
    
    public int getMaxPoolSize() {
        return maxPoolSize;
    }
    
    public int getMinIdle() {
        return minIdle;
    }
    
    public long getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public long getIdleTimeout() {
        return idleTimeout;
    }
    
    public long getMaxLifetime() {
        return maxLifetime;
    }
}
