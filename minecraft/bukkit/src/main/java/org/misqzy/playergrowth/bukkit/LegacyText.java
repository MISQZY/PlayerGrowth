package org.misqzy.playergrowth.bukkit;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;

/**
 * The single message-delivery path this module uses, on any Bukkit-API
 * server (Spigot, CraftBukkit, Paper, Purpur, ...) - this module is compiled
 * only against {@code spigot-api}, never {@code paper-api}, so it has to
 * work on the lowest common denominator by default.
 *
 * <p><b>Native Adventure delivery (the Paper-only optimization).</b> Before
 * falling back to legacy serialization, {@link #send} checks whether the
 * runtime {@code CommandSender} implements Adventure's {@link Audience} -
 * true for Paper/Purpur's own {@code CommandSender} implementation, false
 * for plain Spigot/CraftBukkit's. This needs no {@code paper-api} dependency
 * at all: {@code Audience} lives in {@code adventure-api}, already a
 * dependency here for MiniMessage, not in {@code paper-api}. When it's
 * available, {@code audience.sendMessage(component)} sends the real
 * {@link Component} directly - no serialization round-trip, and unlike the
 * legacy path below, hover/click events and true RGB survive intact. This is
 * "Bukkit as the core, Paper only for optimizations" applied to message
 * delivery: one code path, capability-detected at runtime, rather than a
 * second module.</p>
 *
 * <p><b>Legacy fallback (the Bukkit-API baseline).</b> Vanilla Spigot/
 * CraftBukkit's {@code CommandSender} has no {@code sendMessage(Component)}
 * overload and isn't an {@code Audience}, so the message is serialised to a
 * legacy {@code §}-formatted string first. See the dependency comment in
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
 * regardless of embedded newlines - the same guarantee the native-Adventure
 * path above already gets for free. {@code bungeecord-chat}'s
 * {@code TextComponent}/{@code BaseComponent} come transitively from
 * {@code spigot-api}, already a dependency here, so this needs no new
 * library.</p>
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
 * for the whole span. This path is now only reached on servers whose
 * {@code CommandSender} isn't an {@code Audience} (plain Spigot/CraftBukkit),
 * so it remains load-bearing rather than dead code.</p>
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
        if (sender instanceof Audience audience) {
            audience.sendMessage(component);
            return;
        }

        BaseComponent[] parts = TextComponent.fromLegacyText(SERIALIZER.serialize(component));
        sender.spigot().sendMessage(parts);
    }
}
