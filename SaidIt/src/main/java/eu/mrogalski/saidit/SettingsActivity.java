package eu.mrogalski.saidit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import eu.mrogalski.StringFormat;
import eu.mrogalski.android.TimeFormat;
import eu.mrogalski.saidit.util.FilenamePatternGenerator;

public class SettingsActivity extends AppCompatActivity {

    private SaidItService service;
    private TextView historyLimitTextView;
    private MaterialButtonToggleGroup memoryToggleGroup;
    private MaterialButtonToggleGroup qualityToggleGroup;
    private Button memoryMediumButton, memoryHighButton, memoryOtherButton;
    private Button quality8kHzButton, quality16kHzButton, quality48kHzButton;
    private SwitchMaterial autoSaveSwitch;
    private SwitchMaterial noiseSuppressorSwitch;
    private SwitchMaterial automaticGainControlSwitch;
    private SwitchMaterial autoCleanupSwitch;
    private Slider autoSaveDurationSlider;
    private TextView autoSaveDurationLabel;
    private Slider customMemorySlider;
    private TextView customMemoryLabel;
    private TextView customMemorySectionLabel;
    private TextInputEditText filenamePatternInput;
    private TextView filenamePreview;
    private Slider maxFilesSlider;
    private TextView maxFilesLabel;
    private Slider maxAgeSlider;
    private TextView maxAgeLabel;
    private android.view.View autoCleanupControls;
    private com.google.android.material.button.MaterialButton saveButton;

    private SharedPreferences sharedPreferences;
    
    // Track all pending changes
    private boolean hasPendingChanges = false;
    private long pendingMemorySize = 0;
    private boolean pendingMemorySizeSet = false;
    private int pendingSampleRate = 0;
    private boolean pendingSampleRateSet = false;
    private Boolean pendingAutoSaveEnabled = null;
    private Integer pendingAutoSaveDuration = null;
    private Boolean pendingNoiseSuppressor = null;
    private Boolean pendingAGC = null;
    private Boolean pendingAutoCleanupEnabled = null;
    private Integer pendingMaxFiles = null;
    private Integer pendingMaxAge = null;
    private String pendingFilenamePattern = null;

    private boolean isBound = false;

    private final MaterialButtonToggleGroup.OnButtonCheckedListener memoryToggleListener = (group, checkedId, isChecked) -> {
        if (isChecked && isBound) {
            if (checkedId == R.id.memory_other) {
                // Show custom memory section
                customMemorySectionLabel.setVisibility(android.view.View.VISIBLE);
                customMemoryLabel.setVisibility(android.view.View.VISIBLE);
                customMemorySlider.setVisibility(android.view.View.VISIBLE);
            } else {
                // Hide custom memory section and track the change
                customMemorySectionLabel.setVisibility(android.view.View.GONE);
                customMemoryLabel.setVisibility(android.view.View.GONE);
                customMemorySlider.setVisibility(android.view.View.GONE);
                
                final long maxMemory = Runtime.getRuntime().maxMemory();
                long memorySize = maxMemory / 2; // Default to medium
                if (checkedId == R.id.memory_high) {
                    memorySize = (long) (maxMemory * 0.90);
                }
                
                // Track the pending change
                pendingMemorySize = memorySize;
                pendingMemorySizeSet = true;
                markPendingChange();
            }
        }
    };

