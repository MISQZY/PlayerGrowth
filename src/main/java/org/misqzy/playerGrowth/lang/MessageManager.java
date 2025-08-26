package org.misqzy.playerGrowth.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Map;

public class MessageManager {
    private final Plugin plugin;
    private FileConfiguration messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private String currentLang;

    public MessageManager(Plugin plugin, String lang) {
        this.plugin = plugin;
        loadMessages(lang);
    }

    public void loadMessages(String lang) {
        this.currentLang = lang;

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        String fileName = "messages_" + lang + ".yml";
        File file = new File(langFolder, fileName);

        if (!file.exists()) {
            plugin.saveResource("lang/" + fileName, false);
        }

        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String getRaw(String key) {
        return messages.getString(key, "<red>Missing message: " + key + "</red>");
    }

    public Component getMessage(String key, Map<String, Object> placeholders) {
        String raw = getRaw(key);
        String formatted = PlaceholderUtil.format(raw, placeholders);
        return miniMessage.deserialize(formatted);
    }

    public Component getMessage(String key) {
        return miniMessage.deserialize(getRaw(key));
    }

    public String getCurrentLang() {
        return currentLang;
    }
}
