package org.misqzy.playergrowth.paper.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.domain.Gender;
import org.misqzy.playergrowth.common.domain.GenderRegistry;
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.paper.PaperPlayerAdapter;

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
    public void setOwn(CommandSourceStack source, @Argument(value = "type", suggestions = "gender-types") String type) {
        CommandSender sender = source.getSender();
        if (sender instanceof Player player) {
            apply(player, player, type);
        } else {
            sender.sendMessage(core.messages().get("command.players-only"));
        }
    }

    @Command("gender set <target> <type>")
    @CommandDescription("Set another player's gender.")
    @Permission("playergrowth.gender.others")
    public void setOthers(CommandSourceStack source, @Argument("target") Player target,
                           @Argument(value = "type", suggestions = "gender-types") String type) {
        apply(source.getSender(), target, type);
    }

    private void apply(CommandSender sender, Player targetPlayer, String type) {
        GenderRegistry registry = core.genderRegistry();
        if (!registry.isEnabled()) {
            sender.sendMessage(core.messages().get("gender.disabled"));
            return;
        }
        if (!registry.isGenderInput(type)) {
            sender.sendMessage(core.messages().get("gender.unknown", Map.of("input", type)));
            return;
        }

        Gender gender = registry.resolve(type);
        GrowthEngine engine = core.growthEngine();
        PaperPlayerAdapter target = new PaperPlayerAdapter(targetPlayer);
        engine.setGender(target, gender,
                () -> sender.sendMessage(core.messages().get("gender.set", Map.of(
                        "player", targetPlayer.getName(),
                        "gender", registry.resolveDisplayName(gender, core.messages()::raw)))),
                () -> sender.sendMessage(core.messages().get("gender.set-failed")));
    }
}
