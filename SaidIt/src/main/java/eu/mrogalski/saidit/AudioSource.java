package eu.mrogalski.saidit;

/**
 * Audio source types for recording.
 * 
 * This enum defines different audio sources that can be captured.
 */
public enum AudioSource {
    /**
     * Microphone audio only (default, always supported)
     */
    MICROPHONE,
    
    /**
     * Device/system audio only (requires Android 10+, limited compatibility)
     */
    DEVICE,
    
    /**
     * Both microphone and device audio in separate channels
     * (requires Android 10+, limited compatibility)
     */
    BOTH;
    
    /**
     * Checks if this audio source requires special permissions.
     */
    public boolean requiresSpecialPermission() {
        return this == DEVICE || this == BOTH;
    }
    
    /**
     * Gets the required API level for this audio source.
     */
    public int getRequiredApiLevel() {
        switch (this) {
            case MICROPHONE:
                return 1; // Always supported
            case DEVICE:
            case BOTH:
                return 29; // Android 10 (API 29) for AudioPlaybackCapture
            default:
                return 1;
        }
    }
}
