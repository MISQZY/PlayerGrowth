package org.misqzy.playerGrowth;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.misqzy.playerGrowth.command.base.CommandManager;
import org.misqzy.playerGrowth.config.ConfigManager;
import org.misqzy.playerGrowth.lang.MessageManager;
import org.misqzy.playerGrowth.growth.GrowthManager;
import org.misqzy.playerGrowth.lang.PlaceholderAPIHook;
import org.misqzy.playerGrowth.listeners.PlayerListener;
import org.misqzy.playerGrowth.storage.Storage;
import org.misqzy.playerGrowth.storage.StorageFactory;

public final class PlayerGrowth extends JavaPlugin {

    private static PlayerGrowth instance;

    public ConfigManager configManager;
    public GrowthManager growthManager;
    public MessageManager messageManager;
    public Storage storage;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this, configManager.getLanguage());
        
        storage = StorageFactory.createStorage(this, configManager);
        if (!storage.initialize()) {
            getLogger().severe("Failed to initialize storage! Trying to use YAML as fallback...");
            storage = new org.misqzy.playerGrowth.storage.YAMLStorage(this);
            if (!storage.initialize()) {
                getLogger().severe("Failed to initialize YAML storage! Plugin will be disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            } else {
                getLogger().warning("Using YAML storage as fallback. Custom scales feature may be limited.");
            }
        }
        
        if (!storage.testConnection()) {
            getLogger().warning("Storage connection test failed! Some features may not work properly.");
            getLogger().warning("The plugin will continue to work, but custom scales may not be saved.");
        } else {
            getLogger().info("Storage connection test successful!");
        }
        
        growthManager = new GrowthManager(this, configManager, storage);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(growthManager).register();
        }

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
        if (storage != null) {
            storage.close();
        }
        getLogger().info("PlayerGrowth disabled.");
    }
}
