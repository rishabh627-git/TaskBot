package com.taskbot;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TasksFragment extends Fragment {

    private EditText    etTask;
    private TextView    tvCount;
    private Button      btnAdd, btnPush;
    private TaskAdapter adapter;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler         main = new Handler(Looper.getMainLooper());

    private static volatile boolean espReachable = false;
    public  static void setEspReachable(boolean v) { espReachable = v; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        View root = inf.inflate(R.layout.fragment_tasks, container, false);

        etTask  = root.findViewById(R.id.etTask);
        tvCount = root.findViewById(R.id.tvCount);
        btnAdd  = root.findViewById(R.id.btnAdd);
        btnPush = root.findViewById(R.id.btnPush);

        RecyclerView rv = root.findViewById(R.id.rvTasks);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TaskAdapter();
        rv.setAdapter(adapter);
        adapter.attachTouchHelper(rv);

        adapter.setAll(TaskStore.load(requireContext()));
        updateCount();

        adapter.setOnChangeListener(() -> {
            TaskStore.save(requireContext(), adapter.getAll());
            main.post(this::updateCount);
        });

        btnAdd.setOnClickListener(v -> addTask());
        etTask.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { addTask(); return true; }
            return false;
        });

        btnPush.setOnClickListener(v -> pushTasks());

        return root;
    }

    private void addTask() {
        String text = etTask.getText().toString().trim();
        if (TextUtils.isEmpty(text)) { etTask.setError("Enter a task"); return; }
        if (text.length() > 40) text = text.substring(0, 40);
        if (!adapter.add(text)) {
            Toast.makeText(getContext(), "Max 10 tasks reached", Toast.LENGTH_SHORT).show();
            return;
        }
        etTask.setText("");
        TaskStore.save(requireContext(), adapter.getAll());
        updateCount();
    }

    private void updateCount() {
        int n = adapter.count();
        tvCount.setText(n + " / 10 tasks");
        btnPush.setAlpha(n > 0 ? 1f : 0.4f);
    }

    private void pushTasks() {
        if (adapter.count() == 0) {
            Toast.makeText(getContext(), "Add tasks first", Toast.LENGTH_SHORT).show();
            return;
        }
        btnPush.setEnabled(false);
        btnPush.setText("Pushing…");

        // Snapshot the list before clearing
        java.util.List<String> toSend = adapter.getAll();

        exec.execute(() -> {
            EspApi.Result result = EspApi.postTasks(toSend);
            main.post(() -> {
                if (!isAdded()) return;
                btnPush.setEnabled(true);
                btnPush.setText("Push Tasks to ESP32");

                if (result.ok) {
                    // ── SUCCESS: clear local task list ──────────────
                    // Tasks are now on the ESP32 — user sees them in Status tab
                    adapter.clearAll();
                    TaskStore.save(requireContext(), adapter.getAll()); // save empty list
                    updateCount();

                    int added = 0;
                    try {
                        org.json.JSONObject jo = new org.json.JSONObject(result.body);
                        added = jo.optInt("added", 0);
                        int total = jo.optInt("total", 0);
                        Toast.makeText(getContext(),
                                "Pushed! " + added + " new task(s) on ESP32.\nCheck Status tab.",
                                Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(getContext(),
                                "Pushed! Check Status tab.",
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(),
                            "Failed: " + result.error
                                    + "\n\nMake sure you are on TaskBot WiFi.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        exec.shutdown();
    }
}
