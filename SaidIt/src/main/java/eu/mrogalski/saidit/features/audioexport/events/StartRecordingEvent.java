package eu.mrogalski.saidit.features.audioexport.events;

/**
 * Event to request starting a recording.
 */
public class StartRecordingEvent {
    public final float prependSeconds;
    
    public StartRecordingEvent(float prependSeconds) {
        this.prependSeconds = prependSeconds;
    }
}
