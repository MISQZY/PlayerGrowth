package org.misqzy.playergrowth.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;

/**
 * Vanilla Spigot/CraftBukkit's {@code CommandSender} has no
 * {@code sendMessage(Component)} overload - that's a Paper-only addition -
 * so every message this module sends is serialised to a legacy
 * {@code §}-formatted string first. See the dependency comment in
 * {@code build.gradle.kts} for why this is used instead of
 * {@code adventure-platform-bukkit}.
 *
 * <p>Sends through {@code CommandSender#spigot().sendMessage(BaseComponent...)}
 * rather than the plain {@code sendMessage(String)} overload -
 * CraftBukkit's {@code String} path (via {@code CraftChatMessage.fromString})
 * splits on every {@code \n} and sends each line as its own chat packet, so
 * a multi-line message (e.g. {@code command.invalid-syntax}) arrived as
 * several separate chat entries instead of one message with line breaks.
 * The {@code BaseComponent[]} path sends the whole thing as a single packet
 * regardless of embedded newlines - the same guarantee minecraft:paper
 * already gets for free from {@code CommandSender#sendMessage(Component)}.
 * {@code bungeecord-chat}'s {@code TextComponent}/{@code BaseComponent}
 * come transitively from {@code spigot-api}, already a dependency here, so
 * this needs no new library.</p>
 */
public final class LegacyText {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private LegacyText() {}

    // TextComponent.fromLegacyText is deprecated in bungeecord-chat (the whole
    // net.md_5.bungee.api.chat package is soft-deprecated in favour of kyori
    // Adventure) - but there's no Adventure-based replacement reachable from
    // vanilla CraftBukkit's CommandSender, which is exactly why this class
    // exists. Verified there's no non-deprecated overload for this
    // conversion (javap against the actual bungeecord-chat jar this project
    // resolves): both fromLegacyText(String) and fromLegacyText(String,
    // ChatColor) carry @Deprecated. Deliberate, not an oversight.
    @SuppressWarnings("deprecation")
    public static void send(CommandSender sender, Component component) {
        BaseComponent[] parts = TextComponent.fromLegacyText(SERIALIZER.serialize(component));
        sender.spigot().sendMessage(parts);
    }
}
