package org.misqzy.playergrowth.bukkit.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.misqzy.playergrowth.bukkit.BukkitPlayerAdapter;
import org.misqzy.playergrowth.bukkit.LegacyText;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.lang.TimeFormatter;
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.common.service.ScaleMath;

import java.util.Map;

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
    public void summaryOwn(CommandSender sender) {
        if (sender instanceof Player player) {
            showSummary(player, player);
        } else {
            LegacyText.send(sender, core.messages().get("command.players-only"));
        }
    }

    @Command("growth summary <target>")
    @CommandDescription("View another player's growth summary.")
    @Permission("playergrowth.info.others")
    public void summaryOthers(CommandSender sender, @Argument("target") Player target) {
        showSummary(sender, target);
    }

    @Command("growth height")
    @CommandDescription("View your own height info.")
    @Permission("playergrowth.info")
    public void heightOwn(CommandSender sender) {
        if (sender instanceof Player player) {
            showHeight(player, player);
        } else {
            LegacyText.send(sender, core.messages().get("command.players-only"));
        }
    }

    @Command("growth height <target>")
    @CommandDescription("View another player's height info.")
    @Permission("playergrowth.info.others")
    public void heightOthers(CommandSender sender, @Argument("target") Player target) {
        showHeight(sender, target);
    }

    @Command("growth gender")
    @CommandDescription("View your own gender info.")
    @Permission("playergrowth.info")
    public void genderOwn(CommandSender sender) {
        if (sender instanceof Player player) {
            showGender(player, player);
        } else {
            LegacyText.send(sender, core.messages().get("command.players-only"));
        }
    }

    @Command("growth gender <target>")
    @CommandDescription("View another player's gender info.")
    @Permission("playergrowth.info.others")
    public void genderOthers(CommandSender sender, @Argument("target") Player target) {
        showGender(sender, target);
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
        LegacyText.send(sender, core.messages().get("growth-info.header", Map.of("player", targetPlayer.getName())));
    }

    private void sendGender(CommandSender sender, Player targetPlayer) {
        GrowthEngine engine = core.growthEngine();
        BukkitPlayerAdapter target = new BukkitPlayerAdapter(targetPlayer);
        LegacyText.send(sender, core.messages().get("growth-info.gender", Map.of(
                "gender", core.genderRegistry().resolveDisplayName(engine.genderOf(target), core.messages()::raw))));
    }

    /** Height value plus growth progress - progress is a height-growth metric, so it belongs with height, not the generic summary alone. */
    private void sendHeight(CommandSender sender, Player targetPlayer) {
        GrowthEngine engine = core.growthEngine();
        BukkitPlayerAdapter target = new BukkitPlayerAdapter(targetPlayer);

        Double scale = target.currentScale();
        double current = scale != null ? scale : engine.minScale();

        LegacyText.send(sender, core.messages().get("growth-info.height", Map.of(
                "value", ScaleMath.formatValue(current),
                "unit", core.messages().heightUnit(),
                "gender", core.genderRegistry().resolveDisplayName(engine.genderOf(target), core.messages()::raw))));

        if (engine.isAtMaxGrowth(target)) {
            LegacyText.send(sender, core.messages().get("growth-info.max-reached"));
        } else {
            long remaining = engine.secondsUntilFullGrowth(target);
            LegacyText.send(sender, core.messages().get("growth-info.remaining", Map.of(
                    "time", TimeFormatter.format(remaining, core.messages()))));
        }
    }
}
