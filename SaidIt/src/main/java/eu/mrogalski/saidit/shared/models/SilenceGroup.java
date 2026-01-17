package eu.mrogalski.saidit.shared.models;

/**
 * Represents a group of skipped silence in the audio timeline.
 */
public class SilenceGroup {
    public long endTimeMillis;
    public long durationMillis;
    
    public SilenceGroup(long endTimeMillis, long durationMillis) {
        this.endTimeMillis = endTimeMillis;
        this.durationMillis = durationMillis;
    }
}
