package org.misqzy.playergrowth.common.config.migration;

import org.yaml.snakeyaml.nodes.MappingNode;

/**
 * One explicit, hand-written transform applied to a bundled resource
 * file's composed YAML tree when upgrading *to* {@link #targetVersion()} -
 * for renames, moves, and removals that a generic "copy any bundled key the
 * user's file is missing" merge can't express (that part is handled
 * separately by {@link ConfigMigrations}, not by steps).
 *
 * <p>Mirrors the shape of FlectonePulse's {@code FileMigrator#migration_x_y_z}
 * methods (one method per version, each returning a transformed copy of
 * their config tree) - adapted to operate on a SnakeYAML {@link MappingNode}
 * (via {@link YamlNodeOps}) rather than a plain {@code Map<String, Object>},
 * so a step's edits keep whatever comments the on-disk file already had.</p>
 */
public interface ConfigMigrationStep {

    /** The bundled {@code version} (the plugin's own semver) this step upgrades a file to. */
    String targetVersion();

    /** Which bundled resource file this step applies to, e.g. {@code "config.yml"}. */
    String resourceName();

    /** Mutates {@code root} (the on-disk file's composed tree) in place. */
    void apply(MappingNode root);
}
