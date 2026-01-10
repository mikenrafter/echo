# Echo Intent API Documentation

This document describes the intent-based automation API for the Echo audio recording app. These intents allow external applications (like Tasker, Automate, or custom apps) to control Echo programmatically.

## Table of Contents

1. [Overview](#overview)
2. [Intent Actions](#intent-actions)
3. [Intent Extras](#intent-extras)
4. [Usage Examples](#usage-examples)
5. [Tasker Integration](#tasker-integration)
6. [Security Considerations](#security-considerations)

## Overview

Echo exposes several intent actions that can be sent to control various aspects of the audio recording functionality. All intents should be sent as broadcasts to the `BroadcastReceiver` component.

**Package Name:** `eu.mrogalski.saidit`

## Intent Actions

### Recording Control

#### START_RECORDING
Starts a new recording session. If already listening, begins writing to a file with optional prepended audio from memory.

**Action:** `eu.mrogalski.saidit.action.START_RECORDING`

**Extras:**
- `prepend_seconds` (float, optional): Number of seconds to prepend from memory buffer. Default: 60.0

**Example:**
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.START_RECORDING --ef prepend_seconds 120.0
```

#### STOP_RECORDING
Stops the current recording session and saves the file.

**Action:** `eu.mrogalski.saidit.action.STOP_RECORDING`

**Extras:**
- `filename` (String, optional): Custom filename (without extension). If not provided, timestamp is used.

**Example:**
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.STOP_RECORDING --es filename "MyRecording"
```

#### DUMP_RECORDING
Saves audio from memory buffer to a file without starting continuous recording.

**Action:** `eu.mrogalski.saidit.action.DUMP_RECORDING`

**Extras:**
- `prepend_seconds` (float, optional): Number of seconds to save from buffer. Default: 300.0 (5 minutes)
- `filename` (String, optional): Custom filename (without extension)

**Example:**
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.DUMP_RECORDING --ef prepend_seconds 60.0 --es filename "QuickDump"
```

#### DUMP_RECORDING_RANGE
Saves a specific time range from the memory/disk buffer to a file without starting continuous recording.

Use this to export a slice of recent audio, e.g., from 10 minutes ago to now, or between two points in the past.

**Action:** `eu.mrogalski.saidit.action.DUMP_RECORDING_RANGE`

**Extras:**
- `from_seconds_ago` (float, optional): Start of the range in seconds ago. Default: 300.0
- `to_seconds_ago` (float, optional): End of the range in seconds ago. Default: 0.0 (now)
- `filename` (String, optional): Custom filename (without extension)

Values are clamped to 0–3600 seconds. If `from_seconds_ago` is less than `to_seconds_ago`, they will be swapped.

**Examples:**
```bash
# Export from 10 minutes ago up to now
adb shell am broadcast -a eu.mrogalski.saidit.action.DUMP_RECORDING_RANGE --ef from_seconds_ago 600.0 --ef to_seconds_ago 0.0 --es filename "TenMinToNow"

# Export a slice from 25 min ago to 20 min ago
adb shell am broadcast -a eu.mrogalski.saidit.action.DUMP_RECORDING_RANGE --ef from_seconds_ago 1500.0 --ef to_seconds_ago 1200.0 --es filename "Slice_25_to_20"
```

### Listening Control

#### ENABLE_LISTENING
Enables audio memory buffer (starts listening and buffering audio in memory/disk).

**Action:** `eu.mrogalski.saidit.action.ENABLE_LISTENING`

**Example:**
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.ENABLE_LISTENING
```

#### DISABLE_LISTENING
Disables audio memory buffer (stops listening and frees memory).

**Action:** `eu.mrogalski.saidit.action.DISABLE_LISTENING`

**Example:**
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.DISABLE_LISTENING
```

### Storage Mode Control

#### SET_MEMORY_MODE
Sets storage mode to Memory Only (audio stored only in RAM).

**Action:** `eu.mrogalski.saidit.action.SET_MEMORY_MODE`

**Example:**
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.SET_MEMORY_MODE
```

#### SET_DISK_MODE
Sets storage mode to Batch to Disk (audio automatically written to disk in batches).

**Action:** `eu.mrogalski.saidit.action.SET_DISK_MODE`

**Example:**
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.SET_DISK_MODE
```

### Configuration

#### SET_MEMORY_SIZE
Sets the memory buffer size.

**Action:** `eu.mrogalski.saidit.action.SET_MEMORY_SIZE`

**Extras:**
- `memory_size_mb` (int, required): Memory size in megabytes (minimum: 10 MB)

**Example:**
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.SET_MEMORY_SIZE --ei memory_size_mb 200
```

## Intent Extras

### Extra Keys

| Extra Key | Type | Description |
|-----------|------|-------------|
| `prepend_seconds` | float | Number of seconds to prepend/save from buffer |
| `filename` | String | Custom filename for recordings (without .wav extension) |
| `memory_size_mb` | int | Memory buffer size in megabytes |
| `from_seconds_ago` | float | Start of export range (seconds ago) |
| `to_seconds_ago` | float | End of export range (seconds ago) |

### Extra Type Flags (for adb)

- `--es` : String extra
- `--ei` : Integer extra
- `--ef` : Float extra
- `--ez` : Boolean extra

## Usage Examples

### Example 1: Quick Recording with 2 Minutes of History

Start recording with 2 minutes of prepended audio:
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.START_RECORDING --ef prepend_seconds 120.0
```

Wait for some time, then stop:
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.STOP_RECORDING --es filename "Meeting_2024"
```

### Example 2: Automatic Night Mode

Enable listening before sleep:
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.ENABLE_LISTENING
adb shell am broadcast -a eu.mrogalski.saidit.action.SET_DISK_MODE
```

Disable in the morning:
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.DISABLE_LISTENING
```

### Example 3: Capture Last 30 Seconds

Quickly save the last 30 seconds without starting continuous recording:
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.DUMP_RECORDING --ef prepend_seconds 30.0 --es filename "Important_Moment"
```

### Example 4: Set Memory Size

Configure 500 MB of memory buffer:
```bash
adb shell am broadcast -a eu.mrogalski.saidit.action.SET_MEMORY_SIZE --ei memory_size_mb 500
```

## Tasker Integration

### Profile Example: Location-Based Recording

**Profile:** Location → Work
- **Task:** Start Echo Listening
  - Action: Send Intent
    - Action: `eu.mrogalski.saidit.action.ENABLE_LISTENING`
    - Target: Broadcast Receiver

**Exit Task:** Stop Echo Listening
- Action: Send Intent
  - Action: `eu.mrogalski.saidit.action.DISABLE_LISTENING`
  - Target: Broadcast Receiver

### Task Example: Quick Meeting Recording

**Task:** Record Meeting
1. Send Intent
   - Action: `eu.mrogalski.saidit.action.START_RECORDING`
   - Extra: `prepend_seconds:300.0` (float)
   - Target: Broadcast Receiver

2. Wait (Variable Time)

3. Send Intent
   - Action: `eu.mrogalski.saidit.action.STOP_RECORDING`
   - Extra: `filename:Meeting_%DATE` (string)
   - Target: Broadcast Receiver

### Profile Example: Automatic Disk Mode at Night

**Profile:** Time → 10:00 PM to 7:00 AM
- **Entry Task:**
  - Action: Send Intent → `eu.mrogalski.saidit.action.SET_DISK_MODE`
  - Action: Send Intent → `eu.mrogalski.saidit.action.ENABLE_LISTENING`

- **Exit Task:**
  - Action: Send Intent → `eu.mrogalski.saidit.action.SET_MEMORY_MODE`

## Automate (by LlamaLab) Integration

### Flow Example: Voice-Activated Recording

1. **Trigger:** Voice Command "Start Recording"
2. **Action:** Broadcast send
   - Package: `eu.mrogalski.saidit`
   - Receiver class: `eu.mrogalski.saidit.BroadcastReceiver`
   - Action: `eu.mrogalski.saidit.action.START_RECORDING`
   - Extra: `prepend_seconds` = 60.0 (Number)

3. **Trigger:** Voice Command "Stop Recording"
4. **Action:** Broadcast send
   - Action: `eu.mrogalski.saidit.action.STOP_RECORDING`

## Security Considerations

### Permissions

The BroadcastReceiver is exported (`android:exported="true"`) to allow external apps to send intents. Consider the following security implications:

1. **Any app can control Echo** - Malicious apps could start/stop recordings without user knowledge
2. **No authentication** - Intents are processed without verification of sender
3. **Background execution** - Commands execute even when Echo UI is not visible

### Recommendations

1. **User awareness**: Users should be aware that installing Echo enables automation
2. **Monitor app usage**: Check which apps are installed and their permissions
3. **Logs**: Echo logs all intent actions for debugging (check logcat with tag "EchoBroadcastReceiver")

### Future Enhancements

Potential security improvements for future versions:
- Optional authentication token requirement
- Whitelist of allowed calling apps
- User confirmation prompts for sensitive actions
- Permission-based intent filtering

## Troubleshooting

### Intent Not Working

1. **Check service is running:**
   ```bash
   adb shell dumpsys activity services | grep SaidItService
   ```

2. **Check logcat for errors:**
   ```bash
   adb logcat -s EchoBroadcastReceiver:* SaidItService:*
   ```

3. **Verify intent format:**
   - Ensure action string is exact (case-sensitive)
   - Ensure extras have correct types
   - Use correct package name

### Common Issues

**"Service not found"**
- Ensure Echo app is installed
- Try starting Echo manually first
- Check if service has permission to run in background

**"Command executed but nothing happens"**
- Check if Echo has necessary permissions (microphone, storage)
- Verify app is not in battery optimization
- Check if listening is enabled before starting recording

**"File not created"**
- Ensure storage permissions are granted
- Check available disk space
- Verify output directory exists and is writable

## Support

For bugs or feature requests related to the Intent API:
- GitHub Issues: https://github.com/mikenrafter/echo/issues
- Include logcat output with tag filters: `EchoBroadcastReceiver:* SaidItService:*`

## Version History

- **v2.1.0** - Initial Intent API release
  - Added recording control intents
  - Added listening control intents
  - Added storage mode control intents
  - Added configuration intents

## License

This API documentation is part of the Echo project and is licensed under the same terms as the main application.
