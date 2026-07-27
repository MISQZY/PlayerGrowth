package org.misqzy.playergrowth.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.misqzy.playergrowth.common.platform.PlatformPlayer;

import java.util.UUID;

/**
 * Wraps a live Bukkit {@link Player} as a {@link PlatformPlayer}. Created
 * fresh per call site rather than cached, since it is a thin, stateless view
 * over the live Bukkit object - caching it would risk holding a stale
 * reference across a respawn/teleport.
 */
public final class PaperPlayerAdapter implements PlatformPlayer {

    private final Player player;

    public PaperPlayerAdapter(Player player) {
        this.player = player;
    }

    public Player bukkit() {
        return player;
    }

    @Override public UUID uuid() { return player.getUniqueId(); }
    @Override public String name() { return player.getName(); }
    @Override public boolean isOnline() { return player.isOnline(); }
    @Override public boolean hasPermission(String permission) { return player.hasPermission(permission); }
    @Override public void sendMessage(Component component) { player.sendMessage(component); }

    @Override
    public long playedSeconds() {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
    }

    @Override
    public Double currentScale() {
        AttributeInstance attr = player.getAttribute(Attribute.SCALE);
        return attr != null ? attr.getBaseValue() : null;
    }

    @Override
    public void applyScale(double scale) {
        AttributeInstance attr = player.getAttribute(Attribute.SCALE);
        if (attr != null) attr.setBaseValue(scale);
    }

    @Override
    public boolean isGrowthBlocked() {
        Location loc = player.getLocation();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        if (isSolid(player.getWorld().getBlockAt(bx, by + 1, bz))) return true;

        for (int y = by; y <= by + 1; y++) {
            for (int x = bx - 1; x <= bx + 1; x++) {
                for (int z = bz - 1; z <= bz + 1; z++) {
                    if (x == bx && y == by && z == bz) continue;
                    if (isSolid(player.getWorld().getBlockAt(x, y, z))) return true;
                }
            }
        }
        return false;
    }

    private static boolean isSolid(Block block) {
        Material mat = block.getType();
        return !mat.isAir() && mat.isSolid();
    }
}
