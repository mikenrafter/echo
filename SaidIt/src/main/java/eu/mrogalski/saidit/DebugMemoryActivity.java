package eu.mrogalski.saidit;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DebugMemoryActivity extends Activity {
    private static final String TAG = DebugMemoryActivity.class.getSimpleName();
    private TextView memoryInfoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);
        
        // Add title
        TextView titleView = new TextView(this);
        titleView.setText("Memory Debug Information");
        titleView.setTextSize(20);
        titleView.setTextColor(getResources().getColor(android.R.color.white));
        titleView.setPadding(0, 0, 0, 20);
        mainLayout.addView(titleView);
        
        // Refresh button
        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh");
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateMemoryInfo();
            }
        });
        mainLayout.addView(refreshButton);
        
        // Memory info display
        memoryInfoView = new TextView(this);
        memoryInfoView.setTextColor(getResources().getColor(android.R.color.white));
        memoryInfoView.setTextSize(14);
        memoryInfoView.setPadding(0, 20, 0, 0);
        mainLayout.addView(memoryInfoView);
        
        // Back button
        Button backButton = new Button(this);
        backButton.setText("Back");
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        mainLayout.addView(backButton);
        
        setContentView(mainLayout);
        updateMemoryInfo();
    }

    private void updateMemoryInfo() {
        if (memoryInfoView == null) return;
        
        // Get memory statistics
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        
        // Get native memory statistics
        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);
        
        // Format the info
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
        // GC info not available directly; consider using external tools if needed

        memoryInfoView.setText(info.toString());
        Log.d(TAG, "Memory info:\n" + info.toString());
    }
}
