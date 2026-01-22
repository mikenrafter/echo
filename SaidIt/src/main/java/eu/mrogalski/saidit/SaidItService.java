package eu.mrogalski.saidit;

import static eu.mrogalski.saidit.SaidIt.AUDIO_MEMORY_ENABLED_KEY;
import static eu.mrogalski.saidit.SaidIt.AUDIO_MEMORY_SIZE_KEY;
import static eu.mrogalski.saidit.SaidIt.AUTO_SAVE_DURATION_KEY;
import static eu.mrogalski.saidit.SaidIt.AUTO_SAVE_ENABLED_KEY;
import static eu.mrogalski.saidit.SaidIt.DEVICE_CHANNEL_MODE_KEY;
import static eu.mrogalski.saidit.SaidIt.DUAL_SOURCE_RECORDING_KEY;
import static eu.mrogalski.saidit.SaidIt.MIC_CHANNEL_MODE_KEY;
import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;
import static eu.mrogalski.saidit.SaidIt.RECORD_DEVICE_AUDIO_KEY;
import static eu.mrogalski.saidit.SaidIt.SAMPLE_RATE_KEY;
import static eu.mrogalski.saidit.SaidIt.STORAGE_MODE_KEY;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.Collections;
import java.util.List;

import eu.mrogalski.saidit.R;
import eu.mrogalski.saidit.features.audiocapture.services.AudioCaptureService;
import eu.mrogalski.saidit.features.audiocapture.services.StorageManagementService;
import eu.mrogalski.saidit.features.audioexport.services.RecordingExportService;
import eu.mrogalski.saidit.shared.activities.SaidItActivity;
import eu.mrogalski.saidit.shared.models.SilenceGroup;
import eu.mrogalski.saidit.shared.models.StorageMode;
import eu.mrogalski.saidit.shared.models.TimelineSegment;
import eu.mrogalski.saidit.shared.services.CrashHandler;

public class SaidItService extends Service {
    private static final String TAG = SaidItService.class.getSimpleName();
    private static final String CHANNEL_ID = "SaidItServiceChannel";
    private static final int FOREGROUND_ID = 458;

    private HandlerThread audioThread;
    private Handler audioHandler;
    private StorageManagementService storageService;
    private AudioCaptureService audioCaptureService;
    private RecordingExportService recordingExportService;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private int sampleRate;
    private int fillRate;
    private boolean recording;
    private long recordingStartMillis;
    private float recordingPrependSeconds;
    private PendingIntent autoSavePendingIntent;
    private PendingIntent autoSaveCleanupPendingIntent;

