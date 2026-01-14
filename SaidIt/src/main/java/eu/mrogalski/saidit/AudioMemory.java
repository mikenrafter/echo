package eu.mrogalski.saidit;

import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.LinkedList;

public class AudioMemory {

    private static final String TAG = AudioMemory.class.getSimpleName();

    private final LinkedList<byte[]> filled = new LinkedList<byte[]>();
    private final LinkedList<byte[]> free = new LinkedList<byte[]>();

    private long fillingStartUptimeMillis;
    private boolean filling = false;
    private boolean currentWasFilled = false;
    private byte[] current = null;
    private int offset = 0;
    static final int CHUNK_SIZE = 1920000; // 20 seconds of 48kHz wav (single channel, 16-bit samples) (1875 kB)
    
    // Silence skipping configuration
    private boolean silenceSkipEnabled = false;
    private int silenceThreshold = 500; // Default threshold for silence detection
    private int silenceSegmentCount = 3; // Number of consecutive silent segments before skipping
    private int consecutiveSilentSegments = 0; // Counter for consecutive silent segments
    private int totalSkippedSegments = 0; // Total number of segments skipped due to silence
    private boolean insideSilentGroup = false; // Whether we are currently accumulating a silence group
    private int currentGroupSegments = 0; // Segments in the current silence group
    private int totalSkippedGroups = 0; // Total number of silence groups skipped
    private final LinkedList<SilenceGroupEntry> silenceGroups = new LinkedList<>(); // Log of silence groups
    
    // Memory debug mode tracking
    private boolean debugMemoryEnabled = false;
    public static class ChunkDebugInfo {
        long timestamp;      // When this chunk was filled
        boolean isSilent;    // Whether this chunk is silent
        int chunkIndex;      // Sequential index for tracking
        
        ChunkDebugInfo(long timestamp, boolean isSilent, int chunkIndex) {
            this.timestamp = timestamp;
            this.isSilent = isSilent;
            this.chunkIndex = chunkIndex;
        }
    }
    private final LinkedList<ChunkDebugInfo> chunkDebugInfo = new LinkedList<>();
    private int chunkCounter = 0;

    synchronized public void allocate(long sizeToEnsure) {
        long currentSize = getAllocatedMemorySize();
        while(currentSize < sizeToEnsure) {
            currentSize += CHUNK_SIZE;
            free.addLast(new byte[CHUNK_SIZE]);
        }
        while(!free.isEmpty() && (currentSize - CHUNK_SIZE >= sizeToEnsure)) {
            currentSize -= CHUNK_SIZE;
            free.removeLast();
        }
        while(!filled.isEmpty() && (currentSize - CHUNK_SIZE >= sizeToEnsure)) {
            currentSize -= CHUNK_SIZE;
            filled.removeFirst();
        }
        if((current != null) && (currentSize - CHUNK_SIZE >= sizeToEnsure)) {
            //currentSize -= CHUNK_SIZE;
            current = null;
            offset = 0;
            currentWasFilled = false;
        }
        System.gc();
    }

    synchronized public long getAllocatedMemorySize() {
        return (free.size() + filled.size() + (current == null ? 0 : 1)) * CHUNK_SIZE;
    }

    public interface Consumer {
        public int consume(byte[] array, int offset, int count) throws IOException;
    }

    private int skipAndFeed(int bytesToSkip, byte[] arr, int offset, int length, Consumer consumer)  throws IOException {
        if(bytesToSkip >= length) {
            return length;
        } else if(bytesToSkip > 0) {
            consumer.consume(arr, offset + bytesToSkip, length - bytesToSkip);
            return bytesToSkip;
        }
        consumer.consume(arr, offset, length);
        return 0;
    }

    public void read(int skipBytes, Consumer reader)  throws IOException {
        synchronized (this) {
            if(!filling && current != null && currentWasFilled) {
                skipBytes -= skipAndFeed(skipBytes, current, offset, current.length - offset, reader);
            }
            for(byte[] arr : filled) {
                skipBytes -= skipAndFeed(skipBytes, arr, 0, arr.length, reader);
            }
            if(current != null && offset > 0) {
                skipAndFeed(skipBytes, current, 0, offset, reader);
            }
        }
    }

    public int countFilled() {
        int sum = 0;
        synchronized (this) {
            if(!filling && current != null && currentWasFilled) {
                sum += current.length - offset;
            }
            for(byte[] arr : filled) {
                sum += arr.length;
            }
            if(current != null && offset > 0) {
                sum += offset;
            }
        }
        return sum;
    }

