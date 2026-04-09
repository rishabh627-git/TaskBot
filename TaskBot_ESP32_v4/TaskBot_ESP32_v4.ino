/*
 * ================================================================
 *  TaskBot ESP32  v4  —  FINAL WORKING CONFIG
 * ================================================================
 *
 *  WIRING (exact same as BuzzerTest_Simple that worked):
 *
 *    BUTTONS (INPUT_PULLUP — just wire to GND, no resistors):
 *      GPIO  4  ── [BTN UP]   ── GND
 *      GPIO  5  ── [BTN DOWN] ── GND
 *      GPIO 18  ── [BTN SEL]  ── GND
 *      PRESSED = LOW
 *
 *    BUZZER (passive):
 *      GPIO 25  ── Buzzer +
 *      GND      ── Buzzer -
 *
 *    OLED (I2C):
 *      GPIO 21  ── SDA
 *      GPIO 22  ── SCL
 *      3.3V     ── VCC
 *      GND      ── GND
 *
 *  NETWORK:
 *    SSID: TaskBot  (open, no password)
 *    IP:   192.168.4.1
 *    POST /tasks  →  [{"name":"task1"},{"name":"task2"}]
 *    GET  /tasks  →  full JSON state back to app
 *    GET  /       →  browser status page (auto-refreshes)
 *
 *  PERSISTENCE:
 *    NVS flash — survives power off forever
 *    Saves on: task added, timer toggled, task complete
 *    Auto-save every 30s while a timer is running
 * ================================================================
 */

#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>
#include <Preferences.h>

// ── OLED ──────────────────────────────────────────────────────
#define SCREEN_W  128
#define SCREEN_H   64
#define OLED_ADDR 0x3C
Adafruit_SSD1306 oled(SCREEN_W, SCREEN_H, &Wire, -1);

// ── Network ───────────────────────────────────────────────────
const char* AP_SSID = "TaskBot";
const char* AP_PASS = "";
WebServer server(80);

// ── NVS ───────────────────────────────────────────────────────
Preferences prefs;

// ── Pins (same as working test) ───────────────────────────────
#define BTN_UP    4
#define BTN_DOWN  5
#define BTN_SEL  18
#define BUZZER   25
// PRESSED = LOW  (INPUT_PULLUP, button other leg → GND)

// ── Tasks ─────────────────────────────────────────────────────
#define MAX_TASKS 10

struct Task {
  char          name[48];
  bool          complete;
  bool          running;
  unsigned long sessionStartMs;
  unsigned long accumulatedMs;
};

Task tasks[MAX_TASKS];
int  taskCount = 0;

// ── UI state ──────────────────────────────────────────────────
enum Screen { SCR_LIST, SCR_SESSION, SCR_DONE };
Screen currentScreen = SCR_LIST;
int    listCursor    = 0;
int    activeTask    = -1;
unsigned long doneAt = 0;

// ── Button debounce ───────────────────────────────────────────
struct Btn {
  bool          held;
  unsigned long pressedAt;
  unsigned long lastFiredAt;
};
Btn bUp = {}, bDown = {}, bSel = {};
#define DEBOUNCE   180UL   // ms — ignore bounces shorter than this
#define LONGPRESS  900UL   // ms — hold SEL this long = mark complete

// ─────────────────────────────────────────────────────────────
//  BUZZER  —  tone() only, no LEDC
// ─────────────────────────────────────────────────────────────

void bClick() {
  tone(BUZZER, 1500, 80);
}

void bEnterSession() {
  tone(BUZZER, 1200, 60); delay(90);
  tone(BUZZER, 1600, 80);
}

void bTimerStart() {
  tone(BUZZER, 800,  50); delay(70);
  tone(BUZZER, 1200, 50); delay(70);
  tone(BUZZER, 1600, 80);
}

void bTimerPause() {
  tone(BUZZER, 1000, 60); delay(80);
  tone(BUZZER, 700,  90);
}

