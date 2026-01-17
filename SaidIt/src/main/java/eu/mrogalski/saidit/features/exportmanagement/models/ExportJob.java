package eu.mrogalski.saidit.features.exportmanagement.models;

import java.io.File;

/**
 * Represents an export job.
 */
public class ExportJob {
    private final String id;
    private final File outputFile;
    private final long timestamp;
    private ExportStatus status;
    
    public ExportJob(String id, File outputFile, long timestamp) {
        this.id = id;
        this.outputFile = outputFile;
        this.timestamp = timestamp;
        this.status = ExportStatus.PENDING;
    }
    
    public String getId() {
        return id;
    }
    
    public File getOutputFile() {
        return outputFile;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public ExportStatus getStatus() {
        return status;
    }
    
    public void setStatus(ExportStatus status) {
        this.status = status;
    }
    
    public enum ExportStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
