package net.enelson.sopmines.config;

import net.enelson.sopmines.model.AutoMineDefinition;
import net.enelson.sopmines.model.MineDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SopMinesConfig {

    private final String prefix;
    private final String noPermissionMessage;
    private final String usageAdminMessage;
    private final String automineNotFoundMessage;
    private final String mineNotFoundMessage;
    private final String switchedNextMessage;
    private final String setNextMessage;
    private final String setCurrentMessage;
    private final String reloadedMessage;
    private final String timeFormat;
    private final boolean useFaweIfAvailable;
    private final int blocksPerTick;
    private final Map<String, MineDefinition> mines;
    private final Map<String, AutoMineDefinition> autoMines;

    private SopMinesConfig(String prefix,
                           String noPermissionMessage,
                           String usageAdminMessage,
                           String automineNotFoundMessage,
                           String mineNotFoundMessage,
                           String switchedNextMessage,
                           String setNextMessage,
                           String setCurrentMessage,
                           String reloadedMessage,
                           String timeFormat,
                           boolean useFaweIfAvailable,
                           int blocksPerTick,
                           Map<String, MineDefinition> mines,
                           Map<String, AutoMineDefinition> autoMines) {
        this.prefix = prefix;
        this.noPermissionMessage = noPermissionMessage;
        this.usageAdminMessage = usageAdminMessage;
        this.automineNotFoundMessage = automineNotFoundMessage;
        this.mineNotFoundMessage = mineNotFoundMessage;
        this.switchedNextMessage = switchedNextMessage;
        this.setNextMessage = setNextMessage;
        this.setCurrentMessage = setCurrentMessage;
        this.reloadedMessage = reloadedMessage;
        this.timeFormat = timeFormat;
        this.useFaweIfAvailable = useFaweIfAvailable;
        this.blocksPerTick = blocksPerTick;
        this.mines = Collections.unmodifiableMap(new LinkedHashMap<String, MineDefinition>(mines));
        this.autoMines = Collections.unmodifiableMap(new LinkedHashMap<String, AutoMineDefinition>(autoMines));
    }

    public static SopMinesConfig load(FileConfiguration config, File minesFolder) {
        String prefix = config.getString("messages.prefix", "&8[&bSopMines&8] ");
        String noPermissionMessage = config.getString("messages.no-permission", "{prefix}&cNo permission.");
        String usageAdminMessage = config.getString("messages.usage-admin", "{prefix}&e/sopmines <next|setnext|setcurrent|reload|pos1|pos2|createautomine|createmine|setmineblocks> ...");
        String automineNotFoundMessage = config.getString("messages.automine-not-found", "{prefix}&cAuto mine not found: {automine}");
        String mineNotFoundMessage = config.getString("messages.mine-not-found", "{prefix}&cMine not found: {mine}");
        String switchedNextMessage = config.getString("messages.switched-next", "{prefix}&aSwitched {automine} to {current}");
        String setNextMessage = config.getString("messages.set-next", "{prefix}&aNext for {automine} is {next}");
        String setCurrentMessage = config.getString("messages.set-current", "{prefix}&aCurrent for {automine} is {current}");
        String reloadedMessage = config.getString("messages.reloaded", "{prefix}&aReloaded.");
        String timeFormat = config.getString("time.format", "{hours}h {minutes}m {seconds}s");
        boolean useFaweIfAvailable = config.getBoolean("performance.use-fawe-if-available", true);
        int blocksPerTick = Math.max(1, config.getInt("performance.blocks-per-tick", 1500));

        Map<String, MineDefinition> mines = new LinkedHashMap<String, MineDefinition>();
        ConfigurationSection mineSection = config.getConfigurationSection("mines");
        if (mineSection != null) {
            for (String mineId : mineSection.getKeys(false)) {
                ConfigurationSection entry = mineSection.getConfigurationSection(mineId);
                if (entry == null) {
                    continue;
                }
                List<MineDefinition.BlockEntry> blocks = parseWeightedBlocks(entry, "blocks");
                if (blocks.isEmpty()) {
                    blocks.add(new MineDefinition.BlockEntry("STONE", 1));
                }
                List<MineDefinition.BlockEntry> surfaceBlocks = parseWeightedBlocks(entry, "surface-blocks");
                if (surfaceBlocks.isEmpty()) {
                    surfaceBlocks.add(new MineDefinition.BlockEntry("STONE", 1));
                    surfaceBlocks.add(new MineDefinition.BlockEntry("COBBLESTONE", 1));
                }
                String displayName = firstNonBlank(
                        entry.getString("name"),
                        mineId
                );
                int durationSeconds = Math.max(1, getInt(entry, 300, "duration-seconds"));

                ConfigurationSection surfaceLayerSection = entry.getConfigurationSection("surface-layer");
                boolean surfaceEnabled = false;
                int surfaceThickness = 1;
                if (surfaceLayerSection != null) {
                    surfaceEnabled = getBoolean(surfaceLayerSection, false, "enabled");
                    surfaceThickness = Math.max(1, getInt(surfaceLayerSection, 1, "thickness"));
                    List<MineDefinition.BlockEntry> nestedSurface = parseWeightedBlocks(surfaceLayerSection, "blocks");
                    if (!nestedSurface.isEmpty()) {
                        surfaceBlocks = nestedSurface;
                    }
                }
                mines.put(lower(mineId), new MineDefinition(
                        lower(mineId),
                        displayName,
                        blocks,
                        durationSeconds,
                        surfaceEnabled,
                        surfaceThickness,
                        surfaceBlocks
                ));
            }
        }

        Map<String, AutoMineDefinition> autoMines = new LinkedHashMap<String, AutoMineDefinition>();
        if (minesFolder != null && (minesFolder.exists() || minesFolder.mkdirs())) {
            File[] files = minesFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file == null || !file.isFile() || !file.getName().toLowerCase().endsWith(".yml")) {
                        continue;
                    }
                    YamlConfiguration autoConfig = YamlConfiguration.loadConfiguration(file);
                    String automineId = lower(autoConfig.getString("id", file.getName().substring(0, file.getName().length() - 4)));
                    Map<String, Integer> weightedMines = parseAutoMineWeights(autoConfig, "mines", mines);
                    if (weightedMines.isEmpty()) {
                        continue;
                    }
                    List<String> allowedMines = new ArrayList<String>(weightedMines.keySet());
                    boolean enabled = autoConfig.getBoolean("enabled", true);
                    String displayName = firstNonBlank(autoConfig.getString("name"), automineId);
                    ConfigurationSection position = autoConfig.getConfigurationSection("position");
                    if (position == null) {
                        continue;
                    }
                    String worldName = firstNonBlank(position.getString("world"), "");
                    ConfigurationSection pos1 = position.getConfigurationSection("pos1");
                    ConfigurationSection pos2 = position.getConfigurationSection("pos2");
                    if (worldName.trim().isEmpty() || pos1 == null || pos2 == null) {
                        continue;
                    }
                    int x1 = pos1.getInt("x");
                    int y1 = pos1.getInt("y");
                    int z1 = pos1.getInt("z");
                    int x2 = pos2.getInt("x");
                    int y2 = pos2.getInt("y");
                    int z2 = pos2.getInt("z");
                    boolean allowConsecutiveRepeats = autoConfig.getBoolean("allow-consecutive-repeats", false);
                    String initialCurrentMineId = lower(autoConfig.getString("initial-current", ""));
                    String initialNextMineId = lower(autoConfig.getString("initial-next", ""));
                    List<String> onUpdateCommands = autoConfig.getStringList("on-update-commands");
                    autoMines.put(lower(automineId), new AutoMineDefinition(
                            automineId,
                            enabled,
                            displayName,
                            worldName,
                            Math.min(x1, x2),
                            Math.min(y1, y2),
                            Math.min(z1, z2),
                            Math.max(x1, x2),
                            Math.max(y1, y2),
                            Math.max(z1, z2),
                            allowedMines,
                            weightedMines,
                            allowConsecutiveRepeats,
                            initialCurrentMineId,
                            initialNextMineId,
                            onUpdateCommands
                    ));
                }
            }
        }

        return new SopMinesConfig(
                prefix,
                noPermissionMessage,
                usageAdminMessage,
                automineNotFoundMessage,
                mineNotFoundMessage,
                switchedNextMessage,
                setNextMessage,
                setCurrentMessage,
                reloadedMessage,
                timeFormat,
                useFaweIfAvailable,
                blocksPerTick,
                mines,
                autoMines
        );
    }

    public String getPrefix() {
        return prefix;
    }

    public String getNoPermissionMessage() {
        return noPermissionMessage;
    }

    public String getUsageAdminMessage() {
        return usageAdminMessage;
    }

    public String getAutomineNotFoundMessage() {
        return automineNotFoundMessage;
    }

    public String getMineNotFoundMessage() {
        return mineNotFoundMessage;
    }

    public String getSwitchedNextMessage() {
        return switchedNextMessage;
    }

    public String getSetNextMessage() {
        return setNextMessage;
    }

    public String getSetCurrentMessage() {
        return setCurrentMessage;
    }

    public String getReloadedMessage() {
        return reloadedMessage;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public boolean isUseFaweIfAvailable() {
        return useFaweIfAvailable;
    }

    public int getBlocksPerTick() {
        return blocksPerTick;
    }

    public Map<String, MineDefinition> getMines() {
        return mines;
    }

    public Map<String, AutoMineDefinition> getAutoMines() {
        return autoMines;
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static int getInt(ConfigurationSection section, int fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getInt(key, fallback);
            }
        }
        return fallback;
    }

    private static boolean getBoolean(ConfigurationSection section, boolean fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getBoolean(key, fallback);
            }
        }
        return fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static List<MineDefinition.BlockEntry> parseWeightedBlocks(ConfigurationSection section, String... keys) {
        List<MineDefinition.BlockEntry> blocks = new ArrayList<MineDefinition.BlockEntry>();
        for (String key : keys) {
            if (!section.contains(key)) {
                continue;
            }
            Object raw = section.get(key);
            if (raw instanceof List) {
                List<?> list = (List<?>) raw;
                for (Object element : list) {
                    if (element == null) {
                        continue;
                    }
                    addWeightedEntry(blocks, String.valueOf(element));
                }
            } else if (raw instanceof ConfigurationSection) {
                ConfigurationSection mapSection = (ConfigurationSection) raw;
                for (String blockKey : mapSection.getKeys(false)) {
                    int weight = Math.max(0, mapSection.getInt(blockKey, 0));
                    addWeightedEntry(blocks, blockKey + ":" + weight);
                }
            } else if (raw != null) {
                addWeightedEntry(blocks, String.valueOf(raw));
            }
            if (!blocks.isEmpty()) {
                break;
            }
        }
        return blocks;
    }

    private static void addWeightedEntry(List<MineDefinition.BlockEntry> blocks, String raw) {
        String[] split = raw.split(":");
        String materialName;
        int weight = 1;
        if (split.length == 1) {
            materialName = split[0].trim().toUpperCase();
        } else if (split.length == 2) {
            materialName = split[0].trim().toUpperCase();
            try {
                weight = Integer.parseInt(split[1].trim());
            } catch (NumberFormatException ignored) {
                return;
            }
        } else {
            return;
        }
        if (weight <= 0 || Material.matchMaterial(materialName) == null) {
            return;
        }
        blocks.add(new MineDefinition.BlockEntry(materialName, weight));
    }

    private static Map<String, Integer> parseAutoMineWeights(YamlConfiguration config, String path, Map<String, MineDefinition> availableMines) {
        Map<String, Integer> weighted = new LinkedHashMap<String, Integer>();
        Object raw = config.get(path);
        if (raw instanceof ConfigurationSection) {
            ConfigurationSection section = (ConfigurationSection) raw;
            for (String mineId : section.getKeys(false)) {
                String normalizedMineId = lower(mineId);
                if (!availableMines.containsKey(normalizedMineId)) {
                    continue;
                }
                int weight = Math.max(1, section.getInt(mineId, 1));
                mergeMineWeight(weighted, normalizedMineId, weight);
            }
            return weighted;
        }

        List<String> values = config.getStringList(path);
        for (String line : values) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String[] split = line.split(":");
            String mineId = lower(split[0]);
            if (!availableMines.containsKey(mineId)) {
                continue;
            }
            int weight = 1;
            if (split.length >= 2) {
                try {
                    weight = Integer.parseInt(split[1].trim());
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
            if (weight <= 0) {
                continue;
            }
            mergeMineWeight(weighted, mineId, weight);
        }
        return weighted;
    }

    private static void mergeMineWeight(Map<String, Integer> weighted, String mineId, int weight) {
        Integer existing = weighted.get(mineId);
        if (existing == null) {
            weighted.put(mineId, Integer.valueOf(weight));
            return;
        }
        weighted.put(mineId, Integer.valueOf(existing.intValue() + weight));
    }
}
