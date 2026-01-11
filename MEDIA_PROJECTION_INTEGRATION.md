# MediaProjection Integration Guide

## Overview

As of the latest update, Echo now uses the AudioPlaybackCapture API to record device audio instead of REMOTE_SUBMIX. This provides better compatibility and doesn't require root access. However, it requires a MediaProjection instance which must be obtained through user consent.

## What Changed

- **Old approach**: Used `MediaRecorder.AudioSource.REMOTE_SUBMIX` (required root/system privileges)
- **New approach**: Uses `AudioPlaybackCaptureConfiguration` with MediaProjection (user grants permission via system dialog)

## Integration Steps

### 1. Add Required Imports to Your Activity

```java
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
```

### 2. Add Activity Request Code Constant

```java
private static final int REQUEST_MEDIA_PROJECTION = 1001;
private MediaProjectionManager mediaProjectionManager;
private MediaProjection mediaProjection;
```

### 3. Initialize MediaProjectionManager in onCreate()

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... other initialization code ...
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        mediaProjectionManager = (MediaProjectionManager) 
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }
}
```

### 4. Request MediaProjection Permission Before Enabling Device Audio

When the user wants to enable device audio recording (e.g., via a toggle in settings):

```java
public void onEnableDeviceAudioClicked() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Request user permission for screen/audio capture
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
    } else {
        // Show error: Device audio capture requires Android 10+
        Toast.makeText(this, 
            "Device audio capture requires Android 10 or higher", 
            Toast.LENGTH_LONG).show();
    }
}
```

### 5. Handle Permission Result

```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (requestCode == REQUEST_MEDIA_PROJECTION) {
        if (resultCode == RESULT_OK && data != null) {
            // User granted permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                
                // Pass the MediaProjection to the service
                if (saidItService != null) {
                    saidItService.setMediaProjection(mediaProjection);
                }
                
                // Now enable device audio recording
                if (saidItService != null) {
                    saidItService.setDeviceAudioRecording(true);
                    // Or for dual-source:
                    // saidItService.setDualSourceRecording(true);
                }
                
                // Restart listening to apply changes
                saidItService.disableListening();
                saidItService.enableListening();
                
                Toast.makeText(this, 
                    "Device audio capture enabled", 
                    Toast.LENGTH_SHORT).show();
            }
        } else {
            // User denied permission
            Toast.makeText(this, 
                "Permission denied. Device audio capture requires your consent.", 
                Toast.LENGTH_LONG).show();
        }
    }
}
```

### 6. Clean Up MediaProjection When Done

```java
@Override
protected void onDestroy() {
    super.onDestroy();
    
    // Stop MediaProjection when activity is destroyed
    // Note: Only do this if you're sure recording is stopped
    if (mediaProjection != null) {
        try {
            mediaProjection.stop();
        } catch (Exception e) {
            // Already stopped
        }
        mediaProjection = null;
    }
}
```

## Important Notes

### User Experience
- When requesting MediaProjection, the user will see a system dialog asking for permission to capture screen content and audio
- A persistent notification will be displayed while MediaProjection is active
- This is a security/privacy feature and cannot be bypassed

### App Compatibility
- Only apps that allow audio capture can be recorded
- Many music/video apps (Spotify, YouTube Music, etc.) block audio capture
- Game audio typically works
- System sounds usually work

### Lifecycle Management
- MediaProjection should be stopped when not in use to free resources
- If the user revokes permission, your app should handle it gracefully
- Consider stopping MediaProjection when the app goes to background

### Error Handling
- Always check Android version (API 29+ required)
- Handle cases where MediaProjection initialization fails
- Gracefully fall back to microphone-only recording if device audio fails

## Testing

1. Enable device audio or dual-source recording
2. Grant MediaProjection permission when prompted
3. Start recording
4. Play audio from another app
5. Verify the audio is captured

## Troubleshooting

### No audio from device
- Check if the target app allows audio capture
- Verify MediaProjection permission was granted
- Ensure Android version is 10+ (API 29+)
- Check logcat for error messages

### Audio is mangled/distorted
- Verify channel modes (mono/stereo) are set correctly
- Check sample rate compatibility
- Ensure sufficient buffer sizes

### Permission dialog doesn't appear
- Verify `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission is in manifest
- Check that service foreground type includes `mediaProjection`
- Ensure MediaProjectionManager is initialized correctly

## Example: Simple Toggle Implementation

```java
private void toggleDeviceAudio(boolean enable) {
    if (enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mediaProjection == null) {
                // Need to request permission first
                Intent intent = mediaProjectionManager.createScreenCaptureIntent();
                startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
            } else {
                // Already have permission, just enable
                if (saidItService != null) {
                    saidItService.setMediaProjection(mediaProjection);
                    saidItService.setDeviceAudioRecording(true);
                    restartListening();
                }
            }
        } else {
            showError("Requires Android 10+");
        }
    } else {
        // Disable device audio
        if (saidItService != null) {
            saidItService.setDeviceAudioRecording(false);
            restartListening();
        }
    }
}

private void restartListening() {
    if (saidItService != null) {
        saidItService.disableListening();
        saidItService.enableListening();
    }
}
```

## Security Considerations

- MediaProjection grants access to capture ALL audio and screen content
- Users should be informed about what they're granting access to
- Consider adding a privacy notice before requesting permission
- Store MediaProjection instances securely (don't expose to untrusted code)
- Stop MediaProjection as soon as it's no longer needed

## References

- [Android AudioPlaybackCapture Documentation](https://developer.android.com/guide/topics/media/playback-capture)
- [Android MediaProjection Documentation](https://developer.android.com/reference/android/media/projection/MediaProjection)
- [Restricted Screen Reading Permissions](https://source.android.com/docs/core/permissions/restricted-screen-reading)
