# Android Device Test Results

Last verified: 2026-06-22 KST

## Device

| Field | Result |
| --- | --- |
| ADB command | `adb devices -l` |
| Serial | `R5CY745C8HB` |
| Model | `SM_F766N` / Samsung `SM-F766N` |
| Android version | Android 16, SDK 36 |
| App package | `com.habitguard.app` |
| Privacy note | No notification body, user input text, or raw app timeline was intentionally captured. A transient launcher `uiautomator` dump was deleted because it could include home-screen content. |

## Blocking Crash Found And Fixed

Before Guard v2 scenario testing, the app repeatedly closed on launch. Device log:

- Log path: `docs/device_test_captures/crash_log_main_start.txt`
- Root cause: `java.lang.IllegalStateException: A migration from 7 to 11 was required but not found`
- Existing device DB: `habitguard.db`, `PRAGMA user_version=7`
- Fix added: explicit Room `MIGRATION_7_11`, registered in the runtime Room builder.
- Post-fix device DB check: `user_version=11`; `restriction_rule`, `usage_daily`, `app_usage_daily`, `guard_event`, and `unlock_session` tables exist.
- Post-fix launch evidence: `docs/device_test_captures/04_post_migration_app_launch.png`, `docs/device_test_captures/post_reinstall_launch_log.txt`

## Scenario Results

| # | Scenario | Rule / setup | Expected result | Actual result | Evidence | Repro / notes | Severity |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Usage Access allowed and actual usage collection | Usage Access was allowed before reinstall. | Dashboard opens and reads actual local usage data without crashing. | Partial pass before crash fix; after reinstall, Usage Access app-op was reset to default/no operation and must be re-enabled. | `01_usage_access_allowed_dashboard.png`; `adb shell appops get com.habitguard.app GET_USAGE_STATS` later returned `No operations.` | Re-enable Usage Access in Settings, open app, refresh dashboard, then verify `usage_daily` row count without dumping raw data. | Medium |
| 2 | Usage Access revoked safe behavior | `cmd appops set com.habitguard.app GET_USAGE_STATS ignore` before reinstall. | App should not crash; usage-dependent features should show permission-needed state. | Partial pass: dashboard screenshot captured after revoke. Later app crash was due Room migration, not permission handling. | `02_usage_access_revoked_dashboard.png` | Revoke app-op, force-stop, launch app, confirm permission copy and no fatal log. | Medium |
| 3 | AccessibilityService allowed and restricted app entry detection | Accessibility service was enabled before reinstall. | Foreground restricted app should trigger Lock/Mission only for approved/enabled rule. | Not completed. App launch crash blocked flow; after reinstall accessibility service was reset and not enabled for HabitGuard. | Current secure setting showed only Microsoft screen mirroring accessibility service. | Re-enable HabitGuard service, create approved test rule, launch target package, collect sanitized log/screenshot. | High |
| 4 | 23:00-06:00 overnight rule | No approved test rule configured after reinstall. | Rule active across midnight, including 23:00-23:59 and 00:00-06:00. | Not run on device. Covered by JVM evaluator tests only. | `RestrictionEvaluatorTest`; no device capture. | Configure approved rule with `startHour=23`, `endHour=6`, then test with controlled time or manual late-night window. | High |
| 5 | Daily limit / session limit exceeded | No approved test rule configured after reinstall. | Lock/Mission appears only when daily/session limit condition is met. | Not run on device. | No device evidence. | Create low-limit rule for a harmless test app, generate usage/session, re-enter app. | High |
| 6 | Mission success grants 10-minute temporary unlock | No Lock/Mission flow reached on device. | Successful mission creates temporary unlock session for 10 minutes. | Not run on device. | No device evidence. | Trigger LockActivity through AccessibilityService rule, complete mission, verify app re-entry allowed. | High |
| 7 | Temporary unlock expiry re-locks app | No unlock session created on device. | After expiry, restricted app entry shows Lock/Mission again. | Not run on device. | No device evidence. | Complete 10-minute mission, wait or use test-only shorter rule if available, re-enter app. | High |
| 8 | Home / back / recents / same-app re-entry bypass attempts | No Lock/Mission flow reached on device. | Bypass attempts are logged and re-entry is restricted when no valid unlock exists. | Not run on device. | No device evidence. | During LockActivity, press Home, Back, Recents, relaunch target app, record sanitized guard events. | High |
| 9 | Emergency unlock | No approved rule configured after reinstall. | Emergency unlock grants limited access without auto-enabling rules. | Not run on device. | No device evidence. | Trigger lock screen, use emergency unlock, verify duration and event log. | High |
| 10 | Reboot invalidates temporary unlock | No unlock session created; device reboot not performed. | Existing elapsed-realtime-based unlock should be invalid after reboot. | Not run. Reboot was not forced during this session to avoid disrupting the connected test device while the crash fix was still being verified. | JVM evaluator coverage only. | After creating an unlock session, reboot device, reconnect ADB, re-enter target app. | Medium |
| 11 | Notification launches restricted app | NotificationListener was reset after reinstall; no target notification flow configured. | Entering target app from notification should still be detected by AccessibilityService foreground package event. | Not run on device. | No device evidence. | Enable NotificationListener if needed, send benign test notification without body logging, tap notification, verify lock. | Medium |
| 12 | NotificationListener permission revoked | Notification listeners after reinstall did not include HabitGuard. | App should continue without notification counts and not crash. | Partially observed: setting did not list HabitGuard listener after reinstall. Full UI state not verified. | `adb shell settings get secure enabled_notification_listeners` output did not include HabitGuard. | Enable listener, verify counts, revoke listener, reopen dashboard. | Medium |
| 13 | CSV export | Debug receiver/app export not exercised after reinstall. | User-initiated or debug export creates CSV without notification bodies or input text. | Not run in this device pass. | No export file captured. | Use Settings export or debug receiver, inspect only header/schema and file path, not private row contents. | Medium |

