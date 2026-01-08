# Echo Enhancement TODO List

This document tracks the detailed implementation tasks for enhancing Echo with advanced audio recording features.

## Current Status
- [x] Documentation Phase
  - [x] Created IMPLEMENTATION_PLAN.md
  - [x] Created TODO.md

## Phase 1: Foundation and Reference Code

### Add Reference Implementations
- [ ] Research and find Alibi app repository URL
- [ ] Research and find Circular-Recorder app repository URL
- [ ] Add Alibi as git submodule to `reference/alibi`
- [ ] Add Circular-Recorder as git submodule to `reference/circular-recorder`
- [ ] Review Alibi's disk-based circular buffer implementation
- [ ] Review Circular-Recorder's batch writing approach
- [ ] Document key insights from reference implementations

## Phase 2: Configurable Memory Management

### Backend Implementation
- [ ] Add `MEMORY_SIZE_MB_KEY` constant to `SaidIt.java`
- [ ] Add `getMemorySizeMB()` method to `SaidItService.java`
- [ ] Add `setMemorySizeMB(int mb)` method to `SaidItService.java`
- [ ] Add memory size validation (min: 10MB, max: device-dependent)
- [ ] Update `AudioMemory.allocate()` to handle exact byte sizes
- [ ] Add error handling for memory allocation failures

### UI Implementation
- [ ] Add memory size EditText to `activity_settings.xml`
- [ ] Add memory unit selector (MB/GB) if needed
- [ ] Add "Apply" button for memory settings
- [ ] Add current memory usage display
- [ ] Add available memory display
- [ ] Show memory size in MB/GB in main UI
- [ ] Add string resources for new UI elements
- [ ] Implement input validation in settings UI
- [ ] Show toast/snackbar for successful memory size change

### Testing
- [ ] Test with minimum memory size (10MB)
- [ ] Test with maximum memory size
- [ ] Test with invalid input (negative, zero, too large)
- [ ] Test memory allocation on low-memory devices
- [ ] Verify memory size persists across app restarts

## Phase 3: Storage Modes

### Core Classes
- [ ] Create `StorageMode.java` enum (MEMORY_ONLY, BATCH_TO_DISK)
- [ ] Create `DiskAudioBuffer.java` class
  - [ ] Implement circular file buffer
  - [ ] Add `write(byte[] data)` method
  - [ ] Add `read()` method for playback
  - [ ] Add `cleanup()` method for old files
  - [ ] Add disk space checking
  - [ ] Add file rotation logic
- [ ] Add `STORAGE_MODE_KEY` constant to `SaidIt.java`
- [ ] Add `MAX_DISK_USAGE_MB_KEY` constant

### Service Integration
- [ ] Add storage mode field to `SaidItService.java`
- [ ] Add `setStorageMode(StorageMode mode)` method
- [ ] Modify audio recording loop to support disk mode
- [ ] Implement batch writing to disk
- [ ] Add disk space monitoring
- [ ] Add automatic cleanup when disk is full
- [ ] Handle mode switching during active recording
- [ ] Ensure proper resource cleanup

### UI Implementation
- [ ] Add storage mode toggle to main UI
- [ ] Add mode indicator (icon or text)
- [ ] Add disk usage display for batch mode
- [ ] Add settings for max disk usage
- [ ] Add file browser for saved batches
- [ ] Add string resources for storage modes
- [ ] Update help text to explain modes

### Intent Support
- [ ] Add intent action for SET_MEMORY_MODE
- [ ] Add intent action for SET_DISK_MODE
- [ ] Update `BroadcastReceiver.java` to handle mode intents
- [ ] Add intent extras validation
- [ ] Test intent switching from external apps

### Testing
- [ ] Test memory-only mode (existing behavior)
- [ ] Test batch-to-disk mode with small files
- [ ] Test batch-to-disk mode with large files
- [ ] Test disk space handling when full
- [ ] Test mode switching during recording
- [ ] Test file cleanup
- [ ] Test across app restarts

## Phase 4: Multi-Quality Encoding

### Audio Encoding Infrastructure
- [ ] Create `AudioEncoder.java` utility class
  - [ ] Add method to encode PCM to desired bitrate
  - [ ] Support multiple codecs (AAC, MP3, Opus)
  - [ ] Add quality presets (LOW, MEDIUM, HIGH)
- [ ] Create `EncodingProfile.java` class
  - [ ] Fields: codec, bitrate, sample rate
- [ ] Add encoding quality constants to `SaidIt.java`

