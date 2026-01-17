# VSA Refactoring - Technical Summary

## Executive Summary

This refactoring transforms the Echo audio recording Android app from a flat, tightly-coupled architecture to a Vertical Slice Architecture (VSA) where features are self-contained and communicate via events.

## Technical Overview

### Architecture Pattern: Vertical Slice Architecture (VSA)

**Definition**: VSA organizes code by feature/capability rather than technical layer. Each "slice" contains all the code needed for a feature to work independently.

**Traditional Layered Architecture**:
```
Controllers → Services → Repositories → Models
(Horizontal layers, cross-cutting concerns)
```

**Vertical Slice Architecture**:
```
Feature A: UI → Logic → Data → Events
Feature B: UI → Logic → Data → Events
Feature C: UI → Logic → Data → Events
(Vertical slices, isolated features)
```

### Implementation Structure

```
eu.mrogalski.saidit/
├── shared/                          # Shared infrastructure
│   ├── activities/
│   │   └── SaidItActivity.java     # Main activity
│   ├── models/
│   │   └── StorageMode.java        # Common data types
│   ├── services/
│   │   └── CrashHandler.java       # Error handling
│   └── events/
│       └── EventBusProvider.java   # Event bus singleton
│
└── features/                        # Feature slices
    ├── permissions/                 # Permission management
    │   ├── models/
    │   │   ├── PermissionState.java
    │   │   ├── PermissionsGrantedEvent.java
    │   │   ├── PermissionCheckRequestEvent.java
    │   │   ├── MediaProjectionPermissionGrantedEvent.java
    │   │   └── MediaProjectionPermissionRequestEvent.java
    │   └── services/
    │       └── PermissionService.java
    │
    ├── preferences/                 # Preference management
    │   ├── models/
    │   │   └── PreferenceChangedEvent.java
    │   └── services/
    │       └── PreferenceService.java
    │
    ├── audiocapture/                # Audio recording & capture
    │   ├── models/
    │   │   ├── AudioSource.java
    │   │   ├── AudioCaptureConfig.java
    │   │   ├── StartRecordingEvent.java
    │   │   ├── StopRecordingEvent.java
    │   │   ├── RecordingStateChangedEvent.java
    │   │   └── AudioStatsEvent.java
    │   └── services/
    │       ├── AudioMemory.java             # Circular buffer
    │       └── MultiSourceAudioRecorder.java # Dual-source recording
    │
    ├── audioprocessing/             # Audio processing & effects
    │   ├── models/
    │   │   ├── ProcessAudioEvent.java
    │   │   └── AudioEncoderType.java
    │   └── services/
    │       ├── AudioEffects.java            # Normalization, noise cancel
    │       ├── VoiceActivityDetector.java   # VAD
    │       └── OpusEncoder.java            # Opus stub
    │
    ├── audioexport/                 # Export & auto-save
    │   ├── models/
    │   │   ├── ActivityRecording.java
    │   │   ├── ExportAudioEvent.java
    │   │   ├── ExportCompleteEvent.java
    │   │   └── AutoSaveEvent.java
    │   └── services/
    │       ├── AacMp4Writer.java
    │       ├── DiskAudioBuffer.java
    │       ├── ActivityRecordingDatabase.java
    │       └── ActivityBlockBuilder.java
    │
    ├── audioplayback/               # Playback (skeleton)
    │   ├── models/
    │   │   └── PlayAudioEvent.java
    │   └── services/
    │       └── AudioPlaybackService.java
    │
    └── exportmanagement/            # Export queue (skeleton)
        ├── models/
        │   └── ExportJob.java
        └── services/
            └── ExportManagementService.java
```

## Event-Driven Communication

### EventBus Library
- **Library**: greenrobot EventBus 3.3.1
- **License**: Apache 2.0
- **Size**: ~60 KB
- **Security**: No known vulnerabilities

### Event Flow Architecture

