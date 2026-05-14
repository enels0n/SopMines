package net.enelson.sopmines.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.enelson.sopmines.SopMinesPlugin;
import org.bukkit.OfflinePlayer;

public final class SopMinesExpansion extends PlaceholderExpansion {

    private final SopMinesPlugin plugin;

    public SopMinesExpansion(SopMinesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "sopmines";
    }

    @Override
    public String getAuthor() {
        return "E_NeLsOn";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.trim().isEmpty()) {
            return "";
        }
        String value = params.trim().toLowerCase();
        if (value.startsWith("current_")) {
            String automineId = value.substring("current_".length());
            String mineId = plugin.getAutoMineService().getCurrentMineId(automineId);
            return mineId == null ? "" : plugin.color(plugin.mineDisplayName(mineId));
        }
        if (value.startsWith("next_in_")) {
            String automineId = value.substring("next_in_".length());
            long seconds = plugin.getAutoMineService().getSecondsToNext(automineId);
            if (seconds < 0L) {
                return "";
            }
            return plugin.formatDuration(seconds);
        }
        if (value.startsWith("next_")) {
            String automineId = value.substring("next_".length());
            String mineId = plugin.getAutoMineService().getNextMineId(automineId);
            return mineId == null ? "" : plugin.color(plugin.mineDisplayName(mineId));
        }
        return "";
    }
}
