package eu.mrogalski.saidit.shared.models;

/**
 * Represents a segment in the audio timeline (activity or silence).
 */
public class TimelineSegment {
    public enum Type { ACTIVITY, SILENCE }
    
    public Type type;
    public long startTimeMillis;
    public long endTimeMillis; // 0 if still ongoing
    public int durationSeconds;
    
    public TimelineSegment(Type type, long startTimeMillis) {
        this.type = type;
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = 0;
        this.durationSeconds = 0;
    }
    
    public void end(long endTimeMillis) {
        this.endTimeMillis = endTimeMillis;
        this.durationSeconds = (int)((endTimeMillis - startTimeMillis) / 1000);
    }
    
    public int getCurrentDuration() {
        if (endTimeMillis == 0) {
            return (int)((System.currentTimeMillis() - startTimeMillis) / 1000);
        }
        return durationSeconds;
    }
    
    public boolean isOngoing() {
        return endTimeMillis == 0;
    }
}
