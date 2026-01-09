package eu.mrogalski.saidit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import eu.mrogalski.StringFormat;
import eu.mrogalski.android.TimeFormat;
import eu.mrogalski.android.Views;

public class SettingsActivity extends AppCompatActivity {
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
            dialog.show(getSupportFragmentManager(), "Preparing memory");

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    service.setMemorySize(memory);
                    service.getState(new SaidItService.StateCallback() {
                        @Override
                        public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded) {
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
            dialog.show(getSupportFragmentManager(), "Preparing memory");

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    service.setSampleRate(sampleRate);
                    service.getState(new SaidItService.StateCallback() {
                        @Override
                        public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded) {
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

                dialog.show(getSupportFragmentManager(), "Preparing memory");

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        service.setMemorySizeMB(memorySizeMB);
                        service.getState(new SaidItService.StateCallback() {
                            @Override
                            public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded) {
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
            
            new Handler(Looper.getMainLooper()).post(new Runnable() {
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
}
