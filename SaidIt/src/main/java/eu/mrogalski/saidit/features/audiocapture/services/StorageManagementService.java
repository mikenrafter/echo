package eu.mrogalski.saidit.features.audiocapture.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import eu.mrogalski.saidit.SaidIt;
import eu.mrogalski.saidit.features.audiocapture.services.AudioMemory;
import eu.mrogalski.saidit.features.audioexport.services.DiskAudioBuffer;
import eu.mrogalski.saidit.shared.models.StorageMode;

/**
 * Service responsible for storage management.
 * 
 * Responsibilities:
 * - Memory allocation for audio rings
 * - Disk buffer management
 * - Storage mode switching
 * - Silence skipping configuration
 * - Gradient quality memory ring allocation
 * 
 * This service manages the memory rings that AudioCaptureService uses.
 */
public class StorageManagementService {
    private static final String TAG = StorageManagementService.class.getSimpleName();
    
    private final Context context;
    private final Handler audioHandler;
    
    // Memory rings
    private final AudioMemory audioMemory;
    private final AudioMemory audioMemoryHigh;
    private final AudioMemory audioMemoryMid;
    private final AudioMemory audioMemoryLow;
    
    // Disk buffer
    private DiskAudioBuffer diskAudioBuffer;
    
    // Configuration
    private StorageMode storageMode;
    private boolean gradientQualityEnabled;
    private int gradientQualityHighRate;
    private int gradientQualityMidRate;
    private int gradientQualityLowRate;
    private int silenceThreshold;
    
    public StorageManagementService(Context context, Handler audioHandler) {
        this.context = context;
        this.audioHandler = audioHandler;
        
        // Initialize memory rings
        this.audioMemory = new AudioMemory();
        this.audioMemoryHigh = new AudioMemory();
        this.audioMemoryMid = new AudioMemory();
        this.audioMemoryLow = new AudioMemory();
        
        loadConfiguration();
    }
    
    /**
     * Load configuration from SharedPreferences.
     */
    private void loadConfiguration() {
        final SharedPreferences prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        
        // Load storage mode
        String modeStr = prefs.getString(SaidIt.STORAGE_MODE_KEY, StorageMode.MEMORY_ONLY.name());
        try {
            storageMode = StorageMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            storageMode = StorageMode.MEMORY_ONLY;
        }
        Log.d(TAG, "Storage mode: " + storageMode);
        
        // Load and configure silence skipping
        boolean silenceSkipEnabled = prefs.getBoolean(SaidIt.SILENCE_SKIP_ENABLED_KEY, false);
        silenceThreshold = prefs.getInt(SaidIt.SILENCE_THRESHOLD_KEY, 500);
        int silenceSegmentCount = prefs.getInt(SaidIt.SILENCE_SEGMENT_COUNT_KEY, 3);
        audioMemory.configureSilenceSkipping(silenceSkipEnabled, silenceThreshold, silenceSegmentCount);
        Log.d(TAG, "Silence skipping: enabled=" + silenceSkipEnabled + ", threshold=" + silenceThreshold + ", segmentCount=" + silenceSegmentCount);
        
        // Load gradient quality preferences
        gradientQualityEnabled = prefs.getBoolean(SaidIt.GRADIENT_QUALITY_ENABLED_KEY, false);
        gradientQualityHighRate = prefs.getInt(SaidIt.GRADIENT_QUALITY_HIGH_RATE_KEY, 48000);
        gradientQualityMidRate = prefs.getInt(SaidIt.GRADIENT_QUALITY_MID_RATE_KEY, 16000);
        gradientQualityLowRate = prefs.getInt(SaidIt.GRADIENT_QUALITY_LOW_RATE_KEY, 8000);
        Log.d(TAG, "Gradient quality: enabled=" + gradientQualityEnabled + 
            ", rates=" + gradientQualityHighRate + "/" + gradientQualityMidRate + "/" + gradientQualityLowRate);
    }
    
    /**
     * Get main audio memory ring.
     */
    public AudioMemory getAudioMemory() {
        return audioMemory;
    }
    
    /**
     * Get high quality memory ring.
     */
    public AudioMemory getAudioMemoryHigh() {
        return audioMemoryHigh;
    }
    
    /**
     * Get mid quality memory ring.
     */
    public AudioMemory getAudioMemoryMid() {
        return audioMemoryMid;
    }
    
