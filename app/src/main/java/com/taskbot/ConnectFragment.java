package com.taskbot;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectFragment extends Fragment {

    private static final int REQ_LOCATION = 101;

    private View     vConnDot;
    private TextView tvConnStatus, tvWifiName, tvPingResult;
    private Button   btnOpenWifi, btnPing, btnClearMem;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler         main = new Handler(Looper.getMainLooper());

    public interface OnConnectionChanged {
        void onChanged(boolean connected);
    }

    public interface OnMemoryCleared {
        void onCleared();
    }

    private OnConnectionChanged connCallback;
    private OnMemoryCleared     clearCallback;

    public void setConnectionCallback(OnConnectionChanged cb) { this.connCallback = cb; }
    public void setMemoryClearedCallback(OnMemoryCleared cb)  { this.clearCallback = cb; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        View root = inf.inflate(R.layout.fragment_connect, container, false);

        vConnDot    = root.findViewById(R.id.vConnDot);
        tvConnStatus= root.findViewById(R.id.tvConnStatus);
        tvWifiName  = root.findViewById(R.id.tvWifiName);
        tvPingResult= root.findViewById(R.id.tvPingResult);
        btnOpenWifi = root.findViewById(R.id.btnOpenWifi);
        btnPing     = root.findViewById(R.id.btnPing);
        btnClearMem = root.findViewById(R.id.btnClearMem);

        btnOpenWifi.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnPing.setOnClickListener(v -> doPing());
        btnClearMem.setOnClickListener(v -> confirmClear());

        checkLocationPermission();
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        refreshWifiLabel();
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                    REQ_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_LOCATION) refreshWifiLabel();
    }

    private void refreshWifiLabel() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            tvWifiName.setText("WiFi: (allow location to show name)");
            return;
        }
        WifiManager wm = (WifiManager)
                requireContext().getApplicationContext()
                        .getSystemService(android.content.Context.WIFI_SERVICE);
        if (wm == null || !wm.isWifiEnabled()) {
            tvWifiName.setText("WiFi: off"); return;
        }
        WifiInfo info = wm.getConnectionInfo();
        if (info == null) { tvWifiName.setText("WiFi: not connected"); return; }
        String ssid = info.getSSID();
        if (ssid != null) ssid = ssid.replace("\"", "");
        if (ssid == null || ssid.equals("<unknown ssid>") || ssid.isEmpty()) {
            tvWifiName.setText("WiFi: connected (SSID hidden)");
        } else {
            tvWifiName.setText("WiFi: " + ssid);
            tvWifiName.setTextColor(ContextCompat.getColor(requireContext(),
                    ssid.equals("TaskBot") ? R.color.green : R.color.text_secondary));
        }
    }

    private void doPing() {
        btnPing.setEnabled(false);
        btnPing.setText("Pinging…");
        tvPingResult.setText("");
        exec.execute(() -> {
            EspApi.Result r = EspApi.ping();
            main.post(() -> {
                if (!isAdded()) return;
                btnPing.setEnabled(true);
                btnPing.setText("Ping ESP32");
                boolean ok = r.ok;
                setConnected(ok);
                if (connCallback != null) connCallback.onChanged(ok);
                if (ok) {
                    tvPingResult.setText("ESP32 responded — connected!");
                    tvPingResult.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.green));
                } else {
                    tvPingResult.setText("No response: " + r.error
                            + "\n\nJoin TaskBot WiFi first.");
                    tvPingResult.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.red));
                }
            });
        });
    }

    private void confirmClear() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear ESP32 Memory?")
                .setMessage("This will permanently delete ALL tasks and timers stored on the ESP32.\n\nThis cannot be undone.")
                .setPositiveButton("Clear", (d, w) -> doClear())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doClear() {
        btnClearMem.setEnabled(false);
        btnClearMem.setText("Clearing…");
        tvPingResult.setText("");
        exec.execute(() -> {
            EspApi.Result r = EspApi.clearMemory();
            main.post(() -> {
                if (!isAdded()) return;
                btnClearMem.setEnabled(true);
                btnClearMem.setText("Clear ESP32 Memory");
                if (r.ok) {
                    tvPingResult.setText("ESP32 memory cleared successfully.");
                    tvPingResult.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.green));
                    if (clearCallback != null) clearCallback.onCleared();
                } else {
                    tvPingResult.setText("Failed: " + r.error
                            + "\n\nMake sure you are on TaskBot WiFi.");
                    tvPingResult.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.red));
                }
            });
        });
    }

    public void setConnected(boolean connected) {
        if (getContext() == null || vConnDot == null) return;
        vConnDot.setBackground(ContextCompat.getDrawable(requireContext(),
                connected ? R.drawable.dot_connected : R.drawable.dot_disconnected));
        tvConnStatus.setText(connected ? "Connected to TaskBot" : "Not connected");
        tvConnStatus.setTextColor(ContextCompat.getColor(requireContext(),
                connected ? R.color.green : R.color.text_primary));
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        exec.shutdown();
    }
}