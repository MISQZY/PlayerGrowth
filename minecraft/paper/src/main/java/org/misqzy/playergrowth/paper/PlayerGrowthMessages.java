package org.misqzy.playergrowth.paper;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.lang.Messages;
import org.misqzy.playergrowth.paper.integration.FlectonePulseMessageDispatcher;

import java.util.Map;

/**
 * Single entry point every command class sends a message through, instead
 * of calling {@code core.messages().get(...)} + {@code sender.sendMessage(...)}
 * directly. If {@code sender} is an online {@link Player} and FlectonePulse
 * is installed and tracking them, the message is routed through
 * {@link FlectonePulseMessageDispatcher} instead - so {@code <fcolor:N>} and
 * delivery both go through FlectonePulse itself, matching how its own
 * messages behave, rather than this plugin reimplementing either. Any other
 * sender (console, or a player FlectonePulse doesn't have ready), or
 * FlectonePulse simply not being installed, falls back to this plugin's own
 * {@code Messages} rendering unchanged.
 */
public final class PlayerGrowthMessages {

    private PlayerGrowthMessages() {}

    public static void send(PlayerGrowthCore core, CommandSender sender, String key) {
        send(core, sender, key, Map.of());
    }

    public static void send(PlayerGrowthCore core, CommandSender sender, String key, Map<String, Object> placeholders) {
        Messages messages = core.messages();

        if (sender instanceof Player player
                && FlectonePulseMessageDispatcher.trySend(player, messages.rawForDispatch(key), messages.externalDispatchResolvers(placeholders))) {
            return;
        }

        sender.sendMessage(messages.get(key, placeholders));
    }
}
