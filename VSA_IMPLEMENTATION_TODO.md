# VSA Refactoring - Implementation Plan & TODO

## Current Status (as of this commit)

### ✅ Completed
1. **Infrastructure Setup**
   - Added EventBus library (org.greenrobot:eventbus:3.3.1)
   - Created folder structure for all features
   - Created EventBusProvider singleton
   - Updated Android Gradle Plugin to 8.7.2

2. **Shared Components**
   - Moved SaidItActivity to `shared/activities/`
   - Moved StorageMode to `shared/models/`
   - Moved CrashHandler to `shared/services/`
   - Updated AndroidManifest with new paths
   - Updated all import statements

3. **Feature: Permissions Management**
   - Created models and events
   - Created PermissionService with event subscriptions
   - Ready for integration

4. **Feature: Preference Management**
   - Created PreferenceChangedEvent
   - Created PreferenceService with getters/setters
   - Publishes events on preference changes

5. **Feature: Audio Capture**
   - Moved AudioMemory, MultiSourceAudioRecorder, AudioSource
   - Created events (Start/Stop/StateChanged/Stats)
   - Created AudioCaptureConfig model
   - Updated all imports

6. **Feature: Audio Processing**
   - Moved AudioEffects, VoiceActivityDetector
   - Created OpusEncoder stub
   - Created ProcessAudioEvent and AudioEncoderType
   - Updated all imports

7. **Feature: Audio Export**
   - Moved AacMp4Writer, DiskAudioBuffer
   - Moved ActivityRecording, ActivityRecordingDatabase
   - Moved ActivityBlockBuilder
   - Created export events
   - Updated all imports

8. **Feature Skeletons**
   - Created Audio Playback skeleton
   - Created Export Management skeleton

### 📊 Statistics
- Feature files created: 33
- Shared files: 4
- Root package files remaining: 26 (will be migrated or deleted)

## 🚧 Next Steps (In Priority Order)

### Step 1: Cleanup Duplicate Files
The original files still exist in the root package. We need to:
1. Review which files have been successfully moved
2. Delete the originals from root package
3. Verify no broken imports

**Files to Delete After Verification:**
- `AudioMemory.java` (moved to audiocapture)
- `MultiSourceAudioRecorder.java` (moved to audiocapture)
- `AudioSource.java` (moved to audiocapture)
- `AudioEffects.java` (moved to audioprocessing)
- `VoiceActivityDetector.java` (moved to audioprocessing)
- `AacMp4Writer.java` (moved to audioexport)
- `DiskAudioBuffer.java` (moved to audioexport)
- `ActivityRecording.java` (moved to audioexport)
- `ActivityRecordingDatabase.java` (moved to audioexport)
- `ActivityBlockBuilder.java` (moved to audioexport)
- `StorageMode.java` (moved to shared/models)
- `CrashHandler.java` (moved to shared/services)
- `SaidItActivity.java` (moved to shared/activities)

### Step 2: Refactor SaidItService
**Goal**: Make SaidItService a coordinator that uses events

**Current State**: SaidItService has 1000+ lines with direct feature logic

**Refactoring Strategy**:
1. Create feature service coordinators:
   - `AudioCaptureService` - coordinates audio capture via events
   - `AudioProcessingService` - coordinates processing via events
   - `AudioExportService` - coordinates export via events

2. Initialize services in SaidItService.onCreate():
   ```java
   permissionService = new PermissionService(this);
   preferenceService = new PreferenceService(this);
   audioCaptureService = new AudioCaptureService(...);
   // etc.
   ```

3. Replace direct calls with events:
   - Instead of: `audioMemory.allocate(size)`
   - Use: `EventBus.post(new AllocateMemoryEvent(size))`

4. Subscribe to state change events:
   ```java
   @Subscribe
   public void onRecordingStateChanged(RecordingStateChangedEvent event) {
       updateNotification(event);
   }
   ```

