package com.taskbot;

import android.Manifest;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectFragment extends Fragment {

    private static final int REQ_LOCATION = 101;

    private View     vConnDot;
    private TextView tvConnStatus, tvWifiName, tvPingResult;
    private Button   btnOpenWifi, btnPing;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler         main = new Handler(Looper.getMainLooper());

    public interface OnConnectionChanged {
        void onChanged(boolean connected);
    }
    private OnConnectionChanged callback;
    public void setConnectionCallback(OnConnectionChanged cb) { this.callback = cb; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        View root = inf.inflate(R.layout.fragment_connect, container, false);

        vConnDot     = root.findViewById(R.id.vConnDot);
        tvConnStatus = root.findViewById(R.id.tvConnStatus);
        tvWifiName   = root.findViewById(R.id.tvWifiName);
        tvPingResult = root.findViewById(R.id.tvPingResult);
        btnOpenWifi  = root.findViewById(R.id.btnOpenWifi);
        btnPing      = root.findViewById(R.id.btnPing);

        btnOpenWifi.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        btnPing.setOnClickListener(v -> doPing());

        // Ask for location permission on first open (needed to read SSID)
        checkLocationPermission();

        return root;
    }

    @Override public void onResume() {
        super.onResume();
        refreshWifiLabel();
    }

    // ── Location permission (needed for SSID on Android 8+) ───

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
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            refreshWifiLabel();
        }
    }

    // ── WiFi SSID label ───────────────────────────────────────

    private void refreshWifiLabel() {
        if (getContext() == null) return;

        // Check permission first
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            tvWifiName.setText("WiFi: (location permission needed to show name)");
            return;
        }

        WifiManager wm = (WifiManager)
                requireContext().getApplicationContext()
                        .getSystemService(android.content.Context.WIFI_SERVICE);

        if (wm == null || !wm.isWifiEnabled()) {
            tvWifiName.setText("WiFi: off");
            return;
        }

        WifiInfo info = wm.getConnectionInfo();
        if (info == null) {
            tvWifiName.setText("WiFi: not connected");
            return;
        }

        String ssid = info.getSSID();
        // Android wraps SSID in quotes — strip them
        if (ssid != null) ssid = ssid.replace("\"", "");

        // "<unknown ssid>" means permission not granted or not associated
        if (ssid == null || ssid.equals("<unknown ssid>") || ssid.isEmpty()) {
            tvWifiName.setText("WiFi: connected (SSID hidden — location permission needed)");
        } else {
            tvWifiName.setText("WiFi: " + ssid);

            // Highlight green if on TaskBot network
            if (ssid.equals("TaskBot")) {
                tvWifiName.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.green));
            } else {
                tvWifiName.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }
        }
    }

    // ── Ping ──────────────────────────────────────────────────

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
                if (callback != null) callback.onChanged(ok);

                if (ok) {
                    tvPingResult.setText("ESP32 responded — you are connected!");
                    tvPingResult.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.green));
                } else {
                    tvPingResult.setText("No response: " + r.error
                            + "\n\nMake sure your phone is on the TaskBot WiFi network.");
                    tvPingResult.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.red));
                }
            });
        });
    }

    // ── Called by MainActivity to sync dot state ──────────────

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