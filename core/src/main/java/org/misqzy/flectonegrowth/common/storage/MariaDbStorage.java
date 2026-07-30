package org.misqzy.flectonegrowth.common.storage;

import java.util.logging.Logger;

public final class MariaDbStorage extends AbstractSqlStorage {

    public MariaDbStorage(Logger logger, String host, int port, String database,
                           String username, String password,
                           int maxPoolSize, int minIdle,
                           long connectionTimeoutMs, long idleTimeoutMs, long maxLifetimeMs,
                           String pluginVersion) {
        super(logger, host, port, database, username, password,
                maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, pluginVersion);
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database) {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC";
    }

    @Override protected String driverClassName() { return "org.mariadb.jdbc.Driver"; }
    @Override protected String storageName() { return "MariaDB"; }
    @Override public StorageType type() { return StorageType.MARIADB; }
}
