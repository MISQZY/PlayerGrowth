package org.misqzy.playergrowth.common.storage;

import org.misqzy.playergrowth.common.domain.PlayTime;

import java.util.UUID;

/**
 * Data-access contract. All methods are synchronous and blocking - callers
 * are expected to invoke them from an async thread (see
 * {@link org.misqzy.playergrowth.common.platform.Scheduler#runAsync}), the
 * same contract the original plugin used. Kept synchronous deliberately:
 * the growth engine already funnels every call through one executor, so a
 * CompletableFuture wrapper at this layer would only add indirection.
 */
public interface Storage {

    boolean initialize();

    boolean testConnection();

    StorageType type();

    void close();

    // Custom scale (manually set via /height <value>)
    Double getCustomScale(UUID uuid);
    boolean setCustomScale(UUID uuid, double scale);
    boolean removeCustomScale(UUID uuid);

    // Gender
    String getGenderKey(UUID uuid);
    boolean setGenderKey(UUID uuid, String genderKey);

    // Individually-assigned growth time (range mode)
    Long getGrowthTimeSeconds(UUID uuid);
    boolean setGrowthTimeSeconds(UUID uuid, long seconds);

    // Playtime tracking (first/last/total/sessions per player - modeled on
    // FlectonePulse's own scheme, see ARCHITECTURE.md "Playtime tracking").
    // Each row is keyed by (uuid, server) - `server` is "" for the shared
    // network-wide bucket (network.per-server: false) or the running
    // server's own id when tracking a per-server bucket (network.per-server:
    // true) - see GrowthEngine's serverKey().
    PlayTime getPlayTime(UUID uuid, String server);
    /** Creates the row on a brand new player (first=last=now, total=0, sessions=1); otherwise bumps last/sessions only, leaving first/total untouched. */
    boolean recordJoin(UUID uuid, String server, long nowEpochSeconds);
    /** Persists an updated running total and resets the checkpoint - used at quit. */
    boolean checkpointPlayTime(UUID uuid, String server, long totalSeconds, long nowEpochSeconds);
}
