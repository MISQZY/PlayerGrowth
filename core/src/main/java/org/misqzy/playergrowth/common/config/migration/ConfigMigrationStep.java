package org.misqzy.playergrowth.common.config.migration;

import java.util.Map;

/**
 * One explicit, hand-written transform applied to a bundled resource
 * file's raw parsed YAML tree when upgrading *to* {@link #targetVersion()} -
 * for renames, moves, and removals that a generic "copy any bundled key the
 * user's file is missing" merge can't express (that part is handled
 * separately by {@link ConfigMigrations}, not by steps).
 *
 * <p>Mirrors the shape of FlectonePulse's {@code FileMigrator#migration_x_y_z}
 * methods (one method per version, each returning a transformed copy of
 * their config tree) - adapted to operate on the plain
 * {@code Map<String, Object>} SnakeYAML already produces here, since this
 * project's config is a handful of scalar values, not FlectonePulse's deep
 * Jackson record tree, and doesn't need that machinery.</p>
 */
public interface ConfigMigrationStep {

    /** The bundled {@code version} (the plugin's own semver) this step upgrades a file to. */
    String targetVersion();

    /** Which bundled resource file this step applies to, e.g. {@code "config.yml"}. */
    String resourceName();

    /** Mutates {@code root} (the on-disk file's parsed tree) in place. */
    void apply(Map<String, Object> root);
}
