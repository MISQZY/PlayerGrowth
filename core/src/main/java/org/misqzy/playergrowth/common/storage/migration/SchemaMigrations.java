package org.misqzy.playergrowth.common.storage.migration;

import java.util.Comparator;
import java.util.List;

/**
 * Registry of every {@link SchemaMigration} this project has ever needed,
 * plus the current target version. Each storage implementation
 * ({@code AbstractSqlStorage}, {@code H2Storage}) stores this version in
 * its own {@code playergrowth_schema_version} table (one row, id 1) and
 * runs whatever's {@link #pending(int)} on connect - see the migration
 * methods on those classes for the actual read/write/run orchestration
 * (kept per-class rather than shared, same reasoning as their CRUD SQL:
 * the version-table upsert syntax is itself dialect-specific - H2's
 * {@code MERGE INTO} vs MySQL/MariaDB's {@code ON DUPLICATE KEY UPDATE} -
 * so there's nothing dialect-agnostic left to usefully share beyond
 * {@link SchemaMigrationRunner}, which just executes whatever
 * already-dialect-resolved statements it's handed).
 */
public final class SchemaMigrations {

    /**
     * The schema version this codebase currently expects. A brand new
     * install's tables already match this shape directly (built fresh by
     * each storage class's {@code CREATE TABLE IF NOT EXISTS} statements),
     * so it only stamps this version rather than running any migration.
     */
    public static final int CURRENT_VERSION = 1;

    /** No migrations registered yet - this is the infrastructure a future schema change would register a step into. */
    private static final List<SchemaMigration> STEPS = List.of();

    private SchemaMigrations() {}

    /** Every registered step whose {@code targetVersion()} falls in {@code (fromVersion, CURRENT_VERSION]}, in ascending order. */
    public static List<SchemaMigration> pending(int fromVersion) {
        return STEPS.stream()
                .filter(step -> step.targetVersion() > fromVersion && step.targetVersion() <= CURRENT_VERSION)
                .sorted(Comparator.comparingInt(SchemaMigration::targetVersion))
                .toList();
    }
}
