# Echo Storage Organization

This document clarifies the storage structure for Echo recordings and explains the auto-delete behavior for different file types.

---

## Storage Structure

### Main Directory: `Music/Echo/` (External) or `data/files/Echo/` (Internal)

The Echo app stores files in the following folder structure:

```
Echo/                          # Main folder
├── [Manual Recordings]         # .wav files from manual recording (UI start/stop)
├── VAD/                        # Voice Activity Detection recordings (auto-detected)
│   └── vad_yyyyMMdd_HHmmss.wav
├── AutoSave/                   # Auto-save recordings (periodic dumps)
│   └── Echo - [Date Time].wav
└── EchoBuffer/                 # Disk buffer (BATCH_TO_DISK storage mode only)
    └── buffer_*.raw
```

---

## File Types & Auto-Delete Behavior

### 1. Manual Recordings (Direct in `Echo/` folder)

**Location:** `Music/Echo/Echo - [Day, Date Time].wav`

**When Created:** When user manually starts/stops recording from the UI or via intent broadcasts

**Filename Format:** `Echo - Wed, Jan 10 2:30 PM.wav`

**Auto-Delete:** ❌ **NO** - Never automatically deleted

**Examples:**
- `Echo - Wed, Jan 10 2:30 PM.wav`
- `Echo - Thu, Jan 11 10:45 AM.wav`

---

### 2. Voice Activity Detection (VAD) Recordings (in `Echo/VAD/` subfolder)

**Location:** `Music/Echo/VAD/vad_yyyyMMdd_HHmmss.wav`

**When Created:** Automatically when audio activity is detected (if VAD is enabled)

**Filename Format:** `vad_yyyyMMdd_HHmmss.wav` (e.g., `vad_20260110_143025.wav`)

**Auto-Delete:** ✅ **YES** - Automatically deleted after configured days (default: 7 days)

**Configuration:**
- Setting: `ACTIVITY_AUTO_DELETE_DAYS_KEY`
- Default: 7 days
- Respects flagged recordings (flagged files are never auto-deleted)

**Stored Metadata:** `ActivityRecordingDatabase` tracks:
- Recording ID
- Timestamp (when recording started)
- Duration (in seconds)
- File path
- Flagged status (prevents auto-deletion)
- Deletion deadline (Unix timestamp)

**Examples:**
- `vad_20260110_143025.wav` (created on Jan 10, 2026 at 2:30:25 PM)
- `vad_20260112_085500.wav` (created on Jan 12, 2026 at 8:55:00 AM)

---

### 3. Auto-Save Recordings (in `Echo/AutoSave/` subfolder)

**Location:** `Music/Echo/AutoSave/Echo - [Day, Date Time].wav`

**When Created:** Automatically at configured intervals (default: every 10 minutes)

**Filename Format:** `Echo - [Day, Date Time].wav` (same format as manual recordings)

**Auto-Delete:** ✅ **YES** - Automatically deleted after configured days (default: 7 days)

**Configuration:**
- Setting: `AUTO_SAVE_AUTO_DELETE_DAYS_KEY`
- Default: 7 days
- Cleanup frequency: Daily (runs once per day)

**How Auto-Delete Works:**
1. **Scheduled Cleanup:** Runs once daily via AlarmManager
2. **File Age Check:** Compares file's last modified timestamp against deletion threshold
3. **Automatic Deletion:** Files older than the configured days are deleted
4. **Cleanup Logging:** Reports number of files deleted and bytes freed

**Examples:**
- `Echo - Wed, Jan 10 2:30 PM.wav`
- `Echo - Thu, Jan 11 10:45 AM.wav`

---

### 4. Disk Buffer Files (in `Echo/EchoBuffer/` subfolder)

**Location:** `Music/Echo/EchoBuffer/buffer_*.raw`

**When Created:** Only when using BATCH_TO_DISK storage mode

**Filename Format:** `buffer_1.raw`, `buffer_2.raw`, etc.

