package eu.mrogalski.saidit.features.audioexport.models;

import java.io.File;

/**
 * Event published when export completes.
 */
public class ExportCompleteEvent {
    private final File outputFile;
    private final boolean success;
    private final String errorMessage;
    
    public ExportCompleteEvent(File outputFile, boolean success, String errorMessage) {
        this.outputFile = outputFile;
        this.success = success;
        this.errorMessage = errorMessage;
    }
    
    public File getOutputFile() {
        return outputFile;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}