void bComplete() {
  int notes[] = {523, 659, 784, 1047};
  for (int i = 0; i < 4; i++) {
    tone(BUZZER, notes[i], 100);
    delay(130);
  }
}

void bError() {
  tone(BUZZER, 300, 80); delay(100);
  tone(BUZZER, 250, 120);
}

void bBack() {
  tone(BUZZER, 900, 40);
}

void bTaskReceived(int count) {
  for (int i = 0; i < min(count, 3); i++) {
    tone(BUZZER, 1400, 30); delay(80);
  }
}

// ─────────────────────────────────────────────────────────────
//  TIME helpers
// ─────────────────────────────────────────────────────────────

unsigned long taskTotalMs(int i) {
  unsigned long t = tasks[i].accumulatedMs;
  if (tasks[i].running) t += millis() - tasks[i].sessionStartMs;
  return t;
}

void fmtHMS(unsigned long ms, char* buf) {
  unsigned long s = ms / 1000;
  unsigned long h = s / 3600; s %= 3600;
  unsigned long m = s / 60;   s %= 60;
  sprintf(buf, "%02lu:%02lu:%02lu", h, m, s);
}

void fmtCs(unsigned long ms, char* buf) {
  sprintf(buf, ".%02lu", (ms % 1000) / 10);
}

// ─────────────────────────────────────────────────────────────
//  NVS  save / load
// ─────────────────────────────────────────────────────────────

void saveAll() {
  prefs.begin("tb", false);
  prefs.putUInt("count", (uint32_t)taskCount);
  char key[16];
  for (int i = 0; i < taskCount; i++) {
    sprintf(key, "t%dname", i); prefs.putString(key, tasks[i].name);
    sprintf(key, "t%dsecs", i); prefs.putUInt(key, (uint32_t)(taskTotalMs(i) / 1000));
    sprintf(key, "t%ddone", i); prefs.putUChar(key, tasks[i].complete ? 1 : 0);
  }
  prefs.end();
  Serial.println("[NVS] saved");
}
void handleClear() {
    // Stop any running timers first
    for (int i = 0; i < taskCount; i++) {
        if (tasks[i].running) {
            tasks[i].accumulatedMs += millis() - tasks[i].sessionStartMs;
            tasks[i].running = false;
        }
    }

    // Wipe RAM
    taskCount  = 0;
    activeTask = -1;
    for (int i = 0; i < MAX_TASKS; i++) {
        tasks[i] = Task(); // zero-init
    }

    // Wipe NVS
    prefs.begin("tb", false);
    prefs.clear();          // removes ALL keys in this namespace
    prefs.putUInt("count", 0);
    prefs.end();

    // Reset UI
    currentScreen = SCR_LIST;
    listCursor    = 0;
    drawList();

    // Confirmation beep (descending)
    tone(BUZZER, 1000, 60); delay(80);
    tone(BUZZER, 700,  60); delay(80);
    tone(BUZZER, 400,  100);

    Serial.println("[CLEAR] All tasks wiped from RAM and NVS");
    server.send(200, "application/json", "{\"ok\":true,\"message\":\"cleared\"}");
}
void loadAll() {
  prefs.begin("tb", true);
  uint32_t n = prefs.getUInt("count", 0);
  taskCount = (int)min((uint32_t)MAX_TASKS, n);
  char key[16];
  for (int i = 0; i < taskCount; i++) {
    sprintf(key, "t%dname", i);
    String nm = prefs.getString(key, "Task");
    strncpy(tasks[i].name, nm.c_str(), 47);
    tasks[i].name[47] = '\0';
    sprintf(key, "t%dsecs", i);
    tasks[i].accumulatedMs = (unsigned long)prefs.getUInt(key, 0) * 1000UL;
    sprintf(key, "t%ddone", i);
    tasks[i].complete       = prefs.getUChar(key, 0) == 1;
    tasks[i].running        = false;
    tasks[i].sessionStartMs = 0;
  }
  prefs.end();
  Serial.printf("[NVS] loaded %d tasks\n", taskCount);
}

