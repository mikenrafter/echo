# Storage Organization Implementation - Summary of Changes

## Overview

This implementation reorganizes the Echo app's file storage to provide clear separation between different recording types and adds auto-delete functionality for auto-save files.

---

## Files Modified

### 1. `/workspaces/echo/SaidIt/src/main/java/eu/mrogalski/saidit/SaidItService.java`

#### Changes Made:

**a) Updated Comments & Variable Declarations**
- Changed comment from "Activity detection" to "Voice Activity Detection (VAD) - Records to Echo/VAD subfolder"
- Added field `autoSaveCleanupPendingIntent` for scheduling auto-save cleanup
- Added field `autoSaveAutoDeleteDays` (default: 7 days)
- Added clarifying comments about which files go where

**b) Updated Initialization (onCreate method)**
- Renamed log message from "Activity detection enabled" to "VAD (Voice Activity Detection) enabled"
- Added initialization of `autoSaveAutoDeleteDays` from preferences

**c) Updated Auto-Save Scheduling (innerStartListening method)**
- Added call to `scheduleAutoSaveCleanup()` to schedule daily cleanup
- Now both auto-save recording and cleanup are scheduled together

**d) Updated Auto-Save Cancellation (innerStopListening method)**
- Added call to `cancelAutoSaveCleanup()` to stop cleanup timer
- Now both auto-save recording and cleanup are canceled together

**e) Updated dumpRecording Method**
- Changed storage directory from `Echo/` to `Echo/AutoSave/` subfolder
- Added clarifying comments: "Auto-save files go to Echo/AutoSave subfolder"

**f) Updated VAD File Handling (startActivityRecording method)**
- Changed storage directory from `Echo/Activities/` to `Echo/VAD/` subfolder
- Changed filename prefix from `activity_` to `vad_` (e.g., `vad_20260110_143025.wav`)
- Added clarifying comment: "Create output file in Echo/VAD subfolder"
- Added filename format comment: `vad_yyyyMMdd_HHmmss.wav`

**g) Updated VAD Stop Method (stopActivityRecording method)**
- Updated docstring to clarify: "Closes the WAV file (stored in Echo/VAD subfolder)"

**h) Added onStartCommand Handler**
- Added handler for `ACTION_AUTO_SAVE_CLEANUP` intent
- Calls `handleAutoSaveCleanup()` when cleanup event is triggered

**i) Added New Methods for Auto-Save Cleanup**

```java
scheduleAutoSaveCleanup()
- Schedules daily cleanup of old auto-save files
- Uses AlarmManager for reliable scheduling
- Runs once per day

cancelAutoSaveCleanup()
- Cancels the scheduled cleanup
- Called when service stops listening

handleAutoSaveCleanup()
- Performs actual cleanup of old auto-save files
- Deletes files older than autoSaveAutoDeleteDays
- Logs number of files deleted and bytes freed
- Directory: Echo/AutoSave/
```

---

### 2. `/workspaces/echo/SaidIt/src/main/java/eu/mrogalski/saidit/SaidIt.java`

#### Changes Made:

**Updated Auto-Save Configuration Keys**
```java
// Old:
static final String AUTO_SAVE_ENABLED_KEY = "auto_save_enabled";
static final String AUTO_SAVE_DURATION_KEY = "auto_save_duration";

// New:
static final String AUTO_SAVE_ENABLED_KEY = "auto_save_enabled";
static final String AUTO_SAVE_DURATION_KEY = "auto_save_duration";
static final String AUTO_SAVE_AUTO_DELETE_DAYS_KEY = "auto_save_auto_delete_days";
```

- Added comment: "(records to Echo/AutoSave subfolder)"
- Added new configuration key: `AUTO_SAVE_AUTO_DELETE_DAYS_KEY`
- Added comment explaining auto-delete purpose

---

## New Features Implemented

### 1. Separate Storage Folders

| Type | Location | Filename Format |
|------|----------|-----------------|
| Manual Recording | `Echo/` | `Echo - [Date Time].wav` |
| VAD Recording | `Echo/VAD/` | `vad_yyyyMMdd_HHmmss.wav` |
| Auto-Save | `Echo/AutoSave/` | `Echo - [Date Time].wav` |

