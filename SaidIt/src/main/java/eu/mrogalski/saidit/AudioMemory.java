package eu.mrogalski.saidit;

import android.os.SystemClock;

import java.io.IOException;
import java.util.LinkedList;

public class AudioMemory {

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
                    
                    // If we have enough consecutive silent segments, overwrite in place
                    if (consecutiveSilentSegments >= silenceSegmentCount) {
                        // Overwrite this silent chunk instead of advancing
                        // Keep current buffer, reset offset to overwrite
                        offset = 0;
                        // Don't add to filled list, reuse this buffer
                    } else {
                        // Not enough silent segments yet, advance normally
                        filled.addLast(current);
                        current = null;
                        offset = 0;
                    }
                } else {
                    // Not silent, reset counter and advance normally
                    consecutiveSilentSegments = 0;
                    filled.addLast(current);
                    current = null;
                    offset = 0;
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
    }

    public synchronized Stats getStats(int fillRate) {
        final Stats stats = new Stats();
        stats.filled = filled.size() * CHUNK_SIZE + (current == null ? 0 : currentWasFilled ? CHUNK_SIZE : offset);
        stats.total = (filled.size() + free.size() + (current == null ? 0 : 1)) * CHUNK_SIZE;
        stats.estimation = (int) (filling ? (SystemClock.uptimeMillis() - fillingStartUptimeMillis) * fillRate / 1000 : 0);
        stats.overwriting = currentWasFilled;
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

}
