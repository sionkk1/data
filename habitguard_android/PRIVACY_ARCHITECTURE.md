# HabitGuard Privacy Architecture

Last verified: 2026-06-20 KST

HabitGuard is local-first by default. This document describes the current implementation, not future plans.

## Data Sources

| Source | User approval | Stored data | Current storage |
| --- | --- | --- | --- |
| Android UsageStatsManager / UsageEvents | User enables Usage Access in Android settings | App package/name/category, usage minutes, night minutes, open count, first/last timestamps | Room tables `usage_daily`, `app_usage_daily` |
| AccessibilityService | User enables the service in Android settings | Foreground package event outcome, block/allow status, mission result metadata | Room table `guard_event` |
| Lock/Mission screen | User interacts with mission screen | Mission type, package, start/end time, success/failure, unlock minutes, reason text | Room table `mission_log` |
| NotificationListenerService | User enables notification listener in Android settings | Date/package/app label/count only | Room table `notification_daily` |
| Goal and restriction UI | User confirms save | Goal/rule fields, including local raw goal text | Room tables `user_goal`, `restriction_rule` |
| Optional Firestore REST sync | User toggles consent and token/project are configured | Summary-only goal/rule/weekly data; no raw app-level usage rows; no raw goal text after this update | Network request from `FirestoreSyncRepository` |

## Data Not Collected by Current Code

- Accessibility service does not read screen text, input text, UI tree nodes, or content descriptions.
- Notification listener does not read or store notification title, body, extras, or message text.
- No camera, microphone, contacts, location, or SMS permission is requested.
- No real Firebase SDK or FastAPI/Cloud Run backend is implemented in this Android project.

## Local Storage

- Room DB name: `habitguard.db`, created in `ServiceLocator.kt`.
- Temporary unlock expiry values are stored in SharedPreferences by `GuardPreferences`.
- Firestore consent and project ID are stored in SharedPreferences by `FirestoreSyncRepository`.
- Firestore bearer token is read through `SecureTokenStore`, which encrypts values with Android Keystore-backed AES-GCM before storing encrypted blobs in SharedPreferences.

## Backup and Transfer

- `AndroidManifest.xml` sets `android:allowBackup="false"`.
- `backup_rules.xml` excludes `database` and `sharedpref`.
- `data_extraction_rules.xml` excludes `database` and `sharedpref` for both cloud backup and device transfer.

## Export and Deletion

- CSV export is user-initiated from Settings through `MainActivity.exportThirtyDayCsv()`.
- Debug CSV export exists only under `app/src/debug` for ADB testing.
- Local data deletion currently clears Room tables for rules, mission logs, usage snapshots, notification counts, goals, predictions, and guard events.
- Local data deletion does not currently clear SharedPreferences or encrypted cloud token settings.

## Cloud Transfer Rules

- No cloud upload occurs unless Firestore consent is enabled.
- Firestore upload is currently summary-only.
- Raw app-level usage rows are not uploaded by the current Firestore payload.
- Free-form goal text is no longer included in the current Firestore payload.
- Production authentication and secure token configuration UI are not implemented yet.

## Permission Denial Behavior

- If Usage Access is missing, the app shows permission UI and skips usage queries instead of exiting.
- If Accessibility access is missing, restriction/mission interruption cannot operate, but dashboard/settings still render.
- If notification listener access is missing, notification counts remain unavailable, but the app continues running.
