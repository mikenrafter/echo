package eu.mrogalski.saidit.features.audiocapture.events;

/**
 * Event published when audio capture state changes.
 */
public class AudioCaptureStateChangedEvent {
    public enum State {
        STOPPED,
        LISTENING
    }
    
    public final State state;
    
    public AudioCaptureStateChangedEvent(State state) {
        this.state = state;
    }
}
