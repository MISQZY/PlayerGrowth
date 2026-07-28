package org.misqzy.playergrowth.bukkit.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.misqzy.playergrowth.bukkit.PlayerGrowthMessages;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;

import java.util.function.Consumer;

/**
 * Shared "this subcommand only makes sense for an online player" guard -
 * every {@code *Own} variant across {@link HeightCommand}, {@link GenderCommand},
 * and {@link GrowthCommand} needs the same check (a console/command-block
 * sender has no height/gender/growth of its own to act on), so it lives
 * here once instead of each command class reimplementing the same
 * {@code instanceof Player} + {@code command.players-only} fallback.
 */
final class CommandGuards {

    private CommandGuards() {}

    static void requirePlayer(PlayerGrowthCore core, CommandSender sender, Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            PlayerGrowthMessages.send(core, sender, "command.players-only");
        }
    }
}
