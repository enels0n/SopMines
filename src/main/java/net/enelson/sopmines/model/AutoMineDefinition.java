package net.enelson.sopmines.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AutoMineDefinition {

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final List<String> mineIds;
    private final Map<String, Integer> mineWeights;
    private final boolean allowConsecutiveRepeats;
    private final String initialCurrentMineId;
    private final String initialNextMineId;
    private final List<String> onUpdateCommands;

    public AutoMineDefinition(String id,
                              boolean enabled,
                              String displayName,
                              String worldName,
                              int minX,
                              int minY,
                              int minZ,
                              int maxX,
                              int maxY,
                              int maxZ,
                              List<String> mineIds,
                              Map<String, Integer> mineWeights,
                              boolean allowConsecutiveRepeats,
                              String initialCurrentMineId,
                              String initialNextMineId,
                              List<String> onUpdateCommands) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.mineIds = Collections.unmodifiableList(new ArrayList<String>(mineIds));
        this.mineWeights = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(mineWeights));
        this.allowConsecutiveRepeats = allowConsecutiveRepeats;
        this.initialCurrentMineId = initialCurrentMineId;
        this.initialNextMineId = initialNextMineId;
        this.onUpdateCommands = Collections.unmodifiableList(new ArrayList<String>(onUpdateCommands));
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public List<String> getMineIds() {
        return mineIds;
    }

    public Map<String, Integer> getMineWeights() {
        return mineWeights;
    }

    public boolean isAllowConsecutiveRepeats() {
        return allowConsecutiveRepeats;
    }

    public String getInitialCurrentMineId() {
        return initialCurrentMineId;
    }

    public String getInitialNextMineId() {
        return initialNextMineId;
    }

    public List<String> getOnUpdateCommands() {
        return onUpdateCommands;
    }
}
