# Echo File Storage - Visual Reference Guide

## Quick Reference: Where Each File Type Goes

```
Music/Echo/
│
├─ Echo - Wed, Jan 10 2:30 PM.wav          [MANUAL RECORDING - stays forever]
├─ Echo - Thu, Jan 11 10:45 AM.wav         [MANUAL RECORDING - stays forever]
│
├─ VAD/                                     [Voice Activity Detection folder]
│  ├─ vad_20260110_143025.wav               [VAD auto-detected & auto-deleted in 7 days]
│  └─ vad_20260112_085500.wav               [VAD auto-detected & auto-deleted in 7 days]
│
├─ AutoSave/                                [Auto-Save recordings folder]
│  ├─ Echo - Fri, Jan 12 2:30 PM.wav       [Auto-saved & auto-deleted in 7 days]
│  └─ Echo - Sat, Jan 13 10:45 AM.wav      [Auto-saved & auto-deleted in 7 days]
│
└─ EchoBuffer/                              [Disk buffer only - circular buffer]
   ├─ buffer_1.raw                          [Auto-deleted when 500MB limit exceeded]
   └─ buffer_2.raw                          [Auto-deleted when 500MB limit exceeded]
```

---

## File Organization Clarification

### ✅ MANUAL RECORDINGS
**Folder:** Root `Echo/` folder  
**Created By:** User clicking "Start Recording" button or via intent  
**Filename:** `Echo - Wed, Jan 10 2:30 PM.wav`  
**Auto-Delete:** ❌ NO - These stay forever  
**Example:** `Music/Echo/Echo - Mon, Jan 13 3:45 PM.wav`

---

### 🎤 VAD RECORDINGS (Voice Activity Detection)
**Folder:** `Echo/VAD/` subfolder  
**Created By:** Automatic audio activity detection (if enabled)  
**Filename:** `vad_yyyyMMdd_HHmmss.wav` (e.g., `vad_20260110_143025.wav`)  
**Auto-Delete:** ✅ YES - After 7 days (configurable)  
**Respects Flags:** Yes - Flagged recordings won't be deleted  
**Database:** Tracked in `ActivityRecordingDatabase`  
**Example:** `Music/Echo/VAD/vad_20260110_143025.wav`

---

### ⏱️ AUTO-SAVE RECORDINGS
**Folder:** `Echo/AutoSave/` subfolder  
**Created By:** Automatic timer (every 10 minutes by default)  
**Filename:** `Echo - [Date Time].wav` (same format as manual recordings)  
**Auto-Delete:** ✅ YES - After 7 days (configurable)  
**Cleanup Frequency:** Daily (once per day)  
**Respects Flags:** No - All files auto-deleted when old  
**Example:** `Music/Echo/AutoSave/Echo - Fri, Jan 12 2:30 PM.wav`

---

### 💾 DISK BUFFER (BATCH_TO_DISK mode only)
**Folder:** `Echo/EchoBuffer/` subfolder  
**Created By:** Storage mode set to BATCH_TO_DISK  
**Filename:** `buffer_*.raw` (binary format)  
**Auto-Delete:** ✅ YES - When total disk usage exceeds 500MB  
**Management:** Automatic circular buffer  
**Example:** `Music/Echo/EchoBuffer/buffer_1.raw`

---

## Auto-Delete Comparison

| Feature | VAD | Auto-Save | Manual | Disk Buffer |
|---------|-----|-----------|--------|------------|
| **Folder** | `Echo/VAD/` | `Echo/AutoSave/` | `Echo/` | `Echo/EchoBuffer/` |
| **Auto-Delete** | ✅ Yes | ✅ Yes | ❌ No | ✅ Yes |
| **Default Period** | 7 days | 7 days | Never | 500 MB limit |
| **Config Key** | `ACTIVITY_AUTO_DELETE_DAYS_KEY` | `AUTO_SAVE_AUTO_DELETE_DAYS_KEY` | N/A | `MAX_DISK_USAGE_MB_KEY` |
| **Respects Flags** | ✅ Yes | ❌ No | N/A | N/A |
| **Created By** | Audio activity detected | Periodic timer | User action | Storage mode |

---

## What Gets Automatically Deleted?

### DELETED Automatically (7-day default):
- ✅ VAD recordings older than 7 days (unless flagged)
- ✅ Auto-save files older than 7 days
- ✅ Disk buffer files when 500 MB limit exceeded

### NOT DELETED Automatically:
- ❌ Manual recordings (stay forever)
- ❌ Flagged VAD recordings (protected)
- ❌ Active recording files (never deleted while in use)

---

## Configuration Examples

### Change VAD Auto-Delete to 14 Days
```java
SharedPreferences prefs = context.getSharedPreferences("eu.mrogalski.saidit", MODE_PRIVATE);
prefs.edit().putInt("activity_auto_delete_days", 14).apply();
```

