package eu.mrogalski.saidit.features.audiocapture.models;

/**
 * Event published with live audio statistics.
 */
public class AudioStatsEvent {
    private final long availableMemoryBytes;
    private final long totalMemoryBytes;
    private final int skippedSilenceSegments;
    private final long diskUsageBytes;
    
    public AudioStatsEvent(long availableMemoryBytes, long totalMemoryBytes, 
                           int skippedSilenceSegments, long diskUsageBytes) {
        this.availableMemoryBytes = availableMemoryBytes;
        this.totalMemoryBytes = totalMemoryBytes;
        this.skippedSilenceSegments = skippedSilenceSegments;
        this.diskUsageBytes = diskUsageBytes;
    }
    
    public long getAvailableMemoryBytes() {
        return availableMemoryBytes;
    }
    
    public long getTotalMemoryBytes() {
        return totalMemoryBytes;
    }
    
    public int getSkippedSilenceSegments() {
        return skippedSilenceSegments;
    }
    
    public long getDiskUsageBytes() {
        return diskUsageBytes;
    }
}