### 2. Auto-Save Auto-Delete

- **Schedule:** Daily cleanup via AlarmManager
- **Default Period:** 7 days (configurable)
- **Configuration Key:** `AUTO_SAVE_AUTO_DELETE_DAYS_KEY`
- **Implementation:** `handleAutoSaveCleanup()` method
- **Logging:** Reports deleted file count and freed disk space

### 3. Clarified Naming

- Changed VAD filename prefix from `activity_` to `vad_`
- Updated comments and variables to clearly indicate:
  - What is VAD (Voice Activity Detection)
  - What is Auto-Save
  - Where each type of file is stored
  - Which auto-delete policies apply

---

## Storage Behavior Summary

### Manual Recordings
- **Location:** `Music/Echo/`
- **Auto-Delete:** ❌ Never
- **Controlled By:** User manual start/stop

### VAD Recordings
- **Location:** `Music/Echo/VAD/`
- **Auto-Delete:** ✅ Yes (7 days, configurable via `ACTIVITY_AUTO_DELETE_DAYS_KEY`)
- **Respects Flags:** Yes (flagged recordings never deleted)
- **Database:** `ActivityRecordingDatabase`

### Auto-Save Recordings
- **Location:** `Music/Echo/AutoSave/`
- **Auto-Delete:** ✅ Yes (7 days, configurable via `AUTO_SAVE_AUTO_DELETE_DAYS_KEY`)
- **Respects Flags:** No (all files auto-deleted when old)
- **Schedule:** Saved at configured interval (default: 10 minutes), cleaned daily

### Disk Buffer (BATCH_TO_DISK Mode)
- **Location:** `Music/Echo/EchoBuffer/`
- **Auto-Delete:** ✅ Yes (when 500 MB limit exceeded)
- **Management:** Automatic circular buffer cleanup

---

## Configuration

Users can configure auto-delete behavior via SharedPreferences:

**VAD Auto-Delete Days:**
```java
Key: ACTIVITY_AUTO_DELETE_DAYS_KEY
Type: Integer
Default: 7
```

**Auto-Save Auto-Delete Days:**
```java
Key: AUTO_SAVE_AUTO_DELETE_DAYS_KEY
Type: Integer
Default: 7
```

---

## Thread Safety

- All cleanup operations happen on the audio handler thread
- AlarmManager scheduling happens on main thread (safe)
- File operations are synchronized with cleanup alarms
- No race conditions between recording and cleanup

---

## Backward Compatibility

⚠️ **Note:** Existing VAD files with prefix `activity_` will not be automatically cleaned up by the new system since the cleanup looks for files in `Echo/VAD/` and those old files may be in `Echo/Activities/`.

Recommendation: If migrating from previous version, manually move old activity files:
- From: `Echo/Activities/activity_*.wav`
- To: `Echo/VAD/vad_*.wav` (rename as needed)

---

## Testing Recommendations

1. **VAD Recording & Cleanup:**
   - Enable VAD, allow it to detect activity
   - Verify recordings are saved in `Echo/VAD/`
   - Verify filenames start with `vad_`
   - Test auto-delete after configured days

2. **Auto-Save Recording & Cleanup:**
   - Enable auto-save with 10-minute interval
   - Verify recordings are saved in `Echo/AutoSave/`
   - Verify cleanup runs daily
   - Verify old files are deleted correctly

3. **Manual Recordings:**
   - Verify they still go to `Echo/` (not subfolders)
   - Verify they are never auto-deleted

4. **Disk Usage:**
   - Test with limited disk space
   - Verify cleanup logs are correct

---

## Code Quality

- No breaking changes to public APIs
- Backward compatible with existing configurations
- Proper error handling and logging
- Clear comments and documentation
- Follows existing code style and patterns

---

## Future Enhancements

1. **UI Configuration:** Add settings UI for `AUTO_SAVE_AUTO_DELETE_DAYS_KEY`
2. **Fine-Tuning:** Allow users to enable/disable individual cleanup jobs
3. **Statistics:** Track cleanup metrics (files deleted, space freed)
4. **Migration:** Helper method to migrate old files to new folder structure
5. **Cleanup Optimization:** Run cleanup on-demand via intent instead of only daily

