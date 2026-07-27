package org.misqzy.playergrowth.paper.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.misqzy.playergrowth.paper.PlayerGrowthPlugin;

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
        plugin.reload();
        source.getSender().sendMessage(plugin.core().messages().get("admin.reloaded"));
    }
}
