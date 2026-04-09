package com.taskbot;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatusFragment extends Fragment {

    private StatusAdapter adapter;
    private TextView      tvLastSync, tvStatusEmpty;
    private RecyclerView  rvStatus;

    // Keep a copy of the last fetched list so we can snapshot it on clear
    private final List<TaskItem> lastKnownItems = new ArrayList<>();

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler         main = new Handler(Looper.getMainLooper());

    private static final long POLL_MS = 2000;
    private final Handler  pollHandler  = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            fetchStatus();
            pollHandler.postDelayed(this, POLL_MS);
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        View root = inf.inflate(R.layout.fragment_status, container, false);
        tvLastSync    = root.findViewById(R.id.tvLastSync);
        tvStatusEmpty = root.findViewById(R.id.tvStatusEmpty);
        rvStatus      = root.findViewById(R.id.rvStatus);
        adapter = new StatusAdapter();
        rvStatus.setLayoutManager(new LinearLayoutManager(getContext()));
        rvStatus.setAdapter(adapter);
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        adapter.startTicker();
        pollHandler.post(pollRunnable);
    }

    @Override public void onPause() {
        super.onPause();
        adapter.stopTicker();
        pollHandler.removeCallbacks(pollRunnable);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        exec.shutdown();
    }

    /**
     * Called by MainActivity BEFORE clearing the ESP32.
     * Saves the current live task list as a history snapshot for today.
     */
    public void snapshotToHistory(Context ctx) {
        if (!lastKnownItems.isEmpty()) {
            HistoryStore.saveSnapshot(ctx, new ArrayList<>(lastKnownItems));
        }
    }

    /**
     * Called by MainActivity after ESP32 memory is cleared.
     * Empties the UI immediately without waiting for the next poll.
     */
    public void clearAll() {
        if (!isAdded()) return;
        lastKnownItems.clear();
        adapter.update(new ArrayList<>());
        tvStatusEmpty.setVisibility(View.VISIBLE);
        rvStatus.setVisibility(View.GONE);
        tvStatusEmpty.setText("ESP32 memory cleared.\nPush new tasks from the Tasks tab.");
        tvLastSync.setText("Cleared");
    }

    // ── Polling ───────────────────────────────────────────────

    private void fetchStatus() {
        exec.execute(() -> {
            EspApi.Result r = EspApi.getTasks();
            main.post(() -> {
                if (!isAdded()) return;
                if (r.ok && r.body != null) {
                    List<TaskItem> list = parse(r.body);
                    lastKnownItems.clear();
                    lastKnownItems.addAll(list);
                    adapter.update(list);
                    boolean empty = list.isEmpty();
                    tvStatusEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    rvStatus.setVisibility(empty ? View.GONE : View.VISIBLE);
                    if (empty) tvStatusEmpty.setText(
                            "Connect to TaskBot WiFi to see live status");
                    String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(new Date());
                    tvLastSync.setText("Synced " + time);
                } else {
                    tvLastSync.setText("Sync failed");
                    if (adapter.count() == 0) {
                        tvStatusEmpty.setVisibility(View.VISIBLE);
                        rvStatus.setVisibility(View.GONE);
                        tvStatusEmpty.setText(
                                "Connect to TaskBot WiFi to see live status");
                    }
                }
            });
        });
    }

    private List<TaskItem> parse(String json) {
        List<TaskItem> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new TaskItem(
                        o.getInt("id"),
                        o.getString("name"),
                        o.getBoolean("done"),
                        o.getBoolean("running"),
                        o.getLong("totalMs"),
                        o.getString("hms")
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }
}