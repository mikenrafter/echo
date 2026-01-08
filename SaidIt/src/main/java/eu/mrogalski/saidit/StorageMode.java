package eu.mrogalski.saidit;

/**
 * Defines the storage modes for audio recording.
 */
public enum StorageMode {
    /**
     * Audio is stored only in memory (circular buffer in RAM).
     * This is the default mode and uses the existing AudioMemory implementation.
     */
    MEMORY_ONLY,

    /**
     * Audio is automatically written to disk in batches.
     * Maintains a circular file buffer on disk, automatically cleaning up old files.
     */
    BATCH_TO_DISK
}
