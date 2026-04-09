package com.taskbot;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {

    public interface OnChangeListener {
        void onChange();
    }

    private final List<String>   items    = new ArrayList<>();
    private       OnChangeListener listener;
    private       ItemTouchHelper touchHelper;

    public void setOnChangeListener(OnChangeListener l) { this.listener = l; }

    // Call this once after attaching to RecyclerView
    public void attachTouchHelper(RecyclerView rv) {
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,   // drag directions
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT // swipe directions
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int f = from.getAdapterPosition();
                int t = to.getAdapterPosition();
                Collections.swap(items, f, t);
                notifyItemMoved(f, t);
                // renumber badges live
                notifyItemRangeChanged(Math.min(f,t), Math.abs(f-t)+1);
                if (listener != null) listener.onChange();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                items.remove(pos);
                notifyItemRemoved(pos);
                notifyItemRangeChanged(pos, items.size() - pos);
                if (listener != null) listener.onChange();
            }
        });
        touchHelper.attachToRecyclerView(rv);
    }

    // ── Data ops ──────────────────────────────────────────────

    public boolean add(String name) {
        if (items.size() >= 10) return false;
        items.add(name);
        notifyItemInserted(items.size() - 1);
        if (listener != null) listener.onChange();
        return true;
    }

    public void setAll(List<String> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public List<String> getAll() { return new ArrayList<>(items); }

    public int count() { return items.size(); }

    public void clearAll() {
        int size = items.size();
        items.clear();
        notifyItemRangeRemoved(0, size);
        if (listener != null) listener.onChange();
    }

    // ── RecyclerView ─────────────────────────────────────────

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        String name = items.get(pos);
        h.tvNum.setText(String.valueOf(pos + 1));
        h.tvName.setText(name);
        // normal style (not struck through — that's status tab)
        h.tvName.setPaintFlags(
                h.tvName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);

        // start drag on handle touch
        h.tvHandle.setOnTouchListener((v, e) -> {
            touchHelper.startDrag(h);
            return false;
        });

        h.btnDelete.setOnClickListener(v -> {
            int p = h.getAdapterPosition();
            if (p == RecyclerView.NO_ID) return;
            items.remove(p);
            notifyItemRemoved(p);
            notifyItemRangeChanged(p, items.size() - p);
            if (listener != null) listener.onChange();
        });
    }

    @Override public int getItemCount() { return items.size(); }

    // ── ViewHolder ───────────────────────────────────────────

    static class VH extends RecyclerView.ViewHolder {
        TextView tvHandle, tvNum, tvName, btnDelete;
        VH(View v) {
            super(v);
            tvHandle  = v.findViewById(R.id.tvHandle);
            tvNum     = v.findViewById(R.id.tvNum);
            tvName    = v.findViewById(R.id.tvName);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}