package eu.mrogalski.saidit.features.permissions.services;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import eu.mrogalski.saidit.features.permissions.models.PermissionCheckRequestEvent;
import eu.mrogalski.saidit.features.permissions.models.PermissionState;
import eu.mrogalski.saidit.features.permissions.models.PermissionsGrantedEvent;
import eu.mrogalski.saidit.shared.events.EventBusProvider;

/**
 * Service managing permission state and availability.
 * Responds to permission check requests and publishes permission state events.
 */
public class PermissionService {
    private final Context context;
    
    public PermissionService(Context context) {
        this.context = context;
        EventBusProvider.getEventBus().register(this);
    }
    
    /**
     * Check if all required permissions are granted.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPermissionCheckRequest(PermissionCheckRequestEvent event) {
        if (hasAllRequiredPermissions()) {
            EventBusProvider.getEventBus().post(new PermissionsGrantedEvent());
        }
    }
    
    /**
     * Check if audio recording permission is granted.
     */
    public boolean hasAudioRecordingPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if foreground service permission is granted.
     */
    public boolean hasForegroundServicePermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if notification permission is granted (Android 13+).
     */
    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Not required on older versions
    }
    
    /**
     * Check if storage manager permission is granted.
     */
    public boolean hasStorageManagerPermission() {
        return Environment.isExternalStorageManager();
    }
    
    /**
     * Check if all required permissions are granted.
     */
    public boolean hasAllRequiredPermissions() {
        return hasAudioRecordingPermission() 
                && hasForegroundServicePermission() 
                && hasNotificationPermission()
                && hasStorageManagerPermission();
    }
    
    /**
     * Get the list of permissions to request based on Android version.
     */
    public String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            return new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.FOREGROUND_SERVICE
            };
        }
    }
    
    /**
     * Check if MediaProjection API is available on this device.
     */
    public boolean isMediaProjectionAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }
    
    /**
     * Unregister from event bus.
     */
    public void destroy() {
        EventBusProvider.getEventBus().unregister(this);
    }
}
