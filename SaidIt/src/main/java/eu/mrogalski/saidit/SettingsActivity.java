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


        TimeFormat.naturalLanguage(getResources(), service.getBytesToSeconds() * service.getMemorySize(), timeFormatResult);
        ((TextView)findViewById(R.id.history_limit)).setText(timeFormatResult.text);

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        initSampleRateButton(root, R.id.quality_8kHz, 8000, 11025);
        initSampleRateButton(root, R.id.quality_16kHz, 16000, 22050);
        initSampleRateButton(root, R.id.quality_48kHz, 48000, 44100);
        
        // Initialize silence skipping controls
        initSilenceSkippingControls(root);

        // Initialize activity detection controls
        initActivityDetectionControls(root);
        
        // Initialize auto-save controls
        initAutoSaveControls(root);

        // Initialize export effects controls
        initExportEffectsControls(root);

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
        preBufferInput.setText(String.valueOf(prefs.getInt(SaidIt.ACTIVITY_PRE_BUFFER_SECONDS_KEY, 300)));
        postBufferInput.setText(String.valueOf(prefs.getInt(SaidIt.ACTIVITY_POST_BUFFER_SECONDS_KEY, 300)));
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
    
    private void initAutoSaveControls(View root) {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        
        final CheckBox autoSaveEnabled = (CheckBox) root.findViewById(R.id.auto_save_enabled);
        autoSaveEnabled.setChecked(prefs.getBoolean(SaidIt.AUTO_SAVE_ENABLED_KEY, false));
        
        final EditText durationInput = (EditText) root.findViewById(R.id.auto_save_duration_input);
        int duration = prefs.getInt(SaidIt.AUTO_SAVE_DURATION_KEY, 600);
        durationInput.setText(String.valueOf(duration));
        
        autoSaveEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(SaidIt.AUTO_SAVE_ENABLED_KEY, isChecked).apply();
                if (isChecked) {
                    try {
                        int duration = Integer.parseInt(durationInput.getText().toString());
                        if (duration > 0) {
                            prefs.edit().putInt(SaidIt.AUTO_SAVE_DURATION_KEY, duration).apply();
                            service.scheduleAutoSave();
                            Toast.makeText(SettingsActivity.this, 
                                "Auto-save enabled (every " + duration + "s)", 
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
}
