package eu.mrogalski.saidit;

import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds activity blocks from silence groups.
 * Activity blocks represent periods of non-silence audio, grouped into chunks of a configurable size.
 */
public class ActivityBlockBuilder {
    private static final String TAG = ActivityBlockBuilder.class.getSimpleName();
    
    /**
     * Represents a block of continuous audio activity.
     * Each block is at most blockSizeMillis long, unless it's the most recent one
     * and hasn't reached the threshold yet.
     */
    public static class ActivityBlock {
        public long startTimeMillis;      // When this block started
        public long endTimeMillis;        // When this block ended
        public long durationMillis;       // Total duration of this block
        public int blockIndex;            // 0-based index from oldest to newest
        public boolean isRecorded;        // Whether this block contains recorded data (false for padding)
        
        public ActivityBlock(long startTimeMillis, long endTimeMillis) {
            this.startTimeMillis = startTimeMillis;
            this.endTimeMillis = endTimeMillis;
            this.durationMillis = endTimeMillis - startTimeMillis;
            this.isRecorded = true;       // Default to recorded
        }
    }
    
    /**
     * Build activity blocks from silence groups.
     * Activity blocks are gaps between silence groups, grouped into blockSizeMillis chunks.
     * Each block is at most blockSizeMillis long, unless it's the most recent one
     * and hasn't reached the threshold yet.
     * 
     * @param silenceGroups List of silence groups (may be null or empty)
     * @param totalMemorySeconds Total time span of available memory (can be 0 to calculate from groups)
     * @param blockSizeMillis Size of each activity block in milliseconds
     * @return List of activity blocks, or empty list if no silence groups or invalid input
     */
        public static List<ActivityBlock> buildActivityBlocks(
            List<SaidItService.SilenceGroup> silenceGroups,
            float totalMemorySeconds,
            long blockSizeMillis) {
        
        List<ActivityBlock> blocks = new ArrayList<>();
        
        // Validate input
        if (blockSizeMillis <= 0) {
            Log.d(TAG, "buildActivityBlocks: Invalid input - silenceGroups=" + 
                (silenceGroups != null ? silenceGroups.size() : "null") + 
                ", totalMemorySeconds=" + totalMemorySeconds + 
                ", blockSizeMillis=" + blockSizeMillis);
            return blocks;
        }
        
        // Sort silence groups by endTime (ascending order)
        List<SaidItService.SilenceGroup> sortedGroups = new ArrayList<>();
        if (silenceGroups != null) {
            sortedGroups.addAll(silenceGroups);
            Collections.sort(sortedGroups, (g1, g2) -> Long.compare(g1.endTimeMillis, g2.endTimeMillis));
        }
        
        long now = System.currentTimeMillis();
        
        // Calculate memory span from silence groups if not provided
        long oldestTime;
        if (totalMemorySeconds > 0) {
            long memorySpanMillis = (long)(totalMemorySeconds * 1000);
            oldestTime = now - memorySpanMillis;
        } else {
            if (!sortedGroups.isEmpty()) {
                // Use the oldest silence group as the oldest time
                SaidItService.SilenceGroup oldestGroup = sortedGroups.get(0);
                oldestTime = oldestGroup.endTimeMillis - oldestGroup.durationMillis;
            } else {
                // No information – assume no blocks
                Log.d(TAG, "buildActivityBlocks: No silence groups and totalMemorySeconds=0; returning empty");
                return blocks;
            }
        }
        
        Log.d(TAG, "buildActivityBlocks: now=" + now + ", oldestTime=" + oldestTime + 
            ", blockSizeMillis=" + blockSizeMillis);
        
        // Track activity periods as gaps between silence groups
        long lastActivityStart = oldestTime;
        
        for (SaidItService.SilenceGroup silenceGroup : sortedGroups) {
            long silenceStart = silenceGroup.endTimeMillis - silenceGroup.durationMillis;
            
            // There's activity between lastActivityStart and silenceStart
            if (silenceStart > lastActivityStart) {
                long activityDuration = silenceStart - lastActivityStart;
                Log.d(TAG, "Found activity gap: " + activityDuration + "ms between " + lastActivityStart + " and " + silenceStart);
                
                // Split long activity periods into blocks of blockSizeMillis
                long blockStart = lastActivityStart;
                while (blockStart < silenceStart) {
                    long blockEnd = Math.min(blockStart + blockSizeMillis, silenceStart);
                    ActivityBlock block = new ActivityBlock(blockStart, blockEnd);
                    block.blockIndex = blocks.size();
                    blocks.add(block);
                    Log.d(TAG, "Added block: " + block.durationMillis + "ms (index=" + block.blockIndex + ")");
                    blockStart = blockEnd;
                }
            }
            
            // Move past this silence group
            lastActivityStart = silenceGroup.endTimeMillis;
        }
        
        // Activity from last silence group to now
        if (lastActivityStart < now) {
            long remainingActivity = now - lastActivityStart;
            Log.d(TAG, "Remaining activity after last silence: " + remainingActivity + "ms");
            
            // For the most recent block, only create it if it has substantial content
            // or will be shown as current activity
            if (remainingActivity >= blockSizeMillis) {
                long blockStart = lastActivityStart;
                while (blockStart < now) {
                    long blockEnd = Math.min(blockStart + blockSizeMillis, now);
                    ActivityBlock block = new ActivityBlock(blockStart, blockEnd);
                    block.blockIndex = blocks.size();
                    blocks.add(block);
                    Log.d(TAG, "Added final block: " + block.durationMillis + "ms (index=" + block.blockIndex + ")");
                    blockStart = blockEnd;
                }
            } else {
                Log.d(TAG, "Skipping recent activity block (too small): " + remainingActivity + "ms < " + blockSizeMillis + "ms");
            }
            // Note: recent activity < blockSizeMillis is shown as "current activity" preview
        }
        
        Log.d(TAG, "buildActivityBlocks complete: created " + blocks.size() + " blocks");
        return blocks;
    }
}
