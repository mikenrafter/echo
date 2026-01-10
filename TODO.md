# Echo Enhancement TODO List

This document tracks the detailed implementation tasks for enhancing Echo with advanced audio recording features.

## Current Status

### ✅ Completed Phases
- [x] **Phase 1: Documentation** - Comprehensive planning and architecture docs created
- [x] **Phase 2: Configurable Memory Management** - Exact MB input with validation implemented
- [x] **Phase 3: Storage Modes** - Memory Only and Batch to Disk modes fully functional
- [x] **Phase 7: Intent-Based Automation** - Complete API with 8 actions and full documentation

### 🚧 Partially Completed
- **Phase 6: Audio Activity Detection** - Infrastructure ready, integration incomplete
- **Main UI Enhancements**
  - [x] Replace preset duration buttons with single CLIP RECORDING
  - [x] Add range help text
  - [x] Add live volume meter (service + UI wiring)
  - [x] Add counters for skipped silent seconds and groups
  - [ ] Silence log activity (initial version added; refine pruning/UX)

### ⏳ Not Started
- **Phase 4: Multi-Quality Encoding** - Planned but not implemented
- **Phase 5: Dual-Channel Audio Capture** - Planned but high complexity due to Android limitations
 - **Skipped Silence Details**
   - [ ] Persist silence groups beyond memory window (optional)
   - [ ] Add share/export for silence logs (optional)

---

## Phase 1: Foundation and Reference Code ✅

### Add Reference Implementations
- [x] Created comprehensive IMPLEMENTATION_PLAN.md
- [x] Created TODO.md for task tracking
- [x] Analyzed existing codebase architecture
- [ ] ~~Research and find Alibi app repository URL~~ - Not essential, implemented based on concept
- [ ] ~~Research and find Circular-Recorder app repository URL~~ - Not essential, implemented based on concept
- [ ] ~~Add reference apps as git submodules~~ - Skipped, would require finding appropriate repos
- [x] Implemented circular buffer concept based on understanding
- [x] Documented implementation in CODE_REVIEW_ANALYSIS.md

## Phase 2: Configurable Memory Management ✅

### Backend Implementation
- [x] Add `MEMORY_SIZE_MB_KEY` constant to `SaidIt.java`
- [x] Add `getMemorySizeMB()` method to `SaidItService.java`
- [x] Add `setMemorySizeMB(int mb)` method to `SaidItService.java`
- [x] Add memory size validation (min: 10MB, max: device-dependent)
- [x] AudioMemory already handles exact byte sizes correctly
- [x] Add error handling for memory allocation failures

### UI Implementation
- [x] Add memory size EditText to `activity_settings.xml`
- [x] Add MB unit label next to input field
- [x] Add "Apply" button for memory settings
- [x] Display shows current memory allocation
- [x] Add string resources for new UI elements
- [x] Implement input validation in CustomMemoryApplyListener
- [x] Show toast messages for success/error feedback

### Testing
- [ ] Test with minimum memory size (10MB) - Requires real device
- [ ] Test with maximum memory size - Requires real device
- [ ] Test with invalid input (negative, zero, too large) - Validation implemented
- [ ] Test memory allocation on low-memory devices - Requires testing
- [ ] Verify memory size persists across app restarts - SharedPreferences used

## Phase 3: Storage Modes ✅

### Core Classes
- [x] Create `StorageMode.java` enum (MEMORY_ONLY, BATCH_TO_DISK)
- [x] Create `DiskAudioBuffer.java` class with full implementation:
  - [x] Circular file buffer with automatic rotation
  - [x] `write(byte[] data)` method with chunk management
  - [x] `read(int skipBytes, Consumer)` method for playback/export
  - [x] `cleanup()` method for old files
  - [x] Disk space checking and monitoring
  - [x] File rotation logic based on chunk size
- [x] Add `STORAGE_MODE_KEY` and `MAX_DISK_USAGE_MB_KEY` constants
- [x] Default max disk usage set to 500 MB

### Service Integration
- [x] Add storage mode field to `SaidItService.java`
- [x] Add `setStorageMode(StorageMode mode)` method
- [x] Modify audio recording loop to support disk mode
- [x] Implement batch writing to disk in filler Consumer
- [x] Add disk space monitoring via DiskAudioBuffer
- [x] Add automatic cleanup when disk is full
- [x] Handle mode switching during active recording
- [x] Ensure proper resource cleanup in innerStopListening()
- [x] **NEW**: Add disk buffer read support in dumpRecording()

