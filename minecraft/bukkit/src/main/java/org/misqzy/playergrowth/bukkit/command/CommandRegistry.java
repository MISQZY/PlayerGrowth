package org.misqzy.playergrowth.bukkit.command;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.bukkit.BukkitCommandManager;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.misqzy.playergrowth.bukkit.PlayerGrowthMessages;
import org.misqzy.playergrowth.bukkit.PlayerGrowthPlugin;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.domain.Gender;
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.common.service.ScaleMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wires every command class through one {@link BukkitCommandManager}.
 *
 * <p>Targets plain {@link CommandSender} via cloud-bukkit's legacy command
 * manager rather than Paper's native Brigadier {@code CommandSourceStack} -
 * the only command-registration API that works on stock Spigot/CraftBukkit
 * as well as Paper/Purpur, so one code path covers every Bukkit-API server
 * instead of needing a second, Paper-only module.
 * {@code BukkitCommandManager}'s constructor is {@code protected}, so it's
 * instantiated through a trivial anonymous subclass, matching Cloud's own
 * documented usage pattern for this class.</p>
 *
 * <p>The {@code height-values} suggestion provider reads {@code core}'s
 * live min/max scale on every call rather than a snapshot taken at
 * registration time, so tab-completion automatically reflects the current
 * config after {@code /playergrowth reload} - no explicit "rebuild
 * suggestions" step is needed.</p>
 */
public final class CommandRegistry {

    private final BukkitCommandManager<CommandSender> manager;

    public CommandRegistry(PlayerGrowthPlugin plugin, PlayerGrowthCore core) {
        try {
            this.manager = new BukkitCommandManager<>(
                    plugin, ExecutionCoordinator.simpleCoordinator(), SenderMapper.identity()) {};
        } catch (BukkitCommandManager.InitializationException e) {
            throw new IllegalStateException("Failed to initialise the Bukkit command manager", e);
        }

        manager.parserRegistry().registerSuggestionProvider("height-values",
                SuggestionProvider.blockingStrings((context, input) -> heightSuggestions(core)));
        manager.parserRegistry().registerSuggestionProvider("gender-types",
                SuggestionProvider.blockingStrings((context, input) -> genderSuggestions(core)));

        manager.exceptionController().registerHandler(InvalidSyntaxException.class, ctx ->
                PlayerGrowthMessages.send(core, ctx.context().sender(), "command.invalid-syntax",
                        Map.of("syntax", "/" + ctx.exception().correctSyntax())));

        AnnotationParser<CommandSender> parser = new AnnotationParser<>(manager, CommandSender.class);
        parser.parse(new HeightCommand(core));
        parser.parse(new GenderCommand(core));
        parser.parse(new GrowthCommand(core));
        parser.parse(new PluginAdminCommand(plugin));
        parser.parse(new HelpCommand(core));
    }

    private static List<String> heightSuggestions(PlayerGrowthCore core) {
        GrowthEngine engine = core.growthEngine();
        double min = engine.minScale();
        double max = core.config().maxScale();

        List<String> values = new ArrayList<>();
        double step = (max - min) / 4.0;
        for (int i = 0; i <= 4; i++) {
            values.add(ScaleMath.formatValue(min + step * i));
        }
        return values;
    }

    private static List<String> genderSuggestions(PlayerGrowthCore core) {
        List<String> values = new ArrayList<>();
        for (Gender gender : core.genderRegistry().all()) {
            values.add(gender.key());
        }
        return values;
    }
}
