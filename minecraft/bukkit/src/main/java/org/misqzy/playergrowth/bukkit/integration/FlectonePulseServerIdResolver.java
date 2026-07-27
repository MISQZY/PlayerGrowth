package org.misqzy.playergrowth.bukkit.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playergrowth.bukkit.config.YamlFileLoader;

import java.io.File;

/**
 * If FlectonePulse is installed and enabled, its own {@code config.yml}
 * carries a network-wide {@code server} identifier (added in FlectonePulse
 * 1.10.0, used there for cross-server ban/mute/warn filtering - see
 * {@code net.flectone.pulse.config.Config#server}) that serves exactly the
 * same purpose as PlayerGrowth's own {@code network.server}. Reusing it
 * instead of a separately-configured PlayerGrowth value keeps the two plugins from
 * silently disagreeing about which backend server they're each running on
 * when both are present in the same network.
 *
 * <p>Despite FlectonePulse's changelog calling this a "server UUID", the
 * underlying config field is a plain {@code String} (not a parsed
 * {@link java.util.UUID}) - confirmed by reading FlectonePulse's own
 * {@code Config.java} record, which declares {@code String server}. It's
 * whatever free-form value the admin put in FlectonePulse's config, so it's
 * treated as an opaque string here too.</p>
 *
 * <p>This reads FlectonePulse's config.yml directly off disk via its own
 * {@link Plugin#getDataFolder()} rather than calling into any FlectonePulse
 * Java API - FlectonePulse doesn't publish one for this, and depending on
 * its internal classes would break on every FlectonePulse update.</p>
 */
public final class FlectonePulseServerIdResolver {

    private static final String PLUGIN_NAME = "FlectonePulse";

    private FlectonePulseServerIdResolver() {}

    /** Returns FlectonePulse's configured server id, or {@code fallback} if FlectonePulse isn't present/configured. */
    public static String resolve(JavaPlugin plugin, String fallback) {
        Plugin flectonePulse = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (flectonePulse == null || !flectonePulse.isEnabled()) return fallback;

        File configFile = new File(flectonePulse.getDataFolder(), "config.yml");
        if (!configFile.exists()) return fallback;

        try {
            String serverId = YamlFileLoader.load(configFile).getString("server", "");
            if (serverId == null || serverId.isBlank()) return fallback;

            plugin.getLogger().info("Using FlectonePulse's configured server id (\"" + serverId
                    + "\") instead of network.server from PlayerGrowth's own config.yml.");
            return serverId;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Found FlectonePulse but could not read its config.yml server id: "
                    + e.getMessage() + " - falling back to PlayerGrowth's own network.server.");
            return fallback;
        }
    }
}
