package eu.mrogalski.saidit.shared.events;

import org.greenrobot.eventbus.EventBus;

/**
 * Provides a centralized EventBus instance for feature communication.
 * Features should communicate through events posted to this bus.
 */
public class EventBusProvider {
    private static final EventBus instance = EventBus.getDefault();
    
    private EventBusProvider() {
        // Prevent instantiation
    }
    
    /**
     * Gets the shared EventBus instance.
     * @return The EventBus instance for posting and subscribing to events
     */
    public static EventBus getEventBus() {
        return instance;
    }
}
