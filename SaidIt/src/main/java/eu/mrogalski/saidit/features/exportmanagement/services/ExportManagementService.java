package eu.mrogalski.saidit.features.exportmanagement.services;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.mrogalski.saidit.features.exportmanagement.models.ExportJob;

/**
 * Service for managing export jobs.
 * 
 * This is a skeleton implementation for future export management features.
 * Future enhancements may include:
 * - Queue multiple export jobs
 * - Track export progress
 * - Manage export history
 * - Auto-cleanup old exports
 */
public class ExportManagementService {
    private static final String TAG = ExportManagementService.class.getSimpleName();
    
    private final Map<String, ExportJob> exportJobs = new HashMap<>();
    
    public void addExportJob(ExportJob job) {
        exportJobs.put(job.getId(), job);
        Log.d(TAG, "Export job added: " + job.getId());
    }
    
    public ExportJob getExportJob(String id) {
        return exportJobs.get(id);
    }
    
    public List<ExportJob> getAllExportJobs() {
        return new ArrayList<>(exportJobs.values());
    }
    
    public void removeExportJob(String id) {
        exportJobs.remove(id);
        Log.d(TAG, "Export job removed: " + id);
    }
}
