package eu.mrogalski.saidit.features.audiocapture.models;

/**
 * Event published when recording state changes.
 */
public class RecordingStateChangedEvent {
    private final boolean isRecording;
    private final long startTime;
    
    public RecordingStateChangedEvent(boolean isRecording, long startTime) {
        this.isRecording = isRecording;
        this.startTime = startTime;
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    public long getStartTime() {
        return startTime;
    }
}
