package eu.mrogalski.saidit.features.audioplayback.services;

import android.util.Log;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import eu.mrogalski.saidit.features.audioplayback.models.PlayAudioEvent;
import eu.mrogalski.saidit.shared.events.EventBusProvider;

/**
 * Service for audio playback.
 * 
 * This is a skeleton implementation for future audio playback features.
 * Future enhancements may include:
 * - Preview audio before export
 * - Play back recorded audio segments
 * - Timeline scrubbing and playback
 */
public class AudioPlaybackService {
    private static final String TAG = AudioPlaybackService.class.getSimpleName();
    
    public AudioPlaybackService() {
        EventBusProvider.getEventBus().register(this);
    }
    
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onPlayAudioRequest(PlayAudioEvent event) {
        Log.d(TAG, "Audio playback requested - NOT IMPLEMENTED");
        // TODO: Implement audio playback using AudioTrack
    }
    
    public void destroy() {
        EventBusProvider.getEventBus().unregister(this);
    }
}
