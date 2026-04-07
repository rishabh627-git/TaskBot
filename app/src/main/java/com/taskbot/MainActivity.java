package com.taskbot;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private View           vDot;
    private TextView       tvConnState;
    private ConnectFragment connectFragment;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler         main = new Handler(Looper.getMainLooper());

    // Background ping every 5 seconds to keep header dot accurate
    private static final long PING_INTERVAL = 5000;
    private final Handler pingHandler = new Handler(Looper.getMainLooper());
    private final Runnable pingRunnable = new Runnable() {
        @Override public void run() {
            exec.execute(() -> {
                EspApi.Result r = EspApi.ping();
                main.post(() -> setConnected(r.ok));
            });
            pingHandler.postDelayed(this, PING_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vDot        = findViewById(R.id.vDot);
        tvConnState = findViewById(R.id.tvConnState);

        // Build fragments
        TasksFragment   tasksFragment  = new TasksFragment();
        connectFragment = new ConnectFragment();
        StatusFragment  statusFragment = new StatusFragment();

        // Connect fragment notifies us when connection state changes
        connectFragment.setConnectionCallback(connected -> setConnected(connected));

        // ViewPager2
        ViewPager2 vp = findViewById(R.id.viewPager);
        vp.setAdapter(new FragmentStateAdapter(this) {
            private final Fragment[] frags = {
                    tasksFragment, connectFragment, statusFragment
            };
            @NonNull @Override
            public Fragment createFragment(int pos) { return frags[pos]; }
            @Override public int getItemCount() { return 3; }
        });

        // Tabs
        TabLayout tabs = findViewById(R.id.tabLayout);
        new TabLayoutMediator(tabs, vp, (tab, pos) -> {
            switch (pos) {
                case 0: tab.setText("Tasks");   break;
                case 1: tab.setText("Connect"); break;
                case 2: tab.setText("Status");  break;
            }
        }).attach();

        // Kick off background ping
        pingHandler.postDelayed(pingRunnable, 1000);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        pingHandler.removeCallbacks(pingRunnable);
        exec.shutdown();
    }

    private void setConnected(boolean connected) {
        TasksFragment.setEspReachable(connected);
        if (connectFragment != null) connectFragment.setConnected(connected);

        vDot.setBackground(ContextCompat.getDrawable(this,
                connected ? R.drawable.dot_connected : R.drawable.dot_disconnected));
        tvConnState.setText(connected ? "Connected" : "Not connected");
        tvConnState.setTextColor(ContextCompat.getColor(this,
                connected ? R.color.green : R.color.text_secondary));
    }
}
