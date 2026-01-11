# Silence Timeline Integration with AudioMemory

## Overview
Updated the main activity's timeline display to show silence blocks based on the actual silence segments tracked by `AudioMemory` rather than relying solely on volume-based silence detection. This ensures the timeline accurately reflects the 20-second silence segments that are being skipped in the audio buffer.

## Changes Made

### SaidItFragment.java

#### Modified: `updateTimelineDisplay()` Method
- **Previous behavior**: Displayed activity/silence timeline based on volume-level transitions tracked in `SaidItService.TimelineSegment`
- **New behavior**: 
  - Fetches both the activity timeline from `getTimeline()` AND the actual silence groups from `getSilenceGroups()`
  - Uses the actual silence groups from `AudioMemory` to drive the silence display
  - Only shows save buttons (show from/to selection buttons) when actual silence groups exist
  - Displays silence groups in reverse chronological order (newest first) to match activity segment ordering

#### Added: `addSilenceGroupView(SilenceGroup group)` Method
- New helper method to display silence groups from `AudioMemory`
- Shows silence duration calculated from the number of 20-second segments
- Formats duration in HH:MM:SS format using existing `formatDuration()` method
- Uses matching visual styling (gray text color) as the old silence view for consistency

## Key Design Decisions

1. **Silence Source of Truth**: Silence blocks now come from `AudioMemory.silenceGroups` which tracks:
   - Number of consecutive 20-second segments that were silent
   - End timestamp of the silence group
   - Proper duration calculation based on segment count

2. **Activity Timeline Preserved**: Activity segments continue to be displayed from the volume-based timeline:
   - Shows current/ongoing activity
   - Shows historical activity periods
   - Maintains save range selection functionality

3. **Timeline Block Size**: 
   - Activity timeline block size configuration remains in place (5, 10, 15, 30, 60 minutes)
   - Silence groups are always displayed at their actual 20-second segment granularity
   - This provides accurate representation of what's actually being skipped in the buffer

4. **Chronological Display**: Both activity and silence segments display in newest-first order for consistent timeline presentation

## Integration Points

The implementation leverages existing APIs:
- `SaidItService.getSilenceGroups(SilenceGroupsCallback)` - Fetches silence groups asynchronously
- `SaidItService.SilenceGroup` - Data class containing silence duration and end timestamp
- `AudioMemory.getSilenceGroupsSnapshot()` - Thread-safe snapshot of current silence groups

## Benefits

1. **Accuracy**: Timeline now shows exactly what silence was actually detected and skipped
2. **Consistency**: Silence display is directly tied to the buffer management logic
3. **User Clarity**: Users see the actual silence periods being handled by the silence-skipping feature
4. **Maintainability**: Single source of truth for silence data reduces inconsistencies

## Build Status
✅ Successfully builds with no compilation errors

## Testing Recommendations

1. Enable silence skipping feature
2. Record audio with natural breaks/silence periods
3. Verify silence blocks appear on timeline matching actual silent segments
4. Confirm "save from/to" buttons only appear when silence groups exist
5. Test with different silence thresholds and segment counts
