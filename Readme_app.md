# TaskBot Android App

Android companion app for the TaskBot ESP32 device. Manage your task list, push tasks wirelessly to the device, watch live session timers, and browse day-by-day history of completed work — all from a clean three-tab interface.

---

## Screenshots

> Task list tab → Connect tab → Status tab → History tab

---

## How It Works

```
Phone (TaskBot App)
      │
      │  HTTP over Wi-Fi  (192.168.4.1)
      │
ESP32 Access Point
      │
      │  I2C
      │
SSD1306 OLED Display
```

1. Power on the ESP32 — it creates a Wi-Fi hotspot named **TaskBot**
2. Connect your phone to that network
3. Add tasks in the app and tap **Push Tasks to ESP32**
4. Use the buttons on the device to start/pause/complete tasks
5. The **Status** tab polls the device every 2 seconds and shows live timers
6. When you tap **Clear ESP32 Memory**, the current session is saved to **History** before clearing

---

## Features

### Tasks Tab
- Add tasks (max 10, max 40 characters each)
- Drag the `⠿` handle to reorder
- Swipe left or right to delete
- Task list saved locally — survives app restarts
- **Push Tasks** sends all tasks to the ESP32 in one HTTP POST
- After a successful push the list clears — tasks move to the Status tab

### Connect Tab
- Shows current Wi-Fi network name
- **Open WiFi Settings** — jump straight to Android Wi-Fi settings to join TaskBot network
- **Ping ESP32** — tests connectivity, updates the green/red dot in the header
- **Clear ESP32 Memory** — confirmation dialog, then wipes all tasks and timers from the device. Snapshots current state to History first.

### Status Tab
- Polls `GET /tasks` every 2 seconds
- Shows each task with live ticking HH:MM:SS timer
- **RUNNING** badge (amber) on the active task
- **DONE** badge (green) on completed tasks
- Completed tasks shown with strikethrough

### History Tab
- Horizontal scrolling date chips — swipe to move between days
- **Today** chip always shown first
- 📅 calendar button — pick any date to jump to it
- Each day shows all tasks with time spent and completion status
- Time accumulates across sessions — if you work on the same task multiple times in a day the totals add up

---

## Setup

### Prerequisites
- Android Studio (latest stable)
- Android device running Android 7.0 (API 24) or higher
- Physical device required — BLE/WiFi features do not work on emulator

### Build steps

1. Clone or extract the project
2. Open in Android Studio — `File → Open → TaskBotApp/`
3. Let Gradle sync
4. Connect your Android device via USB with developer mode enabled
5. Run → select your device

### Dependencies

All resolved automatically by Gradle:

```gradle
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.viewpager2:viewpager2:1.0.0'
```

No third-party networking library — all HTTP uses `HttpURLConnection` from the Android standard library.

---

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | HTTP calls to ESP32 |
| `ACCESS_WIFI_STATE` | Read current SSID |
| `ACCESS_FINE_LOCATION` | Required by Android 8+ to read WiFi SSID |

The app requests location permission at runtime on first launch. Denying it only hides the WiFi network name — everything else still works.

---

## Project Structure

```
app/src/main/
├── java/com/taskbot/
│   ├── MainActivity.java        — 4-tab shell, header dot, background ping
│   ├── EspApi.java              — all HTTP calls to 192.168.4.1
│   ├── TaskStore.java           — local SharedPreferences for task list
│   ├── TaskItem.java            — model for live task from ESP32
│   ├── HistoryEntry.java        — model for one task in history
│   ├── HistoryStore.java        — date-keyed history persistence
│   ├── TaskAdapter.java         — tasks tab RecyclerView (drag + swipe)
│   ├── StatusAdapter.java       — status tab RecyclerView (live ticker)
│   ├── HistoryAdapter.java      — history tab RecyclerView
│   ├── TasksFragment.java       — Tab 1: task list + push
│   ├── ConnectFragment.java     — Tab 2: ping + clear
│   ├── StatusFragment.java      — Tab 3: live task monitor
│   └── HistoryFragment.java     — Tab 4: day browser + calendar
└── res/
    ├── layout/                  — 9 XML layout files
    ├── drawable/                — shape drawables, dots, badges
    └── values/                  — colors, styles
```

---

## Network Details

| Property | Value |
|---|---|
| ESP32 SSID | `TaskBot` |
| Password | None (open network) |
| ESP32 IP | `192.168.4.1` |
| Your phone IP | `192.168.4.2` |
| Protocol | HTTP (cleartext — allowed via `network_security_config.xml`) |
| Timeout | 4 seconds per request |
| Poll interval | 2 seconds (Status tab only, pauses when tab is not visible) |
| Background ping | Every 5 seconds (header dot) |

---

## API Reference

The app talks to these ESP32 endpoints:

| Call | Method | Path | When |
|---|---|---|---|
| Ping | GET | `/status` | Every 5s (background) + manual ping |
| Get tasks | GET | `/tasks` | Every 2s when Status tab is open |
| Push tasks | POST | `/tasks` | Tap "Push Tasks" |
| Clear memory | POST | `/clear` | Tap "Clear ESP32 Memory" + confirm |

---

## Data Flow — Clear Memory

```
User taps "Clear ESP32 Memory"
        ↓
Confirmation dialog
        ↓
snapshotToHistory()          ← saves lastKnownItems to HistoryStore
        ↓
POST /clear → ESP32          ← wipes device RAM + NVS
        ↓
clearAll()                   ← empties Status tab UI
        ↓
historyFragment.refresh()    ← today's entry appears in History tab
```

History entries accumulate — running the same task multiple times on the same day adds the times together rather than overwriting.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| "No response" on ping | Make sure phone is connected to **TaskBot** Wi-Fi, not your home network |
| `cleartext HTTP not permitted` | Ensure `network_security_config.xml` is in `res/xml/` and referenced in `AndroidManifest.xml` |
| SSID shows `<unknown ssid>` | Grant location permission when the app asks |
| History not updating on second clear | Wait until Status tab shows the new tasks (green sync timestamp visible) before tapping Clear |
| Status tab stuck on "Sync failed" | ESP32 may have restarted — re-join TaskBot Wi-Fi |

---

