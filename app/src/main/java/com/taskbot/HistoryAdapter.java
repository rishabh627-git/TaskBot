package com.taskbot;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * Flat list of HistoryEntry rows for a single selected date.
 * No expand/collapse — the date picker handles navigation between days.
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private final List<HistoryEntry> items = new ArrayList<>();

    public void update(List<HistoryEntry> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public boolean isEmpty() { return items.isEmpty(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        HistoryEntry e = items.get(pos);

        h.tvName.setText(e.name);
        h.tvTime.setText(e.hms());

        if (e.done) {
            h.tvName.setPaintFlags(h.tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvName.setTextColor(
                    ContextCompat.getColor(h.tvName.getContext(), R.color.text_secondary));
            h.tvStatus.setText("✓");
            h.tvStatus.setTextColor(
                    ContextCompat.getColor(h.tvStatus.getContext(), R.color.green));
        } else {
            h.tvName.setPaintFlags(h.tvName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvName.setTextColor(
                    ContextCompat.getColor(h.tvName.getContext(), R.color.text_primary));
            h.tvStatus.setText("–");
            h.tvStatus.setTextColor(
                    ContextCompat.getColor(h.tvStatus.getContext(), R.color.text_secondary));
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvStatus;
        VH(View v) {
            super(v);
            tvName   = v.findViewById(R.id.tvHistoryName);
            tvTime   = v.findViewById(R.id.tvHistoryTime);
            tvStatus = v.findViewById(R.id.tvHistoryStatus);
        }
    }
}