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
 *
 * <p>{@code SERIALIZER} is built with {@code .hexColors()} +
 * {@code .useUnusualXRepeatedCharacterHexFormat()}, not the plain
 * {@code LegacyComponentSerializer.legacySection()} preset - a real
 * live-deploy bug: the plain preset only emits the 16 basic legacy colors,
 * so a gradient (e.g. FlectonePulse's {@code <fcolor:N>} palette, all
 * varying shades of light blue) had every character independently rounded
 * to the *same* nearest legacy color, collapsing the whole gradient into a
 * single flat aqua - reported as "one-colored message, not a gradient" from
 * a real server. {@code hexColors()} alone emits the newer, compact
 * {@code §#RRGGBB} form, which {@code TextComponent.fromLegacyText} below
 * doesn't understand (verified empirically: it fell through to literal
 * text, no color at all) - {@code useUnusualXRepeatedCharacterHexFormat()}
 * switches to the older {@code §x§R§R§G§G§B§B} form BungeeCord's own parser
 * actually recognises. Verified end-to-end with a standalone harness: a
 * gradient round-tripped through this exact serializer + parser combination
 * came back with one distinct RGB color per character, not one flat color
 * for the whole span.</p>
 */
public final class LegacyText {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

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