### Change Auto-Save Auto-Delete to 30 Days
```java
SharedPreferences prefs = context.getSharedPreferences("eu.mrogalski.saidit", MODE_PRIVATE);
prefs.edit().putInt("auto_save_auto_delete_days", 30).apply();
```

### Disable Auto-Save (never saves automatically)
```java
SharedPreferences prefs = context.getSharedPreferences("eu.mrogalski.saidit", MODE_PRIVATE);
prefs.edit().putBoolean("auto_save_enabled", false).apply();
```

---

## Cleanup Timeline Example

Assuming today is **January 20, 2026** with default 7-day retention:

### VAD Files
- `vad_20260110_143025.wav` - Created: Jan 10 → **DELETE on Jan 17** ✓
- `vad_20260115_090000.wav` - Created: Jan 15 → **DELETE on Jan 22** (future)
- `vad_20260120_140000.wav` (flagged) - Created: Jan 20 → **KEEP** (flagged)

### Auto-Save Files
- `Echo - Mon, Jan 13 2:30 PM.wav` - Created: Jan 13 → **DELETE on Jan 20** ✓
- `Echo - Fri, Jan 17 10:45 AM.wav` - Created: Jan 17 → **DELETE on Jan 24** (future)

### Manual Recordings
- `Echo - Wed, Jan 1 4:00 PM.wav` - Created: Jan 1 → **KEEP FOREVER** (no auto-delete)

---

## Folder Structure Comparison

### Before This Update
```
Echo/
├── Echo - ...wav (manual recordings)
├── Activities/ (VAD with "activity_" prefix)
│   └── activity_*.wav
└── (auto-save mixed with manual recordings)
```

### After This Update ✅
```
Echo/
├── Echo - ...wav (manual recordings ONLY)
├── VAD/ (clear separation)
│   └── vad_*.wav
├── AutoSave/ (clear separation)
│   └── Echo - ...wav
└── EchoBuffer/ (clear separation)
    └── buffer_*.raw
```

---

## Key Benefits

1. **Crystal Clear Organization** - Know exactly which folder each type goes to
2. **Independent Auto-Delete Policies** - VAD and Auto-Save have separate retention periods
3. **No Accidental Loss** - Manual recordings never auto-deleted
4. **Respects Importance** - Flagged VAD recordings protected from auto-delete
5. **Better Disk Management** - Separate cleanup timers for different file types

---

## Common Questions Answered

**Q: Will my old recordings in `Echo/` folder be deleted?**  
A: No. Only files in `Echo/AutoSave/` subfolder are auto-deleted (after 7 days). Files directly in `Echo/` stay forever.

**Q: If I flag a VAD recording, will it stay forever?**  
A: Yes. Flagged VAD recordings are exempt from auto-delete.

**Q: Can I change how long auto-save files stay?**  
A: Yes. Modify `AUTO_SAVE_AUTO_DELETE_DAYS_KEY` in preferences (default: 7 days).

**Q: What happens if I disable auto-save?**  
A: Auto-save stops recording new files, but existing auto-save files will still be auto-deleted based on their age.

**Q: Are VAD and auto-save files stored in the same folder?**  
A: No. VAD files go to `Echo/VAD/`, auto-save files go to `Echo/AutoSave/`. They're completely separate.

**Q: Do manual recordings ever get deleted?**  
A: No. Manual recordings in the main `Echo/` folder are never automatically deleted.

---

## Implementation Details

### Service Methods for Cleanup

**VAD Cleanup:**
- Handled by: `ActivityRecordingDatabase` (checks deletion deadline)
- Triggered by: App startup or periodic checks
- Configuration: `ACTIVITY_AUTO_DELETE_DAYS_KEY`

**Auto-Save Cleanup:**
- Handled by: `SaidItService.handleAutoSaveCleanup()`
- Triggered by: AlarmManager (daily)
- Configuration: `AUTO_SAVE_AUTO_DELETE_DAYS_KEY`

**Disk Buffer Cleanup:**
- Handled by: `DiskAudioBuffer.cleanupOldFiles()`
- Triggered by: When disk usage exceeds limit
- Configuration: `MAX_DISK_USAGE_MB_KEY`

---

## File Retention Summary

| File Type | Location | Retention | Auto-Delete Method |
|-----------|----------|-----------|-------------------|
| Manual Recording | `Echo/` | Forever | Manual delete only |
| VAD Recording | `Echo/VAD/` | 7 days (configurable) | Database deadline check |
| Auto-Save | `Echo/AutoSave/` | 7 days (configurable) | Daily AlarmManager |
| Disk Buffer | `Echo/EchoBuffer/` | 500 MB limit (configurable) | Circular buffer cleanup |

