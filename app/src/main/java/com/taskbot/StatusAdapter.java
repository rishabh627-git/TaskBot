package com.taskbot;

import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.VH> {

    private final List<TaskItem> items = new ArrayList<>();
    private final Handler        ticker = new Handler(Looper.getMainLooper());
    private       RecyclerView   attachedRv;

    // Tick every second to update running timers
    private final Runnable tickRunnable = new Runnable() {
        @Override public void run() {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).running) notifyItemChanged(i);
            }
            ticker.postDelayed(this, 1000);
        }
    };

    public void startTicker() {
        ticker.removeCallbacks(tickRunnable);
        ticker.post(tickRunnable);
    }

    public void stopTicker() {
        ticker.removeCallbacks(tickRunnable);
    }

    public void update(List<TaskItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public int count() { return items.size(); }

    // ── RecyclerView ─────────────────────────────────────────

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_status, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TaskItem t = items.get(pos);

        // Task name
        h.tvName.setText(t.name);
        if (t.done) {
            // strikethrough
            h.tvName.setPaintFlags(h.tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvName.setTextColor(
                    ContextCompat.getColor(h.tvName.getContext(), R.color.text_secondary));
        } else {
            h.tvName.setPaintFlags(
                    h.tvName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvName.setTextColor(
                    ContextCompat.getColor(h.tvName.getContext(), R.color.text_primary));
        }

        // Timer — ticks live if running
        h.tvTimer.setText(t.liveHms());

        // Badge
        if (t.running) {
            h.tvBadge.setVisibility(View.VISIBLE);
            h.tvBadge.setText("RUNNING");
            h.tvBadge.setBackground(
                    ContextCompat.getDrawable(h.tvBadge.getContext(), R.drawable.badge_running));
            h.tvBadge.setTextColor(
                    ContextCompat.getColor(h.tvBadge.getContext(), R.color.amber));
        } else if (t.done) {
            h.tvBadge.setVisibility(View.VISIBLE);
            h.tvBadge.setText("DONE");
            h.tvBadge.setBackground(
                    ContextCompat.getDrawable(h.tvBadge.getContext(), R.drawable.badge_done));
            h.tvBadge.setTextColor(
                    ContextCompat.getColor(h.tvBadge.getContext(), R.color.green));
        } else {
            h.tvBadge.setVisibility(View.GONE);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvTimer, tvBadge;
        VH(View v) {
            super(v);
            tvName  = v.findViewById(R.id.tvTaskName);
            tvTimer = v.findViewById(R.id.tvTimer);
            tvBadge = v.findViewById(R.id.tvBadge);
        }
    }
}
