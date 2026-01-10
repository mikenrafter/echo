# Gradient Quality Recording - Full Implementation Guide

## Overview
Gradient Quality Recording is a memory optimization feature that allocates three separate audio memory rings with different bitrates based on audio age:
- **High Quality (48kHz)**: First 5 minutes of audio
- **Mid Quality (16kHz)**: Next 15 minutes (5-20 minutes old)
- **Low Quality (8kHz)**: Everything older than 20 minutes

## Current Status
✅ **Completed:**
- Settings UI added with checkbox to enable/disable
- Preference keys added to `SaidIt.java`
- Service fields added with TODO comments
- Preference loading on service creation
- Warning log when enabled but not implemented

⚠️ **Not Yet Implemented:**
- Three separate `AudioMemory` instances
- Time-based audio routing logic
- Resampling during export/save
- Duration counter calculations across rings
- State callback updates

## Implementation Requirements

### 1. AudioMemory Ring Management
**Location:** `SaidItService.java`

```java
// Replace single AudioMemory with three instances
final AudioMemory audioMemoryHigh = new AudioMemory(); // 48kHz, 5min capacity
final AudioMemory audioMemoryMid = new AudioMemory();  // 16kHz, 15min capacity
final AudioMemory audioMemoryLow = new AudioMemory();  // 8kHz, rest of memory

// Track recording start time
volatile long recordingStartTimeMillis = 0;
```

### 2. Memory Allocation
**Location:** `innerStartListening()` in `SaidItService.java`

When gradient quality is enabled:
1. Calculate time-based memory splits:
   - High: 5 min × 48kHz × 2 bytes/sample × 60 sec/min = ~28.8 MB
   - Mid: 15 min × 16kHz × 2 bytes/sample × 60 sec/min = ~28.8 MB
   - Low: Remaining memory

2. Allocate each ring:
```java
if (gradientQualityEnabled) {
    audioMemoryHigh.allocate(5 * 60 * 48000 * 2);
    audioMemoryMid.allocate(15 * 60 * 16000 * 2);
    audioMemoryLow.allocate(memorySize - (5*60*48000*2) - (15*60*16000*2));
} else {
    audioMemory.allocate(memorySize);
}
```

### 3. Time-Based Audio Routing
**Location:** `filler` Consumer in `SaidItService.java`

Determine which ring to write to based on elapsed time:

```java
public int consume(final byte[] array, final int offset, final int count) throws IOException {
    if (gradientQualityEnabled) {
        long elapsedMillis = System.currentTimeMillis() - recordingStartTimeMillis;
        int elapsedSeconds = (int)(elapsedMillis / 1000);
        
        if (elapsedSeconds < 300) { // 0-5 minutes: HIGH
            return audioMemoryHigh.fill(filler);
        } else if (elapsedSeconds < 1200) { // 5-20 minutes: MID
            return audioMemoryMid.fill(filler);
        } else { // 20+ minutes: LOW
            return audioMemoryLow.fill(filler);
        }
    } else {
        return audioMemory.fill(filler);
    }
}
```

**Note:** You'll need to handle ring overflow - when high ring fills, oldest audio moves to mid, etc.

### 4. Duration Counter Updates
**Location:** `getState()` callback in `SaidItService.java`

Calculate total duration across all three rings:

```java
if (gradientQualityEnabled) {
    AudioMemory.Stats statsHigh = audioMemoryHigh.getStats(48000 * 2);
    AudioMemory.Stats statsMid = audioMemoryMid.getStats(16000 * 2);
    AudioMemory.Stats statsLow = audioMemoryLow.getStats(8000 * 2);
    
    float durationHigh = statsHigh.filled / (48000.0f * 2);
    float durationMid = statsMid.filled / (16000.0f * 2);
    float durationLow = statsLow.filled / (8000.0f * 2);
    
    float totalDuration = durationHigh + durationMid + durationLow;
    float totalCapacity = statsHigh.total / (48000.0f * 2) + 
                         statsMid.total / (16000.0f * 2) + 
                         statsLow.total / (8000.0f * 2);
    
    // Update callback with combined durations
}
```

### 5. Export/Save Logic with Resampling
**Location:** `dumpRecordingRange()` in `SaidItService.java`

When exporting, you need to:
1. Determine which rings contain the requested time range
2. Read audio from each ring
3. Resample mid/low quality audio to match high quality sample rate
4. Concatenate the resampled audio
5. Write to output file

**Resampling Options:**
- Use Android's `MediaCodec` for resampling
- Implement simple linear interpolation
- Use a library like `libsamplerate` (requires JNI)

**Example pseudocode:**
```java
// Read from high ring (newest 5 min)
byte[] highQualityData = readFromRing(audioMemoryHigh, ...);

// Read from mid ring (next 15 min)
byte[] midQualityData = readFromRing(audioMemoryMid, ...);
byte[] midResampled = resample(midQualityData, 16000, 48000);

// Read from low ring (oldest audio)
byte[] lowQualityData = readFromRing(audioMemoryLow, ...);
byte[] lowResampled = resample(lowQualityData, 8000, 48000);

// Concatenate and write
wavFileWriter.write(highQualityData);
wavFileWriter.write(midResampled);
wavFileWriter.write(lowResampled);
```

### 6. Ring Overflow Handling
**Location:** `AudioMemory.fill()` or custom ring manager

When high ring is full:
- Resample oldest chunk from high → mid quality
- Move to mid ring
- Clear space in high ring

When mid ring is full:
- Resample oldest chunk from mid → low quality
- Move to low ring
- Clear space in mid ring

When low ring is full:
- Oldest audio is discarded (normal overflow behavior)

## Testing Checklist
- [ ] Enable gradient quality in settings
- [ ] Record for 25+ minutes to fill all rings
- [ ] Verify audio transitions smoothly between quality levels
- [ ] Export clips from each quality tier
- [ ] Verify exported audio plays correctly
- [ ] Test ring overflow behavior
- [ ] Measure memory usage matches expectations
- [ ] Test with silence skipping enabled
- [ ] Test with VAD recording enabled
- [ ] Test service stop/restart preserves ring state

## Performance Considerations
1. **Resampling CPU cost**: Resampling during save will increase CPU usage. Consider async processing.
2. **Memory overhead**: Three rings require more bookkeeping than one.
3. **GC pressure**: More allocations during ring transitions.
4. **Complexity**: Significantly more complex than single-ring approach.

## Alternative Approaches
1. **Variable bitrate within single ring**: Adjust sample rate dynamically instead of using separate rings. Simpler but requires per-chunk metadata.
2. **Time-based disk batching**: Write old audio to disk at lower quality instead of keeping in memory.
3. **Compression instead of lower sample rate**: Use audio compression (e.g., Opus) for older audio instead of reducing sample rate.

## Estimated Effort
- **Core implementation**: 16-24 hours
- **Resampling library integration**: 4-8 hours
- **Testing and debugging**: 8-16 hours
- **Total**: ~3-5 days of focused development

## Related Files
- `SaidIt.java`: Preference keys
- `SaidItService.java`: Main service logic
- `AudioMemory.java`: Ring buffer implementation
- `SettingsActivity.java`: Settings UI
- `activity_settings.xml`: Layout
- `strings.xml`: UI strings