    private final MaterialButtonToggleGroup.OnButtonCheckedListener qualityToggleListener = (group, checkedId, isChecked) -> {
        if (isChecked && isBound) {
            int sampleRate = 8000; // Default to 8kHz
            if (checkedId == R.id.quality_16kHz) {
                sampleRate = 16000;
            } else if (checkedId == R.id.quality_48kHz) {
                sampleRate = 48000;
            }
            
            // Track the pending change
            pendingSampleRate = sampleRate;
            pendingSampleRateSet = true;
            markPendingChange();
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            SaidItService.BackgroundRecorderBinder typedBinder = (SaidItService.BackgroundRecorderBinder) binder;
            service = typedBinder.getService();
            isBound = true;
            syncUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize UI components
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        saveButton = toolbar.findViewById(R.id.save_button);
        historyLimitTextView = findViewById(R.id.history_limit);
        memoryToggleGroup = findViewById(R.id.memory_toggle_group);
        qualityToggleGroup = findViewById(R.id.quality_toggle_group);
        memoryMediumButton = findViewById(R.id.memory_medium);
        memoryHighButton = findViewById(R.id.memory_high);
        memoryOtherButton = findViewById(R.id.memory_other);
        quality8kHzButton = findViewById(R.id.quality_8kHz);
        quality16kHzButton = findViewById(R.id.quality_16kHz);
        quality48kHzButton = findViewById(R.id.quality_48kHz);
        autoSaveSwitch = findViewById(R.id.auto_save_switch);
        noiseSuppressorSwitch = findViewById(R.id.noise_suppressor_switch);
        automaticGainControlSwitch = findViewById(R.id.automatic_gain_control_switch);
        autoSaveDurationSlider = findViewById(R.id.auto_save_duration_slider);
        autoSaveDurationLabel = findViewById(R.id.auto_save_duration_label);
        customMemorySectionLabel = findViewById(R.id.custom_memory_section);
        customMemorySlider = findViewById(R.id.custom_memory_slider);
        customMemoryLabel = findViewById(R.id.custom_memory_label);
        filenamePatternInput = findViewById(R.id.filename_pattern_input);
        filenamePreview = findViewById(R.id.filename_preview);
        autoCleanupSwitch = findViewById(R.id.auto_cleanup_switch);
        autoCleanupControls = findViewById(R.id.auto_cleanup_controls);
        maxFilesSlider = findViewById(R.id.max_files_slider);
        maxFilesLabel = findViewById(R.id.max_files_label);
        maxAgeSlider = findViewById(R.id.max_age_slider);
        maxAgeLabel = findViewById(R.id.max_age_label);
        Button howToButton = findViewById(R.id.how_to_button);
        Button showTourButton = findViewById(R.id.show_tour_button);

        sharedPreferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);


        // Setup Toolbar
        toolbar.setNavigationOnClickListener(v -> finish());
        
        // Setup Save Button
        saveButton.setOnClickListener(v -> applyPendingChanges());

        // Setup How-To Button
        howToButton.setOnClickListener(v -> startActivity(new Intent(this, HowToActivity.class)));
        showTourButton.setOnClickListener(v -> {
            sharedPreferences.edit().putBoolean("show_tour_on_next_launch", true).apply();
            finish();
        });

        // Setup Listeners
        memoryToggleGroup.addOnButtonCheckedListener(memoryToggleListener);
        qualityToggleGroup.addOnButtonCheckedListener(qualityToggleListener);

        noiseSuppressorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            pendingNoiseSuppressor = isChecked;
            markPendingChange();
        });

        automaticGainControlSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            pendingAGC = isChecked;
            markPendingChange();
        });

        autoSaveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            pendingAutoSaveEnabled = isChecked;
            autoSaveDurationSlider.setEnabled(isChecked);
            autoSaveDurationLabel.setEnabled(isChecked);
            markPendingChange();
        });

        autoSaveDurationSlider.addOnChangeListener((slider, value, fromUser) -> {
            int minutes = (int) value;
            updateAutoSaveLabel(minutes);
            if (fromUser) {
                pendingAutoSaveDuration = minutes * 60;
                markPendingChange();
            }
        });
        
        // Custom memory size listener
        customMemorySlider.addOnChangeListener((slider, value, fromUser) -> {
            int megabytes = (int) value;
            customMemoryLabel.setText(megabytes + " MB");
            if (fromUser && isBound) {
                long bytes = megabytes * 1024L * 1024L;
                pendingMemorySize = bytes;
                pendingMemorySizeSet = true;
                markPendingChange();
            }
        });
        
        // Filename pattern listener
        filenamePatternInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String pattern = s.toString();
                pendingFilenamePattern = pattern;
                updateFilenamePreview(pattern);
                markPendingChange();
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        
        // Auto-cleanup listener
        autoCleanupSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("auto_cleanup_enabled", isChecked).apply();
            autoCleanupControls.setEnabled(isChecked);
            maxFilesSlider.setEnabled(isChecked);
            maxFilesLabel.setEnabled(isChecked);
            maxAgeSlider.setEnabled(isChecked);
            maxAgeLabel.setEnabled(isChecked);
            // Disable all children of the controls container
            setViewGroupEnabled(autoCleanupControls, isChecked);
        });
        
        // Max files listener
        maxFilesSlider.addOnChangeListener((slider, value, fromUser) -> {
            int files = (int) value;
            maxFilesLabel.setText(getString(R.string.files_format, files));
            if (fromUser) {
                pendingMaxFiles = files;
                markPendingChange();
            }
        });
        
        // Max age listener
        maxAgeSlider.addOnChangeListener((slider, value, fromUser) -> {
            int days = (int) value;
            maxAgeLabel.setText(getString(R.string.days_format, days));
            if (fromUser) {
                pendingMaxAge = days;
                markPendingChange();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, SaidItService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    private void syncUI() {
        if (!isBound || service == null) return;

        // Remove listeners to prevent programmatic changes from triggering them
        memoryToggleGroup.removeOnButtonCheckedListener(memoryToggleListener);
        qualityToggleGroup.removeOnButtonCheckedListener(qualityToggleListener);

        // Set memory button text
        final long maxMemory = Runtime.getRuntime().maxMemory();
        memoryMediumButton.setText(StringFormat.shortFileSize(maxMemory / 2));
        memoryHighButton.setText(StringFormat.shortFileSize((long) (maxMemory * 0.90)));
        
        // Always set slider max to high preset + 64MB
        long highPresetBytes = (long) (maxMemory * 0.90);
        int highPresetMB = (int) (highPresetBytes / 1024 / 1024);
        int maxSliderMB = highPresetMB + 64;
        
        // Check if OOM walkback set a custom limit and use the higher value
        long customMaxMemory = sharedPreferences.getLong(SaidIt.MAX_MEMORY_SIZE_KEY, 0);
        if (customMaxMemory > 0) {
            int customMaxMB = (int) (customMaxMemory / 1024 / 1024);
            maxSliderMB = Math.max(maxSliderMB, customMaxMB);
        }
        
        // Round to valid step size: valueTo must be valueFrom + (n * stepSize)
        // Slider has valueFrom=32, stepSize=16
        int valueFrom = 32;
        int stepSize = 16;
        int stepsNeeded = (maxSliderMB - valueFrom + stepSize - 1) / stepSize; // Round up
        int validValueTo = valueFrom + (stepsNeeded * stepSize);
        
        customMemorySlider.setValueTo(validValueTo);
        Log.d("SettingsActivity", "Set slider max to " + validValueTo + " MB (high preset + 64)");

        // Set memory button state
        long currentMemory = service.getMemorySize();
        if (currentMemory <= maxMemory / 2) {
            memoryToggleGroup.check(R.id.memory_medium);
            customMemorySectionLabel.setVisibility(android.view.View.GONE);
            customMemoryLabel.setVisibility(android.view.View.GONE);
            customMemorySlider.setVisibility(android.view.View.GONE);
        } else if (currentMemory <= (long) (maxMemory * 0.92)) {
            memoryToggleGroup.check(R.id.memory_high);
            customMemorySectionLabel.setVisibility(android.view.View.GONE);
            customMemoryLabel.setVisibility(android.view.View.GONE);
            customMemorySlider.setVisibility(android.view.View.GONE);
        } else {
            // Custom size
            memoryToggleGroup.check(R.id.memory_other);
            customMemorySectionLabel.setVisibility(android.view.View.VISIBLE);
            customMemoryLabel.setVisibility(android.view.View.VISIBLE);
            customMemorySlider.setVisibility(android.view.View.VISIBLE);
            int megabytes = (int) (currentMemory / 1024 / 1024);
            customMemorySlider.setValue(megabytes);
            customMemoryLabel.setText(megabytes + " MB");
        }

        // Set quality button state
        int currentRate = service.getSamplingRate();
        if (currentRate >= 48000) {
            qualityToggleGroup.check(R.id.quality_48kHz);
        } else if (currentRate >= 16000) {
            qualityToggleGroup.check(R.id.quality_16kHz);
        } else {
            qualityToggleGroup.check(R.id.quality_8kHz);
        }

        // Load and apply auto-save settings
        boolean autoSaveEnabled = sharedPreferences.getBoolean("auto_save_enabled", false);
        autoSaveSwitch.setChecked(autoSaveEnabled);
        autoSaveDurationSlider.setEnabled(autoSaveEnabled);
        autoSaveDurationLabel.setEnabled(autoSaveEnabled);

        int autoSaveDurationSeconds = sharedPreferences.getInt("auto_save_duration", 600); // Default to 10 minutes
        int autoSaveDurationMinutes = autoSaveDurationSeconds / 60;
        autoSaveDurationSlider.setValue(autoSaveDurationMinutes);
        updateAutoSaveLabel(autoSaveDurationMinutes);

        updateHistoryLimit();

        // Re-add listeners
        memoryToggleGroup.addOnButtonCheckedListener(memoryToggleListener);
        qualityToggleGroup.addOnButtonCheckedListener(qualityToggleListener);
        
        boolean noiseSuppressorEnabled = sharedPreferences.getBoolean("noise_suppressor_enabled", false);
        noiseSuppressorSwitch.setChecked(noiseSuppressorEnabled);

        boolean automaticGainControlEnabled = sharedPreferences.getBoolean("automatic_gain_control_enabled", false);
        automaticGainControlSwitch.setChecked(automaticGainControlEnabled);
        
        // Load and apply auto-cleanup settings
        boolean autoCleanupEnabled = sharedPreferences.getBoolean("auto_cleanup_enabled", false);
        autoCleanupSwitch.setChecked(autoCleanupEnabled);
        setViewGroupEnabled(autoCleanupControls, autoCleanupEnabled);
        
        int maxFiles = sharedPreferences.getInt("auto_cleanup_max_files", 50);
        maxFilesSlider.setValue(maxFiles);
        maxFilesLabel.setText(getString(R.string.files_format, maxFiles));
        
        int maxAge = sharedPreferences.getInt("auto_cleanup_max_age_days", 30);
        maxAgeSlider.setValue(maxAge);
        maxAgeLabel.setText(getString(R.string.days_format, maxAge));
        
        // Load filename pattern
        String filenamePattern = sharedPreferences.getString("filename_pattern", "{date}_{time}");
        filenamePatternInput.setText(filenamePattern);
        updateFilenamePreview(filenamePattern);
        
        // Reset pending changes
        resetPendingChanges();
    }

    private void updateHistoryLimit() {
        if (isBound && service != null) {
            TimeFormat.Result timeFormatResult = new TimeFormat.Result();
            float historyInSeconds = service.getBytesToSeconds() * service.getMemorySize();
            TimeFormat.naturalLanguage(getResources(), historyInSeconds, timeFormatResult);
            historyLimitTextView.setText(timeFormatResult.text);
        }
    }

    private void updateAutoSaveLabel(int totalMinutes) {
        if (totalMinutes < 60) {
            autoSaveDurationLabel.setText(getResources().getQuantityString(R.plurals.minute_plural, totalMinutes, totalMinutes));
        } else {
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            String hourText = getResources().getQuantityString(R.plurals.hour_plural, hours, hours);
            if (minutes == 0) {
                autoSaveDurationLabel.setText(hourText);
            } else {
                String minuteText = getResources().getQuantityString(R.plurals.minute_plural, minutes, minutes);
                autoSaveDurationLabel.setText(getString(R.string.time_join, hourText, minuteText));
            }
        }
    }
    
    private void updateFilenamePreview(String pattern) {
        String preview = "Preview: " + FilenamePatternGenerator.generatePreview(pattern);
        filenamePreview.setText(preview);
    }
    
    private void showClearBufferDialog(Runnable onConfirm, Runnable onCancel) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.clear_buffer_warning_title)
            .setMessage(R.string.clear_buffer_warning)
            .setPositiveButton(R.string.continue_action, (dialog, which) -> {
                if (onConfirm != null) onConfirm.run();
            })
            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                if (onCancel != null) onCancel.run();
            })
            .setCancelable(false)
            .show();
    }
    
    private void updateSaveButtonState() {
        saveButton.setEnabled(hasPendingChanges);
    }
    
    private void markPendingChange() {
        hasPendingChanges = true;
        updateSaveButtonState();
    }
    
    private void applyPendingChanges() {
        if (!hasPendingChanges || !isBound || service == null) return;
        
        // Check if memory is changing and service is listening
        boolean memoryChanging = pendingMemorySizeSet;
        boolean serviceListening = (service.getState() != SaidItService.ServiceState.READY);
        
        if (memoryChanging && serviceListening) {
            // Show confirmation dialog only when memory changes and service is listening
            showClearBufferDialog(this::performApplyPendingChanges, () -> {
                // Revert to previous selection on cancel
                syncUI();
                resetPendingChanges();
            });
        } else {
            // Apply immediately if no buffer clearing needed
            performApplyPendingChanges();
        }
    }
    
    private void performApplyPendingChanges() {
        if (!isBound || service == null) return;
        
        // Apply memory changes
        if (pendingMemorySizeSet) {
            service.setMemorySize(pendingMemorySize);
            updateHistoryLimit();
        }
        
        // Apply sample rate changes
        if (pendingSampleRateSet) {
            service.setSampleRate(pendingSampleRate);
        }
        
        // Apply auto-save changes
        if (pendingAutoSaveEnabled != null) {
            sharedPreferences.edit().putBoolean("auto_save_enabled", pendingAutoSaveEnabled).apply();
            if (pendingAutoSaveEnabled) {
                service.scheduleAutoSave();
            } else {
                service.cancelAutoSave();
            }
        }
        
        if (pendingAutoSaveDuration != null) {
            sharedPreferences.edit().putInt("auto_save_duration", pendingAutoSaveDuration).apply();
            if (isBound && service != null && sharedPreferences.getBoolean("auto_save_enabled", false)) {
                service.scheduleAutoSave();
            }
        }
        
        // Apply noise suppressor
        if (pendingNoiseSuppressor != null) {
            sharedPreferences.edit().putBoolean("noise_suppressor_enabled", pendingNoiseSuppressor).apply();
            service.setSampleRate(service.getSamplingRate());
        }
        
        // Apply AGC
        if (pendingAGC != null) {
            sharedPreferences.edit().putBoolean("automatic_gain_control_enabled", pendingAGC).apply();
            service.setSampleRate(service.getSamplingRate());
        }
        
        // Apply filename pattern
        if (pendingFilenamePattern != null) {
            sharedPreferences.edit().putString("filename_pattern", pendingFilenamePattern).apply();
        }
        
        // Apply auto-cleanup settings
        if (pendingAutoCleanupEnabled != null) {
            sharedPreferences.edit().putBoolean("auto_cleanup_enabled", pendingAutoCleanupEnabled).apply();
        }
        
        if (pendingMaxFiles != null) {
            sharedPreferences.edit().putInt("auto_cleanup_max_files", pendingMaxFiles).apply();
        }
        
        if (pendingMaxAge != null) {
            sharedPreferences.edit().putInt("auto_cleanup_max_age_days", pendingMaxAge).apply();
        }
        
        resetPendingChanges();
    }
    
    private void resetPendingChanges() {
        hasPendingChanges = false;
        pendingMemorySizeSet = false;
        pendingSampleRateSet = false;
        pendingAutoSaveEnabled = null;
        pendingAutoSaveDuration = null;
        pendingNoiseSuppressor = null;
        pendingAGC = null;
        pendingAutoCleanupEnabled = null;
        pendingMaxFiles = null;
        pendingMaxAge = null;
        pendingFilenamePattern = null;
        updateSaveButtonState();
    }
    
    private void setViewGroupEnabled(android.view.View view, boolean enabled) {
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View child = group.getChildAt(i);
                child.setEnabled(enabled);
                setViewGroupEnabled(child, enabled);
            }
        } else {
            view.setEnabled(enabled);
        }
    }
}