### Step 3: Integrate Permission Management
1. Have SaidItActivity use PermissionService instead of direct checks
2. Publish `MediaProjectionPermissionRequestEvent` from service
3. Subscribe to `MediaProjectionPermissionGrantedEvent` in service
4. Remove permission logic from SaidItActivity

### Step 4: Integrate Preference Management
1. Replace all `SharedPreferences` calls with PreferenceService
2. Subscribe to `PreferenceChangedEvent` where needed
3. Remove direct SharedPreferences usage

### Step 5: Build and Test
1. Fix any compilation errors
2. Build the APK
3. Test core functionality:
   - Start/stop recording
   - Export audio
   - Auto-save
   - VAD recording
   - Permission flows

### Step 6: Code Review and Security Scan
1. Run code review tool
2. Run CodeQL security scanner
3. Address any findings

## 📝 Implementation Notes

### Event Naming Convention
- **Request Events**: `<Action>Event` (e.g., `StartRecordingEvent`)
- **State Change Events**: `<State>ChangedEvent` (e.g., `RecordingStateChangedEvent`)
- **Notification Events**: `<Event>Event` (e.g., `AudioStatsEvent`)

### Thread Safety
- EventBus handles thread safety
- Use `@Subscribe(threadMode = ThreadMode.ASYNC)` for long operations
- Use `@Subscribe(threadMode = ThreadMode.MAIN)` for UI updates

### Error Handling
- Publish error events instead of throwing exceptions
- Use CrashHandler for unrecoverable errors
- Log errors with feature context

### Testing Strategy
- Each feature can be tested in isolation
- Mock EventBus for unit tests
- Integration tests can verify event flow

## 🎯 Success Criteria

1. ✅ All features in separate folders
2. ✅ Event-based communication infrastructure
3. ⏳ No direct dependencies between features
4. ⏳ SaidItService acts as event coordinator
5. ⏳ Application builds successfully
6. ⏳ All core features work
7. ⏳ Code review passes
8. ⏳ Security scan passes

## 📚 Documentation

### Architecture Docs Created
- `VSA_ARCHITECTURE.md` - Complete architecture overview
- This file - Implementation plan and TODO

### Documentation TODO
- Update README.md with architecture section
- Add feature-specific README files
- Document event flow diagrams
- Add contribution guidelines for new features

## 🔄 Future Enhancements

After VSA implementation is complete:

1. **Intent API Integration**
   - Create intent handlers in each feature
   - Route intent actions through events

2. **Audio Playback Feature**
   - Implement AudioPlaybackService
   - Add preview functionality
   - Timeline scrubbing

3. **Export Management Feature**
   - Implement export queue
   - Export history
   - Auto-cleanup

4. **Testing Infrastructure**
   - Add unit tests for each feature
   - Add integration tests for event flows
   - Add UI tests for user flows

5. **Performance Monitoring**
   - Add event performance tracking
   - Monitor memory usage per feature
   - Profile audio processing pipeline

## 🐛 Known Issues

1. **Build Configuration**
   - AGP version changed from 8.10.1 to 8.7.2 due to network issues
   - May need to revert once network is available

2. **Duplicate Files**
   - Original files still in root package
   - Need cleanup after verification

3. **Import Statements**
   - Bulk updated via sed scripts
   - May need manual fixes for edge cases

## 💡 Design Decisions

### Why EventBus?
- Lightweight and fast (in-process, no IPC overhead)
- Well-documented and maintained
- Thread-safe with flexible delivery options
- Can be extended for IPC later if needed

### Why Not RxJava?
- Heavier dependency
- Steeper learning curve
- More than we need for feature communication

### Why Vertical Slices?
- Reduces coupling between features
- Easier to understand and maintain
- Allows independent development
- Facilitates testing
- Enables feature toggles

### File Organization
- Copied files rather than moving initially
- Allows gradual migration without breaking builds
- Can verify correctness before cleanup
