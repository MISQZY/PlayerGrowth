package org.misqzy.playergrowth.common.config.migration;

import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry and runner for {@link ConfigMigrationStep}s.
 *
 * <p>Replaces the old "back the whole file up, then overwrite it with the
 * bundled default" approach a {@code version} bump used to trigger:
 * that lost every customisation in the file, not just whatever actually
 * changed shape, on any bump - including ones as small as renaming a single
 * key. Studying how FlectonePulse handles this (their {@code FileMigrator}
 * runs one hand-written transform per version against their config's
 * in-memory tree, then re-serialises the whole thing - so unrelated user
 * values survive a version bump) is what this mirrors, scaled down: this
 * project's config is a handful of scalar values behind a SnakeYAML
 * {@link MappingNode} tree (via {@link YamlNodeOps}), not a deep Jackson
 * record tree, so it doesn't need FlectonePulse's per-field wither-record
 * machinery to get the same "don't lose what you didn't change" property -
 * and operating on the composed {@link Node} tree rather than a plain
 * {@code Map<String,Object>} also keeps whatever comments the file already
 * had, see {@link YamlNodeOps}.</p>
 *
 * <p>Two mechanisms, run in this order, per resource file:</p>
 * <ol>
 *   <li>Every registered {@link ConfigMigrationStep} for that file whose
 *   {@code targetVersion()} falls in {@code (fromVersion, toVersion]} -
 *   for renames, moves, and removals a generic merge can't express.</li>
 *   <li>A deep merge of any key present in the bundled default but still
 *   absent from the disk file afterwards - covers purely additive new
 *   keys (the common case) without needing a step written for every one.
 *   Only appropriate for resource files with a fixed, closed key set
 *   (config.yml, integrations.yml, the message files); deliberately
 *   <b>not</b> run for {@code gender.yml}'s {@code types} section, which is
 *   an open, user-owned map where a "missing" key usually means the admin
 *   removed it on purpose, not that the file is stale.</li>
 * </ol>
 */
public final class ConfigMigrations {

    /**
     * Registered steps, in no particular order (each is independently
     * filtered by resource file and version range).
     */
    private static final List<ConfigMigrationStep> STEPS = List.of(
            new ConfigMigrationStep() {
                // config.yml's version now does double duty (plugin version +
                // migration trigger, see ConfigMigrator), so the old separate
                // config-version/plugin-version keys are dead weight on any
                // install upgrading from before this - a generic merge only
                // ever adds missing keys, never removes stale ones, so this
                // needs an explicit step. Same release also hoists network.server
                // to a top-level server key (mirroring FlectonePulse's own config
                // shape) - carry the admin's already-persisted id forward instead
                // of letting mergeMissing silently add a *blank* top-level server
                // next to the old, now-unread nested one.
                @Override public String targetVersion() { return "0.1.1"; }
                @Override public String resourceName() { return "config.yml"; }
                @Override public void apply(MappingNode root) {
                    YamlNodeOps.remove(root, "config-version");
                    YamlNodeOps.remove(root, "plugin-version");

                    MappingNode network = YamlNodeOps.getMapping(root, "network");
                    if (network == null) return;

                    String legacyServer = YamlNodeOps.getScalarString(network, "server");
                    String topLevelServer = YamlNodeOps.getScalarString(root, "server");
                    boolean topLevelBlank = topLevelServer == null || topLevelServer.isBlank();
                    if (legacyServer != null && !legacyServer.isBlank() && topLevelBlank) {
                        YamlNodeOps.putScalarString(root, "server", legacyServer);
                    }
                    YamlNodeOps.remove(network, "server");
                }
            },
            // network.blocklist (a list of excluded server ids, checked
            // against every server on the network) is replaced by two
            // per-server booleans this server decides for itself:
            // network.per-server (split playtime into a bucket scoped to
            // this server instead of one network-wide total) and
            // network.include-server (whether this server's time counts at
            // all - the direct replacement for "this server's own id was in
            // the blocklist"). per-server defaults to false (unchanged
            // network-wide-total behavior). include-server is derived from
            // whether this install's own already-resolved top-level `server`
            // id appeared in its own blocklist, so an admin who blocklisted
            // this server keeps the same effective behavior after upgrading
            // instead of silently starting to count blocklisted time again.
            new ConfigMigrationStep() {
                @Override public String targetVersion() { return "0.2.1"; }
                @Override public String resourceName() { return "config.yml"; }
                @Override public void apply(MappingNode root) {
                    MappingNode network = YamlNodeOps.getMapping(root, "network");
                    if (network == null) return;

                    SequenceNode blocklistNode = YamlNodeOps.getSequence(network, "blocklist");
                    List<String> blocklist = YamlNodeOps.scalarStrings(blocklistNode);
                    YamlNodeOps.remove(network, "blocklist");

                    boolean includeServer = true;
                    if (blocklistNode != null) {
                        String ownId = YamlNodeOps.getScalarString(root, "server");
                        if (ownId != null && !ownId.isBlank()) {
                            includeServer = blocklist.stream()
                                    .noneMatch(entry -> ownId.equals(entry == null ? null : entry.trim()));
                        }
                    }

                    YamlNodeOps.putScalarBooleanIfAbsent(network, "per-server", false);
                    YamlNodeOps.putScalarBooleanIfAbsent(network, "include-server", includeServer);
                }
            }
    );

    private ConfigMigrations() {}

    /**
     * @param resourceName     e.g. {@code "config.yml"} - only steps registered for this file run
     * @param disk             the on-disk file's composed tree, mutated in place
     * @param bundled          the jar's bundled default for the same file, used as the merge source
     * @param fromVersion      the disk file's current {@code version}
     * @param toVersion        the bundled default's {@code version}
     * @param mergeMissingKeys whether to deep-merge bundled keys absent from {@code disk} afterwards
     */
    public static void apply(String resourceName, MappingNode disk, MappingNode bundled,
                              String fromVersion, String toVersion, boolean mergeMissingKeys) {
        for (ConfigMigrationStep step : STEPS) {
            if (!step.resourceName().equals(resourceName)) continue;
            if (VersionComparator.compare(step.targetVersion(), fromVersion) <= 0) continue;
            if (VersionComparator.compare(step.targetVersion(), toVersion) > 0) continue;
            step.apply(disk);
        }

        if (mergeMissingKeys) {
            mergeMissing(disk, bundled);
        }
    }

    /**
     * Appends every {@code source} tuple missing from {@code target} (carrying
     * over the bundled file's own comments on that key) and recurses into
     * nested mappings both sides already share - mirrors the old
     * {@code Map.put}-based merge's semantics (new keys are appended, same as
     * a {@code LinkedHashMap} would), just against {@link Node}s instead.
     */
    private static void mergeMissing(MappingNode target, MappingNode source) {
        List<NodeTuple> additions = new ArrayList<>();
        for (NodeTuple sourceTuple : source.getValue()) {
            String key = sourceTuple.getKeyNode() instanceof org.yaml.snakeyaml.nodes.ScalarNode scalar
                    ? scalar.getValue() : null;
            if (key == null) continue;

            NodeTuple existing = YamlNodeOps.find(target, key).orElse(null);
            if (existing == null) {
                additions.add(sourceTuple);
            } else if (existing.getValueNode() instanceof MappingNode targetChild
                    && sourceTuple.getValueNode() instanceof MappingNode sourceChild) {
                mergeMissing(targetChild, sourceChild);
            }
        }

        if (!additions.isEmpty()) {
            List<NodeTuple> merged = new ArrayList<>(target.getValue());
            merged.addAll(additions);
            target.setValue(merged);
        }
    }
}
