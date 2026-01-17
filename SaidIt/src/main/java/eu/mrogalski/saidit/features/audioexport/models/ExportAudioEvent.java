package eu.mrogalski.saidit.features.audioexport.models;

import java.io.File;

/**
 * Event requesting audio export.
 */
public class ExportAudioEvent {
    private final File outputFile;
    private final float prependSeconds;
    private final boolean normalize;
    private final boolean noiseCancellation;
    private final String format; // "WAV" or "AAC"
    
    public ExportAudioEvent(File outputFile, float prependSeconds, boolean normalize, 
                            boolean noiseCancellation, String format) {
        this.outputFile = outputFile;
        this.prependSeconds = prependSeconds;
        this.normalize = normalize;
        this.noiseCancellation = noiseCancellation;
        this.format = format;
    }
    
    public File getOutputFile() {
        return outputFile;
    }
    
    public float getPrependSeconds() {
        return prependSeconds;
    }
    
    public boolean isNormalize() {
        return normalize;
    }
    
    public boolean isNoiseCancellation() {
        return noiseCancellation;
    }
    
    public String getFormat() {
        return format;
    }
}
