package org.misqzy.flectonegrowth.common.storage;

import org.misqzy.flectonegrowth.common.config.CoreConfig;

import java.io.File;
import java.util.logging.Logger;

public final class StorageFactory {

    private StorageFactory() {}

    public static Storage create(Logger logger, File dataFolder, CoreConfig config) {
        return switch (config.storageType()) {
            case YAML -> new YamlStorage(logger, dataFolder);
            case H2 -> new H2Storage(logger, dataFolder, config.version());
            case MYSQL -> new MySQLStorage(logger,
                    config.dbHost(), config.dbPort(), config.dbName(), config.dbUsername(), config.dbPassword(),
                    config.maxPoolSize(), config.minIdle(), config.connectionTimeoutMs(), config.idleTimeoutMs(), config.maxLifetimeMs(),
                    config.version());
            case MARIADB -> new MariaDbStorage(logger,
                    config.dbHost(), config.dbPort(), config.dbName(), config.dbUsername(), config.dbPassword(),
                    config.maxPoolSize(), config.minIdle(), config.connectionTimeoutMs(), config.idleTimeoutMs(), config.maxLifetimeMs(),
                    config.version());
        };
    }

    /** Always-available last-resort backend used if the configured one fails to initialise. */
    public static Storage createFallback(Logger logger, File dataFolder) {
        return new YamlStorage(logger, dataFolder);
    }
}
