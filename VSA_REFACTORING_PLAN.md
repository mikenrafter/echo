# VSA Refactoring Implementation Plan

## Phase 1: File Organization ✅ COMPLETE

### Files to Move

#### 1. AudioEncoder.java → features/audioprocessing/services/
**Rationale**: Encoding is an audio processing function
- Contains Quality and Codec enums
- Provides codec detection utilities
- Used by export features but belongs in processing layer

#### 2. BroadcastReceiver.java → shared/intents/
**Rationale**: Handles cross-cutting automation intents
- Receives system broadcasts (BOOT_COMPLETED)
- Handles automation intents for multiple features
- Not feature-specific, serves as intent gateway

#### 3. IntentResult.java → shared/models/
**Rationale**: User-specified as shared model
- Appears to be for barcode/QR code scanning results
- Generic data structure, not feature-specific

#### 4. RecordingDoneDialog.java → features/audioexport/activities/
**Rationale**: Post-recording UI for completed exports
- Shows recording details after export
- Handles file sharing and playback
- Tightly coupled to export feature

---

## Phase 2: SaidItService Refactoring

### Current Problems
- **2346 lines** in a single service file
- Mixes concerns: audio capture, recording, export, VAD, storage, MediaProjection, auto-save
- Direct field access instead of service delegation
- Difficult to test individual features

### Delegation Strategy

#### A. Audio Capture Logic → AudioCaptureService
**Extract:**
- `innerStartListening()` / `innerStopListening()`
- AudioRecord setup and management
- MediaProjection handling for device audio
- Dual-source recording logic (`readAndMixDualSource()`)
- Audio reader thread (`audioReader` Runnable)
- Memory allocation and management
- Gradient quality routing logic

**Keep in SaidItService:**
- Service lifecycle (onCreate, onDestroy, onBind)
- Foreground notification
- State machine (STATE_READY, STATE_LISTENING, STATE_RECORDING)
- Coordination between features

**Events:**
- Subscribe: `StartListeningEvent`, `StopListeningEvent`
- Publish: `AudioCaptureStateChanged`, `AudioDataAvailable`

#### B. Storage Logic → StorageManagementService
**Extract:**
- StorageMode management
- Memory size configuration
- Disk buffer initialization (`initializeDiskBuffer()`, `cleanupDiskBuffer()`)
- DiskAudioBuffer lifecycle

**Events:**
- Subscribe: `StorageModeChangedEvent`, `MemorySizeChangedEvent`
- Publish: `StorageStateChanged`

#### C. Recording Export Logic → RecordingExportService
**Extract:**
- `startRecording()` / `stopRecording()`
- `dumpRecording()` / `dumpRecordingRange()`
- WAV file writing
- Export with effects (normalization, noise suppression)
- Memory-efficient export fallback

**Events:**
- Subscribe: `StartRecordingEvent`, `StopRecordingEvent`, `ExportRangeEvent`
- Publish: `RecordingStarted`, `RecordingCompleted`, `ExportCompleted`

#### D. VAD Logic → VoiceActivityService
**Extract:**
- `VoiceActivityDetector` integration
- Activity recording state machine
- Activity file writing
- Pre/post buffer management
- Auto-delete cleanup

**Events:**
- Subscribe: `ActivityDetectedEvent`, `ActivityEndedEvent`
- Publish: `VadRecordingStarted`, `VadRecordingCompleted`

#### E. Auto-Save Logic → AutoSaveService
**Extract:**
- AlarmManager setup
- Scheduled save operations
- Auto-delete cleanup
- Pending intent management

**Events:**
- Subscribe: `AutoSaveEvent`, `AutoSaveCleanupEvent`
- Publish: `AutoSaveCompleted`

#### F. MediaProjection Logic → MediaProjectionService
**Extract:**
- MediaProjection lifecycle
- Permission request coordination
- AudioPlaybackCaptureConfiguration setup

**Events:**
- Subscribe: `MediaProjectionRequestEvent`, `MediaProjectionGrantedEvent`
- Publish: `MediaProjectionAvailable`, `MediaProjectionRevoked`

#### G. Timeline/Stats → TimelineService
**Extract:**
- Timeline segment tracking
- Silence group aggregation
- Live stats computation
- Callback interfaces (`StateCallback`, `LiveStatsCallback`, `SilenceGroupsCallback`, `TimelineCallback`)

