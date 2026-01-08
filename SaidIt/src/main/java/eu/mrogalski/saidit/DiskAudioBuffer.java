package eu.mrogalski.saidit;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages a circular buffer of audio files on disk.
 * When the disk usage exceeds the configured limit, old files are automatically deleted.
 */
public class DiskAudioBuffer {
    private static final String TAG = DiskAudioBuffer.class.getSimpleName();
    private static final String BUFFER_DIR_NAME = "EchoBuffer";
    private static final String FILE_PREFIX = "buffer_";
    private static final String FILE_EXTENSION = ".raw";
    
    private File bufferDir;
    private long maxDiskUsageBytes;
    private long currentDiskUsage;
    private List<File> bufferFiles;
    private FileOutputStream currentOutputStream;
    private File currentFile;
    private int fileCounter;
    private final long chunkSize;
    
    /**
     * Creates a new DiskAudioBuffer.
     * @param storageDir Base storage directory (can be internal or external storage)
     * @param maxDiskUsageBytes Maximum disk space to use for the buffer
     * @param chunkSize Size of each buffer chunk/file in bytes
     */
    public DiskAudioBuffer(File storageDir, long maxDiskUsageBytes, long chunkSize) {
        this.maxDiskUsageBytes = maxDiskUsageBytes;
        this.chunkSize = chunkSize;
        this.bufferDir = new File(storageDir, BUFFER_DIR_NAME);
        this.bufferFiles = new ArrayList<>();
        this.fileCounter = 0;
        this.currentDiskUsage = 0;
        
        if (!bufferDir.exists()) {
            bufferDir.mkdirs();
        }
        
        // Load existing buffer files
        loadExistingFiles();
    }
    
    /**
     * Loads existing buffer files from disk and sorts them by creation time.
     */
    private void loadExistingFiles() {
        File[] files = bufferDir.listFiles((dir, name) -> 
            name.startsWith(FILE_PREFIX) && name.endsWith(FILE_EXTENSION));
        
        if (files != null) {
            for (File file : files) {
                bufferFiles.add(file);
                currentDiskUsage += file.length();
            }
            
            // Sort by filename (which includes timestamp/counter)
            Collections.sort(bufferFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            
            // Clean up if we exceed max disk usage
            cleanupOldFiles();
        }
    }
    
    /**
     * Writes audio data to the disk buffer.
     * Automatically creates new files when chunk size is reached.
     * @param data Audio data bytes
     * @param offset Offset in the data array
     * @param length Number of bytes to write
     * @throws IOException if writing fails
     */
    public synchronized void write(byte[] data, int offset, int length) throws IOException {
        if (currentOutputStream == null || shouldRotateFile()) {
            rotateFile();
        }
        
        currentOutputStream.write(data, offset, length);
        currentDiskUsage += length;
        
        // Check if we need to clean up old files
        if (currentDiskUsage > maxDiskUsageBytes) {
            cleanupOldFiles();
        }
    }
    
    /**
     * Checks if the current file should be rotated (reached chunk size).
     */
    private boolean shouldRotateFile() throws IOException {
        if (currentFile == null) {
            return true;
        }
        
        currentOutputStream.flush();
        return currentFile.length() >= chunkSize;
    }
    
    /**
     * Rotates to a new file, closing the current one if it exists.
     */
    private void rotateFile() throws IOException {
        if (currentOutputStream != null) {
            currentOutputStream.flush();
            currentOutputStream.close();
        }
        
        // Create new file with timestamp and counter
        String filename = FILE_PREFIX + System.currentTimeMillis() + "_" + fileCounter + FILE_EXTENSION;
        currentFile = new File(bufferDir, filename);
        currentOutputStream = new FileOutputStream(currentFile);
        bufferFiles.add(currentFile);
        fileCounter++;
        
        Log.d(TAG, "Rotated to new file: " + filename);
    }
    
    /**
     * Removes oldest files until disk usage is below the maximum.
     */
    private void cleanupOldFiles() {
        while (currentDiskUsage > maxDiskUsageBytes && !bufferFiles.isEmpty()) {
            File oldestFile = bufferFiles.get(0);
            long fileSize = oldestFile.length();
            
            // Don't delete the current file being written to
            if (oldestFile.equals(currentFile)) {
                break;
            }
            
            if (oldestFile.delete()) {
                bufferFiles.remove(0);
                currentDiskUsage -= fileSize;
                Log.d(TAG, "Deleted old buffer file: " + oldestFile.getName() + 
                           " (freed " + fileSize + " bytes)");
            } else {
                Log.w(TAG, "Failed to delete old buffer file: " + oldestFile.getName());
                break;
            }
        }
    }
    
    /**
     * Reads all buffered audio data and passes it to a consumer.
     * @param skipBytes Number of bytes to skip from the beginning
     * @param consumer Consumer to process the audio data
     * @throws IOException if reading fails
     */
    public synchronized void read(int skipBytes, AudioMemory.Consumer consumer) throws IOException {
        int totalSkipped = 0;
        
        for (File file : bufferFiles) {
            if (!file.exists()) {
                continue;
            }
            
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    if (totalSkipped < skipBytes) {
                        int toSkip = Math.min(bytesRead, skipBytes - totalSkipped);
                        totalSkipped += toSkip;
                        
                        if (toSkip < bytesRead) {
                            // Partial skip, consume the rest
                            consumer.consume(buffer, toSkip, bytesRead - toSkip);
                        }
                    } else {
                        // No more skipping, consume all data
                        consumer.consume(buffer, 0, bytesRead);
                    }
                }
            }
        }
    }
    
    /**
     * Gets the total number of bytes currently stored in the buffer.
     */
    public synchronized long getTotalBytes() {
        return currentDiskUsage;
    }
    
    /**
     * Gets the number of buffer files.
     */
    public synchronized int getFileCount() {
        return bufferFiles.size();
    }
    
    /**
     * Sets the maximum disk usage for the buffer.
     */
    public synchronized void setMaxDiskUsage(long maxDiskUsageBytes) {
        this.maxDiskUsageBytes = maxDiskUsageBytes;
        cleanupOldFiles();
    }
    
    /**
     * Gets the current disk usage.
     */
    public synchronized long getCurrentDiskUsage() {
        return currentDiskUsage;
    }
    
    /**
     * Flushes any pending writes to disk.
     */
    public synchronized void flush() throws IOException {
        if (currentOutputStream != null) {
            currentOutputStream.flush();
        }
    }
    
    /**
     * Closes the buffer and releases resources.
     */
    public synchronized void close() throws IOException {
        if (currentOutputStream != null) {
            currentOutputStream.flush();
            currentOutputStream.close();
            currentOutputStream = null;
        }
    }
    
    /**
     * Clears all buffer files from disk.
     */
    public synchronized void clearAll() {
        close();
        
        for (File file : bufferFiles) {
            if (file.exists()) {
                file.delete();
            }
        }
        
        bufferFiles.clear();
        currentDiskUsage = 0;
        fileCounter = 0;
        currentFile = null;
    }
    
    /**
     * Gets the buffer directory.
     */
    public File getBufferDir() {
        return bufferDir;
    }
}
