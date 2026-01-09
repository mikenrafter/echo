# Potential Refactoring Directions

This document outlines potential refactoring opportunities for future improvements to the Echo codebase. These are suggestions, not immediate action items.

## 1. Storage Backend Abstraction

### Current State
- Direct coupling between SaidItService and storage implementations (AudioMemory, DiskAudioBuffer)
- Mode switching handled via conditional logic in service

### Potential Refactoring
```java
// Create an interface for storage backends
public interface AudioStorageBackend {
    void write(byte[] data, int offset, int length) throws IOException;
    void read(int skipBytes, AudioMemory.Consumer consumer) throws IOException;
    long getTotalBytes();
    void flush() throws IOException;
    void close() throws IOException;
}

// Implementations:
class MemoryStorageBackend implements AudioStorageBackend { /* uses AudioMemory */ }
class DiskStorageBackend implements AudioStorageBackend { /* uses DiskAudioBuffer */ }

// In SaidItService:
private AudioStorageBackend storageBackend;

// Benefits:
// - Easier to add new storage types (cloud, hybrid, etc.)
// - Cleaner service code without mode conditionals
// - Better testability
// - Single responsibility principle
```

### Estimated Effort
- Medium (2-3 days)
- Would require updating SaidItService and adding interface
- Backwards compatible if done carefully

### Priority
- Low - Current implementation works well
- Consider if adding more storage modes

## 2. Activity Recording Storage Migration

### Current State
- ActivityRecordingDatabase uses JSON in SharedPreferences
- Parses entire JSON on every operation
- Could be slow with 100+ recordings

### Potential Refactoring
```java
// Option 1: Add Caching Layer
class ActivityRecordingDatabase {
    private List<ActivityRecording> cache;
    private boolean cacheValid = false;
    
    private List<ActivityRecording> getAllRecordings() {
        if (!cacheValid) {
            cache = loadFromPreferences();
            cacheValid = true;
        }
        return new ArrayList<>(cache);
    }
    
    public long addRecording(ActivityRecording recording) {
        // Update cache immediately
        cacheValid = false; // Or update cache directly
    }
}

// Option 2: Migrate to SQLite
class ActivityRecordingDatabase {
    private SQLiteDatabase db;
    
    // Use Room or direct SQLite
    // Much better performance for 100+ records
    // Better query capabilities
}

// Benefits:
// - Better performance with many recordings
// - More robust data persistence
// - Query capabilities (search, filter, sort)
```

### Estimated Effort
- Option 1 (Caching): Small (1 day)
- Option 2 (SQLite): Medium (3-4 days)

### Priority
- Low currently (JSON works fine for moderate use)
- Increase priority if users report having 50+ recordings

## 3. Intent Handler Separation

### Current State
- BroadcastReceiver handles all intent logic
- Large executeCommand() method with switch statement
- Binding logic tightly coupled

### Potential Refactoring
```java
// Create command pattern
interface IntentCommand {
    void execute(SaidItService service, Intent intent);
}

class StartRecordingCommand implements IntentCommand {
    @Override
    public void execute(SaidItService service, Intent intent) {
        float prepend = validateAndGetPrepend(intent);
        service.startRecording(prepend);
    }
}

class BroadcastReceiver extends android.content.BroadcastReceiver {
    private Map<String, IntentCommand> commands = new HashMap<>();
    
    public BroadcastReceiver() {
        commands.put(ACTION_START_RECORDING, new StartRecordingCommand());
        commands.put(ACTION_STOP_RECORDING, new StopRecordingCommand());
        // etc.
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        IntentCommand command = commands.get(action);
        if (command != null) {
            bindAndExecute(context, () -> command.execute(service, intent));
        }
    }
}

// Benefits:
// - Each command is self-contained and testable
// - Easier to add new commands
// - Better separation of concerns
// - Cleaner code organization
```

### Estimated Effort
- Medium (2-3 days)
- Refactor existing commands
- Add tests for each command

### Priority
- Low - Current implementation is maintainable
- Consider if adding many more intent actions

## 4. Settings UI Modernization

### Current State
- SettingsActivity uses direct view manipulation
- Multiple listener classes
- Some duplication in button highlighting logic

### Potential Refactoring
```java
// Option 1: Use ViewBinding (recommended for new code)
class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Cleaner view access
        binding.customMemoryInput.setText(...);
    }
}

// Option 2: Use ViewModel + LiveData (more complex but better)
class SettingsViewModel extends ViewModel {
    private MutableLiveData<Integer> memorySizeMB = new MutableLiveData<>();
    private MutableLiveData<StorageMode> storageMode = new MutableLiveData<>();
    
    // Expose read-only LiveData to UI
    public LiveData<Integer> getMemorySizeMB() { return memorySizeMB; }
    
    // Handle business logic
    public void setMemorySize(int sizeMB) {
        // Validation, persistence, etc.
        memorySizeMB.setValue(sizeMB);
    }
}

// Benefits:
// - Better lifecycle handling
// - Configuration change survival
// - Cleaner separation of UI and logic
// - Easier testing
```

### Estimated Effort
- Option 1 (ViewBinding): Small (1 day)
- Option 2 (ViewModel): Medium (2-3 days)

