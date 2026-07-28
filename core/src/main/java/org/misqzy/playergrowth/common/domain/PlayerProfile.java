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

    private volatile long totalPlayedSeconds = 0;
    private volatile long lastCheckpointSeconds = -1;
    private volatile long firstJoinSeconds = -1;
    private volatile int sessions = 0;

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

    /** Accumulated played seconds as of {@link #lastCheckpointSeconds()} - excludes time elapsed since then. */
    public long totalPlayedSeconds() {
        return totalPlayedSeconds;
    }

    /** Epoch second of the last persisted checkpoint (join or quit). {@code -1} means "not yet loaded". */
    public long lastCheckpointSeconds() {
        return lastCheckpointSeconds;
    }

    /** Epoch second of this player's first-ever join. {@code -1} means "not yet loaded". */
    public long firstJoinSeconds() {
        return firstJoinSeconds;
    }

    /** Number of times this player has joined. */
    public int sessions() {
        return sessions;
    }

    /** Hydrates the playtime fields from a freshly loaded/upserted {@link PlayTime} row. */
    public void setPlayTime(long total, long last, long first, int sessions) {
        this.totalPlayedSeconds = total;
        this.lastCheckpointSeconds = last;
        this.firstJoinSeconds = first;
        this.sessions = sessions;
    }
}
