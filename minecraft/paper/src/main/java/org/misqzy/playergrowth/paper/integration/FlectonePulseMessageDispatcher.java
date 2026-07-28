package org.misqzy.playergrowth.paper.integration;

import net.flectone.pulse.execution.dispatcher.MessageDispatcher;
import net.flectone.pulse.execution.pipeline.MessagePipeline;
import net.flectone.pulse.model.entity.FPlayer;
import net.flectone.pulse.model.event.message.MessageSendEvent;
import net.flectone.pulse.model.event.message.context.MessageContext;
import net.flectone.pulse.service.FPlayerService;
import net.flectone.pulse.util.constant.ModuleName;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * Routes a message to a player through FlectonePulse's own send pipeline -
 * {@code MessagePipeline.build} (raw MiniMessage string -&gt; {@code Component},
 * resolving {@code <fcolor:N>} via FlectonePulse's own {@code FColorModule}
 * listener rather than {@code Messages}' local resolver) followed by
 * {@code MessageDispatcher.dispatch} (actual delivery, through FlectonePulse's
 * internal event bus) - instead of this plugin's own {@code Messages} +
 * {@code CommandSender#sendMessage}. {@link #trySend} is the sole entry
 * point; every call site falls back to local rendering when it returns
 * {@code false}.
 *
 * <p>Same undocumented-internal-API caveat as {@link FlectonePulseAccess}
 * (whose {@link FlectonePulseAccess#tryGet} this is built on): confirmed the
 * exact classes/signatures below by decompiling the real
 * {@code core-1.12.0.jar} with {@code javap}, not guessed from documentation.
 * A few things worth recording since they aren't obvious from the method
 * signatures alone:</p>
 * <ul>
 *   <li>{@code MessageDispatcher.dispatch(MessageSendEvent)} does not parse
 *   or format anything itself - it only fires an already-built
 *   {@code Component} through FlectonePulse's internal event bus for
 *   delivery. The raw-string parsing (including {@code <fcolor:N>}) happens
 *   earlier, in {@code MessagePipeline.build(MessageContext)} - both steps
 *   are needed, not just {@code dispatch} alone.</li>
 *   <li>{@code FColorModule} (reacting to the {@code MessageFormattingEvent}
 *   {@code MessagePipeline.build} dispatches internally) only touches the
 *   message at all if the raw string contains {@code <fcolor}, and adds its
 *   own tag resolver on top of whatever this class supplies - so the
 *   {@link TagResolver} handed in here must cover {@code <primary>}/
 *   {@code <secondary>} and this call's placeholders, but deliberately not
 *   {@code <fcolor:N>} (see {@code Messages#externalDispatchResolvers}).</li>
 *   <li>{@code new MessageSendEvent(ModuleName, FPlayer, Component)} is
 *   FlectonePulse's own convenience constructor for exactly this "just
 *   deliver one system message to one player" case - it sets sender and
 *   receiver to the same {@code FPlayer} and defaults to chat delivery
 *   ({@code Destination.DEFAULT_TYPE == Type.CHAT}, confirmed in the
 *   decompiled bytecode).</li>
 *   <li>{@code FPlayer}/{@code MessageSendEvent} are player-centric -
 *   there's no equivalent path for console, so callers keep using local
 *   rendering for non-player senders regardless of FlectonePulse.</li>
 * </ul>
 */
public final class FlectonePulseMessageDispatcher {

    private FlectonePulseMessageDispatcher() {}

    /**
     * Attempts delivery via FlectonePulse. Returns {@code false} (nothing
     * sent) if FlectonePulse isn't installed/ready, the player isn't tracked
     * in its cache yet, or anything about the call throws - the caller
     * should fall back to its own rendering in every such case.
     */
    public static boolean trySend(Player player, String rawMiniMessage, TagResolver tagResolver) {
        try {
            FPlayerService fPlayerService = FlectonePulseAccess.tryGet(FPlayerService.class);
            if (fPlayerService == null) return false;

            FPlayer fPlayer = fPlayerService.getFPlayer(player.getUniqueId());
            if (fPlayer == null || fPlayer.isUnknown() || !fPlayer.isOnline()) return false;

            MessagePipeline pipeline = FlectonePulseAccess.tryGet(MessagePipeline.class);
            MessageDispatcher dispatcher = FlectonePulseAccess.tryGet(MessageDispatcher.class);
            if (pipeline == null || dispatcher == null) return false;

            MessageContext context = MessageContext.builder()
                    .sender(fPlayer)
                    .receiver(fPlayer)
                    .tagResolver(tagResolver)
                    .message(rawMiniMessage)
                    .build();
            Component rendered = pipeline.build(context);

            dispatcher.dispatch(new MessageSendEvent(ModuleName.INTEGRATION, fPlayer, rendered));
            return true;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
