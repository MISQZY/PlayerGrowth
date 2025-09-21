package org.misqzy.playerGrowth.lang;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.misqzy.playerGrowth.growth.GrowthManager;

public class PlaceholderAPIHook extends PlaceholderExpansion {
    private final GrowthManager growthManager;

    public PlaceholderAPIHook(GrowthManager growthManager) {
        this.growthManager = growthManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playerGrowth";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MISQZY";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        if (identifier.equals("height")) {
            return String.format("%.2f", growthManager.getPlayerHeight(player));
        }

        return null;
    }
}
