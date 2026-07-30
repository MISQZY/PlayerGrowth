package org.misqzy.flectonegrowth.common.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.misqzy.flectonegrowth.common.domain.PlayTime;
import org.misqzy.flectonegrowth.common.storage.migration.LegacyTableMigration;
import org.misqzy.flectonegrowth.common.storage.migration.SchemaVersionStore;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Embedded, file-based default storage - zero external setup required.
 *
 * <p>Deliberately does <b>not</b> extend {@link AbstractSqlStorage}: H2's
 * default SQL mode does not understand MySQL's {@code ON DUPLICATE KEY
 * UPDATE} syntax (that only works under H2's MySQL compatibility mode,
 * which this plugin does not force on). Using {@code MERGE INTO} here
 * keeps H2 correct under its native dialect instead of silently relying on
 * a compatibility mode that another plugin/driver version could disable.</p>
 */
public final class H2Storage implements Storage {

    private static final String UPSERT_SCHEMA_VERSION_SQL =
            "MERGE INTO flectonegrowth_schema_version (id, version) KEY(id) VALUES (1, ?)";

    private final Logger logger;
    private final File dataFolder;
    private final String pluginVersion;
    private HikariDataSource dataSource;

    public H2Storage(Logger logger, File dataFolder, String pluginVersion) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public boolean initialize() {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            logger.severe("H2 driver not found on the classpath.");
            return false;
        }

        File dbDir = new File(dataFolder, "data");
        //noinspection ResultOfMethodCallIgnored
        dbDir.mkdirs();
        String jdbcUrl = "jdbc:h2:" + new File(dbDir, "database").getAbsolutePath() + ";AUTO_SERVER=TRUE";

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setDriverClassName("org.h2.Driver");
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(10_000L);
        cfg.setPoolName("FlectoneGrowth-H2");

        try {
            dataSource = new HikariDataSource(cfg);
            renameLegacyTablesIfNeeded();
            createTables();
            migrateSchemaIfNeeded();
            return testConnection();
        } catch (Exception e) {
            logger.severe("Failed to initialise H2 database: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean testConnection() {
        if (dataSource == null || dataSource.isClosed()) return false;
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            logger.warning("H2 connection test failed: " + e.getMessage());
            return false;
        }
    }

    @Override public StorageType type() { return StorageType.H2; }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    /** See {@link LegacyTableMigration} - renames this project's tables from their pre-rebrand {@code playergrowth_*} name if an upgrading install still has them, before {@link #createTables()} would otherwise create fresh empty ones under the new name. */
    private void renameLegacyTablesIfNeeded() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            LegacyTableMigration.renameIfNeeded(conn, logger, "H2");
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_scales (" +
                            "uuid VARCHAR(36) PRIMARY KEY, scale DOUBLE NOT NULL, " +
                            "valid BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            ).executeUpdate();
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_genders (" +
                            "uuid VARCHAR(36) PRIMARY KEY, gender_key VARCHAR(32) NOT NULL DEFAULT 'male', " +
                            "valid BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            ).executeUpdate();
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_growth_time (" +
                            "uuid VARCHAR(36) PRIMARY KEY, growth_seconds BIGINT NOT NULL, " +
                            "valid BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            ).executeUpdate();
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_schema_version (id INT PRIMARY KEY, version VARCHAR(32) NOT NULL)"
            ).executeUpdate();
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS flectonegrowth_playtime (" +
                            "uuid VARCHAR(36) NOT NULL, server VARCHAR(64) NOT NULL DEFAULT '', " +
                            "first_seen BIGINT NOT NULL, last_seen BIGINT NOT NULL, " +
                            "total_seconds BIGINT NOT NULL DEFAULT 0, sessions INT NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY (uuid, server))"
            ).executeUpdate();
        }
    }

