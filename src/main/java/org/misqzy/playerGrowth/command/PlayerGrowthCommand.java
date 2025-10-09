package org.misqzy.playerGrowth.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.misqzy.playerGrowth.PlayerGrowth;

import org.misqzy.playerGrowth.lang.MessageManager;
import org.misqzy.playerGrowth.config.ConfigManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PlayerGrowthCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "help");
    private PlayerGrowth plugin;


    public PlayerGrowthCommand() {
        this.plugin = PlayerGrowth.getInstance();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(plugin.messageManager.getMessage("help-command",
                    Map.of(
                            "command", label,
                            "subcommand", "reload"
                    )));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("playergrowth.reload")) {
                sender.sendMessage(plugin.messageManager.getMessage("not-enough-permission"));
                return true;
            }

            try {
                reloadPlugin();

                sender.sendMessage(plugin.messageManager.getMessage("plugin-reloaded"));
                return true;
            } catch (Exception e){
                sender.sendMessage(plugin.messageManager.getMessage("plugin-reload-error"));
                plugin.getLogger().severe(e.getMessage());
                return false;
            }
        }

        sender.sendMessage(plugin.messageManager.getMessage("unknown-command"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();

            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(input)) {
                    suggestions.add(sub);
                }
            }
            return suggestions;
        }

        return List.of();
    }

    private void reloadPlugin() {
            plugin.reloadConfig();
            ConfigManager newConfig = new ConfigManager(plugin);
            plugin.configManager = newConfig;
            plugin.messageManager = new MessageManager(plugin, newConfig.getLanguage());
            plugin.growthManager.updateConfig(newConfig);
            plugin.growthManager.updateAllPlayersScale();
    }
}

