package org.misqzy.playergrowth.common.network;

import java.util.UUID;

/**
 * A cross-server cache-invalidation event. The plugin's source of truth is
 * always the shared database (MySQL/MariaDB) configured identically on
 * every backend server; this message only tells sibling servers "re-apply
 * this player's state now" instead of waiting for their next natural
 * read - it is a latency optimisation, not a replacement for the DB.
 */
public final class SyncMessage {

    public enum Type { SCALE_SET, SCALE_REMOVED, GENDER_SET }

    private final Type type;
    private final UUID playerUuid;
    private final String stringValue;
    private final double doubleValue;
    private final String sourceServerId;

    private SyncMessage(Type type, UUID playerUuid, String stringValue, double doubleValue, String sourceServerId) {
        this.type = type;
        this.playerUuid = playerUuid;
        this.stringValue = stringValue;
        this.doubleValue = doubleValue;
        this.sourceServerId = sourceServerId;
    }

    public static SyncMessage scaleSet(UUID uuid, double scale, String sourceServerId) {
        return new SyncMessage(Type.SCALE_SET, uuid, null, scale, sourceServerId);
    }

    public static SyncMessage scaleRemoved(UUID uuid, String sourceServerId) {
        return new SyncMessage(Type.SCALE_REMOVED, uuid, null, 0, sourceServerId);
    }

    public static SyncMessage genderSet(UUID uuid, String genderKey, String sourceServerId) {
        return new SyncMessage(Type.GENDER_SET, uuid, genderKey, 0, sourceServerId);
    }

    static SyncMessage raw(Type type, UUID uuid, String stringValue, double doubleValue, String sourceServerId) {
        return new SyncMessage(type, uuid, stringValue, doubleValue, sourceServerId);
    }

    public Type type() { return type; }
    public UUID playerUuid() { return playerUuid; }
    public String stringValue() { return stringValue; }
    public double doubleValue() { return doubleValue; }
    public String sourceServerId() { return sourceServerId; }
}
