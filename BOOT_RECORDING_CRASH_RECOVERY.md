# Boot Recording and Crash Recovery Implementation

## Summary

This implementation adds the following features to the Echo app:

### 1. Auto-Start Recording on Boot (with Toggleable Setting)
- **Setting Key**: `START_RECORDING_ON_BOOT_KEY`
- **UI Location**: Settings → Memory Settings section
- **Behavior**: When enabled, the app will automatically start recording when the device boots up
- **Implementation**:
  - Updated `BroadcastReceiver.java` to check the preference on `BOOT_COMPLETED`
  - If enabled, the receiver automatically calls `enableListening()` on the service
  - User can toggle this in Settings with a checkbox and description

### 2. Crash Detection and Notification System
- **Mechanism**: Uses a flag-based approach
  - `CRASH_DETECTED_KEY` flag is set in `onCreate()` and cleared in `onDestroy()`
  - If the flag is still set when the app restarts, a crash is detected
- **Notification**: Shows a system notification informing the user that:
  - The app has restarted
  - The memory buffer was lost
  - Special message if the crash was OOM-related (memory reduced)
- **Auto-Restart**: Android's `START_STICKY` service type already ensures the service restarts

### 3. OOM Detection and Memory Reduction
- **Detection**: 
  - Modified `AudioMemory.allocate()` to return `boolean` (true on success, false on OOM)
  - Catches `OutOfMemoryError` and returns false
  - All callers in `SaidItService` check the return value
- **OOM Flag**: When OOM is detected during allocation, sets `CRASH_WAS_OOM_KEY` flag
- **Memory Reduction**:
  - On next app start, if `CRASH_WAS_OOM_KEY` is set, reduces memory by 20MB (as requested)
  - Only reduces if current memory > (minimum + 20MB)
  - Prevents reduction below the minimum of 10MB
- **Step Size**: Set to 20MB per the user's request (different from the 10MB in docs)

## Files Modified

### Core Logic
1. **SaidIt.java**
   - Added constants for boot recording, crash detection, and OOM handling
   - `START_RECORDING_ON_BOOT_KEY`
   - `CRASH_DETECTED_KEY`
   - `CRASH_WAS_OOM_KEY`
   - `MEMORY_SIZE_VERIFIED_KEY`
   - `MEMORY_REDUCTION_STEP_MB` (set to 20)

2. **SaidItService.java**
   - `onCreate()`: Detects crashes, handles OOM recovery, shows notification
   - `onDestroy()`: Clears crash flag on normal shutdown
   - `showCrashNotification()`: New method to display crash notifications
   - `innerStartListening()`: Updated to handle OOM during memory allocation
   - `setMemorySize()`: Updated to handle OOM during manual memory changes

3. **AudioMemory.java**
   - `allocate()`: Changed return type to `boolean`, catches `OutOfMemoryError`

4. **BroadcastReceiver.java**
   - `onReceive()`: Enhanced `BOOT_COMPLETED` handler to optionally start recording

### UI
5. **activity_settings.xml**
   - Added checkbox for "Start recording on boot" with description
   - Placed in the Memory Settings section

6. **strings.xml**
   - Added UI strings:
     - `start_recording_on_boot`
     - `start_recording_on_boot_description`
     - `crash_notification_title`
     - `crash_notification_message`
     - `oom_crash_notification_message`

7. **SettingsActivity.java**
   - Added `initBootRecordingControls()` method
   - Handles checkbox state persistence and user feedback

## How It Works

### Normal Flow
1. User enables "Start recording on boot" in Settings
2. Device reboots
3. System sends `BOOT_COMPLETED` broadcast
4. `BroadcastReceiver` starts the service and enables listening
5. App begins recording automatically

### Crash Recovery Flow
1. App crashes (for any reason)
2. `CRASH_DETECTED_KEY` remains set (wasn't cleared in `onDestroy()`)
3. If crash was OOM, `CRASH_WAS_OOM_KEY` is also set
4. System (or user) restarts the app
5. `onCreate()` detects the crash flags
6. Shows appropriate notification to user
7. If OOM: Reduces memory size by 20MB (unless at minimum)
8. Clears crash flags
9. Continues normal startup

### OOM Prevention
- When allocating memory fails, the OOM flag is set
- On next restart, memory is automatically reduced
- Prevents crash loops from excessive memory allocation
- Respects minimum memory size (10MB)

## User Experience
- **Transparent**: User is informed when crashes occur
- **Automatic Recovery**: No manual intervention needed for OOM issues
- **Configurable**: Boot recording can be easily toggled on/off
- **Safe**: Memory reduction prevents repeated OOM crashes

## Testing Recommendations
1. Test boot recording enable/disable
2. Force crash the app and verify notification appears
3. Force OOM by setting memory too high, verify reduction occurs
4. Verify minimum memory limit is respected
5. Test normal shutdown doesn't trigger crash notification