// ─────────────────────────────────────────────────────────────
//  OLED screens
// ─────────────────────────────────────────────────────────────

// ── Screen 1: Task list ──────────────────────────────────────
void drawList() {
  oled.clearDisplay();
  oled.setTextSize(1);
  oled.setTextColor(SSD1306_WHITE);

  // inverted header bar
  oled.fillRect(0, 0, SCREEN_W, 10, SSD1306_WHITE);
  oled.setTextColor(SSD1306_BLACK);
  oled.setCursor(2, 1);
  oled.print("TaskBot");
  char hdr[8]; sprintf(hdr, "%d/%d", taskCount, MAX_TASKS);
  oled.setCursor(SCREEN_W - strlen(hdr) * 6 - 2, 1);
  oled.print(hdr);
  oled.setTextColor(SSD1306_WHITE);

  if (taskCount == 0) {
    oled.setCursor(0, 16); oled.print("No tasks.");
    oled.setCursor(0, 28); oled.print("Connect to WiFi:");
    oled.setCursor(0, 40); oled.print("  TaskBot");
    oled.setCursor(0, 52); oled.print("  192.168.4.1");
    oled.display();
    return;
  }

  // 4 visible rows, scroll to keep cursor visible
  static int scrollOff = 0;
  if (listCursor < scrollOff) scrollOff = listCursor;
  if (listCursor >= scrollOff + 4) scrollOff = listCursor - 3;

  for (int r = 0; r < 4; r++) {
    int idx = scrollOff + r;
    if (idx >= taskCount) break;

    int  y   = 11 + r * 13;
    bool sel = (idx == listCursor);

    if (sel) {
      oled.fillRect(0, y, SCREEN_W - 8, 12, SSD1306_WHITE);
      oled.setTextColor(SSD1306_BLACK);
    } else {
      oled.setTextColor(SSD1306_WHITE);
    }

    oled.setCursor(2, y + 2);

    // cursor / running indicator
    if      (tasks[idx].running) oled.print(">");
    else if (sel)                oled.print("\x10"); // ► char
    else                         oled.print(" ");

    // task number
    char num[4]; sprintf(num, "%d.", idx + 1);
    oled.print(num);

    // name — leave room for time on right (7 chars = 42px)
    char name[13]; strncpy(name, tasks[idx].name, 12); name[12] = '\0';
    oled.print(name);

    // time on right edge if task has been worked on
    if (tasks[idx].accumulatedMs > 0 || tasks[idx].running) {
      unsigned long s = taskTotalMs(idx) / 1000;
      char t[8];
      if (s < 3600) sprintf(t, "%02lu:%02lu", s / 60, s % 60);
      else {
        unsigned long h = s / 3600; s %= 3600;
        sprintf(t, "%luh%02lu", h, s / 60);
      }
      oled.setCursor(SCREEN_W - strlen(t) * 6 - 8, y + 2);
      oled.print(t);
    }

    // strikethrough line for completed tasks
    if (tasks[idx].complete) {
      uint16_t col = sel ? SSD1306_BLACK : SSD1306_WHITE;
      oled.drawLine(2, y + 6, SCREEN_W - 10, y + 6, col);
    }

    oled.setTextColor(SSD1306_WHITE);
  }

  // scroll indicator (right edge dots)
  if (taskCount > 4) {
    for (int i = 0; i < taskCount; i++) {
      int dy = 11 + i * (52 / taskCount);
      if (i == listCursor) oled.fillRect(126, dy, 2, 3, SSD1306_WHITE);
      else                  oled.drawPixel(127, dy + 1, SSD1306_WHITE);
    }
  }

  // bottom hint
  oled.setCursor(0, 57);
  oled.print("^v:scroll  SEL:open");

  oled.display();
}

