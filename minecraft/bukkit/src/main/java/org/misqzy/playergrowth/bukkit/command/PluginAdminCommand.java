package org.misqzy.playergrowth.bukkit.command;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.misqzy.playergrowth.bukkit.PlayerGrowthMessages;
import org.misqzy.playergrowth.bukkit.PlayerGrowthPlugin;
import org.misqzy.playergrowth.common.lang.TimeFormatter;

import java.util.Map;

/** Administrative commands - currently just {@code /playergrowth reload}. */
public final class PluginAdminCommand {

    private final PlayerGrowthPlugin plugin;

    public PluginAdminCommand(PlayerGrowthPlugin plugin) {
        this.plugin = plugin;
    }

    @Command("playergrowth|pg reload")
    @CommandDescription("Reload the PlayerGrowth configuration.")
    @Permission("playergrowth.admin.reload")
    public void reload(CommandSender sender) {
        plugin.reload(time -> PlayerGrowthMessages.send(plugin.core(), sender, "admin.reloaded",
                Map.of("time", TimeFormatter.formatMillis(time))));
    }
}
