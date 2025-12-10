package org.misqzy.playerGrowth.storage;

import org.bukkit.plugin.Plugin;
import org.misqzy.playerGrowth.config.ConfigManager;

public class StorageFactory {
    
    public static Storage createStorage(Plugin plugin, ConfigManager config) {
        String storageType = config.getStorageType().toUpperCase();
        
        switch (storageType) {
            case "YAML":
                return new YAMLStorage(plugin);
                
            case "H2":
                return new H2Storage(plugin);
                
            case "MYSQL":
                return new MySQLStorage(
                    plugin,
                    config.getDatabaseHost(),
                    config.getDatabasePort(),
                    config.getDatabaseName(),
                    config.getDatabaseUsername(),
                    config.getDatabasePassword(),
                    config.getMaxPoolSize(),
                    config.getMinIdle(),
                    config.getConnectionTimeout(),
                    config.getIdleTimeout(),
                    config.getMaxLifetime()
                );
                
            case "MARIADB":
                return new MariaDBStorage(
                    plugin,
                    config.getDatabaseHost(),
                    config.getDatabasePort(),
                    config.getDatabaseName(),
                    config.getDatabaseUsername(),
                    config.getDatabasePassword(),
                    config.getMaxPoolSize(),
                    config.getMinIdle(),
                    config.getConnectionTimeout(),
                    config.getIdleTimeout(),
                    config.getMaxLifetime()
                );
                
            default:
                plugin.getLogger().warning("Unknown storage type: " + storageType + ". Using H2 as default.");
                return new H2Storage(plugin);
        }
    }
}

