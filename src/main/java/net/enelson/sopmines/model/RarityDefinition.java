package net.enelson.sopmines.model;

public final class RarityDefinition {

    private final String id;
    private final String displayName;
    private final int weight;

    public RarityDefinition(String id, String displayName, int weight) {
        this.id = id;
        this.displayName = displayName;
        this.weight = weight;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }
}
