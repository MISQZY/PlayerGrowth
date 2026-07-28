package org.misqzy.playergrowth.common.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.misqzy.playergrowth.common.config.CoreConfig;
import org.misqzy.playergrowth.common.domain.Gender;
import org.misqzy.playergrowth.common.domain.GenderRegistry;
import org.misqzy.playergrowth.common.domain.PlayerProfile;
import org.misqzy.playergrowth.common.domain.ProfileCache;
import org.misqzy.playergrowth.common.network.NetworkMessenger;
import org.misqzy.playergrowth.common.network.SyncMessage;
import org.misqzy.playergrowth.common.platform.Platform;
import org.misqzy.playergrowth.common.platform.PlatformPlayer;
import org.misqzy.playergrowth.common.platform.PlayerLookup;
import org.misqzy.playergrowth.common.storage.Storage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates growth/gender/custom-scale state. This is the direct
 * successor of the original {@code PlayerGrowthService}, but it no longer
 * touches Bukkit types directly - everything it needs comes through
 * {@link PlatformPlayer}, {@link Storage} and {@link NetworkMessenger}, so
 * the whole class is unit-testable without a server and reusable if a
 * second entity-capable platform is ever added.
 */
@Singleton
public final class GrowthEngine {

    private final Platform platform;
    private final ProfileCache profiles;
    private final NetworkMessenger messenger;
    private final PlayerLookup playerLookup;

    private volatile CoreConfig config;
    private volatile Storage storage;
    private volatile GenderRegistry genderRegistry;
    private volatile GrowthTimeAssigner timeAssigner;
    private volatile PlayTimeTracker playTimeTracker;

