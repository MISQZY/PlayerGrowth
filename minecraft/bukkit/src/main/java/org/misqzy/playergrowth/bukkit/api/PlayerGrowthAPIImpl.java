package org.misqzy.playergrowth.bukkit.api;

import org.bukkit.entity.Player;
import org.misqzy.playergrowth.bukkit.BukkitPlayerAdapter;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.common.service.ScaleMath;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;

/**
 * Default {@link PlayerGrowthAPI} implementation, backed by the live
 * {@link PlayerGrowthCore}. Public only so {@code PlayerGrowthPlugin} (a
 * different package) can construct and register it - other plugins should
 * only ever reference it through the {@link PlayerGrowthAPI} interface.
 */
public final class PlayerGrowthAPIImpl implements PlayerGrowthAPI {

    private final PlayerGrowthCore core;

    public PlayerGrowthAPIImpl(PlayerGrowthCore core) {
        this.core = core;
    }

    @Override
    public Optional<Double> getHeight(Player player) {
        return query(player, (engine, target) -> ScaleMath.toMeters(engine.effectiveScale(target)));
    }

    @Override
    public Optional<Double> getScale(Player player) {
        return query(player, GrowthEngine::effectiveScale);
    }

    @Override
    public double getMinHeight() {
        return ScaleMath.toMeters(core.growthEngine().minScale());
    }

    @Override
    public Optional<Double> getMaxHeight(Player player) {
        return query(player, (engine, target) -> ScaleMath.toMeters(engine.maxScaleFor(target)));
    }

    @Override
    public Optional<String> getGender(Player player) {
        return query(player, (engine, target) -> engine.genderOf(target).key());
    }

    @Override
    public Optional<String> getGenderDisplayName(Player player) {
        return query(player, (engine, target) -> core.genderDisplayName(target));
    }

    @Override
    public Optional<Double> getGrowthProgress(Player player) {
        return query(player, GrowthEngine::growthProgress);
    }

    @Override
    public OptionalLong getGrowthRemainingSeconds(Player player) {
        if (player == null || !player.isOnline()) return OptionalLong.empty();
        return OptionalLong.of(core.growthEngine().secondsUntilFullGrowth(new BukkitPlayerAdapter(player)));
    }

    @Override
    public Optional<Boolean> isAtMaxGrowth(Player player) {
        return query(player, GrowthEngine::isAtMaxGrowth);
    }

    @Override
    public Optional<Boolean> hasCustomScale(Player player) {
        return query(player, GrowthEngine::hasCustomScale);
    }

    private <T> Optional<T> query(Player player, BiFunction<GrowthEngine, BukkitPlayerAdapter, T> function) {
        if (player == null || !player.isOnline()) return Optional.empty();
        return Optional.of(function.apply(core.growthEngine(), new BukkitPlayerAdapter(player)));
    }
}
