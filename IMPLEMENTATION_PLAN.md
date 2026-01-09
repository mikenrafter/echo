# Echo Enhancement Implementation Plan

## Overview
This document outlines the implementation plan for enhancing Echo with advanced audio recording capabilities, dual-channel recording, flexible memory management, multi-quality encoding, and audio activity detection features.

## Feature List

### 1. Dual-Channel Audio Capture
**Description:** Capture both device audio (internal audio) and microphone audio simultaneously, placing them in separate stereo channels (L/R for mic, L/R for device).

**Implementation Details:**
- Modify `SaidItService` to support dual audio sources
- Change from `CHANNEL_IN_MONO` to `CHANNEL_IN_STEREO` or use multi-channel configuration
- Use `MediaRecorder.AudioSource.MIC` for microphone
- Use `MediaRecorder.AudioSource.REMOTE_SUBMIX` or `MediaRecorder.AudioSource.DEFAULT` for device audio (Note: Android has limitations on capturing system audio)
- Create separate AudioRecord instances for each source if needed
- Interleave samples to place mic in L/R and device audio in separate L/R
- Update `AudioMemory` to handle multi-channel data
- Add configuration UI to select audio sources

**Files to Modify:**
- `SaidItService.java` - Audio recording logic
- `AudioMemory.java` - Buffer management for multi-channel
- `SaidItFragment.java` - UI for audio source selection
- `SettingsActivity.java` - Settings for audio source configuration
- `strings.xml` - New UI strings
- `AndroidManifest.xml` - Add CAPTURE_AUDIO_OUTPUT permission if needed

**Challenges:**
- Android restricts system audio capture (requires Android 10+ and specific conditions)
- May need to use MediaProjection API for internal audio
- Alternative: Use AudioPlaybackCapture API (Android 10+)

### 2. Configurable Memory Management
**Description:** Allow users to specify exact memory usage instead of vague presets.

**Implementation Details:**
- Add settings UI with memory size input (in MB)
- Remove or augment existing preset buttons
- Add validation (min/max limits based on device capabilities)
- Store exact memory size in SharedPreferences
- Update `AudioMemory.allocate()` to use exact size

**Files to Modify:**
- `SettingsActivity.java` - Add memory configuration UI
- `SaidItFragment.java` - Update memory display and controls
- `SaidIt.java` - Add new preference keys
- `layout/activity_settings.xml` - New settings UI
- `strings.xml` - New UI strings

### 3. Multi-Quality Encoding
**Description:** Recent audio (configurable duration) at high bitrate, older audio at lower bitrate.

**Implementation Details:**
- Implement dual-buffer system in `AudioMemory`:
  - High-quality buffer for recent audio (configurable time window)
  - Lower-quality buffer for older audio
- Add encoding quality settings:
  - High bitrate setting (e.g., 256kbps)
  - Low bitrate setting (e.g., 64kbps)
  - Time window for high-quality audio (e.g., 5 minutes)
- Implement audio re-encoding/compression for older audio
- Use Android's AudioFormat and MediaCodec for encoding

**Files to Modify:**
- `AudioMemory.java` - Dual-buffer implementation
- `SaidItService.java` - Encoding logic
- `SettingsActivity.java` - Quality settings UI
- `SaidIt.java` - New preference keys for bitrates and time window
- Create new `AudioEncoder.java` - Encoding utilities

**Challenges:**
- Real-time encoding may impact performance
- Need to handle buffer transitions smoothly

### 4. Storage Modes
**Description:** Two modes - memory-only and batch-to-disk modes with easy switching via intent.

**Implementation Details:**

#### Mode 1: Memory-Only Mode (Current behavior, enhanced)
- Keep all audio in RAM circular buffer
- No automatic disk writes except manual saves

#### Mode 2: Batch-to-Disk Mode
- Automatically write audio chunks to disk when buffer fills
- Maintain circular file buffer on disk
- Reference implementations: Alibi and Circular-Recorder apps
- Add as git submodules for reference

**Implementation Details:**
- Add storage mode preference
- Create `DiskAudioBuffer.java` for disk-based circular buffer
- Implement batch writing logic
- Add cleanup for old files
- Create intent receiver for mode switching
- Add UI toggle for mode selection

**Files to Create:**
- `DiskAudioBuffer.java` - Disk-based circular buffer
- `StorageMode.java` - Enum for modes

**Files to Modify:**
- `SaidItService.java` - Mode switching logic
- `BroadcastReceiver.java` - Intent handling for automation
- `SaidItFragment.java` - Mode UI
- `AndroidManifest.xml` - Intent filter for mode switching
- `strings.xml` - Mode descriptions

**Git Submodules to Add:**
```bash
git submodule add <alibi-repo-url> reference/alibi
git submodule add <circular-recorder-repo-url> reference/circular-recorder
```

### 5. Audio Activity Detection Mode
**Description:** Save 5 minutes before and after detected audio activity, with higher bitrate. Auto-delete after configurable time unless flagged.

**Implementation Details:**

#### Activity Detection
- Implement Voice Activity Detection (VAD)
- Use amplitude threshold or more sophisticated detection
- Create `VoiceActivityDetector.java` class
- Monitor audio stream continuously

#### Buffer Management
- Maintain sliding 5-minute pre-activity buffer
- Start recording on activity detection
- Continue for 5 minutes after activity stops
- Use high bitrate for these recordings

#### File Management
- Save recordings to disk with timestamps
- Implement auto-deletion after configurable period (e.g., 7 days)
- Add "flag" functionality to prevent deletion
- Create database or metadata file to track:
  - Recording timestamp
  - Activity duration
  - Flagged status
  - Deletion deadline

