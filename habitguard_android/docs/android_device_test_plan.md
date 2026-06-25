# HabitGuard Android Device Test Plan

Run these checks on a real Android phone. Emulator-only results are not enough for the accessibility and usage-stats claims.

## Setup

1. Build debug APK:
   ```powershell
   .\gradlew.bat --no-daemon :app:assembleDebug
   ```
2. Install and open:
   ```powershell
   .\scripts\android_device_check.ps1 -InstallApk -OpenApp
   ```
3. In Android Settings, manually grant:
   - Usage access for HabitGuard.
   - Accessibility service access for HabitGuard.
   - Notification listener access for HabitGuard.
   - Notification posting permission on Android 13+.

## Functional Checks

| Check | Expected evidence |
| --- | --- |
| Permission screen | App shows missing permissions and opens the correct Settings pages. |
| Usage collection | Dashboard shows non-zero app usage after permission and refresh. |
| Daily storage | After refresh or background worker, recent 30-day rows exist in Room-backed dashboard/export. |
| Goal parsing | A Korean natural-language goal resolves to a target app or asks for confirmation. |
| Rule save | Approved rule appears on dashboard/report and is stored after app restart. |
| Accessibility detection | Opening the restricted app logs a `lock_started` guard event. |
| Lock mission success | Completing a mission grants temporary unlock and logs success. |
| Lock mission abandon | Leaving without completion returns home and logs abandoned mission. |
| Temporary unlock expiry | After unlock window expires, opening the restricted app triggers lock again. |
| Notification counts | After notification access, counts increase only for new notifications. |
| CSV export | Share sheet opens from Settings export. Debug broadcast can generate cache CSV in debug builds. |
| Local reset | Reset clears app-local analytics, goals, rules, and mission logs without changing Android system permissions. |

## ADB Helpers

Install/open:

```powershell
.\scripts\android_device_check.ps1 -InstallApk -OpenApp
```

Trigger debug CSV export in debug builds:

```powershell
.\scripts\android_device_check.ps1 -ExportCsv
adb logcat -d -s HabitGuardDebugExport
```

## Record Results

Record device model, Android version, app version, date, and pass/fail evidence. Do not paste raw personal app usage logs into public documents.
