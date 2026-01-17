# VSA Architecture Implementation Summary

## Overview
This document describes the Vertical Slice Architecture (VSA) refactoring of the Echo Android audio recording application.

## Architecture Principles

### 1. Feature-Based Organization
Each feature is self-contained in its own folder with the following structure:
```
features/
  <feature-name>/
    intents/      # Intent handlers and definitions
    activities/   # UI components specific to this feature
    services/     # Business logic and feature services
    models/       # Data models and events for this feature
```

### 2. Event-Driven Communication
Features communicate exclusively through events published via the EventBus:
- Features publish events when their state changes
- Features subscribe to events from other features
- No direct dependencies between features

### 3. Shared Infrastructure
Common components used across features:
```
shared/
  activities/   # Main activity (SaidItActivity)
  models/       # Common data types (StorageMode, etc.)
  services/     # Cross-cutting services (CrashHandler, etc.)
  events/       # EventBusProvider singleton
```

## Features Implemented

### 1. Permissions Management (`features/permissions`)
**Purpose**: Manage all app permissions and device capabilities

**Models/Events**:
- `PermissionState` - State of a specific permission
- `MediaProjectionPermissionGrantedEvent` - MediaProjection granted
- `MediaProjectionPermissionRequestEvent` - Request MediaProjection
- `PermissionsGrantedEvent` - All permissions ready
- `PermissionCheckRequestEvent` - Request permission check

**Services**:
- `PermissionService` - Checks permission state, responds to check requests

**Communication**:
- **Subscribes to**: `PermissionCheckRequestEvent`
- **Publishes**: `PermissionsGrantedEvent`

### 2. Preference Management (`features/preferences`)
**Purpose**: Centralized preference storage with change notifications

**Models/Events**:
- `PreferenceChangedEvent` - Published when any preference changes

**Services**:
- `PreferenceService` - Get/set preferences, publishes change events

**Communication**:
- **Publishes**: `PreferenceChangedEvent` (on any preference change)

### 3. Audio Capture (`features/audiocapture`)
**Purpose**: Audio recording, memory management, timeline, silence detection

**Models/Events**:
- `StartRecordingEvent` - Request to start recording
- `StopRecordingEvent` - Request to stop recording
- `RecordingStateChangedEvent` - Recording state changed
- `AudioStatsEvent` - Live audio statistics
- `AudioCaptureConfig` - Capture configuration
- `AudioSource` - Audio source types (MIC, DEVICE, BOTH)

**Services**:
- `AudioMemory` - Circular buffer with silence skipping
- `MultiSourceAudioRecorder` - Dual-source recording (mic + device)

**Communication**:
- **Subscribes to**: `StartRecordingEvent`, `StopRecordingEvent`
- **Publishes**: `RecordingStateChangedEvent`, `AudioStatsEvent`

### 4. Audio Processing (`features/audioprocessing`)
**Purpose**: Audio effects, VAD, noise cancellation, normalization, encoding

**Models/Events**:
- `ProcessAudioEvent` - Request audio processing
- `AudioEncoderType` - Encoder types (AAC, OPUS, WAV)

**Services**:
- `AudioEffects` - Normalization and noise cancellation
- `VoiceActivityDetector` - Speech detection
- `OpusEncoder` - Opus encoding stub (future implementation)

**Communication**:
- **Subscribes to**: `ProcessAudioEvent`
- **Publishes**: Processing complete events (TBD)

### 5. Audio Export (`features/audioexport`)
**Purpose**: Export audio files, auto-save, VAD recordings

**Models/Events**:
- `ExportAudioEvent` - Request audio export
- `ExportCompleteEvent` - Export completed
- `AutoSaveEvent` - Scheduled auto-save
- `ActivityRecording` - VAD recording metadata

**Services**:
- `AacMp4Writer` - AAC/MP4 encoding
- `DiskAudioBuffer` - Disk-based audio buffer
- `ActivityRecordingDatabase` - VAD recording database
- `ActivityBlockBuilder` - Timeline block construction

