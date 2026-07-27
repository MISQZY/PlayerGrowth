package org.misqzy.playergrowth.common.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.misqzy.playergrowth.common.config.ConfigView;

import java.util.Map;

/**
 * Loads and renders MiniMessage strings from a localisation file into a
 * platform-agnostic {@link Component}. How that {@link Component} actually
 * reaches a player is up to the platform module: Paper's {@code CommandSender}
 * implements kyori's {@code Audience} natively, while Bukkit/Spigot has no
 * such support, so the Bukkit module serialises to a legacy formatted
 * string before sending (see {@code LegacyText} there). Either way, every
 * platform starts from the exact same {@link Component} this class produces.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ConfigView data;

    public Messages(ConfigView data) {
        this.data = data;
    }

    public String raw(String key) {
        return data.getString(key, "<red>Missing message: " + key + "</red>");
    }

    public Component get(String key, Map<String, Object> placeholders) {
        return MINI.deserialize(raw(key), buildResolver(placeholders));
    }

    public Component get(String key) {
        return MINI.deserialize(raw(key));
    }

    public String heightUnit() {
        return raw("height.unit");
    }

    private static TagResolver buildResolver(Map<String, Object> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) return TagResolver.empty();
        TagResolver.Builder builder = TagResolver.builder();
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            builder.resolver(Placeholder.unparsed(entry.getKey(), String.valueOf(entry.getValue())));
        }
        return builder.build();
    }
}
