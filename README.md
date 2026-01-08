# Echo

Time travelling recorder for Android.
It is free/libre and gratis software.

## Download

* [F-Droid](https://f-droid.org/repository/browse/?fdid=eu.mrogalski.saidit)

## Features

### Core Recording
- **Circular Buffer Recording**: Continuously records audio in memory, allowing you to save the past
- **Time Travel**: Save recordings from minutes or hours ago with a single tap
- **Background Service**: Records even when the app is in background or screen is off

### New Features (v2.1.0+)

#### 1. Configurable Memory Management
- Specify exact memory size in megabytes instead of vague presets
- Minimum 10 MB, maximum depends on device
- Real-time memory usage display

#### 2. Storage Modes
- **Memory Only**: Audio stored only in RAM (default, traditional mode)
- **Batch to Disk**: Automatically writes audio to disk in batches with circular buffer
- Configurable maximum disk usage (default 500 MB)
- Automatic cleanup of old files when disk limit reached

#### 3. Intent-Based Automation
Full automation API for integration with Tasker, Automate, and other automation apps:
- Start/stop recording with configurable prepend time
- Enable/disable listening mode
- Switch storage modes programmatically
- Configure memory size via intent
- Dump recording without continuous recording

See [INTENT_API.md](INTENT_API.md) for complete documentation and examples.

#### 4. Audio Activity Detection (Partial Implementation)
Infrastructure for detecting and recording audio activity:
- Voice Activity Detection (VAD) using amplitude-based detection
- Automatic recording when audio activity detected
- 5-minute pre/post activity buffer capture
- Auto-deletion with flag protection
- Database for managing activity recordings

*Note: UI and full integration are still in progress*

### Coming Soon
- Multi-quality encoding (high bitrate for recent audio, low bitrate for older)
- Dual-channel audio capture (microphone + device audio)
- Complete activity detection UI
- And more!

## Architecture

**SaidItFragment** - The main view of the app.

**SaidItService** - Manages a high priority thread that records audio. The thread is a state machine that can be accessed by sending it tasks using Android's Handler (`audioHandler`).

**AudioMemory** - (not thread-safe) Manages the in-memory ring buffer of audio chunks.

**DiskAudioBuffer** - Manages circular file buffer on disk for batch-to-disk mode.

**VoiceActivityDetector** - Detects voice/audio activity using RMS energy analysis.

**ActivityRecordingDatabase** - Stores metadata for activity-triggered recordings.

## Documentation

- [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) - Detailed feature descriptions and implementation approach
- [TODO.md](TODO.md) - Granular task breakdown for ongoing development
- [INTENT_API.md](INTENT_API.md) - Complete automation API documentation

## Building

```bash
./gradlew :SaidIt:assembleDebug
```

The APK will be in `SaidIt/build/outputs/apk/debug/`

## Requirements

- Android 10+ (API level 30+)
- Microphone permission
- Storage permission (for saving recordings)
- Foreground service permission

## Contributing

Contributions are welcome! Please see the TODO.md for a list of planned features and tasks.

## License

See LICENSE.txt for details.
