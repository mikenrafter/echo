package eu.mrogalski.saidit.features.audiocapture.services;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.util.Log;

/**
 * Multi-source audio recorder for capturing microphone and device audio.
 * 
 * STATUS: IMPLEMENTED - Uses AudioPlaybackCapture API for device audio capture
 * 
 * This class provides functionality for dual-channel audio capture,
 * supporting the feature request for simultaneous mic + device audio recording.
 * 
 * IMPLEMENTATION:
 * - Capture microphone audio (MediaRecorder.AudioSource.MIC)
 * - Capture device/system audio (AudioPlaybackCapture API, Android 10+)
 * - Interleave samples into separate L/R stereo channels
 * - Handle synchronization between sources
 * 
 * ANDROID REQUIREMENTS:
 * 
 * 1. AudioPlaybackCapture (Android 10+) requirements:
 *    - Requires FOREGROUND_SERVICE_MEDIA_PROJECTION permission
 *    - Requires MediaProjection (user must grant permission via system dialog)
 *    - User sees persistent notification during capture
 *    - Only captures apps that allow audio capture
 *    
 * 2. Device compatibility:
 *    - Requires Android 10+ (API 29)
 *    - Most modern devices support AudioPlaybackCapture
 *    - Performance varies by device
 *    
 * 3. App compatibility:
 *    - Only captures audio from apps that don't block capture
 *    - Music apps may block capture (Spotify, YouTube Music, etc.)
 *    - System sounds are typically capturable
 *    - Game audio usually works
 *    
 * USAGE:
 * 1. Obtain MediaProjection via MediaProjectionManager.createScreenCaptureIntent()
 * 2. Call SaidItService.setMediaProjection() with the MediaProjection instance
 * 3. Enable device audio recording via setDeviceAudioRecording(true) or setDualSourceRecording(true)
 * 4. Start listening/recording as normal
 * 
 * NOTE: This replaces the previous REMOTE_SUBMIX approach which required root/system privileges.
 * 
 * See: https://developer.android.com/guide/topics/media/playback-capture
 * See: https://source.android.com/docs/core/permissions/restricted-screen-reading
 */
public class MultiSourceAudioRecorder {
    
    private static final String TAG = MultiSourceAudioRecorder.class.getSimpleName();
    
    private AudioRecord microphoneRecorder;
    private AudioRecord deviceRecorder; // Uses AudioPlaybackCapture
    
    /**
     * Checks if device audio capture is available on this device.
     * 
     * @return true if AudioPlaybackCapture API is available (Android 10+)
     */
    public static boolean isDeviceAudioCaptureSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }
    
    /**
     * This class is now implemented in SaidItService using AudioPlaybackCapture API.
     * Direct usage of this class is not required - use SaidItService methods instead.
     * 
     * @deprecated Use SaidItService.setMediaProjection() and related methods instead
     */
    @Deprecated
    public void initialize(AudioSource audioSource, int sampleRate, int bufferSize) {
        Log.w(TAG, "MultiSourceAudioRecorder is deprecated. Use SaidItService methods instead.");
    }
    
    /**
     * @deprecated Use SaidItService methods instead
     */
    @Deprecated
    public void start() {
        Log.w(TAG, "MultiSourceAudioRecorder is deprecated. Use SaidItService methods instead.");
    }
    
    /**
     * @deprecated Use SaidItService methods instead
     */
    @Deprecated
    public int read(byte[] buffer, int offset, int length) {
        Log.w(TAG, "MultiSourceAudioRecorder is deprecated. Use SaidItService methods instead.");
        return 0;
    }
    
    /**
     * @deprecated Use SaidItService methods instead
     */
    @Deprecated
    public void stop() {
        Log.w(TAG, "MultiSourceAudioRecorder is deprecated. Use SaidItService methods instead.");
    }
    
    /**
     * Releases all resources.
     */
    public void release() {
        if (microphoneRecorder != null) {
            microphoneRecorder.release();
            microphoneRecorder = null;
        }
        if (deviceRecorder != null) {
            deviceRecorder.release();
            deviceRecorder = null;
        }
    }
}