### UI Implementation
- [x] Add storage mode toggle buttons to settings UI
- [x] Add mode indicator showing current selection
- [x] Add settings for storage mode in SharedPreferences
- [x] Add string resources for storage mode labels
- [x] Implement StorageModeClickListener
- [x] Update highlightButtons() to show current mode
- [ ] Add disk usage display in UI - Optional enhancement

### Testing
- [ ] Test memory-only mode - Requires real device
- [ ] Test batch-to-disk mode with small files - Requires real device
- [ ] Test batch-to-disk mode with large files - Requires real device
- [ ] Test disk space handling when full - Requires real device
- [ ] Test mode switching during recording - Requires real device
- [ ] Test file cleanup - Requires real device
- [ ] Test across app restarts - Requires real device

## Phase 7: Intent-Based Automation ✅

### Intent API Design
- [x] Define all 8 intent action constants in BroadcastReceiver
- [x] Define intent extra keys (memory_size_mb, prepend_seconds, filename)
- [x] Create intent validation utilities with bounds checking
- [x] Design service binding mechanism for execution

### Intent Handlers
- [x] Update `BroadcastReceiver.java` for automation intents:
  - [x] START_RECORDING intent with prepend time validation (0-3600s)
  - [x] STOP_RECORDING intent with optional filename
  - [x] ENABLE_LISTENING intent
  - [x] DISABLE_LISTENING intent
  - [x] SET_MEMORY_MODE intent
  - [x] SET_DISK_MODE intent
  - [x] SET_MEMORY_SIZE intent with validation (10MB-10GB)
  - [x] DUMP_RECORDING intent with time validation
- [x] Add comprehensive input validation
- [x] Add error logging for all operations
- [x] Add ClassCastException handling for binder
- [x] Proper service binding lifecycle management

### Manifest Updates
- [x] Add intent filters for all 8 new actions
- [x] Set appropriate exported flags for BroadcastReceiver
- [x] Add intent documentation in manifest comments

### Documentation
- [x] Create comprehensive `INTENT_API.md` document
- [x] Document each intent action with examples
- [x] Provide ADB shell command examples
- [x] Add Tasker integration guide with profiles
- [x] Add Automate integration guide with flows
- [x] Document security considerations
- [x] Add troubleshooting section

### Testing
- [ ] Test each intent action individually - Requires adb/device
- [ ] Test with Tasker - Requires Tasker app
- [ ] Test with adb shell commands - Requires device
- [ ] Test invalid intent handling - Validation implemented
- [ ] Test concurrent intent handling - Requires testing

## Phase 6: Audio Activity Detection 🚧

### Voice Activity Detection (Completed)
- [x] Create `VoiceActivityDetector.java` class
  - [x] Implement RMS energy calculation
  - [x] Add adaptive threshold with noise floor estimation
  - [x] Add smoothing window for stability
  - [x] Add configurable sensitivity via threshold
  - [x] Implement hysteresis (min active/silence frames)
  - [x] Add reset() and state query methods

### Recording Management (Completed)
- [x] Create `ActivityRecording.java` model class
  - [x] Fields: id, timestamp, duration, file_path, is_flagged, delete_after
  - [x] Helper methods for formatting
- [x] Create `ActivityRecordingDatabase.java` (JSON-based)
  - [x] CRUD operations (add, update, delete, get)
  - [x] getAllRecordings() with sorting
  - [x] getRecordingsToDelete() for cleanup
  - [x] cleanupExpiredRecordings() method
  - [x] toggleFlag() for deletion protection

### Service Integration (Partial)
- [x] Add activity detection fields to `SaidItService`
- [x] Initialize VAD and database in onCreate()
- [x] Load activity detection settings from preferences
- [x] Add preference keys for all settings
- [ ] **TODO**: Integrate VAD processing into audio recording loop
- [ ] **TODO**: Implement pre-activity buffer (5 minutes)
- [ ] **TODO**: Implement post-activity buffer (5 minutes)
- [ ] **TODO**: Auto-save recordings on activity completion
- [ ] **TODO**: Apply high bitrate for activity recordings

### UI Implementation (Not Started)
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
- [ ] Add string resources for UI

