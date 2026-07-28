package org.misqzy.playergrowth.common.storage;

import java.util.logging.Logger;

public final class MySQLStorage extends AbstractSqlStorage {

    public MySQLStorage(Logger logger, String host, int port, String database,
                         String username, String password,
                         int maxPoolSize, int minIdle,
                         long connectionTimeoutMs, long idleTimeoutMs, long maxLifetimeMs,
                         String pluginVersion) {
        super(logger, host, port, database, username, password,
                maxPoolSize, minIdle, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, pluginVersion);
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    @Override protected String driverClassName() { return "com.mysql.cj.jdbc.Driver"; }
    @Override protected String storageName() { return "MySQL"; }
    @Override public StorageType type() { return StorageType.MYSQL; }
}