**Events:**
- Subscribe: `TimelineUpdateRequest`
- Publish: `TimelineUpdated`, `StatsUpdated`

---

## Phase 3: Service Coordination Pattern

### New SaidItService Structure

```java
public class SaidItService extends Service {
    // Feature services
    private AudioCaptureService audioCaptureService;
    private StorageManagementService storageService;
    private RecordingExportService exportService;
    private VoiceActivityService vadService;
    private AutoSaveService autoSaveService;
    private MediaProjectionService projectionService;
    private TimelineService timelineService;
    
    @Override
    public void onCreate() {
        // Initialize event bus
        EventBus.getDefault().register(this);
        
        // Initialize feature services
        audioCaptureService = new AudioCaptureService(this);
        storageService = new StorageManagementService(this);
        exportService = new RecordingExportService(this);
        vadService = new VoiceActivityService(this);
        autoSaveService = new AutoSaveService(this);
        projectionService = new MediaProjectionService(this);
        timelineService = new TimelineService(this);
        
        // Start listening if enabled
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            EventBus.getDefault().post(new StartListeningEvent());
        }
    }
    
    // Public API - delegates to events
    public void enableListening() {
        EventBus.getDefault().post(new StartListeningEvent());
    }
    
    public void startRecording(float prependSeconds) {
        EventBus.getDefault().post(new StartRecordingEvent(prependSeconds));
    }
    
    // Legacy API adapters for backward compatibility
    public void getState(StateCallback callback) {
        timelineService.getState(callback);
    }
    
    // Binder for activities
    public class BackgroundRecorderBinder extends Binder {
        public SaidItService getService() {
            return SaidItService.this;
        }
    }
}
```

---

## Implementation Order

### Step 1: Create Service Skeletons ✅
- Create empty service classes in feature folders
- Add constructor, EventBus registration
- Define event models

### Step 2: Extract Storage Logic
- Move storage-related methods to StorageManagementService
- Test storage mode switching

### Step 3: Extract Audio Capture Logic
- Move listening logic to AudioCaptureService
- Test audio capture start/stop

### Step 4: Extract Recording Export Logic
- Move recording/export to RecordingExportService
- Test recording and export

### Step 5: Extract VAD Logic
- Move VAD to VoiceActivityService
- Test activity detection

### Step 6: Extract Auto-Save Logic
- Move auto-save to AutoSaveService
- Test scheduled saves

### Step 7: Extract MediaProjection Logic
- Move projection to MediaProjectionService
- Test device audio capture

### Step 8: Extract Timeline Logic
- Move timeline to TimelineService
- Test timeline callbacks

### Step 9: Wire Up Events
- Replace direct method calls with events
- Add event subscribers to services
- Test end-to-end workflows

### Step 10: Cleanup
- Remove extracted code from SaidItService
- Update documentation
- Run full test suite

---

## Testing Strategy

### Unit Tests
- Each service should be independently testable
- Mock EventBus for testing
- Verify event publication/subscription

### Integration Tests
- Test feature interactions via events
- Verify no direct coupling between features

### Regression Tests
- Ensure existing functionality works
- Test all user workflows
- Verify no performance degradation

---

## Migration Checklist

- [ ] Move AudioEncoder.java
- [ ] Move BroadcastReceiver.java
- [ ] Create and move IntentResult.java
- [ ] Move RecordingDoneDialog.java
- [ ] Create StorageManagementService
- [ ] Create AudioCaptureService
- [ ] Create RecordingExportService
- [ ] Create VoiceActivityService
- [ ] Create AutoSaveService
- [ ] Create MediaProjectionService
- [ ] Create TimelineService
- [ ] Define all event models
- [ ] Wire up event flows
- [ ] Update SaidItService to delegate
- [ ] Update imports across codebase
- [ ] Test build
- [ ] Test functionality
- [ ] Update VSA_ARCHITECTURE.md

---

## Benefits After Refactoring

1. **Reduced Complexity**: SaidItService goes from 2346 lines to ~200 lines
2. **Better Testability**: Each service can be tested in isolation
3. **Improved Maintainability**: Features are self-contained
4. **Easier Debugging**: Clear boundaries between features
5. **Scalability**: New features can be added without touching core service
6. **Event Tracing**: All feature interactions visible via EventBus
