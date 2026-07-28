package org.misqzy.playergrowth.bukkit;

import org.bukkit.command.CommandSender;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;

import java.util.Map;

/**
 * Base for any feature that sends an in-game notification to a permitted
 * {@link CommandSender} through {@link PlayerGrowthMessages}. Centralizes
 * the "should this sender receive it right now" gate - a permission check
 * plus a live-read enabled flag - so each concrete notifier only supplies
 * its permission node, its {@link #enabled()} check, and the actual message
 * key/placeholders, instead of re-implementing the same two guards.
 *
 * <p>{@link #enabled()} is read live on every {@link #notify} call, not
 * cached at construction: see {@link UpdateNotifier} for why that matters -
 * a config toggle flipped via {@code /playergrowth reload} takes effect on
 * the very next notification, no restart needed.</p>
 */
public abstract class Notifier {

    protected final PlayerGrowthCore core;

    protected Notifier(PlayerGrowthCore core) {
        this.core = core;
    }

    /** Permission node a sender must hold to receive this notifier's messages. */
    protected abstract String permission();

    /** Whether this notifier is currently allowed to send anything at all. */
    protected abstract boolean enabled();

    protected final void notify(CommandSender sender, String key, Map<String, Object> placeholders) {
        if (!enabled()) return;
        if (!sender.hasPermission(permission())) return;

        PlayerGrowthMessages.send(core, sender, key, placeholders);
    }
}
