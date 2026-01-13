package eu.mrogalski.saidit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

public class BroadcastReceiver extends android.content.BroadcastReceiver {

    private static final String TAG = "EchoBroadcastReceiver";

    // Intent actions for automation
    public static final String ACTION_START_RECORDING = "eu.mrogalski.saidit.action.START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "eu.mrogalski.saidit.action.STOP_RECORDING";
    public static final String ACTION_ENABLE_LISTENING = "eu.mrogalski.saidit.action.ENABLE_LISTENING";
    public static final String ACTION_DISABLE_LISTENING = "eu.mrogalski.saidit.action.DISABLE_LISTENING";
    public static final String ACTION_SET_MEMORY_MODE = "eu.mrogalski.saidit.action.SET_MEMORY_MODE";
    public static final String ACTION_SET_DISK_MODE = "eu.mrogalski.saidit.action.SET_DISK_MODE";
    public static final String ACTION_SET_MEMORY_SIZE = "eu.mrogalski.saidit.action.SET_MEMORY_SIZE";
    public static final String ACTION_DUMP_RECORDING = "eu.mrogalski.saidit.action.DUMP_RECORDING";
    public static final String ACTION_DUMP_RECORDING_RANGE = "eu.mrogalski.saidit.action.DUMP_RECORDING_RANGE";
    public static final String ACTION_SCHEDULE_RECORDING = "eu.mrogalski.saidit.action.SCHEDULE_RECORDING";
    public static final String ACTION_ENABLE_VAD_TIME_WINDOW = "eu.mrogalski.saidit.action.ENABLE_VAD_TIME_WINDOW";
    public static final String ACTION_DISABLE_VAD_TIME_WINDOW = "eu.mrogalski.saidit.action.DISABLE_VAD_TIME_WINDOW";
    public static final String ACTION_SET_VAD_TIME_WINDOW = "eu.mrogalski.saidit.action.SET_VAD_TIME_WINDOW";

    // Intent extras
    public static final String EXTRA_MEMORY_SIZE_MB = "memory_size_mb";
    public static final String EXTRA_PREPEND_SECONDS = "prepend_seconds";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_FROM_SECONDS_AGO = "from_seconds_ago";
    public static final String EXTRA_TO_SECONDS_AGO = "to_seconds_ago";
    public static final String EXTRA_START_TIME_MILLIS = "start_time_millis";
    public static final String EXTRA_END_TIME_MILLIS = "end_time_millis";
    public static final String EXTRA_VAD_START_HOUR = "vad_start_hour";
    public static final String EXTRA_VAD_START_MINUTE = "vad_start_minute";
    public static final String EXTRA_VAD_END_HOUR = "vad_end_hour";
    public static final String EXTRA_VAD_END_MINUTE = "vad_end_minute";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received intent: " + action);

