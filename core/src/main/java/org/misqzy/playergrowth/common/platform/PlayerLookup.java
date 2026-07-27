package org.misqzy.playergrowth.common.platform;

import java.util.UUID;

/**
 * Resolves a currently-online {@link PlatformPlayer} by UUID. Bound by the
 * platform module (e.g. wrapping {@code Bukkit.getPlayer(uuid)} on Paper)
 * so {@code core} can react to incoming network-sync events without
 * owning an online-player registry itself.
 */
public interface PlayerLookup {

    /** Returns the online player, or {@code null} if they are not on this server. */
    PlatformPlayer find(UUID uuid);
}
