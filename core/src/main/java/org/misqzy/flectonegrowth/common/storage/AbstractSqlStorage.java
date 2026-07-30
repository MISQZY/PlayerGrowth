package org.misqzy.flectonegrowth.common.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.misqzy.flectonegrowth.common.domain.PlayTime;
import org.misqzy.flectonegrowth.common.storage.migration.LegacyTableMigration;
import org.misqzy.flectonegrowth.common.storage.migration.SchemaVersionStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Shared JDBC/HikariCP plumbing for the MySQL and MariaDB backends. Unlike
 * the original version this has no dependency on {@code org.bukkit.plugin.Plugin} -
 * it only needs a {@link Logger}, which every platform module that depends
 * on core (currently just the Bukkit module) can hand it.
 */
public abstract class AbstractSqlStorage implements Storage {

    private static final String UPSERT_SCHEMA_VERSION_SQL =
            "INSERT INTO flectonegrowth_schema_version (id, version) VALUES (1, ?) " +
                    "ON DUPLICATE KEY UPDATE version = VALUES(version)";

    protected final Logger logger;
    protected HikariDataSource dataSource;

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeoutMs;
    private final long idleTimeoutMs;
    private final long maxLifetimeMs;
    private final String pluginVersion;

    protected AbstractSqlStorage(Logger logger, String host, int port, String database,
                                  String username, String password,
                                  int maxPoolSize, int minIdle,
                                  long connectionTimeoutMs, long idleTimeoutMs, long maxLifetimeMs,
                                  String pluginVersion) {
        this.logger = logger;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.maxPoolSize = maxPoolSize;
        this.minIdle = minIdle;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
        this.maxLifetimeMs = maxLifetimeMs;
        this.pluginVersion = pluginVersion;
    }

    protected abstract String buildJdbcUrl(String host, int port, String database);
    protected abstract String driverClassName();
    protected abstract String storageName();

