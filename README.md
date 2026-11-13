# FocusGuard - Attendance App with Non-Accessibility App Blocker

A clean Android attendance/focus application that blocks user-selected apps (e.g., Clock, social media, games) while you are **timed in**, without requiring any Accessibility Service permissions.

## 🚀 How It Works (Based on Dev.to Guide)

Most app blockers rely on Android's `AccessibilityService` which can see and read everything on screen, triggering privacy concerns and Play Store scrutiny. FocusGuard uses an alternative, privacy-preserving approach:

1. **Foreground Service (`AppBlockerService`)**: Runs continuously with a sticky notification while you are clocked in.
2. **UsageStatsManager**: Polls `queryEvents()` every 900ms using a rolling time window (`[lastQueryTime, now]`) to catch `MOVE_TO_FOREGROUND` events.
3. **Lock Overlay (`LockOverlayActivity`)**: When a blocked app moves to the foreground, a translucent `singleInstance` Activity is displayed over it with a lock message and a "Go Back" button that redirects to the home screen.
4. **Debounce Guards**:
   - Skips re-launching if the overlay is already showing (`@Volatile isShowing` flag).
   - Skips duplicate events for the same package within 1 second.
   - Ignores transitions when the user taps "Go Back" to prevent strobe/looping.
5. **No Accessibility Permission Required**: Uses only standard `PACKAGE_USAGE_STATS` (Usage Access) and `SYSTEM_ALERT_WINDOW` (Display over other apps).

---

## 📱 Features

- **Clock In / Clock Out**: Single toggle button with visual timer showing elapsed session time.
- **Select Apps to Block**: Browse all installed user apps, search/filter, and check off the apps you want restricted (e.g., Clock, YouTube, Instagram).
- **Persistent Service**: Foreground service stays alive with a silent persistent notification. If the phone reboots while clocked in, `BootReceiver` automatically restarts the blocking service.
- **Modern Dark UI**: Clean, distraction-free aesthetic with purple/slate styling.

---

## 🛠️ How to Build the APK

### Option A: Using Android Studio (Recommended)
1. Open **Android Studio**.
2. Select **Open** and choose this folder: `/Users/JagDagelet/Downloads/attendance app/`.
3. Wait for Gradle to sync.
4. Go to **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**.
5. Once built, click **locate** in the notification to find your `.apk` file (typically under `app/build/outputs/apk/debug/app-debug.apk`).
6. Transfer this APK to your phone and install it.

### Option B: Using Command Line (Terminal)
If you have the Android SDK and Java installed:
```bash
cd "/Users/JagDagelet/Downloads/attendance app"
# If gradlew wrapper is not yet generated:
gradle wrapper
# Build debug APK:
./gradlew assembleDebug
```
The APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## ⚙️ Initial Setup on Your Phone

When you first open FocusGuard and tap **CLOCK IN**, the app will guide you through granting two required permissions:

1. **Usage Access**:
   - The app will prompt you to open **Settings → Usage Access**.
   - Find **FocusGuard** in the list and toggle **Permit usage access** to ON.
   - *Why?* This allows the app to detect which app is currently open.

2. **Display Over Other Apps**:
   - The app will prompt you to open **Settings → Display over other apps**.
   - Toggle **Allow display over other apps** to ON for FocusGuard.
   - *Why?* This allows the lock screen overlay to show up over blocked apps.

3. **Notifications**:
   - On Android 13+, grant notification permission so the foreground monitoring service stays alive reliably.

---

## 📂 Project Architecture

```
attendance app/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/focusguard/app/
│       │   ├── MainActivity.kt        # Clock in/out, session timer, permission checks
│       │   ├── BlockedAppsActivity.kt # App selector with search & checkboxes
│       │   ├── AppBlockerService.kt   # Foreground polling service (900ms loop)
│       │   ├── LockOverlayActivity.kt # Translucent lock screen shown over blocked apps
│       │   ├── AppListAdapter.kt      # RecyclerView adapter for installed apps
│       │   ├── AppInfo.kt             # Data model for app info
│       │   ├── PrefsManager.kt        # SharedPreferences persistence
│       │   └── BootReceiver.kt        # Auto-restart service after phone reboot
│       └── res/
│           ├── layout/                # UI layouts
│           ├── values/                # Colors, strings, themes
│           └── drawable/              # Button styles, badges, icons
├── build.gradle
├── settings.gradle
└── gradle.properties
```