    public void fill(Consumer filler) throws IOException {
        synchronized (this) {
            if(current == null) {
                if(free.isEmpty()) {
                    if(filled.isEmpty()) return;
                    currentWasFilled = true;
                    current = filled.removeFirst();
                } else {
                    currentWasFilled = false;
                    current = free.removeFirst();
                }
                offset = 0;
            }
            filling = true;
            fillingStartUptimeMillis = SystemClock.uptimeMillis();
        }

        final int read = filler.consume(current, offset, current.length - offset);

        synchronized (this) {
            if(offset + read >= current.length) {
                // Check if chunk is silent for silence skipping
                boolean isSilent = isChunkSilent(current, current.length);
                
                if (silenceSkipEnabled && isSilent) {
                    consecutiveSilentSegments++;
                    Log.d(TAG, "Silent chunk detected. consecutiveSilentSegments=" + consecutiveSilentSegments + ", needed=" + silenceSegmentCount);
                    
                    // If we have enough consecutive silent segments, overwrite in place
                    if (consecutiveSilentSegments >= silenceSegmentCount) {
                        // Overwrite this silent chunk instead of advancing
                        // Keep current buffer, reset offset to overwrite
                        offset = 0;
                        // Don't add to filled list, reuse this buffer
                        totalSkippedSegments++; // Increment skipped counter
                        // Start or continue a silence group
                        if (!insideSilentGroup) {
                            insideSilentGroup = true;
                            currentGroupSegments = 0;
                        }
                        currentGroupSegments++;
                        // Track in debug mode (as skipped silent chunk)
                        trackChunkDebugInfo(true);
                    } else {
                        // Not enough silent segments yet, advance normally
                        filled.addLast(current);
                        trackChunkDebugInfo(true); // Track as silent chunk
                        current = null;
                        offset = 0;
                    }
                } else {
                    // Not silent, reset counter and advance normally
                    consecutiveSilentSegments = 0;
                    filled.addLast(current);
                    trackChunkDebugInfo(false); // Track as non-silent chunk
                    current = null;
                    offset = 0;
                    // If we were in a silence group, close it now
                    if (insideSilentGroup) {
                        insideSilentGroup = false;
                        totalSkippedGroups++;
                        long endTime = System.currentTimeMillis();
                        Log.d(TAG, "Silence group closed. segments=" + currentGroupSegments + ", totalGroups=" + totalSkippedGroups + ", endTime=" + endTime);
                        silenceGroups.addLast(new SilenceGroupEntry(currentGroupSegments, endTime));
                        // Prevent unbounded growth: cap list size
                        if (silenceGroups.size() > 1000) {
                            silenceGroups.removeFirst();
                        }
                        currentGroupSegments = 0;
                    }
                }
            } else {
                offset += read;
            }
            filling = false;
        }
    }

    public static class Stats {
        public int filled; // taken
        public int total;
        public int estimation;
        public boolean overwriting; // currentWasFilled;
        public int skippedSegments; // Total segments skipped due to silence
    }

    public synchronized Stats getStats(int fillRate) {
        final Stats stats = new Stats();
        stats.filled = filled.size() * CHUNK_SIZE + (current == null ? 0 : currentWasFilled ? CHUNK_SIZE : offset);
        stats.total = (filled.size() + free.size() + (current == null ? 0 : 1)) * CHUNK_SIZE;
        stats.estimation = (int) (filling ? (SystemClock.uptimeMillis() - fillingStartUptimeMillis) * fillRate / 1000 : 0);
        stats.overwriting = currentWasFilled;
        stats.skippedSegments = totalSkippedSegments;
        return stats;
    }
    
    /**
     * Configure silence skipping feature.
     * When enabled, if multiple consecutive segments are silent,
     * they will be overwritten in place instead of advancing the buffer.
     * 
     * @param enabled Enable or disable silence skipping
     * @param threshold Silence detection threshold (0-32767)
     * @param segmentCount Number of consecutive silent segments before skipping
     */
    public synchronized void configureSilenceSkipping(boolean enabled, int threshold, int segmentCount) {
        this.silenceSkipEnabled = enabled;
        this.silenceThreshold = threshold;
        this.silenceSegmentCount = Math.max(1, segmentCount);
        this.consecutiveSilentSegments = 0;
        Log.d(TAG, "Silence skipping configured: enabled=" + enabled + ", threshold=" + threshold + ", segmentCount=" + segmentCount);
        // Don't reset totalSkippedSegments - keep the running total
    }
    
    /**
     * Check if the current buffer chunk is silent.
     * Used internally for silence skipping.
     */
    private boolean isChunkSilent(byte[] chunk, int length) {
        if (!silenceSkipEnabled || chunk == null || length == 0) {
            return false;
        }
        return AudioEffects.isSilent(chunk, silenceThreshold);
    }

    public static class SilenceGroupEntry {
        public final int segments;
        public final long endTimeMillis;
        public SilenceGroupEntry(int segments, long endTimeMillis) {
            this.segments = segments;
            this.endTimeMillis = endTimeMillis;
        }
    }

    public synchronized int getSkippedGroupsCount() {
        return totalSkippedGroups;
    }

    public synchronized java.util.ArrayList<SilenceGroupEntry> getSilenceGroupsSnapshot() {
        // Create a snapshot with current timestamps for pruning calculation
        return new java.util.ArrayList<>(silenceGroups);
    }
    
    /**
     * Enable or disable debug memory mode.
     * When enabled, each chunk is tagged with timestamp and silence status.
     * This increases memory overhead, so max memory usage should be reduced to ~80%.
     */
    public synchronized void setDebugMemoryEnabled(boolean enabled) {
        this.debugMemoryEnabled = enabled;
        if (enabled) {
            Log.d(TAG, "Debug memory mode enabled - chunks will be tagged with timestamp and silence status");
        } else {
            Log.d(TAG, "Debug memory mode disabled");
            chunkDebugInfo.clear();
        }
    }
    
    /**
     * Get debug info for all chunks currently in memory.
     * Returns a snapshot of chunk timestamps and silence status.
     */
    public synchronized java.util.ArrayList<ChunkDebugInfo> getChunkDebugInfo() {
        return new java.util.ArrayList<>(chunkDebugInfo);
    }
    
    /**
     * Track a chunk in debug mode.
     * Called internally when a chunk is filled.
     */
    private void trackChunkDebugInfo(boolean isSilent) {
        if (!debugMemoryEnabled) return;
        
        long timestamp = System.currentTimeMillis();
        ChunkDebugInfo info = new ChunkDebugInfo(timestamp, isSilent, chunkCounter++);
        chunkDebugInfo.addLast(info);
        
        // Limit debug info size to match filled chunks
        while (chunkDebugInfo.size() > filled.size() + 1) {
            chunkDebugInfo.removeFirst();
        }
        
        Log.d(TAG, String.format("Chunk %d tracked: timestamp=%d, silent=%b", 
            info.chunkIndex, timestamp, isSilent));
    }

}
