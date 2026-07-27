package org.misqzy.playergrowth.paper.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.paper.PaperPlayerAdapter;

/**
 * Drives the growth engine's player lifecycle from Bukkit events, plus a
 * respawn hook the original plugin did not have (see
 * {@code docs/ARCHITECTURE.md} "Bugs fixed" #5).
 */
public final class PlayerConnectionListener implements Listener {

    private final JavaPlugin plugin;
    private final GrowthEngine growthEngine;

    public PlayerConnectionListener(JavaPlugin plugin, GrowthEngine growthEngine) {
        this.plugin = plugin;
        this.growthEngine = growthEngine;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        growthEngine.loadPlayer(new PaperPlayerAdapter(event.getPlayer()), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        growthEngine.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // The scale attribute's base value can be reset back to default as
        // part of resolving the respawn itself, so re-applying in the same
        // tick can be undone a moment later; scheduling one tick later is
        // what actually sticks on affected Paper versions.
        Bukkit.getScheduler().runTask(plugin, () ->
                growthEngine.applyScale(new PaperPlayerAdapter(event.getPlayer())));
    }
}
