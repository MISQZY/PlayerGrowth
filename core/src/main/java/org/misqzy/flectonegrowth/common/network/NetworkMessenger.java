package org.misqzy.flectonegrowth.common.network;

import java.util.function.Consumer;

/**
 * Cross-server messenger contract. The Bukkit module implements this over
 * Bukkit's plugin-messaging channels (which require a routed connection
 * through a proxy); the Velocity module only ever <b>relays</b> bytes
 * between backend servers, it never constructs/applies a
 * {@link SyncMessage} itself since it has no player-entity access.
 */
public interface NetworkMessenger {

    /** No-op implementation used when {@code network.sync-enabled: false}. */
    NetworkMessenger DISABLED = new NetworkMessenger() {
        @Override public void broadcast(SyncMessage message) { }
        @Override public void onReceive(Consumer<SyncMessage> handler) { }
    };

    void broadcast(SyncMessage message);

    void onReceive(Consumer<SyncMessage> handler);
}
