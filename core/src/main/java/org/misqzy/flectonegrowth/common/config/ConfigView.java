package org.misqzy.flectonegrowth.common.config;

import java.util.List;
import java.util.Set;

/**
 * A read-only view over a nested YAML-like structure, independent of any
 * platform's config API (Bukkit's {@code FileConfiguration}, Velocity's
 * TOML wrapper, etc). All platform config loaders eventually produce one
 * of these so the rest of core never imports a platform type.
 */
public interface ConfigView {

    String getString(String path, String def);

    int getInt(String path, int def);

    long getLong(String path, long def);

    double getDouble(String path, double def);

    boolean getBoolean(String path, boolean def);

    List<String> getStringList(String path);

    /** Sub-section at {@code path}, or {@code null} if absent/not a map. */
    ConfigView getSection(String path);

    /** Direct child keys of this section (non-recursive). */
    Set<String> keys();
}
