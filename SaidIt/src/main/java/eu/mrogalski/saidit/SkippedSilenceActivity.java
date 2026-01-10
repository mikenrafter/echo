package eu.mrogalski.saidit;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.text.format.DateUtils;

import java.util.ArrayList;

public class SkippedSilenceActivity extends Activity {

    private SaidItService echo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skipped_silence);
        bindService(new android.content.Intent(this, SaidItService.class), new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                echo = ((SaidItService.BackgroundRecorderBinder) service).getService();
                loadGroups();
            }

            @Override
            public void onServiceDisconnected(android.content.ComponentName name) {
                echo = null;
            }
        }, android.content.Context.BIND_AUTO_CREATE);
    }

    private void loadGroups() {
        if (echo == null) return;
        echo.getSilenceGroups(new SaidItService.SilenceGroupsCallback() {
            @Override
            public void onGroups(java.util.List<SaidItService.SilenceGroup> groups) {
                ListView list = findViewById(R.id.silence_list);
                TextView empty = findViewById(R.id.empty_text);
                if (groups == null || groups.isEmpty()) {
                    empty.setVisibility(View.VISIBLE);
                    list.setVisibility(View.GONE);
                    return;
                }
                empty.setVisibility(View.GONE);
                list.setVisibility(View.VISIBLE);
                ArrayList<String> items = new ArrayList<>();
                for (SaidItService.SilenceGroup g : groups) {
                    String when = DateUtils.formatDateTime(SkippedSilenceActivity.this, g.endTimeMillis,
                            DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY);
                    long secs = g.durationMillis / 1000;
                    items.add("Duration: " + secs + "s, ended: " + when);
                }
                list.setAdapter(new ArrayAdapter<>(SkippedSilenceActivity.this, android.R.layout.simple_list_item_1, items));
            }
        });
    }
}
