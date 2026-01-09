package eu.mrogalski.saidit;

import android.util.Log;

/**
 * Voice Activity Detection (VAD) using amplitude-based detection.
 * Detects speech/audio activity in PCM audio samples.
 */
public class VoiceActivityDetector {
    private static final String TAG = VoiceActivityDetector.class.getSimpleName();
    
    // Configurable parameters
    private float threshold = 500.0f; // Amplitude threshold for activity detection
    private int smoothingWindow = 10; // Number of frames for smoothing
    private int minActivityFrames = 3; // Minimum consecutive frames to trigger activity
    private int minSilenceFrames = 20; // Minimum consecutive frames to end activity
    
    // State tracking
    private boolean isActive = false;
    private int consecutiveActiveFrames = 0;
    private int consecutiveSilentFrames = 0;
    private float noiseFloor = 100.0f; // Estimated background noise level
    private float[] energyHistory;
    private int historyIndex = 0;
    
    /**
     * Creates a new VoiceActivityDetector with default settings.
     */
    public VoiceActivityDetector() {
        this.energyHistory = new float[smoothingWindow];
    }
    
    /**
     * Creates a new VoiceActivityDetector with custom threshold.
     * @param threshold Amplitude threshold for detection (higher = less sensitive)
     */
    public VoiceActivityDetector(float threshold) {
        this();
        this.threshold = threshold;
    }
    
    /**
     * Processes a buffer of 16-bit PCM audio samples and detects activity.
     * @param buffer Audio sample buffer
     * @param offset Offset in buffer
     * @param length Number of bytes to process
     * @return true if activity is detected, false otherwise
     */
    public boolean process(byte[] buffer, int offset, int length) {
        // Calculate RMS energy of the audio frame
        float energy = calculateRMSEnergy(buffer, offset, length);
        
        // Update noise floor estimation (slowly adapting average)
        if (energy < noiseFloor) {
            noiseFloor = noiseFloor * 0.99f + energy * 0.01f;
        }
        
        // Store energy in history for smoothing
        energyHistory[historyIndex] = energy;
        historyIndex = (historyIndex + 1) % smoothingWindow;
        
        // Calculate smoothed energy
        float smoothedEnergy = calculateAverageEnergy();
        
        // Adaptive threshold based on noise floor
        float adaptiveThreshold = Math.max(threshold, noiseFloor * 3.0f);
        
        // Check if current frame has activity
        boolean frameHasActivity = smoothedEnergy > adaptiveThreshold;
        
        // Update state counters
        if (frameHasActivity) {
            consecutiveActiveFrames++;
            consecutiveSilentFrames = 0;
        } else {
            consecutiveSilentFrames++;
            consecutiveActiveFrames = 0;
        }
        
        // Update activity state with hysteresis
        boolean previouslyActive = isActive;
        
        if (!isActive && consecutiveActiveFrames >= minActivityFrames) {
            // Transition to active state
            isActive = true;
            Log.d(TAG, "Activity detected (energy: " + smoothedEnergy + ", threshold: " + adaptiveThreshold + ")");
        } else if (isActive && consecutiveSilentFrames >= minSilenceFrames) {
            // Transition to inactive state
            isActive = false;
            Log.d(TAG, "Activity ended (silence detected)");
        }
        
        return isActive;
    }
    
    /**
     * Calculates RMS (Root Mean Square) energy of audio samples.
     * Assumes 16-bit PCM little-endian format (standard for Android AudioRecord).
     */
    private float calculateRMSEnergy(byte[] buffer, int offset, int length) {
        long sum = 0;
        int sampleCount = length / 2; // 16-bit samples = 2 bytes per sample
        
        for (int i = offset; i < offset + length - 1; i += 2) {
            // Convert two bytes to 16-bit sample (little-endian)
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += sample * sample;
        }
        
        if (sampleCount == 0) return 0;
        
        return (float) Math.sqrt((double) sum / sampleCount);
    }
    
    /**
     * Calculates average energy from history window.
     */
    private float calculateAverageEnergy() {
        float sum = 0;
        for (float energy : energyHistory) {
            sum += energy;
        }
        return sum / smoothingWindow;
    }
    
    /**
     * Returns true if activity is currently detected.
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Sets the sensitivity threshold.
     * @param threshold Higher values = less sensitive (0-10000 typical range)
     */
    public void setThreshold(float threshold) {
        this.threshold = threshold;
        Log.d(TAG, "Threshold set to: " + threshold);
    }
    
    /**
     * Gets the current threshold value.
     */
    public float getThreshold() {
        return threshold;
    }
    
    /**
     * Sets the number of consecutive active frames needed to trigger activity.
     */
    public void setMinActivityFrames(int frames) {
        this.minActivityFrames = frames;
    }
    
    /**
     * Sets the number of consecutive silent frames needed to end activity.
     */
    public void setMinSilenceFrames(int frames) {
        this.minSilenceFrames = frames;
    }
    
    /**
     * Resets the detector state.
     */
    public void reset() {
        isActive = false;
        consecutiveActiveFrames = 0;
        consecutiveSilentFrames = 0;
        noiseFloor = 100.0f;
        for (int i = 0; i < energyHistory.length; i++) {
            energyHistory[i] = 0;
        }
        historyIndex = 0;
        Log.d(TAG, "Detector reset");
    }
    
    /**
     * Gets the estimated noise floor level.
     */
    public float getNoiseFloor() {
        return noiseFloor;
    }
    
    /**
     * Gets the current energy level.
     */
    public float getCurrentEnergy() {
        return calculateAverageEnergy();
    }
}
