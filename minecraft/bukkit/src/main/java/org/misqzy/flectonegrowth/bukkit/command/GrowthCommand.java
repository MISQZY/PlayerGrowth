package org.misqzy.flectonegrowth.bukkit.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.misqzy.flectonegrowth.bukkit.BukkitPlayerAdapter;
import org.misqzy.flectonegrowth.bukkit.FlectoneGrowthMessages;
import org.misqzy.flectonegrowth.common.di.FlectoneGrowthCore;
import org.misqzy.flectonegrowth.common.lang.TimeFormatter;
import org.misqzy.flectonegrowth.common.service.GrowthEngine;
import org.misqzy.flectonegrowth.common.service.ScaleMath;

import java.util.Map;

/**
 * Read-only view of a player's current growth state, split into three
 * focused subcommands rather than one combined dump - {@code summary}
 * shows everything, {@code height}/{@code gender} show just that one
 * piece. Reuses the same {@code flectonegrowth.info}/{@code .others}
 * permissions across all three - they're all "view growth info", just
 * scoped differently, not separate capabilities.
 */
public final class GrowthCommand {

    private final FlectoneGrowthCore core;

    public GrowthCommand(FlectoneGrowthCore core) {
        this.core = core;
    }

    @Command("growth summary")
    @CommandDescription("View your own growth summary.")
    @Permission("flectonegrowth.info")
    public void summaryOwn(CommandSender sender) {
        CommandGuards.requirePlayer(core, sender, player -> showSummary(player, player));
    }

    @Command("growth summary <target>")
    @CommandDescription("View another player's growth summary.")
    @Permission("flectonegrowth.info.others")
    public void summaryOthers(CommandSender sender, @Argument("target") Player target) {
        showSummary(sender, target);
    }

    @Command("growth height")
    @CommandDescription("View your own height info.")
    @Permission("flectonegrowth.info")
    public void heightOwn(CommandSender sender) {
        CommandGuards.requirePlayer(core, sender, player -> showHeight(player, player));
    }

    @Command("growth height <target>")
    @CommandDescription("View another player's height info.")
    @Permission("flectonegrowth.info.others")
    public void heightOthers(CommandSender sender, @Argument("target") Player target) {
        showHeight(sender, target);
    }

    @Command("growth gender")
    @CommandDescription("View your own gender info.")
    @Permission("flectonegrowth.info")
    public void genderOwn(CommandSender sender) {
        CommandGuards.requirePlayer(core, sender, player -> showGender(player, player));
    }

    @Command("growth gender <target>")
    @CommandDescription("View another player's gender info.")
    @Permission("flectonegrowth.info.others")
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
        FlectoneGrowthMessages.send(core, sender, "growth-info.header", Map.of("player", targetPlayer.getName()));
    }

    private void sendGender(CommandSender sender, Player targetPlayer) {
        BukkitPlayerAdapter target = new BukkitPlayerAdapter(targetPlayer);
        FlectoneGrowthMessages.send(core, sender, "growth-info.gender", Map.of(
                "gender", core.genderDisplayName(target)));
    }

    /** Height value plus growth progress - progress is a height-growth metric, so it belongs with height, not the generic summary alone. */
    private void sendHeight(CommandSender sender, Player targetPlayer) {
        GrowthEngine engine = core.growthEngine();
        BukkitPlayerAdapter target = new BukkitPlayerAdapter(targetPlayer);

        double current = engine.effectiveScale(target);

        FlectoneGrowthMessages.send(core, sender, "growth-info.height", Map.of(
                "value", ScaleMath.formatValue(current),
                "unit", core.messages().heightUnit(),
                "gender", core.genderDisplayName(target)));

        if (engine.isAtMaxGrowth(target)) {
            FlectoneGrowthMessages.send(core, sender, "growth-info.max-reached");
        } else {
            long remaining = engine.secondsUntilFullGrowth(target);
            FlectoneGrowthMessages.send(core, sender, "growth-info.remaining", Map.of(
                    "time", TimeFormatter.format(remaining, core.messages())));
        }
    }
}
