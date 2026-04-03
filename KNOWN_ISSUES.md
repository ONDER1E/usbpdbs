## 🛠 Troubleshooting: Manual Rish & Permission Setup
In some environments (notably Android 13+, Samsung OneUI, or Xiaomi MIUI), the in-app "Grant via Shizuku" or "Copy Rish" buttons may fail with a **Permission Denied** error. This occurs because the app process is restricted from accessing the filesystem directly, even via `run-as`.

If the in-app setup fails, follow these steps using a PC and USB Debugging.

### Prerequisites
1.  Ensure **USB Debugging** is enabled on the device.
2.  Place the `rish` and `rish_shizuku.dex` files on the device's internal storage (e.g., `/sdcard/`).
3.  Ensure the app (`com.onder1e.usbpdbs`) is installed as a **debuggable** build.

### Step 1: Grant WRITE_SECURE_SETTINGS
Before setting up Rish, grant the core permission directly via ADB:
```bash
adb shell pm grant com.onder1e.usbpdbs android.permission.WRITE_SECURE_SETTINGS
```

### Step 2: Manual Rish Injection (The "Pipe" Method)
If the app cannot copy the Rish files itself, you must stream the data into the app's private directory. Run these commands in sequence:

#### A. Stage and Prepare
Move the files to a neutral zone and make them globally readable so the `run-as` process can see them.
```powershell
adb shell "cp /sdcard/rish /data/local/tmp/ && cp /sdcard/rish_shizuku.dex /data/local/tmp/"
adb shell "chmod 666 /data/local/tmp/rish /data/local/tmp/rish_shizuku.dex"
```

#### B. Stream Data to App Private Storage
We use `cat` to read the file and pipe it into a `run-as` shell to bypass SELinux copy restrictions.
```powershell
adb shell "cat /data/local/tmp/rish | run-as com.onder1e.usbpdbs sh -c 'cat > ./files/rish'"
adb shell "cat /data/local/tmp/rish_shizuku.dex | run-as com.onder1e.usbpdbs sh -c 'cat > ./files/rish_shizuku.dex'"
```

#### C. Set Execution Permissions
The `rish` script must be executable for the app's `ProcessBuilder` to trigger it.
```powershell
adb shell "run-as com.onder1e.usbpdbs chmod 755 ./files/rish"
adb shell "run-as com.onder1e.usbpdbs chmod 644 ./files/rish_shizuku.dex"
```

#### D. Cleanup
Remove the temporary files from the public folder.
```powershell
adb shell rm /data/local/tmp/rish /data/local/tmp/rish_shizuku.dex
```

---

### 🔍 Why this works
| Method | Why it fails | Why the manual fix works |
| :--- | :--- | :--- |
| **In-App Copy** | The App's sandbox prevents it from "reaching out" to `/sdcard` without user-granted "All Files Access." | We use ADB (which has higher shell privileges) to move files into a neutral zone first. |
| **`run-as cp`** | SELinux blocks the `cp` command from crossing security contexts (Temp -> App). | **Piping** (`cat | cat >`) creates a new file *inside* the destination context, which SELinux views as a native app action. |

> **Note:** Once these commands are run, restart the app. The "Rish: Ready" and "Secure Settings: Granted" indicators should now be green.