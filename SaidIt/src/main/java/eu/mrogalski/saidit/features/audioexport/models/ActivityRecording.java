package eu.mrogalski.saidit.features.audioexport.models;

import java.io.File;

/**
 * Represents a recording captured by audio activity detection.
 */
public class ActivityRecording {
    private long id;
    private long timestamp; // Unix timestamp in milliseconds
    private int durationSeconds;
    private String filePath;
    private boolean isFlagged;
    private long deleteAfterTimestamp; // Unix timestamp when this should be auto-deleted
    private int fileSize; // In bytes
    
    /**
     * Creates a new ActivityRecording.
     */
    public ActivityRecording() {
        this.timestamp = System.currentTimeMillis();
        this.isFlagged = false;
    }
    
    /**
     * Creates a new ActivityRecording with all fields.
     */
    public ActivityRecording(long id, long timestamp, int durationSeconds, String filePath, 
                           boolean isFlagged, long deleteAfterTimestamp, int fileSize) {
        this.id = id;
        this.timestamp = timestamp;
        this.durationSeconds = durationSeconds;
        this.filePath = filePath;
        this.isFlagged = isFlagged;
        this.deleteAfterTimestamp = deleteAfterTimestamp;
        this.fileSize = fileSize;
    }
    
    // Getters and setters
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getDurationSeconds() {
        return durationSeconds;
    }
    
    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public boolean isFlagged() {
        return isFlagged;
    }
    
    public void setFlagged(boolean flagged) {
        isFlagged = flagged;
    }
    
    public long getDeleteAfterTimestamp() {
        return deleteAfterTimestamp;
    }
    
    public void setDeleteAfterTimestamp(long deleteAfterTimestamp) {
        this.deleteAfterTimestamp = deleteAfterTimestamp;
    }
    
    public int getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }
    
    /**
     * Gets the File object for this recording.
     */
    public File getFile() {
        return new File(filePath);
    }
    
    /**
     * Checks if this recording should be deleted now.
     */
    public boolean shouldDelete() {
        if (isFlagged) {
            return false; // Never auto-delete flagged recordings
        }
        return System.currentTimeMillis() >= deleteAfterTimestamp;
    }
    
    /**
     * Gets a human-readable duration string.
     */
    public String getDurationString() {
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    /**
     * Gets a human-readable file size string.
     */
    public String getFileSizeString() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
    
    @Override
    public String toString() {
        return "ActivityRecording{" +
                "id=" + id +
                ", timestamp=" + timestamp +
                ", durationSeconds=" + durationSeconds +
                ", filePath='" + filePath + '\'' +
                ", isFlagged=" + isFlagged +
                ", deleteAfterTimestamp=" + deleteAfterTimestamp +
                ", fileSize=" + fileSize +
                '}';
    }
}
