package org.misqzy.playergrowth.paper.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.misqzy.playergrowth.common.di.PlayerGrowthCore;
import org.misqzy.playergrowth.common.domain.Gender;
import org.misqzy.playergrowth.common.service.GrowthEngine;
import org.misqzy.playergrowth.common.service.ScaleMath;
import org.misqzy.playergrowth.paper.PlayerGrowthPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wires every command class through one {@link PaperCommandManager}, same
 * shape as the original plugin's {@code command/} package.
 *
 * <p>Cloud-Paper's native sender type is Paper's Brigadier
 * {@link CommandSourceStack}, not {@code CommandSender} - there is no
 * public API to build a {@code CommandSourceStack} back from an arbitrary
 * {@code CommandSender}, so a {@code SenderMapper} round-trip isn't
 * available here. Command methods take {@code CommandSourceStack} directly
 * and pull {@code CommandSourceStack#getSender()} themselves.</p>
 *
 * <p>The {@code height-values} suggestion provider reads {@code core}'s
 * live min/max scale on every call rather than a snapshot taken at
 * registration time, so tab-completion automatically reflects the current
 * config after {@code /playergrowth reload} - no explicit "rebuild suggestions" step
 * is needed.</p>
 */
public final class CommandRegistry {

    private final PaperCommandManager<CommandSourceStack> manager;

    public CommandRegistry(PlayerGrowthPlugin plugin, PlayerGrowthCore core) {
        this.manager = PaperCommandManager.builder()
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(plugin);

        manager.parserRegistry().registerSuggestionProvider("height-values",
                SuggestionProvider.blockingStrings((context, input) -> heightSuggestions(core)));
        manager.parserRegistry().registerSuggestionProvider("gender-types",
                SuggestionProvider.blockingStrings((context, input) -> genderSuggestions(core)));

        manager.exceptionController().registerHandler(InvalidSyntaxException.class, ctx -> {
            CommandSourceStack source = ctx.context().sender();
            source.getSender().sendMessage(core.messages().get("command.invalid-syntax",
                    Map.of("syntax", "/" + ctx.exception().correctSyntax())));
        });

        AnnotationParser<CommandSourceStack> parser = new AnnotationParser<>(manager, CommandSourceStack.class);
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
