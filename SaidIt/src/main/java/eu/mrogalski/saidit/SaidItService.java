package eu.mrogalski.saidit;
import eu.mrogalski.saidit.shared.models.SilenceGroup;
import eu.mrogalski.saidit.shared.models.StorageMode;
import eu.mrogalski.saidit.shared.models.TimelineSegment;
import eu.mrogalski.saidit.features.audiocapture.services.AudioMemory;
import eu.mrogalski.saidit.features.audioexport.models.ActivityRecording;
import eu.mrogalski.saidit.features.audioexport.services.AacMp4Writer;
import eu.mrogalski.saidit.features.audioexport.services.ActivityBlockBuilder;
import eu.mrogalski.saidit.features.audioexport.services.ActivityRecordingDatabase;
import eu.mrogalski.saidit.features.audioexport.services.DiskAudioBuffer;
import eu.mrogalski.saidit.features.audioprocessing.services.AudioEffects;
import eu.mrogalski.saidit.features.audioprocessing.services.VoiceActivityDetector;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import simplesound.pcm.WavAudioFormat;
import simplesound.pcm.WavFileWriter;
import static eu.mrogalski.saidit.SaidIt.*;

public class SaidItService extends Service {
    static final String TAG = SaidItService.class.getSimpleName();
    private static final int FOREGROUND_NOTIFICATION_ID = 458;
    private static final String YOUR_NOTIFICATION_CHANNEL_ID = "SaidItServiceChannel";
    private static final String ACTION_AUTO_SAVE = "eu.mrogalski.saidit.ACTION_AUTO_SAVE";

    volatile int SAMPLE_RATE;
    volatile int FILL_RATE;


    File wavFile;
    AudioRecord audioRecord; // used only in the audio thread
    AudioRecord deviceAudioRecord; // second AudioRecord for dual-source mode (uses MediaProjection)
    MediaProjection mediaProjection; // For capturing device audio via AudioPlaybackCapture API
    MediaProjectionManager mediaProjectionManager; // For creating MediaProjection in service context
    WavFileWriter wavFileWriter; // used only in the audio thread
    
    // Gradient quality: three separate memory rings for different quality tiers
    final AudioMemory audioMemory = new AudioMemory(); // used when gradient quality is OFF
    final AudioMemory audioMemoryHigh = new AudioMemory(); // High quality ring (first 5 minutes)
    final AudioMemory audioMemoryMid = new AudioMemory();  // Mid quality ring (next 15 minutes)
    final AudioMemory audioMemoryLow = new AudioMemory();  // Low quality ring (rest)
    volatile long recordingStartTimeMillis = 0; // Track when recording started for gradient routing
    
    DiskAudioBuffer diskAudioBuffer; // used only in the audio thread
    volatile StorageMode storageMode = StorageMode.MEMORY_ONLY;
    volatile boolean recordDeviceAudio = false; // If true, use REMOTE_SUBMIX instead of MIC
    volatile boolean dualSourceRecording = false; // If true, record both MIC and REMOTE_SUBMIX simultaneously
    volatile int micChannelMode = 0; // 0=mono, 1=stereo
    volatile int deviceChannelMode = 0; // 0=mono, 1=stereo
    
    // Voice Activity Detection (VAD) - Records to Echo/VAD subfolder
    volatile boolean activityDetectionEnabled = false;
    VoiceActivityDetector voiceActivityDetector;
    ActivityRecordingDatabase activityRecordingDatabase;
    boolean isRecordingActivity = false;
    long activityStartTime = 0;
    long lastActivityTime = 0;
    WavFileWriter activityWavFileWriter;
    File activityWavFile;
    float activityDetectionThreshold = 500.0f;
    int activityPreBufferSeconds = 300;
    int activityPostBufferSeconds = 300;
    int activityAutoDeleteDays = 7; // Auto-delete VAD files after this many days
    boolean activityHighBitrate = false;
    
    // Auto-save - Records to Echo/AutoSave subfolder
    private PendingIntent autoSavePendingIntent;
    private PendingIntent autoSaveCleanupPendingIntent;
    int autoSaveAutoDeleteDays = 7; // Auto-delete auto-save files after this many days
    
    // Gradient quality recording - allocates 3 memory rings for different quality tiers
    volatile boolean gradientQualityEnabled = false;
    volatile int gradientQualityHighRate = 48000;
    volatile int gradientQualityMidRate = 16000;
    volatile int gradientQualityLowRate = 8000;

    HandlerThread audioThread;
    Handler audioHandler; // used to post messages to audio thread

    // Live stats
    volatile int currentVolumeLevel = 0; // 0-32767
    volatile int silenceThreshold = 500; // Threshold for activity detection

    // Silence groups tracking (service-level data models)
    // Moved to shared.models.SilenceGroup
    /*
    static class SilenceGroup {
        public long endTimeMillis;
        public long durationMillis;
        public SilenceGroup(long endTimeMillis, long durationMillis) {
            this.endTimeMillis = endTimeMillis;
            this.durationMillis = durationMillis;
        }
    }
    */

    // Timeline segment tracking for activity/silence display
    // Moved to shared.models.TimelineSegment
    /*
    public static class TimelineSegment {
        public enum Type { ACTIVITY, SILENCE }
        public Type type;
        public long startTimeMillis;
        public long endTimeMillis; // 0 if still ongoing
        public int durationSeconds;
        
        public TimelineSegment(Type type, long startTimeMillis) {
            this.type = type;
            this.startTimeMillis = startTimeMillis;
            this.endTimeMillis = 0;
            this.durationSeconds = 0;
        }
        
        public void end(long endTimeMillis) {
            this.endTimeMillis = endTimeMillis;
            this.durationSeconds = (int)((endTimeMillis - startTimeMillis) / 1000);
        }
        
        public int getCurrentDuration() {
            if (endTimeMillis == 0) {
                return (int)((System.currentTimeMillis() - startTimeMillis) / 1000);
            }
            return durationSeconds;
        }
        
        public boolean isOngoing() {
            return endTimeMillis == 0;
        }
    }
    */
    
    // Timeline tracking variables
    private final java.util.List<TimelineSegment> timelineSegments = new java.util.ArrayList<>();
    private TimelineSegment currentSegment = null;
    private volatile int blockSizeMinutes = 5; // Default block size
    private volatile int maxTimelineSegments = 50; // Keep last 50 segments max

    @Override
    public void onCreate() {

        Log.d(TAG, "Reading native sample rate");

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        SAMPLE_RATE = preferences.getInt(SAMPLE_RATE_KEY, AudioTrack.getNativeOutputSampleRate (AudioManager.STREAM_MUSIC));
        Log.d(TAG, "Sample rate: " + SAMPLE_RATE);
        FILL_RATE = 2 * SAMPLE_RATE;
        
        // Load storage mode
        String modeStr = preferences.getString(STORAGE_MODE_KEY, StorageMode.MEMORY_ONLY.name());
        try {
            storageMode = StorageMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            storageMode = StorageMode.MEMORY_ONLY;
        }
        Log.d(TAG, "Storage mode: " + storageMode);
        
        // Load and configure silence skipping
        boolean silenceSkipEnabled = preferences.getBoolean(SILENCE_SKIP_ENABLED_KEY, false);
        silenceThreshold = preferences.getInt(SILENCE_THRESHOLD_KEY, 500);
        int silenceSegmentCount = preferences.getInt(SILENCE_SEGMENT_COUNT_KEY, 3);
        audioMemory.configureSilenceSkipping(silenceSkipEnabled, silenceThreshold, silenceSegmentCount);
        Log.d(TAG, "Silence skipping: enabled=" + silenceSkipEnabled + ", threshold=" + silenceThreshold + ", segmentCount=" + silenceSegmentCount);
        
        // Initialize Voice Activity Detection (VAD)
        activityDetectionEnabled = preferences.getBoolean(ACTIVITY_DETECTION_ENABLED_KEY, false);
        activityDetectionThreshold = preferences.getFloat(ACTIVITY_DETECTION_THRESHOLD_KEY, 500.0f);
        activityPreBufferSeconds = preferences.getInt(ACTIVITY_PRE_BUFFER_SECONDS_KEY, 300);
        activityPostBufferSeconds = preferences.getInt(ACTIVITY_POST_BUFFER_SECONDS_KEY, 300);
        activityAutoDeleteDays = preferences.getInt(ACTIVITY_AUTO_DELETE_DAYS_KEY, 7);
        activityHighBitrate = preferences.getBoolean(ACTIVITY_HIGH_BITRATE_KEY, false);
        if (activityDetectionEnabled) {
            voiceActivityDetector = new VoiceActivityDetector(activityDetectionThreshold);
            activityRecordingDatabase = new ActivityRecordingDatabase(this);
            Log.d(TAG, "VAD (Voice Activity Detection) enabled with threshold: " + activityDetectionThreshold);
        }
        
        // Initialize auto-save auto-delete configuration
        autoSaveAutoDeleteDays = preferences.getInt(AUTO_SAVE_AUTO_DELETE_DAYS_KEY, 7);
        
        // Load gradient quality preferences
        gradientQualityEnabled = preferences.getBoolean(GRADIENT_QUALITY_ENABLED_KEY, false);
        gradientQualityHighRate = preferences.getInt(GRADIENT_QUALITY_HIGH_RATE_KEY, 48000);
        gradientQualityMidRate = preferences.getInt(GRADIENT_QUALITY_MID_RATE_KEY, 16000);
        gradientQualityLowRate = preferences.getInt(GRADIENT_QUALITY_LOW_RATE_KEY, 8000);
        Log.d(TAG, "Gradient quality: enabled=" + gradientQualityEnabled + 
            ", rates=" + gradientQualityHighRate + "/" + gradientQualityMidRate + "/" + gradientQualityLowRate);
        
        // Initialize MediaProjectionManager for creating MediaProjection in service context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
        
        // Load device audio recording preference
        recordDeviceAudio = preferences.getBoolean(RECORD_DEVICE_AUDIO_KEY, false);
        Log.d(TAG, "Device audio recording: " + recordDeviceAudio);
        
        // Load dual-source recording preferences
        dualSourceRecording = preferences.getBoolean(DUAL_SOURCE_RECORDING_KEY, false);
        micChannelMode = preferences.getInt(MIC_CHANNEL_MODE_KEY, 0);
        deviceChannelMode = preferences.getInt(DEVICE_CHANNEL_MODE_KEY, 0);
        Log.d(TAG, "Dual-source recording: " + dualSourceRecording + ", micMode=" + micChannelMode + ", deviceMode=" + deviceChannelMode);

        audioThread = new HandlerThread("audioThread", Thread.MAX_PRIORITY);
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());

