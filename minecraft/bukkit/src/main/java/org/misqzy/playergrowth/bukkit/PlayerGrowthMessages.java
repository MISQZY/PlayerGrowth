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
 *
 * <p>A {@link Player} sender's message locale is picked from their own
 * Minecraft client locale ({@link Player#getLocale()}, e.g. {@code "ru_ru"} -
 * only the language segment before the underscore is used) when a matching
 * {@code localizations/messages_<code>.yml} is loaded, falling back to
 * config.yml's configured {@code locale} otherwise (console senders always
 * use the configured locale - there's no client locale to read). This is
 * also what FlectonePulse's own translate module does under the hood when
 * its {@code language.by-player} setting is on (verified by decompiling its
 * real jar - it has no per-player language of its own to read either), so
 * this stays consistent with it without needing any FlectonePulse API.</p>
 */
public final class PlayerGrowthMessages {

    private PlayerGrowthMessages() {}

    public static void send(PlayerGrowthCore core, CommandSender sender, String key) {
        send(core, sender, key, Map.of());
    }

    public static void send(PlayerGrowthCore core, CommandSender sender, String key, Map<String, Object> placeholders) {
        Messages messages = core.messages();

        if (sender instanceof Player player) {
            String locale = clientLocale(messages, player);
            if (tryFlectonePulseSend(player, messages, locale, key, placeholders)) return;
            LegacyText.send(sender, messages.get(locale, key, placeholders));
            return;
        }

        LegacyText.send(sender, messages.get(key, placeholders));
    }

    /** {@code player}'s client locale's language segment (e.g. {@code "ru_ru"} -> {@code "ru"}) if a matching translation file is loaded, else config.yml's configured default. */
    private static String clientLocale(Messages messages, Player player) {
        String raw = player.getLocale();
        if (raw == null || raw.isBlank()) return null;

        int separator = raw.indexOf('_');
        String language = (separator > 0 ? raw.substring(0, separator) : raw).toLowerCase();
        return messages.hasLocale(language) ? language : null;
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
    private static boolean tryFlectonePulseSend(Player player, Messages messages, String locale, String key, Map<String, Object> placeholders) {
        try {
            return FlectonePulseMessageDispatcher.trySend(player, messages.rawForDispatch(locale, key), messages.externalDispatchResolvers(placeholders));
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
