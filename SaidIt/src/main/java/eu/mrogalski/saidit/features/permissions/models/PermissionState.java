package eu.mrogalski.saidit.features.permissions.models;

/**
 * Represents the state of a specific permission.
 */
public class PermissionState {
    private final String permission;
    private final boolean granted;
    
    public PermissionState(String permission, boolean granted) {
        this.permission = permission;
        this.granted = granted;
    }
    
    public String getPermission() {
        return permission;
    }
    
    public boolean isGranted() {
        return granted;
    }
}
