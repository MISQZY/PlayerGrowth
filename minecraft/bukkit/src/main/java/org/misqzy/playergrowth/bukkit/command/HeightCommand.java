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
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.common.service.ScaleMath;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Sets or clears a player's custom (manually fixed) growth scale.
 *
 * <p>The "others" variants live under a literal {@code set}/{@code remove}
 * prefix rather than {@code height <target> <value>} directly - Brigadier's
 * command tree allows any number of literal children per node but only one
 * argument-typed child, so a {@code target} (Player) argument and a
 * {@code value} (double) argument can't both sit directly under
 * {@code height} as siblings (this threw {@code AmbiguousNodeException} at
 * plugin enable on a real server - not something a local compile catches).</p>
 */
public final class HeightCommand {

    private final PlayerGrowthCore core;

    public HeightCommand(PlayerGrowthCore core) {
        this.core = core;
    }

    @Command("height <meters>")
    @CommandDescription("Set your own height, in meters.")
    @Permission("playergrowth.height")
    public void setOwn(CommandSender sender, @Argument(value = "meters", suggestions = "height-values") double meters) {
        requirePlayer(sender, player -> apply(player, player, meters));
    }

    @Command("height set <target> <meters>")
    @CommandDescription("Set another player's height, in meters.")
    @Permission("playergrowth.height.others")
    public void setOthers(CommandSender sender, @Argument("target") Player target,
                           @Argument(value = "meters", suggestions = "height-values") double meters) {
        apply(sender, target, meters);
    }

    @Command("height remove")
    @CommandDescription("Remove your custom height.")
    @Permission("playergrowth.height")
    public void removeOwn(CommandSender sender) {
        requirePlayer(sender, player -> remove(player, player));
    }

    @Command("height remove <target>")
    @CommandDescription("Remove another player's custom height.")
    @Permission("playergrowth.height.others")
    public void removeOthers(CommandSender sender, @Argument("target") Player target) {
        remove(sender, target);
    }

    private void requirePlayer(CommandSender sender, Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            LegacyText.send(sender, core.messages().get("command.players-only"));
        }
    }

    /**
     * {@code meters} is the player-facing unit (matches what {@code height-values}
     * suggests and what every display message shows via {@link ScaleMath#formatValue}) -
     * the Bukkit {@code Attribute.SCALE} the engine actually stores/applies is a
     * different unit entirely (1.0 == {@link ScaleMath#METERS_AT_SCALE_ONE} meters),
     * so the input has to be converted with {@link ScaleMath#fromMeters} before it
     * reaches {@link GrowthEngine#setCustomScale}. Passing the raw meters value
     * through unconverted was a real bug: a suggested value like {@code 1.88} would
     * always fail the engine's scale-space min/max check.
     */
    private void apply(CommandSender sender, Player targetPlayer, double meters) {
        GrowthEngine engine = core.growthEngine();
        BukkitPlayerAdapter target = new BukkitPlayerAdapter(targetPlayer);
        double scale = ScaleMath.fromMeters(meters);
        engine.setCustomScale(target, scale,
                () -> LegacyText.send(sender, core.messages().get("height.set", Map.of(
                        "player", targetPlayer.getName(),
                        "value", ScaleMath.formatValue(scale)))),
                () -> LegacyText.send(sender, core.messages().get("height.invalid", Map.of(
                        "min", ScaleMath.formatValue(engine.minScale()),
                        "max", ScaleMath.formatValue(engine.maxScaleFor(target))))));
    }

    private void remove(CommandSender sender, Player targetPlayer) {
        GrowthEngine engine = core.growthEngine();
        BukkitPlayerAdapter target = new BukkitPlayerAdapter(targetPlayer);
        engine.removeCustomScale(target,
                () -> LegacyText.send(sender, core.messages().get("height.removed", Map.of("player", targetPlayer.getName()))),
                () -> LegacyText.send(sender, core.messages().get("height.remove-failed")));
    }
}
