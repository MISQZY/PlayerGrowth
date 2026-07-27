package org.misqzy.playergrowth.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playergrowth.common.config.ConfigView;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.network.NetworkMessenger;
import org.misqzy.playergrowth.common.platform.PlatformPlayer;
import org.misqzy.playergrowth.common.platform.PlayerLookup;
import org.misqzy.playergrowth.common.platform.Scheduler;
import org.misqzy.playergrowth.bukkit.api.PlayerGrowthAPI;
import org.misqzy.playergrowth.bukkit.api.PlayerGrowthAPIImpl;
import org.misqzy.playergrowth.bukkit.command.CommandRegistry;
import org.misqzy.playergrowth.bukkit.config.ConfigMigrator;
import org.misqzy.playergrowth.bukkit.config.ResourceInstaller;
import org.misqzy.playergrowth.bukkit.config.ServerIdProvisioner;
import org.misqzy.playergrowth.bukkit.config.YamlFileLoader;
import org.misqzy.playergrowth.bukkit.integration.FlectonePulseColorResolver;
import org.misqzy.playergrowth.bukkit.integration.FlectonePulseServerIdResolver;
import org.misqzy.playergrowth.bukkit.integration.PlaceholderAPIHook;
import org.misqzy.playergrowth.bukkit.listener.PlayerConnectionListener;
import org.misqzy.playergrowth.bukkit.network.BukkitNetworkMessenger;

import java.io.File;
import java.util.List;
import java.util.UUID;

public final class PlayerGrowthPlugin extends JavaPlugin {

    private PlayerGrowthCore core;
    private Scheduler scheduler;
    private GrowthTicker ticker;
    private BukkitNetworkMessenger networkMessenger;
    private PlaceholderAPIHook placeholderHook;

    @Override
    public void onEnable() {
        new ResourceInstaller(this).installDefaults();
        new ConfigMigrator(this).migrateIfNeeded();

        core = bootstrapCore();

        Bukkit.getServicesManager().register(PlayerGrowthAPI.class, new PlayerGrowthAPIImpl(core), this, ServicePriority.Normal);

        Bukkit.getPluginManager().registerEvents(
                new PlayerConnectionListener(this, core.growthEngine()), this);

        new CommandRegistry(this, core);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderHook = new PlaceholderAPIHook(core);
            placeholderHook.register();
        }

        ticker = new GrowthTicker(scheduler, core.growthEngine());
        ticker.start();
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (ticker != null) ticker.stop();
        if (placeholderHook != null) placeholderHook.unregister();
        if (networkMessenger != null) networkMessenger.unregister();
        if (core != null) core.shutdown();
    }

    /**
     * Re-reads config/gender/messages from disk and hot-swaps them into the
     * running engine, then re-derives every online player's cached growth
     * state from the new config - without that last step, growth-setting
     * changes (e.g. {@code growth.time-minutes}) silently only took effect
     * for players who relog after the reload, not the ones already online.
     *
     * <p>Re-runs {@link ResourceInstaller} first, same as {@link #onEnable()}:
     * if an admin deletes a bundled resource (e.g. the whole
     * {@code localizations/} folder) after startup and then reloads, this
     * recreates it from the jar instead of {@code loadMessages} throwing an
     * {@code IllegalStateException} on a now-missing file - idempotent, so
     * it never touches a resource that's still present.</p>
     */
    public void reload() {
        new ResourceInstaller(this).installDefaults();
        new ConfigMigrator(this).migrateIfNeeded();

        ConfigView mainConfig = loadConfig("config.yml");
        FlectonePulseColorResolver.logDiagnostics(this);
        core.reload(mainConfig, loadConfig("gender.yml"), loadMessages(mainConfig), FlectonePulseColorResolver::resolveDefaultColors);
        if (ticker != null) ticker.restart();

        List<PlatformPlayer> online = Bukkit.getOnlinePlayers().stream()
                .<PlatformPlayer>map(BukkitPlayerAdapter::new)
                .toList();
        core.growthEngine().refreshAfterReload(online);
    }

    public PlayerGrowthCore core() {
        return core;
    }

    private PlayerGrowthCore bootstrapCore() {
        ConfigView mainConfig = loadConfig("config.yml");

        scheduler = new BukkitScheduler(this);

        NetworkMessenger messenger;
        if (mainConfig.getBoolean("network.sync-enabled", false)) {
            networkMessenger = new BukkitNetworkMessenger(this);
            networkMessenger.register();
            messenger = networkMessenger;
        } else {
            messenger = NetworkMessenger.DISABLED;
        }

        PlayerLookup lookup = this::findOnline;

        String configuredServerId = ServerIdProvisioner.resolveOrGenerate(this, mainConfig);
        String serverId = FlectonePulseServerIdResolver.resolve(this, configuredServerId);
        ServerIdProvisioner.persistIfChanged(this, configuredServerId, serverId);
        BukkitPlatform platform = new BukkitPlatform(this, scheduler, serverId);
        BukkitModule bukkitModule = new BukkitModule(scheduler, messenger, lookup);

        FlectonePulseColorResolver.logDiagnostics(this);
        return PlayerGrowthCore.bootstrap(platform, bukkitModule, mainConfig, loadConfig("gender.yml"), loadMessages(mainConfig),
                FlectonePulseColorResolver::resolveDefaultColors);
    }

    private PlatformPlayer findOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? new BukkitPlayerAdapter(player) : null;
    }

    private ConfigView loadConfig(String fileName) {
        return YamlFileLoader.load(new File(getDataFolder(), fileName));
    }

    private ConfigView loadMessages(ConfigView mainConfig) {
        String locale = mainConfig.getString("locale", "en");
        File file = new File(getDataFolder(), "localizations/messages_" + locale + ".yml");
        if (!file.exists()) file = new File(getDataFolder(), "localizations/messages_en.yml");
        return YamlFileLoader.load(file);
    }
}
