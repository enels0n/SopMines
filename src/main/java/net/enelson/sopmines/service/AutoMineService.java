package net.enelson.sopmines.service;

import net.enelson.sopmines.SopMinesPlugin;
import net.enelson.sopmines.config.SopMinesConfig;
import net.enelson.sopmines.model.AutoMineDefinition;
import net.enelson.sopmines.model.AutoMineState;
import net.enelson.sopmines.model.MineDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class AutoMineService {

    public static final class BlockChange {
        private final int x;
        private final int y;
        private final int z;
        private final Material material;

        public BlockChange(int x, int y, int z, Material material) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public Material getMaterial() {
            return material;
        }
    }

    private static final class PendingFill {
        private final String automineId;
        private final String worldName;
        private final List<BlockChange> changes;
        private int index;

        private PendingFill(String automineId, String worldName, List<BlockChange> changes) {
            this.automineId = automineId;
            this.worldName = worldName;
            this.changes = changes;
            this.index = 0;
        }
    }

    private final SopMinesPlugin plugin;
    private final Random random = new Random();
    private final Map<String, AutoMineState> states = new LinkedHashMap<String, AutoMineState>();
    private final Map<String, PendingFill> pendingByAutoMine = new HashMap<String, PendingFill>();
    private final Deque<PendingFill> fillQueue = new ArrayDeque<PendingFill>();
    private final FaweIntegration faweIntegration = new FaweIntegration();
    private BukkitTask rotationTask;
    private BukkitTask fillApplyTask;
    private boolean useFaweIfAvailable;
    private int blocksPerTick;

    public AutoMineService(SopMinesPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(SopMinesConfig config) {
        shutdown();
        states.clear();
        this.useFaweIfAvailable = config.isUseFaweIfAvailable();
        this.blocksPerTick = config.getBlocksPerTick();
        long now = nowSeconds();
        for (AutoMineDefinition definition : config.getAutoMines().values()) {
            if (!definition.isEnabled()) {
                continue;
            }
            String currentMine = resolveInitialCurrentMine(config, definition);
            if (currentMine == null) {
                plugin.getLogger().warning("Skipping automine '" + definition.getId() + "': no valid mines in its 'mines' list.");
                continue;
            }
            String nextMine = resolveInitialNextMine(config, definition, currentMine);
            AutoMineState state = new AutoMineState(definition, currentMine, nextMine, now + getMineDurationSeconds(currentMine));
            states.put(definition.getId(), state);
            fillRegion(state, currentMine);
        }
        startTicker();
    }

    public void shutdown() {
        if (rotationTask != null) {
            rotationTask.cancel();
            rotationTask = null;
        }
        if (fillApplyTask != null) {
            fillApplyTask.cancel();
            fillApplyTask = null;
        }
        fillQueue.clear();
        pendingByAutoMine.clear();
    }

    public Map<String, AutoMineState> getStates() {
        return Collections.unmodifiableMap(states);
    }

    public AutoMineState getState(String automineId) {
        if (automineId == null) {
            return null;
        }
        return states.get(automineId.trim().toLowerCase());
    }

    public boolean switchToNext(String automineId) {
        AutoMineState state = getState(automineId);
        if (state == null) {
            return false;
        }
        performSwitch(state, false);
        return true;
    }

    public boolean setNext(String automineId, String mineId) {
        AutoMineState state = getState(automineId);
        if (state == null) {
            return false;
        }
        String normalized = normalize(mineId);
        if (!isMineAllowedForAutoMine(normalized, state.getDefinition(), plugin.getMinesConfig())) {
            return false;
        }
        state.setNextMineId(normalized);
        return true;
    }

    public boolean setCurrent(String automineId, String mineId) {
        AutoMineState state = getState(automineId);
        if (state == null) {
            return false;
        }
        String normalized = normalize(mineId);
        if (!isMineAllowedForAutoMine(normalized, state.getDefinition(), plugin.getMinesConfig())) {
            return false;
        }
        state.setCurrentMineId(normalized);
        if (normalized.equals(state.getNextMineId())) {
            state.setNextMineId(pickRandomMine(plugin.getMinesConfig(), state.getDefinition(), normalized));
        }
        state.setNextSwitchAtEpochSecond(nowSeconds() + getMineDurationSeconds(normalized));
        fillRegion(state, normalized);
        executeUpdateCommands(state);
        return true;
    }

    public String getCurrentMineId(String automineId) {
        AutoMineState state = getState(automineId);
        return state == null ? null : state.getCurrentMineId();
    }

    public String getNextMineId(String automineId) {
        AutoMineState state = getState(automineId);
        return state == null ? null : state.getNextMineId();
    }

    public long getSecondsToNext(String automineId) {
        AutoMineState state = getState(automineId);
        if (state == null) {
            return -1L;
        }
        long delta = state.getNextSwitchAtEpochSecond() - nowSeconds();
        return Math.max(0L, delta);
    }

    private void startTicker() {
        this.rotationTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    private void tick() {
        long now = nowSeconds();
        for (AutoMineState state : states.values()) {
            if (now < state.getNextSwitchAtEpochSecond()) {
                continue;
            }
            performSwitch(state, true);
        }
    }

    private void performSwitch(AutoMineState state, boolean timed) {
        String nextMine = state.getNextMineId();
        if (nextMine == null || nextMine.isEmpty()) {
            nextMine = pickRandomMine(plugin.getMinesConfig(), state.getDefinition(), state.getCurrentMineId());
        }
        if (nextMine == null) {
            return;
        }
        state.setCurrentMineId(nextMine);
        state.setNextMineId(pickRandomMine(plugin.getMinesConfig(), state.getDefinition(), state.getCurrentMineId()));
        state.setNextSwitchAtEpochSecond(nowSeconds() + getMineDurationSeconds(nextMine));
        fillRegion(state, nextMine);
        executeUpdateCommands(state);
        if (timed) {
            plugin.getLogger().info("Auto mine '" + state.getDefinition().getId() + "' switched to '" + state.getCurrentMineId() + "'.");
        }
    }

    private void executeUpdateCommands(AutoMineState state) {
        for (String raw : state.getDefinition().getOnUpdateCommands()) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String command = applyTokens(raw, state);
            if (command.regionMatches(true, 0, "[broadcast]", 0, "[broadcast]".length())) {
                String message = command.substring("[broadcast]".length()).trim();
                Bukkit.broadcastMessage(plugin.color(message));
                continue;
            }
            if (command.regionMatches(true, 0, "[console]", 0, "[console]".length())) {
                String consoleCommand = command.substring("[console]".length()).trim();
                if (!consoleCommand.isEmpty()) {
                    ConsoleCommandSender console = Bukkit.getConsoleSender();
                    Bukkit.dispatchCommand(console, consoleCommand);
                }
                continue;
            }
            // By default treat as console command.
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private String applyTokens(String input, AutoMineState state) {
        MineDefinition current = plugin.getMinesConfig().getMines().get(state.getCurrentMineId());
        MineDefinition next = state.getNextMineId() == null ? null : plugin.getMinesConfig().getMines().get(state.getNextMineId());
        return input
                .replace("{automine}", state.getDefinition().getId())
                .replace("{automine_display}", state.getDefinition().getDisplayName())
                .replace("{current_mine}", state.getCurrentMineId() == null ? "" : state.getCurrentMineId())
                .replace("{current_display}", current == null ? "" : current.getDisplayName())
                .replace("{next_mine}", state.getNextMineId() == null ? "" : state.getNextMineId())
                .replace("{next_display}", next == null ? "" : next.getDisplayName())
                .replace("{next_in}", plugin.formatDuration(getSecondsToNext(state.getDefinition().getId())));
    }

    private String resolveInitialCurrentMine(SopMinesConfig config, AutoMineDefinition definition) {
        String forced = normalize(definition.getInitialCurrentMineId());
        if (isMineAllowedForAutoMine(forced, definition, config)) {
            return forced;
        }
        return pickRandomMine(config, definition, null);
    }

    private String resolveInitialNextMine(SopMinesConfig config, AutoMineDefinition definition, String currentMine) {
        String forced = normalize(definition.getInitialNextMineId());
        if (isMineAllowedForAutoMine(forced, definition, config) && !forced.equals(currentMine)) {
            return forced;
        }
        return pickRandomMine(config, definition, currentMine);
    }

    private String pickRandomMine(SopMinesConfig config, AutoMineDefinition definition, String avoidMineId) {
        List<String> candidates = new ArrayList<String>();
        int totalWeight = 0;
        for (String mineId : definition.getMineIds()) {
            if (!config.getMines().containsKey(mineId)) {
                continue;
            }
            if (!definition.isAllowConsecutiveRepeats()
                    && avoidMineId != null
                    && avoidMineId.equals(mineId)
                    && definition.getMineIds().size() > 1) {
                continue;
            }
            candidates.add(mineId);
            Integer weight = definition.getMineWeights().get(mineId);
            totalWeight += Math.max(1, weight == null ? 1 : weight.intValue());
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (totalWeight <= 0) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (String mineId : candidates) {
            Integer weight = definition.getMineWeights().get(mineId);
            cursor += Math.max(1, weight == null ? 1 : weight.intValue());
            if (roll < cursor) {
                return mineId;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private boolean isMineAllowedForAutoMine(String mineId, AutoMineDefinition definition, SopMinesConfig config) {
        if (mineId == null || mineId.isEmpty()) {
            return false;
        }
        MineDefinition mine = config.getMines().get(mineId);
        if (mine == null) {
            return false;
        }
        return definition.getMineIds().contains(mine.getId());
    }

    private long nowSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }

    private void fillRegion(AutoMineState state, String mineId) {
        if (mineId == null || mineId.isEmpty()) {
            return;
        }
        MineDefinition mine = plugin.getMinesConfig().getMines().get(mineId);
        if (mine == null || mine.getBlocks().isEmpty()) {
            return;
        }
        queueRegionFill(state, mine);
    }

    private void queueRegionFill(final AutoMineState state, final MineDefinition mine) {
        final String automineId = state.getDefinition().getId();
        final String worldName = state.getDefinition().getWorldName();
        if (worldName == null || worldName.trim().isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final List<BlockChange> plan = buildFillPlan(state, mine);
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (plan.isEmpty()) {
                            return;
                        }
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) {
                            plugin.getLogger().warning("World '" + worldName + "' not found for automine " + automineId);
                            return;
                        }
                        if (useFaweIfAvailable && faweIntegration.isAvailable() && faweIntegration.applyPlan(world, plan)) {
                            return;
                        }
                        PendingFill pending = new PendingFill(automineId, worldName, plan);
                        pendingByAutoMine.put(automineId, pending);
                        fillQueue.addLast(pending);
                        ensureFillApplyTask();
                    }
                });
            }
        });
    }

    private void ensureFillApplyTask() {
        if (fillApplyTask != null) {
            return;
        }
        fillApplyTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                applyFillQueueTick();
            }
        }, 1L, 1L);
    }

    private void applyFillQueueTick() {
        if (fillQueue.isEmpty()) {
            if (fillApplyTask != null) {
                fillApplyTask.cancel();
                fillApplyTask = null;
            }
            return;
        }

        int budget = Math.max(1, blocksPerTick);
        while (budget > 0 && !fillQueue.isEmpty()) {
            PendingFill pending = fillQueue.peekFirst();
            if (pending == null) {
                fillQueue.pollFirst();
                continue;
            }
            if (pendingByAutoMine.get(pending.automineId) != pending) {
                fillQueue.pollFirst();
                continue;
            }
            World world = Bukkit.getWorld(pending.worldName);
            if (world == null) {
                pendingByAutoMine.remove(pending.automineId);
                fillQueue.pollFirst();
                continue;
            }

            while (budget > 0 && pending.index < pending.changes.size()) {
                BlockChange change = pending.changes.get(pending.index++);
                world.getBlockAt(change.getX(), change.getY(), change.getZ()).setType(change.getMaterial(), false);
                budget--;
            }

            if (pending.index >= pending.changes.size()) {
                pendingByAutoMine.remove(pending.automineId);
                fillQueue.pollFirst();
            }
        }
    }

    private List<BlockChange> buildFillPlan(AutoMineState state, MineDefinition mine) {
        List<BlockChange> changes = new ArrayList<BlockChange>();
        List<MaterialWeight> orePalette = buildWeightedPalette(mine.getBlocks());
        List<MaterialWeight> surfacePalette = buildWeightedPalette(mine.getSurfaceBlocks());
        if (orePalette.isEmpty()) {
            return changes;
        }

        int minX = state.getDefinition().getMinX();
        int minY = state.getDefinition().getMinY();
        int minZ = state.getDefinition().getMinZ();
        int maxX = state.getDefinition().getMaxX();
        int maxY = state.getDefinition().getMaxY();
        int maxZ = state.getDefinition().getMaxZ();
        int thickness = Math.max(1, mine.getSurfaceLayerThickness());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean surface = mine.isSurfaceLayerEnabled()
                            && !surfacePalette.isEmpty()
                            && isSurfaceLayer(x, y, z, minX, minY, minZ, maxX, maxY, maxZ, thickness);
                    Material material = surface
                            ? pickWeightedMaterial(surfacePalette)
                            : pickWeightedMaterial(orePalette);
                    if (material != null) {
                        changes.add(new BlockChange(x, y, z, material));
                    }
                }
            }
        }
        return changes;
    }

    private boolean isSurfaceLayer(int x, int y, int z,
                                   int minX, int minY, int minZ,
                                   int maxX, int maxY, int maxZ,
                                   int thickness) {
        return (x - minX) < thickness || (maxX - x) < thickness
                || (y - minY) < thickness || (maxY - y) < thickness
                || (z - minZ) < thickness || (maxZ - z) < thickness;
    }

    private List<MaterialWeight> buildWeightedPalette(List<MineDefinition.BlockEntry> entries) {
        List<MaterialWeight> palette = new ArrayList<MaterialWeight>();
        for (MineDefinition.BlockEntry entry : entries) {
            Material material = Material.matchMaterial(entry.getMaterialName());
            if (material == null) {
                continue;
            }
            int weight = Math.max(0, entry.getWeight());
            if (weight <= 0) {
                continue;
            }
            palette.add(new MaterialWeight(material, weight));
        }
        return palette;
    }

    private Material pickWeightedMaterial(List<MaterialWeight> palette) {
        if (palette.isEmpty()) {
            return null;
        }
        int total = 0;
        for (MaterialWeight weight : palette) {
            total += weight.weight;
        }
        if (total <= 0) {
            return palette.get(0).material;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (MaterialWeight weight : palette) {
            cursor += weight.weight;
            if (roll < cursor) {
                return weight.material;
            }
        }
        return palette.get(palette.size() - 1).material;
    }

    private static final class MaterialWeight {
        private final Material material;
        private final int weight;

        private MaterialWeight(Material material, int weight) {
            this.material = material;
            this.weight = weight;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private long getMineDurationSeconds(String mineId) {
        MineDefinition mineDefinition = plugin.getMinesConfig().getMines().get(normalize(mineId));
        if (mineDefinition == null) {
            return 300L;
        }
        return Math.max(1, mineDefinition.getDurationSeconds());
    }
}