// ── Screen 2: Stopwatch session ──────────────────────────────
void drawSession() {
  if (activeTask < 0) return;
  Task& t  = tasks[activeTask];
  unsigned long ms = taskTotalMs(activeTask);

  oled.clearDisplay();
  oled.setTextColor(SSD1306_WHITE);
  oled.setTextSize(1);

  // task name centred at top
  char sname[19]; strncpy(sname, t.name, 17); sname[17] = '\0';
  oled.setCursor(max(0, (SCREEN_W - (int)strlen(sname) * 6) / 2), 0);
  oled.print(sname);

  // PAUSED badge top-right
  if (!t.running) {
    oled.fillRect(93, 0, 35, 9, SSD1306_WHITE);
    oled.setTextColor(SSD1306_BLACK);
    oled.setCursor(95, 1);
    oled.print("PAUSED");
    oled.setTextColor(SSD1306_WHITE);
  }

  // ── big HH:MM:SS (size 2) ──
  char hms[12]; fmtHMS(ms, hms);
  oled.setTextSize(2);
  int bw = strlen(hms) * 12;
  oled.setCursor((SCREEN_W - bw) / 2, 10);
  oled.print(hms);

  // ── centiseconds (size 1, right-aligned under clock) ──
  char cs[5]; fmtCs(ms, cs);
  oled.setTextSize(1);
  oled.setCursor(SCREEN_W - strlen(cs) * 6 - 1, 28);
  oled.print(cs);

  // ── icon row  y=36..50 ────────────────────────────────────
  int iy = 36;

  // LEFT: triangle ▶ (paused/idle) or bars ‖ (running)
  if (!t.running) {
    // filled right-pointing triangle
    oled.fillTriangle(18, iy, 18, iy + 10, 27, iy + 5, SSD1306_WHITE);
    oled.setCursor(14, iy + 12); oled.print("START");
  } else {
    // two solid vertical bars
    oled.fillRect(16, iy,     4, 10, SSD1306_WHITE);
    oled.fillRect(23, iy,     4, 10, SSD1306_WHITE);
    oled.setCursor(13, iy + 12); oled.print("PAUSE");
  }

  // centre dotted divider
  for (int py = iy; py <= iy + 10; py += 2)
    oled.drawPixel(64, py, SSD1306_WHITE);

  // RIGHT: tick ✓
  oled.drawLine(88, iy + 6, 92, iy + 11, SSD1306_WHITE);
  oled.drawLine(89, iy + 6, 93, iy + 11, SSD1306_WHITE);
  oled.drawLine(92, iy + 11, 103, iy,    SSD1306_WHITE);
  oled.drawLine(93, iy + 11, 104, iy,    SSD1306_WHITE);
  oled.setCursor(88, iy + 12); oled.print("DONE");

  // bottom hint
  oled.setCursor(0, 57);
  oled.print("^v:back  LONG:done");

  oled.display();
}

// ── Screen 3: Complete splash ─────────────────────────────────
void drawDone(int idx) {
  oled.clearDisplay();
  oled.setTextColor(SSD1306_WHITE);

  oled.setTextSize(2);
  oled.setCursor(20, 2);
  oled.print("* DONE!");

  oled.setTextSize(1);
  char name[22]; strncpy(name, tasks[idx].name, 20); name[20] = '\0';
  int nx = max(0, (SCREEN_W - (int)strlen(name) * 6) / 2);
  oled.setCursor(nx, 28);
  oled.print(name);
  oled.drawLine(nx, 32, nx + strlen(name) * 6, 32, SSD1306_WHITE);

  char hms[12]; fmtHMS(taskTotalMs(idx), hms);
  oled.setCursor(12, 42);
  oled.print("Total: "); oled.print(hms);

  oled.setCursor(26, 56);
  oled.print("back in 3s...");

  oled.display();
}

// ─────────────────────────────────────────────────────────────
//  Session actions
// ─────────────────────────────────────────────────────────────

void enterSession(int idx) {
  activeTask    = idx;
  currentScreen = SCR_SESSION;
  bEnterSession();
  drawSession();
  Serial.printf("[SESSION] %s\n", tasks[idx].name);
}

