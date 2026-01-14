package eu.mrogalski.saidit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class DebugMemoryActivity extends Activity {
    private static final String TAG = DebugMemoryActivity.class.getSimpleName();
    private TextView memoryInfoView;
    private TextView chunkStatusView;
    private LinearLayout chunkListContainer;
    private CheckBox debugToggle;
    private SaidItService service;
    private boolean bound = false;
    private boolean updatingToggle = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            SaidItService.BackgroundRecorderBinder typedBinder = (SaidItService.BackgroundRecorderBinder) binder;
            service = typedBinder.getService();
            bound = true;
            syncDebugToggle();
            refreshAll();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    private final CompoundButton.OnCheckedChangeListener debugToggleListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (updatingToggle) return;
            if (service == null) {
                setToggleChecked(false);
                if (chunkStatusView != null) {
                    chunkStatusView.setText("Service not connected");
                }
                return;
            }
            service.setDebugMemoryEnabled(isChecked);
            refreshChunks();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);
        scrollView.addView(mainLayout);

        TextView titleView = new TextView(this);
        titleView.setText("Memory Debug Information");
        titleView.setTextSize(20);
        titleView.setTextColor(getResources().getColor(android.R.color.white));
        titleView.setPadding(0, 0, 0, 20);
        mainLayout.addView(titleView);

        debugToggle = new CheckBox(this);
        debugToggle.setText("Enable debug memory tracking");
        debugToggle.setTextColor(getResources().getColor(android.R.color.white));
        debugToggle.setPadding(0, 0, 0, 10);
        debugToggle.setOnCheckedChangeListener(debugToggleListener);
        mainLayout.addView(debugToggle);

        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh");
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshAll();
            }
        });
        mainLayout.addView(refreshButton);

        memoryInfoView = new TextView(this);
        memoryInfoView.setTextColor(getResources().getColor(android.R.color.white));
        memoryInfoView.setTextSize(14);
        memoryInfoView.setPadding(0, 20, 0, 0);
        mainLayout.addView(memoryInfoView);

        TextView chunkHeader = new TextView(this);
        chunkHeader.setText("In-memory segments");
        chunkHeader.setTextSize(16);
        chunkHeader.setTextColor(getResources().getColor(android.R.color.white));
        chunkHeader.setPadding(0, 20, 0, 10);
        mainLayout.addView(chunkHeader);

        chunkStatusView = new TextView(this);
        chunkStatusView.setTextColor(getResources().getColor(android.R.color.white));
        chunkStatusView.setTextSize(14);
        mainLayout.addView(chunkStatusView);

        chunkListContainer = new LinearLayout(this);
        chunkListContainer.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(chunkListContainer);

        Button backButton = new Button(this);
        backButton.setText("Back");
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        backButton.setPadding(0, 20, 0, 0);
        mainLayout.addView(backButton);

        setContentView(scrollView);
        updateMemoryInfo();
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
        if (bound) {
            unbindService(connection);
            bound = false;
            service = null;
        }
    }

    private void refreshAll() {
        updateMemoryInfo();
        refreshChunks();
    }

    private void updateMemoryInfo() {
        if (memoryInfoView == null) return;

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);

        StringBuilder info = new StringBuilder();
        info.append("=== HEAP MEMORY ===\n");
        info.append(String.format("Max Memory: %d MB\n", maxMemory));
        info.append(String.format("Total Memory: %d MB\n", totalMemory));
        info.append(String.format("Used Memory: %d MB\n", usedMemory));
        info.append(String.format("Free Memory: %d MB\n", freeMemory));
        info.append("\n=== NATIVE MEMORY ===\n");
        info.append(String.format("Native Heap: %d KB\n", memInfo.nativePss));
        info.append(String.format("Dalvik Heap: %d KB\n", memInfo.dalvikPss));
        info.append(String.format("Other Dev: %d KB\n", memInfo.otherPss));

        memoryInfoView.setText(info.toString());
        Log.d(TAG, "Memory info:\n" + info.toString());
    }

    private void refreshChunks() {
        if (chunkStatusView == null || chunkListContainer == null) return;
        if (service == null) {
            chunkStatusView.setText("Service not connected");
            chunkListContainer.removeAllViews();
            return;
        }
        if (!service.isDebugMemoryEnabled()) {
            chunkStatusView.setText("Debug memory disabled. Enable it to capture chunks.");
            chunkListContainer.removeAllViews();
            return;
        }

        chunkStatusView.setText("Loading memory chunks...");
        service.getMemoryDebugChunks(new SaidItService.MemoryDebugCallback() {
            @Override
            public void onMemoryDebug(final java.util.List<SaidItService.MemoryDebugChunk> chunks) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        renderChunks(chunks);
                    }
                });
            }
        });
    }

    private void renderChunks(java.util.List<SaidItService.MemoryDebugChunk> chunks) {
        chunkListContainer.removeAllViews();
        if (chunks == null || chunks.isEmpty()) {
            chunkStatusView.setText("No chunks captured yet.");
            return;
        }

        chunkStatusView.setText("Chunks captured: " + chunks.size());

        for (int i = chunks.size() - 1; i >= 0; i--) {
            SaidItService.MemoryDebugChunk chunk = chunks.get(i);
            TextView chunkView = new TextView(this);
            chunkView.setTextColor(getResources().getColor(android.R.color.white));
            chunkView.setTextSize(14);
            chunkView.setPadding(0, 6, 0, 6);

            String timeRange = formatTimeRange(chunk.startTimeMillis, chunk.endTimeMillis);
            String label = chunk.isSilent ? "silent" : "audio";
            chunkView.setText(String.format("#%d  %s  [%s]", chunk.chunkIndex, timeRange, label));

            chunkListContainer.addView(chunkView);
        }
    }

    private void syncDebugToggle() {
        if (debugToggle == null || service == null) return;
        setToggleChecked(service.isDebugMemoryEnabled());
    }

    private void setToggleChecked(boolean checked) {
        updatingToggle = true;
        debugToggle.setChecked(checked);
        updatingToggle = false;
    }

    private String formatTimeRange(long startMillis, long endMillis) {
        String start = DateFormat.format("HH:mm:ss", startMillis).toString();
        String end = DateFormat.format("HH:mm:ss", endMillis).toString();
        return start + " - " + end;
    }
}