        if(preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            innerStartListening();
        }

    }

    @Override
    public void onDestroy() {
        stopRecording(null, "");
        innerStopListening();
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new BackgroundRecorderBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    public void enableListening() {
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE)
                .edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, true).commit();

        innerStartListening();
    }

    public void disableListening() {
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE)
                .edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, false).commit();

        innerStopListening();
    }

    int state;

    static final int STATE_READY = 0;
    static final int STATE_LISTENING = 1;
    static final int STATE_RECORDING = 2;

    private void innerStartListening() {
        switch(state) {
            case STATE_READY:
                break;
            case STATE_LISTENING:
            case STATE_RECORDING:
                return;
        }
        state = STATE_LISTENING;

        Log.d(TAG, "Queueing: START LISTENING");

        startService(new Intent(this, this.getClass()));

        final long memorySize = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE).getLong(AUDIO_MEMORY_SIZE_KEY, Runtime.getRuntime().maxMemory() / 4);

        audioHandler.post(new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                Log.d(TAG, "Executing: START LISTENING");
                Log.d(TAG, "Audio: INITIALIZING AUDIO_RECORD");

                if (dualSourceRecording) {
                    // Dual-source mode: Initialize both MIC and device audio (via AudioPlaybackCapture)
                    Log.d(TAG, "Initializing DUAL-SOURCE recording (MIC + Device Audio via MediaProjection)");
                    
                    // Microphone
                    audioRecord = createMicrophoneAudioRecord(micChannelMode);
                    
                    if(audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "Audio: MIC INITIALIZATION ERROR - releasing resources");
                        audioRecord.release();
                        audioRecord = null;
                        state = STATE_READY;
                        return;
                    }
                    
                    // Device audio via AudioPlaybackCapture (requires MediaProjection)
                    if (mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            AudioFormat audioFormat = createAudioFormat(deviceChannelMode);
                            AudioPlaybackCaptureConfiguration config = createAudioPlaybackCaptureConfig();
                            
                            if (config != null) {
                                deviceAudioRecord = new AudioRecord.Builder()
                                    .setAudioFormat(audioFormat)
                                    .setBufferSizeInBytes(AudioMemory.CHUNK_SIZE)
                                    .setAudioPlaybackCaptureConfig(config)
                                    .build();
                                
                                if(deviceAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                                    Log.e(TAG, "Audio: AudioPlaybackCapture INITIALIZATION ERROR - falling back to single source");
                                    deviceAudioRecord.release();
                                    deviceAudioRecord = null;
                                    // Continue with just microphone
                                } else {
                                    Log.d(TAG, "AudioPlaybackCapture initialized successfully");
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error initializing AudioPlaybackCapture: " + e.getMessage(), e);
                            if (deviceAudioRecord != null) {
                                deviceAudioRecord.release();
                                deviceAudioRecord = null;
                            }
                        }
                    } else {
                        Log.w(TAG, "MediaProjection not available or Android version < 10 (API 29) - device audio capture not supported");
                    }
                    
                    Log.d(TAG, "Dual-source initialized: MIC=" + (audioRecord != null) + ", DEVICE=" + (deviceAudioRecord != null));
                } else {
                    // Single-source mode
                    if (recordDeviceAudio && mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Device audio only via AudioPlaybackCapture
                        Log.d(TAG, "Using audio source: Device Audio (via AudioPlaybackCapture)");
                        
                        try {
                            AudioFormat audioFormat = createAudioFormat(0); // mono for single source
                            AudioPlaybackCaptureConfiguration config = createAudioPlaybackCaptureConfig();
                            
                            if (config != null) {
                                audioRecord = new AudioRecord.Builder()
                                    .setAudioFormat(audioFormat)
                                    .setBufferSizeInBytes(AudioMemory.CHUNK_SIZE)
                                    .setAudioPlaybackCaptureConfig(config)
                                    .build();
                                
                                if(audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                                    Log.e(TAG, "Audio: AudioPlaybackCapture INITIALIZATION ERROR - falling back to microphone");
                                    audioRecord.release();
                                    audioRecord = null;
                                    
                                    // Fallback to microphone
                                    audioRecord = createMicrophoneAudioRecord(0);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error initializing AudioPlaybackCapture: " + e.getMessage(), e);
                            
                            // Fallback to microphone
                            audioRecord = createMicrophoneAudioRecord(0);
                        }
                    } else {
                        // Microphone only
                        Log.d(TAG, "Using audio source: MIC");
                        
                        audioRecord = createMicrophoneAudioRecord(0);
                    }

                    if(audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "Audio: INITIALIZATION ERROR - releasing resources");
                        audioRecord.release();
                        audioRecord = null;
                        state = STATE_READY;
                        return;
                    }
                }

                Log.d(TAG, "Audio: STARTING AudioRecord");
                
                // Allocate memory rings based on gradient quality mode
                if (gradientQualityEnabled) {
                    // Calculate memory allocation for each tier
                    // High: 5 min × sampleRate × 2 bytes/sample × 60 sec/min
                    long highBytes = 5L * 60L * gradientQualityHighRate * 2L;
                    // Mid: 15 min × sampleRate × 2 bytes/sample × 60 sec/min
                    long midBytes = 15L * 60L * gradientQualityMidRate * 2L;
                    // Low: remaining memory
                    long lowBytes = Math.max(0, memorySize - highBytes - midBytes);
                    
                    audioMemoryHigh.allocate(highBytes);
                    audioMemoryMid.allocate(midBytes);
                    audioMemoryLow.allocate(lowBytes);
                    
                    recordingStartTimeMillis = System.currentTimeMillis();
                    
                    Log.d(TAG, "Gradient quality allocated: high=" + (highBytes/1024/1024) + "MB@" + gradientQualityHighRate + 
                        "Hz, mid=" + (midBytes/1024/1024) + "MB@" + gradientQualityMidRate + 
                        "Hz, low=" + (lowBytes/1024/1024) + "MB@" + gradientQualityLowRate + "Hz");
                } else {
                    audioMemory.allocate(memorySize);
                    Log.d(TAG, "Single ring allocated: " + (memorySize/1024/1024) + "MB@" + SAMPLE_RATE + "Hz");
                }
                
                // Initialize disk buffer if in BATCH_TO_DISK mode
                if (storageMode == StorageMode.BATCH_TO_DISK) {
                    initializeDiskBuffer();
                }

                Log.d(TAG, "Audio: STARTING AudioRecord");
                audioRecord.startRecording();
                if (deviceAudioRecord != null) {
                    deviceAudioRecord.startRecording();
                }
                audioHandler.post(audioReader);
            }
        });
        
        // Schedule auto-save if enabled
        scheduleAutoSave();
        scheduleAutoSaveCleanup();


    }

    private void innerStopListening() {
        switch(state) {
            case STATE_READY:
            case STATE_RECORDING:
                return;
            case STATE_LISTENING:
                break;
        }
        state = STATE_READY;
        Log.d(TAG, "Queueing: STOP LISTENING");

        stopForeground(true);
        stopService(new Intent(this, this.getClass()));
        
        // Cancel auto-save when stopping
        cancelAutoSave();
        cancelAutoSaveCleanup();

        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Executing: STOP LISTENING");
                // Ensure any ongoing VAD recording is properly finalized to avoid corruption
                if (isRecordingActivity) {
                    try {
                        stopActivityRecording();
                    } catch (Throwable t) {
                        Log.e(TAG, "Error while closing VAD recording on stop", t);
                    }
                }
                if(audioRecord != null)
                    audioRecord.release();
                if(deviceAudioRecord != null) {
                    deviceAudioRecord.release();
                    deviceAudioRecord = null;
                }
                // Note: MediaProjection lifecycle is managed by the activity that created it.
                // The activity is responsible for stopping it when appropriate.
                // If the activity doesn't clean up properly, MediaProjection may leak resources
                // and continue consuming system resources. Consider implementing a timeout or
                // callback mechanism to detect and handle abandoned MediaProjection instances.
                audioHandler.removeCallbacks(audioReader);
                
                // Deallocate memory rings
                if (gradientQualityEnabled) {
                    audioMemoryHigh.allocate(0);
                    audioMemoryMid.allocate(0);
                    audioMemoryLow.allocate(0);
                } else {
                    audioMemory.allocate(0);
                }
                
                // Cleanup disk buffer
                cleanupDiskBuffer();
            }
        });

    }

    public void dumpRecording(final float memorySeconds, final WavFileReceiver wavFileReceiver, String newFileName) {
        // Backward-compatible: export last "memorySeconds" ending at now
        dumpRecordingRange(memorySeconds, 0.0f, wavFileReceiver, newFileName);
    }


    /**
     * Export a recording from a specified time range.
     * startSecondsAgo: the FROM time (seconds ago)
     * endSecondsAgo: the TO time (seconds ago), 0 means now
     */
    public void dumpRecordingRange(final float startSecondsAgo, final float endSecondsAgo,
                                   final WavFileReceiver wavFileReceiver, final String newFileName) {
        if(state != STATE_LISTENING) throw new IllegalStateException("Not listening!");

        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                flushAudioRecord();

                // Sanitize inputs
                float startSec = Math.max(0f, startSecondsAgo);
                float endSec = Math.max(0f, Math.min(startSec, endSecondsAgo)); // end <= start

                int bytesAvailable;
                boolean useDisk = (storageMode == StorageMode.BATCH_TO_DISK && diskAudioBuffer != null);
                if (useDisk) {
                    bytesAvailable = (int) diskAudioBuffer.getTotalBytes();
                    Log.d(TAG, "Dumping range from disk buffer: " + bytesAvailable + " bytes");
                } else {
                    bytesAvailable = audioMemory.countFilled();
                    Log.d(TAG, "Dumping range from memory buffer: " + bytesAvailable + " bytes");
                }

                int startOffsetBytes = (int)(startSec * FILL_RATE);
                int endOffsetBytes = (int)(endSec * FILL_RATE);

                int startPos = Math.max(0, bytesAvailable - startOffsetBytes);
                int endPos = Math.max(0, bytesAvailable - endOffsetBytes);
                if (endPos < startPos) {
                    // Swap if inputs were reversed
                    int tmp = startPos;
                    startPos = endPos;
                    endPos = tmp;
                }
                int skipBytes = startPos;
                int bytesToWrite = Math.max(0, Math.min(bytesAvailable - skipBytes, endPos - startPos));

                // Build filename based on end time
                long millis  = System.currentTimeMillis() - (long)(1000L * (endOffsetBytes) / FILL_RATE);
                final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
                final String dateTime = DateUtils.formatDateTime(SaidItService.this, millis, flags);
                String filename = "Echo - " + dateTime + ".wav";
                if(newFileName != null && !newFileName.equals("")){
                    filename = newFileName + ".wav";
                }

                // Auto-save exports go to Echo/AutoSave; manual exports go to Echo/
                // If caller provided a name, treat as manual export
                File storageDir;
                boolean isManualExport = newFileName != null && !newFileName.isEmpty();
                if(isExternalStorageWritable()){
                    storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                            isManualExport ? "Echo" : "Echo/AutoSave");
                }else{
                    storageDir = new File(getFilesDir(), isManualExport ? "Echo" : "Echo/AutoSave");
                }

                if(!storageDir.exists()){
                    storageDir.mkdirs();
                }
                File file = new File(storageDir, filename);

                // Create the file if it doesn't exist
                if (!file.exists()) {
                    try {
                        if (!file.createNewFile()) {
                            throw new IOException("Failed to create file");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        showToast(getString(R.string.cant_create_file) + file.getAbsolutePath());
                        return;
                    }
                }

                // Read export effect preferences
                final SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
                final boolean normalizeEnabled = prefs.getBoolean(EXPORT_AUTO_NORMALIZE_ENABLED_KEY, false);
                final boolean noiseSuppressionEnabled = prefs.getBoolean(EXPORT_NOISE_SUPPRESSION_ENABLED_KEY, false);
                final int noiseThreshold = prefs.getInt(EXPORT_NOISE_THRESHOLD_KEY, 500);

                final WavAudioFormat format = new WavAudioFormat.Builder().sampleRate(SAMPLE_RATE).build();
                try (WavFileWriter writer = new WavFileWriter(format, file)) {
                    try {
                        try {
                            // First pass: collect the range into memory for optional normalization
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(bytesToWrite);
                            final int totalTarget = bytesToWrite;
                            final int[] written = new int[]{0};

                            AudioMemory.Consumer consumer = new AudioMemory.Consumer() {
                                @Override
                                public int consume(byte[] array, int offset, int count) throws IOException {
                                    int remaining = totalTarget - written[0];
                                    if (remaining <= 0) return 0;
                                    int toCopy = Math.min(count, remaining);
                                    baos.write(array, offset, toCopy);
                                    written[0] += toCopy;
                                    return 0;
                                }
                            };

                            if (useDisk) {
                                diskAudioBuffer.read(skipBytes, consumer);
                            } else {
                                audioMemory.read(skipBytes, consumer);
                            }

                            byte[] output = baos.toByteArray();
                            // Apply effects in order: noise suppression -> normalization
                            if (noiseSuppressionEnabled) {
                                output = AudioEffects.applyNoiseSuppression(output, noiseThreshold);
                            }
                            if (normalizeEnabled) {
                                output = AudioEffects.applyAutoNormalization(output);
                            }

                            writer.write(output);

                            if (wavFileReceiver != null) {
                                wavFileReceiver.fileReady(file, writer.getTotalSampleBytesWritten() * getBytesToSeconds());
                            }
                        } catch (OutOfMemoryError oomError) {
                            // OOM at line 457 - try memory-efficient fallback
                            Log.e(TAG, "OutOfMemoryError during export, attempting memory-efficient fallback", oomError);
                            CrashHandler.writeCrashLog(SaidItService.this, "OOM during export at line 457", oomError);
                            
                            try {
                                // Memory-efficient approach: stream directly to disk without loading into memory
                                exportMemoryEfficient(writer, skipBytes, bytesToWrite, useDisk);
                                
                                if (wavFileReceiver != null) {
                                    wavFileReceiver.fileReady(file, writer.getTotalSampleBytesWritten() * getBytesToSeconds());
                                }
                                
                                showToast(getString(R.string.export_completed_memory_efficient));
                            } catch (Exception fallbackError) {
                                Log.e(TAG, "Memory-efficient fallback also failed", fallbackError);
                                CrashHandler.writeCrashLog(SaidItService.this, "Memory-efficient export fallback failed", fallbackError);
                                showToast(getString(R.string.export_failed_oom));
                                throw fallbackError;
                            }
                        }
                    } catch (IOException e) {
                        showToast(getString(R.string.error_during_writing_history_into) + file.getAbsolutePath());
                        Log.e(TAG, "Error during writing history into " + file.getAbsolutePath(), e);
                        CrashHandler.writeCrashLog(SaidItService.this, "IOException during export", e);
                    } catch (Exception e) {
                        showToast(getString(R.string.error_during_writing_history_into) + file.getAbsolutePath());
                        Log.e(TAG, "Unexpected error during export: " + file.getAbsolutePath(), e);
                        CrashHandler.writeCrashLog(SaidItService.this, "Unexpected error during export", e);
                    }
                } catch (IOException e) {
                    showToast(getString(R.string.cant_create_file) + file.getAbsolutePath());
                    Log.e(TAG, "Can't create file " + file.getAbsolutePath(), e);
                    CrashHandler.writeCrashLog(SaidItService.this, "Failed to create export file", e);
                }
            }
        });
    }

    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
    private void showToast(String message) {
        Toast.makeText(SaidItService.this, message, Toast.LENGTH_LONG).show();
    }

    public void startRecording(final float prependedMemorySeconds) {
        // Always re-acquire MediaProjection when starting a recording that needs it.
        // This ensures we have a fresh token and avoids crashes from stale ones.
        if (isMediaProjectionRequired()) {
            Log.d(TAG, "MediaProjection is required. Invalidating old one and requesting new permission.");
            this.mediaProjection = null; // Invalidate any existing projection

            if (mediaProjectionRequestCallback != null) {
                mediaProjectionRequestCallback.onRequestMediaProjection();
                // Stop here. The recording will be re-initiated by the activity
                // in onActivityResult after the user grants permission.
                return;
            } else {
                Log.w(TAG, "MediaProjection required, but no callback is set to request permission.");
                // Fallback or error toast could be here
                return;
            }
        }

        switch(state) {
            case STATE_READY:
                innerStartListening();
                break;
            case STATE_LISTENING:
                break;
            case STATE_RECORDING:
                return;
        }
        state = STATE_RECORDING;

        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                flushAudioRecord();
                int prependBytes = (int)(prependedMemorySeconds * FILL_RATE);
                int bytesAvailable = audioMemory.countFilled();

                int skipBytes = Math.max(0, bytesAvailable - prependBytes);

                int useBytes = bytesAvailable - skipBytes;
                long millis  = System.currentTimeMillis() - 1000 * useBytes / FILL_RATE;
                final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
                final String dateTime = DateUtils.formatDateTime(SaidItService.this, millis, flags);
                String filename = "Echo - " + dateTime + ".wav";

                File storageDir;
                if(isExternalStorageWritable()){
                    // Use public storage directory for Android 11+ (min SDK 30)
                    storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Echo");
                }else{
                    storageDir = new File(getFilesDir(), "Echo");
                }
                final String storagePath = storageDir.getAbsolutePath();

                String path = storagePath + "/" + filename;

                wavFile = new File(path);
                try {
                    wavFile.createNewFile();
                } catch (IOException e) {
                    filename = filename.replace(':', '.');
                    path = storagePath + "/" + filename;
                    wavFile = new File(path);
                }
                WavAudioFormat format = new WavAudioFormat.Builder().sampleRate(SAMPLE_RATE).build();
                try {
                    wavFileWriter = new WavFileWriter(format, wavFile);
                } catch (IOException e) {
                    final String errorMessage = getString(R.string.cant_create_file) + path;
                    Toast.makeText(SaidItService.this, errorMessage, Toast.LENGTH_LONG).show();
                    Log.e(TAG, errorMessage, e);
                    return;
                }

                final String finalPath = path;

                if(skipBytes < bytesAvailable) {
                    try {
                        audioMemory.read(skipBytes, new AudioMemory.Consumer() {
                            @Override
                            public int consume(byte[] array, int offset, int count) throws IOException {
                                wavFileWriter.write(array, offset, count);
                                return 0;
                            }
                        });
                    } catch (IOException e) {
                        final String errorMessage = getString(R.string.error_during_writing_history_into) + finalPath;
                        Toast.makeText(SaidItService.this, errorMessage, Toast.LENGTH_LONG).show();
                        Log.e(TAG, errorMessage, e);
                        stopRecording(new SaidItFragment.NotifyFileReceiver(SaidItService.this), "");
                    }
                }
            }
        });

    }

    public long getMemorySize() {
        return audioMemory.getAllocatedMemorySize();
    }

    public void setMemorySize(final long memorySize) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putLong(AUDIO_MEMORY_SIZE_KEY, memorySize).commit();

        if(preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            audioHandler.post(new Runnable() {
                @Override
                public void run() {
                    audioMemory.allocate(memorySize);
                }
            });
        }
    }

    public int getMemorySizeMB() {
        return (int) (getMemorySize() / (1024 * 1024));
    }

    public void setMemorySizeMB(final int memorySizeMB) {
        final long memorySize = memorySizeMB * 1024L * 1024L;
        setMemorySize(memorySize);
    }

    public StorageMode getStorageMode() {
        return storageMode;
    }

    public void setStorageMode(final StorageMode mode) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putString(STORAGE_MODE_KEY, mode.name()).commit();
        
        final StorageMode oldMode = storageMode;
        storageMode = mode;
        
        // Initialize or cleanup based on mode
        if (mode == StorageMode.BATCH_TO_DISK && diskAudioBuffer == null) {
            audioHandler.post(new Runnable() {
                @Override
                public void run() {
                    initializeDiskBuffer();
                }
            });
        } else if (mode == StorageMode.MEMORY_ONLY && diskAudioBuffer != null) {
            audioHandler.post(new Runnable() {
                @Override
                public void run() {
                    cleanupDiskBuffer();
                }
            });
        }
    }

    private void initializeDiskBuffer() {
        // Only called on audio thread
        assert audioHandler.getLooper() == Looper.myLooper();
        
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        long maxDiskUsageMB = preferences.getLong(MAX_DISK_USAGE_MB_KEY, 500); // Default 500 MB
        long maxDiskUsageBytes = maxDiskUsageMB * 1024L * 1024L;
        
        File storageDir;
        if (isExternalStorageWritable()) {
            storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Echo");
        } else {
            storageDir = getFilesDir();
        }
        
        diskAudioBuffer = new DiskAudioBuffer(storageDir, maxDiskUsageBytes, AudioMemory.CHUNK_SIZE);
        Log.d(TAG, "Initialized disk buffer with max size: " + maxDiskUsageMB + " MB");
    }

    private void cleanupDiskBuffer() {
        // Only called on audio thread
        assert audioHandler.getLooper() == Looper.myLooper();
        
        if (diskAudioBuffer != null) {
            try {
                diskAudioBuffer.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing disk buffer", e);
            }
            diskAudioBuffer = null;
        }
    }

    public int getSamplingRate() {
        return SAMPLE_RATE;
    }

    public void setSampleRate(int sampleRate) {
        switch(state) {
            case STATE_READY:
            case STATE_RECORDING:
                return;
            case STATE_LISTENING:
                break;
        }

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putInt(SAMPLE_RATE_KEY, sampleRate).commit();

        innerStopListening();
        SAMPLE_RATE = sampleRate;
        FILL_RATE = 2 * SAMPLE_RATE;
        innerStartListening();
    }
    
    /**
     * Configure silence skipping feature.
     * @param enabled Enable or disable silence skipping
     * @param threshold Silence detection threshold (0-32767)
     * @param segmentCount Number of consecutive silent segments before skipping
     */
    public void configureSilenceSkipping(final boolean enabled, final int threshold, final int segmentCount) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit()
            .putBoolean(SILENCE_SKIP_ENABLED_KEY, enabled)
            .putInt(SILENCE_THRESHOLD_KEY, threshold)
            .putInt(SILENCE_SEGMENT_COUNT_KEY, segmentCount)
            .apply();
        
        silenceThreshold = threshold; // Update the class field
        
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                audioMemory.configureSilenceSkipping(enabled, threshold, segmentCount);
                Log.d(TAG, "Silence skipping configured: enabled=" + enabled + 
                    ", threshold=" + threshold + ", segmentCount=" + segmentCount);
            }
        });
    }

    /**
     * Enable or disable device audio recording (via AudioPlaybackCapture API).
     * Requires MediaProjection to be set via setMediaProjection() before starting.
     * Requires restart of listening to take effect.
     * @param enabled If true, record device audio; if false, record microphone
     */
    public void setDeviceAudioRecording(final boolean enabled) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putBoolean(RECORD_DEVICE_AUDIO_KEY, enabled).apply();
        recordDeviceAudio = enabled;
        Log.d(TAG, "Device audio recording set to: " + enabled + " (restart listening to apply)");
    }

    public void setDualSourceRecording(final boolean enabled) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putBoolean(DUAL_SOURCE_RECORDING_KEY, enabled).apply();
        dualSourceRecording = enabled;
        Log.d(TAG, "Dual-source recording set to: " + enabled + " (restart listening to apply)");
    }

    public void setMicChannelMode(final int mode) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putInt(MIC_CHANNEL_MODE_KEY, mode).apply();
        micChannelMode = mode;
        Log.d(TAG, "Mic channel mode set to: " + (mode == 0 ? "mono" : "stereo") + " (restart listening to apply)");
    }

    public void setDeviceChannelMode(final int mode) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putInt(DEVICE_CHANNEL_MODE_KEY, mode).apply();
        deviceChannelMode = mode;
        Log.d(TAG, "Device channel mode set to: " + (mode == 0 ? "mono" : "stereo") + " (restart listening to apply)");
    }

    /**
     * Initialize MediaProjection from the result of a MediaProjection permission request.
     * This method creates the MediaProjection in the service context, which is required
     * for Android 14+ (API 34+) where MediaProjection must be created in a foreground
     * service with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION.
     * 
     * @param resultCode The result code from onActivityResult
     * @param data The Intent data from onActivityResult containing the MediaProjection token
     * @return true if MediaProjection was successfully initialized, false otherwise
     */
    public boolean initializeMediaProjection(int resultCode, Intent data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaProjectionManager != null) {
            // For Android 14+ (API 34+), MUST update foreground service type to include MEDIA_PROJECTION
            // BEFORE calling getMediaProjection(). The Android system checks the foreground service type
            // at the time MediaProjection is created and throws SecurityException if not properly declared.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE |
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                try {
                    Log.d(TAG, "Calling startForeground with MEDIA_PROJECTION type before getting projection.");
                    startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(), foregroundServiceType);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to update foreground service type for MediaProjection", e);
                    // We can still try to get the projection, but it will likely fail.
                }
            }

            try {
                // Now create the MediaProjection with the proper foreground service type already in place
                MediaProjection projection = mediaProjectionManager.getMediaProjection(resultCode, data);
                if (projection != null) {
                    this.mediaProjection = projection;
                    Log.d(TAG, "MediaProjection initialized in service context - device audio capture is now available");
                    return true;
                } else {
                    Log.e(TAG, "Failed to initialize MediaProjection - projection is null");
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize MediaProjection", e);
                return false;
            }
        } else {
            Log.e(TAG, "Cannot initialize MediaProjection - MediaProjectionManager not available");
            return false;
        }
    }

    /**
     * Check if MediaProjection is required for the current audio configuration.
     * Returns true if device audio or dual-source recording is enabled but MediaProjection is not yet initialized.
     * 
     * @return true if MediaProjection needs to be requested, false otherwise
     */
    public boolean isMediaProjectionRequired() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false; // AudioPlaybackCapture requires Android 10+
        }
        
        final SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        final boolean deviceAudioEnabled = prefs.getBoolean(RECORD_DEVICE_AUDIO_KEY, false);
        final boolean dualSourceEnabled = prefs.getBoolean(DUAL_SOURCE_RECORDING_KEY, false);
        
        if ((deviceAudioEnabled || dualSourceEnabled) && mediaProjection == null) {
            Log.d(TAG, "MediaProjection required but not initialized - device audio=" + deviceAudioEnabled + ", dual-source=" + dualSourceEnabled);
            return true;
        }
        return false;
    }

    /**
     * Set the MediaProjection instance for device audio capture.
     * This must be called before starting recording if device audio capture is enabled.
     * 
     * @param projection The MediaProjection instance, or null to clear
     * @deprecated Use initializeMediaProjection(int, Intent) instead to properly handle Android 14+ requirements
     */
    @Deprecated
    public void setMediaProjection(MediaProjection projection) {
        this.mediaProjection = projection;
        if (projection != null) {
            Log.d(TAG, "MediaProjection set - device audio capture is now available");
        } else {
            Log.d(TAG, "MediaProjection cleared - device audio capture is not available");
        }
    }

    /**
     * Configure voice activity detection and activity recording parameters.
     */
    public void configureActivityDetection(final boolean enabled, final float threshold,
                                           final int preBufferSeconds, final int postBufferSeconds,
                                           final int autoDeleteDays, final boolean highBitrate) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit()
            .putBoolean(ACTIVITY_DETECTION_ENABLED_KEY, enabled)
            .putFloat(ACTIVITY_DETECTION_THRESHOLD_KEY, threshold)
            .putInt(ACTIVITY_PRE_BUFFER_SECONDS_KEY, preBufferSeconds)
            .putInt(ACTIVITY_POST_BUFFER_SECONDS_KEY, postBufferSeconds)
            .putInt(ACTIVITY_AUTO_DELETE_DAYS_KEY, autoDeleteDays)
            .putBoolean(ACTIVITY_HIGH_BITRATE_KEY, highBitrate)
            .apply();

        activityDetectionThreshold = threshold;
        activityPreBufferSeconds = preBufferSeconds;
        activityPostBufferSeconds = postBufferSeconds;
        activityAutoDeleteDays = autoDeleteDays;
        activityHighBitrate = highBitrate;

        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                activityDetectionEnabled = enabled;

                if (!enabled) {
                    if (isRecordingActivity) {
                        stopActivityRecording();
                    }
                    voiceActivityDetector = null;
                    return;
                }

                if (voiceActivityDetector == null) {
                    voiceActivityDetector = new VoiceActivityDetector(threshold);
                } else {
                    voiceActivityDetector.setThreshold(threshold);
                    voiceActivityDetector.reset();
                }

                if (activityRecordingDatabase == null) {
                    activityRecordingDatabase = new ActivityRecordingDatabase(SaidItService.this);
                }

                Log.d(TAG, "Activity detection configured: enabled=" + enabled
                        + ", threshold=" + threshold
                        + ", preBufferSeconds=" + preBufferSeconds
                        + ", postBufferSeconds=" + postBufferSeconds
                        + ", autoDeleteDays=" + autoDeleteDays
                        + ", highBitrate=" + highBitrate);
            }
        });
    }
    
    /**
     * Enable or disable VAD time window feature.
     * When enabled, VAD only operates during specified time window.
     */
    public void setVadTimeWindowEnabled(final boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(SaidIt.VAD_TIME_WINDOW_ENABLED_KEY, enabled).apply();
        Log.d(TAG, "VAD time window " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Set the time window during which VAD should be active.
     * @param startHour Hour to start VAD (0-23)
     * @param startMinute Minute to start VAD (0-59)
     * @param endHour Hour to end VAD (0-23)
     * @param endMinute Minute to end VAD (0-59)
     */
    public void setVadTimeWindow(final int startHour, final int startMinute, 
                                  final int endHour, final int endMinute) {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        prefs.edit()
            .putInt(SaidIt.VAD_START_HOUR_KEY, startHour)
            .putInt(SaidIt.VAD_START_MINUTE_KEY, startMinute)
            .putInt(SaidIt.VAD_END_HOUR_KEY, endHour)
            .putInt(SaidIt.VAD_END_MINUTE_KEY, endMinute)
            .apply();
        Log.d(TAG, "VAD time window set: " + startHour + ":" + startMinute + " to " + endHour + ":" + endMinute);
    }
    
    /**
     * Check if VAD should be active based on time window settings.
     * @return true if VAD should be active, false otherwise
     */
    private boolean isVadActiveInTimeWindow() {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        boolean vadTimeWindowEnabled = prefs.getBoolean(SaidIt.VAD_TIME_WINDOW_ENABLED_KEY, false);
        
        if (!vadTimeWindowEnabled) {
            return true; // If time window not enabled, VAD always active
        }
        
        int startHour = prefs.getInt(SaidIt.VAD_START_HOUR_KEY, 22);
        int startMinute = prefs.getInt(SaidIt.VAD_START_MINUTE_KEY, 0);
        int endHour = prefs.getInt(SaidIt.VAD_END_HOUR_KEY, 6);
        int endMinute = prefs.getInt(SaidIt.VAD_END_MINUTE_KEY, 0);
        
        java.util.Calendar now = java.util.Calendar.getInstance();
        int currentHour = now.get(java.util.Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(java.util.Calendar.MINUTE);
        
        // Convert to minutes since midnight for easier comparison
        int currentMinutes = currentHour * 60 + currentMinute;
        int startMinutes = startHour * 60 + startMinute;
        int endMinutes = endHour * 60 + endMinute;
        
        // Handle time window that crosses midnight
        if (startMinutes > endMinutes) {
            // Example: 22:00 to 06:00
            return currentMinutes >= startMinutes || currentMinutes <= endMinutes;
        } else {
            // Example: 06:00 to 22:00
            return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
        }
    }

    public interface WavFileReceiver {
        public void fileReady(File file, float runtime);
    }

    public void stopRecording(final WavFileReceiver wavFileReceiver, String newFileName) {
        switch(state) {
            case STATE_READY:
            case STATE_LISTENING:
                return;
            case STATE_RECORDING:
                break;
        }
        state = STATE_LISTENING;

        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                flushAudioRecord();
                try {
                    wavFileWriter.close();
                } catch (IOException e) {
                    Log.e(TAG, "CLOSING ERROR", e);
                }
                if(wavFileReceiver != null) {
                    wavFileReceiver.fileReady(wavFile, wavFileWriter.getTotalSampleBytesWritten() * getBytesToSeconds());
                }
                wavFileWriter = null;
            }
        });

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        if(!preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            innerStopListening();
        }

        stopForeground(true);
    }
    
    /**
     * Setup a scheduled recording with partial file writing.
     * Uses VAD-like incremental writing to handle rolling memory window.
     * @param startTimeMillis When to start recording (milliseconds since epoch)
     * @param endTimeMillis When to end recording (milliseconds since epoch)
     * @param filename Output filename
     */
    public void setupScheduledRecording(final long startTimeMillis, final long endTimeMillis, final String filename) {
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    long now = System.currentTimeMillis();
                    long delayUntilStart = startTimeMillis - now;
                    
                    if (delayUntilStart < 0) {
                        Log.w(TAG, "Scheduled recording start time is in the past");
                        return;
                    }
                    
                    // Schedule start of recording with partial file writing
                    audioHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startScheduledRecordingWithPartialWriting(endTimeMillis, filename);
                        }
                    }, delayUntilStart);
                    
                    Log.d(TAG, "Scheduled recording setup: will start in " + (delayUntilStart/1000) + " seconds");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error setting up scheduled recording", e);
                }
            }
        });
    }
    
    /**
     * Start a scheduled recording with partial file writing (like VAD).
     * Writes audio incrementally so if start time passes the rolling window, it's not a problem.
     */
    private void startScheduledRecordingWithPartialWriting(final long endTimeMillis, final String filename) {
        try {
            // Create output directory
            File echoDir = new File(android.os.Environment.getExternalStorageDirectory(), "Echo");
            File scheduledDir = new File(echoDir, "Scheduled");
            if (!scheduledDir.exists()) {
                scheduledDir.mkdirs();
            }
            
            // Create WAV file
            String sanitizedFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            File outputFile = new File(scheduledDir, sanitizedFilename + ".wav");
            
            // Setup partial file writer (similar to VAD)
            simplesound.pcm.WavAudioFormat wavFormat = simplesound.pcm.WavAudioFormat.mono16Bit(getSamplingRate());
            simplesound.pcm.WavFileWriter partialWriter = new simplesound.pcm.WavFileWriter(wavFormat, outputFile);
            
            // Calculate how much time until end
            long now = System.currentTimeMillis();
            long duration = endTimeMillis - now;
            
            if (duration <= 0) {
                Log.w(TAG, "Scheduled recording end time is in the past");
                return;
            }
            
            // Start partial writing from memory with incremental updates
            startPartialFileWriting(partialWriter, duration, outputFile);
            
            Log.d(TAG, "Started scheduled recording with partial file writing: " + outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting scheduled recording with partial file writing", e);
            CrashHandler.writeCrashLog(this, "Error starting scheduled recording", e);
        }
    }
    
    /**
     * Write audio to file incrementally (partial file writing like VAD).
     * This ensures that if the start time passes the rolling window, we still capture what's available.
     */
    private void startPartialFileWriting(final simplesound.pcm.WavFileWriter writer, final long durationMillis, final File outputFile) {
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Write current memory buffer to start the file
                    audioMemory.read(0, new AudioMemory.Consumer() {
                        @Override
                        public int consume(byte[] arr, int offset, int count) throws IOException {
                            try {
                                writer.write(arr, offset, count);
                                return count;
                            } catch (Exception e) {
                                Log.e(TAG, "Error writing scheduled recording chunk", e);
                                return 0;
                            }
                        }
                    });
                    
                    // Continue writing incrementally until end time
                    final long endTime = System.currentTimeMillis() + durationMillis;
                    final Runnable incrementalWriter = new Runnable() {
                        @Override
                        public void run() {
                            long now = System.currentTimeMillis();
                            if (now >= endTime) {
                                // Finish recording
                                try {
                                    writer.close();
                                    Log.d(TAG, "Scheduled recording completed: " + outputFile.getAbsolutePath());
                                } catch (Exception e) {
                                    Log.e(TAG, "Error closing scheduled recording", e);
                                }
                                return;
                            }
                            
                            // Write latest audio chunks incrementally
                            // This is partial file writing - we write what's currently in memory
                            try {
                                // Get the latest 10 seconds of audio and append
                                float skipSeconds = 10.0f;
                                audioMemory.read((int)(skipSeconds * getSamplingRate() * 2), 
                                    new AudioMemory.Consumer() {
                                        @Override
                                        public int consume(byte[] arr, int offset, int count) throws IOException {
                                            try {
                                                writer.write(arr, offset, count);
                                                return count;
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error writing incremental chunk", e);
                                                return 0;
                                            }
                                        }
                                    });
                            } catch (Exception e) {
                                Log.e(TAG, "Error in incremental write", e);
                            }
                            
                            // Schedule next incremental write in 10 seconds
                            audioHandler.postDelayed(this, 10000);
                        }
                    };
                    
                    // Start incremental writing
                    audioHandler.postDelayed(incrementalWriter, 10000);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in partial file writing setup", e);
                }
            }
        });
    }

    private void flushAudioRecord() {
        // Only allowed on the audio thread
        assert audioHandler.getLooper() == Looper.myLooper();
        audioHandler.removeCallbacks(audioReader); // remove any delayed callbacks
        audioReader.run();
    }

    /**
     * Read from both mic and device audio sources and mix them into stereo output.
     * Output is always stereo (2 channels): Left channel = mic, Right channel = device audio
     * Each source can be mono or stereo independently.
     * If one source has no data available, silence is used for that channel.
     * 
     * @param array Output buffer (stereo interleaved: L,R,L,R,...)
     * @param offset Offset in output buffer
     * @param count Number of bytes to read (must be even, stereo 16-bit samples)
     * @return Number of bytes written to output buffer
     */
    private int readAndMixDualSource(byte[] array, int offset, int count) {
        // Temporary buffers for each source
        // Output is stereo, so count bytes = count/4 stereo frames
        int stereoFrames = count / 4; // Each stereo frame = 4 bytes (2 samples x 2 bytes each)
        
        // Calculate input buffer sizes based on channel modes
        int micBytesNeeded = (micChannelMode == 0) ? stereoFrames * 2 : stereoFrames * 4; // mono: 2 bytes/frame, stereo: 4 bytes/frame
        int deviceBytesNeeded = (deviceChannelMode == 0) ? stereoFrames * 2 : stereoFrames * 4;
        
        byte[] micBuffer = new byte[micBytesNeeded];
        byte[] deviceBuffer = new byte[deviceBytesNeeded];
        
        // Read from both sources
        int micRead = audioRecord.read(micBuffer, 0, micBytesNeeded, AudioRecord.READ_NON_BLOCKING);
        int deviceRead = (deviceAudioRecord != null) ? deviceAudioRecord.read(deviceBuffer, 0, deviceBytesNeeded, AudioRecord.READ_NON_BLOCKING) : 0;
        
        // Handle read errors
        if (micRead < 0) {
            Log.e(TAG, "MIC AUDIO RECORD ERROR: " + micRead);
            micRead = 0;
        }
        if (deviceRead < 0) {
            Log.e(TAG, "DEVICE AUDIO RECORD ERROR: " + deviceRead);
            deviceRead = 0;
        }
        
        // Calculate how many frames we have from each source
        int micFrames = (micRead > 0) ? ((micChannelMode == 0) ? micRead / 2 : micRead / 4) : 0;
        int deviceFrames = (deviceRead > 0) ? ((deviceChannelMode == 0) ? deviceRead / 2 : deviceRead / 4) : 0;
        
        // Use the maximum of available frames (fill missing channel with silence)
        int outputFrames = Math.max(micFrames, deviceFrames);
        outputFrames = Math.min(outputFrames, stereoFrames);
        
        // If neither source has data, return 0
        if (outputFrames == 0) {
            return 0;
        }
        
        // Mix samples into output buffer
        int outIdx = offset;
        for (int frame = 0; frame < outputFrames; frame++) {
            // Get mic sample (left channel) - use silence (0) if no data available
            short micSample = 0;
            if (frame < micFrames) {
                if (micChannelMode == 0) {
                    // Mono: use the single channel
                    int idx = frame * 2;
                    int lo = micBuffer[idx] & 0xff;
                    int hi = micBuffer[idx + 1];
                    micSample = (short)((hi << 8) | lo);
                } else {
                    // Stereo: use left channel (or could mix both)
                    int idx = frame * 4;
                    int lo = micBuffer[idx] & 0xff;
                    int hi = micBuffer[idx + 1];
                    micSample = (short)((hi << 8) | lo);
                }
            }
            
            // Get device sample (right channel) - use silence (0) if no data available
            short deviceSample = 0;
            if (frame < deviceFrames) {
                if (deviceChannelMode == 0) {
                    // Mono: use the single channel
                    int idx = frame * 2;
                    int lo = deviceBuffer[idx] & 0xff;
                    int hi = deviceBuffer[idx + 1];
                    deviceSample = (short)((hi << 8) | lo);
                } else {
                    // Stereo: use left channel (or could mix both)
                    int idx = frame * 4;
                    int lo = deviceBuffer[idx] & 0xff;
                    int hi = deviceBuffer[idx + 1];
                    deviceSample = (short)((hi << 8) | lo);
                }
            }
            
            // Write to output: Left = mic, Right = device
            array[outIdx++] = (byte)(micSample & 0xff);
            array[outIdx++] = (byte)((micSample >> 8) & 0xff);
            array[outIdx++] = (byte)(deviceSample & 0xff);
            array[outIdx++] = (byte)((deviceSample >> 8) & 0xff);
        }
        
        return outputFrames * 4; // Return bytes written
    }

    final AudioMemory.Consumer filler = new AudioMemory.Consumer() {
        @Override
        public int consume(final byte[] array, final int offset, final int count) throws IOException {
//            Log.d(TAG, "READING " + count + " B");
            
            int read;
            if (dualSourceRecording && deviceAudioRecord != null && audioRecord != null) {
                // Dual-source mode: read from both sources and mix into stereo
                read = readAndMixDualSource(array, offset, count);
            } else if (audioRecord != null) {
                // Single-source mode: read from primary audioRecord
                read = audioRecord.read(array, offset, count, AudioRecord.READ_NON_BLOCKING);
                if (read == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "AUDIO RECORD ERROR - BAD VALUE");
                    return 0;
                }
                if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "AUDIO RECORD ERROR - INVALID OPERATION");
                    return 0;
                }
                if (read == AudioRecord.ERROR) {
                    Log.e(TAG, "AUDIO RECORD ERROR - UNKNOWN ERROR");
                    return 0;
                }
            } else {
                // No audio record available - this shouldn't happen in normal operation
                Log.w(TAG, "No audio record available in filler consumer");
                return 0;
            }
            
            // Compute live volume (peak) for this buffer
            if (read > 0) {
                int max = 0;
                for (int i = 0; i + 1 < read; i += 2) {
                    int lo = array[offset + i] & 0xff;
                    int hi = array[offset + i + 1];
                    int sample = (short)((hi << 8) | lo);
                    int abs = sample < 0 ? -sample : sample;
                    if (abs > max) max = abs;
                }
                currentVolumeLevel = max;
                
                // Update timeline tracking
                updateTimeline(max, silenceThreshold);
            }

            // Write to active recording file if recording
            if (wavFileWriter != null && read > 0) {
                wavFileWriter.write(array, offset, read);
            }
            
            // Write to disk buffer if in BATCH_TO_DISK mode
            if (storageMode == StorageMode.BATCH_TO_DISK && diskAudioBuffer != null && read > 0) {
                try {
                    diskAudioBuffer.write(array, offset, read);
                } catch (IOException e) {
                    Log.e(TAG, "Error writing to disk buffer", e);
                    // Continue recording, just skip this write
                }
            }
            
            // Voice Activity Detection integration
            if (activityDetectionEnabled && voiceActivityDetector != null && read > 0) {
                boolean hasActivity = voiceActivityDetector.process(array, offset, read);
                long currentTime = System.currentTimeMillis();
                
                if (hasActivity) {
                    lastActivityTime = currentTime;
                    
                    // Start activity recording if not already recording
                    if (!isRecordingActivity) {
                        startActivityRecording();
                    }
                }
                
                // Write to activity recording if active
                if (isRecordingActivity && activityWavFileWriter != null) {
                    try {
                        activityWavFileWriter.write(array, offset, read);
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing to activity recording", e);
                    }
                }
                
                // Check if we should stop activity recording (post-activity buffer expired)
                if (isRecordingActivity && !hasActivity) {
                    long postBufferMillis = activityPostBufferSeconds * 1000L;

                    if (currentTime - lastActivityTime > postBufferMillis) {
                        stopActivityRecording();
                    }
                }
            }
            //     if (hasActivity && !isRecordingActivity) {
            //         // Start activity recording with pre-buffer
            //     } else if (!hasActivity && isRecordingActivity && 
            //                (System.currentTimeMillis() - lastActivityTime > POST_ACTIVITY_BUFFER_MS)) {
            //         // Stop activity recording and save
            //     }
            // }
            
            if (read == count) {
                // We've filled the buffer, so let's read again.
                audioHandler.post(audioReader);
            } else {
                // It seems we've read everything!
                //
                // Estimate how long do we have until audioRecord fills up completely and post the callback 1 second before that
                // (but not earlier than half the buffer and no later than 90% of the buffer).
                float bufferSizeInSeconds = audioRecord.getBufferSizeInFrames() / (float)SAMPLE_RATE;
                float delaySeconds = bufferSizeInSeconds - 1;
                delaySeconds = Math.max(delaySeconds, bufferSizeInSeconds * 0.5f);
                delaySeconds = Math.min(delaySeconds, bufferSizeInSeconds * 0.9f);
                audioHandler.postDelayed(audioReader, (long)(delaySeconds * 1000));
            }
            return read;
        }
    };
    final Runnable audioReader = new Runnable() {
        @Override
        public void run() {
            try {
                if (gradientQualityEnabled) {
                    // Route audio to the appropriate quality tier based on elapsed time
                    long elapsedMillis = System.currentTimeMillis() - recordingStartTimeMillis;
                    int elapsedSeconds = (int)(elapsedMillis / 1000);
                    
                    if (elapsedSeconds < 300) { // 0-5 minutes: HIGH quality
                        audioMemoryHigh.fill(filler);
                    } else if (elapsedSeconds < 1200) { // 5-20 minutes: MID quality
                        audioMemoryMid.fill(filler);
                    } else { // 20+ minutes: LOW quality
                        audioMemoryLow.fill(filler);
                    }
                } else {
                    audioMemory.fill(filler);
                }
            } catch (IOException e) {
                final String errorMessage = getString(R.string.error_during_recording_into) + wavFile.getName();
                Toast.makeText(SaidItService.this, errorMessage, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorMessage, e);
                CrashHandler.writeCrashLog(SaidItService.this, "Audio reader error", e);
                // Close any active VAD recording to ensure file integrity on errors
                try {
                    if (isRecordingActivity) {
                        stopActivityRecording();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Error while closing VAD recording after audio error", t);
                    CrashHandler.writeCrashLog(SaidItService.this, "Error closing VAD after audio error", t);
                }
                stopRecording(new SaidItFragment.NotifyFileReceiver(SaidItService.this), "");
            } catch (Throwable t) {
                Log.e(TAG, "Unexpected error in audio reader", t);
                CrashHandler.writeCrashLog(SaidItService.this, "Unexpected error in audio reader", t);
                stopRecording(new SaidItFragment.NotifyFileReceiver(SaidItService.this), "");
            }
        }
    };

    public interface StateCallback {
        public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded, float skippedSeconds);
    }

    /**
     * Callback interface for requesting MediaProjection permission.
     * Implemented by the activity to handle showing the MediaProjection permission dialog.
     */
    public interface MediaProjectionRequestCallback {
        /**
         * Called when the service needs MediaProjection permission to be requested.
         * The activity should start the MediaProjection intent and handle the result.
         */
        void onRequestMediaProjection();
    }

    // Callback for MediaProjection requests
    private MediaProjectionRequestCallback mediaProjectionRequestCallback = null;

    /**
     * Set the callback for MediaProjection requests.
     * This should be called by the activity that can handle the permission request.
     */
    public void setMediaProjectionRequestCallback(MediaProjectionRequestCallback callback) {
        this.mediaProjectionRequestCallback = callback;
    }

    public void getState(final StateCallback stateCallback) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        final boolean listeningEnabled = preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true);
        final boolean recording = (state == STATE_RECORDING);
        final Handler sourceHandler = new Handler();
        // Note that we may not run this for quite a while, if audioReader decides to read a lot of audio!
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                flushAudioRecord();
                
                final AudioMemory.Stats stats;
                final float bytesToSeconds;
                final float skippedSeconds;
                final float totalMemorySeconds; // Actual total capacity in seconds
                
                if (gradientQualityEnabled) {
                    // Combine stats from all three rings
                    AudioMemory.Stats statsHigh = audioMemoryHigh.getStats(gradientQualityHighRate * 2);
                    AudioMemory.Stats statsMid = audioMemoryMid.getStats(gradientQualityMidRate * 2);
                    AudioMemory.Stats statsLow = audioMemoryLow.getStats(gradientQualityLowRate * 2);
                    
                    // Create combined stats
                    stats = new AudioMemory.Stats();
                    stats.filled = statsHigh.filled + statsMid.filled + statsLow.filled;
                    stats.total = statsHigh.total + statsMid.total + statsLow.total;
                    stats.estimation = statsHigh.estimation + statsMid.estimation + statsLow.estimation;
                    stats.overwriting = statsHigh.overwriting || statsMid.overwriting || statsLow.overwriting;
                    stats.skippedSegments = statsHigh.skippedSegments + statsMid.skippedSegments + statsLow.skippedSegments;
                    
                    // Calculate ACTUAL total capacity by converting each ring's capacity to seconds
                    float totalCapacityHigh = statsHigh.total / (float)(gradientQualityHighRate * 2);
                    float totalCapacityMid = statsMid.total / (float)(gradientQualityMidRate * 2);
                    float totalCapacityLow = statsLow.total / (float)(gradientQualityLowRate * 2);
                    totalMemorySeconds = totalCapacityHigh + totalCapacityMid + totalCapacityLow;
                    
                    // Calculate durations for each ring
                    float durationHigh = statsHigh.filled / (float)(gradientQualityHighRate * 2);
                    float durationMid = statsMid.filled / (float)(gradientQualityMidRate * 2);
                    float durationLow = statsLow.filled / (float)(gradientQualityLowRate * 2);
                    
                    // Use weighted average for bytes to seconds conversion (for memorized calculation)
                    float totalDuration = durationHigh + durationMid + durationLow;
                    if (totalDuration > 0 && stats.filled > 0) {
                        bytesToSeconds = totalDuration / stats.filled;
                    } else {
                        bytesToSeconds = 1.0f / (gradientQualityHighRate * 2); // Default to high rate
                    }
                    
                    // Calculate skipped seconds from each ring
                    float skippedHigh = statsHigh.skippedSegments * AudioMemory.CHUNK_SIZE / (float)(gradientQualityHighRate * 2);
                    float skippedMid = statsMid.skippedSegments * AudioMemory.CHUNK_SIZE / (float)(gradientQualityMidRate * 2);
                    float skippedLow = statsLow.skippedSegments * AudioMemory.CHUNK_SIZE / (float)(gradientQualityLowRate * 2);
                    skippedSeconds = skippedHigh + skippedMid + skippedLow;
                } else {
                    // Single ring mode
                    stats = audioMemory.getStats(FILL_RATE);
                    bytesToSeconds = getBytesToSeconds();
                    totalMemorySeconds = stats.total * bytesToSeconds;
                    skippedSeconds = stats.skippedSegments * AudioMemory.CHUNK_SIZE * bytesToSeconds;
                }
                
                int recorded = 0;
                if(wavFileWriter != null) {
                    recorded += wavFileWriter.getTotalSampleBytesWritten();
                    recorded += stats.estimation;
                }
                final int finalRecorded = recorded;
                sourceHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        stateCallback.state(listeningEnabled, recording,
                                (stats.overwriting ? stats.total : stats.filled + stats.estimation) * bytesToSeconds,
                                totalMemorySeconds,
                                finalRecorded * bytesToSeconds,
                                skippedSeconds);
                    }
                });
            }
        });
    }

    // Provide live stats for UI (volume and skipped groups)
    public interface LiveStatsCallback {
        void stats(int volumeLevel, int skippedGroups);
    }

    public void getLiveStats(final LiveStatsCallback cb) {
        final Handler sourceHandler = new Handler();
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                final int volume = currentVolumeLevel;
                final int groups;
                
                if (gradientQualityEnabled) {
                    // Sum skipped groups from all rings
                    groups = audioMemoryHigh.getSkippedGroupsCount() + 
                            audioMemoryMid.getSkippedGroupsCount() + 
                            audioMemoryLow.getSkippedGroupsCount();
                } else {
                    groups = audioMemory.getSkippedGroupsCount();
                }
                
                Log.d(TAG, "getLiveStats: volume=" + volume + ", groups=" + groups);
                sourceHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.stats(volume, groups);
                    }
                });
            }
        });
    }

    // Silence groups log API
    public interface SilenceGroupsCallback {
        void onGroups(java.util.List<SilenceGroup> groups);
    }

    public void getSilenceGroups(final SilenceGroupsCallback cb) {
        if (cb == null) return;
        
        final Handler sourceHandler = new Handler();
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                // Translate AudioMemory entries into service-level groups with durations
                java.util.ArrayList<AudioMemory.SilenceGroupEntry> entries = audioMemory.getSilenceGroupsSnapshot();
                java.util.ArrayList<SilenceGroup> snapshot = new java.util.ArrayList<>(entries.size());
                long segmentMillis = (long)(AudioMemory.CHUNK_SIZE * getBytesToSeconds() * 1000);
                Log.d(TAG, "getSilenceGroups: rawEntries=" + entries.size() + ", segmentMillis=" + segmentMillis);
                for (AudioMemory.SilenceGroupEntry e : entries) {
                    long duration = segmentMillis * e.segments;
                    snapshot.add(new SilenceGroup(e.endTimeMillis, duration));
                }
                // Prune based on current memory span
                int bytesAvailable = audioMemory.countFilled();
                long memorySpanMillis = (long)(bytesAvailable * getBytesToSeconds() * 1000);
                long cutoff = System.currentTimeMillis() - memorySpanMillis;
                Log.d(TAG, "Pruning: memorySpanMillis=" + memorySpanMillis + ", cutoff=" + cutoff + ", beforePrune=" + snapshot.size());
                java.util.Iterator<SilenceGroup> it = snapshot.iterator();
                while (it.hasNext()) {
                    SilenceGroup g = it.next();
                    if (g.endTimeMillis < cutoff) it.remove();
                }
                Log.d(TAG, "After pruning: groups=" + snapshot.size());
                sourceHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cb != null) {
                            cb.onGroups(snapshot);
                        }
                    }
                });
            }
        });
    }

    // Timeline segments API for activity/silence display
    public interface TimelineCallback {
        void onTimeline(java.util.List<TimelineSegment> segments, TimelineSegment currentSegment, float totalMemorySeconds);
    }

    public void getTimeline(final TimelineCallback cb) {
        if (cb == null) return;
        
        final Handler sourceHandler = new Handler();
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (timelineSegments) {
                    // Create a copy of the timeline segments
                    final java.util.List<TimelineSegment> segmentsCopy = new java.util.ArrayList<>(timelineSegments);
                    final TimelineSegment currentCopy = currentSegment;
                    
                    // Calculate total memory in seconds
                    int bytesAvailable;
                    if (gradientQualityEnabled) {
                        bytesAvailable = audioMemoryHigh.countFilled() + 
                                       audioMemoryMid.countFilled() + 
                                       audioMemoryLow.countFilled();
                    } else {
                        bytesAvailable = audioMemory.countFilled();
                    }
                    final float totalMemorySec = bytesAvailable * getBytesToSeconds();
                    
                    sourceHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (cb != null) {
                                cb.onTimeline(segmentsCopy, currentCopy, totalMemorySec);
                            }
                        }
                    });
                }
            }
        });
    }
    
    // Update timeline tracking based on volume level
    private void updateTimeline(int volumeLevel, int silenceThreshold) {
        synchronized (timelineSegments) {
            boolean isActivity = volumeLevel >= silenceThreshold;
            
            if (currentSegment == null) {
                // Start first segment
                TimelineSegment.Type type = isActivity ? TimelineSegment.Type.ACTIVITY : TimelineSegment.Type.SILENCE;
                currentSegment = new TimelineSegment(type, System.currentTimeMillis());
            } else {
                // Check if we need to transition to a new segment
                TimelineSegment.Type currentType = currentSegment.type;
                TimelineSegment.Type newType = isActivity ? TimelineSegment.Type.ACTIVITY : TimelineSegment.Type.SILENCE;
                
                if (currentType != newType) {
                    // End current segment and start new one
                    currentSegment.end(System.currentTimeMillis());
                    timelineSegments.add(currentSegment);
                    
                    // Prune old segments to keep memory manageable
                    while (timelineSegments.size() > maxTimelineSegments) {
                        timelineSegments.remove(0);
                    }
                    
                    currentSegment = new TimelineSegment(newType, System.currentTimeMillis());
                }
            }
        }
    }

    /**
     * Create an AudioFormat for audio recording.
     * @param channelMode 0 for mono, 1 for stereo
     * @return AudioFormat configured with sample rate and channel mode
     */
    private AudioFormat createAudioFormat(int channelMode) {
        return new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(channelMode == 0 ? 
                AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO)
            .build();
    }

    /**
     * Create an AudioPlaybackCaptureConfiguration for device audio capture.
     * @return AudioPlaybackCaptureConfiguration configured to capture media, game, and unknown audio
     */
    private AudioPlaybackCaptureConfiguration createAudioPlaybackCaptureConfig() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
            return new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                .build();
        }
        return null;
    }

    /**
     * Create a microphone AudioRecord with specified channel mode.
     * @param channelMode 0 for mono, 1 for stereo
     * @return AudioRecord configured for microphone capture
     */
    private AudioRecord createMicrophoneAudioRecord(int channelMode) {
        return new AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            channelMode == 0 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioMemory.CHUNK_SIZE);
    }

    public float getBytesToSeconds() {
        return 1f / FILL_RATE;
    }

    class BackgroundRecorderBinder extends Binder {
        public SaidItService getService() {
            return SaidItService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle auto-save action
        if (intent != null && ACTION_AUTO_SAVE.equals(intent.getAction())) {
            handleAutoSave();
            return START_STICKY;
        }
        
        // Handle auto-save cleanup action
        if (intent != null && "eu.mrogalski.saidit.ACTION_AUTO_SAVE_CLEANUP".equals(intent.getAction())) {
            handleAutoSaveCleanup();
            return START_STICKY;
        }
        
        // Determine foreground service type based on what we're recording
        int foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        if (mediaProjection != null && (recordDeviceAudio || dualSourceRecording) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Add media projection type ONLY when we have a valid, user-granted projection
            foregroundServiceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        }
        
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(), foregroundServiceType);
        return START_STICKY;
    }

    // Workaround for bug where recent app removal caused service to stop
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent = PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT| PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmService = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, SaidItActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, YOUR_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.recording))
                .setSmallIcon(R.drawable.ic_stat_notify_recording)
                .setTicker(getString(R.string.recording))
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true); // Ensure notification is ongoing

        // Create the notification channel
        NotificationChannel channel = new NotificationChannel(
                YOUR_NOTIFICATION_CHANNEL_ID,
                "Recording Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        return notificationBuilder.build();
    }
    
    /**
     * Schedule automatic saving of recordings at configured intervals.
     */
    public void scheduleAutoSave() {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(AUTO_SAVE_ENABLED_KEY, false);
        
        // Always cancel first to avoid duplicate alarms
        cancelAutoSave();
        
        if (!enabled || state != STATE_LISTENING) {
            Log.d(TAG, "Auto-save not scheduled: enabled=" + enabled + ", state=" + state);
            return;
        }
        
        int durationSeconds = prefs.getInt(AUTO_SAVE_DURATION_KEY, 600); // Default 10 minutes
        long intervalMillis = durationSeconds * 1000L;
        
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, SaidItService.class);
        intent.setAction(ACTION_AUTO_SAVE);
        autoSavePendingIntent = PendingIntent.getService(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + intervalMillis,
            intervalMillis,
            autoSavePendingIntent
        );
        
        Log.d(TAG, "Auto-save scheduled every " + durationSeconds + " seconds");
    }
    
    /**
     * Cancel scheduled auto-save.
     */
    public void cancelAutoSave() {
        if (autoSavePendingIntent != null) {
            Log.d(TAG, "Cancelling auto-save");
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarmManager.cancel(autoSavePendingIntent);
            autoSavePendingIntent = null;
        }
    }
    
    /**
     * Handle an auto-save event.
     */
    private void handleAutoSave() {
        if (state != STATE_LISTENING) {
            Log.d(TAG, "Auto-save skipped: service not listening");
            return;
        }
        
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(AUTO_SAVE_ENABLED_KEY, false)) {
            Log.d(TAG, "Auto-save skipped: feature disabled");
            return;
        }
        
        int durationSeconds = prefs.getInt(AUTO_SAVE_DURATION_KEY, 600);
        Log.d(TAG, "Performing auto-save with " + durationSeconds + " seconds of audio");
        
        // Dump the recording with the configured duration
        float durationFloat = (float) durationSeconds;
        dumpRecording(durationFloat, null, "");
    }
    
    /**
     * Schedule automatic cleanup of old auto-save files.
     * Deletes auto-save files older than the configured retention period.
     */
    public void scheduleAutoSaveCleanup() {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        
        // Always cancel first to avoid duplicate alarms
        cancelAutoSaveCleanup();
        
        if (state != STATE_LISTENING) {
            Log.d(TAG, "Auto-save cleanup not scheduled: service not listening");
            return;
        }
        
        // Schedule cleanup to run daily
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, SaidItService.class);
        intent.setAction("eu.mrogalski.saidit.ACTION_AUTO_SAVE_CLEANUP");
        autoSaveCleanupPendingIntent = PendingIntent.getService(
            this, 1, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        // Schedule daily cleanup (24 hours)
        long dailyIntervalMillis = 24L * 60L * 60L * 1000L;
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + dailyIntervalMillis,
            dailyIntervalMillis,
            autoSaveCleanupPendingIntent
        );
        
        Log.d(TAG, "Auto-save cleanup scheduled daily (delete files older than " + autoSaveAutoDeleteDays + " days)");
    }
    
    /**
     * Cancel scheduled auto-save cleanup.
     */
    public void cancelAutoSaveCleanup() {
        if (autoSaveCleanupPendingIntent != null) {
            Log.d(TAG, "Cancelling auto-save cleanup");
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarmManager.cancel(autoSaveCleanupPendingIntent);
            autoSaveCleanupPendingIntent = null;
        }
    }
    
    /**
     * Handle auto-save file cleanup event.
     * Deletes auto-save files older than autoSaveAutoDeleteDays.
     */
    private void handleAutoSaveCleanup() {
        Log.d(TAG, "Performing auto-save cleanup (deleting files older than " + autoSaveAutoDeleteDays + " days)");
        
        File autoSaveDir;
        if (isExternalStorageWritable()) {
            autoSaveDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Echo/AutoSave");
        } else {
            autoSaveDir = new File(getFilesDir(), "Echo/AutoSave");
        }
        
        if (!autoSaveDir.exists() || !autoSaveDir.isDirectory()) {
            Log.d(TAG, "Auto-save directory does not exist: " + autoSaveDir.getAbsolutePath());
            return;
        }
        
        // Get current time
        long currentTime = System.currentTimeMillis();
        long deleteBeforeTime = currentTime - (autoSaveAutoDeleteDays * 24L * 60L * 60L * 1000L);
        
        // List all files in the auto-save directory
        File[] files = autoSaveDir.listFiles();
        if (files == null) {
            Log.d(TAG, "Failed to list files in auto-save directory");
            return;
        }
        
        int deletedCount = 0;
        long freedBytes = 0;
        
        for (File file : files) {
            if (file.isFile() && file.lastModified() < deleteBeforeTime) {
                long fileSize = file.length();
                if (file.delete()) {
                    deletedCount++;
                    freedBytes += fileSize;
                    Log.d(TAG, "Deleted old auto-save file: " + file.getName());
                } else {
                    Log.w(TAG, "Failed to delete auto-save file: " + file.getName());
                }
            }
        }
        
        Log.d(TAG, "Auto-save cleanup complete: deleted " + deletedCount + " files, freed " + 
            (freedBytes / 1024 / 1024) + " MB");
    }
    
    /**
     * Start recording detected voice activity.
     * Creates a new WAV file in Echo/VAD subfolder and starts writing audio with pre-activity buffer.
     */
    private void startActivityRecording() {
        // Only called on audio thread
        assert audioHandler.getLooper() == Looper.myLooper();
        
        if (isRecordingActivity) {
            return; // Already recording
        }
        
        try {
            activityStartTime = System.currentTimeMillis();
            isRecordingActivity = true;
            
            // Create output file in Echo/VAD subfolder
            File storageDir;
            if (isExternalStorageWritable()) {
                storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Echo/VAD");
            } else {
                storageDir = new File(getFilesDir(), "Echo/VAD");
            }
            
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            
            // Filename format: vad_yyyyMMdd_HHmmss.wav (e.g., vad_20260110_143025.wav)
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
            activityWavFile = new File(storageDir, "vad_" + timestamp + ".wav");
            
            // Initialize WAV writer
            WavAudioFormat wavAudioFormat = new WavAudioFormat.Builder().sampleRate(SAMPLE_RATE).build();
            activityWavFileWriter = new WavFileWriter(wavAudioFormat, activityWavFile);
            
            // Get pre-activity buffer settings
            int preBufferBytes = (int)(activityPreBufferSeconds * FILL_RATE);
            
            // Write pre-activity buffer from audioMemory
            audioMemory.read(Math.max(0, audioMemory.countFilled() - preBufferBytes), 
                new AudioMemory.Consumer() {
                    @Override
                    public int consume(byte[] array, int offset, int count) throws IOException {
                        activityWavFileWriter.write(array, offset, count);
                        return count;
                    }
                });
            
            Log.d(TAG, "Started activity recording: " + activityWavFile.getName());
            
        } catch (IOException e) {
            Log.e(TAG, "Error starting activity recording", e);
            isRecordingActivity = false;
            activityWavFileWriter = null;
            activityWavFile = null;
        }
    }
    
    /**
     * Stop recording detected voice activity.
     * Closes the WAV file (stored in Echo/VAD subfolder) and adds it to the VAD recording database.
     */
    private void stopActivityRecording() {
        // Only called on audio thread
        assert audioHandler.getLooper() == Looper.myLooper();
        
        if (!isRecordingActivity) {
            return; // Not recording
        }
        
        try {
            if (activityWavFileWriter != null) {
                activityWavFileWriter.close();

                // Validate file has audio data (WAV header is ~44 bytes)
                boolean validAudio = activityWavFile != null && activityWavFile.length() > 44;

                if (validAudio) {
                    // Calculate duration
                    long durationMillis = System.currentTimeMillis() - activityStartTime;
                    float durationSeconds = durationMillis / 1000.0f;

                    // Add to database
                    if (activityRecordingDatabase != null && activityWavFile != null) {
                        long deleteAfter = System.currentTimeMillis() + (activityAutoDeleteDays * 24L * 60L * 60L * 1000L);

                        ActivityRecording recording = new ActivityRecording(
                                System.currentTimeMillis(), // id
                                activityStartTime, // timestamp
                                (int) durationSeconds, // durationSeconds
                                activityWavFile.getAbsolutePath(), // filePath
                                false, // not flagged
                                deleteAfter, // deleteAfterTimestamp
                                (int) activityWavFile.length() // fileSize in bytes
                        );

                        activityRecordingDatabase.addRecording(recording);

                        Log.d(TAG, "Stopped activity recording: " + activityWavFile.getName() +
                                " (duration: " + String.format("%.1f", durationSeconds) + "s)");
                    }
                } else {
                    Log.w(TAG, "VAD recording contained no audio; deleting file: " +
                            (activityWavFile != null ? activityWavFile.getAbsolutePath() : "<null>"));
                    if (activityWavFile != null) {
                        // Delete the empty/corrupted file
                        //noinspection ResultOfMethodCallIgnored
                        activityWavFile.delete();
                    }
                }

                activityWavFileWriter = null;
                activityWavFile = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error stopping activity recording", e);
        } finally {
            isRecordingActivity = false;
        }
    }
    
    /**
     * Memory-efficient export that streams audio data directly to disk
     * without loading the entire buffer into memory.
     * Used as fallback when OOM occurs during normal export.
     * 
     * NOTE: This method bypasses audio effects (noise suppression and normalization)
     * to minimize memory usage. This is a necessary tradeoff to complete the export
     * when memory is constrained.
     */
    private void exportMemoryEfficient(WavFileWriter writer, int skipBytes, int bytesToWrite, boolean useDisk) throws IOException {
        // Only called on audio thread - verify in both debug and production builds
        if (audioHandler.getLooper() != Looper.myLooper()) {
            throw new IllegalStateException("exportMemoryEfficient must be called on audio thread");
        }
        
        Log.d(TAG, "Starting memory-efficient export: skipBytes=" + skipBytes + ", bytesToWrite=" + bytesToWrite);
        Log.d(TAG, "Note: Audio effects (normalization/noise suppression) are bypassed in memory-efficient mode");
        
        final int[] totalWritten = new int[]{0};
        final int targetBytes = bytesToWrite;
        
        // Stream data in chunks directly to the writer
        AudioMemory.Consumer directWriter = new AudioMemory.Consumer() {
            @Override
            public int consume(byte[] array, int offset, int count) throws IOException {
                int remaining = targetBytes - totalWritten[0];
                if (remaining <= 0) return 0;
                
                int toWrite = Math.min(count, remaining);
                writer.write(array, offset, toWrite);
                totalWritten[0] += toWrite;
                
                // Periodically suggest GC to free up memory
                if (totalWritten[0] % (AudioMemory.CHUNK_SIZE * 10) == 0) {
                    System.gc();
                }
                
                return 0;
            }
        };
        
        // Read and write in streaming fashion
        if (useDisk) {
            diskAudioBuffer.read(skipBytes, directWriter);
        } else {
            audioMemory.read(skipBytes, directWriter);
        }
        
        Log.d(TAG, "Memory-efficient export completed: " + totalWritten[0] + " bytes written");
    }
    
    /**
     * Count the number of crash log files in the Echo/Crashes directory.
     * @return Number of crash logs
     */
    public int getCrashLogCount() {
        return CrashHandler.getCrashLogCount(this);
    }

}