    /**
     * Runs any pending schema migrations via {@link SchemaVersionStore} - see
     * that class for the shared read/compare/migrate/write orchestration
     * (identical here and in {@code AbstractSqlStorage}; only
     * {@link #UPSERT_SCHEMA_VERSION_SQL} actually differs between dialects).
     */
    private void migrateSchemaIfNeeded() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            SchemaVersionStore.migrateIfNeeded(conn, StorageType.H2, UPSERT_SCHEMA_VERSION_SQL, pluginVersion, logger, "H2");
        }
    }

    @Override
    public Double getCustomScale(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT scale FROM flectonegrowth_scales WHERE uuid = ? AND valid = TRUE")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble("scale") : null;
            }
        } catch (SQLException e) {
            logger.severe("H2 getCustomScale: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean setCustomScale(UUID uuid, double scale) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "MERGE INTO flectonegrowth_scales (uuid, scale, valid, updated_at) KEY(uuid) " +
                             "VALUES (?, ?, TRUE, CURRENT_TIMESTAMP)")) {
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, scale);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe("H2 setCustomScale: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean removeCustomScale(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE flectonegrowth_scales SET valid = FALSE, updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND valid = TRUE")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe("H2 removeCustomScale: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getGenderKey(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT gender_key FROM flectonegrowth_genders WHERE uuid = ? AND valid = TRUE")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("gender_key") : null;
            }
        } catch (SQLException e) {
            logger.severe("H2 getGenderKey: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean setGenderKey(UUID uuid, String genderKey) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "MERGE INTO flectonegrowth_genders (uuid, gender_key, valid, updated_at) KEY(uuid) " +
                             "VALUES (?, ?, TRUE, CURRENT_TIMESTAMP)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, genderKey.toLowerCase());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe("H2 setGenderKey: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Long getGrowthTimeSeconds(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT growth_seconds FROM flectonegrowth_growth_time WHERE uuid = ? AND valid = TRUE")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("growth_seconds") : null;
            }
        } catch (SQLException e) {
            logger.severe("H2 getGrowthTimeSeconds: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean setGrowthTimeSeconds(UUID uuid, long seconds) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "MERGE INTO flectonegrowth_growth_time (uuid, growth_seconds, valid, updated_at) KEY(uuid) " +
                             "VALUES (?, ?, TRUE, CURRENT_TIMESTAMP)")) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, seconds);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe("H2 setGrowthTimeSeconds: " + e.getMessage());
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
            logger.severe("H2 getPlayTime: " + e.getMessage());
            return null;
        }
    }

    /**
     * H2's {@code MERGE INTO ... KEY(uuid)} always replaces every listed column
     * unconditionally - unlike MySQL/MariaDB's partial {@code ON DUPLICATE KEY
     * UPDATE}, it can't express "preserve first_seen/total_seconds on conflict"
     * in one statement. So this reads the existing row first (if any) and
     * carries its first_seen/total_seconds/sessions forward into the MERGE,
     * the same get-or-create shape {@code GrowthTimeAssigner.loadInto} already
     * uses at the Java level instead of the SQL level.
     */
    @Override
    public boolean recordJoin(UUID uuid, String server, long nowEpochSeconds) {
        try (Connection conn = dataSource.getConnection()) {
            PlayTime existing;
            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT first_seen, last_seen, total_seconds, sessions FROM flectonegrowth_playtime WHERE uuid = ? AND server = ?")) {
                select.setString(1, uuid.toString());
                select.setString(2, server);
                try (ResultSet rs = select.executeQuery()) {
                    existing = rs.next()
                            ? new PlayTime(rs.getLong("first_seen"), rs.getLong("last_seen"), rs.getLong("total_seconds"), rs.getInt("sessions"))
                            : null;
                }
            }

            long firstSeen = existing != null ? existing.first() : nowEpochSeconds;
            long totalSeconds = existing != null ? existing.total() : 0L;
            int sessions = existing != null ? existing.sessions() + 1 : 1;

            try (PreparedStatement merge = conn.prepareStatement(
                    "MERGE INTO flectonegrowth_playtime (uuid, server, first_seen, last_seen, total_seconds, sessions) KEY(uuid, server) " +
                            "VALUES (?, ?, ?, ?, ?, ?)")) {
                merge.setString(1, uuid.toString());
                merge.setString(2, server);
                merge.setLong(3, firstSeen);
                merge.setLong(4, nowEpochSeconds);
                merge.setLong(5, totalSeconds);
                merge.setInt(6, sessions);
                merge.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            logger.severe("H2 recordJoin: " + e.getMessage());
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
            logger.severe("H2 checkpointPlayTime: " + e.getMessage());
            return false;
        }
    }
}