    /**
     * Get low quality memory ring.
     */
    public AudioMemory getAudioMemoryLow() {
        return audioMemoryLow;
    }
    
    /**
     * Get disk audio buffer (may be null if not in disk mode).
     */
    public DiskAudioBuffer getDiskAudioBuffer() {
        return diskAudioBuffer;
    }
    
    /**
     * Get current storage mode.
     */
    public StorageMode getStorageMode() {
        return storageMode;
    }
    
    /**
     * Set storage mode.
     */
    public void setStorageMode(final StorageMode mode) {
        final SharedPreferences prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(SaidIt.STORAGE_MODE_KEY, mode.name()).commit();
        
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
    
    /**
     * Get allocated memory size.
     */
    public long getMemorySize() {
        return audioMemory.getAllocatedMemorySize();
    }
    
    /**
     * Set memory size.
     */
    public void setMemorySize(final long memorySize) {
        final SharedPreferences prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(SaidIt.AUDIO_MEMORY_SIZE_KEY, memorySize).commit();

        if (prefs.getBoolean(SaidIt.AUDIO_MEMORY_ENABLED_KEY, true)) {
            audioHandler.post(new Runnable() {
                @Override
                public void run() {
                    audioMemory.allocate(memorySize);
                }
            });
        }
    }
    
    /**
     * Get memory size in MB.
     */
    public int getMemorySizeMB() {
        return (int) (getMemorySize() / (1024 * 1024));
    }
    
    /**
     * Set memory size in MB.
     */
    public void setMemorySizeMB(final int memorySizeMB) {
        final long memorySize = memorySizeMB * 1024L * 1024L;
        setMemorySize(memorySize);
    }
    
    /**
     * Configure silence skipping.
     */
    public void configureSilenceSkipping(final boolean enabled, final int threshold, final int segmentCount) {
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                audioMemory.configureSilenceSkipping(enabled, threshold, segmentCount);
                if (gradientQualityEnabled) {
                    audioMemoryHigh.configureSilenceSkipping(enabled, threshold, segmentCount);
                    audioMemoryMid.configureSilenceSkipping(enabled, threshold, segmentCount);
                    audioMemoryLow.configureSilenceSkipping(enabled, threshold, segmentCount);
                }
                Log.d(TAG, "Silence skipping configured: enabled=" + enabled + ", threshold=" + threshold + ", segmentCount=" + segmentCount);
            }
        });
        
        // Save to preferences
        final SharedPreferences prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(SaidIt.SILENCE_SKIP_ENABLED_KEY, enabled)
            .putInt(SaidIt.SILENCE_THRESHOLD_KEY, threshold)
            .putInt(SaidIt.SILENCE_SEGMENT_COUNT_KEY, segmentCount)
            .apply();
        
        silenceThreshold = threshold;
    }
    
    /**
     * Check if gradient quality is enabled.
     */
    public boolean isGradientQualityEnabled() {
        return gradientQualityEnabled;
    }
    
    /**
     * Get gradient quality rates.
     */
    public int getGradientQualityHighRate() {
        return gradientQualityHighRate;
    }
    
    public int getGradientQualityMidRate() {
        return gradientQualityMidRate;
    }
    
    public int getGradientQualityLowRate() {
        return gradientQualityLowRate;
    }
    
    /**
     * Initialize disk buffer (called on audio thread).
     */
    private void initializeDiskBuffer() {
        // Only called on audio thread
        assert audioHandler.getLooper() == Looper.myLooper();
        
        final SharedPreferences prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        long maxDiskUsageMB = prefs.getLong(SaidIt.MAX_DISK_USAGE_MB_KEY, 500); // Default 500 MB
        long maxDiskUsageBytes = maxDiskUsageMB * 1024L * 1024L;
        
        File storageDir;
        if (isExternalStorageWritable()) {
            storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Echo");
        } else {
            storageDir = context.getFilesDir();
        }
        
        diskAudioBuffer = new DiskAudioBuffer(storageDir, maxDiskUsageBytes, AudioMemory.CHUNK_SIZE);
        Log.d(TAG, "Initialized disk buffer with max size: " + maxDiskUsageMB + " MB");
    }
    
    /**
     * Cleanup disk buffer (called on audio thread).
     */
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
    
    /**
     * Check if external storage is writable.
     */
    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
    
    /**
     * Release resources.
     */
    public void shutdown() {
        cleanupDiskBuffer();
    }
}
