package org.misqzy.playerGrowth.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.misqzy.playerGrowth.PlayerGrowth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SetHeightCommand implements CommandExecutor, TabCompleter {
    
    private final PlayerGrowth plugin;
    
    public SetHeightCommand() {
        this.plugin = PlayerGrowth.getInstance();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        
        if (args.length == 0) {
            sender.sendMessage(plugin.messageManager.getMessage("setheight-usage",
                    Map.of("command", label)));
            return true;
        }
        
        Player target;
        String heightArg;
        
        boolean isSelfTarget = false;
        
        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.messageManager.getMessage("setheight-usage",
                        Map.of("command", label)));
                return true;
            }
            target = (Player) sender;
            heightArg = args[0];
            isSelfTarget = true;
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.messageManager.getMessage("player-not-found",
                        Map.of("player", args[0])));
                return true;
            }
            heightArg = args[1];
            isSelfTarget = sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId());
        }
        
        String requiredPermission = isSelfTarget ? "playergrowth.setheight.own" : "playergrowth.setheight.other";
        if (!sender.hasPermission(requiredPermission)) {
            sender.sendMessage(plugin.messageManager.getMessage("not-enough-permission"));
            return true;
        }
        
        if (heightArg.equalsIgnoreCase("remove") || heightArg.equalsIgnoreCase("reset")) {
            CompletableFuture<Boolean> result = plugin.growthManager.removeCustomScale(target);
            result.thenAccept(success -> {
                if (success) {
                    sender.sendMessage(plugin.messageManager.getMessage("setheight-removed",
                            Map.of("player", target.getName())));
                } else {
                    sender.sendMessage(plugin.messageManager.getMessage("setheight-remove-failed",
                            Map.of("player", target.getName())));
                }
            });
            return true;
        }
        
        try {
            double scale = Double.parseDouble(heightArg);
            double minScale = plugin.growthManager.getMinScale();
            double maxScale = plugin.growthManager.getMaxScale();
            
            if (scale < minScale || scale > maxScale) {
                sender.sendMessage(plugin.messageManager.getMessage("setheight-out-of-range",
                        Map.of(
                                "min", String.format("%.2f", minScale),
                                "max", String.format("%.2f", maxScale)
                        )));
                return true;
            }
            
            CompletableFuture<Boolean> result = plugin.growthManager.setCustomScale(target, scale);
            result.thenAccept(success -> {
                if (success) {
                    sender.sendMessage(plugin.messageManager.getMessage("setheight-success",
                            Map.of(
                                    "player", target.getName(),
                                    "scale", String.format("%.2f", scale)
                            )));
                } else {
                    sender.sendMessage(plugin.messageManager.getMessage("setheight-failed",
                            Map.of("player", target.getName())));
                }
            });
            
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.messageManager.getMessage("setheight-invalid-number",
                    Map.of("value", heightArg)));
            return true;
        }
        
        return true;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        
        boolean hasOwnPermission = sender.hasPermission("playergrowth.setheight.own");
        boolean hasOtherPermission = sender.hasPermission("playergrowth.setheight.other");
        
        if (!hasOwnPermission && !hasOtherPermission) {
            return List.of();
        }
        
        if (args.length == 1) {
            if (sender instanceof Player && hasOwnPermission) {
                String input = args[0].toLowerCase();
                List<String> suggestions = new ArrayList<>();
                
                if ("remove".startsWith(input) || "reset".startsWith(input)) {
                    suggestions.add("remove");
                }
                
                double minScale = plugin.growthManager.getMinScale();
                double maxScale = plugin.growthManager.getMaxScale();
                for (double scale = minScale; scale <= maxScale; scale += 0.1) {
                    String scaleStr = String.format("%.1f", scale);
                    if (scaleStr.startsWith(input)) {
                        suggestions.add(scaleStr);
                    }
                }
                
                if (hasOtherPermission) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(input)) {
                            suggestions.add(player.getName());
                        }
                    }
                }
                
                return suggestions;
            } else if (hasOtherPermission) {
                List<String> players = new ArrayList<>();
                String input = args[0].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        players.add(player.getName());
                    }
                }
                return players;
            }
        }
        
        if (args.length == 2 && hasOtherPermission) {
            String input = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            
            if ("remove".startsWith(input) || "reset".startsWith(input)) {
                suggestions.add("remove");
            }
            
            double minScale = plugin.growthManager.getMinScale();
            double maxScale = plugin.growthManager.getMaxScale();
            for (double scale = minScale; scale <= maxScale; scale += 0.1) {
                String scaleStr = String.format("%.1f", scale);
                if (scaleStr.startsWith(input)) {
                    suggestions.add(scaleStr);
                }
            }
            
            return suggestions;
        }
        
        return List.of();
    }
}

