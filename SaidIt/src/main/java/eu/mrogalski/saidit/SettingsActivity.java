package eu.mrogalski.saidit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import eu.mrogalski.StringFormat;
import eu.mrogalski.android.TimeFormat;
import eu.mrogalski.android.Views;

public class SettingsActivity extends Activity {
    static final String TAG = SettingsActivity.class.getSimpleName();
    private final MemoryOnClickListener memoryClickListener = new MemoryOnClickListener();
    private final QualityOnClickListener qualityClickListener = new QualityOnClickListener();
    private final CustomMemoryApplyListener customMemoryApplyListener = new CustomMemoryApplyListener();
    private final StorageModeClickListener storageModeClickListener = new StorageModeClickListener();

    private static final int MEDIA_PROJECTION_REQUEST_CODE = 1001;
    private MediaProjectionManager mediaProjectionManager;
    private boolean waitingForMediaProjection = false;

    final WorkingDialog dialog = new WorkingDialog();

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, SaidItService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    SaidItService service;
    ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            SaidItService.BackgroundRecorderBinder typedBinder = (SaidItService.BackgroundRecorderBinder) binder;
            service = typedBinder.getService();
            syncUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            service = null;
        }
    };

    final TimeFormat.Result timeFormatResult = new TimeFormat.Result();

    private void syncUI() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("maxMemory = " + maxMemory);
        System.out.println("totalMemory = " + Runtime.getRuntime().totalMemory());

        ((Button) findViewById(R.id.memory_low)).setText(StringFormat.shortFileSize(maxMemory / 4));
        ((Button) findViewById(R.id.memory_medium)).setText(StringFormat.shortFileSize(maxMemory / 2));