**Communication**:
- **Subscribes to**: `ExportAudioEvent`, `AutoSaveEvent`
- **Publishes**: `ExportCompleteEvent`

### 6. Audio Playback (`features/audioplayback`) - SKELETON
**Purpose**: Audio preview and playback (future feature)

**Models/Events**:
- `PlayAudioEvent` - Request audio playback

**Services**:
- `AudioPlaybackService` - Stub for playback functionality

### 7. Export Management (`features/exportmanagement`) - SKELETON
**Purpose**: Manage export queue and history (future feature)

**Models**:
- `ExportJob` - Export job metadata

**Services**:
- `ExportManagementService` - Export job management

## Integration Points

### SaidItService (To Be Refactored)
The main service should be refactored to:
1. Initialize all feature services
2. Subscribe to relevant events
3. Coordinate feature interactions via events
4. Remove direct feature logic

### SaidItActivity (Shared)
Located in `shared/activities/`:
- Handles permission UI dialogs
- Binds to SaidItService
- Manages MediaProjection permission flow
- Should delegate to PermissionService for logic

## Event Flow Examples

### Starting a Recording
1. User triggers recording → `StartRecordingEvent` published
2. Audio Capture subscribes → starts recording
3. Audio Capture publishes → `RecordingStateChangedEvent`
4. UI updates based on state change event

### Auto-Save
1. AlarmManager fires → `AutoSaveEvent` published
2. Audio Export subscribes → saves audio
3. Audio Export publishes → `ExportCompleteEvent`
4. Notification shown based on complete event

### Permission Grant
1. Permission granted in Activity
2. Activity publishes → `PermissionsGrantedEvent`
3. Features waiting for permissions → can now initialize

## Migration Strategy

### Phase 1: Copy Files ✅
- Copy files to feature folders
- Update package declarations
- Update imports throughout codebase

### Phase 2: Create Event Infrastructure ✅
- Add EventBus dependency
- Create EventBusProvider
- Create event models for each feature

### Phase 3: Refactor Services (IN PROGRESS)
- Wire up event subscriptions
- Replace direct calls with event publishing
- Test each feature independently

### Phase 4: Cleanup
- Delete original files after migration
- Remove unused imports
- Verify no coupling between features

## Benefits of This Architecture

1. **Loose Coupling**: Features don't depend on each other directly
2. **High Cohesion**: Related code is grouped together
3. **Testability**: Each feature can be tested independently
4. **Scalability**: New features can be added without touching existing ones
5. **Maintainability**: Changes to one feature don't affect others
6. **Flexibility**: Features can be enabled/disabled independently

## Dependencies

- **EventBus**: `org.greenrobot:eventbus:3.3.1`
  - Lightweight (~60 KB)
  - In-process communication (no IPC overhead)
  - Thread-safe with thread delivery options
  - No known vulnerabilities

## File Organization Summary

### Before (Flat Structure)
```
eu.mrogalski.saidit/
  SaidItActivity.java
  SaidItService.java
  AudioMemory.java
  AudioEffects.java
  VoiceActivityDetector.java
  AacMp4Writer.java
  ... (26 files)
```

### After (VSA Structure)
```
eu.mrogalski.saidit/
  shared/
    activities/ (SaidItActivity)
    models/ (StorageMode)
    services/ (CrashHandler)
    events/ (EventBusProvider)
  features/
    permissions/ (models, services)
    preferences/ (models, services)
    audiocapture/ (models, services)
    audioprocessing/ (models, services)
    audioexport/ (models, services)
    audioplayback/ (models, services - skeleton)
    exportmanagement/ (models, services - skeleton)
  SaidItService.java (to be refactored)
  ... (other UI components)
```

## Notes

- Original files remain in root package during migration
- Import statements updated to point to new locations
- AndroidManifest updated with new activity location
- Build system updated with EventBus dependency
