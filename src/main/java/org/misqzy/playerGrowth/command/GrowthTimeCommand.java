package org.misqzy.playerGrowth.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.misqzy.playerGrowth.PlayerGrowth;

import java.util.Map;

public class GrowthTimeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("&cOnly player can use this command.");
            return true;
        }
        long totalSeconds = PlayerGrowth.getInstance().growthManager.calculateUntilGrowthEndTimeSeconds(player);

        TimeParts parts = splitSeconds(totalSeconds);

        String timeMessage = formatTimeMessage(parts);

        Component message = PlayerGrowth.getInstance().messageManager.getMessage(
                "time-until-growth-end-command", Map.of("time", timeMessage));

        player.sendMessage(message);

        return true;
    }

    private static class TimeParts {
        long days;
        long hours;
        long minutes;
        long seconds;
    }

    private TimeParts splitSeconds(long totalSeconds) {
        TimeParts parts = new TimeParts();
        parts.days = totalSeconds / (24 * 3600);
        long remainder = totalSeconds % (24 * 3600);
        parts.hours = remainder / 3600;
        remainder %= 3600;
        parts.minutes = remainder / 60;
        parts.seconds = remainder % 60;
        return parts;
    }

    private String formatTimeMessage(TimeParts parts) {
        StringBuilder sb = new StringBuilder();

        if (parts.days > 0) {
            sb.append(parts.days).append(" ").append(PlayerGrowth.getInstance().messageManager.getRaw("days"));
        }

        if (parts.hours > 0) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(parts.hours).append(" ").append(PlayerGrowth.getInstance().messageManager.getRaw("hours"));
        }

        if (parts.minutes > 0) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(parts.minutes).append(" ").append(PlayerGrowth.getInstance().messageManager.getRaw("mins"));
        }

        if (!sb.isEmpty()) {
            sb.append(" ");
        }
        sb.append(parts.seconds).append(" ").append(PlayerGrowth.getInstance().messageManager.getRaw("seconds"));

        return sb.toString().trim();
    }
}
