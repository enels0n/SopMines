package net.enelson.sopmines.model;

public final class AutoMineState {

    private final AutoMineDefinition definition;
    private String currentMineId;
    private String nextMineId;
    private long nextSwitchAtEpochSecond;

    public AutoMineState(AutoMineDefinition definition, String currentMineId, String nextMineId, long nextSwitchAtEpochSecond) {
        this.definition = definition;
        this.currentMineId = currentMineId;
        this.nextMineId = nextMineId;
        this.nextSwitchAtEpochSecond = nextSwitchAtEpochSecond;
    }

    public AutoMineDefinition getDefinition() {
        return definition;
    }

    public String getCurrentMineId() {
        return currentMineId;
    }

    public void setCurrentMineId(String currentMineId) {
        this.currentMineId = currentMineId;
    }

    public String getNextMineId() {
        return nextMineId;
    }

    public void setNextMineId(String nextMineId) {
        this.nextMineId = nextMineId;
    }

    public long getNextSwitchAtEpochSecond() {
        return nextSwitchAtEpochSecond;
    }

    public void setNextSwitchAtEpochSecond(long nextSwitchAtEpochSecond) {
        this.nextSwitchAtEpochSecond = nextSwitchAtEpochSecond;
    }
}
