package org.misqzy.playergrowth.common.service;

import org.misqzy.playergrowth.common.config.CoreConfig;
import org.misqzy.playergrowth.common.domain.PlayerProfile;
import org.misqzy.playergrowth.common.storage.Storage;

import java.util.Random;
import java.util.UUID;

/**
 * Assigns each player's target growth time. In fixed mode everyone shares
 * one value from config; in range mode each player is given a random value
 * on first join, persisted so it survives restarts/relogs.
 *
 * <p><b>Storage calls in here are blocking</b> - call only from an async
 * thread (see {@link org.misqzy.playergrowth.common.platform.Scheduler#runAsync}).</p>
 */
public final class GrowthTimeAssigner {

    private static final Random RANDOM = new Random();

    private final CoreConfig config;
    private final Storage storage;

    public GrowthTimeAssigner(CoreConfig config, Storage storage) {
        this.config = config;
        this.storage = storage;
    }

    /** Loads (or assigns + persists) the target growth time into {@code profile}. Blocking - call off-thread. */
    public void loadInto(PlayerProfile profile) {
        if (!config.isRangeMode()) {
            profile.setTargetGrowthSeconds(config.growTimeSeconds());
            return;
        }

        UUID uuid = profile.uuid();
        Long stored = storage.getGrowthTimeSeconds(uuid);
        if (stored != null) {
            profile.setTargetGrowthSeconds(stored);
            return;
        }

        long assigned = randomSeconds();
        profile.setTargetGrowthSeconds(assigned);
        storage.setGrowthTimeSeconds(uuid, assigned);
    }

    private long randomSeconds() {
        long min = config.growTimeMinSeconds();
        long max = config.growTimeMaxSeconds();
        long range = max - min;
        long chosen = min + (range > 0 ? (long) RANDOM.nextInt((int) Math.min(range + 1, Integer.MAX_VALUE)) : 0);
        return chosen;
    }
}
