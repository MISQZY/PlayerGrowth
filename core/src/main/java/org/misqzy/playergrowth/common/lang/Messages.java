package org.misqzy.playergrowth.common.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.misqzy.playergrowth.common.config.ConfigView;

import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Loads and renders MiniMessage strings from a localisation file into a
 * platform-agnostic {@link Component}. How that {@link Component} actually
 * reaches a player is up to the platform module: Paper's {@code CommandSender}
 * implements kyori's {@code Audience} natively, while Bukkit/Spigot has no
 * such support, so the Bukkit module serialises to a legacy formatted
 * string before sending (see {@code LegacyText} there). Either way, every
 * platform starts from the exact same {@link Component} this class produces.
 *
 * <p>Every message also gets two theme-color tags, resolved as real
 * MiniMessage {@link TagResolver}s (not a hand-rolled pre-processing regex -
 * an earlier version worked that way and broke on nested tags; see below):</p>
 * <ul>
 *   <li>{@code <primary>}/{@code <secondary>} - config.yml's
 *   {@code colors.primary}/{@code colors.secondary}, so a server can
 *   reskin every message's accent colors from one place instead of editing
 *   every localization string.</li>
 *   <li>{@code <fcolor:N>} - FlectonePulse's own configured default color
 *   palette (see the platform module's {@code FlectonePulseColorResolver}),
 *   available whenever FlectonePulse integration is enabled, a no-op
 *   otherwise. Looked up live via a {@link Supplier} on every render, not
 *   cached at construction: FlectonePulse can still be finishing its own
 *   (possibly async) startup at the exact moment this plugin's
 *   {@code onEnable()} runs, even though Bukkit already reports it enabled -
 *   caching an empty snapshot from that instant would leave every
 *   {@code <fcolor:N>} unresolved until the next {@code /playergrowth reload}.</li>
 * </ul>
 *
 * <p><b>How this actually works</b> - studied from FlectonePulse's own
 * {@code FColorModule} (decompiled the real published jar,
 * {@code net.flectone.pulse:core}, rather than guessing): the resolved color
 * can be an arbitrary MiniMessage fragment (a bare name, a hex code, or a
 * full gradient), which {@link Tag#styling} can't represent on its own - it
 * only takes {@code StyleBuilderApplicable}s, not markup text. FlectonePulse's
 * fix is {@link Tag#preProcessParsed(String)}: the resolver returns the
 * *opening* markup (e.g. {@code "<gradient:#A6D8FF:#8CC8FF>"}) as a string,
 * which MiniMessage re-injects into the token stream and parses normally
 * from that point on - handling gradients, and nesting, correctly, because
 * it becomes genuine MiniMessage input instead of text this class tries to
 * pattern-match itself.</p>
 *
 * <p>The tradeoff (also taken from FlectonePulse, not invented here):
 * {@code preProcessParsed} only substitutes the *opening* tag - there's no
 * matching close, so {@code </primary>} etc. would otherwise show up as
 * literal, unresolved text in the output (verified empirically). FlectonePulse
 * strips its closing tag with a regex before parsing rather than trying to
 * properly re-close it; this class does the same for all three tags. The
 * practical effect is that these colors apply *forward* until the next color
 * change or end of message, not scoped to a precise span - so, unlike the
 * regex-substitution version this replaced, a localization string that wants
 * to return to an outer color after a short inner span (e.g. secondary text
 * following a primary-colored name) needs to re-open that color explicitly
 * rather than relying on a closing tag to restore it.</p>
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    // preProcessParsed only ever substitutes the opening tag (see class
    // javadoc) - closing tags are meaningless here and would otherwise leak
    // into the output as literal text, so they're stripped before parsing.
    private static final Pattern CLOSING_TAGS = Pattern.compile("</primary>|</secondary>|</fcolor(?::[0-9]+)?>");

    private final ConfigView data;
    private final String primaryColor;
    private final String secondaryColor;
    private final Supplier<Map<Integer, String>> fcolors;

    public Messages(ConfigView data, String primaryColor, String secondaryColor, Supplier<Map<Integer, String>> fcolors) {
        this.data = data;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.fcolors = fcolors != null ? fcolors : Map::of;
    }

    public String raw(String key) {
        return data.getString(key, "<red>Missing message: " + key + "</red>");
    }

    public Component get(String key, Map<String, Object> placeholders) {
        return MINI.deserialize(stripClosingTags(raw(key)), themeResolvers(), buildResolver(placeholders));
    }

    public Component get(String key) {
        return MINI.deserialize(stripClosingTags(raw(key)), themeResolvers());
    }

    public String heightUnit() {
        return raw("height.unit");
    }

    /**
     * The closing-tag-stripped raw MiniMessage string for {@code key} -
     * exactly what {@link #get(String, Map)} would hand to its own
     * {@code MiniMessage.deserialize}, exposed so a platform module can
     * instead hand it to FlectonePulse's own {@code MessagePipeline.build}
     * when routing a message through FlectonePulse (see that module's
     * {@code FlectonePulseMessageDispatcher}). {@code <fcolor:N>} is
     * deliberately left unresolved here - FlectonePulse resolves that tag
     * itself as part of its own pipeline, the whole reason for going through
     * it in the first place, so this class must not race it with its own
     * resolution first.
     */
    public String rawForDispatch(String key) {
        return stripClosingTags(raw(key));
    }

    /**
     * {@code <primary>}/{@code <secondary>} plus this call's placeholders -
     * everything {@link #themeResolvers()} resolves *except* {@code <fcolor:N>}.
     * Paired with {@link #rawForDispatch(String)} for the FlectonePulse
     * dispatch path: FlectonePulse's own pipeline adds its own
     * {@code <fcolor:N>} resolver on top of whatever it's handed, so this
     * class only needs to supply the theme/placeholder tags it alone knows
     * about, not fcolor.
     */
    public TagResolver externalDispatchResolvers(Map<String, Object> placeholders) {
        return TagResolver.resolver(primarySecondaryResolvers(), buildResolver(placeholders));
    }

    private static String stripClosingTags(String message) {
        if (!message.contains("</primary>") && !message.contains("</secondary>") && !message.contains("</fcolor")) {
            return message;
        }
        return CLOSING_TAGS.matcher(message).replaceAll("");
    }

    private TagResolver themeResolvers() {
        return TagResolver.resolver(
                primarySecondaryResolvers(),
                TagResolver.resolver("fcolor", (queue, ctx) -> Tag.preProcessParsed(resolveFColor(queue)))
        );
    }

    private TagResolver primarySecondaryResolvers() {
        return TagResolver.resolver(
                TagResolver.resolver("primary", (queue, ctx) -> Tag.preProcessParsed(asTag(primaryColor))),
                TagResolver.resolver("secondary", (queue, ctx) -> Tag.preProcessParsed(asTag(secondaryColor)))
        );
    }

    /** Mirrors FlectonePulse's own {@code <fcolor:N>} argument handling: missing/non-numeric index -> no-op, not an error. */
    private String resolveFColor(ArgumentQueue queue) {
        if (!queue.hasNext()) return "";
        OptionalInt index = queue.pop().asInt();
        if (index.isEmpty()) return "";

        return asTag(fcolors.get().get(index.getAsInt()));
    }

    /** {@code null}/blank -> no-op; a bare name or hex ({@code "gold"}, {@code "#FFD700"}) -> wrapped ({@code "<gold>"}); already-tagged input passed through as-is. */
    private static String asTag(String value) {
        if (value == null || value.isBlank()) return "";
        return value.startsWith("<") ? value : "<" + value + ">";
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
