package org.misqzy.playergrowth.paper.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playergrowth.common.config.ConfigView;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves this server's {@code network.server} identifier, generating and
 * persisting a random UUID the first time the field is blank (e.g. a fresh
 * install) so the value stays stable across restarts instead of being
 * re-derived - and re-randomised - on every startup.
 *
 * <p>Writes the generated value back into {@code config.yml} with a
 * targeted line replacement rather than a full SnakeYAML re-dump, so a
 * brand new install doesn't immediately lose all of the bundled file's
 * comments the very first time it starts (same tradeoff as
 * {@link ConfigMigrator}, which only rewrites the whole file on an actual
 * {@code config-version} bump, not on every run).</p>
 */
public final class ServerIdProvisioner {

    private ServerIdProvisioner() {}

    public static String resolveOrGenerate(JavaPlugin plugin, ConfigView mainConfig) {
        String existing = mainConfig.getString("network.server", "");
        if (existing != null && !existing.isBlank()) return existing;

        String generated = UUID.randomUUID().toString();
        persist(plugin, generated);
        return generated;
    }

    /**
     * Writes {@code value} into {@code config.yml}'s {@code network.server}
     * if it differs from {@code current} - a no-op otherwise. Used to make
     * FlectonePulse's server id (see {@link org.misqzy.playergrowth.paper.integration.FlectonePulseServerIdResolver})
     * stick permanently in PlayerGrowth's own config once found, instead of
     * only overriding it in memory for the running session and re-resolving
     * it (redundantly, but harmlessly) on every future restart.
     */
    public static void persistIfChanged(JavaPlugin plugin, String current, String value) {
        if (value.equals(current)) return;
        persist(plugin, value);
    }

    /**
     * Replaces an existing {@code server:} line inside the {@code network:}
     * section if there is one (the common case - the bundled default already
     * has one), otherwise inserts one - handling upgrades from a version
     * that only had the old {@code network.server-id} key (left behind
     * untouched; nothing reads it anymore) and never had {@code server:} at
     * all. Verified against both shapes by hand before relying on the regex
     * here - see the comment in {@code docs/PROJECT_STATUS.md}.
     */
    private static void persist(JavaPlugin plugin, String serverId) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>(lines.size() + 2);
            boolean inNetworkSection = false;
            boolean foundNetworkSection = false;
            boolean replaced = false;

            for (String line : lines) {
                if (!replaced && line.matches("^network:\\s*$")) {
                    inNetworkSection = true;
                    foundNetworkSection = true;
                    updated.add(line);
                    continue;
                }
                if (inNetworkSection && !replaced) {
                    if (line.matches("^\\s+server:\\s*.*$")) {
                        String indent = line.substring(0, line.indexOf("server:"));
                        updated.add(indent + "server: '" + serverId + "'");
                        replaced = true;
                        continue;
                    }
                    if (!line.matches("^\\s.*")) {
                        // Left the network: section (dedented to a new top-level key) without finding server:.
                        updated.add("  server: '" + serverId + "'");
                        replaced = true;
                        inNetworkSection = false;
                    }
                }
                updated.add(line);
            }

            if (inNetworkSection && !replaced) {
                // network: was the last section in the file.
                updated.add("  server: '" + serverId + "'");
                replaced = true;
            }
            if (!foundNetworkSection) {
                updated.add("network:");
                updated.add("  server: '" + serverId + "'");
            }

            Files.write(configFile.toPath(), updated, StandardCharsets.UTF_8);
            plugin.getLogger().info("Saved network.server = \"" + serverId + "\" to config.yml.");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to persist network.server to config.yml: " + e.getMessage());
        }
    }
}
