package org.misqzy.playergrowth.bukkit;

import com.google.inject.AbstractModule;
import org.misqzy.playergrowth.common.network.NetworkMessenger;
import org.misqzy.playergrowth.common.platform.PlayerLookup;
import org.misqzy.playergrowth.common.platform.Scheduler;

/**
 * Binds the Bukkit-specific implementations of the contracts
 * {@link org.misqzy.playergrowth.common.di.CoreModule} deliberately leaves
 * unbound. Built once in {@link PlayerGrowthPlugin#onEnable()} and passed
 * into {@link org.misqzy.playergrowth.common.di.PlayerGrowthCore#bootstrap}.
 */
public final class BukkitModule extends AbstractModule {

    private final Scheduler scheduler;
    private final NetworkMessenger networkMessenger;
    private final PlayerLookup playerLookup;

    public BukkitModule(Scheduler scheduler, NetworkMessenger networkMessenger, PlayerLookup playerLookup) {
        this.scheduler = scheduler;
        this.networkMessenger = networkMessenger;
        this.playerLookup = playerLookup;
    }

    @Override
    protected void configure() {
        bind(Scheduler.class).toInstance(scheduler);
        bind(NetworkMessenger.class).toInstance(networkMessenger);
        bind(PlayerLookup.class).toInstance(playerLookup);
    }
}
