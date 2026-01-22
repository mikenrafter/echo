package eu.mrogalski.saidit.features.audioexport.events;

import java.io.File;

/**
 * Event published when a recording is completed.
 */
public class RecordingCompletedEvent {
    public final File file;
    public final float durationSeconds;
    
    public RecordingCompletedEvent(File file, float durationSeconds) {
        this.file = file;
        this.durationSeconds = durationSeconds;
    }
}
