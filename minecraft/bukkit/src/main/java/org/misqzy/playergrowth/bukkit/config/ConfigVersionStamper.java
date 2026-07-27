package org.misqzy.playergrowth.bukkit.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playergrowth.common.config.ConfigView;

import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps config.yml's {@code plugin-version} in sync with the actually
 * running plugin version. Deliberately independent of {@code config-version}
 * (the unrelated schema/migration counter {@link ConfigMigrator} owns, which
 * only changes when config.yml's shape does) - the plugin's own semver
 * changes on every release, so an already-installed server's config.yml
 * would otherwise keep whatever version string it was first generated
 * under forever ({@link org.misqzy.playergrowth.common.config.migration.ConfigMigrations}'s
 * missing-key merge only adds keys the disk file lacks, it never overwrites
 * one that's already there).
 *
 * <p>Same targeted single-line replace-or-insert tradeoff as
 * {@link ServerIdProvisioner}: avoids a full SnakeYAML re-dump (and the
 * comment loss that comes with it) just to keep one informational field
 * current.</p>
 */
public final class ConfigVersionStamper {

    private ConfigVersionStamper() {}

    public static void stamp(JavaPlugin plugin, ConfigView mainConfig, String runningVersion) {
        String onDisk = mainConfig.getString("plugin-version", "");
        if (runningVersion.equals(onDisk)) return;

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>(lines.size() + 1);
            boolean replaced = false;

            for (String line : lines) {
                if (!replaced && line.matches("^plugin-version:\\s*.*$")) {
                    updated.add("plugin-version: '" + runningVersion + "'");
                    replaced = true;
                } else {
                    updated.add(line);
                }
            }
            if (!replaced) updated.add(0, "plugin-version: '" + runningVersion + "'");

            Files.write(configFile.toPath(), updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to stamp plugin-version into config.yml: " + e.getMessage());
        }
    }
}
