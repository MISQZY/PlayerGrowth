package org.misqzy.playergrowth.common.config.migration;

import java.util.List;
import java.util.Map;

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
 * project's config is a handful of scalar values behind a plain
 * {@code Map<String, Object>}, not a deep Jackson record tree, so it
 * doesn't need FlectonePulse's per-field wither-record machinery to get
 * the same "don't lose what you didn't change" property.</p>
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
 *   (config.yml, the message files); deliberately <b>not</b> run for
 *   {@code gender.yml}'s {@code types} section, which is an open,
 *   user-owned map where a "missing" key usually means the admin removed
 *   it on purpose, not that the file is stale.</li>
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
                @Override public void apply(Map<String, Object> root) {
                    root.remove("config-version");
                    root.remove("plugin-version");

                    Object networkSection = root.get("network");
                    if (networkSection instanceof Map<?, ?> network) {
                        Object legacyServer = network.get("server");
                        boolean topLevelBlank = !(root.get("server") instanceof String s) || s.isBlank();
                        if (legacyServer instanceof String legacy && !legacy.isBlank() && topLevelBlank) {
                            root.put("server", legacy);
                        }
                        network.remove("server");
                    }
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
                @Override @SuppressWarnings("unchecked")
                public void apply(Map<String, Object> root) {
                    Object networkSection = root.get("network");
                    if (!(networkSection instanceof Map<?, ?> networkRaw)) return;
                    Map<String, Object> network = (Map<String, Object>) networkRaw;

                    Object blocklist = network.remove("blocklist");
                    boolean includeServer = true;
                    if (blocklist instanceof List<?> entries) {
                        Object ownServer = root.get("server");
                        if (ownServer instanceof String ownId && !ownId.isBlank()) {
                            includeServer = entries.stream().noneMatch(entry -> ownId.equals(String.valueOf(entry).trim()));
                        }
                    }

                    network.putIfAbsent("per-server", false);
                    network.putIfAbsent("include-server", includeServer);
                }
            }
    );

    private ConfigMigrations() {}

    /**
     * @param resourceName     e.g. {@code "config.yml"} - only steps registered for this file run
     * @param disk             the on-disk file's parsed tree, mutated in place
     * @param bundled          the jar's bundled default for the same file, used as the merge source
     * @param fromVersion      the disk file's current {@code version}
     * @param toVersion        the bundled default's {@code version}
     * @param mergeMissingKeys whether to deep-merge bundled keys absent from {@code disk} afterwards
     */
    public static void apply(String resourceName, Map<String, Object> disk, Map<String, Object> bundled,
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

    @SuppressWarnings("unchecked")
    private static void mergeMissing(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object sourceValue = entry.getValue();
            if (!target.containsKey(key)) {
                target.put(key, sourceValue);
            } else if (sourceValue instanceof Map<?, ?> && target.get(key) instanceof Map<?, ?>) {
                mergeMissing((Map<String, Object>) target.get(key), (Map<String, Object>) sourceValue);
            }
        }
    }
}
