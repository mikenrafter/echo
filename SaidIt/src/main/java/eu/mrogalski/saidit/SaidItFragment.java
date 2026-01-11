package eu.mrogalski.saidit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import java.io.File;

import eu.mrogalski.android.TimeFormat;
import eu.mrogalski.android.Views;

public class SaidItFragment extends Fragment {

    private static final String TAG = SaidItFragment.class.getSimpleName();
    private static final String YOUR_NOTIFICATION_CHANNEL_ID = "SaidItServiceChannel";
    private Button record_pause_button;
    private Button listenButton;
    private Button recordClipButton;
    private ProgressBar volumeMeter;
    private TextView rangeHelpText;
    private TextView skippedGroupsInfo;
    private Button viewSkippedSilenceButton;
    private TextView crashLogsInfo;

    ListenButtonClickListener listenButtonClickListener = new ListenButtonClickListener();
    RecordButtonClickListener recordButtonClickListener = new RecordButtonClickListener();

    private boolean isListening = true;
    private boolean isRecording = false;

    private LinearLayout ready_section;
    private TextView history_limit;
    private TextView history_size;
    // Removed history_size_title - no longer displaying "memory holds the most recent"
    private TextView volumeMeterLabel;
    private int silenceThreshold = 500; // Default from settings
    private int silenceSegmentCount = 3; // Default from settings
    private int blockSizeMinutes = 5; // Default block size for activity timeline
    
    // Activity/Silence timeline display
    private LinearLayout activityTimelineContainer;
    private LinearLayout activityTimeline;
    private TimelineSegmentSelection selectedFrom = null;
    private TimelineSegmentSelection selectedTo = null;
    
    // Cache for timeline state to avoid unnecessary re-renders
    private int lastSilenceGroupsCount = -1;
    private long lastTimelineUpdate = 0;
    private static final long TIMELINE_UPDATE_INTERVAL_MS = 2000; // Only update every 2 seconds at most
    
    // Track which activity blocks are selected for save range
    private static class TimelineSegmentSelection {
        ActivityBlockBuilder.ActivityBlock block;
        int index;
        
        TimelineSegmentSelection(ActivityBlockBuilder.ActivityBlock block, int index) {
            this.block = block;
            this.index = index;
        }
    }

    private LinearLayout rec_section;
    private TextView rec_indicator;
    private TextView rec_time;

    private ImageButton rate_on_google_play;
    private ImageView heart;

    // Callback for minute input prompts used in range export flow
    private interface MinutesCallback { void onMinutes(float minutes); }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        final Activity activity = getActivity();
        assert activity != null;
        activity.bindService(new Intent(activity, SaidItService.class), echoConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        final Activity activity = getActivity();
        assert activity != null;
        activity.unbindService(echoConnection);
        echo = null;
    }

    class ActivityResult {
        final int requestCode;
        final int resultCode;
        final Intent data;

        ActivityResult(int requestCode, int resultCode, Intent data) {
            this.requestCode = requestCode;
            this.resultCode = resultCode;
            this.data = data;
        }
    }

    private Runnable updater = new Runnable() {
        @Override
        public void run() {
            final View view = getView();
            if (view == null) return;
            if (echo == null) return;
            echo.getState(serviceStateCallback);
        }
    };

