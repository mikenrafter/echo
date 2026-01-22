package eu.mrogalski.saidit.features.audioplayback.models;

/**
 * Event requesting audio playback.
 */
public class PlayAudioEvent {
    private final byte[] audioData;
    private final int sampleRate;
    
    public PlayAudioEvent(byte[] audioData, int sampleRate) {
        this.audioData = audioData;
        this.sampleRate = sampleRate;
    }
    
    public byte[] getAudioData() {
        return audioData;
    }
    
    public int getSampleRate() {
        return sampleRate;
    }
}
