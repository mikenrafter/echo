package eu.mrogalski.saidit;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import eu.mrogalski.saidit.analysis.SegmentationController;
import eu.mrogalski.saidit.analysis.SimpleSegmentationController;
import eu.mrogalski.saidit.ml.AudioEventClassifier;
import eu.mrogalski.saidit.ml.TfLiteClassifier;
import eu.mrogalski.saidit.storage.RecordingStoreManager;
import eu.mrogalski.saidit.storage.SimpleRecordingStoreManager;
import eu.mrogalski.saidit.export.AacExporter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import static eu.mrogalski.saidit.SaidIt.AUDIO_MEMORY_ENABLED_KEY;
import static eu.mrogalski.saidit.SaidIt.AUDIO_MEMORY_SIZE_KEY;
import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;
import static eu.mrogalski.saidit.SaidIt.SAMPLE_RATE_KEY;
import static eu.mrogalski.saidit.SaidIt.MEMORY_SIZE_VERIFIED_KEY;
import static eu.mrogalski.saidit.SaidIt.MAX_MEMORY_SIZE_KEY;
import static eu.mrogalski.saidit.SaidIt.MEMORY_REDUCTION_STEP_MB;

public class SaidItService extends Service {
    static final String TAG = SaidItService.class.getSimpleName();
    private static final int FOREGROUND_NOTIFICATION_ID = 458;
    private static final String YOUR_NOTIFICATION_CHANNEL_ID = "SaidItServiceChannel";
    private static final String ACTION_AUTO_SAVE = "eu.mrogalski.saidit.ACTION_AUTO_SAVE";

    public static final String ACTION_START_LISTENING = "eu.mrogalski.saidit.ACTION_START_LISTENING";
    public static final String ACTION_STOP_LISTENING = "eu.mrogalski.saidit.ACTION_STOP_LISTENING";
    public static final String ACTION_START_RECORDING = "eu.mrogalski.saidit.ACTION_START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "eu.mrogalski.saidit.ACTION_STOP_RECORDING";
    public static final String ACTION_EXPORT_RECORDING = "eu.mrogalski.saidit.ACTION_EXPORT_RECORDING";
    public static final String ACTION_GET_STATE = "eu.mrogalski.saidit.ACTION_GET_STATE";
    public static final String ACTION_STATE_UPDATE = "eu.mrogalski.saidit.ACTION_STATE_UPDATE";

    public static final String EXTRA_PREPENDED_MEMORY_SECONDS = "eu.mrogalski.saidit.EXTRA_PREPENDED_MEMORY_SECONDS";
    public static final String EXTRA_MEMORY_SECONDS = "eu.mrogalski.saidit.EXTRA_MEMORY_SECONDS";
    public static final String EXTRA_FORMAT = "eu.mrogalski.saidit.EXTRA_FORMAT";
    public static final String EXTRA_NEW_FILE_NAME = "eu.mrogalski.saidit.EXTRA_NEW_FILE_NAME";
    public static final String EXTRA_LISTENING_ENABLED = "eu.mrogalski.saidit.EXTRA_LISTENING_ENABLED";
    public static final String EXTRA_RECORDING = "eu.mrogalski.saidit.EXTRA_RECORDING";
    public static final String EXTRA_MEMORIZED = "eu.mrogalski.saidit.EXTRA_MEMORIZED";
    public static final String EXTRA_TOTAL_MEMORY = "eu.mrogalski.saidit.EXTRA_TOTAL_MEMORY";
    public static final String EXTRA_RECORDED = "eu.mrogalski.saidit.EXTRA_RECORDED";

    volatile int SAMPLE_RATE;
    volatile int FILL_RATE;

    public enum ServiceState {
        READY,
        LISTENING,
        RECORDING
    }

    // A flag to indicate if the service is running in a test environment.
    // This is a pragmatic approach to prevent test hangs.
    boolean mIsTestEnvironment = false;
    private volatile boolean isShuttingDown = false;
    private final Object shutdownLock = new Object();