### Priority
- Low - Current UI works well
- Consider for major UI overhaul

## 5. Error Handling Consolidation

### Current State
- Error handling scattered throughout code
- Some errors logged, some show toasts, some do both
- Inconsistent error message formatting

### Potential Refactoring
```java
// Create centralized error handler
class ErrorHandler {
    private Context context;
    
    public void handleError(ErrorType type, Throwable error, String context) {
        // Log with consistent format
        Log.e(TAG, context + ": " + type, error);
        
        // Show user feedback if appropriate
        if (type.shouldShowToUser()) {
            showUserMessage(type.getUserMessage(context), type.getSeverity());
        }
        
        // Track metrics (if implemented)
        Analytics.logError(type, error);
    }
}

enum ErrorType {
    DISK_WRITE_FAILED("Failed to write audio to disk", true, Severity.HIGH),
    MEMORY_ALLOCATION_FAILED("Failed to allocate memory", true, Severity.HIGH),
    AUDIO_RECORD_ERROR("Audio recording error", true, Severity.HIGH),
    // etc.
}

// Usage:
try {
    diskAudioBuffer.write(data, offset, length);
} catch (IOException e) {
    errorHandler.handleError(ErrorType.DISK_WRITE_FAILED, e, "filler.consume");
}

// Benefits:
// - Consistent error handling
// - Easier to add error tracking/metrics
// - Better user experience
// - Centralized error policies
```

### Estimated Effort
- Medium (2-3 days)
- Would touch many files
- Need careful testing

### Priority
- Low - Current error handling is adequate
- Consider if adding error metrics/tracking

## 6. Testing Infrastructure

### Current State
- No automated tests
- Manual testing required

### Potential Additions
```java
// Unit tests for core logic
class VoiceActivityDetectorTest {
    @Test
    public void testSilenceDetection() {
        VAD vad = new VoiceActivityDetector(500.0f);
        byte[] silence = generateSilence(1000);
        assertFalse(vad.process(silence, 0, silence.length));
    }
    
    @Test
    public void testSpeechDetection() {
        VAD vad = new VoiceActivityDetector(500.0f);
        byte[] speech = generateTestSpeech(1000);
        // Process enough frames to trigger
        for (int i = 0; i < 5; i++) {
            vad.process(speech, 0, speech.length);
        }
        assertTrue(vad.isActive());
    }
}

// Integration tests for service
@RunWith(AndroidJUnit4.class)
class SaidItServiceTest {
    @Test
    public void testStorageModeSwitch() {
        // Bind to service
        // Switch mode
        // Verify mode changed
        // Verify audio still records
    }
}

// Benefits:
// - Catch regressions early
// - Document expected behavior
// - Enable confident refactoring
// - Faster development iteration
```

### Estimated Effort
- Large (1-2 weeks for comprehensive coverage)
- Ongoing maintenance

### Priority
- Medium - Would be valuable
- Start with unit tests for critical logic
- Add integration tests over time

## 7. Performance Monitoring

### Current State
- No built-in performance metrics
- Difficult to identify bottlenecks

### Potential Addition
```java
class PerformanceMonitor {
    private Map<String, Long> timings = new HashMap<>();
    private Map<String, Integer> counters = new HashMap<>();
    
    public void startTiming(String operation) {
        timings.put(operation, System.nanoTime());
    }
    
    public void endTiming(String operation) {
        long start = timings.get(operation);
        long duration = System.nanoTime() - start;
        Log.d(TAG, operation + " took " + (duration / 1000000) + "ms");
        
        // Could aggregate statistics
        // Could send to analytics
    }
    
    public void incrementCounter(String metric) {
        counters.merge(metric, 1, Integer::sum);
    }
}

// Usage in service:
perfMonitor.startTiming("disk_write");
diskAudioBuffer.write(data, offset, length);
perfMonitor.endTiming("disk_write");

// Benefits:
// - Identify performance bottlenecks
// - Monitor battery impact
// - Optimize based on real usage
// - Track metrics over time
```

### Estimated Effort
- Small to Medium (1-2 days)
- Infrastructure is simple
- Analysis requires ongoing attention

### Priority
- Low currently - No reported performance issues
- Increase if users report problems

## Summary

### High Impact, Low Effort
- Error Handling Consolidation (if adding error tracking)
- Performance Monitoring (if performance concerns arise)

### High Impact, Medium Effort
- Activity Recording Storage Migration (if many recordings)
- Testing Infrastructure (ongoing value)

### Medium Impact, Medium Effort
- Storage Backend Abstraction (if adding storage types)
- Intent Handler Separation (if many more commands)

### Low Impact
- Settings UI Modernization (nice to have)

## Recommendation

**Current State**: Code is well-structured and maintainable as-is.

**When to Refactor**:
1. **Storage Abstraction**: When adding 3rd storage mode
2. **Database Migration**: When users have 50+ activity recordings
3. **Testing**: Start now if resources allow, high value
4. **Error Handling**: When adding analytics or error tracking
5. **Performance Monitoring**: If battery/performance issues reported

**Don't Prematurely Refactor**: Current code works well. Refactor when there's a clear need or when adding related features.