    @Inject
    public GrowthEngine(Platform platform, ProfileCache profiles, NetworkMessenger messenger, PlayerLookup playerLookup,
                         CoreConfig config, Storage storage, GenderRegistry genderRegistry) {
        this.platform = platform;
        this.profiles = profiles;
        this.messenger = messenger;
        this.playerLookup = playerLookup;
        this.config = config;
        this.storage = storage;
        this.genderRegistry = genderRegistry;
        this.timeAssigner = new GrowthTimeAssigner(config, storage);
        this.playTimeTracker = new PlayTimeTracker(storage);

        messenger.onReceive(this::applyIncomingSync);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Whether the periodic tick should run at all. The engine does not own
     * the repeating task itself - gathering "all online players" is a
     * platform concern ({@code Bukkit.getOnlinePlayers()} and friends), so
     * the platform module owns the {@link org.misqzy.playergrowth.common.platform.Scheduler}
     * timer and calls {@link #tick(List)} on each firing.
     */
    public boolean autoGrowthEnabled() {
        return config.autoGrowth();
    }

    public long tickIntervalMillis() {
        return Math.max(50L, (long) (config.growthUpdateIntervalSeconds() * 1000L));
    }

    /** Hot-swaps config/storage/gender registry after a reload; safe to call from any thread (fields are volatile). */
    public void applyReload(CoreConfig newConfig, Storage newStorage, GenderRegistry newGenderRegistry) {
        this.config = newConfig;
        this.storage = newStorage;
        this.genderRegistry = newGenderRegistry;
        this.timeAssigner = new GrowthTimeAssigner(newConfig, newStorage);
        this.playTimeTracker = new PlayTimeTracker(newStorage);
    }

    /** Called once per tick (main thread) with the currently online players. */
    public void tick(List<PlatformPlayer> onlinePlayers) {
        for (PlatformPlayer player : onlinePlayers) {
            if (config.pauseWhenBoxedIn() && player.isGrowthBlocked()) continue;
            if (isAtMaxGrowth(player)) continue;
            applyScale(player);
        }
    }

    // -----------------------------------------------------------------------
    // Player lifecycle
    // -----------------------------------------------------------------------

    /**
     * Loads a joining player's persisted state. Call {@code onLoaded} back on
     * the main thread.
     *
     * <p>Also called (with {@code onLoaded == null}) by {@link #refreshAfterReload}
     * for every already-online player - {@code freshLoad} below distinguishes
     * that case from a genuine join so {@link PlayTimeTracker#recordJoin} (which
     * bumps the persisted session count) only ever fires on a real join,
     * not on every reload.</p>
     *
     * <p>The DB-backed playtime tracker is only ever consulted when
     * {@link CoreConfig#networkSyncEnabled()} is on - without a synced
     * network there's no shared record to keep consistent, so growth just
     * reads {@link PlatformPlayer#playedSeconds()} directly (see
     * {@link #playedSeconds}) and this whole block is skipped, avoiding
     * needless DB writes on every join/quit for the common single-server
     * case. When sync is on but this server's own id is in
     * {@link CoreConfig#networkBlocklist()} (e.g. a hub/lobby), a genuine
     * join still only reads ({@link PlayTimeTracker#loadInto}), never
     * records ({@link PlayTimeTracker#recordJoin}) - time spent here must
     * not bump {@code last_seen}/{@code sessions} or it would count toward
     * play_time the moment the player leaves.</p>
     */
    public void loadPlayer(PlatformPlayer player, Runnable onLoaded) {
        platform.scheduler().runAsync(() -> {
            UUID uuid = player.uuid();
            boolean freshLoad = profiles.get(uuid) == null;
            PlayerProfile profile = profiles.getOrCreate(uuid, genderRegistry.getDefault());

            Double storedScale = storage.getCustomScale(uuid);
            profile.setCustomScale(storedScale);

            String genderKey = storage.getGenderKey(uuid);
            profile.setGender(genderRegistry.resolve(genderKey));

            timeAssigner.loadInto(profile);

            if (config.networkSyncEnabled()) {
                if (freshLoad && !isServerBlocked()) playTimeTracker.recordJoin(profile);
                else playTimeTracker.loadInto(profile);
            }

            platform.scheduler().runSync(() -> {
                if (player.isOnline()) applyScale(player);
                if (onLoaded != null) onLoaded.run();
            });
        });
    }

    /**
     * Checkpoints the player's accumulated playtime, then evicts their
     * cached profile. Skipped entirely when sync is off (nothing was ever
     * recorded to checkpoint) or this server is blocklisted (this session's
     * time must not count).
     */
    public void unloadPlayer(UUID uuid) {
        PlayerProfile profile = profiles.get(uuid);
        profiles.remove(uuid);
        if (!config.networkSyncEnabled() || isServerBlocked()) return;
        if (profile == null || profile.lastCheckpointSeconds() < 0) return;

        long now = Instant.now().getEpochSecond();
        long newTotal = profile.totalPlayedSeconds() + (now - profile.lastCheckpointSeconds());
        PlayTimeTracker tracker = playTimeTracker;
        platform.scheduler().runAsync(() -> tracker.checkpoint(uuid, newTotal, now));
    }

    // -----------------------------------------------------------------------
    // Scale
    // -----------------------------------------------------------------------

    public double minScale() {
        return Math.max(0.01, Math.min(config.minScale(), config.maxScale()));
    }

    /** The scale a player is actually at right now - their custom/growth-derived attribute value if set, else {@link #minScale()} as the starting point before it's ever been applied. */
    public double effectiveScale(PlatformPlayer player) {
        Double scale = player.currentScale();
        return scale != null ? scale : minScale();
    }

    public double maxScaleFor(PlatformPlayer player) {
        PlayerProfile profile = profiles.get(player.uuid());
        Gender gender = profile != null ? profile.gender() : genderRegistry.getDefault();
        return genderRegistry.getMaxScaleFor(gender);
    }

    public Gender genderOf(PlatformPlayer player) {
        PlayerProfile profile = profiles.get(player.uuid());
        return profile != null ? profile.gender() : genderRegistry.getDefault();
    }

    public GenderRegistry genderRegistry() {
        return genderRegistry;
    }

    /**
     * Current played seconds driving growth math.
     *
     * <p>Without a synced network ({@link CoreConfig#networkSyncEnabled()}
     * off), falls back to {@link PlatformPlayer#playedSeconds()} - the
     * server's own local statistic - exactly as before centralized playtime
     * tracking existed; there's no shared record to keep consistent with a
     * single server. With sync on, reads the DB-backed
     * {@code total + (now - last)} reconstruction instead, mirroring
     * FlectonePulse's own {@code TOTAL_DYNAMIC} - unless this server is
     * listed in {@link CoreConfig#networkBlocklist()}, in which case time
     * spent here is frozen out: the persisted total is returned as-is, with
     * no live elapsed-time addition.</p>
     */
    private long playedSeconds(PlatformPlayer player, PlayerProfile profile) {
        if (!config.networkSyncEnabled()) return player.playedSeconds();
        if (profile == null) return 0L;
        if (isServerBlocked()) return profile.totalPlayedSeconds();
        if (profile.lastCheckpointSeconds() < 0) return 0L;
        return profile.totalPlayedSeconds() + (Instant.now().getEpochSecond() - profile.lastCheckpointSeconds());
    }

    /** Whether this server's own id is in {@link CoreConfig#networkBlocklist()} - its in-game time should not count toward play_time. */
    private boolean isServerBlocked() {
        return config.networkBlocklist().contains(platform.serverId());
    }

    public boolean isAtMaxGrowth(PlatformPlayer player) {
        PlayerProfile profile = profiles.get(player.uuid());
        if (profile == null || profile.hasCustomScale()) return false;

        Double scale = player.currentScale();
        if (scale == null) return false;

        double max = maxScaleFor(player);
        long target = profile.targetGrowthSeconds();
        if (target <= 0) return scale >= max;

        double progress = ScaleMath.progress(playedSeconds(player, profile), target);
        return scale >= max && progress >= 1.0;
    }

    public long secondsUntilFullGrowth(PlatformPlayer player) {
        PlayerProfile profile = profiles.get(player.uuid());
        long target = profile != null ? profile.targetGrowthSeconds() : config.growTimeSeconds();
        return ScaleMath.secondsRemaining(playedSeconds(player, profile), target);
    }

    /** Progress toward this player's natural growth target, in {@code [0.0, 1.0]}. Custom-scale players have no growth curve to track, so they read as fully progressed. */
    public double growthProgress(PlatformPlayer player) {
        PlayerProfile profile = profiles.get(player.uuid());
        if (profile == null) return 0.0;
        if (profile.hasCustomScale()) return 1.0;

        long target = profile.targetGrowthSeconds();
        if (target <= 0) return 1.0;

        return ScaleMath.progress(playedSeconds(player, profile), target);
    }

    public boolean hasCustomScale(PlatformPlayer player) {
        PlayerProfile profile = profiles.get(player.uuid());
        return profile != null && profile.hasCustomScale();
    }

    /** Recomputes and applies the effective scale for one online player. Main thread only. */
    public void applyScale(PlatformPlayer player) {
        PlayerProfile profile = profiles.getOrCreate(player.uuid(), genderRegistry.getDefault());
        Double custom = profile.customScale();

        if (custom != null) {
            player.applyScale(Math.min(custom, maxScaleFor(player)));
            return;
        }

        double max = maxScaleFor(player);
        long target = profile.targetGrowthSeconds();
        if (target <= 0) {
            player.applyScale(max);
            return;
        }

        double progress = ScaleMath.progress(playedSeconds(player, profile), target);
        player.applyScale(ScaleMath.scaleAtProgress(progress, minScale(), max));
    }

    /**
     * Re-derives every online player's cached growth-timing/custom-scale/
     * gender state from the just-reloaded config/storage, then reapplies
     * scale - call after {@link org.misqzy.playergrowth.common.di.PlayerGrowthCore#reload}
     * so growth-setting changes (e.g. {@code growth.time-minutes}) take
     * effect immediately instead of only on a player's next join.
     *
     * <p>{@link #applyScale} alone isn't enough here: it reads
     * {@link PlayerProfile#targetGrowthSeconds()} as-is, and that field is
     * only ever populated once, at {@link #loadPlayer}. Without re-running
     * {@link #loadPlayer}, an already-online player's target growth time
     * stays whatever it was computed as under the pre-reload config until
     * they relog - which was the actual bug being fixed here, not just a
     * missing scale refresh.</p>
     */
    public void refreshAfterReload(List<PlatformPlayer> onlinePlayers) {
        for (PlatformPlayer player : onlinePlayers) loadPlayer(player, null);
    }

    public void setCustomScale(PlatformPlayer player, double scale, Runnable onSuccess, Runnable onFailure) {
        double min = minScale();
        double max = maxScaleFor(player);
        if (scale < min || scale > max) {
            if (onFailure != null) onFailure.run();
            return;
        }

        UUID uuid = player.uuid();
        platform.scheduler().runAsync(() -> {
            boolean ok = storage.setCustomScale(uuid, scale);
            platform.scheduler().runSync(() -> {
                if (ok) {
                    profiles.getOrCreate(uuid, genderRegistry.getDefault()).setCustomScale(scale);
                    player.applyScale(scale);
                    messenger.broadcast(SyncMessage.scaleSet(uuid, scale, platform.serverId()));
                    if (onSuccess != null) onSuccess.run();
                } else if (onFailure != null) {
                    onFailure.run();
                }
            });
        });
    }

    public void removeCustomScale(PlatformPlayer player, Runnable onSuccess, Runnable onFailure) {
        UUID uuid = player.uuid();
        platform.scheduler().runAsync(() -> {
            boolean ok = storage.removeCustomScale(uuid);
            platform.scheduler().runSync(() -> {
                if (ok) {
                    PlayerProfile profile = profiles.get(uuid);
                    if (profile != null) profile.setCustomScale(null);
                    if (player.isOnline()) applyScale(player);
                    messenger.broadcast(SyncMessage.scaleRemoved(uuid, platform.serverId()));
                    if (onSuccess != null) onSuccess.run();
                } else if (onFailure != null) {
                    onFailure.run();
                }
            });
        });
    }

    // -----------------------------------------------------------------------
    // Gender
    // -----------------------------------------------------------------------

    public void setGender(PlatformPlayer player, Gender gender, Runnable onSuccess, Runnable onFailure) {
        UUID uuid = player.uuid();
        platform.scheduler().runAsync(() -> {
            boolean ok = storage.setGenderKey(uuid, gender.key());
            if (!ok) {
                platform.scheduler().runSync(() -> { if (onFailure != null) onFailure.run(); });
                return;
            }

            double newMax = genderRegistry.getMaxScaleFor(gender);
            PlayerProfile profile = profiles.getOrCreate(uuid, genderRegistry.getDefault());
            Double currentCustom = profile.customScale();
            boolean needsClamp = currentCustom != null && currentCustom > newMax;
            boolean clampOk = !needsClamp || storage.setCustomScale(uuid, newMax);

            platform.scheduler().runSync(() -> {
                profile.setGender(gender);
                if (needsClamp && clampOk) profile.setCustomScale(newMax);
                if (player.isOnline()) applyScale(player);
                messenger.broadcast(SyncMessage.genderSet(uuid, gender.key(), platform.serverId()));
                if (onSuccess != null) onSuccess.run();
            });
        });
    }

    // -----------------------------------------------------------------------
    // Network sync (incoming)
    // -----------------------------------------------------------------------

    private void applyIncomingSync(SyncMessage message) {
        if (platform.serverId().equals(message.sourceServerId())) return; // echo guard

        platform.scheduler().runSync(() -> {
            PlayerProfile profile = profiles.get(message.playerUuid());
            if (profile == null) return; // player not online on this server, nothing to refresh

            switch (message.type()) {
                case SCALE_SET -> profile.setCustomScale(message.doubleValue());
                case SCALE_REMOVED -> profile.setCustomScale(null);
                case GENDER_SET -> profile.setGender(genderRegistry.resolve(message.stringValue()));
            }

            PlatformPlayer online = playerLookup.find(message.playerUuid());
            if (online != null && online.isOnline()) applyScale(online);
        });
    }
}
