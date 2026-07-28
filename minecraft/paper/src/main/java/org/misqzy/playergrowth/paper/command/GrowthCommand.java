package org.misqzy.playergrowth.paper.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.lang.TimeFormatter;
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.common.service.ScaleMath;
import org.misqzy.playergrowth.paper.PaperPlayerAdapter;
import org.misqzy.playergrowth.paper.PlayerGrowthMessages;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Read-only view of a player's current growth state, split into three
 * focused subcommands rather than one combined dump - {@code summary}
 * shows everything, {@code height}/{@code gender} show just that one
 * piece. Reuses the same {@code playergrowth.info}/{@code .others}
 * permissions across all three - they're all "view growth info", just
 * scoped differently, not separate capabilities.
 */
public final class GrowthCommand {

    private final PlayerGrowthCore core;

    public GrowthCommand(PlayerGrowthCore core) {
        this.core = core;
    }

    @Command("growth summary")
    @CommandDescription("View your own growth summary.")
    @Permission("playergrowth.info")
    public void summaryOwn(CommandSourceStack source) {
        requirePlayer(source, player -> showSummary(player, player));
    }

    @Command("growth summary <target>")
    @CommandDescription("View another player's growth summary.")
    @Permission("playergrowth.info.others")
    public void summaryOthers(CommandSourceStack source, @Argument("target") Player target) {
        showSummary(source.getSender(), target);
    }

    @Command("growth height")
    @CommandDescription("View your own height info.")
    @Permission("playergrowth.info")
    public void heightOwn(CommandSourceStack source) {
        requirePlayer(source, player -> showHeight(player, player));
    }

    @Command("growth height <target>")
    @CommandDescription("View another player's height info.")
    @Permission("playergrowth.info.others")
    public void heightOthers(CommandSourceStack source, @Argument("target") Player target) {
        showHeight(source.getSender(), target);
    }

    @Command("growth gender")
    @CommandDescription("View your own gender info.")
    @Permission("playergrowth.info")
    public void genderOwn(CommandSourceStack source) {
        requirePlayer(source, player -> showGender(player, player));
    }

    @Command("growth gender <target>")
    @CommandDescription("View another player's gender info.")
    @Permission("playergrowth.info.others")
    public void genderOthers(CommandSourceStack source, @Argument("target") Player target) {
        showGender(source.getSender(), target);
    }

    private void requirePlayer(CommandSourceStack source, Consumer<Player> action) {
        CommandSender sender = source.getSender();
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            PlayerGrowthMessages.send(core, sender, "command.players-only");
        }
    }

    private void showSummary(CommandSender sender, Player targetPlayer) {
        sendHeader(sender, targetPlayer);
        sendHeight(sender, targetPlayer);
        sendGender(sender, targetPlayer);
    }

    private void showHeight(CommandSender sender, Player targetPlayer) {
        sendHeader(sender, targetPlayer);
        sendHeight(sender, targetPlayer);
    }

    private void showGender(CommandSender sender, Player targetPlayer) {
        sendHeader(sender, targetPlayer);
        sendGender(sender, targetPlayer);
    }

    private void sendHeader(CommandSender sender, Player targetPlayer) {
        PlayerGrowthMessages.send(core, sender, "growth-info.header", Map.of("player", targetPlayer.getName()));
    }

    private void sendGender(CommandSender sender, Player targetPlayer) {
        GrowthEngine engine = core.growthEngine();
        PaperPlayerAdapter target = new PaperPlayerAdapter(targetPlayer);
        PlayerGrowthMessages.send(core, sender, "growth-info.gender", Map.of(
                "gender", core.genderRegistry().resolveDisplayName(engine.genderOf(target), core.messages()::raw)));
    }

    /** Height value plus growth progress - progress is a height-growth metric, so it belongs with height, not the generic summary alone. */
    private void sendHeight(CommandSender sender, Player targetPlayer) {
        GrowthEngine engine = core.growthEngine();
        PaperPlayerAdapter target = new PaperPlayerAdapter(targetPlayer);

        Double scale = target.currentScale();
        double current = scale != null ? scale : engine.minScale();

        PlayerGrowthMessages.send(core, sender, "growth-info.height", Map.of(
                "value", ScaleMath.formatValue(current),
                "unit", core.messages().heightUnit(),
                "gender", core.genderRegistry().resolveDisplayName(engine.genderOf(target), core.messages()::raw)));

        if (engine.isAtMaxGrowth(target)) {
            PlayerGrowthMessages.send(core, sender, "growth-info.max-reached");
        } else {
            long remaining = engine.secondsUntilFullGrowth(target);
            PlayerGrowthMessages.send(core, sender, "growth-info.remaining", Map.of(
                    "time", TimeFormatter.format(remaining, core.messages())));
        }
    }
}
