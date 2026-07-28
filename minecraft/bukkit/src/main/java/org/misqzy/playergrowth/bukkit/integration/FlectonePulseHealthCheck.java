package org.misqzy.playergrowth.bukkit.integration;

import net.flectone.pulse.execution.dispatcher.MessageDispatcher;
import net.flectone.pulse.execution.pipeline.MessagePipeline;
import net.flectone.pulse.service.FPlayerService;
import net.flectone.pulse.util.file.FileFacade;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * One consolidated, admin-visible report of every FlectonePulse integration
 * point this plugin has - {@code <fcolor:N>} palette reuse
 * ({@link FlectonePulseColorResolver}), server-id reuse
 * ({@link FlectonePulseServerIdResolver}), and outgoing message dispatch
 * ({@link FlectonePulseMessageDispatcher}) - run once per
 * {@code onEnable()}/{@code reload()}.
 *
 * <p>Each of those classes is built entirely against FlectonePulse's
 * undocumented internal API (see {@link FlectonePulseAccess}) and degrades
 * silently and independently by design - a chat message falling back to
 * local rendering can't afford to log on every single message. That means a
 * FlectonePulse update breaking one integration point was previously only
 * discoverable by noticing its *symptom* later (a missing gradient, a wrong
 * server id), one integration at a time, with nothing tying them together.
 * This is purely read-only diagnostics - it changes no behavior, just makes
 * "is FlectonePulse actually working with PlayerGrowth right now" answerable
 * from one place in the log instead of pieced together after the fact.</p>
 */
public final class FlectonePulseHealthCheck {

    private FlectonePulseHealthCheck() {}

    public static void logSummary(JavaPlugin plugin) {
        Logger logger = plugin.getLogger();

        if (!Bukkit.getPluginManager().isPluginEnabled("FlectonePulse")) {
            logger.info("[FlectonePulse] Not installed/enabled - every integration (fcolor palette, server-id "
                    + "reuse, message dispatch) is a no-op; PlayerGrowth works standalone.");
            return;
        }

        // Reuses the existing, more detailed fcolor-specific report rather
        // than duplicating its "not ready yet / module disabled" branching.
        FlectonePulseColorResolver.logDiagnostics(plugin);

        FileFacade fileFacade = FlectonePulseAccess.tryGetFileFacade();
        if (fileFacade == null) {
            logger.warning("[FlectonePulse] Detected as enabled, but its API wasn't reachable just now (either "
                    + "still starting up, or an incompatible version) - server-id reuse and message dispatch will "
                    + "also fall back to local behavior until the next /playergrowth reload succeeds in reaching it.");
            return;
        }

        String serverId = fileFacade.config().server();
        logger.info("[FlectonePulse] server-id reuse " + (serverId == null || serverId.isBlank()
                ? "inactive (FlectonePulse's own `server` config key is blank) - using PlayerGrowth's own."
                : "active (using \"" + serverId + "\")."));

        boolean dispatchReady = FlectonePulseAccess.tryGet(FPlayerService.class) != null
                && FlectonePulseAccess.tryGet(MessagePipeline.class) != null
                && FlectonePulseAccess.tryGet(MessageDispatcher.class) != null;
        logger.info("[FlectonePulse] message dispatch integration " + (dispatchReady
                ? "active - outgoing player messages try FlectonePulse's own pipeline first."
                : "unavailable right now (FPlayerService/MessagePipeline/MessageDispatcher not reachable) - "
                        + "messages will use local rendering only, until it is."));
    }
}
