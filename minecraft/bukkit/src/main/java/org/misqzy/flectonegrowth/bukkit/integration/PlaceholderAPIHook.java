package org.misqzy.flectonegrowth.bukkit.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.misqzy.flectonegrowth.common.di.FlectoneGrowthCore;
import org.misqzy.flectonegrowth.common.domain.OfflineProfile;
import org.misqzy.flectonegrowth.common.lang.TimeFormatter;
import org.misqzy.flectonegrowth.common.service.GrowthEngine;
import org.misqzy.flectonegrowth.common.service.ScaleMath;
import org.misqzy.flectonegrowth.bukkit.BuildVersion;
import org.misqzy.flectonegrowth.bukkit.BukkitPlayerAdapter;

import java.util.List;

/**
 * Exposes {@code %flectonegrowth_<placeholder>%} to other plugins. Swapped
 * from {@code FlectoneGrowthService} to {@link GrowthEngine} + a fresh
 * {@link BukkitPlayerAdapter} per call, same pattern every command class
 * uses - unchanged in spirit from the original hook.
 *
 * <p>{@link #getPlaceholders()} lists every placeholder below so
 * {@code /papi info flectonegrowth} documents them instead of showing an
 * empty "Placeholders" section.</p>
 */
public final class PlaceholderAPIHook extends PlaceholderExpansion {

    private static final List<String> PLACEHOLDERS = List.of(
            "%flectonegrowth_height%",
            "%flectonegrowth_height_full%",
            "%flectonegrowth_height_min%",
            "%flectonegrowth_height_max%",
            "%flectonegrowth_scale%",
            "%flectonegrowth_scale_min%",
            "%flectonegrowth_scale_max%",
            "%flectonegrowth_gender%",
            "%flectonegrowth_growth_remaining_seconds%",
            "%flectonegrowth_growth_remaining_formatted%",
            "%flectonegrowth_growth_percentage%",
            "%flectonegrowth_growth_max_reached%",
            "%flectonegrowth_growth_active%",
            "%flectonegrowth_has_custom_scale%"
    );

    private final FlectoneGrowthCore core;

    public PlaceholderAPIHook(FlectoneGrowthCore core) {
        this.core = core;
    }

    @Override public @NotNull String getIdentifier() { return "flectonegrowth"; }
    @Override public @NotNull String getName() { return "FlectoneGrowth"; }
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
