package org.misqzy.playergrowth.common.domain;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory state for one online player. Replaces the three separate
 * customScales / genderCache / growthTimeCache maps the original plugin
 * kept in three unrelated classes - one profile per player is easier to
 * reason about and to invalidate on cross-server sync.
 */
public final class PlayerProfile {

    private final UUID uuid;
    private final AtomicReference<Double> customScale = new AtomicReference<>(null);
    private volatile Gender gender;
    private volatile long targetGrowthSeconds = -1;

    public PlayerProfile(UUID uuid, Gender initialGender) {
        this.uuid = uuid;
        this.gender = initialGender;
    }

    public UUID uuid() {
        return uuid;
    }

    public Double customScale() {
        return customScale.get();
    }

    public void setCustomScale(Double scale) {
        customScale.set(scale);
    }

    public boolean hasCustomScale() {
        return customScale.get() != null;
    }

    public Gender gender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    /** -1 means "not yet assigned/loaded". */
    public long targetGrowthSeconds() {
        return targetGrowthSeconds;
    }

    public void setTargetGrowthSeconds(long seconds) {
        this.targetGrowthSeconds = seconds;
    }
}
