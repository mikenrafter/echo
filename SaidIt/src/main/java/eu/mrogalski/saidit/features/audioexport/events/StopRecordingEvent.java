package eu.mrogalski.saidit.features.audioexport.events;

/**
 * Event to request stopping a recording.
 */
public class StopRecordingEvent {
    public final String customFilename;
    
    public StopRecordingEvent(String customFilename) {
        this.customFilename = customFilename != null ? customFilename : "";
    }
}
