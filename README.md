# TBP Android — USB PD Bypass Monitor

A native Android app that monitors battery level and automatically toggles **USB PD battery bypass (pass-through) mode** via the **Shizuku API**.

This is the native successor to [tbp](https://github.com/ONDER1E/tbp). It runs as a proper Android foreground service and includes a "Self-Healing" recovery system to maintain the bypass state even if Shizuku or WiFi drops.

---

## Why This Exists

The original shell-based tbp relied on a 5-second polling loop, which kept the CPU active. This app replaces that with a native, event-driven architecture:
- **Zero Polling:** The app does not "check" the battery on a timer. It waits for the Android OS to notify it of changes.
- **Deep Sleep Friendly:** Allows the SoC to enter deep sleep states between battery events, significantly reducing background power consumption.
- **Native IPC:** Integrates directly with the Shizuku API for privileged system calls.

---

## How It Works

The app utilizes a Foreground Service that remains idle until triggered by system events.

1. **Event Listening:** The service registers a receiver for `ACTION_BATTERY_CHANGED`. 
2. **Reactive Logic:** When the battery level changes, the app wakes up, compares the new level against your **Enable/Disable** thresholds, and decides if action is needed.
3. **Execution:** Toggles `settings put system pass_through <0|1>` via Shizuku.
4. **Self-Healing:** If the Shizuku connection is lost (e.g., after a reboot or WiFi toggle), the app uses a local `rish` binary and Termux `RunCommand` to automatically restore the environment.

---

## Build Tutorial

### Prerequisites
- **JDK 17** or higher.
- **Android SDK** (Command line tools or Android Studio).

### Build Steps (Windows)
1. Clone the repository:
   ```powershell
   git clone [https://github.com/ONDER1E/tbp-android.git](https://github.com/ONDER1E/tbp-android.git)
   cd tbp-android