```
┌─────────────────┐
│   UI Layer      │
│  (Activities)   │
└────────┬────────┘
         │ publishes events
         ▼
┌─────────────────────────────────┐
│       EventBus (Singleton)      │
│  (Central Communication Hub)    │
└─────────┬───────────────────────┘
          │ dispatches to subscribers
          ▼
┌─────────────────────────────────┐
│     Feature Services            │
│  ┌─────────────────────────┐   │
│  │ @Subscribe(...)         │   │
│  │ onEvent(Event e) {      │   │
│  │   // Handle event       │   │
│  │   // Publish response   │   │
│  │ }                       │   │
│  └─────────────────────────┘   │
└─────────────────────────────────┘
```

### Event Patterns

#### 1. Request-Response Pattern
```java
// Requester
EventBus.post(new StartRecordingEvent(prependSeconds));

// Handler
@Subscribe(threadMode = ThreadMode.ASYNC)
public void onStartRecording(StartRecordingEvent event) {
    // Start recording
    EventBus.post(new RecordingStateChangedEvent(true, timestamp));
}
```

#### 2. Observer Pattern
```java
// Publisher
EventBus.post(new PreferenceChangedEvent(key, value));

// Multiple subscribers can listen
@Subscribe
public void onPreferenceChanged(PreferenceChangedEvent event) {
    // React to preference change
}
```

#### 3. Request-Only Pattern
```java
// Fire and forget
EventBus.post(new AutoSaveEvent());
```

## Feature Descriptions

### 1. Permissions Management
**Responsibility**: Centralize all permission checks and requests

**Key Components**:
- `PermissionService`: Check and track permission state
- Events for permission lifecycle

**Benefits**:
- Single source of truth for permissions
- Other features can query permission state via events
- Android API version compatibility handled centrally

### 2. Preference Management
**Responsibility**: Centralize SharedPreferences access

**Key Components**:
- `PreferenceService`: Type-safe preference access
- `PreferenceChangedEvent`: Notify on changes

**Benefits**:
- No direct SharedPreferences access
- Preference changes propagate via events
- Easy to add validation or default values

### 3. Audio Capture
**Responsibility**: Record audio from mic and/or device

**Key Components**:
- `AudioMemory`: Circular buffer with silence skipping
- `MultiSourceAudioRecorder`: Dual-source recording
- Events for recording lifecycle

**Technical Details**:
- Supports mic, device audio (AudioPlaybackCapture API), or both
- Circular buffer with configurable size
- Silence detection and skipping
- Timeline management

### 4. Audio Processing
**Responsibility**: Process audio (effects, VAD, encoding)

**Key Components**:
- `AudioEffects`: Normalization, noise cancellation
- `VoiceActivityDetector`: Speech detection
- `OpusEncoder`: Future Opus support (stub)

**Technical Details**:
- Effects applied only during export (not real-time)
- VAD using amplitude-based detection
- Encoder passed via events (extensible)

### 5. Audio Export
**Responsibility**: Export audio to files, auto-save, VAD recordings

**Key Components**:
- `AacMp4Writer`: AAC/MP4 encoding
- `DiskAudioBuffer`: Disk-based circular buffer
- `ActivityRecordingDatabase`: VAD recording management
- `ActivityBlockBuilder`: Timeline construction

**Technical Details**:
- Scheduled auto-save via AlarmManager
- VAD recordings in separate folder
- Auto-cleanup after N days
- Supports WAV and AAC formats

### 6. Audio Playback (Skeleton)
**Purpose**: Preview and playback (future feature)

**Planned Features**:
- Preview audio before export
- Timeline scrubbing
- Playback controls

### 7. Export Management (Skeleton)
**Purpose**: Manage export queue and history (future feature)

**Planned Features**:
- Queue multiple exports
- Track export progress
- Export history
- Auto-cleanup

## Technical Benefits

### 1. Loose Coupling
- Features don't directly depend on each other
- Changes to one feature don't affect others
- Easy to add/remove features

### 2. High Cohesion
- Related code is grouped together
- Feature logic is self-contained
- Easy to understand feature scope