## Commands And Results

| Command | Result |
| --- | --- |
| `adb devices -l` | Passed; Samsung `SM-F766N` connected and authorized. |
| `.\gradlew.bat --no-daemon :app:installDebug` | Passed; debug APK installed on the connected device. |
| `adb shell am start -n com.habitguard.app/.MainActivity` | Initially reproduced launch crash; after migration fix and reinstall, app process stayed alive. |
| `adb logcat` filtered for crash lines | Found missing `7 -> 11` Room migration before fix; no matching launch crash after fix. |
| Device DB schema check via `run-as` copy to temp SQLite file | Passed; post-fix DB `user_version=11` and expected tables existed. Temp DB copy was deleted after schema-only check. |
| `.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest` | Passed. |
| `.\gradlew.bat --no-daemon :app:testDebugUnitTest` | Passed. |
| `.\gradlew.bat --no-daemon "-Pandroid.testInstrumentationRunnerArguments.class=com.habitguard.app.data.RoomMigrationVerificationTest" :app:connectedDebugAndroidTest` | Passed; 2 migration tests ran on `SM-F766N`. |
| `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest` | Failed because existing `ScreenTimingComposeSemanticsTest.primaryButtonHasAccessibleClickAction` reported no Compose hierarchy. Migration tests passed when run alone. |
| `.\gradlew.bat --no-daemon :app:lintDebug` | Passed. |

## Remaining Device Work

- Re-enable Usage Access, HabitGuard AccessibilityService, and NotificationListener after reinstall.
- Create a harmless approved test rule through the app UI before testing Guard v2 behavior.
- Repeat scenarios 3-13 with sanitized screenshots/logs.
- Fix or quarantine the existing Compose instrumentation launch issue before treating full `connectedDebugAndroidTest` as green.
