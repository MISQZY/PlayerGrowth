package org.misqzy.playerGrowth.command;

import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.misqzy.playerGrowth.PlayerGrowth;

import java.util.Map;

public class HeightCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("&cOnly player can use this command.");
            return true;
        }

        Double currentScale = player.getAttribute(Attribute.SCALE).getBaseValue();

        Component message = PlayerGrowth.getInstance().messageManager.getMessage(
                "height-command",
                Map.of("height", String.format("%.2f", currentScale))
        );

        player.sendMessage(message);

        return true;
    }
}
