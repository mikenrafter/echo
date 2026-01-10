package eu.mrogalski.saidit;

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
    WavFileWriter wavFileWriter; // used only in the audio thread
    final AudioMemory audioMemory = new AudioMemory(); // used only in the audio thread
    DiskAudioBuffer diskAudioBuffer; // used only in the audio thread
    volatile StorageMode storageMode = StorageMode.MEMORY_ONLY;
    
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

    HandlerThread audioThread;
    Handler audioHandler; // used to post messages to audio thread

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
        int silenceThreshold = preferences.getInt(SILENCE_THRESHOLD_KEY, 500);
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

                audioRecord = new AudioRecord(
                       MediaRecorder.AudioSource.MIC,
                       SAMPLE_RATE,
                       AudioFormat.CHANNEL_IN_MONO,
                       AudioFormat.ENCODING_PCM_16BIT,
                       AudioMemory.CHUNK_SIZE);

                if(audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Audio: INITIALIZATION ERROR - releasing resources");
                    audioRecord.release();
                    audioRecord = null;
                    state = STATE_READY;
                    return;
                }

                Log.d(TAG, "Audio: STARTING AudioRecord");
                audioMemory.allocate(memorySize);
                
                // Initialize disk buffer if in BATCH_TO_DISK mode
                if (storageMode == StorageMode.BATCH_TO_DISK) {
                    initializeDiskBuffer();
                }

                Log.d(TAG, "Audio: STARTING AudioRecord");
                audioRecord.startRecording();
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
                if(audioRecord != null)
                    audioRecord.release();
                audioHandler.removeCallbacks(audioReader);
                audioMemory.allocate(0);
                
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
                    } catch (IOException e) {
                        showToast(getString(R.string.error_during_writing_history_into) + file.getAbsolutePath());
                        Log.e(TAG, "Error during writing history into " + file.getAbsolutePath(), e);
                    }
                } catch (IOException e) {
                    showToast(getString(R.string.cant_create_file) + file.getAbsolutePath());
                    Log.e(TAG, "Can't create file " + file.getAbsolutePath(), e);
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

    private void flushAudioRecord() {
        // Only allowed on the audio thread
        assert audioHandler.getLooper() == Looper.myLooper();
        audioHandler.removeCallbacks(audioReader); // remove any delayed callbacks
        audioReader.run();
    }

    final AudioMemory.Consumer filler = new AudioMemory.Consumer() {
        @Override
        public int consume(final byte[] array, final int offset, final int count) throws IOException {
//            Log.d(TAG, "READING " + count + " B");
            final int read = audioRecord.read(array, offset, count, AudioRecord.READ_NON_BLOCKING);
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
                audioMemory.fill(filler);
            } catch (IOException e) {
                final String errorMessage = getString(R.string.error_during_recording_into) + wavFile.getName();
                Toast.makeText(SaidItService.this, errorMessage, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorMessage, e);
                stopRecording(new SaidItFragment.NotifyFileReceiver(SaidItService.this), "");
            }
        }
    };

    public interface StateCallback {
        public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded, float skippedSeconds);
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
                final AudioMemory.Stats stats = audioMemory.getStats(FILL_RATE);
                
                int recorded = 0;
                if(wavFileWriter != null) {
                    recorded += wavFileWriter.getTotalSampleBytesWritten();
                    recorded += stats.estimation;
                }
                final float bytesToSeconds = getBytesToSeconds();
                final int finalRecorded = recorded;
                // Calculate skipped seconds: each segment is CHUNK_SIZE bytes
                final float skippedSeconds = stats.skippedSegments * AudioMemory.CHUNK_SIZE * bytesToSeconds;
                sourceHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        stateCallback.state(listeningEnabled, recording,
                                (stats.overwriting ? stats.total : stats.filled + stats.estimation) * bytesToSeconds,
                                stats.total * bytesToSeconds,
                                finalRecorded * bytesToSeconds,
                                skippedSeconds);
                    }
                });
            }
        });
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
        
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
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

}