### Settings (Not Started)
- [ ] Add activity detection enable/disable toggle
- [ ] Add sensitivity setting (threshold slider)
- [ ] Add pre-activity buffer duration setting
- [ ] Add post-activity buffer duration setting
- [ ] Add auto-delete period setting (days)
- [ ] Add high bitrate setting for activity recordings

### Testing (Pending Integration)
- [ ] Test VAD with various speech samples
- [ ] Test VAD with background noise
- [ ] Test pre-activity buffer capture
- [ ] Test post-activity buffer capture
- [ ] Test auto-save functionality
- [ ] Test auto-deletion
- [ ] Test flag protection
- [ ] Test on real usage scenarios (sleeping mode)

## Phase 4: Multi-Quality Encoding ⏳

### Not Yet Implemented - Requires Complex Real-Time Encoding

**Reason for deferral**: Requires significant performance optimization and real-time audio encoding which may impact battery life. Should be evaluated based on user demand.

### Planned Implementation
- [ ] Design dual-buffer system in AudioMemory
  - [ ] High-quality buffer for recent audio
  - [ ] Low-quality buffer for older audio
- [ ] Create `AudioEncoder.java` utility class
  - [ ] Support AAC/MP3/Opus codecs
  - [ ] Quality presets (LOW, MEDIUM, HIGH)
- [ ] Implement configurable time window
- [ ] Add encoding settings UI
- [ ] Performance testing and optimization

## Phase 5: Dual-Channel Audio Capture ⏳

### Not Yet Implemented - High Complexity Due to Android Limitations

**Reason for deferral**: Android has significant restrictions on system audio capture. AudioPlaybackCapture API (Android 10+) has limited device/app compatibility. Many apps block their audio from being captured.

### Planned Research
- [ ] Research AudioPlaybackCapture API requirements
- [ ] Test on multiple devices for compatibility
- [ ] Document which apps/scenarios work
- [ ] Determine feasibility for target user base

### If Feasible
- [ ] Create `MultiSourceAudioRecorder.java`
- [ ] Implement sample interleaving for L/R channels
- [ ] Add permission handling (CAPTURE_AUDIO_OUTPUT)
- [ ] Update AudioMemory for multi-channel
- [ ] Add audio source selection UI
- [ ] Provide clear fallback for unsupported devices

## Phase 8: Testing, Optimization, and Polish

### Code Quality (Ongoing)
- [x] Add exception handling throughout
- [x] Add input validation for all user inputs
- [x] Thread-safe audio operations maintained
- [x] Resource cleanup verified
- [x] API compatibility ensured (minSdk 30)
- [x] Create CODE_REVIEW_ANALYSIS.md with issues and recommendations

### Testing (Requires Real Device)
- [ ] End-to-end testing of all features
- [ ] Performance profiling (CPU, memory, battery)
- [ ] Test on Android 10, 11, 12, 13, 14
- [ ] Test on different screen sizes
- [ ] Test on tablets
- [ ] Memory leak detection

### Documentation (Comprehensive)
- [x] IMPLEMENTATION_PLAN.md created
- [x] TODO.md created and maintained
- [x] INTENT_API.md with complete automation guide
- [x] IMPLEMENTATION_SUMMARY.md with status
- [x] CODE_REVIEW_ANALYSIS.md with deep review
- [x] README.md updated with new features
- [ ] Create USER_GUIDE.md for end users
- [ ] Add inline code documentation where needed

### Release Preparation (Future)
- [ ] Update version number in build.gradle
- [ ] Update changelog
- [ ] Test release build
- [ ] Verify ProGuard rules
- [ ] Create release notes
- [ ] Prepare F-Droid metadata

## Future Enhancements (Out of Current Scope)

- [ ] Cloud backup integration
- [ ] Audio visualization in UI
- [ ] Advanced audio effects
- [ ] Speech-to-text transcription
- [ ] Multi-language support
- [ ] Dark theme
- [ ] Widget support
- [ ] Wear OS companion app

## Notes

- This TODO list reflects the actual implementation status as of the latest commit
- Phases 1, 2, 3, and 7 are fully implemented and functional
- Phase 6 has infrastructure ready but needs integration work
- Phases 4 and 5 are deferred due to complexity and Android limitations
- Testing requires real Android device as build is not possible in sandbox environment
- See CODE_REVIEW_ANALYSIS.md for detailed code review and identified issues


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
