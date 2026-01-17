package eu.mrogalski.saidit.features.audiocapture.models;

/**
 * Event requesting to start audio recording.
 */
public class StartRecordingEvent {
    private final float prependSeconds;
    
    public StartRecordingEvent(float prependSeconds) {
        this.prependSeconds = prependSeconds;
    }
    
    public float getPrependSeconds() {
        return prependSeconds;
    }
}
