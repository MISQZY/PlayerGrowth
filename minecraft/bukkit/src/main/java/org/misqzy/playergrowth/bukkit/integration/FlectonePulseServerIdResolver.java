package org.misqzy.playergrowth.bukkit.integration;

import net.flectone.pulse.util.file.FileFacade;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playergrowth.bukkit.config.YamlFileLoader;
import org.misqzy.playergrowth.common.config.ConfigView;

import java.io.File;

/**
 * If FlectonePulse is installed and enabled, its own config carries a
 * network-wide {@code server} identifier (added in FlectonePulse 1.10.0,
 * used there for cross-server ban/mute/warn filtering - see
 * {@code net.flectone.pulse.config.Config#server}) that serves exactly the
 * same purpose as PlayerGrowth's own top-level {@code server} key. Reusing it
 * instead of a separately-configured PlayerGrowth value keeps the two
 * plugins from silently disagreeing about which backend server they're each
 * running on when both are present in the same network.
 *
 * <p>Despite FlectonePulse's changelog calling this a "server UUID", the
 * underlying config field is a plain {@code String} (not a parsed
 * {@link java.util.UUID}) - confirmed by reading FlectonePulse's own
 * {@code Config.java} record, which declares {@code String server}. It's
 * whatever free-form value the admin put in FlectonePulse's config, so it's
 * treated as an opaque string here too.</p>
 *
 * <p>Prefers reading it through {@link FlectonePulseAccess} (FlectonePulse's
 * own live, parsed config, via its Java API) over reading its config.yml
 * directly off disk - the API path can't drift out of sync with an
 * in-memory {@code /flectonepulse reload}, and doesn't need this class to
 * re-implement FlectonePulse's own YAML parsing. The file read is kept as a
 * fallback for FlectonePulse versions that predate this API.</p>
 */
public final class FlectonePulseServerIdResolver {

    private static final String PLUGIN_NAME = "FlectonePulse";

    private FlectonePulseServerIdResolver() {}

    /**
     * Returns FlectonePulse's configured server id, or {@code fallback} if
     * FlectonePulse isn't present/configured, or if {@code integrations}
     * has {@code flectonepulse.enabled}/{@code flectonepulse.server-id} set
     * to {@code false}.
     */
    public static String resolve(JavaPlugin plugin, String fallback, ConfigView integrations) {
        if (!integrations.getBoolean("flectonepulse.enabled", true)
                || !integrations.getBoolean("flectonepulse.server-id", true)) {
            return fallback;
        }

        String viaApi = resolveViaApi();
        if (viaApi != null) {
            plugin.getLogger().info("Using FlectonePulse's configured server id (\"" + viaApi
                    + "\") instead of the server key from PlayerGrowth's own config.yml.");
            return viaApi;
        }

        return resolveViaConfigFile(plugin, fallback);
    }

    private static String resolveViaApi() {
        FileFacade fileFacade = FlectonePulseAccess.tryGetFileFacade();
        if (fileFacade == null) return null;

        String serverId = fileFacade.config().server();
        return (serverId == null || serverId.isBlank()) ? null : serverId;
    }

    private static String resolveViaConfigFile(JavaPlugin plugin, String fallback) {
        Plugin flectonePulse = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (flectonePulse == null || !flectonePulse.isEnabled()) return fallback;

        File configFile = new File(flectonePulse.getDataFolder(), "config.yml");
        if (!configFile.exists()) return fallback;

        try {
            String serverId = YamlFileLoader.load(configFile).getString("server", "");
            if (serverId == null || serverId.isBlank()) return fallback;

            plugin.getLogger().info("Using FlectonePulse's configured server id (\"" + serverId
                    + "\") instead of the server key from PlayerGrowth's own config.yml.");
            return serverId;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Found FlectonePulse but could not read its config.yml server id: "
                    + e.getMessage() + " - falling back to PlayerGrowth's own server key.");
            return fallback;
        }
    }
}