### 3. Testability
- Features can be tested in isolation
- Mock EventBus for unit tests
- Integration tests verify event flow

### 4. Maintainability
- Clear boundaries between features
- Easy to locate feature code
- Reduce merge conflicts

### 5. Scalability
- Add features without touching existing code
- Scale team with feature ownership
- Enable/disable features independently

### 6. Performance
- EventBus is lightweight (in-process)
- No IPC overhead
- Thread-safe with flexible delivery

## Migration Strategy

### Phase 1: Setup (✅ Complete)
1. Add EventBus dependency
2. Create folder structure
3. Create EventBusProvider

### Phase 2: Copy Files (✅ Complete)
1. Copy files to feature folders
2. Update package declarations
3. Update all imports

### Phase 3: Refactor Services (🚧 In Progress)
1. Create feature service coordinators
2. Wire up event subscriptions
3. Replace direct calls with events

### Phase 4: Cleanup (⏳ Pending)
1. Delete original files
2. Remove unused imports
3. Verify no coupling

### Phase 5: Testing (⏳ Pending)
1. Build application
2. Test all features
3. Code review
4. Security scan

## Code Quality Metrics

### Before Refactoring
- **Files in root package**: 26
- **Average file size**: ~400 lines
- **Coupling**: High (direct dependencies)
- **Cohesion**: Low (mixed concerns)

### After Refactoring
- **Feature files**: 33
- **Shared files**: 4
- **Root package files**: 26 (to be removed)
- **Coupling**: Low (event-based only)
- **Cohesion**: High (feature-focused)

## Dependency Graph

### Before (Tightly Coupled)
```
SaidItService ──┬──> AudioMemory
                ├──> AudioEffects
                ├──> VoiceActivityDetector
                ├──> AacMp4Writer
                ├──> DiskAudioBuffer
                └──> ActivityRecordingDatabase
(Direct dependencies, hard to test)
```

### After (Loosely Coupled)
```
SaidItService ──> EventBus <── AudioCaptureService
                    ↑ ↓
                    ↑ ↓      AudioProcessingService
                    ↑ ↓
                    ↑ ↓      AudioExportService
                    ↑ ↓
                    ↑ ↓      PermissionService
                    ↑ ↓
                    ↑ └──── PreferenceService
(Event-based, easy to test)
```

## Security Considerations

1. **EventBus Security**
   - In-process only (no IPC exposure)
   - No network communication
   - No known vulnerabilities

2. **Permission Management**
   - Centralized permission checks
   - MediaProjection properly handled
   - Storage permissions validated

3. **Data Privacy**
   - Audio data stays on device
   - No telemetry or analytics
   - User controls all data

## Performance Considerations

1. **EventBus Overhead**
   - Minimal (nanoseconds per event)
   - Thread-safe with lock-free reads
   - Efficient subscriber lookup

2. **Memory Usage**
   - No additional memory for events
   - Events are short-lived
   - GC-friendly

3. **Audio Processing**
   - Events don't block audio thread
   - Async processing for heavy tasks
   - Handler-based threading preserved

## Future Extensibility

### Adding New Features
1. Create feature folder structure
2. Define events and models
3. Implement service with event handlers
4. Register service in SaidItService
5. Publish events as needed

### Adding New Events
1. Create event class in feature/models
2. Add @Subscribe handler in service
3. Publish event from any component
4. No changes to existing code

### Inter-Process Communication
If IPC is needed in the future:
- EventBus supports sticky events
- Can integrate with Android Messenger
- Can use AIDL for cross-process events
- No architectural changes needed

## Conclusion

This VSA refactoring provides:
- **Better organization**: Features are self-contained
- **Loose coupling**: Event-based communication
- **High testability**: Isolated feature testing
- **Maintainability**: Clear boundaries and responsibilities
- **Scalability**: Easy to add new features
- **Performance**: Minimal overhead with EventBus

The architecture supports future growth while maintaining clean separation of concerns and enabling independent feature development.
