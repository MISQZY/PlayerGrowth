package org.misqzy.playergrowth.bukkit.api;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Public, read-only API other plugins can use to query a player's current
 * PlayerGrowth state - height, gender, and growth progress - without going
 * through PlaceholderAPI as a string-based intermediary or reaching into
 * PlayerGrowth's internal classes.
 *
 * <p>Obtain an instance through Bukkit's {@link org.bukkit.plugin.ServicesManager},
 * PlayerGrowth's standard integration point for other plugins:
 * <pre>{@code
 * RegisteredServiceProvider<PlayerGrowthAPI> provider =
 *         Bukkit.getServicesManager().getRegistration(PlayerGrowthAPI.class);
 * if (provider != null) {
 *     PlayerGrowthAPI api = provider.getProvider();
 * }
 * }</pre>
 * The service is only registered while PlayerGrowth is enabled - always
 * guard against a {@code null} registration rather than hard-depending on
 * it being present.</p>
 *
 * <p>Every per-player method only has data for players PlayerGrowth is
 * actively tracking, i.e. currently online - height/gender/growth timing
 * are computed from live entity state (Bukkit's Attribute API, played
 * time), not something persisted for offline players. An empty
 * {@link Optional}/{@link OptionalLong} means "not tracked right now", not
 * zero or false.</p>
 */
public interface PlayerGrowthAPI {

    /** This player's current height, in meters. */
    Optional<Double> getHeight(Player player);

    /** This player's current height, as the raw (unconverted) scale multiplier Bukkit's Attribute API uses. */
    Optional<Double> getScale(Player player);

    /** The minimum possible height, in meters - the same for every player regardless of gender. */
    double getMinHeight();

    /** This player's maximum possible height, in meters (gender-aware). */
    Optional<Double> getMaxHeight(Player player);

    /** This player's gender key, e.g. {@code "male"} - one of {@code gender.yml}'s configured {@code types}. */
    Optional<String> getGender(Player player);

    /** Localized display name for {@link #getGender(Player)}, e.g. {@code "Male"}. */
    Optional<String> getGenderDisplayName(Player player);

    /** Growth progress toward this player's target, in {@code [0.0, 1.0]}. A player with a custom height reads as fully progressed. */
    Optional<Double> getGrowthProgress(Player player);

    /** Seconds remaining until this player reaches full growth. */
    OptionalLong getGrowthRemainingSeconds(Player player);

    /** Whether this player has reached their maximum growth. */
    Optional<Boolean> isAtMaxGrowth(Player player);

    /** Whether this player has a manually-set custom height currently overriding natural growth. */
    Optional<Boolean> hasCustomScale(Player player);
}