void toggleTimer(int idx) {
  if (tasks[idx].running) {
    tasks[idx].accumulatedMs += millis() - tasks[idx].sessionStartMs;
    tasks[idx].running = false;
    bTimerPause();
  } else {
    tasks[idx].sessionStartMs = millis();
    tasks[idx].running = true;
    bTimerStart();
  }
  saveAll();
  drawSession();
}

void markComplete(int idx) {
  if (tasks[idx].running) {
    tasks[idx].accumulatedMs += millis() - tasks[idx].sessionStartMs;
    tasks[idx].running = false;
  }
  tasks[idx].complete = true;
  saveAll();
  bComplete();
  currentScreen = SCR_DONE;
  doneAt = millis();
  drawDone(idx);
  Serial.printf("[DONE] %s  %lus\n", tasks[idx].name, tasks[idx].accumulatedMs / 1000);
}

// ─────────────────────────────────────────────────────────────
//  Button handler  —  INPUT_PULLUP  →  PRESSED = LOW
// ─────────────────────────────────────────────────────────────

void handleButtons() {
  unsigned long now = millis();

  bool upRaw   = digitalRead(BTN_UP)   == LOW;
  bool downRaw = digitalRead(BTN_DOWN) == LOW;
  bool selRaw  = digitalRead(BTN_SEL)  == LOW;

  // ── UP ──────────────────────────────────────────────────
  if (upRaw) {
    if (!bUp.held) { bUp.held = true; bUp.pressedAt = now; }
  } else {
    if (bUp.held &&
        now - bUp.pressedAt   < LONGPRESS &&
        now - bUp.lastFiredAt > DEBOUNCE) {
      bUp.lastFiredAt = now;
      bClick();
      if (currentScreen == SCR_LIST && taskCount > 0) {
        listCursor = (listCursor - 1 + taskCount) % taskCount;
        drawList();
      } else if (currentScreen == SCR_SESSION) {
        if (tasks[activeTask].running) {
          tasks[activeTask].accumulatedMs += millis() - tasks[activeTask].sessionStartMs;
          tasks[activeTask].running = false;
          saveAll();
        }
        bBack();
        currentScreen = SCR_LIST;
        drawList();
      }
    }
    bUp.held = false;
  }

  // ── DOWN ────────────────────────────────────────────────
  if (downRaw) {
    if (!bDown.held) { bDown.held = true; bDown.pressedAt = now; }
  } else {
    if (bDown.held &&
        now - bDown.pressedAt   < LONGPRESS &&
        now - bDown.lastFiredAt > DEBOUNCE) {
      bDown.lastFiredAt = now;
      bClick();
      if (currentScreen == SCR_LIST && taskCount > 0) {
        listCursor = (listCursor + 1) % taskCount;
        drawList();
      } else if (currentScreen == SCR_SESSION) {
        if (tasks[activeTask].running) {
          tasks[activeTask].accumulatedMs += millis() - tasks[activeTask].sessionStartMs;
          tasks[activeTask].running = false;
          saveAll();
        }
        bBack();
        currentScreen = SCR_LIST;
        drawList();
      }
    }
    bDown.held = false;
  }

  // ── SEL  short = select/toggle   long = complete ────────
  if (selRaw) {
    if (!bSel.held) { bSel.held = true; bSel.pressedAt = now; }

    // long press fires while still held
    if (bSel.held &&
        now - bSel.pressedAt   >= LONGPRESS &&
        now - bSel.lastFiredAt >  LONGPRESS) {
      bSel.lastFiredAt = now;
      if (currentScreen == SCR_SESSION) {
        markComplete(activeTask);
      }
    }
  } else {
    if (bSel.held) {
      if (now - bSel.pressedAt   <  LONGPRESS &&
          now - bSel.lastFiredAt >  DEBOUNCE) {
        bSel.lastFiredAt = now;
        if (currentScreen == SCR_LIST && taskCount > 0) {
          if (tasks[listCursor].complete) {
            bError();                    // already done
          } else {
            enterSession(listCursor);
          }
        } else if (currentScreen == SCR_SESSION) {
          toggleTimer(activeTask);
        }
      }
      bSel.held = false;
    }
  }
}

