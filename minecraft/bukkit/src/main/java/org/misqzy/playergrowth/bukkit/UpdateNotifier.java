package org.misqzy.playergrowth.bukkit;

import org.bukkit.command.CommandSender;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.platform.Scheduler;
import org.misqzy.playergrowth.common.update.UpdateChecker;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Platform glue around {@link UpdateChecker}: runs the GitHub check off the
 * main thread once per startup, logs a console warning if a newer version
 * is found, and notifies {@code playergrowth.update-notify} holders on join
 * afterwards. {@code update-checker.enabled} in {@code config.yml} (see
 * {@code CoreConfig#updateCheckerEnabled()}) gates both - re-checked live in
 * {@link #notifyIfPending} (not just once in {@link #checkAsync}), so
 * disabling it via {@code /playergrowth reload} silences further join
 * notifications immediately without a restart.
 */
public final class UpdateNotifier {

    private static final String REPO_OWNER = "MISQZY";
    private static final String REPO_NAME = "PlayerGrowth";

    private final PlayerGrowthCore core;
    private final Scheduler scheduler;
    private final Logger logger;

    private volatile String availableVersion;

    public UpdateNotifier(PlayerGrowthCore core, Scheduler scheduler, Logger logger) {
        this.core = core;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void checkAsync() {
        if (!core.config().updateCheckerEnabled()) return;

        scheduler.runAsync(() ->
                UpdateChecker.latestVersionIfNewer(REPO_OWNER, REPO_NAME, BuildVersion.VERSION, logger)
                        .ifPresent(latest -> {
                            availableVersion = latest;
                            logger.warning("A new PlayerGrowth version is available: v" + latest
                                    + " (running v" + BuildVersion.VERSION + "). Get it from "
                                    + "https://github.com/" + REPO_OWNER + "/" + REPO_NAME + "/releases");
                        }));
    }

    public void notifyIfPending(CommandSender sender) {
        String latest = availableVersion;
        if (latest == null) return;
        if (!core.config().updateCheckerEnabled()) return;
        if (!sender.hasPermission("playergrowth.update-notify")) return;

        PlayerGrowthMessages.send(core, sender, "admin.update-available", Map.of(
                "latest", latest,
                "current", BuildVersion.VERSION));
    }
}
