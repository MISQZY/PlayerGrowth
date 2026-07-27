package org.misqzy.playergrowth.paper;

import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playergrowth.common.platform.Platform;
import org.misqzy.playergrowth.common.platform.Scheduler;

import java.io.File;
import java.util.logging.Logger;

public final class PaperPlatform implements Platform {

    private final JavaPlugin plugin;
    private final Scheduler scheduler;
    private final String serverId;

    public PaperPlatform(JavaPlugin plugin, Scheduler scheduler, String serverId) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.serverId = serverId;
    }

    @Override public String name() { return "Paper"; }
    @Override public String serverId() { return serverId; }
    @Override public File dataFolder() { return plugin.getDataFolder(); }
    @Override public Logger logger() { return plugin.getLogger(); }
    @Override public Scheduler scheduler() { return scheduler; }
}