    SaidItService echo;
    private ServiceConnection echoConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            SaidItService.BackgroundRecorderBinder typedBinder = (SaidItService.BackgroundRecorderBinder) binder;
            if (echo != null && echo == typedBinder.getService()) {
                Log.d(TAG, "update loop already running, skipping");
                return;
            }
            echo = typedBinder.getService();
            getView().postOnAnimation(updater);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "onServiceDisconnected");
            echo = null;
        }
    };

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_background_recorder, container, false);

        if (rootView == null) return null;

        final Activity activity = getActivity();
        final AssetManager assets = activity.getAssets();
        final Typeface robotoCondensedBold = Typeface.createFromAsset(assets, "RobotoCondensedBold.ttf");
        final Typeface robotoCondensedRegular = Typeface.createFromAsset(assets, "RobotoCondensed-Regular.ttf");
        final float density = activity.getResources().getDisplayMetrics().density;

        Views.search((ViewGroup) rootView, new Views.SearchViewCallback() {
            @Override
            public void onView(View view, ViewGroup parent) {

                if (view instanceof Button) {
                    final Button button = (Button) view;
                    button.setTypeface(robotoCondensedBold);
                    final int shadowColor = button.getShadowColor();
                    button.setShadowLayer(0.01f, 0, density * 2, shadowColor);
                } else if (view instanceof TextView) {

                    final TextView textView = (TextView) view;
                    textView.setTypeface(robotoCondensedRegular);
                }
            }
        });

        history_limit = (TextView) rootView.findViewById(R.id.history_limit);
        history_size = (TextView) rootView.findViewById(R.id.history_size);
        // history_size_title removed

        history_limit.setTypeface(robotoCondensedBold);
        history_size.setTypeface(robotoCondensedBold);

        listenButton = (Button) rootView.findViewById(R.id.listen_button);
        if (listenButton != null) {
            listenButton.setOnClickListener(listenButtonClickListener);
        }

        final int statusBarHeight = getStatusBarHeight();
        listenButton.setPadding(listenButton.getPaddingLeft(), listenButton.getPaddingTop() + statusBarHeight, listenButton.getPaddingRight(), listenButton.getPaddingBottom());
        final ViewGroup.LayoutParams layoutParams = listenButton.getLayoutParams();
        layoutParams.height += statusBarHeight;
        listenButton.setLayoutParams(layoutParams);


        record_pause_button = (Button) rootView.findViewById(R.id.rec_stop_button);
        record_pause_button.setOnClickListener(recordButtonClickListener);

        recordClipButton = (Button) rootView.findViewById(R.id.record_clip_button);
        if (recordClipButton != null) {
            recordClipButton.setOnClickListener(recordButtonClickListener);
            recordClipButton.setOnLongClickListener(recordButtonClickListener);
        }

        volumeMeter = (ProgressBar) rootView.findViewById(R.id.volume_meter);
        volumeMeterLabel = (TextView) rootView.findViewById(R.id.volume_meter_label);
        rangeHelpText = (TextView) rootView.findViewById(R.id.range_help_text);
        skippedGroupsInfo = (TextView) rootView.findViewById(R.id.skipped_groups_info);
        viewSkippedSilenceButton = (Button) rootView.findViewById(R.id.view_skipped_silence_button);
        crashLogsInfo = (TextView) rootView.findViewById(R.id.crash_logs_info);
        
        // Activity/Silence timeline views
        activityTimelineContainer = (LinearLayout) rootView.findViewById(R.id.activity_timeline_container);
        activityTimeline = (LinearLayout) rootView.findViewById(R.id.activity_timeline);
        
        // Load silence threshold and segment count from preferences
        android.content.SharedPreferences prefs = activity.getSharedPreferences(eu.mrogalski.saidit.SaidIt.PACKAGE_NAME, android.content.Context.MODE_PRIVATE);
        silenceThreshold = prefs.getInt(eu.mrogalski.saidit.SaidIt.SILENCE_THRESHOLD_KEY, 500);
        silenceSegmentCount = prefs.getInt(eu.mrogalski.saidit.SaidIt.SILENCE_SEGMENT_COUNT_KEY, 3);
        blockSizeMinutes = prefs.getInt(eu.mrogalski.saidit.SaidIt.TIMELINE_BLOCK_SIZE_MINUTES_KEY, 5);

        ready_section = (LinearLayout) rootView.findViewById(R.id.ready_section);
        rec_section = (LinearLayout) rootView.findViewById(R.id.rec_section);
        rec_indicator = (TextView) rootView.findViewById(R.id.rec_indicator);
        rec_time = (TextView) rootView.findViewById(R.id.rec_time);

        rate_on_google_play = (ImageButton) rootView.findViewById(R.id.rate_on_google_play);

        final Animation pulse = AnimationUtils.loadAnimation(activity, R.anim.pulse);
        heart = (ImageView) rootView.findViewById(R.id.heart);
        heart.startAnimation(pulse);

        rate_on_google_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mafik/echo")));
                } catch (android.content.ActivityNotFoundException anfe) {
                    // ignore
                }
            }
        });

        heart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                heart.animate().scaleX(10).scaleY(10).alpha(0).setDuration(2000).start();
                Handler handler = new Handler(activity.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // star the app
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sponsors/mafik")));
                        } catch (android.content.ActivityNotFoundException anfe) {
                            // ignore
                        }
                    }
                }, 1000);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        heart.setAlpha(0f);
                        heart.setScaleX(1);
                        heart.setScaleY(1);
                        heart.animate().alpha(1).start();

                    }
                }, 3000);
            }
        });

        rootView.findViewById(R.id.settings_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(activity, SettingsActivity.class));
            }
        });

        if (viewSkippedSilenceButton != null) {
            viewSkippedSilenceButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(activity, SkippedSilenceActivity.class));
                }
            });
        }
        serviceStateCallback.state(isListening, isRecording, 0, 0, 0, 0);
        return rootView;
    }

    private SaidItService.StateCallback serviceStateCallback = new SaidItService.StateCallback() {
        @Override
        public void state(final boolean listeningEnabled, final boolean recording, final float memorized, final float totalMemory, final float recorded, final float skippedSeconds) {
            final Activity activity = getActivity();
            if (activity == null) return;
            final Resources resources = activity.getResources();
            if ((isRecording != recording) || (isListening != listeningEnabled)) {
                if (recording != isRecording) {
                    isRecording = recording;
                    if (recording) {
                        rec_section.setVisibility(View.VISIBLE);
                    } else {
                        rec_section.setVisibility(View.GONE);
                    }
                }

                if (listeningEnabled != isListening) {
                    isListening = listeningEnabled;
                    if (listeningEnabled) {
                        listenButton.setText(R.string.listening_enabled_disable);
                        listenButton.setBackgroundResource(R.drawable.top_green_button);
                        listenButton.setShadowLayer(0.01f, 0, resources.getDimensionPixelOffset(R.dimen.shadow_offset), resources.getColor(R.color.dark_green));
                    } else {
                        listenButton.setText(R.string.listening_disabled_enable);
                        listenButton.setBackgroundResource(R.drawable.top_gray_button);
                        listenButton.setShadowLayer(0.01f, 0, resources.getDimensionPixelOffset(R.dimen.shadow_offset), 0xff666666);
                    }
                }

                if (listeningEnabled && !recording) {
                    ready_section.setVisibility(View.VISIBLE);
                } else {
                    ready_section.setVisibility(View.GONE);
                }
            }

            TimeFormat.naturalLanguage(resources, totalMemory, timeFormatResult);

            if (!history_limit.getText().equals(timeFormatResult.text)) {
                history_limit.setText(timeFormatResult.text);
            }

            // Format memorized time as hh:mm:ss
            int memorizedSeconds = Math.round(memorized);
            int hours = memorizedSeconds / 3600;
            int minutes = (memorizedSeconds % 3600) / 60;
            int seconds = memorizedSeconds % 60;
            String memorizedText = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            
            if (!history_size.getText().equals(memorizedText)) {
                history_size.setText(memorizedText);
            }

            TimeFormat.naturalLanguage(resources, recorded, timeFormatResult);

            if (!rec_time.getText().equals(timeFormatResult.text)) {
                rec_indicator.setText(resources.getQuantityText(R.plurals.recorded, timeFormatResult.count));
                rec_time.setText(timeFormatResult.text);
            }

            // No separate skipped seconds display here; combined with groups below

            // Fetch live stats for volume and groups
            if (echo != null) {
                echo.getLiveStats(new SaidItService.LiveStatsCallback() {
                    @Override
                    public void stats(int volumeLevel, int skippedGroups) {
                        if (volumeMeter != null) {
                            volumeMeter.setProgress(Math.max(0, Math.min(32767, volumeLevel)));
                        }
                        // Update volume meter label and visibility based on silence threshold
                        if (volumeMeterLabel != null && volumeMeter != null) {
                            if (volumeLevel < silenceThreshold) {
                                volumeMeterLabel.setText("SILENT");
                            } else {
                                volumeMeterLabel.setText(R.string.volume_meter_label);
                            }
                        }
                        if (skippedGroupsInfo != null) {
                            // Combine hh:mm:ss skipped and groups into a single line above the button
                            int total = Math.max(0, Math.round(skippedSeconds));
                            int hours = total / 3600;
                            int minutes = (total % 3600) / 60;
                            int seconds = total % 60;
                            String hms = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                            if (skippedGroups > 0 || total > 0) {
                                String combined = resources.getString(R.string.silence_skipped_combined_label, hms, skippedGroups);
                                skippedGroupsInfo.setText(combined);
                                skippedGroupsInfo.setVisibility(View.VISIBLE);
                                if (viewSkippedSilenceButton != null) viewSkippedSilenceButton.setVisibility(View.VISIBLE);
                            } else {
                                skippedGroupsInfo.setVisibility(View.GONE);
                                if (viewSkippedSilenceButton != null) viewSkippedSilenceButton.setVisibility(View.GONE);
                            }
                        }
                        
                        // Display crash log count if any exist
                        if (crashLogsInfo != null && echo != null) {
                            int crashLogCount = echo.getCrashLogCount();
                            if (crashLogCount > 0) {
                                crashLogsInfo.setText(resources.getString(R.string.crash_logs_available, crashLogCount));
                                crashLogsInfo.setVisibility(View.VISIBLE);
                            } else {
                                crashLogsInfo.setVisibility(View.GONE);
                            }
                        }
                        
                        // Update timeline only when skipped groups count changes or enough time has passed
                        // This prevents constant re-rendering that slows down the clock
                        long now = System.currentTimeMillis();
                        if (skippedGroups != lastSilenceGroupsCount || 
                            (now - lastTimelineUpdate) > TIMELINE_UPDATE_INTERVAL_MS) {
                            lastSilenceGroupsCount = skippedGroups;
                            lastTimelineUpdate = now;
                            updateTimelineDisplay();
                        }
                    }
                });
            }

            history_size.postOnAnimationDelayed(updater, 100);
        }
    };
    
    // Update the activity/silence timeline display
    // Only updates when there are actual changes to silence groups
    private void updateTimelineDisplay() {
        if (echo == null || activityTimeline == null || activityTimelineContainer == null) return;
        
        // Fetch silence groups to determine activity blocks
        // Silence groups only change at 20s segment boundaries, not continuously
        echo.getSilenceGroups(new SaidItService.SilenceGroupsCallback() {
            @Override
            public void onGroups(java.util.List<SaidItService.SilenceGroup> silenceGroups) {
                if (echo == null) return;
                echo.getTimeline(new SaidItService.TimelineCallback() {
                    @Override
                    public void onTimeline(java.util.List<SaidItService.TimelineSegment> segments, SaidItService.TimelineSegment currentSegment, float totalMemorySeconds) {
                        final Activity activity = getActivity();
                        if (activity == null) return;
                        
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Use block size from settings
                                long blockSizeMillis = blockSizeMinutes * 60 * 1000;
                                
                                // Build activity blocks from silence groups and memory span
                                java.util.List<ActivityBlockBuilder.ActivityBlock> activityBlocks = 
                                    ActivityBlockBuilder.buildActivityBlocks(
                                        silenceGroups, totalMemorySeconds, blockSizeMillis
                                    );
                                
                                // Check if we have any content to display
                                boolean hasContent = (activityBlocks != null && !activityBlocks.isEmpty()) ||
                                    (silenceGroups != null && !silenceGroups.isEmpty());
                                
                                if (!hasContent) {
                                    // Keep container visible but show it's empty if it was visible before
                                    if (activityTimelineContainer.getVisibility() == View.VISIBLE) {
                                        activityTimeline.removeAllViews();
                                    }
                                    return;
                                }
                                
                                // Make timeline always visible once we have content
                                activityTimelineContainer.setVisibility(View.VISIBLE);
                                
                                // Clear and rebuild only when there are changes
                                activityTimeline.removeAllViews();
                                
                                // Always show save buttons for activity blocks
                                boolean showSaveButtons = true;
                                
                                // Display activity blocks in reverse order (newest first)
                                if (activityBlocks != null && !activityBlocks.isEmpty()) {
                                    for (int i = activityBlocks.size() - 1; i >= 0; i--) {
                                        ActivityBlockBuilder.ActivityBlock block = activityBlocks.get(i);
                                        addActivityBlockView(block, i, showSaveButtons);
                                    }
                                }
                                
                                // Display silence groups in reverse order (newest first)
                                if (silenceGroups != null && !silenceGroups.isEmpty()) {
                                    for (int i = silenceGroups.size() - 1; i >= 0; i--) {
                                        SaidItService.SilenceGroup group = silenceGroups.get(i);
                                        addSilenceGroupView(group);
                                    }
                                }
                            }
                        });
                    }
                });
            }
        });
    }
    
    // Add a view for an activity block calculated from silence groups
    private void addActivityBlockView(final ActivityBlockBuilder.ActivityBlock block, final int blockIndex, boolean showSaveButton) {
        final Activity activity = getActivity();
        if (activity == null) return;
        
        LinearLayout blockLayout = new LinearLayout(activity);
        blockLayout.setOrientation(LinearLayout.HORIZONTAL);
        blockLayout.setPadding(10, 5, 10, 5);
        
        TextView textView = new TextView(activity);
        int durationSeconds = (int)(block.durationMillis / 1000);
        String durationStr = formatDuration(durationSeconds);
        textView.setText(String.format("Activity: %s", durationStr));
        textView.setTextSize(14);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        ));
        
        blockLayout.addView(textView);
        
        if (showSaveButton) {
            Button saveButton = new Button(activity);
            // Set button text based on selection state
            if (selectedFrom == null) {
                saveButton.setText(R.string.save_from_here);
            } else {
                saveButton.setText(R.string.save_to_here);
            }
            saveButton.setTextSize(12);
            saveButton.setPadding(20, 10, 20, 10);
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleActivityBlockSelection(block, blockIndex);
                }
            });
            
            blockLayout.addView(saveButton);
        }
        
        activityTimeline.addView(blockLayout);
    }
    


    // Add a view for a silence group from AudioMemory (20-second segments)
    private void addSilenceGroupView(SaidItService.SilenceGroup group) {
        final Activity activity = getActivity();
        if (activity == null) return;
        
        TextView textView = new TextView(activity);
        long durationMillis = group.durationMillis;
        int durationSeconds = (int)(durationMillis / 1000);
        String durationStr = formatDuration(durationSeconds);
        textView.setText(String.format("Silence: %s", durationStr));
        textView.setTextSize(14);
        textView.setPadding(10, 5, 10, 5);
        textView.setTextColor(0xFF888888);
        
        activityTimeline.addView(textView);
    }
    // Handle selection of a timeline segment for save range
    private void handleActivityBlockSelection(ActivityBlockBuilder.ActivityBlock block, int index) {
        final Activity activity = getActivity();
        if (activity == null) return;
        
        if (selectedFrom == null) {
            // First selection - set FROM
            selectedFrom = new TimelineSegmentSelection(block, index);
            Toast.makeText(activity, "FROM selected. Now select TO.", Toast.LENGTH_SHORT).show();
            // Update button text to "save to here"
            updateTimelineDisplay(); // Refresh to show new button states
        } else if (selectedTo == null) {
            // Second selection - set TO
            selectedTo = new TimelineSegmentSelection(block, index);
            
            // Validate and initiate save
            initiateRangeSave();
            
            // Reset selection
            selectedFrom = null;
            selectedTo = null;
            updateTimelineDisplay(); // Refresh to show original button states
        }
    }
    
    // Initiate save of selected range from activity blocks
    private void initiateRangeSave() {
        final Activity activity = getActivity();
        if (activity == null || selectedFrom == null || selectedTo == null || echo == null) return;
        
        // Calculate time ranges based on activity block timestamps
        long currentTime = System.currentTimeMillis();
        
        // Get the start and end times of the selected blocks
        long fromStartTime = selectedFrom.block.startTimeMillis;
        long toEndTime = selectedTo.block.endTimeMillis;
        
        // Convert to seconds ago (from current time)
        float fromSecondsTmp = (currentTime - fromStartTime) / 1000f;
        float toSecondsTmp = (currentTime - toEndTime) / 1000f;
        
        // Ensure correct order (fromSecondsAgo should be larger than toSecondsAgo)
        if (fromSecondsTmp < toSecondsTmp) {
            float temp = fromSecondsTmp;
            fromSecondsTmp = toSecondsTmp;
            toSecondsTmp = temp;
        }
        
        final float fromSecondsAgo = fromSecondsTmp;
        final float toSecondsAgo = toSecondsTmp;
        
        // Prompt for filename
        View dialogView = View.inflate(activity, R.layout.dialog_save_recording, null);
        EditText fileName = dialogView.findViewById(R.id.recording_name);
        
        new AlertDialog.Builder(activity)
            .setView(dialogView)
            .setTitle("Save Recording Range")
            .setMessage(String.format("FROM: %s ago\nTO: %s ago", 
                formatDuration((int)fromSecondsAgo), 
                formatDuration((int)toSecondsAgo)))
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(fileName.getText().toString().length() > 0){
                        echo.dumpRecordingRange(fromSecondsAgo, toSecondsAgo, 
                            new SaidItFragment.PromptFileReceiver(activity), 
                            fileName.getText().toString());
                    } else {
                        Toast.makeText(activity, "Please enter a file name", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    // Format duration in seconds to HH:MM:SS
    private String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    final TimeFormat.Result timeFormatResult = new TimeFormat.Result();


    private class ListenButtonClickListener implements View.OnClickListener {

        @SuppressLint("ValidFragment")
        final WorkingDialog dialog = new WorkingDialog();

        public ListenButtonClickListener() {
            dialog.setDescriptionStringId(R.string.work_preparing_memory);
        }

        @Override
        public void onClick(View v) {
            echo.getState(new SaidItService.StateCallback() {
                @Override
                public void state(final boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded, float skippedSeconds) {
                    if (listeningEnabled) {
                        echo.disableListening();
                    } else {
                        dialog.show(getFragmentManager(), "Preparing memory");

                        new Handler().post(new Runnable() {
                            @Override
                            public void run() {
                                echo.enableListening();
                                echo.getState(new SaidItService.StateCallback() {
                                    @Override
                                    public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded, float skippedSeconds) {
                                        dialog.dismiss();
                                    }
                                });
                            }
                        });
                    }
                }
            });
        }
    }

    private class RecordButtonClickListener implements View.OnClickListener, View.OnLongClickListener {

        @Override
        public void onClick(final View v) {
            record(v, false);
        }

        @Override
        public boolean onLongClick(final View v) {
            record(v, true);
            return true;
        }

        public void record(final View button, final boolean keepRecording) {
            echo.getState(new SaidItService.StateCallback() {
                @Override
                public void state(final boolean listeningEnabled, final boolean recording, float memorized, float totalMemory, float recorded, float skippedSeconds) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final boolean isClipButton = button.getId() == R.id.record_clip_button;
                            final float defaultSeconds = isClipButton ? 300f : 0f;
                            if (recording) {
                                echo.stopRecording(new PromptFileReceiver(getActivity()),"");
                            } else {
                                // Proceed with recording start - the service will request MediaProjection if needed
                                doStartRecording(keepRecording, isClipButton, defaultSeconds);
                            }
                        }
                    });
                }
            });
        }

        private void doStartRecording(final boolean keepRecording, final boolean isClipButton, final float defaultSeconds) {
            ProgressDialog pd = new ProgressDialog(getActivity());
            pd.setMessage("Recording...");
            pd.show();
            if (keepRecording) {
                echo.startRecording(defaultSeconds);
            } else {
                if (isClipButton) {
                    // Range export flow: select FROM then TO, then filename
                    pd.dismiss();
                    showRangeExportDialog(defaultSeconds);
                } else {
                    echo.startRecording(defaultSeconds);
                }
            }
        }

        private void showRangeExportDialog(final float defaultStartSeconds) {
            final Context ctx = getActivity();

            // Step 1: FROM time
            final String[] options = new String[]{
                    "Last 1 minute", "Last 5 minutes", "Last 30 minutes",
                    "Last 2 hours", "Last 6 hours", "Max", "Other…"
            };
            final float[] values = new float[]{ 60f, 300f, 1800f, 7200f, 21600f, 60f * 60f * 24f * 365f, -1f };
            int defaultIndex = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == defaultStartSeconds) { defaultIndex = i; break; }
            }

            new AlertDialog.Builder(ctx)
                    .setTitle("Select FROM time")
                    .setSingleChoiceItems(options, defaultIndex, null)
                    .setPositiveButton("Next", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            AlertDialog ad = (AlertDialog) dialog;
                            int selected = ad.getListView().getCheckedItemPosition();
                            float startSec = values[selected];
                            if (startSec < 0) {
                                // Other… prompt
                                promptForMinutes(ctx, "Enter minutes (FROM)", new MinutesCallback() {
                                    @Override public void onMinutes(float minutes) {
                                        proceedToToTime(ctx, minutes * 60f);
                                    }
                                });
                            } else {
                                proceedToToTime(ctx, startSec);
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void proceedToToTime(final Context ctx, final float startSec) {
            // Step 2: TO time (Now or Other…)
            final String[] toOptions = new String[]{ "Now", "Other…" };
            new AlertDialog.Builder(ctx)
                    .setTitle("Select TO time")
                    .setItems(toOptions, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {
                                // Now
                                promptForFileNameAndExport(ctx, startSec, 0f);
                            } else {
                                // Other… minutes
                                promptForMinutes(ctx, "Enter minutes (TO)", new MinutesCallback() {
                                    @Override public void onMinutes(float minutes) {
                                        float endSec = Math.max(0f, Math.min(startSec, minutes * 60f));
                                        promptForFileNameAndExport(ctx, startSec, endSec);
                                    }
                                });
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void promptForFileNameAndExport(final Context ctx, final float startSec, final float endSec) {
            View dialogView = View.inflate(ctx, R.layout.dialog_save_recording, null);
            EditText fileName = dialogView.findViewById(R.id.recording_name);
            new AlertDialog.Builder(ctx)
                    .setView(dialogView)
                    .setTitle("Save Recording Range")
                    .setMessage("FROM: " + (int)(startSec/60f) + " min ago\nTO: " + (endSec == 0f ? "Now" : ((int)(endSec/60f) + " min ago")))
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if(fileName.getText().toString().length() > 0){
                                echo.dumpRecordingRange(startSec, endSec, new PromptFileReceiver(getActivity()), fileName.getText().toString());
                            } else {
                                Toast.makeText(ctx, "Please enter a file name", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }


        private void promptForMinutes(Context ctx, String title, MinutesCallback cb) {
            final EditText input = new EditText(ctx);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setHint("e.g. 15");
            new AlertDialog.Builder(ctx)
                    .setTitle(title)
                    .setView(input)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            try {
                                String txt = input.getText().toString();
                                float minutes = Float.parseFloat(txt);
                                cb.onMinutes(minutes);
                            } catch (Exception e) {
                                Toast.makeText(ctx, "Invalid minutes", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    static Notification buildNotificationForFile(Context context, File outFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri fileUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", outFile);
        intent.setDataAndType(fileUri, "audio/wav");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Grant read permission to the receiving app

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, YOUR_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.recording_saved))
                .setContentText(outFile.getName())
                .setSmallIcon(R.drawable.ic_stat_notify_recorded)
                .setTicker(outFile.getName())
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notificationBuilder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
        return notificationBuilder.build();
    }

    static class NotifyFileReceiver implements SaidItService.WavFileReceiver {

        private Context context;

        public NotifyFileReceiver(Context context) {
            this.context = context;
        }

        @Override
        public void fileReady(final File file, float runtime) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            notificationManager.notify(43, buildNotificationForFile(context, file));
        }
    }

    static class PromptFileReceiver implements SaidItService.WavFileReceiver {

        private Activity activity;

        public PromptFileReceiver(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void fileReady(final File file, float runtime) {
            new RecordingDoneDialog()
                    .setFile(file)
                    .setRuntime(runtime)
                    .show(activity.getFragmentManager(), "Recording Done");
        }
    }
}