#### UI Components
- Mode activation toggle
- Activity detection sensitivity settings
- Auto-delete time period setting
- List of saved activity recordings
- Flag/unflag buttons

**Files to Create:**
- `VoiceActivityDetector.java` - VAD implementation
- `ActivityRecording.java` - Recording metadata model
- `ActivityRecordingDatabase.java` - SQLite database for recordings
- `ActivityRecordingListActivity.java` - UI to view/manage recordings

**Files to Modify:**
- `SaidItService.java` - Activity detection mode
- `AudioMemory.java` - Pre-activity buffer
- `SaidItFragment.java` - Mode UI
- `SettingsActivity.java` - VAD settings
- `AndroidManifest.xml` - New activity
- `strings.xml` - New UI strings

### 6. Intent-Based Automation
**Description:** Allow external apps to control recording modes and actions via intents.

**Implementation Details:**

#### Intent Actions
```java
// Start/stop recording
eu.mrogalski.saidit.action.START_RECORDING
eu.mrogalski.saidit.action.STOP_RECORDING

// Switch modes
eu.mrogalski.saidit.action.SET_MEMORY_MODE
eu.mrogalski.saidit.action.SET_DISK_MODE
eu.mrogalski.saidit.action.SET_ACTIVITY_MODE

// Enable/disable listening
eu.mrogalski.saidit.action.ENABLE_LISTENING
eu.mrogalski.saidit.action.DISABLE_LISTENING

// Configuration
eu.mrogalski.saidit.action.SET_MEMORY_SIZE (extra: size_mb)
eu.mrogalski.saidit.action.SET_BITRATE (extra: bitrate_kbps)
```

**Files to Modify:**
- `BroadcastReceiver.java` - Handle new intents
- `SaidItService.java` - Intent processing
- `AndroidManifest.xml` - Intent filters

**Documentation:**
- Create `INTENT_API.md` documenting all intents and extras

## Implementation Order and TODO List

### Phase 1: Foundation and Documentation (Current)
- [x] Create IMPLEMENTATION_PLAN.md
- [ ] Create TODO.md with detailed task breakdown
- [ ] Add reference implementations as git submodules
- [ ] Review Alibi and Circular-Recorder code

### Phase 2: Configurable Memory Management
- [ ] Add memory size input UI in SettingsActivity
- [ ] Update memory allocation logic
- [ ] Add validation and error handling
- [ ] Test with various memory sizes

### Phase 3: Storage Modes
- [ ] Implement DiskAudioBuffer class
- [ ] Add mode selection UI
- [ ] Implement batch-to-disk writing
- [ ] Add disk space management
- [ ] Test mode switching

### Phase 4: Multi-Quality Encoding
- [ ] Implement dual-buffer in AudioMemory
- [ ] Add AudioEncoder utility class
- [ ] Implement re-encoding for older audio
- [ ] Add quality settings UI
- [ ] Performance testing

### Phase 5: Dual-Channel Audio
- [ ] Research Android audio capture limitations
- [ ] Implement multi-source recording
- [ ] Update AudioMemory for multi-channel
- [ ] Add audio source selection UI
- [ ] Test with different audio sources

### Phase 6: Audio Activity Detection
- [ ] Implement VoiceActivityDetector
- [ ] Create recording database
- [ ] Implement auto-save on activity
- [ ] Add recording management UI
- [ ] Implement auto-deletion with flag protection
- [ ] Add activity detection settings

### Phase 7: Intent Automation
- [ ] Define intent API
- [ ] Implement intent handlers
- [ ] Add intent filters to manifest
- [ ] Create INTENT_API.md documentation
- [ ] Test with automation apps (Tasker, etc.)

### Phase 8: Testing and Polish
- [ ] End-to-end testing of all features
- [ ] Performance optimization
- [ ] Battery usage optimization
- [ ] Update app documentation
- [ ] Update user-facing strings
- [ ] Test on multiple Android versions

## Technical Considerations

### Android Permissions
- `RECORD_AUDIO` - Already present
- `FOREGROUND_SERVICE` - Already present
- `FOREGROUND_SERVICE_MICROPHONE` - Already present
- `CAPTURE_AUDIO_OUTPUT` - May be needed for system audio
- `WRITE_EXTERNAL_STORAGE` - For disk mode
- `READ_EXTERNAL_STORAGE` - For disk mode
- May need `BIND_ACCESSIBILITY_SERVICE` for some audio capture scenarios

### Performance Considerations
- Real-time encoding impact on battery
- Memory usage with dual-buffer system
- Disk I/O performance for batch mode
- VAD computational overhead

### Compatibility
- Target Android 10+ for AudioPlaybackCapture
- Fallback for devices without system audio capture
- Handle various screen sizes and orientations

### Battery Optimization
- Use efficient VAD algorithms
- Batch disk writes to reduce wake locks
- Optimize buffer sizes for performance

### User Privacy
- Clear documentation about what audio is captured
- User control over all recording features
- Secure deletion of recordings
- No network transmission without explicit user action

## Testing Strategy

### Unit Tests
- AudioMemory buffer management
- VoiceActivityDetector accuracy
- AudioEncoder quality verification
- DiskAudioBuffer circular buffer logic

### Integration Tests
- Mode switching
- Intent handling
- Service lifecycle
- Database operations

### Manual Tests
- Audio quality verification
- Battery life testing
- Memory leak detection
- UI/UX testing
- Cross-device testing

## References
- Alibi app (to be added as submodule)
- Circular-Recorder app (to be added as submodule)
- Android AudioPlaybackCapture API documentation
- Android MediaCodec documentation
- Voice Activity Detection algorithms

## Notes
- This is a complex enhancement requiring significant development time
- Some features (especially system audio capture) have Android OS limitations
- Performance and battery impact must be carefully monitored
- User privacy and security are paramount
