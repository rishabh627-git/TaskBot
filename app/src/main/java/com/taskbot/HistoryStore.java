package com.taskbot;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class HistoryStore {

    private static final String TAG       = "TaskBot";
    private static final String PREFS     = "tb_history";
    private static final String KEY_DATES = "dates";
    private static final String DAY_PFX   = "day_";

    // ── Save ──────────────────────────────────────────────────

    public static void saveSnapshot(Context ctx, List<TaskItem> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            Log.w(TAG, "saveSnapshot called with empty list — nothing written");
            return;
        }

        String today  = todayStr();
        SharedPreferences        prefs  = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Load what is already stored for today
        List<HistoryEntry> existing = loadDay(prefs, today);
        Log.d(TAG, "saveSnapshot: today=" + today
                + "  existing=" + existing.size()
                + "  incoming=" + tasks.size());

        for (TaskItem t : tasks) {
            String  name = t.name.trim();
            long    secs = t.totalMs / 1000;
            boolean found = false;

            for (HistoryEntry e : existing) {
                if (e.name.trim().equalsIgnoreCase(name)) {
                    Log.d(TAG, "  merge: " + name
                            + "  +" + secs + "s  done=" + t.done);
                    e.totalSecs += secs;
                    if (t.done) e.done = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                Log.d(TAG, "  new entry: " + name
                        + "  " + secs + "s  done=" + t.done);
                existing.add(new HistoryEntry(today, name, t.done, secs));
            }
        }

        // Write merged day data
        try {
            JSONArray dayArr = new JSONArray();
            for (HistoryEntry e : existing) {
                JSONObject o = new JSONObject();
                o.put("name", e.name);
                o.put("done", e.done);
                o.put("secs", e.totalSecs);
                dayArr.put(o);
            }
            editor.putString(DAY_PFX + today, dayArr.toString());
            Log.d(TAG, "saveSnapshot: wrote " + existing.size()
                    + " entries for " + today);
        } catch (Exception ex) {
            Log.e(TAG, "saveSnapshot JSON error: " + ex.getMessage());
        }

        // Ensure today is in the dates index
        List<String> dates = loadDates(prefs);
        if (!dates.contains(today)) {
            dates.add(0, today);
            JSONArray dArr = new JSONArray();
            for (String d : dates) dArr.put(d);
            editor.putString(KEY_DATES, dArr.toString());
            Log.d(TAG, "saveSnapshot: added " + today + " to dates index");
        }

        editor.apply(); // single atomic commit
    }

    // ── Load ──────────────────────────────────────────────────

    public static LinkedHashMap<String, List<HistoryEntry>> loadAll(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        LinkedHashMap<String, List<HistoryEntry>> result = new LinkedHashMap<>();
        for (String date : loadDates(prefs)) {
            List<HistoryEntry> entries = loadDay(prefs, date);
            if (!entries.isEmpty()) result.put(date, entries);
        }
        return result;
    }

    public static List<String> allDates(Context ctx) {
        return loadDates(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    public static List<HistoryEntry> loadDate(Context ctx, String date) {
        return loadDay(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE), date);
    }

    // ── Date formatting ───────────────────────────────────────

    public static String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    public static String formatDate(String raw) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(raw);
            return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(d);
        } catch (Exception e) { return raw; }
    }

    public static String formatDateShort(String raw) {
        try {
            if (raw.equals(todayStr())) return "Today";
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(raw);
            return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(d);
        } catch (Exception e) { return raw; }
    }

    // ── Private helpers ───────────────────────────────────────

    private static List<String> loadDates(SharedPreferences p) {
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(p.getString(KEY_DATES, "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (Exception ignored) {}
        return list;
    }

    private static List<HistoryEntry> loadDay(SharedPreferences p, String date) {
        List<HistoryEntry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(p.getString(DAY_PFX + date, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new HistoryEntry(date,
                        o.getString("name"),
                        o.getBoolean("done"),
                        o.getLong("secs")));
            }
        } catch (Exception ignored) {}
        return list;
    }
}