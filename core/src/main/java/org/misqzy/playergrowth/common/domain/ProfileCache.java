package org.misqzy.playergrowth.common.domain;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry of {@link PlayerProfile}s for currently online players. */
public final class ProfileCache {

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();

    public PlayerProfile getOrCreate(UUID uuid, Gender defaultGender) {
        return profiles.computeIfAbsent(uuid, id -> new PlayerProfile(id, defaultGender));
    }

    public PlayerProfile get(UUID uuid) {
        return profiles.get(uuid);
    }

    public void remove(UUID uuid) {
        profiles.remove(uuid);
    }

    public void clear() {
        profiles.clear();
    }
}
