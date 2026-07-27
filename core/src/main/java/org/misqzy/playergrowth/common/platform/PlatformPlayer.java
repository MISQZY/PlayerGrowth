package org.misqzy.playergrowth.common.platform;

import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * Minimal player contract the growth engine needs. Only the Paper module
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

    /** Seconds of accumulated play time, used to drive time-based growth. */
    long playedSeconds();

    /** Current applied scale, or {@code null} if the scale attribute is unavailable. */
    Double currentScale();

    void applyScale(double scale);

    /** Whether the player is currently boxed in by solid blocks (growth should pause visually). */
    boolean isGrowthBlocked();
}