### Dual-Buffer System
- [ ] Design dual-buffer architecture
- [ ] Add high-quality buffer to `AudioMemory.java`
- [ ] Add low-quality buffer to `AudioMemory.java`
- [ ] Implement buffer transition logic
- [ ] Add `moveToLowQuality()` method
- [ ] Implement re-encoding on buffer transition
- [ ] Add configurable time window for high-quality

### Service Integration
- [ ] Add encoding parameters to `SaidItService.java`
- [ ] Integrate encoder with recording loop
- [ ] Add background encoding thread
- [ ] Handle encoding errors gracefully
- [ ] Monitor encoding performance
- [ ] Add encoding progress callbacks

### Settings UI
- [ ] Add high-quality bitrate setting
- [ ] Add low-quality bitrate setting
- [ ] Add high-quality time window setting
- [ ] Add codec selection (if multiple supported)
- [ ] Show estimated file sizes
- [ ] Add quality presets for quick selection
- [ ] Add string resources for encoding settings

### Testing
- [ ] Test encoding quality differences
- [ ] Test buffer transitions
- [ ] Verify audio quality with different bitrates
- [ ] Test encoding performance on various devices
- [ ] Test with long recording sessions
- [ ] Verify no audio loss during transitions

## Phase 5: Dual-Channel Audio Capture

### Research Phase
- [ ] Research Android audio capture limitations
- [ ] Document AudioPlaybackCapture API requirements
- [ ] Research MediaProjection for system audio
- [ ] Test AudioPlaybackCapture on target devices
- [ ] Determine fallback strategy for unsupported devices
- [ ] Document permission requirements

### Core Implementation
- [ ] Create `AudioSource.java` enum (MIC, DEVICE, BOTH)
- [ ] Create `MultiSourceAudioRecorder.java` class
  - [ ] Support separate AudioRecord instances
  - [ ] Implement sample interleaving
  - [ ] Handle synchronization
- [ ] Add audio source selection to service
- [ ] Update `AudioMemory` for multi-channel data
- [ ] Modify WAV file writing for stereo/quad

### Permission Handling
- [ ] Add CAPTURE_AUDIO_OUTPUT permission (if needed)
- [ ] Add MediaProjection permission handling
- [ ] Create permission request flow
- [ ] Add permission explanations for users
- [ ] Handle permission denial gracefully

### Service Integration
- [ ] Add audio source configuration to `SaidItService`
- [ ] Modify recording initialization for dual sources
- [ ] Handle device audio capture setup
- [ ] Implement fallback to mic-only if device audio fails
- [ ] Update recording state callbacks

### UI Implementation
- [ ] Add audio source selector in settings
- [ ] Add visual indicator for active sources
- [ ] Show warning for unsupported devices
- [ ] Add help text explaining dual-channel
- [ ] Update existing UI for multi-channel display

### Testing
- [ ] Test mic-only recording (baseline)
- [ ] Test device-only recording
- [ ] Test dual recording (mic + device)
- [ ] Verify channel separation in output files
- [ ] Test on Android 10, 11, 12, 13+
- [ ] Test with various audio formats

## Phase 6: Audio Activity Detection Mode

### Voice Activity Detection
- [ ] Create `VoiceActivityDetector.java` class
  - [ ] Implement amplitude-based detection
  - [ ] Add configurable threshold
  - [ ] Add noise floor estimation
  - [ ] Add smoothing/debouncing
  - [ ] Consider ML-based VAD (optional)
- [ ] Add VAD unit tests
- [ ] Benchmark VAD performance

### Recording Management
- [ ] Create `ActivityRecording.java` model class
  - [ ] Fields: id, timestamp, duration, file_path, is_flagged, delete_after
- [ ] Create `ActivityRecordingDatabase.java` (SQLite)
  - [ ] Schema design
  - [ ] CRUD operations
  - [ ] Query methods
- [ ] Create `RecordingAutoDeleter.java` scheduled job
  - [ ] Check deletion deadlines
  - [ ] Skip flagged recordings
  - [ ] Delete old files and database entries

### Buffer Management
- [ ] Add pre-activity buffer (5 min) to service
- [ ] Add post-activity buffer (5 min) to service
- [ ] Implement activity start detection
- [ ] Implement activity end detection
- [ ] Implement auto-save on activity window completion

### Service Integration
- [ ] Add activity detection mode to `SaidItService`
- [ ] Integrate VAD with recording loop
- [ ] Implement activity recording state machine
- [ ] Add high-bitrate recording for activity mode
- [ ] Handle mode switching

