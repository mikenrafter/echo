package eu.mrogalski.saidit.features.audioexport.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import eu.mrogalski.saidit.features.audioexport.models.ActivityRecording;

/**
 * Simple database for storing activity recording metadata using SharedPreferences.
 * For a production app, consider using SQLite for better performance with many recordings.
 */
public class ActivityRecordingDatabase {
    private static final String TAG = ActivityRecordingDatabase.class.getSimpleName();
    private static final String PREFS_NAME = "activity_recordings";
    private static final String KEY_RECORDINGS = "recordings_json";
    private static final String KEY_NEXT_ID = "next_id";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    public ActivityRecordingDatabase(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Adds a new recording to the database.
     */
    public synchronized long addRecording(ActivityRecording recording) {
        List<ActivityRecording> recordings = getAllRecordings();
        
        // Assign new ID
        long nextId = prefs.getLong(KEY_NEXT_ID, 1);
        recording.setId(nextId);
        prefs.edit().putLong(KEY_NEXT_ID, nextId + 1).apply();
        
        recordings.add(recording);
        saveRecordings(recordings);
        
        Log.d(TAG, "Added recording: " + recording);
        return nextId;
    }
    
    /**
     * Updates an existing recording.
     */
    public synchronized boolean updateRecording(ActivityRecording recording) {
        List<ActivityRecording> recordings = getAllRecordings();
        
        for (int i = 0; i < recordings.size(); i++) {
            if (recordings.get(i).getId() == recording.getId()) {
                recordings.set(i, recording);
                saveRecordings(recordings);
                Log.d(TAG, "Updated recording: " + recording);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Deletes a recording from the database and optionally deletes the file.
     */
    public synchronized boolean deleteRecording(long id, boolean deleteFile) {
        List<ActivityRecording> recordings = getAllRecordings();
        
        for (int i = 0; i < recordings.size(); i++) {
            if (recordings.get(i).getId() == id) {
                ActivityRecording recording = recordings.remove(i);
                
                if (deleteFile) {
                    File file = recording.getFile();
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted file: " + file.getAbsolutePath());
                        } else {
                            Log.w(TAG, "Failed to delete file: " + file.getAbsolutePath());
                        }
                    }
                }
                
                saveRecordings(recordings);
                Log.d(TAG, "Deleted recording: " + id);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Gets a recording by ID.
     */
    public synchronized ActivityRecording getRecording(long id) {
        List<ActivityRecording> recordings = getAllRecordings();
        
        for (ActivityRecording recording : recordings) {
            if (recording.getId() == id) {
                return recording;
            }
        }
        
        return null;
    }
    
    /**
     * Gets all recordings, sorted by timestamp (newest first).
     */
    public synchronized List<ActivityRecording> getAllRecordings() {
        List<ActivityRecording> recordings = new ArrayList<>();
        
        String json = prefs.getString(KEY_RECORDINGS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ActivityRecording recording = fromJSON(obj);
                recordings.add(recording);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing recordings JSON", e);
        }
        
        // Sort by timestamp, newest first
        Collections.sort(recordings, new Comparator<ActivityRecording>() {
            @Override
            public int compare(ActivityRecording r1, ActivityRecording r2) {
                long t1 = r1.getTimestamp();
                long t2 = r2.getTimestamp();
                // Newest first (reverse order)
                return (t2 < t1) ? -1 : ((t2 == t1) ? 0 : 1);
            }
        });
        
        return recordings;
    }
    
    /**
     * Gets recordings that should be auto-deleted.
     */
    public synchronized List<ActivityRecording> getRecordingsToDelete() {
        List<ActivityRecording> allRecordings = getAllRecordings();
        List<ActivityRecording> toDelete = new ArrayList<>();
        
        for (ActivityRecording recording : allRecordings) {
            if (recording.shouldDelete()) {
                toDelete.add(recording);
            }
        }
        
        return toDelete;
    }
    
    /**
     * Deletes all expired recordings and returns the count.
     */
    public synchronized int cleanupExpiredRecordings() {
        List<ActivityRecording> toDelete = getRecordingsToDelete();
        int count = 0;
        
        for (ActivityRecording recording : toDelete) {
            if (deleteRecording(recording.getId(), true)) {
                count++;
            }
        }
        
        Log.d(TAG, "Cleaned up " + count + " expired recordings");
        return count;
    }
    
    /**
     * Toggles the flagged status of a recording.
     */
    public synchronized boolean toggleFlag(long id) {
        ActivityRecording recording = getRecording(id);
        if (recording != null) {
            recording.setFlagged(!recording.isFlagged());
            return updateRecording(recording);
        }
        return false;
    }
    
    /**
     * Clears all recordings (but doesn't delete files).
     */
    public synchronized void clearAll() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Cleared all recordings from database");
    }
    
    /**
     * Saves the list of recordings to SharedPreferences.
     */
    private void saveRecordings(List<ActivityRecording> recordings) {
        try {
            JSONArray array = new JSONArray();
            for (ActivityRecording recording : recordings) {
                array.put(toJSON(recording));
            }
            prefs.edit().putString(KEY_RECORDINGS, array.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving recordings to JSON", e);
        }
    }
    
    /**
     * Converts a recording to JSON.
     */
    private JSONObject toJSON(ActivityRecording recording) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", recording.getId());
        obj.put("timestamp", recording.getTimestamp());
        obj.put("durationSeconds", recording.getDurationSeconds());
        obj.put("filePath", recording.getFilePath());
        obj.put("isFlagged", recording.isFlagged());
        obj.put("deleteAfterTimestamp", recording.getDeleteAfterTimestamp());
        obj.put("fileSize", recording.getFileSize());
        return obj;
    }
    
    /**
     * Creates a recording from JSON.
     */
    private ActivityRecording fromJSON(JSONObject obj) throws JSONException {
        return new ActivityRecording(
            obj.getLong("id"),
            obj.getLong("timestamp"),
            obj.getInt("durationSeconds"),
            obj.getString("filePath"),
            obj.getBoolean("isFlagged"),
            obj.getLong("deleteAfterTimestamp"),
            obj.getInt("fileSize")
        );
    }
}
