package eu.mrogalski.saidit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class SaidItActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 5465;
    public static final int MEDIA_PROJECTION_REQUEST_CODE = 1002;
    private boolean isFragmentSet = false;
    private AlertDialog permissionDeniedDialog;
    private AlertDialog storagePermissionDialog;
    
    // Handle pending recording action after MediaProjection permission
    public Runnable recordingActionPending = null;
    private SaidItService service;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            SaidItService.BackgroundRecorderBinder typedBinder = (SaidItService.BackgroundRecorderBinder) binder;
            service = typedBinder.getService();
            
            // Set up the MediaProjection request callback
            service.setMediaProjectionRequestCallback(new SaidItService.MediaProjectionRequestCallback() {
                @Override
                public void onRequestMediaProjection() {
                    requestMediaProjectionPermission();
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            service = null;
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_recorder);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, SaidItService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
        if(permissionDeniedDialog != null) {
            permissionDeniedDialog.dismiss();
        }
        if(storagePermissionDialog != null) {
            storagePermissionDialog.dismiss();
        }
        requestPermissions();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if(permissionDeniedDialog != null) {
            permissionDeniedDialog.dismiss();
        }
        if(storagePermissionDialog != null) {
            storagePermissionDialog.dismiss();
        }
        requestPermissions();
    }

    private void requestPermissions() {
        // Ask for storage permission

        String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.FOREGROUND_SERVICE};
        if(Build.VERSION.SDK_INT >= 33) {
            permissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.POST_NOTIFICATIONS};
        }
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all permissions are granted
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                // All permissions are granted
                if (Environment.isExternalStorageManager()) {
                    // Permission already granted
                    if(storagePermissionDialog != null) {
                        storagePermissionDialog.dismiss();
                    }
                    showFragment();
                } else {
                    // Request MANAGE_EXTERNAL_STORAGE permission
                    storagePermissionDialog = new AlertDialog.Builder(this)
                            .setTitle(R.string.permission_required)
                            .setMessage(R.string.permission_required_message)
                            .setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Open app settings
                                    Intent intent = new Intent();
                                    intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .setCancelable(false)
                            .show();
                }
            } else {
                if(permissionDeniedDialog == null || !permissionDeniedDialog.isShowing()) {
                    showPermissionDeniedDialog();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null && service != null) {
                // User granted screen recording permission
                boolean success = service.initializeMediaProjection(resultCode, data);
                if (success) {
                    Toast.makeText(this, "Screen recording permission granted", Toast.LENGTH_SHORT).show();
                    Log.d("SaidItActivity", "MediaProjection initialized successfully. Re-initiating recording.");
                    
                    // IMPORTANT: Re-trigger the recording action now that permission is granted.
                    // The service is waiting for this.
                    service.startRecording(0f); // Use a default, or retrieve from pending action

                } else {
                    Toast.makeText(this, "Failed to initialize screen recording", Toast.LENGTH_SHORT).show();
                }
            } else {
                // User denied permission
                Toast.makeText(this, "Screen recording permission denied", Toast.LENGTH_SHORT).show();
            }
            // Clear any pending action regardless of outcome
            recordingActionPending = null;
        }
    }

    private void showFragment() {
        if (!isFragmentSet) {
            isFragmentSet = true;
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, new SaidItFragment(), "main-fragment")
                    .commit();
        }
    }
    private void showPermissionDeniedDialog() {
        permissionDeniedDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.permission_required)
                .setMessage(R.string.permission_required_message)
                .setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Open app settings
                        Intent intent = new Intent();
                        intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Request MediaProjection permission from the user.
     * This is called by the service when it detects that device audio is enabled
     * but MediaProjection hasn't been initialized yet.
     */
    public void requestMediaProjectionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaProjectionManager projectionManager = 
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (projectionManager != null) {
                Intent captureIntent = projectionManager.createScreenCaptureIntent();
                startActivityForResult(captureIntent, MEDIA_PROJECTION_REQUEST_CODE);
                Toast.makeText(this,
                    "Please grant screen recording permission for device audio capture",
                    Toast.LENGTH_LONG).show();
            }
        }
    }
}