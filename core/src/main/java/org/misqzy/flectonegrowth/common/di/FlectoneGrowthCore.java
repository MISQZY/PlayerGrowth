package org.misqzy.flectonegrowth.common.di;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.misqzy.flectonegrowth.common.platform.PlayerLookup;
import org.misqzy.flectonegrowth.common.platform.Scheduler;
import org.misqzy.flectonegrowth.common.config.ConfigView;
import org.misqzy.flectonegrowth.common.config.CoreConfig;
import org.misqzy.flectonegrowth.common.config.IntegrationsConfig;
import org.misqzy.flectonegrowth.common.domain.Gender;
import org.misqzy.flectonegrowth.common.domain.GenderRegistry;
import org.misqzy.flectonegrowth.common.lang.Messages;
import org.misqzy.flectonegrowth.common.network.NetworkMessenger;
import org.misqzy.flectonegrowth.common.platform.Platform;
import org.misqzy.flectonegrowth.common.platform.PlatformPlayer;
import org.misqzy.flectonegrowth.common.service.GrowthEngine;
import org.misqzy.flectonegrowth.common.storage.Storage;
import org.misqzy.flectonegrowth.common.storage.StorageFactory;

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
public final class FlectoneGrowthCore {

    private final Platform platform;
    private Injector injector;
    // volatile: reload() now runs its blocking file/DB I/O off the main
    // thread (see FlectoneGrowthPlugin#reload) and swaps these in from there,
    // while commands/PAPI/etc. keep reading them on the main thread.
    private volatile CoreConfig config;
    private volatile IntegrationsConfig integrations;
    private volatile Storage storage;
    private volatile GenderRegistry genderRegistry;
    private volatile Messages messages;

    private FlectoneGrowthCore(Platform platform) {
        this.platform = platform;
    }

    /**
     * @param platform          platform-supplied handle (data folder, logger, scheduler, server id)
     * @param platformModule    binds {@link Scheduler},
     *                          {@link NetworkMessenger} and {@link PlayerLookup}
     * @param mainConfigView    parsed config.yml
     * @param genderConfigView  parsed gender.yml
     * @param messagesByLocale  every loaded {@code localizations/messages_<code>.yml}, keyed by locale code -
     *                          not just the configured default, so {@link Messages} can serve a specific
     *                          recipient's own locale (e.g. a player's Minecraft client locale) on demand
     * @param integrationsConfigView parsed integrations.yml
     * @param fcolors           looks up FlectonePulse's configured {@code <fcolor:N>} palette on demand (empty if
     *                          unavailable/disabled) - a live lookup, not a value, since the platform module's
     *                          FlectonePulse detection can lag behind {@code onEnable()} by a beat (see {@link Messages})
     */
    public static FlectoneGrowthCore bootstrap(Platform platform, Module platformModule,
                                               ConfigView mainConfigView, ConfigView genderConfigView,
                                               Map<String, ConfigView> messagesByLocale, ConfigView integrationsConfigView,
                                               Supplier<Map<Integer, String>> fcolors) {
        FlectoneGrowthCore core = new FlectoneGrowthCore(platform);
        core.buildAll(platformModule, mainConfigView, genderConfigView, messagesByLocale, integrationsConfigView, fcolors);
        return core;
    }

    private void buildAll(Module platformModule, ConfigView mainConfigView, ConfigView genderConfigView,
                           Map<String, ConfigView> messagesByLocale, ConfigView integrationsConfigView,
                           Supplier<Map<Integer, String>> fcolors) {
        this.config = new CoreConfig(mainConfigView);
        this.integrations = new IntegrationsConfig(integrationsConfigView);
        this.storage = initStorage(platform.logger(), platform.dataFolder(), config);
        this.genderRegistry = new GenderRegistry(genderConfigView, config.maxScale());
        this.messages = new Messages(messagesByLocale, config.locale(), config.primaryColor(), config.secondaryColor(), fcolors);

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
    public void reload(ConfigView mainConfigView, ConfigView genderConfigView,
                        Map<String, ConfigView> messagesByLocale, ConfigView integrationsConfigView,
                        Supplier<Map<Integer, String>> fcolors) {
        CoreConfig newConfig = new CoreConfig(mainConfigView);
        GenderRegistry newGenderRegistry = new GenderRegistry(genderConfigView, newConfig.maxScale());

        Storage newStorage = storage;
        if (storage.type() != newConfig.storageType()) {
            Storage candidate = initStorage(platform.logger(), platform.dataFolder(), newConfig);
            storage.close();
            newStorage = candidate;
        }

        this.config = newConfig;
        this.integrations = new IntegrationsConfig(integrationsConfigView);
        this.storage = newStorage;
        this.genderRegistry = newGenderRegistry;
        this.messages = new Messages(messagesByLocale, newConfig.locale(), newConfig.primaryColor(), newConfig.secondaryColor(), fcolors);

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

    public IntegrationsConfig integrations() {
        return integrations;
    }

    public GenderRegistry genderRegistry() {
        return genderRegistry;
    }

    /** {@code gender}'s configured display name (falls back to its key if none is configured) - the composition every caller needs {@link GenderRegistry#resolveDisplayName} for, in one place instead of each re-wiring {@code messages()::raw} into it. */
    public String genderDisplayName(Gender gender) {
        return genderRegistry.resolveDisplayName(gender, messages::raw);
    }

    /** {@code player}'s *current* gender's display name - see {@link #genderDisplayName(Gender)}. */
    public String genderDisplayName(PlatformPlayer player) {
        return genderDisplayName(growthEngine().genderOf(player));
    }

    public Storage storage() {
        return storage;
    }

    public Injector injector() {
        return injector;
    }
}