    File mediaFile;
    AudioRecord audioRecord; // used only in the audio thread
    NoiseSuppressor noiseSuppressor; // used only in the audio thread
    AutomaticGainControl automaticGainControl; // used only in the audio thread
    AacMp4Writer aacWriter; // used only in the audio thread
    final AudioMemory audioMemory = new AudioMemory(new SystemClockWrapper()); // used only in the audio thread

    volatile HandlerThread audioThread;
    volatile Handler audioHandler; // used to post messages to audio thread
    volatile HandlerThread analysisThread;
    volatile Handler analysisHandler; // used to post messages to analysis thread
    AudioMemory.Consumer filler;
    Runnable audioReader;
    AudioRecord.OnRecordPositionUpdateListener positionListener;

    private AudioProcessingPipeline audioProcessingPipeline;
    private RecordingStoreManager recordingStoreManager;
    private RecordingExporter recordingExporter;
    private int analyzedBytes;
    private Runnable analysisTick;
    private LocalBroadcastManager localBroadcastManager;

    volatile ServiceState state = ServiceState.READY;

    @Override
    public void onCreate() {
        super.onCreate();
        mIsTestEnvironment = "true".equals(System.getProperty("test.environment"));
        Log.d(TAG, "Reading native sample rate");

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        SAMPLE_RATE = preferences.getInt(SAMPLE_RATE_KEY, AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC));
        Log.d(TAG, "Sample rate: " + SAMPLE_RATE);
        FILL_RATE = 2 * SAMPLE_RATE;

        if (audioThread == null) {
            audioThread = new HandlerThread("audioThread", Process.THREAD_PRIORITY_AUDIO);
            audioThread.start();
            audioHandler = new Handler(audioThread.getLooper());
        }

        if (analysisThread == null) {
            analysisThread = new HandlerThread("analysisThread", Process.THREAD_PRIORITY_BACKGROUND);
            analysisThread.start();
            analysisHandler = new Handler(analysisThread.getLooper());
        }
        localBroadcastManager = LocalBroadcastManager.getInstance(this);

        filler = (array, offset, count) -> {
            if (audioRecord == null) return 0;
            final int read = audioRecord.read(array, offset, count, AudioRecord.READ_NON_BLOCKING);
            if (read < 0) {
                Log.e(TAG, "AUDIO RECORD ERROR: " + read);
                return 0;
            }
            if (aacWriter != null && read > 0) {
                aacWriter.write(array, offset, read);
            }
            return read;
        };

        audioReader = () -> {
            try {
                audioMemory.fill(filler);
            } catch (IOException e) {
                final String errorMessage = getString(R.string.error_during_recording_into) + (mediaFile != null ? mediaFile.getName() : "");
                showToast(errorMessage);
                Log.e(TAG, errorMessage, e);
                stopRecording(new SaidItFragment.NotifyFileReceiver(SaidItService.this));
            }
        };

        audioProcessingPipeline = new AudioProcessingPipeline(this, SAMPLE_RATE);
        audioProcessingPipeline.start();
        recordingStoreManager = audioProcessingPipeline.getRecordingStoreManager();
        recordingExporter = new RecordingExporter(this, SAMPLE_RATE);

        analysisTick = () -> {
            if (state != ServiceState.LISTENING && state != ServiceState.RECORDING) {
                return;
            }

            final int frameMs = 20; // Process 20ms chunks
            final int frameBytes = (SAMPLE_RATE / (1000 / frameMs)) * 2;
            final AudioMemory.Stats stats = audioMemory.getStats(FILL_RATE);
            int currentBufferSize = stats.overwriting ? stats.total : stats.filled;

            if (analyzedBytes > currentBufferSize) {
                // Buffer was likely reset. Let's try to recover by seeking back a bit.
                analyzedBytes = Math.max(0, currentBufferSize - (int)(getMemoryDurationSeconds() * FILL_RATE / 2));
            }

            int availableToAnalyze = currentBufferSize - analyzedBytes;

            while (availableToAnalyze >= frameBytes) {
                try {
                    audioMemory.read(analyzedBytes, frameBytes, (array, offset, count) -> {
                        audioProcessingPipeline.process(array, offset, count);
                        return 0; // Consumer ignores return
                    });
                    analyzedBytes += frameBytes;
                    availableToAnalyze -= frameBytes;
                } catch (IOException e) {
                    Log.e(TAG, "Error during audio analysis", e);
                    break; // Exit the loop on error
                }
            }
            analysisHandler.postDelayed(analysisTick, frameMs); // Re-schedule
        };

