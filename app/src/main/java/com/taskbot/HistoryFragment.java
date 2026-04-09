package com.taskbot;

import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Calendar;
import java.util.List;

public class HistoryFragment extends Fragment {

    private HistoryAdapter    adapter;
    private LinearLayout      llChips;
    private HorizontalScrollView hsvDates;
    private TextView          tvSelectedDate, tvHistoryEmpty;
    private RecyclerView      rvHistory;

    private List<String>      dates;          // all available date strings
    private String            selectedDate;   // currently shown date

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        View root = inf.inflate(R.layout.fragment_history, container, false);

        llChips        = root.findViewById(R.id.llChips);
        hsvDates       = root.findViewById(R.id.hsvDates);
        tvSelectedDate = root.findViewById(R.id.tvSelectedDate);
        tvHistoryEmpty = root.findViewById(R.id.tvHistoryEmpty);
        rvHistory      = root.findViewById(R.id.rvHistory);

        adapter = new HistoryAdapter();
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        rvHistory.setAdapter(adapter);

        root.findViewById(R.id.btnCalendar).setOnClickListener(v -> showCalendar());

        loadHistory();
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        loadHistory();
    }

    /** Called by MainActivity after a clear+snapshot */
    public void refresh() {
        if (isAdded()) loadHistory();
    }

    // ── Load & render ─────────────────────────────────────────

    private void loadHistory() {
        dates = HistoryStore.allDates(requireContext());

        if (dates.isEmpty()) {
            showEmpty();
            return;
        }

        // Default to first date (newest) if none selected or selection gone
        if (selectedDate == null || !dates.contains(selectedDate)) {
            selectedDate = dates.get(0);
        }

        buildChips();
        showDate(selectedDate);
    }

    private void showEmpty() {
        llChips.removeAllViews();
        rvHistory.setVisibility(View.GONE);
        tvSelectedDate.setText("");
        tvHistoryEmpty.setVisibility(View.VISIBLE);
        tvHistoryEmpty.setText("No history yet.\n\nClear the ESP32 memory after\na session to save it here.");
    }

    // ── Date chips ────────────────────────────────────────────

    private void buildChips() {
        llChips.removeAllViews();
        for (int i = 0; i < dates.size(); i++) {
            String date = dates.get(i);
            llChips.addView(makeChip(date, date.equals(selectedDate)));
        }
    }

    private View makeChip(String date, boolean selected) {
        TextView chip = new TextView(requireContext());
        chip.setText(HistoryStore.formatDateShort(date));
        chip.setTextSize(13f);
        chip.setPadding(dp(16), dp(6), dp(16), dp(6));

        if (selected) {
            chip.setBackground(makeChipBg(true));
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            chip.setTypeface(null, Typeface.BOLD);
        } else {
            chip.setBackground(makeChipBg(false));
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            chip.setTypeface(null, Typeface.NORMAL);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> {
            selectedDate = date;
            buildChips();      // re-render chips with new selection
            showDate(date);    // scroll chip into view
            scrollChipIntoView(date);
        });
        return chip;
    }

    private android.graphics.drawable.GradientDrawable makeChipBg(boolean selected) {
        android.graphics.drawable.GradientDrawable gd =
                new android.graphics.drawable.GradientDrawable();
        gd.setCornerRadius(dp(20));
        if (selected) {
            gd.setColor(ContextCompat.getColor(requireContext(), R.color.accent));
        } else {
            gd.setColor(ContextCompat.getColor(requireContext(), R.color.surface2));
        }
        return gd;
    }

    private void scrollChipIntoView(String date) {
        int idx = dates.indexOf(date);
        if (idx < 0) return;
        // Approximate chip width to scroll to it
        hsvDates.postDelayed(() -> {
            View chip = llChips.getChildAt(idx);
            if (chip != null) hsvDates.smoothScrollTo(chip.getLeft(), 0);
        }, 80);
    }

    // ── Show tasks for a date ─────────────────────────────────

    private void showDate(String date) {
        List<HistoryEntry> entries = HistoryStore.loadDate(requireContext(), date);
        adapter.update(entries);

        tvSelectedDate.setText(HistoryStore.formatDate(date));

        if (entries.isEmpty()) {
            rvHistory.setVisibility(View.GONE);
            tvHistoryEmpty.setVisibility(View.VISIBLE);
            tvHistoryEmpty.setText("No tasks recorded for " + HistoryStore.formatDate(date));
        } else {
            rvHistory.setVisibility(View.VISIBLE);
            tvHistoryEmpty.setVisibility(View.GONE);
        }
    }

    // ── Calendar picker ───────────────────────────────────────

    private void showCalendar() {
        Calendar cal = Calendar.getInstance();

        // Pre-select the currently viewed date in the picker
        if (selectedDate != null) {
            try {
                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                java.util.Date d = sdf.parse(selectedDate);
                if (d != null) cal.setTime(d);
            } catch (Exception ignored) {}
        }

        new DatePickerDialog(requireContext(),
                (view, year, month, day) -> {
                    // Format chosen date as "yyyy-MM-dd"
                    String chosen = String.format(java.util.Locale.getDefault(),
                            "%04d-%02d-%02d", year, month + 1, day);
                    if (dates.contains(chosen)) {
                        selectedDate = chosen;
                        buildChips();
                        showDate(chosen);
                        scrollChipIntoView(chosen);
                    } else {
                        // Date has no history — show message
                        tvSelectedDate.setText(HistoryStore.formatDate(chosen));
                        rvHistory.setVisibility(View.GONE);
                        tvHistoryEmpty.setVisibility(View.VISIBLE);
                        tvHistoryEmpty.setText(
                                "No history for " + HistoryStore.formatDate(chosen));
                    }
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private int dp(int val) {
        return Math.round(val * requireContext().getResources()
                .getDisplayMetrics().density);
    }
}