package org.misqzy.flectonegrowth.common.storage.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

/**
 * One-time rename of this project's tables from their pre-rebrand
 * {@code playergrowth_*} name to {@code flectonegrowth_*} - the plugin used
 * to be named PlayerGrowth, and every {@code CREATE TABLE IF NOT EXISTS} in
 * {@link org.misqzy.flectonegrowth.common.storage.AbstractSqlStorage} and
 * {@link org.misqzy.flectonegrowth.common.storage.H2Storage} now targets the
 * new name. Without this step an upgrading MySQL/MariaDB/H2 install would
 * find no {@code flectonegrowth_*} tables, silently create empty ones, and
 * orphan every existing player's data under the old name.
 *
 * <p>{@code ALTER TABLE x RENAME TO y} is one of the few DDL statements
 * whose syntax is identical across MySQL, MariaDB, and H2, so a single
 * dialect-agnostic statement covers all three backends here - no need to
 * branch on {@link org.misqzy.flectonegrowth.common.storage.StorageType}
 * the way the CRUD SQL and {@link SchemaMigrations} steps do elsewhere.
 * Existence is checked via {@link DatabaseMetaData} rather than attempting
 * the rename and swallowing the failure, since a bare "did it fail because
 * the old table is missing, or for some other reason" is not something a
 * caught {@link SQLException} lets this reliably tell apart.</p>
 */
public final class LegacyTableMigration {

    private static final String OLD_PREFIX = "playergrowth_";
    private static final String NEW_PREFIX = "flectonegrowth_";

    private static final List<String> TABLES = List.of(
            "scales", "genders", "growth_time", "schema_version", "playtime");

    private LegacyTableMigration() {}

    public static void renameIfNeeded(Connection conn, Logger logger, String storageName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        for (String table : TABLES) {
            String oldName = OLD_PREFIX + table;
            String newName = NEW_PREFIX + table;
            if (tableExists(meta, oldName) && !tableExists(meta, newName)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE " + oldName + " RENAME TO " + newName);
                }
                logger.info(storageName + " renamed legacy table " + oldName + " to " + newName + " (PlayerGrowth -> FlectoneGrowth rename).");
            }
        }
    }

    /**
     * Tries the name as given first (matches MySQL/MariaDB, which store
     * these unquoted identifiers verbatim in lowercase as created), then
     * uppercased (matches H2, which folds unquoted identifiers to uppercase
     * internally) - {@link DatabaseMetaData#getTables} needs an exact match
     * on the pattern, unlike a DML statement referencing the same unquoted
     * name, which every one of these dialects case-folds for you.
     */
    private static boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), null)) {
            return rs.next();
        }
    }
}
