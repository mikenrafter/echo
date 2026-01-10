# Activity/Silence Timeline Implementation

## Overview
This document describes the implementation of the dynamic activity/silence timeline display feature for the Echo app, as requested in the issue.

## Features Implemented

### 1. Updated Readouts with Ring Size Information
- **Settings Activity**: Now displays accurate ring size information when gradient quality is enabled
  - Format: "48 kHz: 0-5m, 16 kHz: 5-20m, 8 kHz: 20m+"
  - Falls back to total memory time display when gradient quality is disabled

### 2. UI Reorganization
- **Clip Recording Button**: Moved to top of screen, directly below "Echo Enabled" button
- **Description Text**: "Echo lets you go back in time..." moved above FAQ section
- **Memory Text**: Removed "Memory holds the most recent" static text from main screen
- **Volume Display**: Made SILENT label and activity bar (Live volume) mutually exclusive
  - When volume is below silence threshold: Shows "SILENT", hides progress bar
  - When volume is above threshold: Shows "Live volume" with progress bar

### 3. Activity/Silence Timeline Display
- **Timeline Container**: New scrollable container showing recording timeline
- **Current Activity Segment**: Displays ongoing activity with [RECORDING] indicator and live duration
- **Historical Segments**: Shows past activity and silence periods in reverse chronological order (newest first)
- **Duration Format**: All durations displayed in HH:MM:SS format
- **Visibility**: Timeline only visible when there are segments to display

#### Timeline Segment Tracking
- **Data Structure**: `TimelineSegment` class with type (ACTIVITY/SILENCE), timestamps, and duration
- **Live Tracking**: Monitors volume level to detect activity/silence transitions
- **Segment Management**: Maintains up to 50 most recent segments
- **Automatic Transitions**: Seamlessly switches between activity and silence states

### 4. Save From/To Selection
- **Button States**: 
  - Initial state: "save from here" on all activity segments
  - After first selection: Changes to "save to here" on all segments
- **Selection Process**:
  1. User clicks first activity segment → FROM selected
  2. User clicks second activity segment → TO selected
  3. Dialog prompts for filename
  4. Range is saved using existing `dumpRecordingRange` API
- **Order Correction**: Automatically validates and corrects range order (older to newer)
- **Inclusive Selection**: Selecting same block twice includes entire block
- **Boundary Validation**: Ensures ranges are within available audio memory

### 5. Block Size Configuration
- **Settings UI**: Added configuration for activity block sizes
- **Available Options**: 5, 10, 15, 30, 60 minutes
- **Default**: 5 minutes
- **Persistence**: Saved in shared preferences
- **Note**: Currently a configuration placeholder - timeline displays naturally-sized segments based on actual activity periods

## Technical Implementation Details

### Service Layer (SaidItService.java)
1. **TimelineSegment Class**: Nested static class representing activity or silence periods
2. **Timeline Tracking**: 
   - `updateTimeline()` method called on every audio sample processing
   - Detects transitions based on volume threshold
   - Manages segment lifecycle (create, end, store)
3. **Timeline API**: `getTimeline()` callback interface for UI to retrieve segments
4. **Thread Safety**: Synchronized access to timeline segments list

### Fragment Layer (SaidItFragment.java)
1. **Timeline Display**: 
   - `updateTimelineDisplay()` refreshes UI from service data
   - `addCurrentActivityView()` displays ongoing activity
   - `addActivityView()` displays historical activity with save buttons
   - `addSilenceView()` displays silence periods
2. **Selection Management**:
   - `TimelineSegmentSelection` tracks FROM and TO selections
   - `handleTimelineSegmentSelection()` manages selection state
   - `initiateRangeSave()` calculates time ranges and initiates save
3. **Duration Formatting**: `formatDuration()` converts seconds to HH:MM:SS

### Layout Changes
1. **fragment_background_recorder.xml**:
   - Moved clip recording button to top
   - Added timeline container with scrollable segment list
   - Reorganized text elements
2. **activity_settings.xml**:
   - Added block size configuration section with 5 buttons

### String Resources
- Added strings for timeline UI elements
- Added strings for save button states
- Added strings for block size configuration

## User Workflow

### Viewing Timeline
1. Enable Echo listening
2. Timeline appears below volume meter showing:
   - Current activity (if actively recording sound)
   - Recent activity periods with durations
   - Silence breaks between activity

### Saving a Range
1. Wait for multiple activity periods separated by silence
2. Click "save from here" on first desired activity segment
3. Click "save to here" on last desired activity segment
4. Enter filename in dialog
5. Click Save to export the range

### Configuring Block Size
1. Go to Settings
2. Scroll to "Activity Block Size" section
3. Select desired block size (5, 10, 15, 30, or 60 minutes)
4. Setting is saved for future reference

## Known Limitations and Future Enhancements

1. **Block Size**: Currently a configuration placeholder - timeline shows naturally-sized segments
2. **Memory Pressure**: Timeline keeps last 50 segments; older segments are pruned
3. **Transition Accuracy**: Segment transitions occur at audio sample processing intervals (~20ms)
4. **Save Validation**: Range validation ensures boundaries but doesn't check for sufficient data

## Testing Recommendations

1. **UI Changes**:
   - Verify clip recording button position at top
   - Confirm SILENT vs activity bar mutual exclusivity
   - Check timeline container visibility

2. **Timeline Display**:
   - Test with various activity patterns
   - Verify HH:MM:SS duration formatting
   - Check current segment [RECORDING] indicator

3. **Save Range**:
   - Test FROM/TO selection with different segments
   - Verify button text changes
   - Test with inverted selection order
   - Test with same segment selected twice

4. **Settings**:
   - Verify gradient quality ring info display
   - Test block size configuration persistence
   - Check button highlighting

5. **Edge Cases**:
   - No activity (timeline hidden)
   - Continuous activity (no silence segments, no save buttons)
   - Single activity segment
   - Very short segments (<1 second)

## Code Quality Notes

- All changes follow existing code patterns
- Minimal modifications to existing functionality
- Thread-safe access to shared data
- Proper resource cleanup
- Consistent naming conventions
- Adequate logging for debugging
