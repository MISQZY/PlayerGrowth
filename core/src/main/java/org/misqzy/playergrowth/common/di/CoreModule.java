package org.misqzy.playergrowth.common.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.misqzy.playergrowth.common.config.CoreConfig;
import org.misqzy.playergrowth.common.domain.GenderRegistry;
import org.misqzy.playergrowth.common.domain.ProfileCache;
import org.misqzy.playergrowth.common.storage.Storage;

/**
 * Binds the pieces every platform shares. {@link org.misqzy.playergrowth.common.platform.Platform},
 * {@link org.misqzy.playergrowth.common.platform.Scheduler}, {@link org.misqzy.playergrowth.common.network.NetworkMessenger}
 * and {@link org.misqzy.playergrowth.common.platform.PlayerLookup} are intentionally
 * <b>not</b> bound here - each platform module supplies its own Guice module
 * for those, since only the platform knows how to implement them.
 *
 * <p>{@link CoreConfig}, {@link Storage} and {@link GenderRegistry} are
 * bound as plain instances handed in at construction time (see
 * {@link PlayerGrowthCore}) rather than built lazily by Guice, because they
 * are hot-swapped as a unit on {@code /playergrowth reload} - Guice's providers would
 * make that swap harder to reason about than a small owning facade class.</p>
 */
public final class CoreModule extends AbstractModule {

    private final CoreConfig config;
    private final Storage storage;
    private final GenderRegistry genderRegistry;

    public CoreModule(CoreConfig config, Storage storage, GenderRegistry genderRegistry) {
        this.config = config;
        this.storage = storage;
        this.genderRegistry = genderRegistry;
    }

    @Override
    protected void configure() {
        bind(ProfileCache.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    CoreConfig provideConfig() {
        return config;
    }

    @Provides
    @Singleton
    Storage provideStorage() {
        return storage;
    }

    @Provides
    @Singleton
    GenderRegistry provideGenderRegistry() {
        return genderRegistry;
    }
}
