package eu.mrogalski.saidit.features.preferences.models;

/**
 * Event published when a preference changes.
 */
public class PreferenceChangedEvent {
    private final String key;
    private final Object value;
    
    public PreferenceChangedEvent(String key, Object value) {
        this.key = key;
        this.value = value;
    }
    
    public String getKey() {
        return key;
    }
    
    public Object getValue() {
        return value;
    }
}
