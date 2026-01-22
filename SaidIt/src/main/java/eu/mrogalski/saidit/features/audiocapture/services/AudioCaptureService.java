package eu.mrogalski.saidit.features.audiocapture.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;

import eu.mrogalski.saidit.R;
import eu.mrogalski.saidit.SaidIt;
import eu.mrogalski.saidit.shared.services.CrashHandler;

/**
 * Service responsible for audio capture.
 * 
 * Responsibilities:
 * - Managing AudioRecord lifecycle
 * - Capturing audio from microphone
 * - Capturing audio from device (MediaProjection)
 * - Dual-source audio mixing
 * - Writing audio to memory rings
 * 
 * This service publishes AudioCaptureStateChangedEvent when capture starts/stops.
 */
public class AudioCaptureService {
    private static final String TAG = AudioCaptureService.class.getSimpleName();
    
    private final Context context;
    private final Handler audioHandler;
    private final StorageManagementService storageManagementService;
    
    // Audio recording
    private AudioRecord audioRecord;
    private AudioRecord deviceAudioRecord;
    private MediaProjection mediaProjection;
    
    // Configuration
    private int sampleRate;
    private int fillRate;
    private int micChannelMode; // 0 = MONO, 1 = STEREO
    private int deviceChannelMode; // 0 = MONO, 1 = STEREO
    private boolean deviceAudioEnabled;
    private boolean dualSourceRecording;
    
    // Consumer for audio processing
    private final AudioMemory.Consumer consumer;
    
    // Audio reader runnable
    private final Runnable audioReader;
    
    // State
    private volatile boolean isListening = false;
    private long recordingStartTimeMillis;
    
    // Volume tracking for UI
    private volatile int currentVolumeLevel = 0;
    
    public AudioCaptureService(Context context, Handler audioHandler, 
                              StorageManagementService storageManagementService,
                              int sampleRate) {
        this.context = context;
        this.audioHandler = audioHandler;
        this.storageManagementService = storageManagementService;
        this.sampleRate = sampleRate;
        this.fillRate = sampleRate * 2; // 16-bit = 2 bytes per sample
        
        // Create consumer for audio processing
        this.consumer = createConsumer();
        
        // Create audio reader
        this.audioReader = createAudioReader();
        
        loadConfiguration();
    }
    
    /**
     * Load configuration from SharedPreferences.
     */
    private void loadConfiguration() {
        final SharedPreferences prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        
        // Load device audio settings
        deviceAudioEnabled = prefs.getBoolean(SaidIt.RECORD_DEVICE_AUDIO_KEY, false);
        dualSourceRecording = prefs.getBoolean(SaidIt.DUAL_SOURCE_RECORDING_KEY, false);
        
        // Load channel modes
        micChannelMode = prefs.getInt(SaidIt.MIC_CHANNEL_MODE_KEY, 0); // 0 = MONO
        deviceChannelMode = prefs.getInt(SaidIt.DEVICE_CHANNEL_MODE_KEY, 0); // 0 = MONO
        
        Log.d(TAG, "Configuration loaded: deviceAudio=" + deviceAudioEnabled + 
            ", dualSource=" + dualSourceRecording + 
            ", micChannel=" + micChannelMode + 
            ", deviceChannel=" + deviceChannelMode);
    }
    
    /**
     * Check if listening is active.
     */
    public boolean isListening() {
        return isListening;
    }
    
    /**
     * Get current volume level (for UI).
     */
    public int getCurrentVolumeLevel() {
        return currentVolumeLevel;
    }
    
