package org.misqzy.playergrowth.common.storage.migration;

import org.misqzy.playergrowth.common.storage.StorageType;

import java.util.List;

/**
 * One incremental database schema change (an {@code ALTER TABLE}, a data
 * copy between tables, etc.) - the SQL analogue of
 * {@link org.misqzy.playergrowth.common.config.migration.ConfigMigrationStep}.
 *
 * <p>Mirrors FlectonePulse's {@code sqls/migrations/&lt;version&gt;.sql}
 * files (one script per version bump that needed one, applied in order and
 * gated by a version stored in the database itself - their {@code fp_version}
 * table) - adapted for this project's smaller, simpler schema and its
 * existing per-dialect split ({@link org.misqzy.playergrowth.common.storage.AbstractSqlStorage}
 * for MySQL/MariaDB, {@link org.misqzy.playergrowth.common.storage.H2Storage}
 * independently for H2 - see {@code docs/ARCHITECTURE.md} "Bugs fixed" #1
 * for why those two are never collapsed together). A plain {@code int}
 * version is used here instead of FlectonePulse's semantic-version string
 * comparison: this project's schema version is independent of the plugin's
 * own release version and only this project controls when it advances, so
 * a monotonic counter (matching how {@code config-version} already works)
 * is simpler and needs no version-comparison utility.</p>
 */
public interface SchemaMigration {

    /** The schema version this step upgrades the database to. */
    int targetVersion();

    /** Dialect-specific statements to run when upgrading to {@link #targetVersion()}, in order. */
    List<String> statements(StorageType dialect);
}