// ─────────────────────────────────────────────────────────────
//  HTTP handlers
// ─────────────────────────────────────────────────────────────

void handleRoot() {
  String html =
    "<!DOCTYPE html><html><head>"
    "<meta name='viewport' content='width=device-width,initial-scale=1'>"
    "<meta http-equiv='refresh' content='3'>"
    "<style>"
    "body{font-family:monospace;background:#0f1117;color:#eee;padding:16px;margin:0}"
    "h2{color:#4ade80;margin:0 0 4px}"
    "p{color:#555;font-size:12px;margin:0 0 12px}"
    ".t{background:#1a1d27;border-radius:8px;padding:10px 14px;margin:6px 0;"
        "display:flex;align-items:center;gap:10px}"
    ".done .nm{text-decoration:line-through;color:#444}"
    ".tm{color:#4ade80;margin-left:auto;font-size:13px}"
    ".run{background:#422006;color:#facc15;font-size:11px;"
         "padding:2px 7px;border-radius:4px}"
    ".ok {background:#052e16;color:#4ade80;font-size:11px;"
         "padding:2px 7px;border-radius:4px}"
    "</style></head><body>"
    "<h2>TaskBot</h2>"
    "<p>Auto-refresh 3s &nbsp;|&nbsp; "
    "<a href='/tasks' style='color:#4F8EF7'>JSON</a></p>";

  for (int i = 0; i < taskCount; i++) {
    char hms[12]; fmtHMS(taskTotalMs(i), hms);
    html += "<div class='t" + String(tasks[i].complete ? " done" : "") + "'>";
    html += "<span style='color:#555'>" + String(i + 1) + ".</span>";
    html += "<span class='nm'>" + String(tasks[i].name) + "</span>";
    if (tasks[i].running)        html += "<span class='run'>RUNNING</span>";
    else if (tasks[i].complete)  html += "<span class='ok'>DONE</span>";
    html += "<span class='tm'>" + String(hms) + "</span>";
    html += "</div>";
  }
  if (taskCount == 0)
    html += "<p style='color:#555;margin-top:20px'>No tasks yet — POST to /tasks</p>";
  html += "</body></html>";
  server.send(200, "text/html", html);
}

void handlePostTasks() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"no body\"}");
    return;
  }
  StaticJsonDocument<2048> doc;
  if (deserializeJson(doc, server.arg("plain"))) {
    server.send(400, "application/json", "{\"error\":\"bad json\"}");
    return;
  }
  JsonArray arr = doc.as<JsonArray>();
  int added = 0;
  for (JsonObject obj : arr) {
    if (taskCount >= MAX_TASKS) break;
    const char* n = obj["name"] | "";
    if (!n || strlen(n) == 0) continue;
    // skip duplicates
    bool dup = false;
    for (int i = 0; i < taskCount; i++)
      if (strncmp(tasks[i].name, n, 47) == 0) { dup = true; break; }
    if (dup) continue;

    strncpy(tasks[taskCount].name, n, 47);
    tasks[taskCount].name[47]       = '\0';
    tasks[taskCount].complete       = false;
    tasks[taskCount].running        = false;
    tasks[taskCount].sessionStartMs = 0;
    tasks[taskCount].accumulatedMs  = 0;
    taskCount++;
    added++;
  }
  saveAll();
  if (currentScreen == SCR_LIST) drawList();
  bTaskReceived(added);

  server.send(200, "application/json",
    "{\"ok\":true,\"added\":" + String(added) +
    ",\"total\":"             + String(taskCount) + "}");
  Serial.printf("[POST] added=%d total=%d\n", added, taskCount);
}