    public interface WavFileReceiver { void fileReady(File file, float runtime); }
    public interface StateCallback { void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded, float skippedSeconds); }
    public interface LiveStatsCallback { void stats(int volumeLevel, int skippedGroups); }
    public interface SilenceGroupsCallback { void onGroups(List<SilenceGroup> groups); }
    public interface TimelineCallback { void onTimeline(List<TimelineSegment> segments, TimelineSegment currentSegment, float totalMemorySeconds); }
    public interface MediaProjectionRequestCallback { void onRequestMediaProjection(); }

    private MediaProjectionRequestCallback mediaProjectionRequestCallback;

    @Override public void onCreate() {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        sampleRate = prefs.getInt(SAMPLE_RATE_KEY, 48000);
        fillRate = sampleRate * 2;
        mediaProjectionManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE) : null;
        audioThread = new HandlerThread("audioThread", Thread.MAX_PRIORITY);
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());
        storageService = new StorageManagementService(this, audioHandler);
        rebuildCaptureAndExport(false);
        if (prefs.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) enableListening();
    }

    @Override public void onDestroy() {
        recording = false;
        if (audioCaptureService != null) audioCaptureService.stopListening();
        if (storageService != null) storageService.shutdown();
        stopForeground(true);
        if (audioThread != null) audioThread.quitSafely();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundIfNeeded();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return new BackgroundRecorderBinder(); }
    public class BackgroundRecorderBinder extends Binder { public SaidItService getService() { return SaidItService.this; } }

    public void enableListening() {
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE).edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, true).apply();
        if (audioCaptureService != null) {
            audioCaptureService.startListening();
            startForegroundIfNeeded();
        }
    }

    public void disableListening() {
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE).edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, false).apply();
        if (audioCaptureService != null) audioCaptureService.stopListening();
        stopForeground(true);
    }

    public void startRecording(final float prependSeconds) {
        recording = true;
        recordingStartMillis = System.currentTimeMillis();
        recordingPrependSeconds = Math.max(0f, prependSeconds);
        if (audioCaptureService != null && !audioCaptureService.isListening()) enableListening();
        startForegroundIfNeeded();
    }

    public void stopRecording(final WavFileReceiver receiver, String newFileName) {
        if (!recording) return;
        float elapsed = (System.currentTimeMillis() - recordingStartMillis) / 1000f;
        float startAgo = recordingPrependSeconds + elapsed;
        recording = false;
        dumpRecordingRange(startAgo, 0f, receiver, newFileName);
        startForegroundIfNeeded();
    }

    public void dumpRecording(final float memorySeconds, final WavFileReceiver receiver, String newFileName) { dumpRecordingRange(memorySeconds, 0f, receiver, newFileName); }

    public void dumpRecordingRange(final float startSecondsAgo, final float endSecondsAgo, final WavFileReceiver receiver, final String newFileName) {
        updateExportStorageState();
        if (recordingExportService != null) recordingExportService.dumpRecordingRange(startSecondsAgo, endSecondsAgo, receiver, newFileName);
    }

    public long getMemorySize() { return storageService.getMemorySize(); }
    public void setMemorySize(final long memorySize) { storageService.setMemorySize(memorySize); }
    public int getMemorySizeMB() { return storageService.getMemorySizeMB(); }
    public void setMemorySizeMB(final int memorySizeMB) { storageService.setMemorySizeMB(memorySizeMB); }
    public StorageMode getStorageMode() { return storageService.getStorageMode(); }
    public void setStorageMode(final StorageMode mode) { storageService.setStorageMode(mode); updateExportStorageState(); }

    public int getSamplingRate() { return sampleRate; }
    public void setSampleRate(int newRate) {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        prefs.edit().putInt(SAMPLE_RATE_KEY, newRate).apply();
        sampleRate = newRate;
        fillRate = sampleRate * 2;
        boolean restart = audioCaptureService != null && audioCaptureService.isListening();
        rebuildCaptureAndExport(restart);
    }

    public float getBytesToSeconds() { return 1f / fillRate; }
    public void configureSilenceSkipping(final boolean enabled, final int threshold, final int segmentCount) { storageService.configureSilenceSkipping(enabled, threshold, segmentCount); }
    public void setDeviceAudioRecording(final boolean enabled) { getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE).edit().putBoolean(RECORD_DEVICE_AUDIO_KEY, enabled).apply(); rebuildCaptureAndExport(audioCaptureService != null && audioCaptureService.isListening()); }
    public void setDualSourceRecording(final boolean enabled) { getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE).edit().putBoolean(DUAL_SOURCE_RECORDING_KEY, enabled).apply(); rebuildCaptureAndExport(audioCaptureService != null && audioCaptureService.isListening()); }
    public void setMicChannelMode(final int mode) { getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE).edit().putInt(MIC_CHANNEL_MODE_KEY, mode).apply(); rebuildCaptureAndExport(audioCaptureService != null && audioCaptureService.isListening()); }
    public void setDeviceChannelMode(final int mode) { getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE).edit().putInt(DEVICE_CHANNEL_MODE_KEY, mode).apply(); rebuildCaptureAndExport(audioCaptureService != null && audioCaptureService.isListening()); }

    public boolean initializeMediaProjection(int resultCode, Intent data) {
        if (mediaProjectionManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
        if (audioCaptureService != null) audioCaptureService.setMediaProjection(mediaProjection);
        return mediaProjection != null;
    }

    public boolean isMediaProjectionRequired() { return audioCaptureService != null && audioCaptureService.isMediaProjectionRequired(); }

    @Deprecated public void setMediaProjection(MediaProjection projection) { mediaProjection = projection; if (audioCaptureService != null) audioCaptureService.setMediaProjection(projection); }

    // Legacy APIs kept for compatibility (no-op in refactor)
    public void configureActivityDetection(final boolean enabled, final float threshold, final int preBufferSeconds, final int postBufferSeconds, final int autoDeleteDays, final boolean highBitrate) { Log.d(TAG, "configureActivityDetection noop"); }
    public void setVadTimeWindowEnabled(final boolean enabled) { Log.d(TAG, "setVadTimeWindowEnabled noop"); }
    public void setVadTimeWindow(final int startHour, final int startMinute, final int endHour, final int endMinute) { Log.d(TAG, "setVadTimeWindow noop"); }
    public void setupScheduledRecording(final long startTimeMillis, final long endTimeMillis, final String filename) { Log.d(TAG, "setupScheduledRecording noop"); }

    public void getState(final StateCallback cb) {
        if (cb == null) return;
        boolean listening = audioCaptureService != null && audioCaptureService.isListening();
        float memorized = storageService.getAudioMemory().countFilled() / (float) fillRate;
        float totalMemory = storageService.getAudioMemory().getAllocatedMemorySize() / (float) fillRate;
        float recorded = recording ? (System.currentTimeMillis() - recordingStartMillis) / 1000f : 0f;
        int skipped = storageService.getAudioMemory().getStats(fillRate).skippedSegments;
        cb.state(listening, recording, memorized, totalMemory, recorded, skipped * getBytesToSeconds());
    }

    public void getLiveStats(final LiveStatsCallback cb) {
        if (cb != null && audioCaptureService != null) cb.stats(audioCaptureService.getCurrentVolumeLevel(), storageService.getAudioMemory().getStats(fillRate).skippedSegments);
    }

    public void getSilenceGroups(final SilenceGroupsCallback cb) { if (cb != null) cb.onGroups(Collections.<SilenceGroup>emptyList()); }
    public void getTimeline(final TimelineCallback cb) { if (cb != null) cb.onTimeline(Collections.<TimelineSegment>emptyList(), null, storageService.getAudioMemory().getAllocatedMemorySize() / (float) fillRate); }

    public void setMediaProjectionRequestCallback(MediaProjectionRequestCallback callback) { this.mediaProjectionRequestCallback = callback; }

    private void rebuildCaptureAndExport(boolean restartListening) {
        if (audioCaptureService != null && audioCaptureService.isListening()) audioCaptureService.stopListening();
        audioCaptureService = new AudioCaptureService(this, audioHandler, storageService, sampleRate);
        audioCaptureService.setMediaProjection(mediaProjection);
        recordingExportService = new RecordingExportService(this, audioHandler, storageService.getAudioMemory(), sampleRate, new RecordingExportService.FlushCallback() { @Override public void flush() { audioCaptureService.flushAudioRecord(); } });
        updateExportStorageState();
        if (restartListening) audioCaptureService.startListening();
    }

    private void updateExportStorageState() {
        if (recordingExportService != null) {
            recordingExportService.setStorageMode(storageService.getStorageMode());
            recordingExportService.setDiskAudioBuffer(storageService.getDiskAudioBuffer());
        }
    }

    private void startForegroundIfNeeded() {
        if (audioCaptureService == null || !audioCaptureService.isListening()) return;
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            if (mediaProjection != null) serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            startForeground(FOREGROUND_ID, notification, serviceType);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    private Notification buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "SaidIt", NotificationManager.IMPORTANCE_LOW));
        Intent intent = new Intent(this, SaidItActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SaidIt is listening")
                .setSmallIcon(R.drawable.ic_stat_notify_recording)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    public void scheduleAutoSave() {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(AUTO_SAVE_ENABLED_KEY, false);
        cancelAutoSave();
        if (!enabled || (audioCaptureService != null && !audioCaptureService.isListening())) return;
        int durationSeconds = prefs.getInt(AUTO_SAVE_DURATION_KEY, 600);
        long intervalMillis = durationSeconds * 1000L;
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, SaidItService.class);
        intent.setAction("eu.mrogalski.saidit.ACTION_AUTO_SAVE");
        autoSavePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + intervalMillis, intervalMillis, autoSavePendingIntent);
        Log.d(TAG, "Auto-save scheduled every " + durationSeconds + " seconds");
    }

    public void cancelAutoSave() {
        if (autoSavePendingIntent != null) {
            Log.d(TAG, "Cancelling auto-save");
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarmManager.cancel(autoSavePendingIntent);
            autoSavePendingIntent = null;
        }
    }

    public void scheduleAutoSaveCleanup() {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        cancelAutoSaveCleanup();
        if (audioCaptureService != null && !audioCaptureService.isListening()) return;
        long dailyIntervalMillis = 24L * 60L * 60L * 1000L;
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, SaidItService.class);
        intent.setAction("eu.mrogalski.saidit.ACTION_AUTO_SAVE_CLEANUP");
        autoSaveCleanupPendingIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + dailyIntervalMillis, dailyIntervalMillis, autoSaveCleanupPendingIntent);
        Log.d(TAG, "Auto-save cleanup scheduled daily");
    }

    public void cancelAutoSaveCleanup() {
        if (autoSaveCleanupPendingIntent != null) {
            Log.d(TAG, "Cancelling auto-save cleanup");
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarmManager.cancel(autoSaveCleanupPendingIntent);
            autoSaveCleanupPendingIntent = null;
        }
    }

    public int getCrashLogCount() { return CrashHandler.getCrashLogCount(this); }
}
