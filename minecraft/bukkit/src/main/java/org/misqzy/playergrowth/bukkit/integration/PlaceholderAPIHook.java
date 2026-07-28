package org.misqzy.playergrowth.bukkit.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.domain.OfflineProfile;
import org.misqzy.playergrowth.common.lang.TimeFormatter;
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.common.service.ScaleMath;
import org.misqzy.playergrowth.bukkit.BuildVersion;
import org.misqzy.playergrowth.bukkit.BukkitPlayerAdapter;

import java.util.List;

/**
 * Exposes {@code %playergrowth_<placeholder>%} to other plugins. Swapped
 * from {@code PlayerGrowthService} to {@link GrowthEngine} + a fresh
 * {@link BukkitPlayerAdapter} per call, same pattern every command class
 * uses - unchanged in spirit from the original hook.
 *
 * <p>{@link #getPlaceholders()} lists every placeholder below so
 * {@code /papi info playergrowth} documents them instead of showing an
 * empty "Placeholders" section.</p>
 */
public final class PlaceholderAPIHook extends PlaceholderExpansion {

    private static final List<String> PLACEHOLDERS = List.of(
            "%playergrowth_height%",
            "%playergrowth_height_full%",
            "%playergrowth_height_min%",
            "%playergrowth_height_max%",
            "%playergrowth_scale%",
            "%playergrowth_scale_min%",
            "%playergrowth_scale_max%",
            "%playergrowth_gender%",
            "%playergrowth_growth_remaining_seconds%",
            "%playergrowth_growth_remaining_formatted%",
            "%playergrowth_growth_percentage%",
            "%playergrowth_growth_max_reached%",
            "%playergrowth_growth_active%",
            "%playergrowth_has_custom_scale%"
    );

    private final PlayerGrowthCore core;

    public PlaceholderAPIHook(PlayerGrowthCore core) {
        this.core = core;
    }

    @Override public @NotNull String getIdentifier() { return "playergrowth"; }
    @Override public @NotNull String getName() { return "PlayerGrowth"; }
    @Override public @NotNull String getAuthor() { return "MISQZY"; }
    @Override public @NotNull String getVersion() { return BuildVersion.VERSION; }
    @Override public boolean persist() { return true; }
    @Override public @NotNull List<String> getPlaceholders() { return PLACEHOLDERS; }

    @Override
    public String onRequest(@NotNull OfflinePlayer offlinePlayer, @NotNull String params) {
        GrowthEngine engine = core.growthEngine();

        if (offlinePlayer instanceof Player player && player.isOnline()) {
            return resolveOnline(engine, new BukkitPlayerAdapter(player), params);
        }
        return resolveOffline(engine, offlinePlayer, params);
    }

    private String resolveOnline(GrowthEngine engine, BukkitPlayerAdapter target, String params) {
        double current = engine.effectiveScale(target);

        return switch (params.toLowerCase()) {
            case "height" -> ScaleMath.formatValue(current);
            case "height_full" -> ScaleMath.format(current, core.messages().heightUnit());
            case "height_min" -> ScaleMath.formatValue(engine.minScale());
            case "height_max" -> ScaleMath.formatValue(engine.maxScaleFor(target));
            case "scale" -> ScaleMath.formatRaw(current);
            case "scale_min" -> ScaleMath.formatRaw(engine.minScale());
            case "scale_max" -> ScaleMath.formatRaw(engine.maxScaleFor(target));
            case "gender" -> core.genderDisplayName(target);
            case "growth_remaining_seconds" -> String.valueOf(engine.secondsUntilFullGrowth(target));
            case "growth_remaining_formatted" -> TimeFormatter.format(engine.secondsUntilFullGrowth(target), core.messages());
            case "growth_percentage" -> ScaleMath.formatPercentage(engine.growthProgress(target));
            case "growth_max_reached" -> String.valueOf(engine.isAtMaxGrowth(target));
            case "growth_active" -> String.valueOf(!engine.isAtMaxGrowth(target) && !engine.hasCustomScale(target));
            case "has_custom_scale" -> String.valueOf(engine.hasCustomScale(target));
            default -> null;
        };
    }

    /**
     * Same placeholders, computed from a one-shot {@link OfflineProfile}
     * read straight out of storage instead of the online-only
     * {@code ProfileCache}/live entity attribute - see
     * {@link GrowthEngine#loadOffline} for the (deliberately blocking)
     * tradeoff. Lets leaderboard/hologram plugins showing offline players
     * get real data instead of PAPI's usual empty-string default.
     */
    private String resolveOffline(GrowthEngine engine, OfflinePlayer offlinePlayer, String params) {
        OfflineProfile profile = engine.loadOffline(offlinePlayer.getUniqueId());
        double current = engine.effectiveScale(profile);

        return switch (params.toLowerCase()) {
            case "height" -> ScaleMath.formatValue(current);
            case "height_full" -> ScaleMath.format(current, core.messages().heightUnit());
            case "height_min" -> ScaleMath.formatValue(engine.minScale());
            case "height_max" -> ScaleMath.formatValue(engine.maxScaleFor(profile.gender()));
            case "scale" -> ScaleMath.formatRaw(current);
            case "scale_min" -> ScaleMath.formatRaw(engine.minScale());
            case "scale_max" -> ScaleMath.formatRaw(engine.maxScaleFor(profile.gender()));
            case "gender" -> core.genderDisplayName(profile.gender());
            case "growth_remaining_seconds" -> String.valueOf(engine.secondsUntilFullGrowth(profile));
            case "growth_remaining_formatted" -> TimeFormatter.format(engine.secondsUntilFullGrowth(profile), core.messages());
            case "growth_percentage" -> ScaleMath.formatPercentage(engine.growthProgress(profile));
            case "growth_max_reached" -> String.valueOf(engine.isAtMaxGrowth(profile));
            case "growth_active" -> String.valueOf(!engine.isAtMaxGrowth(profile) && !profile.hasCustomScale());
            case "has_custom_scale" -> String.valueOf(profile.hasCustomScale());
            default -> null;
        };
    }
}