    @Override
    public boolean initialize() {
        try {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(buildJdbcUrl(host, port, database));
            cfg.setUsername(username);
            cfg.setPassword(password);
            cfg.setDriverClassName(driverClassName());
            cfg.setMaximumPoolSize(maxPoolSize);
            cfg.setMinimumIdle(minIdle);
            cfg.setConnectionTimeout(connectionTimeoutMs);
            cfg.setIdleTimeout(idleTimeoutMs);
            cfg.setMaxLifetime(maxLifetimeMs);
            cfg.setLeakDetectionThreshold(60_000);
            cfg.setPoolName("FlectoneGrowth-" + storageName());

            dataSource = new HikariDataSource(cfg);
            renameLegacyTablesIfNeeded();
            createTables();
            migrateSchemaIfNeeded();
            return testConnection();
        } catch (Exception e) {
            logger.severe("Failed to initialise " + storageName() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean testConnection() {
        if (dataSource == null || dataSource.isClosed()) return false;
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            logger.warning(storageName() + " connection test failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    /** See {@link LegacyTableMigration} - renames this project's tables from their pre-rebrand {@code playergrowth_*} name if an upgrading install still has them, before {@link #createTables()} would otherwise create fresh empty ones under the new name. */
    private void renameLegacyTablesIfNeeded() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            LegacyTableMigration.renameIfNeeded(conn, logger, storageName());
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_scales (" +
                            "uuid VARCHAR(36) PRIMARY KEY, scale DOUBLE NOT NULL, valid TINYINT(1) NOT NULL DEFAULT 1, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            ).executeUpdate();

            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_genders (" +
                            "uuid VARCHAR(36) PRIMARY KEY, gender_key VARCHAR(32) NOT NULL DEFAULT 'male', valid TINYINT(1) NOT NULL DEFAULT 1, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            ).executeUpdate();

            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_growth_time (" +
                            "uuid VARCHAR(36) PRIMARY KEY, growth_seconds BIGINT NOT NULL, valid TINYINT(1) NOT NULL DEFAULT 1, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            ).executeUpdate();

            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_schema_version (" +
                            "id INT PRIMARY KEY, version VARCHAR(32) NOT NULL" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            ).executeUpdate();

            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_playtime (" +
                            "uuid VARCHAR(36) NOT NULL, server VARCHAR(64) NOT NULL DEFAULT '', " +
                            "first_seen BIGINT NOT NULL, last_seen BIGINT NOT NULL, " +
                            "total_seconds BIGINT NOT NULL DEFAULT 0, sessions INT NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY (uuid, server)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            ).executeUpdate();
        }
    }

    /**
     * Runs any pending schema migrations via {@link SchemaVersionStore} - see
     * that class for the shared read/compare/migrate/write orchestration
     * (identical here and in {@code H2Storage}; only {@link #UPSERT_SCHEMA_VERSION_SQL}
     * actually differs between dialects).
     */
    private void migrateSchemaIfNeeded() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            SchemaVersionStore.migrateIfNeeded(conn, type(), UPSERT_SCHEMA_VERSION_SQL, pluginVersion, logger, storageName());
        }
    }

    @Override
    public Double getCustomScale(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT scale FROM flectonegrowth_scales WHERE uuid = ? AND valid = 1")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble("scale") : null;
            }
        } catch (SQLException e) {
            logger.severe(storageName() + " getCustomScale: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean setCustomScale(UUID uuid, double scale) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO flectonegrowth_scales (uuid, scale, valid, updated_at) VALUES (?, ?, 1, NOW()) " +
                             "ON DUPLICATE KEY UPDATE scale = VALUES(scale), valid = 1, updated_at = NOW()")) {
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, scale);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe(storageName() + " setCustomScale: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean removeCustomScale(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE flectonegrowth_scales SET valid = 0, updated_at = NOW() WHERE uuid = ? AND valid = 1")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe(storageName() + " removeCustomScale: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getGenderKey(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT gender_key FROM flectonegrowth_genders WHERE uuid = ? AND valid = 1")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("gender_key") : null;
            }
        } catch (SQLException e) {
            logger.severe(storageName() + " getGenderKey: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean setGenderKey(UUID uuid, String genderKey) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO flectonegrowth_genders (uuid, gender_key, valid, updated_at) VALUES (?, ?, 1, NOW()) " +
                             "ON DUPLICATE KEY UPDATE gender_key = VALUES(gender_key), valid = 1, updated_at = NOW()")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, genderKey.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe(storageName() + " setGenderKey: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Long getGrowthTimeSeconds(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT growth_seconds FROM flectonegrowth_growth_time WHERE uuid = ? AND valid = 1")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("growth_seconds") : null;
            }
        } catch (SQLException e) {
            logger.severe(storageName() + " getGrowthTimeSeconds: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean setGrowthTimeSeconds(UUID uuid, long seconds) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO flectonegrowth_growth_time (uuid, growth_seconds, valid, updated_at) VALUES (?, ?, 1, NOW()) " +
                             "ON DUPLICATE KEY UPDATE growth_seconds = VALUES(growth_seconds), valid = 1, updated_at = NOW()")) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, seconds);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe(storageName() + " setGrowthTimeSeconds: " + e.getMessage());
            return false;
        }
    }

    @Override
    public PlayTime getPlayTime(UUID uuid, String server) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT first_seen, last_seen, total_seconds, sessions FROM flectonegrowth_playtime WHERE uuid = ? AND server = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, server);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return new PlayTime(rs.getLong("first_seen"), rs.getLong("last_seen"),
                        rs.getLong("total_seconds"), rs.getInt("sessions"));
            }
        } catch (SQLException e) {
            logger.severe(storageName() + " getPlayTime: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean recordJoin(UUID uuid, String server, long nowEpochSeconds) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO flectonegrowth_playtime (uuid, server, first_seen, last_seen, total_seconds, sessions) " +
                             "VALUES (?, ?, ?, ?, 0, 1) " +
                             "ON DUPLICATE KEY UPDATE last_seen = VALUES(last_seen), sessions = sessions + 1")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, server);
            stmt.setLong(3, nowEpochSeconds);
            stmt.setLong(4, nowEpochSeconds);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe(storageName() + " recordJoin: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean checkpointPlayTime(UUID uuid, String server, long totalSeconds, long nowEpochSeconds) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE flectonegrowth_playtime SET last_seen = ?, total_seconds = ? WHERE uuid = ? AND server = ?")) {
            stmt.setLong(1, nowEpochSeconds);
            stmt.setLong(2, totalSeconds);
            stmt.setString(3, uuid.toString());
            stmt.setString(4, server);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe(storageName() + " checkpointPlayTime: " + e.getMessage());
            return false;
        }
    }
}