### UI Implementation
- [ ] Create `ActivityRecordingListActivity.java`
  - [ ] RecyclerView for recordings list
  - [ ] Show timestamp, duration, flagged status
  - [ ] Play button for each recording
  - [ ] Flag/unflag toggle
  - [ ] Delete button
  - [ ] Share functionality
- [ ] Add activity mode toggle in main UI
- [ ] Add VAD sensitivity slider in settings
- [ ] Add auto-delete period setting
- [ ] Add activity detection indicator
- [ ] Add layouts for recording list
- [ ] Add string resources

### Settings
- [ ] Add activity detection enable/disable
- [ ] Add sensitivity setting (threshold)
- [ ] Add pre-activity buffer duration
- [ ] Add post-activity buffer duration
- [ ] Add auto-delete period (days)
- [ ] Add high bitrate for activity recordings

### Testing
- [ ] Test VAD with various speech samples
- [ ] Test VAD with background noise
- [ ] Test pre-activity buffer capture
- [ ] Test post-activity buffer capture
- [ ] Test auto-save functionality
- [ ] Test auto-deletion
- [ ] Test flag protection
- [ ] Test on real usage scenarios (sleeping)

## Phase 7: Intent-Based Automation

### Intent API Design
- [ ] Define all intent action constants
- [ ] Define all intent extra keys
- [ ] Create intent validation utilities
- [ ] Design response/callback mechanism

### Intent Handlers
- [ ] Update `BroadcastReceiver.java` for new intents
  - [ ] START_RECORDING intent
  - [ ] STOP_RECORDING intent
  - [ ] ENABLE_LISTENING intent
  - [ ] DISABLE_LISTENING intent
  - [ ] SET_MEMORY_MODE intent
  - [ ] SET_DISK_MODE intent
  - [ ] SET_ACTIVITY_MODE intent
  - [ ] SET_MEMORY_SIZE intent (with extra)
  - [ ] SET_BITRATE intent (with extra)
- [ ] Add intent validation
- [ ] Add error responses
- [ ] Add success confirmations

### Manifest Updates
- [ ] Add intent filters for all new actions
- [ ] Set appropriate exported flags
- [ ] Add intent documentation comments

### Documentation
- [ ] Create `INTENT_API.md` document
- [ ] Document each intent action
- [ ] Provide usage examples
- [ ] Add Tasker integration examples
- [ ] Add automation app examples

### Testing
- [ ] Test each intent action individually
- [ ] Test with Tasker (if available)
- [ ] Test with adb shell commands
- [ ] Test invalid intent handling
- [ ] Test concurrent intent handling

## Phase 8: Testing and Polish

### Integration Testing
- [ ] Test all features together
- [ ] Test feature interactions
- [ ] Test state persistence across restarts
- [ ] Test with various Android versions
- [ ] Test on different device sizes
- [ ] Test on tablets
- [ ] Test accessibility features

### Performance Testing
- [ ] Profile CPU usage
- [ ] Profile memory usage
- [ ] Profile battery drain
- [ ] Optimize hot paths
- [ ] Reduce memory allocations
- [ ] Optimize disk I/O

### User Experience
- [ ] Review all UI flows
- [ ] Ensure consistent design
- [ ] Add loading indicators
- [ ] Add error messages
- [ ] Add success confirmations
- [ ] Improve help text
- [ ] Add tooltips where needed
- [ ] Test with non-technical users

### Documentation
- [ ] Update README.md
- [ ] Create USER_GUIDE.md
- [ ] Update in-app help
- [ ] Add feature screenshots
- [ ] Create quick start guide
- [ ] Document known limitations

### Code Quality
- [ ] Remove debug logging
- [ ] Add JavaDoc comments
- [ ] Format code consistently
- [ ] Remove unused imports
- [ ] Remove dead code
- [ ] Add error handling
- [ ] Add input validation

### Release Preparation
- [ ] Update version number
- [ ] Update changelog
- [ ] Test release build
- [ ] Verify ProGuard rules
- [ ] Test app signing
- [ ] Create release notes
- [ ] Prepare F-Droid metadata

## Future Enhancements (Out of Scope)

- [ ] Cloud backup integration
- [ ] Audio visualization
- [ ] Advanced audio effects
- [ ] Speech-to-text transcription
- [ ] Multi-language support
- [ ] Dark theme
- [ ] Widget support
- [ ] Wear OS companion app

## Notes

- Keep this TODO list updated as work progresses
- Reference IMPLEMENTATION_PLAN.md for detailed feature descriptions
- Mark items as complete with [x] as they finish
- Add new items as discovered during implementation
- Track blockers and issues in separate BLOCKERS.md if needed
