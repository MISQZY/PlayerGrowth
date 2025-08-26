package org.misqzy.playerGrowth;

import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playerGrowth.command.base.CommandManager;
import org.misqzy.playerGrowth.config.ConfigManager;
import org.misqzy.playerGrowth.lang.MessageManager;
import org.misqzy.playerGrowth.growth.GrowthManager;
import org.misqzy.playerGrowth.listeners.PlayerListener;

public final class PlayerGrowth extends JavaPlugin {

    private static PlayerGrowth instance;

    public ConfigManager configManager;
    public GrowthManager growthManager;
    public MessageManager messageManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this, configManager.getLanguage());
        growthManager = new GrowthManager(this, configManager);

        getServer().getPluginManager().registerEvents(new PlayerListener(growthManager), this);

        CommandManager commandManager = new CommandManager(this);
        commandManager.registerCommands();

        getLogger().info("PlayerGrowth enabled.");
    }

    public static PlayerGrowth getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        getLogger().info("PlayerGrowth disabled.");
    }
}
