package org.misqzy.playergrowth.paper.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.misqzy.playergrowth.common.lang.TimeFormatter;
import org.misqzy.playergrowth.paper.PlayerGrowthMessages;
import org.misqzy.playergrowth.paper.PlayerGrowthPlugin;

import java.util.Map;

/** Administrative commands - currently just {@code /playergrowth reload}. */
public final class PluginAdminCommand {

    private final PlayerGrowthPlugin plugin;

    public PluginAdminCommand(PlayerGrowthPlugin plugin) {
        this.plugin = plugin;
    }

    @Command("playergrowth reload")
    @CommandDescription("Reload the PlayerGrowth configuration.")
    @Permission("playergrowth.admin.reload")
    public void reload(CommandSourceStack source) {
        plugin.reload(time -> PlayerGrowthMessages.send(plugin.core(), source.getSender(), "admin.reloaded",
                Map.of("time", TimeFormatter.formatMillis(time))));
    }
}
