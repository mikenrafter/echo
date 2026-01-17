package eu.mrogalski.saidit.features.permissions.models;

import android.content.Intent;

/**
 * Event published when MediaProjection permission is granted.
 */
public class MediaProjectionPermissionGrantedEvent {
    private final int resultCode;
    private final Intent data;
    
    public MediaProjectionPermissionGrantedEvent(int resultCode, Intent data) {
        this.resultCode = resultCode;
        this.data = data;
    }
    
    public int getResultCode() {
        return resultCode;
    }
    
    public Intent getData() {
        return data;
    }
}
