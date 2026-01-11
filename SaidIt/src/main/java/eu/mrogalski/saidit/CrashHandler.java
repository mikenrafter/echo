package eu.mrogalski.saidit;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Centralized crash logging utility for Echo app.
 * Writes crash logs to Echo/Crashes directory with timestamps and device info.
 */
public class CrashHandler {
    private static final String TAG = CrashHandler.class.getSimpleName();
    
    /**
     * Write a crash log file with timestamp to Echo/Crashes directory.
     * @param context Application context
     * @param message Error message describing the crash
     * @param error The exception or error that occurred
     */
    public static void writeCrashLog(Context context, String message, Throwable error) {
        try {
            // Create Crashes directory
            File crashDir;
            if (isExternalStorageWritable()) {
                crashDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Echo/Crashes");
            } else {
                crashDir = new File(context.getFilesDir(), "Echo/Crashes");
            }
            
            if (!crashDir.exists()) {
                crashDir.mkdirs();
            }
            
            // Create crash log file with timestamp
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
            File crashFile = new File(crashDir, "crash_" + timestamp + ".log");
            
            // Write crash details using try-with-resources
            try (FileWriter writer = new FileWriter(crashFile)) {
                writer.write("Crash Log - " + timestamp + "\n");
                writer.write("=================================\n\n");
                writer.write("Message: " + message + "\n\n");
                writer.write("Error Type: " + error.getClass().getName() + "\n");
                writer.write("Error Message: " + error.getMessage() + "\n\n");
                writer.write("Stack Trace:\n");
                
                // Use try-with-resources for StringWriter and PrintWriter
                try (StringWriter sw = new StringWriter();
                     PrintWriter pw = new PrintWriter(sw)) {
                    error.printStackTrace(pw);
                    writer.write(sw.toString());
                }
                
                writer.write("\n\nDevice Info:\n");
                writer.write("Android Version: " + Build.VERSION.RELEASE + "\n");
                writer.write("SDK: " + Build.VERSION.SDK_INT + "\n");
                writer.write("Model: " + Build.MODEL + "\n");
                writer.write("Manufacturer: " + Build.MANUFACTURER + "\n");
                writer.write("Max Memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB\n");
                writer.write("Free Memory: " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + " MB\n");
                writer.write("Total Memory: " + (Runtime.getRuntime().totalMemory() / 1024 / 1024) + " MB\n");
            }
            
            Log.d(TAG, "Crash log written to: " + crashFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to write crash log", e);
        }
    }
    
    /**
     * Count the number of crash log files in the Echo/Crashes directory.
     * @param context Application context
     * @return Number of crash logs
     */
    public static int getCrashLogCount(Context context) {
        try {
            File crashDir;
            if (isExternalStorageWritable()) {
                crashDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Echo/Crashes");
            } else {
                crashDir = new File(context.getFilesDir(), "Echo/Crashes");
            }
            
            if (!crashDir.exists() || !crashDir.isDirectory()) {
                return 0;
            }
            
            File[] crashFiles = crashDir.listFiles(new java.io.FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("crash_") && name.endsWith(".log");
                }
            });
            return crashFiles != null ? crashFiles.length : 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to count crash logs", e);
            return 0;
        }
    }
    
    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
}
