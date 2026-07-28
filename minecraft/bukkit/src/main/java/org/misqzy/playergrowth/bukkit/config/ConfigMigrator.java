package org.misqzy.playergrowth.bukkit.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playergrowth.common.config.migration.ConfigMigrations;
import org.misqzy.playergrowth.common.config.migration.VersionComparator;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Migrates every bundled resource file (config.yml, gender.yml, both locale
 * files) forward when config.yml's {@code version} is stale, by
 * transforming each on-disk parsed tree in place rather than discarding it
 * - see {@link ConfigMigrations} for why and how. All four files share
 * config.yml's version rather than each tracking their own; there's
 * only one bundled "resource pack" version to be behind or caught up with.
 *
 * <p>{@code version} is the plugin's own semver (e.g. {@code "0.1.1"}),
 * token-expanded into the bundled config.yml at build time - not a separate
 * incrementing counter. This means the on-disk copy also doubles as "the
 * plugin build that generated/last touched this file" (what a dedicated
 * {@code ConfigVersionStamper} used to do as a second mechanism, before the
 * two fields were merged into one): every release runs this migration path
 * at least once, which is harmless since {@link ConfigMigrations#apply}'s
 * merge step is idempotent when nothing actually changed shape.</p>
 *
 * <p>Comments are still lost on any file this rewrites - that's an
 * unavoidable SnakeYAML limitation (see docs/ARCHITECTURE.md "Known,
 * accepted limitations"), unrelated to and not fixed by this class. What
 * this fixes is <em>data</em> loss: every value the admin customised in
 * {@code config.yml}/{@code gender.yml} survives a version bump now (unless
 * an explicit migration step intentionally changes it), where the old
 * "back up and overwrite with the bundled default" approach discarded a
 * file's entire customisation on any bump at all, however small.</p>
 *
 * <p>Localization files are the deliberate exception to that: they're
 * still backed up first, but then replaced outright with the bundled
 * translation rather than merged - see {@link #replaceWithBundled}. A
 * merge only ever <em>adds</em> keys the disk copy is missing, so wording
 * or color-tag changes to an <em>existing</em> message key in a new release
 * would otherwise never reach an upgrading install; the admin's old
 * translation would keep loading forever, indistinguishable from an
 * intentional customisation.</p>
 */
public final class ConfigMigrator {

    /** Has a fixed, closed key set and admin-customisable values - safe to auto-merge any bundled key the disk copy is missing. */
    private static final String MAIN_CONFIG_RESOURCE = "config.yml";

    /** Bundled translations this plugin fully owns - replaced wholesale rather than merged, see the class-level note above. */
    private static final List<String> LOCALIZATION_RESOURCES = List.of(
            "localizations/messages_en.yml", "localizations/messages_ru.yml");

    /** Has an open, user-owned {@code types} section - only explicit steps run against it, never a blind merge. */
    private static final String GENDER_RESOURCE = "gender.yml";

    /** No {@code version} on disk at all (an install from before this field existed) - always stale. */
    private static final String UNKNOWN_VERSION = "0.0.0";

    private final JavaPlugin plugin;
    private final Yaml yaml = new Yaml();

    public ConfigMigrator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void migrateIfNeeded() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        String bundledVersion = readVersion(readBundled("config.yml"));
        String onDiskVersion = readVersion(readDisk(configFile));
        if (VersionComparator.compare(onDiskVersion, bundledVersion) >= 0) return;

        migrateOne(MAIN_CONFIG_RESOURCE, onDiskVersion, bundledVersion, true);
        for (String resource : LOCALIZATION_RESOURCES) {
            replaceWithBundled(resource);
        }
        migrateOne(GENDER_RESOURCE, onDiskVersion, bundledVersion, false);

        plugin.getLogger().warning("Migrated PlayerGrowth's config files from version " + onDiskVersion
                + " to " + bundledVersion + ". config.yml/gender.yml were backed up (.bak.<timestamp>) with custom"
                + " values carried over automatically; localization files were backed up the same way but replaced"
                + " outright with the new bundled translations.");
    }

    private void migrateOne(String resourceName, String fromVersion, String toVersion, boolean mergeMissingKeys) {
        File target = new File(plugin.getDataFolder(), resourceName);
        if (!target.exists()) return;

        Map<String, Object> bundled = readBundled(resourceName);
        if (bundled.isEmpty()) return; // not actually bundled under this name - nothing to migrate against

        Map<String, Object> disk = readDisk(target);
        backup(target);
        ConfigMigrations.apply(resourceName, disk, bundled, fromVersion, toVersion, mergeMissingKeys);
        if (MAIN_CONFIG_RESOURCE.equals(resourceName)) {
            disk.put("version", toVersion);
        }
        write(target, disk);
    }

    /**
     * Backs up {@code resourceName} then overwrites it with the bundled jar
     * copy verbatim (a raw byte copy, not a parse/re-dump - so it also keeps
     * whatever comments the bundled file has, unlike {@link #write}). Used
     * for localization files instead of {@link #migrateOne}'s merge, so a
     * new release's translation actually replaces the old one on disk
     * rather than only ever gaining newly-added keys.
     */
    private void replaceWithBundled(String resourceName) {
        File target = new File(plugin.getDataFolder(), resourceName);
        if (!target.exists()) return;

        try (InputStream in = plugin.getResource(resourceName)) {
            if (in == null) return; // not actually bundled under this name - nothing to replace with
            backup(target);
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to replace " + resourceName + " with the bundled version: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBundled(String resourceName) {
        try (InputStream in = plugin.getResource(resourceName)) {
            if (in == null) return Map.of();
            Object loaded = yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return loaded instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        } catch (IOException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDisk(File file) {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            Object loaded = yaml.load(reader);
            return loaded instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private String readVersion(Map<?, ?> map) {
        Object v = map.get("version");
        return v != null ? String.valueOf(v) : UNKNOWN_VERSION;
    }

    private void backup(File target) {
        File backup = new File(target.getParentFile(), target.getName() + ".bak." + System.currentTimeMillis());
        try {
            Files.copy(target.toPath(), backup.toPath());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to back up " + target.getName() + " before migration: " + e.getMessage());
        }
    }

    private void write(File target, Map<String, Object> content) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml writer = new Yaml(options);
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
            writer.dump(content, out);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write migrated " + target.getName() + ": " + e.getMessage());
        }
    }
}
