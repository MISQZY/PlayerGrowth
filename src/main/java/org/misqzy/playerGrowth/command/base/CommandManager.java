package org.misqzy.playerGrowth.command.base;

import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playerGrowth.command.GrowthTimeCommand;
import org.misqzy.playerGrowth.command.HeightCommand;
import org.misqzy.playerGrowth.command.PlayerGrowthCommand;
import org.misqzy.playerGrowth.command.SetHeightCommand;

import java.util.Objects;

public class CommandManager {

    private final JavaPlugin plugin;
    private final HeightCommand heightCommand;
    private final PlayerGrowthCommand playerGrowthCommand;
    private final GrowthTimeCommand growthTimeCommand;
    private final SetHeightCommand setHeightCommand;

    public CommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.heightCommand = new HeightCommand();
        this.playerGrowthCommand = new PlayerGrowthCommand();
        this.growthTimeCommand = new GrowthTimeCommand();
        this.setHeightCommand = new SetHeightCommand();
    }

    public void registerCommands() {
        Objects.requireNonNull(plugin.getCommand("height")).setExecutor(heightCommand);
        Objects.requireNonNull(plugin.getCommand("playergrowth")).setExecutor(playerGrowthCommand);
        Objects.requireNonNull(plugin.getCommand("growthtime")).setExecutor(growthTimeCommand);
        Objects.requireNonNull(plugin.getCommand("setheight")).setExecutor(setHeightCommand);
        Objects.requireNonNull(plugin.getCommand("setheight")).setTabCompleter(setHeightCommand);
    }
}
