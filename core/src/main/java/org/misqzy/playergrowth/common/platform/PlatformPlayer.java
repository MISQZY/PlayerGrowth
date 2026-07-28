package org.misqzy.playergrowth.common.platform;

import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * Minimal player contract the growth engine needs. Only the Bukkit module
 * implements it today (scale is a Bukkit Attribute API concept), but the
 * interface itself carries no Bukkit types so nothing stops another
 * platform with entity access (e.g. a future Fabric module) from
 * implementing it too.
 */
public interface PlatformPlayer {

    UUID uuid();

    String name();

    boolean isOnline();

    boolean hasPermission(String permission);

    void sendMessage(Component component);

    /**
     * Seconds of accumulated play time from this server's own local
     * statistic - used to drive growth only when {@code network.sync-enabled}
     * is off (see {@code GrowthEngine}'s "Playtime tracking" doc in
     * {@code ARCHITECTURE.md}); with sync on, growth reads the shared,
     * cross-server-consistent record instead.
     */
    long playedSeconds();

    /** Current applied scale, or {@code null} if the scale attribute is unavailable. */
    Double currentScale();

    void applyScale(double scale);

    /** Whether the player is currently boxed in by solid blocks (growth should pause visually). */
    boolean isGrowthBlocked();
}
