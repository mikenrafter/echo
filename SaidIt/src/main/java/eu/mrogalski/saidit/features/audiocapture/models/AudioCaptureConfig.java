package eu.mrogalski.saidit.features.audiocapture.models;

/**
 * Configuration for audio capture.
 */
public class AudioCaptureConfig {
    private int sampleRate = 48000;
    private boolean recordDeviceAudio = false;
    private boolean dualSourceRecording = false;
    private int micChannelMode = 0; // 0=mono, 1=stereo
    private int deviceChannelMode = 0; // 0=mono, 1=stereo
    private boolean silenceSkipEnabled = false;
    private int silenceThreshold = 500;
    
    public int getSampleRate() {
        return sampleRate;
    }
    
    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }
    
    public boolean isRecordDeviceAudio() {
        return recordDeviceAudio;
    }
    
    public void setRecordDeviceAudio(boolean recordDeviceAudio) {
        this.recordDeviceAudio = recordDeviceAudio;
    }
    
    public boolean isDualSourceRecording() {
        return dualSourceRecording;
    }
    
    public void setDualSourceRecording(boolean dualSourceRecording) {
        this.dualSourceRecording = dualSourceRecording;
    }
    
    public int getMicChannelMode() {
        return micChannelMode;
    }
    
    public void setMicChannelMode(int micChannelMode) {
        this.micChannelMode = micChannelMode;
    }
    
    public int getDeviceChannelMode() {
        return deviceChannelMode;
    }
    
    public void setDeviceChannelMode(int deviceChannelMode) {
        this.deviceChannelMode = deviceChannelMode;
    }
    
    public boolean isSilenceSkipEnabled() {
        return silenceSkipEnabled;
    }
    
    public void setSilenceSkipEnabled(boolean silenceSkipEnabled) {
        this.silenceSkipEnabled = silenceSkipEnabled;
    }
    
    public int getSilenceThreshold() {
        return silenceThreshold;
    }
    
    public void setSilenceThreshold(int silenceThreshold) {
        this.silenceThreshold = silenceThreshold;
    }
}