        // Handle boot completed - start service if tutorial finished
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE)
                    .getBoolean("skip_tutorial", false)) {
                context.startService(new Intent(context, SaidItService.class));
            }
            return;
        }

        // Handle automation intents
        // We need to bind to the service to execute commands
        bindAndExecute(context, intent);
    }

    private void bindAndExecute(final Context context, final Intent commandIntent) {
        Intent serviceIntent = new Intent(context, SaidItService.class);
        context.startService(serviceIntent); // Ensure service is running

        context.bindService(serviceIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                try {
                    SaidItService.BackgroundRecorderBinder typedBinder = 
                        (SaidItService.BackgroundRecorderBinder) binder;
                    SaidItService service = typedBinder.getService();

                    String action = commandIntent.getAction();
                    executeCommand(service, action, commandIntent);
                    Log.d(TAG, "Command executed successfully: " + action);
                } catch (ClassCastException e) {
                    Log.e(TAG, "Invalid binder type", e);
                } catch (Exception e) {
                    Log.e(TAG, "Error executing command", e);
                } finally {
                    context.unbindService(this);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // Service disconnected
            }
        }, Context.BIND_AUTO_CREATE);
    }

    private void executeCommand(SaidItService service, String action, Intent intent) {
        switch (action) {
            case ACTION_START_RECORDING:
                float prependSeconds = intent.getFloatExtra(EXTRA_PREPEND_SECONDS, 60.0f);
                // Validate prepend seconds (0 to 1 hour)
                if (prependSeconds < 0) prependSeconds = 0;
                if (prependSeconds > 3600) prependSeconds = 3600;
                service.startRecording(prependSeconds);
                Log.d(TAG, "Started recording with " + prependSeconds + "s prepend");
                break;

            case ACTION_STOP_RECORDING:
                String filename = intent.getStringExtra(EXTRA_FILENAME);
                if (filename == null) filename = "";
                service.stopRecording(null, filename);
                Log.d(TAG, "Stopped recording");
                break;

            case ACTION_ENABLE_LISTENING:
                service.enableListening();
                Log.d(TAG, "Enabled listening");
                break;

            case ACTION_DISABLE_LISTENING:
                service.disableListening();
                Log.d(TAG, "Disabled listening");
                break;

            case ACTION_SET_MEMORY_MODE:
                service.setStorageMode(StorageMode.MEMORY_ONLY);
                Log.d(TAG, "Set storage mode to MEMORY_ONLY");
                break;

            case ACTION_SET_DISK_MODE:
                service.setStorageMode(StorageMode.BATCH_TO_DISK);
                Log.d(TAG, "Set storage mode to BATCH_TO_DISK");
                break;

            case ACTION_SET_MEMORY_SIZE:
                int memorySizeMB = intent.getIntExtra(EXTRA_MEMORY_SIZE_MB, 100);
                // Validate memory size (10 MB to 10 GB)
                if (memorySizeMB < 10) memorySizeMB = 10;
                if (memorySizeMB > 10240) memorySizeMB = 10240;
                service.setMemorySizeMB(memorySizeMB);
                Log.d(TAG, "Set memory size to " + memorySizeMB + " MB");
                break;

            case ACTION_DUMP_RECORDING:
                float dumpSeconds = intent.getFloatExtra(EXTRA_PREPEND_SECONDS, 300.0f);
                // Validate dump seconds (0 to 1 hour)
                if (dumpSeconds < 0) dumpSeconds = 0;
                if (dumpSeconds > 3600) dumpSeconds = 3600;
                String dumpFilename = intent.getStringExtra(EXTRA_FILENAME);
                if (dumpFilename == null) dumpFilename = "";
                service.dumpRecording(dumpSeconds, null, dumpFilename);
                Log.d(TAG, "Dumped recording");
                break;

            case ACTION_DUMP_RECORDING_RANGE:
                float fromSeconds = intent.getFloatExtra(EXTRA_FROM_SECONDS_AGO, 300.0f);
                float toSeconds = intent.getFloatExtra(EXTRA_TO_SECONDS_AGO, 0.0f);
                // Validate range seconds (0 to 1 hour), and ensure from >= to
                if (fromSeconds < 0) fromSeconds = 0;
                if (toSeconds < 0) toSeconds = 0;
                if (fromSeconds > 3600) fromSeconds = 3600;
                if (toSeconds > 3600) toSeconds = 3600;
                if (fromSeconds < toSeconds) {
                    float tmp = fromSeconds; fromSeconds = toSeconds; toSeconds = tmp;
                }
                String rangeFilename = intent.getStringExtra(EXTRA_FILENAME);
                if (rangeFilename == null) rangeFilename = "";
                service.dumpRecordingRange(fromSeconds, toSeconds, null, rangeFilename);
                Log.d(TAG, "Dumped recording range from " + fromSeconds + "s to " + toSeconds + "s ago");
                break;
                
            case ACTION_SCHEDULE_RECORDING:
                long startTimeMillis = intent.getLongExtra(EXTRA_START_TIME_MILLIS, System.currentTimeMillis());
                long endTimeMillis = intent.getLongExtra(EXTRA_END_TIME_MILLIS, System.currentTimeMillis() + 3600000); // default 1 hour
                String schedFilename = intent.getStringExtra(EXTRA_FILENAME);
                if (schedFilename == null) schedFilename = "scheduled_recording";
                
                service.setupScheduledRecording(startTimeMillis, endTimeMillis, schedFilename);
                Log.d(TAG, "Scheduled recording from " + startTimeMillis + " to " + endTimeMillis);
                break;
                
            case ACTION_ENABLE_VAD_TIME_WINDOW:
                service.setVadTimeWindowEnabled(true);
                Log.d(TAG, "Enabled VAD time window");
                break;
                
            case ACTION_DISABLE_VAD_TIME_WINDOW:
                service.setVadTimeWindowEnabled(false);
                Log.d(TAG, "Disabled VAD time window");
                break;
                
            case ACTION_SET_VAD_TIME_WINDOW:
                int startHour = intent.getIntExtra(EXTRA_VAD_START_HOUR, 22); // default 10 PM
                int startMinute = intent.getIntExtra(EXTRA_VAD_START_MINUTE, 0);
                int endHour = intent.getIntExtra(EXTRA_VAD_END_HOUR, 6); // default 6 AM
                int endMinute = intent.getIntExtra(EXTRA_VAD_END_MINUTE, 0);
                
                // Validate hours and minutes
                if (startHour < 0 || startHour > 23) startHour = 22;
                if (startMinute < 0 || startMinute > 59) startMinute = 0;
                if (endHour < 0 || endHour > 23) endHour = 6;
                if (endMinute < 0 || endMinute > 59) endMinute = 0;
                
                service.setVadTimeWindow(startHour, startMinute, endHour, endMinute);
                Log.d(TAG, "Set VAD time window: " + startHour + ":" + startMinute + " to " + endHour + ":" + endMinute);
                break;

            default:
                Log.w(TAG, "Unknown action: " + action);
                break;
        }
    }
}
