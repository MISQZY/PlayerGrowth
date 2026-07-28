package org.misqzy.playergrowth.common.di;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.misqzy.playergrowth.common.config.ConfigView;
import org.misqzy.playergrowth.common.config.CoreConfig;
import org.misqzy.playergrowth.common.domain.GenderRegistry;
import org.misqzy.playergrowth.common.lang.Messages;
import org.misqzy.playergrowth.common.network.NetworkMessenger;
import org.misqzy.playergrowth.common.platform.Platform;
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.common.storage.Storage;
import org.misqzy.playergrowth.common.storage.StorageFactory;

import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Entry point every platform module boots through. Mirrors the role
 * FlectonePulse's per-platform {@code *Bootstrap} classes play: build the
 * platform-specific Guice module (Platform/Scheduler/NetworkMessenger/
 * PlayerLookup bindings), hand it here together with the loaded config, and
 * get back a ready {@link GrowthEngine}.
 */
public final class PlayerGrowthCore {

    private final Platform platform;
    private Injector injector;
    // volatile: reload() now runs its blocking file/DB I/O off the main
    // thread (see PlayerGrowthPlugin#reload) and swaps these in from there,
    // while commands/PAPI/etc. keep reading them on the main thread.
    private volatile CoreConfig config;
    private volatile Storage storage;
    private volatile GenderRegistry genderRegistry;
    private volatile Messages messages;

    private PlayerGrowthCore(Platform platform) {
        this.platform = platform;
    }

    /**
     * @param platform          platform-supplied handle (data folder, logger, scheduler, server id)
     * @param platformModule    binds {@link org.misqzy.playergrowth.common.platform.Scheduler},
     *                          {@link NetworkMessenger} and {@link org.misqzy.playergrowth.common.platform.PlayerLookup}
     * @param mainConfigView    parsed config.yml
     * @param genderConfigView  parsed gender.yml
     * @param messagesView      parsed active-locale localisation file
     * @param fcolors           looks up FlectonePulse's configured {@code <fcolor:N>} palette on demand (empty if
     *                          unavailable/disabled) - a live lookup, not a value, since the platform module's
     *                          FlectonePulse detection can lag behind {@code onEnable()} by a beat (see {@link Messages})
     */
    public static PlayerGrowthCore bootstrap(Platform platform, Module platformModule,
                                              ConfigView mainConfigView, ConfigView genderConfigView,
                                              ConfigView messagesView, Supplier<Map<Integer, String>> fcolors) {
        PlayerGrowthCore core = new PlayerGrowthCore(platform);
        core.buildAll(platformModule, mainConfigView, genderConfigView, messagesView, fcolors);
        return core;
    }

    private void buildAll(Module platformModule, ConfigView mainConfigView, ConfigView genderConfigView,
                           ConfigView messagesView, Supplier<Map<Integer, String>> fcolors) {
        this.config = new CoreConfig(mainConfigView);
        this.storage = initStorage(platform.logger(), platform.dataFolder(), config);
        this.genderRegistry = new GenderRegistry(genderConfigView, config.maxScale());
        this.messages = new Messages(messagesView, config.primaryColor(), config.secondaryColor(), fcolors);

        this.injector = Guice.createInjector(
                new CoreModule(config, storage, genderRegistry),
                platformModule,
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(Platform.class).toInstance(platform);
                    }
                }
        );
    }

    /** Falls back to YAML storage if the configured backend fails to initialise (e.g. bad DB credentials). */
    private static Storage initStorage(Logger logger, java.io.File dataFolder, CoreConfig config) {
        Storage primary = StorageFactory.create(logger, dataFolder, config);
        if (primary.initialize()) {
            if (!primary.testConnection()) {
                logger.warning("Storage connection test failed - some features may not work.");
            }
            return primary;
        }

        logger.severe("Primary storage (" + config.storageType() + ") failed to initialise. Falling back to YAML.");
        Storage fallback = StorageFactory.createFallback(logger, dataFolder);
        if (fallback.initialize()) {
            logger.warning("Using YAML storage as a fallback.");
            return fallback;
        }

        throw new IllegalStateException("All storage backends failed to initialise (configured + YAML fallback).");
    }

    /** Rebuilds config/storage/gender registry from freshly re-read files and hot-swaps them into the running engine. */
    public void reload(ConfigView mainConfigView, ConfigView genderConfigView, ConfigView messagesView, Supplier<Map<Integer, String>> fcolors) {
        CoreConfig newConfig = new CoreConfig(mainConfigView);
        GenderRegistry newGenderRegistry = new GenderRegistry(genderConfigView, newConfig.maxScale());

        Storage newStorage = storage;
        if (storage.type() != newConfig.storageType()) {
            Storage candidate = initStorage(platform.logger(), platform.dataFolder(), newConfig);
            storage.close();
            newStorage = candidate;
        }

        this.config = newConfig;
        this.storage = newStorage;
        this.genderRegistry = newGenderRegistry;
        this.messages = new Messages(messagesView, newConfig.primaryColor(), newConfig.secondaryColor(), fcolors);

        growthEngine().applyReload(newConfig, newStorage, newGenderRegistry);
    }

    public void shutdown() {
        if (storage != null) storage.close();
    }

    public GrowthEngine growthEngine() {
        return injector.getInstance(GrowthEngine.class);
    }

    public Messages messages() {
        return messages;
    }

    public CoreConfig config() {
        return config;
    }

    public GenderRegistry genderRegistry() {
        return genderRegistry;
    }

    public Storage storage() {
        return storage;
    }

    public Injector injector() {
        return injector;
    }
}
