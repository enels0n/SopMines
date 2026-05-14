package net.enelson.sopmines;

import me.clip.placeholderapi.PlaceholderAPI;
import net.enelson.sopmines.command.SopMinesAdminCommand;
import net.enelson.sopmines.config.SopMinesConfig;
import net.enelson.sopmines.model.MineDefinition;
import net.enelson.sopmines.placeholder.SopMinesExpansion;
import net.enelson.sopmines.service.AutoMineService;
import net.enelson.sopli.lib.SopLib;
import net.enelson.sopli.lib.text.TextUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class SopMinesPlugin extends JavaPlugin {

    private TextUtils textUtils;
    private SopMinesConfig minesConfig;
    private AutoMineService autoMineService;
    private SopMinesExpansion expansion;

    @Override
    public void onEnable() {
        if (SopLib.getInstance() == null) {
            getLogger().severe("SopLib is required for SopMines.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.textUtils = SopLib.getInstance().getTextUtils();
        saveDefaultConfig();
        saveResource("mines/example-mine.yml", false);
        this.autoMineService = new AutoMineService(this);
        reloadPlugin();

        SopMinesAdminCommand command = new SopMinesAdminCommand(this);
        if (getCommand("sopmines") != null) {
            getCommand("sopmines").setExecutor(command);
            getCommand("sopmines").setTabCompleter(command);
        }

        registerExpansion();
    }

    @Override
    public void onDisable() {
        if (autoMineService != null) {
            autoMineService.shutdown();
        }
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        this.minesConfig = SopMinesConfig.load(getConfig(), getMinesFolder());
        this.autoMineService.reload(this.minesConfig);
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        registerExpansion();
    }

    public File getMinesFolder() {
        return new File(getDataFolder(), "mines");
    }

    public void sendMessage(CommandSender sender, String message) {
        sendMessage(sender, message, Collections.<String, String>emptyMap());
    }

    public void sendMessage(CommandSender sender, String message, Map<String, String> placeholders) {
        sender.sendMessage(formatMessage(sender, message, placeholders));
    }

    public String formatMessage(CommandSender sender, String template, Map<String, String> placeholders) {
        String resolved = withPrefix(template == null ? "" : template);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        if (sender instanceof Player && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders((Player) sender, resolved);
        }
        return color(resolved);
    }

    public String color(String input) {
        if (textUtils != null) {
            return textUtils.color(input == null ? "" : input);
        }
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public String withPrefix(String template) {
        return (template == null ? "" : template).replace("{prefix}", minesConfig.getPrefix());
    }

    public String mineDisplayName(String mineId) {
        if (mineId == null || mineId.isEmpty()) {
            return "";
        }
        MineDefinition mine = minesConfig.getMines().get(mineId);
        return mine == null ? mineId : mine.getDisplayName();
    }

    public String formatDuration(long seconds) {
        long value = Math.max(0L, seconds);
        long hours = TimeUnit.SECONDS.toHours(value);
        long minutes = TimeUnit.SECONDS.toMinutes(value) % 60L;
        long secs = value % 60L;
        return minesConfig.getTimeFormat()
                .replace("{hours}", Long.toString(hours))
                .replace("{minutes}", Long.toString(minutes))
                .replace("{seconds}", Long.toString(secs));
    }

    public SopMinesConfig getMinesConfig() {
        return minesConfig;
    }

    public AutoMineService getAutoMineService() {
        return autoMineService;
    }

    private void registerExpansion() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        this.expansion = new SopMinesExpansion(this);
        this.expansion.register();
    }
}
