package org.misqzy.flectonegrowth.bukkit.command;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.misqzy.flectonegrowth.bukkit.FlectoneGrowthMessages;
import org.misqzy.flectonegrowth.bukkit.FlectoneGrowthPlugin;
import org.misqzy.flectonegrowth.common.lang.TimeFormatter;

import java.util.Map;

/** Administrative commands - currently just {@code /flectonegrowth reload}. */
public final class PluginAdminCommand {

    private final FlectoneGrowthPlugin plugin;

    public PluginAdminCommand(FlectoneGrowthPlugin plugin) {
        this.plugin = plugin;
    }

    @Command("flectonegrowth|pg reload")
    @CommandDescription("Reload the FlectoneGrowth configuration.")
    @Permission("flectonegrowth.admin.reload")
    public void reload(CommandSender sender) {
        plugin.reload(time -> FlectoneGrowthMessages.send(plugin.core(), sender, "admin.reloaded",
                Map.of("time", TimeFormatter.formatMillis(time))));
    }
}
