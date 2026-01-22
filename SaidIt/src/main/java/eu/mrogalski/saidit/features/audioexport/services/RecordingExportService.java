package eu.mrogalski.saidit.features.audioexport.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import eu.mrogalski.saidit.R;
import eu.mrogalski.saidit.SaidIt;
import eu.mrogalski.saidit.SaidItService;
import eu.mrogalski.saidit.features.audiocapture.services.AudioMemory;
import eu.mrogalski.saidit.features.audioprocessing.services.AudioEffects;
import eu.mrogalski.saidit.shared.models.StorageMode;
import eu.mrogalski.saidit.shared.services.CrashHandler;
import simplesound.pcm.WavAudioFormat;
import simplesound.pcm.WavFileWriter;

/**
 * Handles recording export and file writing concerns.
 * Delegated from SaidItService to reduce monolith size.
 */
public class RecordingExportService {
    private static final String TAG = RecordingExportService.class.getSimpleName();

    public interface FlushCallback {
        void flush();
    }

    private final Context context;
    private final Handler audioHandler;
    private final AudioMemory audioMemory;
    private final FlushCallback flushCallback;

    // Current storage state (kept in sync by SaidItService)
    private StorageMode storageMode;
    private DiskAudioBuffer diskAudioBuffer;

    private final int sampleRate;
    private final int fillRate;

    public RecordingExportService(Context context,
                                  Handler audioHandler,
                                  AudioMemory audioMemory,
                                  int sampleRate,
                                  FlushCallback flushCallback) {
        this.context = context;
        this.audioHandler = audioHandler;
        this.audioMemory = audioMemory;
        this.sampleRate = sampleRate;
        this.fillRate = sampleRate * 2; // 16-bit PCM
        this.flushCallback = flushCallback;
    }

    public void setStorageMode(StorageMode storageMode) {
        this.storageMode = storageMode;
    }

    public void setDiskAudioBuffer(DiskAudioBuffer diskAudioBuffer) {
        this.diskAudioBuffer = diskAudioBuffer;
    }