//        ((Button) findViewById(R.id.memory_high)).setText(StringFormat.shortFileSize(maxMemory * 3 / 4));
        ((Button) findViewById(R.id.memory_high)).setText(StringFormat.shortFileSize((long) (maxMemory * 0.90)));


        // Display memory info with ring breakdown if gradient is enabled
        SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        boolean gradientEnabled = prefs.getBoolean(SaidIt.GRADIENT_QUALITY_ENABLED_KEY, false);
        
        if (gradientEnabled) {
            int highRate = prefs.getInt(SaidIt.GRADIENT_QUALITY_HIGH_RATE_KEY, 48000);
            int midRate = prefs.getInt(SaidIt.GRADIENT_QUALITY_MID_RATE_KEY, 16000);
            int lowRate = prefs.getInt(SaidIt.GRADIENT_QUALITY_LOW_RATE_KEY, 8000);
            
            String ringInfo = String.format("%d kHz: 0-5m, %d kHz: 5-20m, %d kHz: 20m+", 
                highRate/1000, midRate/1000, lowRate/1000);
            ((TextView)findViewById(R.id.history_limit)).setText(ringInfo);
        } else {
            TimeFormat.naturalLanguage(getResources(), service.getBytesToSeconds() * service.getMemorySize(), timeFormatResult);
            ((TextView)findViewById(R.id.history_limit)).setText(timeFormatResult.text);
        }

        highlightButtons();
    }

    void highlightButtons() {
        final long maxMemory = Runtime.getRuntime().maxMemory();

        int button = (int)(service.getMemorySize() / (maxMemory / 4)); // 1 - memory_low; 2 - memory_medium; 3 - memory_high
        highlightButton(R.id.memory_low, R.id.memory_medium, R.id.memory_high, button);

        int samplingRate = service.getSamplingRate();
        if(samplingRate >= 44100) button = 3;
        else if(samplingRate >= 16000) button = 2;
        else button = 1;
        highlightButton(R.id.quality_8kHz, R.id.quality_16kHz, R.id.quality_48kHz, button);
        
        // Highlight storage mode
        StorageMode mode = service.getStorageMode();
        highlightButton(R.id.storage_mode_memory, R.id.storage_mode_disk, 
            mode == StorageMode.MEMORY_ONLY ? 1 : 2);
    }

    private void highlightButton(int button1, int button2, int i) {
        findViewById(button1).setBackgroundResource(1 == i ? R.drawable.green_button : R.drawable.gray_button);
        findViewById(button2).setBackgroundResource(2 == i ? R.drawable.green_button : R.drawable.gray_button);
    }

    private void highlightButton(int button1, int button2, int button3, int i) {
        findViewById(button1).setBackgroundResource(1 == i ? R.drawable.green_button : R.drawable.gray_button);
        findViewById(button2).setBackgroundResource(2 == i ? R.drawable.green_button : R.drawable.gray_button);
        findViewById(button3).setBackgroundResource(3 == i ? R.drawable.green_button : R.drawable.gray_button);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Handle MediaProjection permission for both device audio and dual-source
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE || requestCode == MEDIA_PROJECTION_REQUEST_CODE + 1) {
            final boolean isDualSource = (requestCode == MEDIA_PROJECTION_REQUEST_CODE + 1);
            final CheckBox deviceAudioEnabled = (CheckBox) findViewById(R.id.device_audio_enabled);
            final CheckBox dualSourceEnabled = (CheckBox) findViewById(R.id.dual_source_enabled);
            final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
            
            if (resultCode == RESULT_OK && data != null) {
                // User granted permission - pass to service to create MediaProjection
                // This must be done in the service context since Android 14+ requires
                // MediaProjection to be created in a foreground service with MEDIA_PROJECTION type
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && service != null) {
                    boolean success = service.initializeMediaProjection(resultCode, data);
                    
                    if (success) {
                        if (isDualSource) {
                            prefs.edit().putBoolean(SaidIt.DUAL_SOURCE_RECORDING_KEY, true).apply();
                            service.setDualSourceRecording(true);
                            if (dualSourceEnabled != null) {
                                dualSourceEnabled.setChecked(true);
                            }
                            Toast.makeText(this,
                                "Dual-source recording enabled (restart listening to apply)",
                                Toast.LENGTH_LONG).show();
                        } else {
                            prefs.edit().putBoolean(SaidIt.RECORD_DEVICE_AUDIO_KEY, true).apply();
                            service.setDeviceAudioRecording(true);
                            if (deviceAudioEnabled != null) {
                                deviceAudioEnabled.setChecked(true);
                            }
                            Toast.makeText(this,
                                "Device audio recording enabled (restart listening to apply)",
                                Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // Failed to initialize MediaProjection
                        if (isDualSource && dualSourceEnabled != null) {
                            dualSourceEnabled.setChecked(false);
                        } else if (deviceAudioEnabled != null) {
                            deviceAudioEnabled.setChecked(false);
                        }
                        Toast.makeText(this,
                            "Failed to initialize screen recording. Please try again.",
                            Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                // User denied permission
                if (isDualSource && dualSourceEnabled != null) {
                    dualSourceEnabled.setChecked(false);
                    prefs.edit().putBoolean(SaidIt.DUAL_SOURCE_RECORDING_KEY, false).apply();
                } else if (deviceAudioEnabled != null) {
                    deviceAudioEnabled.setChecked(false);
                    prefs.edit().putBoolean(SaidIt.RECORD_DEVICE_AUDIO_KEY, false).apply();
                }
                Toast.makeText(this,
                    "Screen recording permission denied. Device audio capture disabled.",
                    Toast.LENGTH_LONG).show();
            }
            waitingForMediaProjection = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark theme if enabled
        SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(SaidIt.DARK_MODE_KEY, false);
        if (isDarkMode) {
            setTheme(R.style.SaidItDark);
        }
        
        super.onCreate(savedInstanceState);

        // Initialize MediaProjectionManager for device audio capture
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }

        final AssetManager assets = getAssets();
        final Resources resources = getResources();

        final float density = resources.getDisplayMetrics().density;

        final Typeface robotoCondensedBold = Typeface.createFromAsset(assets,"RobotoCondensedBold.ttf");
        final Typeface robotoCondensedRegular = Typeface.createFromAsset(assets, "RobotoCondensed-Regular.ttf");

        final ViewGroup root = (ViewGroup) getLayoutInflater().inflate(R.layout.activity_settings, null);
        Views.search(root, new Views.SearchViewCallback() {
            @Override
            public void onView(View view, ViewGroup parent) {
                if(view instanceof Button) {
                    final Button button = (Button) view;
                    button.setTypeface(robotoCondensedBold);
                } else if(view instanceof TextView) {
                    final String tag = (String) view.getTag();
                    final TextView textView = (TextView) view;
                    if(tag != null) {
                        if(tag.equals("bold")) {
                            textView.setTypeface(robotoCondensedBold);
                        } else {
                            textView.setTypeface(robotoCondensedRegular);
                        }
                    } else {
                        textView.setTypeface(robotoCondensedRegular);
                    }
                }
            }
        });

        root.findViewById(R.id.settings_return).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        final LinearLayout settingsLayout = (LinearLayout) root.findViewById(R.id.settings_layout);

        final FrameLayout myFrameLayout = new FrameLayout(this) {
            @Override
            protected boolean fitSystemWindows(Rect insets) {
                settingsLayout.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return true;
            }
        };

        myFrameLayout.addView(root);

        root.findViewById(R.id.memory_low).setOnClickListener(memoryClickListener);
        root.findViewById(R.id.memory_medium).setOnClickListener(memoryClickListener);
        root.findViewById(R.id.memory_high).setOnClickListener(memoryClickListener);

        root.findViewById(R.id.custom_memory_apply).setOnClickListener(customMemoryApplyListener);

        root.findViewById(R.id.storage_mode_memory).setOnClickListener(storageModeClickListener);
        root.findViewById(R.id.storage_mode_disk).setOnClickListener(storageModeClickListener);

        // Initialize boot recording controls
        initBootRecordingControls(root);

        initSampleRateButton(root, R.id.quality_8kHz, 8000, 11025);
        initSampleRateButton(root, R.id.quality_16kHz, 16000, 22050);
        initSampleRateButton(root, R.id.quality_48kHz, 48000, 44100);
        
        // Initialize silence skipping controls
        initSilenceSkippingControls(root);

        // Initialize activity detection controls
        initActivityDetectionControls(root);
        
        // Initialize VAD time window controls
        initVadTimeWindowControls(root);
        
        // Initialize device audio controls
        initDeviceAudioControls(root);
        
        // Initialize dual-source controls
        initDualSourceControls(root);
        
        // Initialize auto-save controls
        initAutoSaveControls(root);

        // Initialize export effects controls
        initExportEffectsControls(root);
        
        // Initialize gradient quality controls
        initGradientQualityControls(root);
        
        // Initialize dark mode controls
        initDarkModeControls(root);
        
        // Initialize accordion/collapsible sections
        initAccordionSections(root);

    // Initialize debug memory controls
    initDebugMemoryControls(root);

        //debugPrintCodecs();

        dialog.setDescriptionStringId(R.string.work_preparing_memory);

        setContentView(myFrameLayout);
    }

    private void debugPrintCodecs() {
        final int codecCount = MediaCodecList.getCodecCount();
        for(int i = 0; i < codecCount; ++i) {
            final MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if(!info.isEncoder()) continue;
            boolean audioFound = false;
            String types = "";
            final String[] supportedTypes = info.getSupportedTypes();
            for(int j = 0; j < supportedTypes.length; ++j) {
                if(j > 0)
                    types += ", ";
                types += supportedTypes[j];
                if(supportedTypes[j].startsWith("audio")) audioFound = true;
            }
            if(!audioFound) continue;
            Log.d(TAG, "Codec " + i + ": " + info.getName() + " (" + types + ") encoder: " + info.isEncoder());
        }
    }

    private void initSampleRateButton(ViewGroup layout, int buttonId, int primarySampleRate, int secondarySampleRate) {
        Button button = (Button) layout.findViewById(buttonId);
        button.setOnClickListener(qualityClickListener);
        if(testSampleRateValid(primarySampleRate)) {
            button.setText(String.format("%d kHz", primarySampleRate / 1000));
            button.setTag(primarySampleRate);
        } else if(testSampleRateValid(secondarySampleRate)) {
            button.setText(String.format("%d kHz", secondarySampleRate / 1000));
            button.setTag(secondarySampleRate);
        } else {
            button.setVisibility(View.GONE);
        }
    }

    private boolean testSampleRateValid(int sampleRate) {
        final int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        return bufferSize > 0;
    }

    private class MemoryOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            final long memory = getMultiplier(v) * Runtime.getRuntime().maxMemory() / 4;
            dialog.show(getFragmentManager(), "Preparing memory");

            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    service.setMemorySize(memory);
                    service.getState(new SaidItService.StateCallback() {
                        @Override
                        public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded, float skippedSeconds) {
                            syncUI();
                            if (dialog.isVisible()) dialog.dismiss();
                        }
                    });
                }
            });
        }

        private int getMultiplier(View button) {
            switch (button.getId()) {
                case R.id.memory_high: return 3;
                case R.id.memory_medium: return 2;
                case R.id.memory_low: return 1;
            }
            return 0;
        }
    }

    private class QualityOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            final int sampleRate = getSampleRate(v);
            dialog.show(getFragmentManager(), "Preparing memory");

            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    service.setSampleRate(sampleRate);
                    service.getState(new SaidItService.StateCallback() {
                        @Override
                        public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded, float skippedSeconds) {
                            syncUI();
                            if (dialog.isVisible()) dialog.dismiss();
                        }
                    });
                }
            });
        }

        private int getSampleRate(View button) {
            Object tag = button.getTag();
            if(tag instanceof Integer) {
                return ((Integer) tag).intValue();
            }
            return 8000;
        }
    }

    private class CustomMemoryApplyListener implements View.OnClickListener {
        private static final int MIN_MEMORY_MB = 10;

        @Override
        public void onClick(View v) {
            EditText input = (EditText) findViewById(R.id.custom_memory_input);
            String text = input.getText().toString().trim();
            
            if (text.isEmpty()) {
                Toast.makeText(SettingsActivity.this, R.string.custom_memory_hint, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                final int memorySizeMB = Integer.parseInt(text);
                final long maxMemoryBytes = Runtime.getRuntime().maxMemory();
                final int maxMemoryMB = (int) (maxMemoryBytes / (1024 * 1024));

                if (memorySizeMB < MIN_MEMORY_MB || memorySizeMB > maxMemoryMB) {
                    String message = getString(R.string.invalid_memory_size, maxMemoryMB);
                    Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                    return;
                }

                dialog.show(getFragmentManager(), "Preparing memory");

                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        service.setMemorySizeMB(memorySizeMB);
                        service.getState(new SaidItService.StateCallback() {
                            @Override
                            public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded, float skippedSeconds) {
                                syncUI();
                                String message = getString(R.string.memory_size_applied, memorySizeMB);
                                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                                if (dialog.isVisible()) dialog.dismiss();
                            }
                        });
                    }
                });
            } catch (NumberFormatException e) {
                Toast.makeText(SettingsActivity.this, R.string.custom_memory_hint, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class StorageModeClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            final StorageMode mode = getStorageMode(v);
            
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    service.setStorageMode(mode);
                    highlightButtons();
                    String modeName = mode == StorageMode.MEMORY_ONLY ? 
                        getString(R.string.storage_mode_memory) : 
                        getString(R.string.storage_mode_disk);
                    Toast.makeText(SettingsActivity.this, 
                        "Storage mode: " + modeName, 
                        Toast.LENGTH_SHORT).show();
                }
            });
        }

        private StorageMode getStorageMode(View button) {
            switch (button.getId()) {
                case R.id.storage_mode_disk:
                    return StorageMode.BATCH_TO_DISK;
                case R.id.storage_mode_memory:
                default:
                    return StorageMode.MEMORY_ONLY;
            }
        }
    }
    
    private void initSilenceSkippingControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        final CheckBox silenceSkipEnabled = (CheckBox) root.findViewById(R.id.silence_skip_enabled);
        silenceSkipEnabled.setChecked(prefs.getBoolean(SaidIt.SILENCE_SKIP_ENABLED_KEY, false));
        
        final SeekBar thresholdSlider = (SeekBar) root.findViewById(R.id.silence_threshold_slider);
        final TextView thresholdValue = (TextView) root.findViewById(R.id.silence_threshold_value);
        int threshold = prefs.getInt(SaidIt.SILENCE_THRESHOLD_KEY, 500);
        thresholdSlider.setProgress(threshold);
        thresholdValue.setText(String.valueOf(threshold));
        
        thresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thresholdValue.setText(String.valueOf(progress));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int threshold = seekBar.getProgress();
                int segmentCount = prefs.getInt(SaidIt.SILENCE_SEGMENT_COUNT_KEY, 3);
                boolean enabled = silenceSkipEnabled.isChecked();
                service.configureSilenceSkipping(enabled, threshold, segmentCount);
                prefs.edit().putInt(SaidIt.SILENCE_THRESHOLD_KEY, threshold).apply();
            }
        });
        
        final SeekBar segmentCountSlider = (SeekBar) root.findViewById(R.id.silence_segment_count_slider);
        final TextView segmentCountValue = (TextView) root.findViewById(R.id.silence_segment_count_value);
        int segmentCount = prefs.getInt(SaidIt.SILENCE_SEGMENT_COUNT_KEY, 3);
        segmentCountSlider.setProgress(segmentCount);
        segmentCountValue.setText(String.valueOf(segmentCount));
        
        segmentCountSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                segmentCountValue.setText(String.valueOf(Math.max(1, progress)));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int segmentCount = Math.max(1, seekBar.getProgress());
                int threshold = prefs.getInt(SaidIt.SILENCE_THRESHOLD_KEY, 500);
                boolean enabled = silenceSkipEnabled.isChecked();
                service.configureSilenceSkipping(enabled, threshold, segmentCount);
                prefs.edit().putInt(SaidIt.SILENCE_SEGMENT_COUNT_KEY, segmentCount).apply();
            }
        });
        
        silenceSkipEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int threshold = prefs.getInt(SaidIt.SILENCE_THRESHOLD_KEY, 500);
                int segmentCount = prefs.getInt(SaidIt.SILENCE_SEGMENT_COUNT_KEY, 3);
                service.configureSilenceSkipping(isChecked, threshold, segmentCount);
                prefs.edit().putBoolean(SaidIt.SILENCE_SKIP_ENABLED_KEY, isChecked).apply();
                Toast.makeText(SettingsActivity.this, 
                    "Silence skipping " + (isChecked ? "enabled" : "disabled"), 
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initActivityDetectionControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);

        final CheckBox activityEnabled = (CheckBox) root.findViewById(R.id.activity_detection_enabled);
        final SeekBar thresholdSlider = (SeekBar) root.findViewById(R.id.activity_detection_threshold_slider);
        final TextView thresholdValue = (TextView) root.findViewById(R.id.activity_detection_threshold_value);
        final EditText preBufferInput = (EditText) root.findViewById(R.id.activity_prebuffer_input);
        final EditText postBufferInput = (EditText) root.findViewById(R.id.activity_postbuffer_input);
        final EditText autoDeleteInput = (EditText) root.findViewById(R.id.activity_autodelete_input);
        final CheckBox highBitrate = (CheckBox) root.findViewById(R.id.activity_high_bitrate);

        // Load existing preferences
        int savedThreshold = Math.max(50, Math.round(prefs.getFloat(SaidIt.ACTIVITY_DETECTION_THRESHOLD_KEY, 500f)));
        thresholdSlider.setProgress(savedThreshold);
        thresholdValue.setText(String.valueOf(savedThreshold));
        activityEnabled.setChecked(prefs.getBoolean(SaidIt.ACTIVITY_DETECTION_ENABLED_KEY, false));
        preBufferInput.setText(String.valueOf(prefs.getInt(SaidIt.ACTIVITY_PRE_BUFFER_SECONDS_KEY, 20)));
        postBufferInput.setText(String.valueOf(prefs.getInt(SaidIt.ACTIVITY_POST_BUFFER_SECONDS_KEY, 20)));
        autoDeleteInput.setText(String.valueOf(prefs.getInt(SaidIt.ACTIVITY_AUTO_DELETE_DAYS_KEY, 7)));
        highBitrate.setChecked(prefs.getBoolean(SaidIt.ACTIVITY_HIGH_BITRATE_KEY, false));

        final Runnable applyWithToast = new Runnable() {
            @Override
            public void run() {
                applyActivityDetectionConfig(prefs, activityEnabled, thresholdSlider, preBufferInput,
                    postBufferInput, autoDeleteInput, highBitrate, true);
            }
        };

        final Runnable applySilently = new Runnable() {
            @Override
            public void run() {
                applyActivityDetectionConfig(prefs, activityEnabled, thresholdSlider, preBufferInput,
                    postBufferInput, autoDeleteInput, highBitrate, false);
            }
        };

        thresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int clamped = Math.max(50, progress);
                thresholdValue.setText(String.valueOf(clamped));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar.getProgress() < 50) {
                    seekBar.setProgress(50);
                }
                applyWithToast.run();
            }
        });

        activityEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applyWithToast.run();
            }
        });

        highBitrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applySilently.run();
            }
        });

        View.OnFocusChangeListener onBlurApply = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    applyWithToast.run();
                }
            }
        };

        preBufferInput.setOnFocusChangeListener(onBlurApply);
        postBufferInput.setOnFocusChangeListener(onBlurApply);
        autoDeleteInput.setOnFocusChangeListener(onBlurApply);
    }

    private void initVadTimeWindowControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        final CheckBox vadTimeWindowEnabled = (CheckBox) root.findViewById(R.id.vad_time_window_enabled);
        final EditText vadStartHour = (EditText) root.findViewById(R.id.vad_start_hour);
        final EditText vadStartMinute = (EditText) root.findViewById(R.id.vad_start_minute);
        final EditText vadEndHour = (EditText) root.findViewById(R.id.vad_end_hour);
        final EditText vadEndMinute = (EditText) root.findViewById(R.id.vad_end_minute);
        
        if (vadTimeWindowEnabled == null) {
            return; // UI elements not found, skip initialization
        }
        
        // Load existing preferences
        vadTimeWindowEnabled.setChecked(prefs.getBoolean(SaidIt.VAD_TIME_WINDOW_ENABLED_KEY, false));
        vadStartHour.setText(String.valueOf(prefs.getInt(SaidIt.VAD_START_HOUR_KEY, 22)));
        vadStartMinute.setText(String.valueOf(prefs.getInt(SaidIt.VAD_START_MINUTE_KEY, 0)));
        vadEndHour.setText(String.valueOf(prefs.getInt(SaidIt.VAD_END_HOUR_KEY, 6)));
        vadEndMinute.setText(String.valueOf(prefs.getInt(SaidIt.VAD_END_MINUTE_KEY, 0)));
        
        final Runnable applyVadTimeWindow = new Runnable() {
            @Override
            public void run() {
                try {
                    int startHour = Integer.parseInt(vadStartHour.getText().toString());
                    int startMinute = Integer.parseInt(vadStartMinute.getText().toString());
                    int endHour = Integer.parseInt(vadEndHour.getText().toString());
                    int endMinute = Integer.parseInt(vadEndMinute.getText().toString());
                    
                    // Validate ranges
                    startHour = Math.max(0, Math.min(23, startHour));
                    startMinute = Math.max(0, Math.min(59, startMinute));
                    endHour = Math.max(0, Math.min(23, endHour));
                    endMinute = Math.max(0, Math.min(59, endMinute));
                    
                    // Update UI with validated values
                    vadStartHour.setText(String.valueOf(startHour));
                    vadStartMinute.setText(String.valueOf(startMinute));
                    vadEndHour.setText(String.valueOf(endHour));
                    vadEndMinute.setText(String.valueOf(endMinute));
                    
                    // Save to preferences
                    prefs.edit()
                        .putBoolean(SaidIt.VAD_TIME_WINDOW_ENABLED_KEY, vadTimeWindowEnabled.isChecked())
                        .putInt(SaidIt.VAD_START_HOUR_KEY, startHour)
                        .putInt(SaidIt.VAD_START_MINUTE_KEY, startMinute)
                        .putInt(SaidIt.VAD_END_HOUR_KEY, endHour)
                        .putInt(SaidIt.VAD_END_MINUTE_KEY, endMinute)
                        .apply();
                    
                    // Apply to service
                    if (service != null) {
                        service.setVadTimeWindowEnabled(vadTimeWindowEnabled.isChecked());
                        service.setVadTimeWindow(startHour, startMinute, endHour, endMinute);
                    }
                    
                    Toast.makeText(SettingsActivity.this,
                        "VAD schedule: " + String.format("%02d:%02d-%02d:%02d", startHour, startMinute, endHour, endMinute),
                        Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(SettingsActivity.this,
                        "Invalid time format. Please use 0-23 for hours and 0-59 for minutes.",
                        Toast.LENGTH_SHORT).show();
                }
            }
        };
        
        // Set up listeners
        vadTimeWindowEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applyVadTimeWindow.run();
            }
        });
        
        // Apply on focus loss for time inputs
        View.OnFocusChangeListener onBlurApply = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    applyVadTimeWindow.run();
                }
            }
        };
        
        vadStartHour.setOnFocusChangeListener(onBlurApply);
        vadStartMinute.setOnFocusChangeListener(onBlurApply);
        vadEndHour.setOnFocusChangeListener(onBlurApply);
        vadEndMinute.setOnFocusChangeListener(onBlurApply);
    }

    private void initDeviceAudioControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        final CheckBox deviceAudioEnabled = (CheckBox) root.findViewById(R.id.device_audio_enabled);
        if (deviceAudioEnabled != null) {
            deviceAudioEnabled.setChecked(prefs.getBoolean(SaidIt.RECORD_DEVICE_AUDIO_KEY, false));
            deviceAudioEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        // Request MediaProjection permission for device audio capture
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjectionManager != null) {
                            waitingForMediaProjection = true;
                            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
                            startActivityForResult(captureIntent, MEDIA_PROJECTION_REQUEST_CODE);
                            Toast.makeText(SettingsActivity.this,
                                "Please grant screen recording permission for device audio capture",
                                Toast.LENGTH_LONG).show();
                        } else {
                            // Android version too old
                            deviceAudioEnabled.setChecked(false);
                            Toast.makeText(SettingsActivity.this,
                                "Device audio capture requires Android 10 or higher",
                                Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // Disabling device audio
                        prefs.edit().putBoolean(SaidIt.RECORD_DEVICE_AUDIO_KEY, false).apply();
                        if (service != null) {
                            service.setDeviceAudioRecording(false);
                            service.setMediaProjection(null);
                        }
                        Toast.makeText(SettingsActivity.this,
                            "Microphone recording enabled",
                            Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void initDualSourceControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        final CheckBox dualSourceEnabled = (CheckBox) root.findViewById(R.id.dual_source_enabled);
        final Button micMono = (Button) root.findViewById(R.id.mic_channel_mono);
        final Button micStereo = (Button) root.findViewById(R.id.mic_channel_stereo);
        final Button deviceMono = (Button) root.findViewById(R.id.device_channel_mono);
        final Button deviceStereo = (Button) root.findViewById(R.id.device_channel_stereo);
        
        if (dualSourceEnabled != null) {
            dualSourceEnabled.setChecked(prefs.getBoolean(SaidIt.DUAL_SOURCE_RECORDING_KEY, false));
            dualSourceEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        // Dual-source needs MediaProjection for device audio
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjectionManager != null) {
                            waitingForMediaProjection = true;
                            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
                            startActivityForResult(captureIntent, MEDIA_PROJECTION_REQUEST_CODE + 1); // Different code for dual-source
                            Toast.makeText(SettingsActivity.this,
                                "Please grant screen recording permission for device audio capture",
                                Toast.LENGTH_LONG).show();
                        } else {
                            dualSourceEnabled.setChecked(false);
                            Toast.makeText(SettingsActivity.this,
                                "Dual-source recording requires Android 10 or higher",
                                Toast.LENGTH_LONG).show();
                        }
                    } else {
                        prefs.edit().putBoolean(SaidIt.DUAL_SOURCE_RECORDING_KEY, false).apply();
                        if (service != null) {
                            service.setDualSourceRecording(false);
                        }
                        Toast.makeText(SettingsActivity.this,
                            "Single-source recording",
                            Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        // Mic channel mode buttons
        final int micMode = prefs.getInt(SaidIt.MIC_CHANNEL_MODE_KEY, 0);
        if (micMono != null && micStereo != null) {
            root.findViewById(R.id.mic_channel_mono).setBackgroundResource(micMode == 0 ? R.drawable.green_button : R.drawable.gray_button);
            root.findViewById(R.id.mic_channel_stereo).setBackgroundResource(micMode == 1 ? R.drawable.green_button : R.drawable.gray_button);
            
            View.OnClickListener micChannelListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int mode = (v.getId() == R.id.mic_channel_mono) ? 0 : 1;
                    prefs.edit().putInt(SaidIt.MIC_CHANNEL_MODE_KEY, mode).apply();
                    root.findViewById(R.id.mic_channel_mono).setBackgroundResource(mode == 0 ? R.drawable.green_button : R.drawable.gray_button);
                    root.findViewById(R.id.mic_channel_stereo).setBackgroundResource(mode == 1 ? R.drawable.green_button : R.drawable.gray_button);
                    if (service != null) {
                        service.setMicChannelMode(mode);
                    }
                    Toast.makeText(SettingsActivity.this,
                        "Mic: " + (mode == 0 ? "Mono" : "Stereo"), Toast.LENGTH_SHORT).show();
                }
            };
            micMono.setOnClickListener(micChannelListener);
            micStereo.setOnClickListener(micChannelListener);
        }
        
        // Device channel mode buttons
        final int deviceMode = prefs.getInt(SaidIt.DEVICE_CHANNEL_MODE_KEY, 0);
        if (deviceMono != null && deviceStereo != null) {
            root.findViewById(R.id.device_channel_mono).setBackgroundResource(deviceMode == 0 ? R.drawable.green_button : R.drawable.gray_button);
            root.findViewById(R.id.device_channel_stereo).setBackgroundResource(deviceMode == 1 ? R.drawable.green_button : R.drawable.gray_button);
            
            View.OnClickListener deviceChannelListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int mode = (v.getId() == R.id.device_channel_mono) ? 0 : 1;
                    prefs.edit().putInt(SaidIt.DEVICE_CHANNEL_MODE_KEY, mode).apply();
                    root.findViewById(R.id.device_channel_mono).setBackgroundResource(mode == 0 ? R.drawable.green_button : R.drawable.gray_button);
                    root.findViewById(R.id.device_channel_stereo).setBackgroundResource(mode == 1 ? R.drawable.green_button : R.drawable.gray_button);
                    if (service != null) {
                        service.setDeviceChannelMode(mode);
                    }
                    Toast.makeText(SettingsActivity.this,
                        "Device: " + (mode == 0 ? "Mono" : "Stereo"), Toast.LENGTH_SHORT).show();
                }
            };
            deviceMono.setOnClickListener(deviceChannelListener);
            deviceStereo.setOnClickListener(deviceChannelListener);
        }
    }
    
    private void initAutoSaveControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        final CheckBox autoSaveEnabled = (CheckBox) root.findViewById(R.id.auto_save_enabled);
        autoSaveEnabled.setChecked(prefs.getBoolean(SaidIt.AUTO_SAVE_ENABLED_KEY, false));
        
        final EditText durationInput = (EditText) root.findViewById(R.id.auto_save_duration_input);
        int duration = prefs.getInt(SaidIt.AUTO_SAVE_DURATION_KEY, 600);
        durationInput.setText(String.valueOf(duration));

        final EditText autoDeleteInput = (EditText) root.findViewById(R.id.auto_save_autodelete_input);
        int autoDeleteDays = prefs.getInt(SaidIt.AUTO_SAVE_AUTO_DELETE_DAYS_KEY, 7);
        autoDeleteInput.setText(String.valueOf(autoDeleteDays));
        
        autoSaveEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(SaidIt.AUTO_SAVE_ENABLED_KEY, isChecked).apply();
                if (isChecked) {
                    try {
                        int duration = Integer.parseInt(durationInput.getText().toString());
                        int deleteDays = Integer.parseInt(autoDeleteInput.getText().toString());
                        if (duration > 0) {
                            prefs.edit().putInt(SaidIt.AUTO_SAVE_DURATION_KEY, duration).apply();
                            prefs.edit().putInt(SaidIt.AUTO_SAVE_AUTO_DELETE_DAYS_KEY, Math.max(1, deleteDays)).apply();
                            service.scheduleAutoSave();
                            service.scheduleAutoSaveCleanup();
                            Toast.makeText(SettingsActivity.this, 
                                "Auto-save enabled (every " + duration + "s, auto-delete after " + Math.max(1, deleteDays) + "d)", 
                                Toast.LENGTH_SHORT).show();
                        } else {
                            autoSaveEnabled.setChecked(false);
                            Toast.makeText(SettingsActivity.this, 
                                "Invalid duration", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        autoSaveEnabled.setChecked(false);
                        Toast.makeText(SettingsActivity.this, 
                            "Please enter a valid duration", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    service.cancelAutoSave();
                    service.cancelAutoSaveCleanup();
                    Toast.makeText(SettingsActivity.this, 
                        "Auto-save disabled", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        durationInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus && autoSaveEnabled.isChecked()) {
                    try {
                        int duration = Integer.parseInt(durationInput.getText().toString());
                        if (duration > 0) {
                            prefs.edit().putInt(SaidIt.AUTO_SAVE_DURATION_KEY, duration).apply();
                            service.cancelAutoSave();
                            service.scheduleAutoSave();
                            Toast.makeText(SettingsActivity.this, 
                                "Auto-save interval updated", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(SettingsActivity.this, 
                            "Invalid duration", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        autoDeleteInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus && autoSaveEnabled.isChecked()) {
                    try {
                        int days = Integer.parseInt(autoDeleteInput.getText().toString());
                        days = Math.max(1, days);
                        prefs.edit().putInt(SaidIt.AUTO_SAVE_AUTO_DELETE_DAYS_KEY, days).apply();
                        service.cancelAutoSaveCleanup();
                        service.scheduleAutoSaveCleanup();
                        Toast.makeText(SettingsActivity.this,
                            "Auto-save auto-delete set to " + days + " days", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(SettingsActivity.this, 
                            "Invalid days", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private void initBootRecordingControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        final CheckBox startRecordingOnBoot = (CheckBox) root.findViewById(R.id.start_recording_on_boot);
        if (startRecordingOnBoot != null) {
            startRecordingOnBoot.setChecked(prefs.getBoolean(SaidIt.START_RECORDING_ON_BOOT_KEY, false));
            
            startRecordingOnBoot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    prefs.edit().putBoolean(SaidIt.START_RECORDING_ON_BOOT_KEY, isChecked).apply();
                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "Recording will auto-start on device boot" : "Auto-start on boot disabled",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void initExportEffectsControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);

        final CheckBox normalizeEnabled = (CheckBox) root.findViewById(R.id.export_auto_normalize_enabled);
        if (normalizeEnabled != null) {
            normalizeEnabled.setChecked(prefs.getBoolean(SaidIt.EXPORT_AUTO_NORMALIZE_ENABLED_KEY, false));
            normalizeEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    prefs.edit().putBoolean(SaidIt.EXPORT_AUTO_NORMALIZE_ENABLED_KEY, isChecked).apply();
                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "Export normalization enabled" : "Export normalization disabled",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        final CheckBox noiseSuppressionEnabled = (CheckBox) root.findViewById(R.id.export_noise_suppression_enabled);
        if (noiseSuppressionEnabled != null) {
            noiseSuppressionEnabled.setChecked(prefs.getBoolean(SaidIt.EXPORT_NOISE_SUPPRESSION_ENABLED_KEY, false));
            noiseSuppressionEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    prefs.edit().putBoolean(SaidIt.EXPORT_NOISE_SUPPRESSION_ENABLED_KEY, isChecked).apply();
                    Toast.makeText(SettingsActivity.this,
                            isChecked ? "Noise suppression on export enabled" : "Noise suppression on export disabled",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        final SeekBar noiseThresholdSlider = (SeekBar) root.findViewById(R.id.export_noise_threshold_slider);
        final TextView noiseThresholdValue = (TextView) root.findViewById(R.id.export_noise_threshold_value);
        if (noiseThresholdSlider != null && noiseThresholdValue != null) {
            int threshold = prefs.getInt(SaidIt.EXPORT_NOISE_THRESHOLD_KEY, 500);
            noiseThresholdSlider.setProgress(threshold);
            noiseThresholdValue.setText(String.valueOf(threshold));

            noiseThresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    noiseThresholdValue.setText(String.valueOf(progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int threshold = seekBar.getProgress();
                    prefs.edit().putInt(SaidIt.EXPORT_NOISE_THRESHOLD_KEY, threshold).apply();
                    Toast.makeText(SettingsActivity.this,
                        "Noise suppression threshold set to " + threshold,
                        Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void applyActivityDetectionConfig(SharedPreferences prefs,
                                              CheckBox activityEnabled,
                                              SeekBar thresholdSlider,
                                              EditText preBufferInput,
                                              EditText postBufferInput,
                                              EditText autoDeleteInput,
                                              CheckBox highBitrate,
                                              boolean showToast) {
        int threshold = Math.max(50, thresholdSlider.getProgress());
        int preSeconds = clampSeconds(parsePositiveInt(preBufferInput, prefs.getInt(SaidIt.ACTIVITY_PRE_BUFFER_SECONDS_KEY, 300)));
        int postSeconds = clampSeconds(parsePositiveInt(postBufferInput, prefs.getInt(SaidIt.ACTIVITY_POST_BUFFER_SECONDS_KEY, 300)));
        int autoDeleteDays = clampDays(parsePositiveInt(autoDeleteInput, prefs.getInt(SaidIt.ACTIVITY_AUTO_DELETE_DAYS_KEY, 7)));

        // Normalize UI values after clamping
        preBufferInput.setText(String.valueOf(preSeconds));
        postBufferInput.setText(String.valueOf(postSeconds));
        autoDeleteInput.setText(String.valueOf(autoDeleteDays));

        prefs.edit()
            .putFloat(SaidIt.ACTIVITY_DETECTION_THRESHOLD_KEY, threshold)
            .putInt(SaidIt.ACTIVITY_PRE_BUFFER_SECONDS_KEY, preSeconds)
            .putInt(SaidIt.ACTIVITY_POST_BUFFER_SECONDS_KEY, postSeconds)
            .putInt(SaidIt.ACTIVITY_AUTO_DELETE_DAYS_KEY, autoDeleteDays)
            .putBoolean(SaidIt.ACTIVITY_HIGH_BITRATE_KEY, highBitrate.isChecked())
            .putBoolean(SaidIt.ACTIVITY_DETECTION_ENABLED_KEY, activityEnabled.isChecked())
            .apply();

        if (service != null) {
            service.configureActivityDetection(activityEnabled.isChecked(), threshold, preSeconds,
                postSeconds, autoDeleteDays, highBitrate.isChecked());
        }

        if (showToast) {
            Toast.makeText(this, R.string.activity_detection_updated, Toast.LENGTH_SHORT).show();
        }
    }

    private int parsePositiveInt(EditText input, int fallback) {
        try {
            int value = Integer.parseInt(input.getText().toString());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int clampSeconds(int value) {
        return Math.max(1, Math.min(value, 3600));
    }

    private int clampDays(int value) {
        return Math.max(1, Math.min(value, 365));
    }
    
    private void initGradientQualityControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        final CheckBox gradientEnabled = (CheckBox) root.findViewById(R.id.gradient_quality_enabled);
        if (gradientEnabled != null) {
            gradientEnabled.setChecked(prefs.getBoolean(SaidIt.GRADIENT_QUALITY_ENABLED_KEY, false));
            
            // Initialize sample rate buttons
            final int highRate = prefs.getInt(SaidIt.GRADIENT_QUALITY_HIGH_RATE_KEY, 48000);
            final int midRate = prefs.getInt(SaidIt.GRADIENT_QUALITY_MID_RATE_KEY, 16000);
            final int lowRate = prefs.getInt(SaidIt.GRADIENT_QUALITY_LOW_RATE_KEY, 8000);
            
            // High quality buttons
            setupGradientRateButtons(root, highRate, 
                R.id.gradient_high_8khz, R.id.gradient_high_16khz, R.id.gradient_high_48khz,
                SaidIt.GRADIENT_QUALITY_HIGH_RATE_KEY, "High");
            
            // Mid quality buttons
            setupGradientRateButtons(root, midRate,
                R.id.gradient_mid_8khz, R.id.gradient_mid_16khz, R.id.gradient_mid_48khz,
                SaidIt.GRADIENT_QUALITY_MID_RATE_KEY, "Mid");
            
            // Low quality buttons
            setupGradientRateButtons(root, lowRate,
                R.id.gradient_low_8khz, R.id.gradient_low_16khz, R.id.gradient_low_48khz,
                SaidIt.GRADIENT_QUALITY_LOW_RATE_KEY, "Low");
            
            gradientEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    prefs.edit()
                        .putBoolean(SaidIt.GRADIENT_QUALITY_ENABLED_KEY, isChecked)
                        .apply();
                    Toast.makeText(SettingsActivity.this,
                        isChecked ? "Gradient quality enabled (restart listening to apply)" : "Gradient quality disabled",
                        Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    
    private void setupGradientRateButtons(View root, int currentRate,
                                         int id8khz, int id16khz, int id48khz,
                                         final String prefKey, final String tierName) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        Button btn8k = (Button) root.findViewById(id8khz);
        Button btn16k = (Button) root.findViewById(id16khz);
        Button btn48k = (Button) root.findViewById(id48khz);
        
        // Highlight current selection
        btn8k.setBackgroundResource(currentRate == 8000 ? R.drawable.green_button : R.drawable.gray_button);
        btn16k.setBackgroundResource(currentRate == 16000 ? R.drawable.green_button : R.drawable.gray_button);
        btn48k.setBackgroundResource(currentRate == 48000 ? R.drawable.green_button : R.drawable.gray_button);
        
        View.OnClickListener rateListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int rate = 8000;
                if (v.getId() == id16khz) rate = 16000;
                else if (v.getId() == id48khz) rate = 48000;
                
                prefs.edit().putInt(prefKey, rate).apply();
                
                // Update button highlights
                root.findViewById(id8khz).setBackgroundResource(rate == 8000 ? R.drawable.green_button : R.drawable.gray_button);
                root.findViewById(id16khz).setBackgroundResource(rate == 16000 ? R.drawable.green_button : R.drawable.gray_button);
                root.findViewById(id48khz).setBackgroundResource(rate == 48000 ? R.drawable.green_button : R.drawable.gray_button);
                
                Toast.makeText(SettingsActivity.this,
                    tierName + " quality: " + (rate/1000) + " kHz (restart listening to apply)",
                    Toast.LENGTH_SHORT).show();
            }
        };
        
        btn8k.setOnClickListener(rateListener);
        btn16k.setOnClickListener(rateListener);
        btn48k.setOnClickListener(rateListener);
    }
    
    private void initDarkModeControls(View root) {
        SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        final CheckBox darkModeEnabled = (CheckBox) root.findViewById(R.id.dark_mode_enabled);
        if (darkModeEnabled != null) {
            boolean isDarkMode = prefs.getBoolean(SaidIt.DARK_MODE_KEY, false);
            darkModeEnabled.setChecked(isDarkMode);
            
            darkModeEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    prefs.edit().putBoolean(SaidIt.DARK_MODE_KEY, isChecked).apply();
                    Toast.makeText(SettingsActivity.this,
                        "Dark mode " + (isChecked ? "enabled" : "disabled") + ". Please restart the app.",
                        Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    
    /**
     * Initialize accordion/collapsible sections for settings.
     * Each section header becomes clickable and toggles its content visibility.
     */
    private void initAccordionSections(View root) {
        // Define section header IDs and their corresponding content section IDs
        // Note: We'll use existing TextViews with bold styling as headers
        // and wrap groups of controls in LinearLayouts for toggling
        
        // Since the layout doesn't have pre-defined section containers,
        // we'll implement a simple accordion by finding TextViews with textStyle="bold"
        // and textSize="18sp" which are section headers, then managing visibility
        // of subsequent views until the next header.
        
        // For now, let's implement toggling for major sections we can identify by ID
        // This is a minimal implementation that uses existing headers
        
        setupAccordionHeader(root, R.id.header_memory, R.id.section_memory);
        setupAccordionHeader(root, R.id.header_quality, R.id.section_quality);
        setupAccordionHeader(root, R.id.header_storage, R.id.section_storage);
        setupAccordionHeader(root, R.id.header_silence, R.id.section_silence);
        setupAccordionHeader(root, R.id.header_device_audio, R.id.section_device_audio);
        setupAccordionHeader(root, R.id.header_dual_source, R.id.section_dual_source);
        setupAccordionHeader(root, R.id.header_vad, R.id.section_vad);
        setupAccordionHeader(root, R.id.header_debug_memory, R.id.section_debug_memory);
        setupAccordionHeader(root, R.id.header_about, R.id.section_about);
        
        // Setup GitHub button in About section
        android.widget.ImageButton githubButton = (android.widget.ImageButton) root.findViewById(R.id.rate_on_google_play);
        if (githubButton != null) {
            githubButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/mafik/echo")));
                    } catch (android.content.ActivityNotFoundException anfe) {
                        // ignore
                    }
                }
            });
        }
    }
    
    /**
     * Setup a single accordion header to toggle its section visibility.
     * @param root The root view
     * @param headerId ID of the header TextView
     * @param sectionId ID of the section LinearLayout to toggle
     */
    private void setupAccordionHeader(View root, int headerId, int sectionId) {
        final TextView header = (TextView) root.findViewById(headerId);
        final View section = root.findViewById(sectionId);
        
        if (header == null || section == null) {
            // IDs not found - skip this section
            return;
        }
        
        // Set initial state
        final boolean[] isExpanded = {section.getVisibility() == View.VISIBLE};
        updateHeaderIcon(header, isExpanded[0]);
        
        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isExpanded[0] = !isExpanded[0];
                section.setVisibility(isExpanded[0] ? View.VISIBLE : View.GONE);
                updateHeaderIcon(header, isExpanded[0]);
            }
        });
    }
    
    /**
     * Update the header icon to show expanded/collapsed state.
     * @param header The header TextView
     * @param isExpanded Whether the section is expanded
     */
    private void updateHeaderIcon(TextView header, boolean isExpanded) {
        String text = header.getText().toString();
        // Remove existing arrow if present
        if (text.startsWith("▼ ") || text.startsWith("▶ ")) {
            text = text.substring(2);
        }
        // Add appropriate arrow
        header.setText((isExpanded ? "▼ " : "▶ ") + text);
    }

    private void initDebugMemoryControls(View root) {
        Button debugMemoryButton = (Button) root.findViewById(R.id.debug_memory_button);
        if (debugMemoryButton != null) {
            debugMemoryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(SettingsActivity.this, DebugMemoryActivity.class);
                    startActivity(intent);
                }
            });
        }
    }
}