void handleGetTasks() {
  String out = "[";
  for (int i = 0; i < taskCount; i++) {
    if (i > 0) out += ",";
    char hms[12]; fmtHMS(taskTotalMs(i), hms);
    unsigned long ms = taskTotalMs(i);
    out += "{\"id\":"      + String(i)
        + ",\"name\":\""   + String(tasks[i].name) + "\""
        + ",\"done\":"     + (tasks[i].complete ? "true" : "false")
        + ",\"running\":"  + (tasks[i].running  ? "true" : "false")
        + ",\"totalMs\":"  + String(ms)
        + ",\"totalSecs\":"+ String(ms / 1000)
        + ",\"hms\":\""    + String(hms) + "\"}";
  }
  out += "]";
  server.send(200, "application/json", out);
}

// ─────────────────────────────────────────────────────────────
//  SETUP
// ─────────────────────────────────────────────────────────────

void setup() {
  Serial.begin(115200);
  Serial.println("\n[TaskBot] v4 boot");

  // buttons — INPUT_PULLUP, pressed = LOW
  pinMode(BTN_UP,   INPUT_PULLUP);
  pinMode(BTN_DOWN, INPUT_PULLUP);
  pinMode(BTN_SEL,  INPUT_PULLUP);

  // buzzer pin
  pinMode(BUZZER, OUTPUT);
  digitalWrite(BUZZER, LOW);

  // OLED
  if (!oled.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR)) {
    Serial.println("[OLED] not found");
  }
  oled.clearDisplay();
  oled.setTextColor(SSD1306_WHITE);
  oled.setTextSize(2);
  oled.setCursor(6, 6);  oled.print("TaskBot");
  oled.setTextSize(1);
  oled.setCursor(6, 38); oled.print("Loading...");
  oled.display();

  // boot beep
  tone(BUZZER, 800,  80); delay(110);
  tone(BUZZER, 1200, 100);
  delay(200);

  // NVS
  loadAll();

  // demo task if nothing saved yet
  if (taskCount == 0) {
    strncpy(tasks[0].name, "Stopwatch demo", 47);
    tasks[0].complete = false; tasks[0].running = false;
    tasks[0].sessionStartMs = 0; tasks[0].accumulatedMs = 0;
    taskCount = 1;
    saveAll();
    Serial.println("[DEMO] added stopwatch demo task");
  }

  // WiFi AP
  oled.setCursor(6, 50); oled.print("Starting WiFi...");
  oled.display();
  WiFi.softAP(AP_SSID, AP_PASS);
  Serial.printf("[WiFi] SSID:%s  IP:%s\n", AP_SSID,
                WiFi.softAPIP().toString().c_str());

  // routes
  server.on("/",       HTTP_GET,  handleRoot);
  server.on("/tasks",  HTTP_POST, handlePostTasks);
  server.on("/tasks",  HTTP_GET,  handleGetTasks);
  server.on("/status", HTTP_GET,  []() {
    server.send(200, "application/json",
      "{\"ok\":true,\"tasks\":" + String(taskCount) + "}");
  });
  server.on("/clear", HTTP_POST, handleClear);
  server.onNotFound([]() { server.send(404, "text/plain", "not found"); });
  server.begin();
  Serial.println("[HTTP] ready");

  delay(300);
  drawList();
}

// ─────────────────────────────────────────────────────────────
//  LOOP
// ─────────────────────────────────────────────────────────────

void loop() {
  server.handleClient();
  handleButtons();

  // auto-dismiss done screen after 3s
  if (currentScreen == SCR_DONE && millis() - doneAt > 3000) {
    currentScreen = SCR_LIST;
    listCursor    = 0;
    drawList();
  }

  // refresh session every 50ms so centiseconds tick smoothly
  static unsigned long lastDraw = 0;
  if (currentScreen == SCR_SESSION && millis() - lastDraw >= 50) {
    lastDraw = millis();
    drawSession();
  }

  // periodic NVS save every 30s while a timer is running
  static unsigned long lastSave = 0;
  if (millis() - lastSave >= 30000) {
    lastSave = millis();
    for (int i = 0; i < taskCount; i++) {
      if (tasks[i].running) { saveAll(); break; }
    }
  }
}
