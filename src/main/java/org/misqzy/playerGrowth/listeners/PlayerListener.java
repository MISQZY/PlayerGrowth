package org.misqzy.playerGrowth.listeners;

import org.misqzy.playerGrowth.growth.GrowthManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {
    private final GrowthManager growthManager;

    public PlayerListener(GrowthManager growthManager) {
        this.growthManager = growthManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        growthManager.loadPlayerCustomScale(e.getPlayer());
        growthManager.updatePlayerScale(e.getPlayer());
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        growthManager.unloadPlayerCustomScale(e.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        growthManager.updatePlayerScale(e.getPlayer());
    }
}
