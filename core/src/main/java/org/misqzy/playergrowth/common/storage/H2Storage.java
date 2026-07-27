package org.misqzy.playergrowth.common.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.misqzy.playergrowth.common.storage.migration.SchemaMigration;
import org.misqzy.playergrowth.common.storage.migration.SchemaMigrationRunner;
import org.misqzy.playergrowth.common.storage.migration.SchemaMigrations;

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

    private final Logger logger;
    private final File dataFolder;
    private HikariDataSource dataSource;

    public H2Storage(Logger logger, File dataFolder) {
        this.logger = logger;
        this.dataFolder = dataFolder;
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
        cfg.setPoolName("PlayerGrowth-H2");

        try {
            dataSource = new HikariDataSource(cfg);
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

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playergrowth_scales (" +
                            "uuid VARCHAR(36) PRIMARY KEY, scale DOUBLE NOT NULL, " +
                            "valid BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            ).executeUpdate();
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playergrowth_genders (" +
                            "uuid VARCHAR(36) PRIMARY KEY, gender_key VARCHAR(32) NOT NULL DEFAULT 'male', " +
                            "valid BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            ).executeUpdate();
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playergrowth_growth_time (" +
                            "uuid VARCHAR(36) PRIMARY KEY, growth_seconds BIGINT NOT NULL, " +
                            "valid BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            ).executeUpdate();
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playergrowth_schema_version (id INT PRIMARY KEY, version INT NOT NULL)"
            ).executeUpdate();
        }
    }

    /**
     * Runs any pending {@link SchemaMigration}s - see that interface and
     * {@link SchemaMigrations} for why/how. A brand new install's tables
     * already match {@link SchemaMigrations#CURRENT_VERSION}'s shape
     * directly (just built fresh above), so {@link #readSchemaVersion}
     * stamps that version immediately rather than treating "no version row
     * yet" as "run every migration ever written".
     */
    private void migrateSchemaIfNeeded() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            int current = readSchemaVersion(conn);
            if (current >= SchemaMigrations.CURRENT_VERSION) return;

            for (SchemaMigration step : SchemaMigrations.pending(current)) {
                SchemaMigrationRunner.run(conn, step.statements(StorageType.H2));
            }
            writeSchemaVersion(conn, SchemaMigrations.CURRENT_VERSION);
            logger.info("Migrated H2 schema from version " + current + " to " + SchemaMigrations.CURRENT_VERSION + ".");
        }
    }

    private int readSchemaVersion(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT version FROM playergrowth_schema_version WHERE id = 1");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt("version");
        }
        writeSchemaVersion(conn, SchemaMigrations.CURRENT_VERSION);
        return SchemaMigrations.CURRENT_VERSION;
    }

    private void writeSchemaVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "MERGE INTO playergrowth_schema_version (id, version) KEY(id) VALUES (1, ?)")) {
            stmt.setInt(1, version);
            stmt.executeUpdate();
        }
    }

    @Override
    public Double getCustomScale(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT scale FROM playergrowth_scales WHERE uuid = ? AND valid = TRUE")) {
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
                     "MERGE INTO playergrowth_scales (uuid, scale, valid, updated_at) KEY(uuid) " +
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
                     "UPDATE playergrowth_scales SET valid = FALSE, updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND valid = TRUE")) {
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
                     "SELECT gender_key FROM playergrowth_genders WHERE uuid = ? AND valid = TRUE")) {
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
                     "MERGE INTO playergrowth_genders (uuid, gender_key, valid, updated_at) KEY(uuid) " +
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
                     "SELECT growth_seconds FROM playergrowth_growth_time WHERE uuid = ? AND valid = TRUE")) {
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
                     "MERGE INTO playergrowth_growth_time (uuid, growth_seconds, valid, updated_at) KEY(uuid) " +
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
}
