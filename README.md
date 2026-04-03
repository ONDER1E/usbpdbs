# USB PD BS - Univeral Serial Bus Power Delivery Bypass Script

A native Android app that monitors battery level and automatically toggles **USB PD battery bypass (pass-through) mode** via the **Shizuku API**.

This is the native successor to [tbp](https://github.com/ONDER1E/tbp), which accomplished the same goal using shell scripts in Termux. This version runs as a proper Android foreground service and includes a "Self-Healing" recovery system to maintain the bypass state even if Shizuku or WiFi drops.

---

## Why This Exists

The original shell-based tbp worked well, but had fundamental limitations:
- Required Termux and a manually acquired wakelock to stay alive.
- The wakelock prevented the SoC from entering deep sleep states.
- Battery reads relied on spawning `dumpsys` subprocesses every 5 seconds.
- No boot autostart without Termux:Boot.

This app replaces all of that with a native, **event-driven** architecture that allows the CPU to sleep between battery updates.

---

## How It Works

Instead of polling every 5 seconds, the app registers for Android's `ACTION_BATTERY_CHANGED` broadcast. The CPU is not woken on a timer — it only wakes when the OS broadcasts a change in battery state.

1. **Listen:** The service waits for the `ACTION_BATTERY_CHANGED` event.
2. **Evaluate:** Compares the current level against user-defined **Enable/Disable** thresholds (Hysteresis).
3. **Execute:** Calls `settings put system pass_through <0|1>` via Shizuku IPC.
4. **Recover:** If Shizuku is disconnected, the app uses a local `rish` binary and Termux `RunCommand` to automatically restore the environment.

---

## Features

- **Event-Driven:** Zero polling; the SoC can enter deep sleep between events.
- **Self-Healing:** Automated Shizuku/WiFi recovery using `rish`.
- **Hysteresis Zone:** Separate thresholds to prevent rapid toggle "flapping."
- **Live Logs:** Real-time monitoring of service logic and system events.
- **Boot Autostart:** Automatically starts the monitor on device boot.

---

## Build Tutorial

### Prerequisites
- **JDK 17** or higher.
- **Android SDK** (Command line tools or Android Studio).

### Build Steps (Windows)
1. Clone the repository:
   ```powershell
   git clone [https://github.com/ONDER1E/usbpdbs.git](https://github.com/ONDER1E/usbpdbs.git)
   cd tbp-android

2.  Build the Debug APK:
    ```powershell
    .\gradlew.bat assembleDebug
    ```
3.  Locate your APK at: `app/build/outputs/apk/debug/app-debug.apk`

-----

## Initial Setup (Required)

To enable "Self-Healing" and allow the app to work without a PC:

1.  **Rish Files:** Place `rish` and `rish_shizuku.dex` in the root of your `/sdcard/`.
2.  **Permissions Tab:**
      - Tap **Copy Rish from /sdcard** to move them to internal storage.
      - Tap **Grant via Shizuku** under Write Secure Settings.
3.  **Battery:** Set usb pd bd to **Unrestricted** battery usage.

-----

## Device Compatibility

This app uses the same `pass_through` system setting as the original shell scripts.

  - **Samsung:** S22/S23/S24 series (via "Pause USB Power Delivery").
  - **ASUS:** ROG Phone / Zenfone (Bypass Charging).
  - **Sony:** Xperia (H.S. Power Control).
  - **Others:** RedMagic, Black Shark, Lenovo Legion.

-----

## FAQ

#### **Q: Does this require Root?**

**A:** No. It only requires **Shizuku**, which can be started via Wireless Debugging (ADB).

#### **Q: Why do I need Termux for "Recovery"?**

**A:** Shizuku often stops after reboots or WiFi toggles. The app uses Termux's `RunCommand` API to trigger a script that restarts the Shizuku environment and WiFi without needing a PC.

#### **Q: My device isn't reacting to the toggle.**

**A:** Ensure your charger supports **USB-PD PPS**. Standard "Fast Chargers" may not support the PD protocols required for system-level bypass.

#### **Q: Is this safe for my battery?**

**A:** Yes. It reduces heat by preventing the battery from charging/discharging while the device is under high load (gaming/navigation), which preserves long-term health.

#### **Q: How do I know it's working?**

**A:** Check the "Live Logs" tab. You will see "Setting pass\_through to 1" when the enable threshold is hit. On many devices, the battery icon will show "Plugged in, not charging."

-----

## Differences from tbp (Shell Version)

| Feature | tbp (shell) | tbp-android (this) |
|---|---|---|
| **Monitoring Method** | 5s Polling Loop | **Event-Driven (`ACTION_BATTERY_CHANGED`)** |
| **CPU State** | Constant Wake | **Deep Sleep between events** |
| **Recovery** | Manual | **Automated via Rish/Termux** |
| **UI** | CLI / Notifications | **Full GUI + Live Logs** |
| **Boot Autostart** | Termux:Boot | **Native `BOOT_COMPLETED`** |

-----

## Safety Disclaimer

This app modifies system-level power settings. Use at your own risk. Improper thresholds or unsupported hardware may lead to battery degradation or unexpected behaviour.

-----

## Known issues

There are known issues regarding the permissions of the following:
- Write Secure Settings
- Rish Setup
Solutions can be found [here](https://github.com/ONDER1E/usbpdbs/blob/main/KNOWN_ISSUES.md)


-----

## Licence

See [LICENCE](https://github.com/ONDER1E/usbpdbs/blob/main/LICENCE).