package eu.mrogalski.saidit;

/**
 * Audio effects processing utilities for export and preview.
 * 
 * IMPORTANT: These effects are applied ONLY during export/preview,
 * NOT during real-time recording. This improves recording performance
 * and reduces battery usage.
 * 
 * Effects include:
 * - Noise suppression
 * - Auto-normalization
 */
public class AudioEffects {
    
    /**
     * Apply noise suppression to PCM audio data.
     * This is a simple noise gate implementation.
     * 
     * @param data PCM 16-bit audio data
     * @param threshold Noise threshold (0-32767)
     * @return Processed audio data
     */
    public static byte[] applyNoiseSuppression(byte[] data, int threshold) {
        if (data == null || data.length == 0) {
            return data;
        }
        
        byte[] result = new byte[data.length];
        
        // Process 16-bit samples (2 bytes per sample)
        for (int i = 0; i < data.length - 1; i += 2) {
            // Read 16-bit sample (little-endian)
            short sample = (short) ((data[i + 1] << 8) | (data[i] & 0xFF));
            int amplitude = Math.abs(sample);
            
            // Apply noise gate
            if (amplitude < threshold) {
                sample = 0; // Suppress noise
            }
            
            // Write back (little-endian)
            result[i] = (byte) (sample & 0xFF);
            result[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        
        return result;
    }
    
    /**
     * Apply auto-normalization to PCM audio data.
     * This finds the peak amplitude and scales all samples to maximize volume
     * without clipping.
     * 
     * @param data PCM 16-bit audio data
     * @return Normalized audio data
     */
    public static byte[] applyAutoNormalization(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        
        // Find peak amplitude
        int peak = 0;
        for (int i = 0; i < data.length - 1; i += 2) {
            short sample = (short) ((data[i + 1] << 8) | (data[i] & 0xFF));
            int amplitude = Math.abs(sample);
            if (amplitude > peak) {
                peak = amplitude;
            }
        }
        
        // If already at max or silent, no normalization needed
        if (peak == 0 || peak >= 32000) {
            return data;
        }
        
        // Calculate scaling factor (leave headroom to avoid clipping)
        float scale = 32000.0f / peak;
        
        byte[] result = new byte[data.length];
        
        // Apply normalization
        for (int i = 0; i < data.length - 1; i += 2) {
            short sample = (short) ((data[i + 1] << 8) | (data[i] & 0xFF));
            int normalized = (int) (sample * scale);
            
            // Clamp to prevent overflow
            if (normalized > 32767) normalized = 32767;
            if (normalized < -32768) normalized = -32768;
            
            short normalizedSample = (short) normalized;
            result[i] = (byte) (normalizedSample & 0xFF);
            result[i + 1] = (byte) ((normalizedSample >> 8) & 0xFF);
        }
        
        return result;
    }
    
    /**
     * Apply both noise suppression and auto-normalization.
     * Noise suppression is applied first, then normalization.
     * 
     * @param data PCM 16-bit audio data
     * @param noiseThreshold Noise suppression threshold
     * @return Processed audio data
     */
    public static byte[] applyAll(byte[] data, int noiseThreshold) {
        data = applyNoiseSuppression(data, noiseThreshold);
        data = applyAutoNormalization(data);
        return data;
    }
    
    /**
     * Detect if an audio segment contains only silence/background noise.
     * 
     * @param data PCM 16-bit audio data
     * @param threshold Silence threshold (0-32767)
     * @return true if the segment is silent, false otherwise
     */
    public static boolean isSilent(byte[] data, int threshold) {
        if (data == null || data.length == 0) {
            return true;
        }
        
        // Check all samples
        for (int i = 0; i < data.length - 1; i += 2) {
            short sample = (short) ((data[i + 1] << 8) | (data[i] & 0xFF));
            int amplitude = Math.abs(sample);
            
            // If any sample exceeds threshold, not silent
            if (amplitude >= threshold) {
                return false;
            }
        }
        
        return true;
    }
}
