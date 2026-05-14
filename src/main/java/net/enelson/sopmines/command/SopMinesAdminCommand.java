package net.enelson.sopmines.command;

import net.enelson.sopmines.SopMinesPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SopMinesAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUB = Arrays.asList(
            "next", "setnext", "setcurrent", "reload",
            "pos1", "pos2", "createautomine", "createmine", "setmineblocks"
    );
    private final SopMinesPlugin plugin;
    private final Map<UUID, Location> pos1ByPlayer = new HashMap<UUID, Location>();
    private final Map<UUID, Location> pos2ByPlayer = new HashMap<UUID, Location>();

    public SopMinesAdminCommand(SopMinesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sopmines.admin")) {
            plugin.sendMessage(sender, plugin.getMinesConfig().getNoPermissionMessage());
            return true;
        }
        if (args.length == 0) {
            plugin.sendMessage(sender, plugin.getMinesConfig().getUsageAdminMessage());
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            plugin.reloadPlugin();
            plugin.sendMessage(sender, plugin.getMinesConfig().getReloadedMessage());
            return true;
        }

        if ("pos1".equals(sub) || "pos2".equals(sub)) {
            if (!(sender instanceof Player)) {
                plugin.sendMessage(sender, "{prefix}&cКоманда только для игрока.");
                return true;
            }
            Player player = (Player) sender;
            Location location = player.getLocation().getBlock().getLocation();
            if ("pos1".equals(sub)) {
                pos1ByPlayer.put(player.getUniqueId(), location);
                plugin.sendMessage(sender, "{prefix}&aТочка pos1 сохранена: &f" + formatLocation(location));
            } else {
                pos2ByPlayer.put(player.getUniqueId(), location);
                plugin.sendMessage(sender, "{prefix}&aТочка pos2 сохранена: &f" + formatLocation(location));
            }
            return true;
        }

        if ("createmine".equals(sub)) {
            if (args.length < 4) {
                plugin.sendMessage(sender, "{prefix}&e/sopmines createmine <id> <durationSeconds> <blocksSpec> [surfaceBlocksSpec] [displayName...]");
                plugin.sendMessage(sender, "{prefix}&7Пример blocksSpec: STONE:70,COAL_ORE:30");
                return true;
            }
            String mineId = args[1].toLowerCase();
            int durationSeconds;
            try {
                durationSeconds = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                plugin.sendMessage(sender, "{prefix}&cdurationSeconds должен быть числом.");
                return true;
            }
            if (durationSeconds <= 0) {
                plugin.sendMessage(sender, "{prefix}&cdurationSeconds должен быть > 0.");
                return true;
            }
            String blocksSpec = args[3];
            String surfaceBlocksSpec = args.length >= 5 ? args[4] : "STONE:1,COBBLESTONE:1";
            String displayName = args.length >= 6 ? joinFrom(args, 5) : mineId;

            if (!isValidBlockSpec(blocksSpec)) {
                plugin.sendMessage(sender, "{prefix}&cНекорректный blocksSpec. Пример: STONE:70,COAL_ORE:30");
                return true;
            }
            if (!isValidBlockSpec(surfaceBlocksSpec)) {
                plugin.sendMessage(sender, "{prefix}&cНекорректный surfaceBlocksSpec. Пример: STONE:1,COBBLESTONE:1");
                return true;
            }

            String path = "mines." + mineId + ".";
            plugin.getConfig().set(path + "name", displayName);
            plugin.getConfig().set(path + "blocks", parseBlocksSpec(blocksSpec));
            plugin.getConfig().set(path + "surface-blocks", parseBlocksSpec(surfaceBlocksSpec));
            plugin.getConfig().set(path + "duration-seconds", durationSeconds);
            plugin.getConfig().set(path + "surface-layer.enabled", false);
            plugin.getConfig().set(path + "surface-layer.thickness", 1);
            plugin.saveConfig();
            plugin.reloadPlugin();
            plugin.sendMessage(sender, "{prefix}&aШахта создана: &f" + mineId);
            return true;
        }

        if ("setmineblocks".equals(sub)) {
            if (args.length < 3) {
                plugin.sendMessage(sender, "{prefix}&e/sopmines setmineblocks <mineId> <blocksSpec>");
                return true;
            }
            String mineId = args[1].toLowerCase();
            String blocksSpec = args[2];
            if (!plugin.getMinesConfig().getMines().containsKey(mineId)) {
                plugin.sendMessage(sender, plugin.getMinesConfig().getMineNotFoundMessage(), tokens("mine", mineId));
                return true;
            }
            if (!isValidBlockSpec(blocksSpec)) {
                plugin.sendMessage(sender, "{prefix}&cНекорректный blocksSpec. Пример: STONE:70,COAL_ORE:30");
                return true;
            }
            plugin.getConfig().set("mines." + mineId + ".blocks", parseBlocksSpec(blocksSpec));
            plugin.saveConfig();
            plugin.reloadPlugin();
            plugin.sendMessage(sender, "{prefix}&aБлоки шахты обновлены: &f" + mineId);
            return true;
        }

        if ("createautomine".equals(sub)) {
            if (!(sender instanceof Player)) {
                plugin.sendMessage(sender, "{prefix}&cКоманда только для игрока.");
                return true;
            }
            if (args.length < 3) {
                plugin.sendMessage(sender, "{prefix}&e/sopmines createautomine <id> <minesCsv>");
                plugin.sendMessage(sender, "{prefix}&7Перед этим поставь /sopmines pos1 и /sopmines pos2");
                return true;
            }
            Player player = (Player) sender;
            String automineId = args[1].toLowerCase();
            String minesCsv = args[2].toLowerCase();

            Location pos1 = pos1ByPlayer.get(player.getUniqueId());
            Location pos2 = pos2ByPlayer.get(player.getUniqueId());
            if (pos1 == null || pos2 == null) {
                plugin.sendMessage(sender, "{prefix}&cСначала задай /sopmines pos1 и /sopmines pos2");
                return true;
            }
            List<String> allowedMines = parseCsv(minesCsv);
            if (allowedMines.isEmpty()) {
                plugin.sendMessage(sender, "{prefix}&cНужна хотя бы одна шахта в списке mines.");
                return true;
            }
            for (String mineId : allowedMines) {
                if (!plugin.getMinesConfig().getMines().containsKey(mineId)) {
                    plugin.sendMessage(sender, plugin.getMinesConfig().getMineNotFoundMessage(), tokens("mine", mineId));
                    return true;
                }
            }

            File minesFolder = plugin.getMinesFolder();
            if (!minesFolder.exists() && !minesFolder.mkdirs()) {
                plugin.sendMessage(sender, "{prefix}&cНе удалось создать папку mines.");
                return true;
            }
            File file = new File(minesFolder, automineId + ".yml");
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("id", automineId);
            yml.set("enabled", true);
            yml.set("name", automineId);
            yml.set("position.world", pos1.getWorld().getName());
            yml.set("position.pos1.x", pos1.getBlockX());
            yml.set("position.pos1.y", pos1.getBlockY());
            yml.set("position.pos1.z", pos1.getBlockZ());
            yml.set("position.pos2.x", pos2.getBlockX());
            yml.set("position.pos2.y", pos2.getBlockY());
            yml.set("position.pos2.z", pos2.getBlockZ());
            yml.set("mines", allowedMines);
            yml.set("allow-consecutive-repeats", false);
            yml.set("on-update-commands", new ArrayList<String>());
            try {
                yml.save(file);
            } catch (Exception exception) {
                plugin.sendMessage(sender, "{prefix}&cНе удалось сохранить файл автошахты: " + file.getName());
                return true;
            }
            plugin.reloadPlugin();
            plugin.sendMessage(sender, "{prefix}&aАвтошахта создана: &f" + automineId);
            return true;
        }

        if ("next".equals(sub)) {
            if (args.length < 2) {
                plugin.sendMessage(sender, plugin.getMinesConfig().getUsageAdminMessage());
                return true;
            }
            String automineId = args[1].toLowerCase();
            if (!plugin.getAutoMineService().switchToNext(automineId)) {
                plugin.sendMessage(sender, plugin.getMinesConfig().getAutomineNotFoundMessage(), tokens("automine", automineId));
                return true;
            }
            String currentMine = plugin.getAutoMineService().getCurrentMineId(automineId);
            Map<String, String> placeholders = tokens("automine", automineId);
            placeholders.put("current", plugin.mineDisplayName(currentMine));
            plugin.sendMessage(sender, plugin.getMinesConfig().getSwitchedNextMessage(), placeholders);
            return true;
        }

        if ("setnext".equals(sub) || "setcurrent".equals(sub)) {
            if (args.length < 3) {
                plugin.sendMessage(sender, plugin.getMinesConfig().getUsageAdminMessage());
                return true;
            }
            String automineId = args[1].toLowerCase();
            String mineId = args[2].toLowerCase();
            if (!plugin.getMinesConfig().getAutoMines().containsKey(automineId)) {
                plugin.sendMessage(sender, plugin.getMinesConfig().getAutomineNotFoundMessage(), tokens("automine", automineId));
                return true;
            }
            if (!plugin.getMinesConfig().getMines().containsKey(mineId)) {
                plugin.sendMessage(sender, plugin.getMinesConfig().getMineNotFoundMessage(), tokens("mine", mineId));
                return true;
            }
            boolean success;
            if ("setnext".equals(sub)) {
                success = plugin.getAutoMineService().setNext(automineId, mineId);
                if (success) {
                    Map<String, String> placeholders = tokens("automine", automineId);
                    placeholders.put("next", plugin.mineDisplayName(mineId));
                    plugin.sendMessage(sender, plugin.getMinesConfig().getSetNextMessage(), placeholders);
                }
            } else {
                success = plugin.getAutoMineService().setCurrent(automineId, mineId);
                if (success) {
                    Map<String, String> placeholders = tokens("automine", automineId);
                    placeholders.put("current", plugin.mineDisplayName(mineId));
                    plugin.sendMessage(sender, plugin.getMinesConfig().getSetCurrentMessage(), placeholders);
                }
            }
            if (!success) {
                plugin.sendMessage(sender, plugin.getMinesConfig().getMineNotFoundMessage(), tokens("mine", mineId));
            }
            return true;
        }

        plugin.sendMessage(sender, plugin.getMinesConfig().getUsageAdminMessage());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sopmines.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(ADMIN_SUB, args[0]);
        }
        if (args.length == 2 && Arrays.asList("setmineblocks").contains(args[0].toLowerCase())) {
            return filter(new ArrayList<String>(plugin.getMinesConfig().getMines().keySet()), args[1]);
        }
        if (args.length == 2 && Arrays.asList("createmine", "createautomine").contains(args[0].toLowerCase())) {
            return Collections.emptyList();
        }
        if (args.length == 3 && "createautomine".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<String>(plugin.getMinesConfig().getMines().keySet()), args[2]);
        }
        if (args.length == 2 && Arrays.asList("next", "setnext", "setcurrent").contains(args[0].toLowerCase())) {
            return filter(new ArrayList<String>(plugin.getMinesConfig().getAutoMines().keySet()), args[1]);
        }
        if (args.length == 3 && Arrays.asList("setnext", "setcurrent").contains(args[0].toLowerCase())) {
            String automineId = args[1].toLowerCase();
            if (!plugin.getMinesConfig().getAutoMines().containsKey(automineId)) {
                return Collections.emptyList();
            }
            List<String> mines = new ArrayList<String>();
            mines.addAll(plugin.getMinesConfig().getAutoMines().get(automineId).getMineIds());
            return filter(mines, args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase();
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private Map<String, String> tokens(String key, String value) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(key, value);
        return map;
    }

    private String joinFrom(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName()
                + " " + location.getBlockX()
                + " " + location.getBlockY()
                + " " + location.getBlockZ();
    }

    private boolean isValidBlockSpec(String spec) {
        return !parseBlocksSpec(spec).isEmpty();
    }

    private List<String> parseBlocksSpec(String spec) {
        List<String> out = new ArrayList<String>();
        if (spec == null || spec.trim().isEmpty()) {
            return out;
        }
        String[] parts = spec.split(",");
        for (String part : parts) {
            String raw = part.trim();
            if (raw.isEmpty()) {
                continue;
            }
            String[] pair = raw.split(":");
            if (pair.length != 2) {
                continue;
            }
            String material = pair[0].trim().toUpperCase();
            int weight;
            try {
                weight = Integer.parseInt(pair[1].trim());
            } catch (NumberFormatException exception) {
                continue;
            }
            if (weight <= 0) {
                continue;
            }
            out.add(material + ":" + weight);
        }
        return out;
    }

    private List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<String>();
        if (csv == null || csv.trim().isEmpty()) {
            return out;
        }
        for (String raw : csv.split(",")) {
            String value = raw.trim().toLowerCase();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }
}
