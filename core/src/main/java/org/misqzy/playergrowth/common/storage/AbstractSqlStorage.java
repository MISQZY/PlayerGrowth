package org.misqzy.playergrowth.common.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.misqzy.playergrowth.common.config.migration.VersionComparator;
import org.misqzy.playergrowth.common.storage.migration.SchemaMigration;
import org.misqzy.playergrowth.common.storage.migration.SchemaMigrationRunner;
import org.misqzy.playergrowth.common.storage.migration.SchemaMigrations;

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

    /** Stamped when the version row is missing/unreadable, e.g. a pre-unification install whose column still held a bare int - matches ConfigMigrator's UNKNOWN_VERSION. */
    private static final String UNKNOWN_VERSION = "0.0.0";

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
            cfg.setPoolName("PlayerGrowth-" + storageName());

            dataSource = new HikariDataSource(cfg);
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

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playergrowth_scales (" +
                            "uuid VARCHAR(36) PRIMARY KEY, scale DOUBLE NOT NULL, valid TINYINT(1) NOT NULL DEFAULT 1, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            ).executeUpdate();

            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playergrowth_genders (" +
                            "uuid VARCHAR(36) PRIMARY KEY, gender_key VARCHAR(32) NOT NULL DEFAULT 'male', valid TINYINT(1) NOT NULL DEFAULT 1, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            ).executeUpdate();

            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playergrowth_growth_time (" +
                            "uuid VARCHAR(36) PRIMARY KEY, growth_seconds BIGINT NOT NULL, valid TINYINT(1) NOT NULL DEFAULT 1, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            ).executeUpdate();

            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playergrowth_schema_version (" +
                            "id INT PRIMARY KEY, version VARCHAR(32) NOT NULL" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            ).executeUpdate();
        }
    }

    /**
     * Runs any pending {@link SchemaMigration}s - see that interface and
     * {@link SchemaMigrations} for why/how. A brand new install's tables
     * already match the running plugin's shape directly (just built fresh
     * above), so {@link #readSchemaVersion} stamps {@link #pluginVersion}
     * immediately rather than treating "no version row yet" as "run every
     * migration ever written".
     */
    private void migrateSchemaIfNeeded() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String current = readSchemaVersion(conn);
            if (VersionComparator.compare(current, pluginVersion) >= 0) return;

            for (SchemaMigration step : SchemaMigrations.pending(current, pluginVersion)) {
                SchemaMigrationRunner.run(conn, step.statements(type()));
            }
            writeSchemaVersion(conn, pluginVersion);
            logger.info(storageName() + " schema migrated from version " + current + " to " + pluginVersion + ".");
        }
    }

    private String readSchemaVersion(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT version FROM playergrowth_schema_version WHERE id = 1");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                // getString() reads a legacy INT-typed column (the pre-unification "1") as text too;
                // a bare integer isn't a real semver, so treat it as unknown rather than let
                // VersionComparator misread e.g. "1" as newer than "0.1.2".
                String stored = rs.getString("version");
                return stored != null && stored.indexOf('.') >= 0 ? stored : UNKNOWN_VERSION;
            }
        }
        writeSchemaVersion(conn, pluginVersion);
        return pluginVersion;
    }

    /**
     * Writing a real semver here (not a bare int) requires the column to
     * already be {@code VARCHAR} - a fresh install's {@code CREATE TABLE IF
     * NOT EXISTS} already builds it that way, and an upgrading install gets
     * there via {@link SchemaMigrations}' registered widening step (see its
     * javadoc), run by {@link #migrateSchemaIfNeeded} before this is ever
     * called with the new format.
     */
    private void writeSchemaVersion(Connection conn, String version) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO playergrowth_schema_version (id, version) VALUES (1, ?) " +
                        "ON DUPLICATE KEY UPDATE version = VALUES(version)")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    @Override
    public Double getCustomScale(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT scale FROM playergrowth_scales WHERE uuid = ? AND valid = 1")) {
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
                     "INSERT INTO playergrowth_scales (uuid, scale, valid, updated_at) VALUES (?, ?, 1, NOW()) " +
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
                     "UPDATE playergrowth_scales SET valid = 0, updated_at = NOW() WHERE uuid = ? AND valid = 1")) {
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
                     "SELECT gender_key FROM playergrowth_genders WHERE uuid = ? AND valid = 1")) {
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
                     "INSERT INTO playergrowth_genders (uuid, gender_key, valid, updated_at) VALUES (?, ?, 1, NOW()) " +
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
                     "SELECT growth_seconds FROM playergrowth_growth_time WHERE uuid = ? AND valid = 1")) {
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
                     "INSERT INTO playergrowth_growth_time (uuid, growth_seconds, valid, updated_at) VALUES (?, ?, 1, NOW()) " +
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
}