    /**
     * Export a recording from a specified time range.
     */
    public void dumpRecordingRange(final float startSecondsAgo, final float endSecondsAgo,
                                   final SaidItService.WavFileReceiver wavFileReceiver,
                                   final String newFileName) {
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                // Ensure capture buffers are flushed before export
                flushCallback.flush();

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

                int startOffsetBytes = (int)(startSec * fillRate);
                int endOffsetBytes = (int)(endSec * fillRate);

                int startPos = Math.max(0, bytesAvailable - startOffsetBytes);
                int endPos = Math.max(0, bytesAvailable - endOffsetBytes);
                if (endPos < startPos) {
                    int tmp = startPos;
                    startPos = endPos;
                    endPos = tmp;
                }
                int skipBytes = startPos;
                int bytesToWrite = Math.max(0, Math.min(bytesAvailable - skipBytes, endPos - startPos));

                // Build filename based on end time
                long millis  = System.currentTimeMillis() - (long)(1000L * (endOffsetBytes) / fillRate);
                final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
                final String dateTime = DateUtils.formatDateTime(context, millis, flags);
                String filename = "Echo - " + dateTime + ".wav";
                if(newFileName != null && !newFileName.equals("")){
                    filename = newFileName + ".wav";
                }

                // Auto-save exports go to Echo/AutoSave; manual exports go to Echo/
                File storageDir;
                boolean isManualExport = newFileName != null && !newFileName.isEmpty();
                if(isExternalStorageWritable()){
                    storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                            isManualExport ? "Echo" : "Echo/AutoSave");
                }else{
                    storageDir = new File(context.getFilesDir(), isManualExport ? "Echo" : "Echo/AutoSave");
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
                        showToast(context.getString(R.string.cant_create_file) + file.getAbsolutePath());
                        return;
                    }
                }

                // Read export effect preferences
                final SharedPreferences prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
                final boolean normalizeEnabled = prefs.getBoolean(SaidIt.EXPORT_AUTO_NORMALIZE_ENABLED_KEY, false);
                final boolean noiseSuppressionEnabled = prefs.getBoolean(SaidIt.EXPORT_NOISE_SUPPRESSION_ENABLED_KEY, false);
                final int noiseThreshold = prefs.getInt(SaidIt.EXPORT_NOISE_THRESHOLD_KEY, 500);

                final WavAudioFormat format = new WavAudioFormat.Builder().sampleRate(sampleRate).build();
                try (WavFileWriter writer = new WavFileWriter(format, file)) {
                    try {
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
                        } catch (OutOfMemoryError oomError) {
                            Log.e(TAG, "OutOfMemoryError during export, attempting memory-efficient fallback", oomError);
                            CrashHandler.writeCrashLog(context, "OOM during export", oomError);
                            try {
                                exportMemoryEfficient(writer, skipBytes, bytesToWrite, useDisk);
                                if (wavFileReceiver != null) {
                                    wavFileReceiver.fileReady(file, writer.getTotalSampleBytesWritten() * getBytesToSeconds());
                                }
                                showToast(context.getString(R.string.export_completed_memory_efficient));
                            } catch (Exception fallbackError) {
                                Log.e(TAG, "Memory-efficient fallback also failed", fallbackError);
                                CrashHandler.writeCrashLog(context, "Memory-efficient export fallback failed", fallbackError);
                                showToast(context.getString(R.string.export_failed_oom));
                                throw fallbackError;
                            }
                        }
                    } catch (IOException e) {
                        showToast(context.getString(R.string.error_during_writing_history_into) + file.getAbsolutePath());
                        Log.e(TAG, "Error during writing history into " + file.getAbsolutePath(), e);
                        CrashHandler.writeCrashLog(context, "IOException during export", e);
                    } catch (Exception e) {
                        showToast(context.getString(R.string.error_during_writing_history_into) + file.getAbsolutePath());
                        Log.e(TAG, "Unexpected error during export: " + file.getAbsolutePath(), e);
                        CrashHandler.writeCrashLog(context, "Unexpected error during export", e);
                    }
                } catch (IOException e) {
                    showToast(context.getString(R.string.cant_create_file) + file.getAbsolutePath());
                    Log.e(TAG, "Can't create file " + file.getAbsolutePath(), e);
                    CrashHandler.writeCrashLog(context, "Failed to create export file", e);
                }
            }
        });
    }

    /**
     * Stream export without loading full buffer into memory.
     */
    private void exportMemoryEfficient(WavFileWriter writer, int skipBytes, int bytesToWrite, boolean useDisk) throws IOException {
        if (audioHandler.getLooper() != Looper.myLooper()) {
            throw new IllegalStateException("exportMemoryEfficient must be called on audio thread");
        }

        Log.d(TAG, "Starting memory-efficient export: skipBytes=" + skipBytes + ", bytesToWrite=" + bytesToWrite);
        Log.d(TAG, "Note: Audio effects (normalization/noise suppression) are bypassed in memory-efficient mode");

        final int[] totalWritten = new int[]{0};
        final int targetBytes = bytesToWrite;

        AudioMemory.Consumer directWriter = new AudioMemory.Consumer() {
            @Override
            public int consume(byte[] array, int offset, int count) throws IOException {
                int remaining = targetBytes - totalWritten[0];
                if (remaining <= 0) return 0;

                int toWrite = Math.min(count, remaining);
                writer.write(array, offset, toWrite);
                totalWritten[0] += toWrite;

                if (totalWritten[0] % (AudioMemory.CHUNK_SIZE * 10) == 0) {
                    System.gc();
                }

                return 0;
            }
        };

        if (useDisk && diskAudioBuffer != null) {
            diskAudioBuffer.read(skipBytes, directWriter);
        } else {
            audioMemory.read(skipBytes, directWriter);
        }

        Log.d(TAG, "Memory-efficient export completed: " + totalWritten[0] + " bytes written");
    }

    private float getBytesToSeconds() {
        return 1.0f / fillRate;
    }

    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