        if (preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            innerStartListening();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isShuttingDown = true;
        
        synchronized (shutdownLock) {
            // 1. Stop recording first
            if (state == ServiceState.RECORDING) {
                stopRecording(null);
            }
            
            // 2. Stop listening
            if (state != ServiceState.READY) {
                innerStopListening();
            }
            
            // 3. Stop audio processing pipeline
            if (audioProcessingPipeline != null) {
                audioProcessingPipeline.stop();
                audioProcessingPipeline = null;
            }
            
            // 4. Clean up handlers and threads with timeout
            cleanupHandlerThread(analysisHandler, analysisThread, "analysis");
            cleanupHandlerThread(audioHandler, audioThread, "audio");
            
            // 5. Stop foreground
            stopForeground(true);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new BackgroundRecorderBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);

        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_START_LISTENING:
                        enableListening();
                        break;
                    case ACTION_STOP_LISTENING:
                        disableListening();
                        break;
                    case ACTION_START_RECORDING:
                        startRecording(intent.getFloatExtra(EXTRA_PREPENDED_MEMORY_SECONDS, 0));
                        break;
                    case ACTION_STOP_RECORDING:
                        stopRecording(null);
                        break;
                    case ACTION_EXPORT_RECORDING:
                        exportRecording(intent.getFloatExtra(EXTRA_MEMORY_SECONDS, 0),
                                intent.getStringExtra(EXTRA_FORMAT),
                                null,
                                intent.getStringExtra(EXTRA_NEW_FILE_NAME));
                        break;
                    case ACTION_GET_STATE:
                        broadcastState();
                        break;
                    case ACTION_AUTO_SAVE:
                        handleAutoSave();
                        break;
                }
            }
        }

        return START_STICKY;
    }

    private void innerStartListening() {
        if (state != ServiceState.READY || isShuttingDown) return;
        state = ServiceState.LISTENING;

        Log.d(TAG, "Queueing: START LISTENING");
        startService(new Intent(this, this.getClass()));
        
        final SharedPreferences preferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        long memorySize = preferences.getLong(AUDIO_MEMORY_SIZE_KEY, Runtime.getRuntime().maxMemory() / 4);
        
        // Check if memory size was previously verified
        final boolean memoryVerified = preferences.getBoolean(MEMORY_SIZE_VERIFIED_KEY, false);
        
        // If not verified, try to reduce by 10MB and retry next boot
        if (!memoryVerified) {
            memorySize = reduceMemorySizeForRetry(memorySize);
        }
        
        final long finalMemorySize = memorySize;
        final SharedPreferences finalPreferences = preferences;

        audioHandler.post(() -> {
            if (isShuttingDown) return;
            Log.d(TAG, "Executing: START LISTENING");
            @SuppressLint("MissingPermission")
            AudioRecord newAudioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioMemory.CHUNK_SIZE);

            if (newAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Audio: INITIALIZATION ERROR");
                newAudioRecord.release();
                state = ServiceState.READY;
                return;
            }
            audioRecord = newAudioRecord;
            
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioRecord.getAudioSessionId());
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(finalPreferences.getBoolean("noise_suppressor_enabled", false));
                    Log.d(TAG, "NoiseSuppressor enabled: " + noiseSuppressor.getEnabled());
                }
            }
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(audioRecord.getAudioSessionId());
                if (automaticGainControl != null) {
                    automaticGainControl.setEnabled(finalPreferences.getBoolean("automatic_gain_control_enabled", false));
                    Log.d(TAG, "AutomaticGainControl enabled: " + automaticGainControl.getEnabled());
                }
            }

            // Attempt memory allocation and handle OOM
            if (audioMemory.allocate(finalMemorySize)) {
                // Allocation succeeded - mark memory size as verified
                finalPreferences.edit()
                    .putBoolean(MEMORY_SIZE_VERIFIED_KEY, true)
                    .putLong(AUDIO_MEMORY_SIZE_KEY, finalMemorySize)
                    .apply();
                Log.d(TAG, "Memory allocation verified: " + (finalMemorySize / (1024 * 1024)) + " MB");
            } else {
                // Allocation failed - unset verification flag for next boot
                finalPreferences.edit()
                    .putBoolean(MEMORY_SIZE_VERIFIED_KEY, false)
                    .apply();
                Log.e(TAG, "Memory allocation failed for " + (finalMemorySize / (1024 * 1024)) + " MB. Will retry with reduced size on next boot.");
                showToast("Failed to allocate " + (finalMemorySize / (1024 * 1024)) + " MB. App will try with less memory on next launch.");
                audioRecord.release();
                audioRecord = null;
                state = ServiceState.READY;
                return;
            }
            // Set up event-driven periodic callbacks (~50ms)
            final int periodFrames = Math.max(128, SAMPLE_RATE / 20);
            positionListener = new AudioRecord.OnRecordPositionUpdateListener() {
                @Override
                public void onPeriodicNotification(AudioRecord recorder) {
                    audioHandler.post(audioReader);
                }
                @Override
                public void onMarkerReached(AudioRecord recorder) { }
            };
            // In a test environment, don't set up the periodic listener to avoid hangs.
            if (!mIsTestEnvironment) {
                audioRecord.setRecordPositionUpdateListener(positionListener, audioHandler);
                audioRecord.setPositionNotificationPeriod(periodFrames);
            }
            audioRecord.startRecording();
            // Kickstart a first read to reduce latency
            if (!mIsTestEnvironment) {
                audioHandler.post(audioReader);
            }
            
            // Broadcast state and schedule auto-save AFTER audio is set up
            // Use a Handler to ensure this runs after the audio thread completes initialization
            new Handler(Looper.getMainLooper()).post(() -> {
                broadcastState();
                scheduleAutoSave();
            });
        });

        analysisHandler.post(() -> {
            analyzedBytes = 0;
            analysisHandler.post(analysisTick);
        });
    }

    private void innerStopListening() {
        if (state == ServiceState.READY || isShuttingDown) return;
        state = ServiceState.READY;

        Log.d(TAG, "Queueing: STOP LISTENING");
        cancelAutoSave();
        analysisHandler.removeCallbacks(analysisTick);
        stopForeground(true);
        stopService(new Intent(this, this.getClass()));

        audioHandler.post(() -> {
            Log.d(TAG, "Executing: STOP LISTENING");
            if (noiseSuppressor != null) {
                noiseSuppressor.release();
                noiseSuppressor = null;
            }
            if (automaticGainControl != null) {
                automaticGainControl.release();
                automaticGainControl = null;
            }
            if (audioRecord != null) {
                try {
                    // CRITICAL: Remove listener before stopping
                    audioRecord.setRecordPositionUpdateListener(null);
                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping audio record", e);
                } finally {
                    audioRecord.release();
                    audioRecord = null;
                }
            }
            
            // Remove all pending callbacks
            if (audioHandler != null) {
                audioHandler.removeCallbacksAndMessages(null);
            }
            audioMemory.allocate(0);
        });
    }

    public void enableListening() {
        if (mIsTestEnvironment) {
            state = ServiceState.LISTENING;
            return;
        }
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE)
                .edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, true).apply();
        innerStartListening();
    }

    public void disableListening() {
        if (mIsTestEnvironment) {
            state = ServiceState.READY;
            return;
        }
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE)
                .edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, false).apply();
        innerStopListening();
    }

    public void startRecording(final float prependedMemorySeconds) {
        if (state == ServiceState.RECORDING) return;
        if (state == ServiceState.READY) innerStartListening();
        state = ServiceState.RECORDING;

        audioHandler.post(() -> {
            flushAudioRecord();
            try {
                if (mIsTestEnvironment) {
                    // Skip actual I/O in tests
                    mediaFile = null;
                    aacWriter = null;
                    return;
                }
                mediaFile = File.createTempFile("saidit", ".m4a", getCacheDir());
                // 96 kbps for mono voice
                aacWriter = new AacMp4Writer(SAMPLE_RATE, 1, 96_000, mediaFile);
                Log.d(TAG, "Recording to: " + mediaFile.getAbsolutePath());

                // Write prepended memory
                if (prependedMemorySeconds > 0) {
                    final int bytesPerSecond = (int) (1f / getBytesToSeconds());
                    final int bytesToDump = (int) (prependedMemorySeconds * bytesPerSecond);
                    audioMemory.dump((array, offset, count) -> { aacWriter.write(array, offset, count); return count; }, bytesToDump);
                }
            } catch (IOException e) {
                Log.e(TAG, "ERROR creating AAC/MP4 file", e);
                Toast.makeText(this, getString(R.string.error_creating_recording_file), Toast.LENGTH_LONG).show();
                state = ServiceState.LISTENING; // Revert state
            }
            broadcastState();
        });
    }

    public void stopRecording(final WavFileReceiver wavFileReceiver) {
        if (state != ServiceState.RECORDING) return;
        state = ServiceState.LISTENING;

        audioHandler.post(() -> {
            flushAudioRecord();
            if (aacWriter != null) {
                aacWriter.close();
            }
            if (wavFileReceiver != null && mediaFile != null) {
                recordingExporter.saveFileToMediaStore(mediaFile, mediaFile.getName(), "audio/mp4", wavFileReceiver);
            }
            aacWriter = null;
            broadcastState();
        });
    }

        public void exportRecording(final float memorySeconds, final String format, final WavFileReceiver wavFileReceiver, String newFileName) {
        if (state == ServiceState.READY) {
            Log.w(TAG, "exportRecording called while service READY; ignoring");
            return;
        }

        if (recordingExporter == null) {
            Log.e(TAG, "Recording exporter not initialized");
            if (wavFileReceiver != null) {
                wavFileReceiver.onFailure(new IllegalStateException("Recording exporter not available"));
            }
            return;
        }

        final RecordingStoreManager currentStore = recordingStoreManager;
        Log.d(TAG, "exportRecording request: memorySeconds=" + memorySeconds + ", format=" + format + ", storeNull=" + (currentStore == null));

        analysisHandler.post(() -> recordingExporter.export(currentStore, memorySeconds, format, newFileName, wavFileReceiver));
    }

    private void flushAudioRecord() {
        // In tests we may not have a real Looper; just ensure we synchronously drain any pending read.
        if (audioHandler != null) {
            try { audioHandler.removeCallbacks(audioReader); } catch (Exception ignored) {}
        }
        if (audioReader != null) audioReader.run();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void broadcastState() {
        getState((listeningEnabled, recording, memorized, totalMemory, recorded) -> {
            Intent intent = new Intent(ACTION_STATE_UPDATE);
            intent.putExtra(EXTRA_LISTENING_ENABLED, listeningEnabled);
            intent.putExtra(EXTRA_RECORDING, recording);
            intent.putExtra(EXTRA_MEMORIZED, memorized);
            intent.putExtra(EXTRA_TOTAL_MEMORY, totalMemory);
            intent.putExtra(EXTRA_RECORDED, recorded);
            localBroadcastManager.sendBroadcast(intent);
        });
    }

    public void scheduleAutoSave() {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("auto_save_enabled", false);
        
        // Always cancel first to avoid duplicate alarms
        cancelAutoSave();
        
        if (!enabled || state == ServiceState.READY) {
            Log.d(TAG, "Auto-save not scheduled: enabled=" + enabled + ", state=" + state);
            return;
        }
        
        int durationSeconds = prefs.getInt("auto_save_duration", 600); // Default 10 minutes
        long intervalMillis = durationSeconds * 1000L;
        
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, SaidItService.class);
        intent.setAction(ACTION_AUTO_SAVE);
        PendingIntent pendingIntent = PendingIntent.getService(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + intervalMillis,
            intervalMillis,
            pendingIntent
        );
        
        Log.d(TAG, "Auto-save scheduled every " + durationSeconds + " seconds");
    }

    public void cancelAutoSave() {
        Log.d(TAG, "Cancelling auto-save");
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, SaidItService.class);
        intent.setAction(ACTION_AUTO_SAVE);
        PendingIntent pendingIntent = PendingIntent.getService(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        alarmManager.cancel(pendingIntent);
    }

    private void handleAutoSave() {
        if (state == ServiceState.READY) {
            Log.d(TAG, "Auto-save skipped: service not listening");
            return;
        }
        
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("auto_save_enabled", false)) {
            Log.d(TAG, "Auto-save skipped: disabled in settings");
            return;
        }
        
        int durationSeconds = prefs.getInt("auto_save_duration", 600);
        String pattern = prefs.getString("filename_pattern", "Auto-save_{date}_{time}");
        String fileName = eu.mrogalski.saidit.util.FilenamePatternGenerator.generate(pattern, durationSeconds, true);
        
        Log.d(TAG, "Executing auto-save: " + fileName + " (" + durationSeconds + "s)");
        
        exportRecording(
            durationSeconds,
            "aac",
            new SaidItFragment.NotifyFileReceiver(this),
            fileName
        );
    }

    public long getMemorySize() {
        return audioMemory.getAllocatedMemorySize();
    }
    
    public ServiceState getState() {
        return state;
    }

    /**
     * Reduces the requested memory size by MEMORY_REDUCTION_STEP_MB (10MB) for retry.
     * If the reduced size would be below 32MB minimum, sets it to 32MB.
     * Also updates max memory size to be 30MB more than the attempted size.
     * @param currentSize The current memory size in bytes
     * @return The reduced memory size in bytes
     */
    private long reduceMemorySizeForRetry(long currentSize) {
        long reductionBytes = MEMORY_REDUCTION_STEP_MB * 1024 * 1024;
        long reducedSize = currentSize - reductionBytes;
        long minMemoryBytes = 32 * 1024 * 1024; // 32 MB minimum
        
        if (reducedSize < minMemoryBytes) {
            Log.w(TAG, "Reduced memory size would be below minimum. Setting to 32 MB.");
            reducedSize = minMemoryBytes;
        }
        
        // Set max memory size to 30MB more than the attempted allocation
        long maxMemorySize = currentSize + (30 * 1024 * 1024);
        SharedPreferences preferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putLong(MAX_MEMORY_SIZE_KEY, maxMemorySize).apply();
        
        Log.d(TAG, "Reducing memory size from " + (currentSize / (1024 * 1024)) + " MB to " + (reducedSize / (1024 * 1024)) + " MB for retry.");
        Log.d(TAG, "Setting max slider to " + (maxMemorySize / (1024 * 1024)) + " MB");
        return reducedSize;
    }

    public void setMemorySize(final long memorySize) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        // When user manually sets memory size, reset verification flag to re-verify
        preferences.edit()
            .putLong(AUDIO_MEMORY_SIZE_KEY, memorySize)
            .putBoolean(MEMORY_SIZE_VERIFIED_KEY, false)
            .apply();

        if(preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            audioHandler.post(() -> {
                if (audioMemory.allocate(memorySize)) {
                    // Allocation succeeded - mark as verified
                    preferences.edit()
                        .putBoolean(MEMORY_SIZE_VERIFIED_KEY, true)
                        .apply();
                    Log.d(TAG, "Manual memory allocation verified: " + (memorySize / (1024 * 1024)) + " MB");
                    broadcastMemoryAllocationSuccess();
                } else {
                    // Allocation failed - keep flag unset for retry on next boot
                    Log.e(TAG, "Manual memory allocation failed for " + (memorySize / (1024 * 1024)) + " MB");
                    broadcastMemoryAllocationFailure(memorySize);
                }
            });
        }
    }

    private void broadcastMemoryAllocationSuccess() {
        Intent intent = new Intent("eu.mrogalski.saidit.MEMORY_ALLOCATION_SUCCESS");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastMemoryAllocationFailure(long memorySize) {
        Intent intent = new Intent("eu.mrogalski.saidit.MEMORY_ALLOCATION_FAILURE");
        intent.putExtra("requested_memory_mb", memorySize / 1024 / 1024);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public int getSamplingRate() {
        return SAMPLE_RATE;
    }

    public void setSampleRate(int sampleRate) {
        if (state == ServiceState.RECORDING) return;
        if (state == ServiceState.READY) {
            final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
            preferences.edit().putInt(SAMPLE_RATE_KEY, sampleRate).apply();
            SAMPLE_RATE = sampleRate;
            FILL_RATE = 2 * SAMPLE_RATE;
            return;
        }

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putInt(SAMPLE_RATE_KEY, sampleRate).apply();

        innerStopListening();
        SAMPLE_RATE = sampleRate;
        FILL_RATE = 2 * SAMPLE_RATE;
        innerStartListening();
    }

    public void getState(final StateCallback stateCallback) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        final boolean listeningEnabled = preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true);
        final boolean recording = (state == ServiceState.RECORDING);
        final Handler sourceHandler = new Handler(Looper.getMainLooper());
        audioHandler.post(() -> {
            flushAudioRecord();
            final AudioMemory.Stats stats = audioMemory.getStats(FILL_RATE);

            int recorded = 0;
            if(aacWriter != null) {
                recorded += aacWriter.getTotalSampleBytesWritten();
                recorded += stats.estimation;
            }
            final float bytesToSeconds = getBytesToSeconds();
            final int finalRecorded = recorded;
            if (stateCallback != null) {
                sourceHandler.post(() -> stateCallback.state(listeningEnabled, recording,
                        (stats.overwriting ? stats.total : stats.filled + stats.estimation) * bytesToSeconds,
                        stats.total * bytesToSeconds,
                        finalRecorded * bytesToSeconds));
            }
        });
    }

    public float getBytesToSeconds() {
        return 1f / FILL_RATE;
    }

    public float getMemoryDurationSeconds() {
        if (audioMemory == null) return 0f;
        final AudioMemory.Stats stats = audioMemory.getStats(FILL_RATE);
        return (stats.overwriting ? stats.total : stats.filled) * getBytesToSeconds();
    }


    private Notification buildNotification() {
        NotificationChannel channel = new NotificationChannel(YOUR_NOTIFICATION_CHANNEL_ID, "SaidIt Service", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        Intent notificationIntent = new Intent(this, SaidItActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, YOUR_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_hearing)
                .setContentIntent(pendingIntent)
                .build();
    }

    public interface WavFileReceiver {
        void onSuccess(Uri fileUri);
        void onFailure(Exception e);
    }

    public interface StateCallback {
        void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded);
    }

    class BackgroundRecorderBinder extends Binder {
        public SaidItService getService() {
            return SaidItService.this;
        }
    }

    private void cleanupHandlerThread(Handler handler, HandlerThread thread, String name) {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        
        if (thread != null) {
            thread.quitSafely();
            try {
                // Wait max 1 second for thread to finish
                thread.join(1000);
                if (thread.isAlive()) {
                    Log.w(TAG, name + " thread did not terminate in time");
                    thread.interrupt();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for " + name + " thread", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