    /**
     * Start audio capture.
     */
    @SuppressLint("MissingPermission")
    public void startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening");
            return;
        }
        
        isListening = true;
        Log.d(TAG, "Queueing: START LISTENING");
        
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Executing: START LISTENING");
                Log.d(TAG, "Audio: INITIALIZING AUDIO_RECORD");
                
                if (dualSourceRecording) {
                    // Dual-source mode: Initialize both MIC and device audio
                    initializeDualSource();
                } else if (deviceAudioEnabled) {
                    // Device audio only mode
                    initializeDeviceAudio();
                } else {
                    // Microphone only mode (default)
                    initializeMicrophone();
                }
                
                if (audioRecord == null && deviceAudioRecord == null) {
                    Log.e(TAG, "Failed to initialize any audio source");
                    isListening = false;
                    return;
                }
                
                Log.d(TAG, "Audio: STARTING AudioRecord");
                recordingStartTimeMillis = System.currentTimeMillis();
                
                // Start recording
                if (audioRecord != null) {
                    audioRecord.startRecording();
                }
                if (deviceAudioRecord != null) {
                    deviceAudioRecord.startRecording();
                }
                
                // Start audio reader
                audioHandler.post(audioReader);
            }
        });
    }
    
    /**
     * Stop audio capture.
     */
    public void stopListening() {
        if (!isListening) {
            Log.w(TAG, "Not listening");
            return;
        }
        
        isListening = false;
        Log.d(TAG, "Queueing: STOP LISTENING");
        
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Executing: STOP LISTENING");
                
                // Stop audio reader
                audioHandler.removeCallbacks(audioReader);
                
                // Release audio resources
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
                if (deviceAudioRecord != null) {
                    deviceAudioRecord.release();
                    deviceAudioRecord = null;
                }
            }
        });
    }
    
    /**
     * Set MediaProjection for device audio capture.
     */
    public void setMediaProjection(MediaProjection projection) {
        this.mediaProjection = projection;
        if (projection != null) {
            Log.d(TAG, "MediaProjection set - device audio capture is now available");
        } else {
            Log.d(TAG, "MediaProjection cleared - device audio capture is not available");
        }
    }
    
    /**
     * Check if MediaProjection is required.
     */
    public boolean isMediaProjectionRequired() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        
        return (deviceAudioEnabled || dualSourceRecording) && mediaProjection == null;
    }
    
    /**
     * Initialize dual-source recording (mic + device audio).
     */
    @SuppressLint("MissingPermission")
    private void initializeDualSource() {
        Log.d(TAG, "Initializing DUAL-SOURCE recording (MIC + Device Audio)");
        
        // Initialize microphone
        audioRecord = createMicrophoneAudioRecord(micChannelMode);
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Audio: MIC INITIALIZATION ERROR");
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
            return;
        }
        
        // Initialize device audio
        if (mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                AudioFormat audioFormat = createAudioFormat(deviceChannelMode);
                android.media.AudioPlaybackCaptureConfiguration config = createAudioPlaybackCaptureConfig();
                
                if (config != null) {
                    deviceAudioRecord = new AudioRecord.Builder()
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(AudioMemory.CHUNK_SIZE)
                        .setAudioPlaybackCaptureConfig(config)
                        .build();
                    
                    if (deviceAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "Audio: AudioPlaybackCapture INITIALIZATION ERROR");
                        deviceAudioRecord.release();
                        deviceAudioRecord = null;
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
        }
        
        Log.d(TAG, "Dual-source initialized: MIC=" + (audioRecord != null) + 
            ", DEVICE=" + (deviceAudioRecord != null));
    }
    
    /**
     * Initialize device audio only.
     */
    @SuppressLint("MissingPermission")
    private void initializeDeviceAudio() {
        Log.d(TAG, "Initializing DEVICE AUDIO recording");
        
        if (mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                AudioFormat audioFormat = createAudioFormat(deviceChannelMode);
                android.media.AudioPlaybackCaptureConfiguration config = createAudioPlaybackCaptureConfig();
                
                if (config != null) {
                    audioRecord = new AudioRecord.Builder()
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(AudioMemory.CHUNK_SIZE)
                        .setAudioPlaybackCaptureConfig(config)
                        .build();
                    
                    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "Audio: Device audio INITIALIZATION ERROR");
                        audioRecord.release();
                        audioRecord = null;
                    } else {
                        Log.d(TAG, "Device audio initialized successfully");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing device audio: " + e.getMessage(), e);
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
            }
        }
    }
    
    /**
     * Initialize microphone only.
     */
    @SuppressLint("MissingPermission")
    private void initializeMicrophone() {
        Log.d(TAG, "Initializing MICROPHONE recording");
        
        audioRecord = createMicrophoneAudioRecord(0); // MONO
        
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Audio: MICROPHONE INITIALIZATION ERROR");
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
        } else {
            Log.d(TAG, "Microphone initialized successfully");
        }
    }
    
    /**
     * Create AudioRecord for microphone.
     */
    @SuppressLint("MissingPermission")
    private AudioRecord createMicrophoneAudioRecord(int channelMode) {
        try {
            int channelConfig = channelMode == 0 ? 
                AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
            
            return new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioMemory.CHUNK_SIZE);
        } catch (Exception e) {
            Log.e(TAG, "Error creating microphone AudioRecord: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create AudioFormat.
     */
    private AudioFormat createAudioFormat(int channelMode) {
        return new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMode == 0 ? 
                AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO)
            .build();
    }
    
    /**
     * Create AudioPlaybackCaptureConfiguration.
     */
    private android.media.AudioPlaybackCaptureConfiguration createAudioPlaybackCaptureConfig() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null - cannot create AudioPlaybackCaptureConfig");
            return null;
        }
        
        try {
            return new android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                .build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating AudioPlaybackCaptureConfig: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create consumer for audio processing.
     */
    private AudioMemory.Consumer createConsumer() {
        return new AudioMemory.Consumer() {
            private final byte[] buffer = new byte[AudioMemory.CHUNK_SIZE];
            
            @Override
            public int consume(byte[] array, int offset, int count) throws IOException {
                // Read from audio sources
                int bytesRead = 0;
                
                if (audioRecord != null) {
                    bytesRead = audioRecord.read(array, offset, count);
                }
                
                if (deviceAudioRecord != null) {
                    int deviceBytesRead = deviceAudioRecord.read(buffer, 0, count);
                    
                    if (bytesRead > 0 && deviceBytesRead > 0) {
                        // Mix both sources
                        mixAudioSources(array, offset, buffer, 0, Math.min(bytesRead, deviceBytesRead));
                    } else if (deviceBytesRead > 0) {
                        // Only device audio available
                        System.arraycopy(buffer, 0, array, offset, deviceBytesRead);
                        bytesRead = deviceBytesRead;
                    }
                }
                
                // Calculate volume level for UI
                if (bytesRead > 0) {
                    currentVolumeLevel = calculateVolumeLevel(array, offset, bytesRead);
                }
                
                return bytesRead;
            }
        };
    }
    
    /**
     * Mix two audio sources.
     */
    private void mixAudioSources(byte[] dest, int destOffset, byte[] src, int srcOffset, int length) {
        for (int i = 0; i < length; i += 2) {
            // Read 16-bit samples
            short sample1 = (short)((dest[destOffset + i + 1] << 8) | (dest[destOffset + i] & 0xFF));
            short sample2 = (short)((src[srcOffset + i + 1] << 8) | (src[srcOffset + i] & 0xFF));
            
            // Mix samples (average)
            int mixed = (sample1 + sample2) / 2;
            
            // Clamp to 16-bit range
            if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE;
            if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE;
            
            // Write back
            dest[destOffset + i] = (byte)(mixed & 0xFF);
            dest[destOffset + i + 1] = (byte)((mixed >> 8) & 0xFF);
        }
    }
    
    /**
     * Calculate volume level from audio data.
     */
    private int calculateVolumeLevel(byte[] data, int offset, int length) {
        long sum = 0;
        for (int i = offset; i < offset + length; i += 2) {
            short sample = (short)((data[i + 1] << 8) | (data[i] & 0xFF));
            sum += Math.abs(sample);
        }
        return (int)(sum / (length / 2));
    }
    
    /**
     * Create audio reader runnable.
     */
    private Runnable createAudioReader() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    if (storageManagementService.isGradientQualityEnabled()) {
                        // Route audio to appropriate quality tier
                        long elapsedMillis = System.currentTimeMillis() - recordingStartTimeMillis;
                        int elapsedSeconds = (int)(elapsedMillis / 1000);
                        
                        if (elapsedSeconds < 300) { // 0-5 minutes: HIGH
                            storageManagementService.getAudioMemoryHigh().fill(consumer);
                        } else if (elapsedSeconds < 1200) { // 5-20 minutes: MID
                            storageManagementService.getAudioMemoryMid().fill(consumer);
                        } else { // 20+ minutes: LOW
                            storageManagementService.getAudioMemoryLow().fill(consumer);
                        }
                    } else {
                        // Single ring mode
                        storageManagementService.getAudioMemory().fill(consumer);
                    }
                    
                    // Schedule next read
                    if (isListening) {
                        audioHandler.post(this);
                    }
                } catch (IOException e) {
                    String errorMessage = context.getString(R.string.error_during_recording_into, "audio buffer");
                    Log.e(TAG, errorMessage, e);
                    CrashHandler.writeCrashLog(context, "Audio reader error", e);
                    stopListening();
                } catch (Throwable t) {
                    Log.e(TAG, "Unexpected error in audio reader", t);
                    CrashHandler.writeCrashLog(context, "Unexpected error in audio reader", t);
                    stopListening();
                }
            }
        };
    }
    
    /**
     * Flush audio record buffers.
     */
    public void flushAudioRecord() {
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                if (audioRecord != null) {
                    byte[] buffer = new byte[AudioMemory.CHUNK_SIZE];
                    while (audioRecord.read(buffer, 0, buffer.length) > 0) {
                        // Drain buffer
                    }
                }
                if (deviceAudioRecord != null) {
                    byte[] buffer = new byte[AudioMemory.CHUNK_SIZE];
                    while (deviceAudioRecord.read(buffer, 0, buffer.length) > 0) {
                        // Drain buffer
                    }
                }
            }
        });
    }
}
