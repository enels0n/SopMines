package net.enelson.sopmines.model;

public final class MineDefinition {

    public static final class BlockEntry {
        private final String materialName;
        private final int weight;

        public BlockEntry(String materialName, int weight) {
            this.materialName = materialName;
            this.weight = weight;
        }

        public String getMaterialName() {
            return materialName;
        }

        public int getWeight() {
            return weight;
        }
    }

    private final String id;
    private final String displayName;
    private final java.util.List<BlockEntry> blocks;
    private final int durationSeconds;
    private final boolean surfaceLayerEnabled;
    private final int surfaceLayerThickness;
    private final java.util.List<BlockEntry> surfaceBlocks;

    public MineDefinition(String id,
                          String displayName,
                          java.util.List<BlockEntry> blocks,
                          int durationSeconds,
                          boolean surfaceLayerEnabled,
                          int surfaceLayerThickness,
                          java.util.List<BlockEntry> surfaceBlocks) {
        this.id = id;
        this.displayName = displayName;
        this.blocks = java.util.Collections.unmodifiableList(new java.util.ArrayList<BlockEntry>(blocks));
        this.durationSeconds = durationSeconds;
        this.surfaceLayerEnabled = surfaceLayerEnabled;
        this.surfaceLayerThickness = surfaceLayerThickness;
        this.surfaceBlocks = java.util.Collections.unmodifiableList(new java.util.ArrayList<BlockEntry>(surfaceBlocks));
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public java.util.List<BlockEntry> getBlocks() {
        return blocks;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isSurfaceLayerEnabled() {
        return surfaceLayerEnabled;
    }

    public int getSurfaceLayerThickness() {
        return surfaceLayerThickness;
    }

    public java.util.List<BlockEntry> getSurfaceBlocks() {
        return surfaceBlocks;
    }
}
