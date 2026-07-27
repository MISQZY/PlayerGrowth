package org.misqzy.playergrowth.common.storage;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * Flat-file storage using plain SnakeYAML (no Bukkit {@code FileConfiguration}
 * dependency), so it can run unmodified on any platform module. A
 * {@link ReadWriteLock} keeps concurrent async reads/writes safe, same as
 * the original implementation.
 */
public final class YamlStorage implements Storage {

    private final Logger logger;
    private final File file;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Yaml yaml;

    @SuppressWarnings("unchecked")
    private Map<String, Object> root = new HashMap<>();

    public YamlStorage(Logger logger, File dataFolder) {
        this.logger = logger;
        this.file = new File(new File(dataFolder, "data"), "player_data.yml");

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yaml = new Yaml(options);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean initialize() {
        try {
            File parent = file.getParentFile();
            if (parent != null) //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();

            if (!file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } else {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                    Object loaded = yaml.load(reader);
                    if (loaded instanceof Map<?, ?> m) root = (Map<String, Object>) m;
                }
            }
            section("scales");
            section("genders");
            section("growth-times");
            return true;
        } catch (IOException e) {
            logger.severe("Failed to initialise YAML storage: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean testConnection() {
        return file.exists() && file.canWrite();
    }

    @Override public StorageType type() { return StorageType.YAML; }

    @Override public void close() { /* flat file - nothing to close */ }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(String name) {
        return (Map<String, Object>) root.computeIfAbsent(name, k -> new HashMap<String, Object>());
    }

    private void persist() throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            yaml.dump(root, writer);
        }
    }

    // -----------------------------------------------------------------------
    // Custom scale
    // -----------------------------------------------------------------------

    @Override
    public Double getCustomScale(UUID uuid) {
        lock.readLock().lock();
        try {
            Map<?, ?> entry = entry(section("scales"), uuid);
            if (entry == null || !Boolean.TRUE.equals(entry.get("valid"))) return null;
            Object v = entry.get("value");
            return v instanceof Number n ? n.doubleValue() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean setCustomScale(UUID uuid, double scale) {
        lock.writeLock().lock();
        try {
            Map<String, Object> entry = newEntry();
            entry.put("value", scale);
            entry.put("valid", true);
            section("scales").put(uuid.toString(), entry);
            persist();
            return true;
        } catch (IOException e) {
            logger.severe("YAML setCustomScale: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean removeCustomScale(UUID uuid) {
        lock.writeLock().lock();
        try {
            Map<?, ?> entry = entry(section("scales"), uuid);
            if (entry == null) return true;
            ((Map<String, Object>) entry).put("valid", false);
            persist();
            return true;
        } catch (IOException e) {
            logger.severe("YAML removeCustomScale: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -----------------------------------------------------------------------
    // Gender
    // -----------------------------------------------------------------------

    @Override
    public String getGenderKey(UUID uuid) {
        lock.readLock().lock();
        try {
            Map<?, ?> entry = entry(section("genders"), uuid);
            if (entry == null || !Boolean.TRUE.equals(entry.get("valid"))) return null;
            Object v = entry.get("value");
            return v != null ? String.valueOf(v) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean setGenderKey(UUID uuid, String genderKey) {
        lock.writeLock().lock();
        try {
            Map<String, Object> entry = newEntry();
            entry.put("value", genderKey.toLowerCase());
            entry.put("valid", true);
            section("genders").put(uuid.toString(), entry);
            persist();
            return true;
        } catch (IOException e) {
            logger.severe("YAML setGenderKey: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -----------------------------------------------------------------------
    // Growth time
    // -----------------------------------------------------------------------

    @Override
    public Long getGrowthTimeSeconds(UUID uuid) {
        lock.readLock().lock();
        try {
            Map<?, ?> entry = entry(section("growth-times"), uuid);
            if (entry == null || !Boolean.TRUE.equals(entry.get("valid"))) return null;
            Object v = entry.get("value");
            return v instanceof Number n ? n.longValue() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean setGrowthTimeSeconds(UUID uuid, long seconds) {
        lock.writeLock().lock();
        try {
            Map<String, Object> entry = newEntry();
            entry.put("value", seconds);
            entry.put("valid", true);
            section("growth-times").put(uuid.toString(), entry);
            persist();
            return true;
        } catch (IOException e) {
            logger.severe("YAML setGrowthTimeSeconds: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -----------------------------------------------------------------------

    private Map<?, ?> entry(Map<String, Object> section, UUID uuid) {
        Object v = section.get(uuid.toString());
        return v instanceof Map<?, ?> m ? m : null;
    }

    private Map<String, Object> newEntry() {
        return new HashMap<>();
    }
}
