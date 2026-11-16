# FocusBlocker 🛡️

FocusBlocker is a native Android application built in Java to help students and professionals eliminate distractions during dedicated focus hours. It monitors foreground applications using `UsageStatsManager` and overlays an unskippable block screen whenever a restricted application (such as Instagram, Facebook, or mobile games) is launched past a scheduled start time. Users can grant themselves a temporary pause (revoke) for up to 30 minutes.

---

## 🏛️ Architecture & Viva Explanation

This project is built using standard Android core components:

```
┌─────────────────────────────────────────────────────────────┐
│                       FocusBlocker                          │
├─────────────────────┬───────────────────┬───────────────────┤
│    MainActivity     │ AppSelectActivity │   BlockActivity   │
│  (Config & Status)  │ (App Picker List) │ (Full-screen Lock)│
└──────────┬──────────┴─────────┬─────────┴─────────▲─────────┘
           │                    │                   │ Starts Intent
           ▼                    ▼                   │ (FLAG_ACTIVITY_NEW_TASK)
┌─────────────────────────────────────────┐         │
│           SharedPreferences             │         │
│  - is_blocking_enabled                  │         │
│  - start_hour / start_minute            │         │
│  - blocked_packages (Set<String>)       │         │
│  - revoke_until (timestamp ms)          │         │
└────────────────────┬────────────────────┘         │
                     │ Reads Config                 │
                     ▼                              │
┌─────────────────────────────────────────┐         │
│              BlockService               │─────────┘
│          (Foreground Service)           │
│  - Runs loop every 1 second             │
│  - UsageStatsManager.queryEvents()      │
│  - Checks:                              │
│      1. isBlockingEnabled == true       │
│      2. currentTime >= startTime        │
│      3. now > revokeUntil               │
│      4. foregroundApp in blockedList    │
│      5. Not own app and not launcher    │
└─────────────────────────────────────────┘
                     ▲
                     │ Starts Service on Reboot
┌────────────────────┴────────────────────┐
│              BootReceiver               │
│   (BroadcastReceiver: BOOT_COMPLETED)   │
└─────────────────────────────────────────┘
```

### 1. Foreground Service (`BlockService`)
- **What is it?** A Service is an Android application component that can perform long-running operations in the background without a user interface.
- **Why a Foreground Service?** Standard background services are heavily restricted in modern Android (API 26+) and will be killed after a few minutes. A Foreground Service displays a persistent system notification (`startForeground`) giving it high priority so the OS does not terminate it.
- **Android 14 (API 34) Compliance:** Android 14 mandates foreground service types. FocusBlocker specifies `android:foregroundServiceType="specialUse"` and declares `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Focus blocking and app restriction" />` in `AndroidManifest.xml`.

### 2. UsageStatsManager & Continuous Foreground App Detection
- **What is it?** Android's official API to access device usage history and app switching events.
- **Why a simple 10-second query fails for continuous usage:**
  Android generates an `ACTIVITY_RESUMED` event *only* when an application window is launched or resumed. If a user is already actively browsing Instagram for 5 minutes when the block start time arrives, no new `ACTIVITY_RESUMED` event occurred in the last 10 seconds.
- **How FocusBlocker solves this:**
  `BlockService` maintains persistent knowledge of the currently active foreground app (`currentForegroundApp`). It checks `UsageEvents` every 1 second to capture new app transitions. If no new app was launched, it knows the user is still in the active application. When the block start time strikes, it immediately intercepts and blocks the running app without requiring the user to leave and reopen it.

### 3. Intents
- **Explicit Intents:** Used to start known internal components within the app:
  - `new Intent(this, AppSelectActivity.class)`: Opens the app selection screen.
  - `new Intent(this, BlockService.class)`: Starts the monitoring service via `ContextCompat.startForegroundService()`.
  - `new Intent(this, BlockActivity.class)`: Launched from `BlockService` with `FLAG_ACTIVITY_NEW_TASK` to bring the blocking screen on top of the distracting app.
- **Implicit Intents:** Request an action from the Android OS:
  - `Settings.ACTION_USAGE_ACCESS_SETTINGS`: Opens system Settings for Usage Access.
  - `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`: Opens system Settings for "Display over other apps".
  - `Intent.ACTION_MAIN` with `Intent.CATEGORY_HOME`: Sends the user back to their Android home screen when they tap "Go Home" or try to press Back on `BlockActivity`.

### 4. SharedPreferences
- **What is it?** A lightweight key-value data storage mechanism saved as an XML file in the application's private sandbox directory (`/data/data/com.example.focusblocker/shared_prefs/`).
- **Data stored:**
  - `is_blocking_enabled` (boolean): Whether blocking is toggled ON.
  - `start_hour` / `start_minute` (int): Time when blocking begins.
  - `blocked_packages` (`Set<String>`): Set of package names chosen by the user.
  - `revoke_until` (long): Timestamp in milliseconds until which blocking is temporarily bypassed.

### 5. BroadcastReceiver (`BootReceiver`)
- Listens for the `android.intent.action.BOOT_COMPLETED` system broadcast.
- When the phone reboots, it reads `is_blocking_enabled` from `SharedPreferences`. If enabled, it automatically restarts `BlockService` so the user remains protected without needing to open the app manually.

### 6. Dynamic Schedule Synchronization
- **TimePicker AM/PM & Real-time Listener:** `TimePicker.setOnTimeChangedListener()` immediately persists user adjustments to `SharedPreferences` without requiring a service restart.
- **Dynamic Status Display:** `MainActivity` checks whether `currentTime >= startTime` in real-time every second, showing users whether blocking is waiting for a future scheduled time or actively enforcing restrictions.

---

## 🚀 Building from Command Line

This project requires JDK 17 and Android SDK Platform 34.

Build the debug APK:
```bash
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Installation on a USB-Connected Phone

1. Enable **Developer Options** and **USB Debugging** on your Android phone.
2. Connect your phone to your PC via USB cable.
3. Verify your device is detected:
   ```bash
   adb devices
   ```
   *(Ensure your device shows up as `device`, not `unauthorized`. If unauthorized, accept the USB debugging prompt on your phone).*
4. Install the debug APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## ⚙️ Manual Steps to Complete on Your Phone

After installing and launching FocusBlocker for the first time:

1. **Grant Usage Access**:
   - In `MainActivity`, tap **Grant** next to *Usage Access*.
   - Find **FocusBlocker** in the list and toggle the switch to **Allow usage tracking**.
2. **Grant Display Over Other Apps**:
   - Tap **Grant** next to *Display Over Other Apps*.
   - Toggle the switch to **Allow display over other apps**.
3. **Grant Notification Permission (Android 13+)**:
   - Tap **Grant** next to *Post Notifications* or accept the system permission dialog.
4. **Disable Battery Optimization (Crucial for background services)**:
   - Go to phone **Settings** > **Apps** > **FocusBlocker** > **Battery** (or *App Battery Usage*).
   - Change from *Optimized* to **Unrestricted** (or *Don't optimize*).
   - *Why?* Aggressive battery saver software (MIUI, OxygenOS, Samsung OneUI) kills background services if not set to Unrestricted.
