package eu.mrogalski.saidit;

public class SaidIt {

    static final String PACKAGE_NAME = "eu.mrogalski.saidit";
    static final String AUDIO_MEMORY_ENABLED_KEY = "audio_memory_enabled";
    static final String AUDIO_MEMORY_SIZE_KEY = "audio_memory_size";
    static final String MEMORY_SIZE_MB_KEY = "memory_size_mb";
    static final String STORAGE_MODE_KEY = "storage_mode";
    static final String MAX_DISK_USAGE_MB_KEY = "max_disk_usage_mb";
    static final String ACTIVITY_DETECTION_ENABLED_KEY = "activity_detection_enabled";
    static final String ACTIVITY_DETECTION_THRESHOLD_KEY = "activity_detection_threshold";
    static final String ACTIVITY_PRE_BUFFER_SECONDS_KEY = "activity_pre_buffer_seconds";
    static final String ACTIVITY_POST_BUFFER_SECONDS_KEY = "activity_post_buffer_seconds";
    static final String ACTIVITY_AUTO_DELETE_DAYS_KEY = "activity_auto_delete_days";
    static final String ACTIVITY_HIGH_BITRATE_KEY = "activity_high_bitrate";
    static final String SAMPLE_RATE_KEY = "sample_rate";
    
    // Audio effects configuration (applied during export/preview only)
    static final String EXPORT_NOISE_SUPPRESSION_ENABLED_KEY = "export_noise_suppression_enabled";
    static final String EXPORT_NOISE_THRESHOLD_KEY = "export_noise_threshold";
    static final String EXPORT_AUTO_NORMALIZE_ENABLED_KEY = "export_auto_normalize_enabled";
    
    // Long silence skipping configuration
    static final String SILENCE_SKIP_ENABLED_KEY = "silence_skip_enabled";
    static final String SILENCE_THRESHOLD_KEY = "silence_threshold";
    static final String SILENCE_SEGMENT_COUNT_KEY = "silence_segment_count"; // How many consecutive silent segments before skipping
    
    // Device audio recording
    static final String RECORD_DEVICE_AUDIO_KEY = "record_device_audio";
    
    // Dual-source recording (mic + device audio simultaneously)
    static final String DUAL_SOURCE_RECORDING_KEY = "dual_source_recording";
    static final String MIC_CHANNEL_MODE_KEY = "mic_channel_mode"; // 0=mono, 1=stereo
    static final String DEVICE_CHANNEL_MODE_KEY = "device_channel_mode"; // 0=mono, 1=stereo
    static final String TOTAL_SKIPPED_SECONDS_KEY = "total_skipped_seconds"; // Statistics: total seconds skipped
    
    // Auto-save configuration (records to Echo/AutoSave subfolder)
    static final String AUTO_SAVE_ENABLED_KEY = "auto_save_enabled";
    static final String AUTO_SAVE_DURATION_KEY = "auto_save_duration"; // in seconds
    static final String AUTO_SAVE_AUTO_DELETE_DAYS_KEY = "auto_save_auto_delete_days"; // Auto-delete files older than this many days
    
    static final String SKU = "unlimited_history";
    static final String BASE64_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlD0FMFGp4AWzjW" +
            "LTsUZgm0soga0mVVNGFj0qoATaoQCE/LamF7yrMCIFm9sEOB1guCEhzdr16sjysrVc2EPRisS83FoJ4K0R8" +
            "XPDP2TrVT2SAeQpTCG27NNH+W86SlGEqQeQhMPMhR+HDTckHv3KBpD8BZEEIbkXPv6SGFqcZub6xzn9r14l" +
            "6ptYIWboKGGBh1i9/nJpdhCMPxuLn/WZnRXGxqGpfNw2xT25/muUDZgRVezy6/5eI+ciMn5H1U0ADBjXvl1" +
            "Py+4ClkR1V1Mfo9lvauB03zM8Fsa3LlIPle5a+wGKsRCLW/rJ/eE/rje6X7x/n+w8J4OiFvVATj0T8QIDAQ" +
            "AB";

}
