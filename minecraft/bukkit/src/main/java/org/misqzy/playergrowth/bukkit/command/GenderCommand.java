package org.misqzy.playergrowth.bukkit.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.misqzy.playergrowth.bukkit.BukkitPlayerAdapter;
import org.misqzy.playergrowth.bukkit.PlayerGrowthMessages;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.domain.Gender;
import org.misqzy.playergrowth.common.domain.GenderRegistry;
import org.misqzy.playergrowth.common.service.GrowthEngine;

import java.util.Map;

/**
 * Sets a player's gender, which controls their maximum growth scale.
 *
 * <p>The "others" variant lives under a literal {@code set} prefix rather
 * than {@code gender <target> <type>} directly - see the equivalent note on
 * {@link HeightCommand}; a {@code target} (Player) argument and a
 * {@code type} (String) argument can't both sit directly under
 * {@code gender} as sibling argument nodes.</p>
 */
public final class GenderCommand {

    private final PlayerGrowthCore core;

    public GenderCommand(PlayerGrowthCore core) {
        this.core = core;
    }

    @Command("gender <type>")
    @CommandDescription("Set your own gender.")
    @Permission("playergrowth.gender")
    public void setOwn(CommandSender sender, @Argument(value = "type", suggestions = "gender-types") String type) {
        if (sender instanceof Player player) {
            apply(player, player, type);
        } else {
            PlayerGrowthMessages.send(core, sender, "command.players-only");
        }
    }

    @Command("gender set <target> <type>")
    @CommandDescription("Set another player's gender.")
    @Permission("playergrowth.gender.others")
    public void setOthers(CommandSender sender, @Argument("target") Player target,
                           @Argument(value = "type", suggestions = "gender-types") String type) {
        apply(sender, target, type);
    }

    private void apply(CommandSender sender, Player targetPlayer, String type) {
        GenderRegistry registry = core.genderRegistry();
        if (!registry.isEnabled()) {
            PlayerGrowthMessages.send(core, sender, "gender.disabled");
            return;
        }
        if (!registry.isGenderInput(type)) {
            PlayerGrowthMessages.send(core, sender, "gender.unknown", Map.of("input", type));
            return;
        }

        Gender gender = registry.resolve(type);
        GrowthEngine engine = core.growthEngine();
        BukkitPlayerAdapter target = new BukkitPlayerAdapter(targetPlayer);
        engine.setGender(target, gender,
                () -> PlayerGrowthMessages.send(core, sender, "gender.set", Map.of(
                        "player", targetPlayer.getName(),
                        "gender", registry.resolveDisplayName(gender, core.messages()::raw))),
                () -> PlayerGrowthMessages.send(core, sender, "gender.set-failed"));
    }
}
