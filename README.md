# TaskBot ESP32 Firmware

A task management and time tracking device built on ESP32 with a 0.96" OLED display, passive buzzer, and three push buttons. Tasks are pushed wirelessly from an Android app over Wi-Fi and displayed on the OLED. Each task has a built-in stopwatch that tracks time spent, persists across power cycles, and syncs back to the app in real time.

---

## Hardware

| Component | Details |
|---|---|
| Microcontroller | ESP32 DevKit v1 (CP2102 USB-Serial) |
| Display | SSD1306 0.96" OLED — 128×64 px, I2C |
| Buzzer | Passive piezo buzzer |
| Buttons | 3× momentary push buttons |

### Wiring

**OLED (I2C)**
| OLED Pin | ESP32 Pin |
|---|---|
| VCC | 3.3V |
| GND | GND |
| SDA | GPIO 21 |
| SCL | GPIO 22 |

**Buttons — INPUT_PULLUP, connect other leg to GND**
| Button | GPIO | Function |
|---|---|---|
| UP | 4 | Scroll up / back to list |
| DOWN | 5 | Scroll down / back to list |
| SEL | 18 | Select task / toggle timer / long-press to complete |

**Buzzer (passive)**
| Pin | ESP32 |
|---|---|
| + | GPIO 25 |
| − | GND |

---

## Features

- **Wi-Fi Access Point** — ESP32 creates its own hotspot (`TaskBot`, no password). No router needed.
- **HTTP REST API** — Android app communicates via plain HTTP to `192.168.4.1`
- **Stopwatch per task** — HH:MM:SS with centiseconds, ticking live on OLED
- **Three screens** — Task list → Session/timer → Done splash
- **Buzzer feedback** — unique sound for every action (start, pause, complete, error, boot)
- **NVS persistence** — task names, accumulated time, and completion state survive power loss
- **Auto-save** — saves to flash every 30 seconds while a timer is running

---

## Button Controls

| Context | Button | Action |
|---|---|---|
| Task list | UP / DOWN | Scroll through tasks |
| Task list | SEL | Open task session |
| Session | SEL (short) | Start / pause timer toggle |
| Session | SEL (long, 1 s) | Mark task as complete |
| Session | UP or DOWN | Go back to task list |

---

## REST API

All endpoints served on `http://192.168.4.1`

| Method | Path | Body | Description |
|---|---|---|---|
| GET | `/` | — | HTML status page (auto-refreshes every 3s) |
| GET | `/tasks` | — | JSON array of all tasks with live timer values |
| POST | `/tasks` | `[{"name":"task1"},…]` | Add tasks (deduplicates by name) |
| POST | `/clear` | `{}` | Wipe all tasks from RAM and NVS flash |
| GET | `/status` | — | Quick ping — returns `{"ok":true}` |

### GET /tasks response example
```json
[
  {
    "id": 0,
    "name": "Buy groceries",
    "done": false,
    "running": true,
    "totalMs": 167340,
    "totalSecs": 167,
    "hms": "00:02:47"
  }
]
```

---

## Libraries Required

Install all via Arduino Library Manager:

| Library | Purpose |
|---|---|
| Adafruit SSD1306 | OLED driver |
| Adafruit GFX Library | Graphics primitives |
| ArduinoJson (v6.x) | JSON parsing for HTTP body |
| WiFi | Built into ESP32 Arduino core |
| WebServer | Built into ESP32 Arduino core |
| Preferences | Built into ESP32 Arduino core — NVS flash storage |

---

## Arduino IDE Setup

1. Install **ESP32 board package** via Boards Manager — search `esp32` by Espressif
2. Select board: **ESP32 Dev Module**
3. Upload speed: `115200`
4. Install the three libraries listed above
5. Flash `TaskBot_ESP32_v4.ino`

---

## OLED Screens

### Screen 1 — Task List
```
┌ TaskBot          3/10 ┐
│ ► 1. Buy groceries    │
│   2. Call dentist     │
│ ~~3. Fix bike~~       │  ← strikethrough = complete
│   4. Read book        │
└ ^v:scroll  SEL:open   ┘
```

### Screen 2 — Session (stopwatch)
```
┌ Call dentist  [PAUSED] ┐
│                        │
│      00:02:47          │
│                  .48   │
│ ▶ START  ·  ✓ DONE     │
└ ^v:back  LONG:done     ┘
```

### Screen 3 — Complete splash
```
┌                        ┐
│  * DONE!               │
│  ~~Call dentist~~      │
│  Total: 00:05:10       │
│  back in 3s...         │
└                        ┘
```

---

## NVS Storage Keys

| Key | Type | Content |
|---|---|---|
| `count` | uint32 | Number of tasks stored |
| `t0name` | String | Task 0 name |
| `t0secs` | uint32 | Task 0 accumulated seconds |
| `t0done` | uint8 | Task 0 completion flag (0 or 1) |
| `t1name` … | String | Task 1+ (same pattern) |

Data survives power cycles indefinitely. To wipe: call `POST /clear` from the app or reflash the firmware.

---

## Buzzer Sounds

| Event | Sound |
|---|---|
| Boot | Two-tone rising chime |
| Button press | Short 1500 Hz click |
| Enter task session | Two rising beeps |
| Timer start | Three rising tones |
| Timer pause | Two falling tones |
| Task complete | C–E–G–C victory tune |
| Error (completed task) | Low descending buzz |
| Memory cleared | Descending three-tone |

---

## Project Structure

```
TaskBot_ESP32_v4.ino   — main firmware (single file)
```

---

## License

MIT
