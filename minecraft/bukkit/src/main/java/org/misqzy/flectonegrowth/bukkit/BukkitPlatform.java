package org.misqzy.flectonegrowth.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.flectonegrowth.common.platform.Platform;
import org.misqzy.flectonegrowth.common.platform.Scheduler;

import java.io.File;
import java.util.logging.Logger;

public final class BukkitPlatform implements Platform {

    private final JavaPlugin plugin;
    private final Scheduler scheduler;
    private final String serverId;

    public BukkitPlatform(JavaPlugin plugin, Scheduler scheduler, String serverId) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.serverId = serverId;
    }

    @Override
    public String name() {
        return "Bukkit";
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public File dataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public Logger logger() {
        return plugin.getLogger();
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }
}