**Auto-Delete:** ✅ **YES** - Automatically deleted when disk usage exceeds limit (default: 500 MB)

**Configuration:**
- Setting: `MAX_DISK_USAGE_MB_KEY`
- Default: 500 MB
- Cleanup: Automatic when limit is exceeded (oldest files deleted first)

**Note:** This is NOT related to VAD or Auto-save - it's a circular buffer for the disk storage mode.

---

## Auto-Delete Summary Table

| File Type | Location | Auto-Delete | Default Period | Respects Flags |
|-----------|----------|-------------|-----------------|---|
| Manual Recording | `Echo/` | ❌ No | N/A | N/A |
| VAD Recording | `Echo/VAD/` | ✅ Yes | 7 days | Yes |
| Auto-Save | `Echo/AutoSave/` | ✅ Yes | 7 days | No |
| Disk Buffer | `Echo/EchoBuffer/` | ✅ Yes | 500 MB limit | No |

---

## Key Points

### Clear Separation

- **Manual recordings** stay in the main `Echo/` folder
- **VAD recordings** are completely separate in `Echo/VAD/` subfolder
- **Auto-save recordings** are separate in `Echo/AutoSave/` subfolder
- **Disk buffer** is in its own `Echo/EchoBuffer/` subfolder

### Auto-Delete Behavior

- **VAD auto-delete:** Controlled by `ACTIVITY_AUTO_DELETE_DAYS_KEY` (7 days default)
- **Auto-save auto-delete:** Controlled by `AUTO_SAVE_AUTO_DELETE_DAYS_KEY` (7 days default)
- **Manual recordings:** NEVER auto-deleted
- **Disk buffer:** Auto-managed by disk limit (500 MB default)

### How to Configure

**VAD Auto-Delete Days:**
```java
preferences.edit().putInt(ACTIVITY_AUTO_DELETE_DAYS_KEY, 14).apply(); // 14 days
```

**Auto-Save Auto-Delete Days:**
```java
preferences.edit().putInt(AUTO_SAVE_AUTO_DELETE_DAYS_KEY, 30).apply(); // 30 days
```

---

## External vs. Internal Storage

The app automatically chooses storage based on availability:

**External Storage** (Primary choice):
- `Music/Echo/` (or `Music/Echo/VAD/`, etc.)
- Used when external storage is writable

**Internal Storage** (Fallback):
- `data/files/Echo/` (or `data/files/Echo/VAD/`, etc.)
- Used when external storage is not available

The folder structure is identical in both locations.

---

## Implementation Details

### VAD Recordings

- **Service Field:** `activityWavFileWriter` (WavFileWriter)
- **Storage Dir:** `Echo/VAD` (new in this update)
- **Filename Prefix:** `vad_` (changed from `activity_`)
- **Database:** `ActivityRecordingDatabase` tracks all recordings
- **Cleanup:** Via database query for expired recordings

### Auto-Save Recordings

- **Service Method:** `dumpRecording()` (called by auto-save timer)
- **Storage Dir:** `Echo/AutoSave` (new in this update)
- **Filename Format:** `Echo - [timestamp].wav` (same as manual recordings)
- **Cleanup Method:** `handleAutoSaveCleanup()` (new in this update)
- **Cleanup Trigger:** Daily via AlarmManager

### Cleanup Scheduling

Both cleanup operations are scheduled when service starts listening:
```java
scheduleAutoSaveCleanup();  // Scheduled daily cleanup
```

And canceled when service stops listening:
```java
cancelAutoSaveCleanup();    // Cancel cleanup timer
```

---

## Version History

- **v1.0 (This Update):**
  - Separated VAD files into `Echo/VAD/` subfolder
  - Separated auto-save files into `Echo/AutoSave/` subfolder
  - Implemented auto-delete for auto-save files
  - Clarified naming and documentation
  - Changed VAD filename prefix from `activity_` to `vad_`
