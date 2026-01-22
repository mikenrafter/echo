package eu.mrogalski.saidit.features.preferences.services;

import android.content.Context;
import android.content.SharedPreferences;

import org.greenrobot.eventbus.EventBus;

import eu.mrogalski.saidit.SaidIt;
import eu.mrogalski.saidit.features.preferences.models.PreferenceChangedEvent;
import eu.mrogalski.saidit.shared.events.EventBusProvider;

/**
 * Service for managing application preferences.
 * Provides getter/setter methods and publishes preference change events.
 */
public class PreferenceService {
    private final SharedPreferences preferences;
    
    public PreferenceService(Context context) {
        this.preferences = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Get a string preference.
     */
    public String getString(String key, String defaultValue) {
        return preferences.getString(key, defaultValue);
    }
    
    /**
     * Set a string preference and publish change event.
     */
    public void setString(String key, String value) {
        preferences.edit().putString(key, value).apply();
        EventBusProvider.getEventBus().post(new PreferenceChangedEvent(key, value));
    }
    
    /**
     * Get an integer preference.
     */
    public int getInt(String key, int defaultValue) {
        return preferences.getInt(key, defaultValue);
    }
    
    /**
     * Set an integer preference and publish change event.
     */
    public void setInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
        EventBusProvider.getEventBus().post(new PreferenceChangedEvent(key, value));
    }
    
    /**
     * Get a boolean preference.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }
    
    /**
     * Set a boolean preference and publish change event.
     */
    public void setBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
        EventBusProvider.getEventBus().post(new PreferenceChangedEvent(key, value));
    }
    
    /**
     * Get a float preference.
     */
    public float getFloat(String key, float defaultValue) {
        return preferences.getFloat(key, defaultValue);
    }
    
    /**
     * Set a float preference and publish change event.
     */
    public void setFloat(String key, float value) {
        preferences.edit().putFloat(key, value).apply();
        EventBusProvider.getEventBus().post(new PreferenceChangedEvent(key, value));
    }
    
    /**
     * Get a long preference.
     */
    public long getLong(String key, long defaultValue) {
        return preferences.getLong(key, defaultValue);
    }
    
    /**
     * Set a long preference and publish change event.
     */
    public void setLong(String key, long value) {
        preferences.edit().putLong(key, value).apply();
        EventBusProvider.getEventBus().post(new PreferenceChangedEvent(key, value));
    }
    
    /**
     * Check if a preference exists.
     */
    public boolean contains(String key) {
        return preferences.contains(key);
    }
    
    /**
     * Remove a preference.
     */
    public void remove(String key) {
        preferences.edit().remove(key).apply();
        EventBusProvider.getEventBus().post(new PreferenceChangedEvent(key, null));
    }
}
