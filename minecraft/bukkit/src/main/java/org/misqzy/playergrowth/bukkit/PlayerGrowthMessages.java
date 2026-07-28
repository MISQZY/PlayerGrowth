package org.misqzy.playergrowth.bukkit;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.misqzy.playergrowth.bukkit.integration.FlectonePulseMessageDispatcher;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.lang.Messages;

import java.util.Map;

/**
 * Single entry point every command class sends a message through, instead
 * of calling {@code core.messages().get(...)} + {@code LegacyText.send(...)}
 * directly. If {@code sender} is an online {@link Player} and FlectonePulse
 * is installed and tracking them, the message is routed through
 * {@link FlectonePulseMessageDispatcher} instead - so {@code <fcolor:N>} and
 * delivery both go through FlectonePulse itself, matching how its own
 * messages behave, rather than this plugin reimplementing either. Any other
 * sender (console, or a player FlectonePulse doesn't have ready), or
 * FlectonePulse simply not being installed, falls back to this plugin's own
 * {@code Messages} + {@link LegacyText} rendering unchanged.
 */
public final class PlayerGrowthMessages {

    private PlayerGrowthMessages() {}

    public static void send(PlayerGrowthCore core, CommandSender sender, String key) {
        send(core, sender, key, Map.of());
    }

    public static void send(PlayerGrowthCore core, CommandSender sender, String key, Map<String, Object> placeholders) {
        Messages messages = core.messages();

        if (sender instanceof Player player && tryFlectonePulseSend(player, messages, key, placeholders)) {
            return;
        }

        LegacyText.send(sender, messages.get(key, placeholders));
    }

    /**
     * Guards the call into {@link FlectonePulseMessageDispatcher} itself, not
     * just its body - a real live-deploy crash: {@code trySend}'s own
     * internal try/catch only covers failures while its bytecode is
     * *executing*, but with FlectonePulse not installed, the JVM fails to
     * *link/verify* the {@link FlectonePulseMessageDispatcher} class the
     * first time any of its methods is actively called at all (its bytecode
     * needs FlectonePulse's {@code FEntity} type resolvable to type-check
     * {@code MessageContext.Builder#sender(FEntity)}/{@code receiver(FEntity)}).
     * That failure - a {@link LinkageError} - is thrown at the call site in
     * the *caller*, before a single instruction of {@code trySend} ever
     * runs, so no try/catch inside that method can ever see it. Same root
     * cause as {@code FlectonePulseAccess.tryGetFileFacade}'s fix, one class
     * over.
     */
    private static boolean tryFlectonePulseSend(Player player, Messages messages, String key, Map<String, Object> placeholders) {
        try {
            return FlectonePulseMessageDispatcher.trySend(player, messages.rawForDispatch(key), messages.externalDispatchResolvers(placeholders));
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
