package org.misqzy.playergrowth.bukkit.config;

import org.misqzy.playergrowth.common.config.ConfigView;
import org.misqzy.playergrowth.common.config.YamlConfigView;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Loads a YAML file on disk into a platform-agnostic {@link ConfigView}. */
public final class YamlFileLoader {

    private YamlFileLoader() {}

    public static ConfigView load(File file) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            return new YamlConfigView(loaded instanceof Map<?, ?> map ? map : Map.of());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load YAML file: " + file, e);
        }
    }
}
