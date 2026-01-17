package eu.mrogalski.saidit.features.audioprocessing.models;

/**
 * Event requesting audio processing (normalization, noise cancellation, etc.)
 */
public class ProcessAudioEvent {
    private final byte[] audioData;
    private final boolean normalize;
    private final boolean noiseCancellation;
    
    public ProcessAudioEvent(byte[] audioData, boolean normalize, boolean noiseCancellation) {
        this.audioData = audioData;
        this.normalize = normalize;
        this.noiseCancellation = noiseCancellation;
    }
    
    public byte[] getAudioData() {
        return audioData;
    }
    
    public boolean isNormalize() {
        return normalize;
    }
    
    public boolean isNoiseCancellation() {
        return noiseCancellation;
    }
}
