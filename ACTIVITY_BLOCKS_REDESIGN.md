# Activity Blocks Redesign from Silence-Skipping Data

## Overview
Completely redesigned the activity/timeline display to calculate activity blocks directly from the silence-skipping data in `AudioMemory`. Activity blocks are now derived from gaps between silence groups, with proper block size grouping (default 5 minutes).

## Key Changes

### 1. Activity Block Calculation
- **Source**: Silence groups from `AudioMemory.getSilenceGroupsSnapshot()`
- **Logic**: Activity periods are calculated as gaps between silence group end times
- **Grouping**: Activity gaps are split into n-minute blocks (default 5 minutes)
  - Each block is at most `blockSizeMinutes` long
  - The most recent block may be smaller if it hasn't reached the threshold yet

### 2. Data Structure
New `ActivityBlock` class in `SaidItFragment`:
```java
private static class ActivityBlock {
    long startTimeMillis;      // When this block started
    long endTimeMillis;        // When this block ended
    long durationMillis;       // Total duration of this block
    int blockIndex;            // 0-based index from oldest to newest
}
```

### 3. Algorithm: `buildActivityBlocks()`
1. Sort silence groups by end time (oldest first)
2. Identify activity gaps between consecutive silence groups
3. Split each gap into `blockSizeMillis` chunks
4. For the most recent activity (after the last silence group):
   - Only create blocks if they meet or exceed `blockSizeMinutes` duration
   - Remaining activity < threshold is shown as "current activity" preview

### 4. Timeline Display Flow
1. Fetch silence groups from AudioMemory
2. Calculate activity blocks from silence gaps
3. Display timeline in reverse chronological order (newest first):
   - Current activity (live preview, if active)
   - Historical activity blocks (with save buttons if silence exists)
   - Silence groups (actual skipped segments)

### 5. Live Readout Preserved
- Current/ongoing activity is still displayed as `[RECORDING]` for preview
- Live preview doesn't count toward activity block thresholds
- Only used for real-time visual feedback

## Configuration
- **Block Size**: Default 5 minutes (configurable via settings)
- **Location**: `SaidItService.blockSizeMinutes`
- Currently hardcoded to 5 in fragment; should fetch from SharedPreferences when available

## Updated Selection Handling
- `handleActivityBlockSelection()`: Replaces `handleTimelineSegmentSelection()`
- Works with `ActivityBlock` timestamps instead of `TimelineSegment`
- Selection logic unchanged: FROM/TO pattern with save confirmation dialog

## Architecture Benefits

1. **Single Source of Truth**: Activity display driven by actual buffer management (silence groups)
2. **Consistent Data**: No discrepancy between what's shown and what's actually being skipped
3. **Block Size Control**: Activity properly grouped into user-configurable chunks
4. **Memory Accurate**: Reflects actual memory span and available audio data
5. **Clean Separation**: 
   - Live readout for real-time preview
   - Block-based timeline for structured audio layout

## Build Status
✅ Successfully compiles with no errors

## Testing Recommendations

1. **With Silence Skipping Enabled**:
   - Enable silence-skipping feature
   - Record audio with natural breaks
   - Verify activity blocks appear at correct 5-minute boundaries
   - Confirm silence groups display with actual durations

2. **Without Silence Skipping**:
   - Disable silence-skipping
   - Record continuous audio
   - Should show activity blocks for full recording period

3. **Edge Cases**:
   - Activity less than block size (should show as current preview only)
   - Multiple silence groups with varying gaps
   - Long activity periods (should split into multiple blocks)
   - Recent activity still accumulating (no block until threshold reached)

4. **Save Range Selection**:
   - FROM/TO selection should work with activity blocks
   - Time ranges should be calculated correctly from block timestamps
