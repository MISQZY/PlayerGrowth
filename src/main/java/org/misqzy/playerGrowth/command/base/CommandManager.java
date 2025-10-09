package org.misqzy.playerGrowth.command.base;

import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playerGrowth.command.GrowthTimeCommand;
import org.misqzy.playerGrowth.command.HeightCommand;
import org.misqzy.playerGrowth.command.PlayerGrowthCommand;

import java.util.Objects;

public class CommandManager {

    private final JavaPlugin plugin;
    private final HeightCommand heightCommand;
    private final PlayerGrowthCommand playerGrowthCommand;
    private final GrowthTimeCommand growthTimeCommand;

    public CommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.heightCommand = new HeightCommand();
        this.playerGrowthCommand = new PlayerGrowthCommand();
        this.growthTimeCommand = new GrowthTimeCommand();
    }

    public void registerCommands() {
        Objects.requireNonNull(plugin.getCommand("height")).setExecutor(heightCommand);
        Objects.requireNonNull(plugin.getCommand("playergrowth")).setExecutor(playerGrowthCommand);
        Objects.requireNonNull(plugin.getCommand("growthtime")).setExecutor(growthTimeCommand);
    }
}
