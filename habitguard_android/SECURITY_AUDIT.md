# HabitGuard Android Security Audit

Last verified: 2026-06-20 KST

Scope: Android native Kotlin + Jetpack Compose app in `habitguard_android`. This audit is limited to the current worktree. It does not claim Google Play approval or real-device security validation.

## Commands Run

| Command | Result |
| --- | --- |
| `python scripts\security_check.py` | Passed after fixes. |
| `.\gradlew.bat --no-daemon :app:assembleDebug` | Exit code 0. SDK XML version warning remains. |

## Findings

| Item | Severity | Status | Evidence |
| --- | --- | --- | --- |
| HTTPS-only network policy | High | Fixed | Added `app/src/main/res/xml/network_security_config.xml`; `AndroidManifest.xml` now sets `android:usesCleartextTraffic="false"` and `android:networkSecurityConfig="@xml/network_security_config"`. |
| OAuth bearer token read from plain SharedPreferences | High | Fixed for read path | `FirestoreRestGateway` now reads `KEY_BEARER_TOKEN` through `SecureTokenStore`, which encrypts values with Android Keystore-backed AES-GCM before SharedPreferences persistence. No UI exists yet to write this token. |
| Free-form goal text in cloud summary | Medium | Fixed | Removed `rawText` from Firestore summary payload in `FirestoreSyncRepository.kt`. Local Room still stores raw goal text. |
| Debug CSV absolute path logging | Medium | Fixed | `DebugExportReceiver.kt` no longer logs `output.absolutePath`; it logs only generic export completion/failure in debug source set. |
| `android:exported` settings | Medium | Accept with constraints | `MainActivity` is exported for launcher. `LockActivity` and `FileProvider` are not exported. Accessibility and notification listener services are exported with required Android bind permissions. Debug receiver is exported only in `app/src/debug`. |
| Unnecessary permissions | Medium | No extra high-risk permission found | Manifest uses `PACKAGE_USAGE_STATS`, `POST_NOTIFICATIONS`, and `INTERNET`. These are tied to current usage collection, notification counting, and optional Firestore REST sync. No camera/location/microphone/contact permissions found. |
| Deep links and browsable intents | Low | No issue found | No `BROWSABLE` or external `VIEW` deep link intent filters found in main manifest. |
| Cleartext HTTP URLs | High | No issue found after fix | Static check found no `http://` in main Kotlin sources and network config blocks cleartext. |
| Hardcoded API keys/service account keys | High | No issue found | Static check did not find hardcoded API key/private key/service account patterns in Android source. |
| Accessibility content access | High | No issue found | `habitguard_accessibility_service.xml` has `canRetrieveWindowContent=false`; service code uses package name only and does not use `rootInActiveWindow`, node traversal, `event.text`, or `event.contentDescription`. |
| Notification body storage | High | No issue found | `HabitGuardNotificationListenerService.kt` stores date, package, app label, count, and timestamp only. It does not read notification extras/title/text. |
| Auto Backup of raw usage data | High | Mitigated | Manifest has `allowBackup=false`; `backup_rules.xml` and `data_extraction_rules.xml` exclude `database` and `sharedpref`. |
| Data deletion/export | Medium | Partially implemented | `MainActivity.clearLocalData()` clears local Room tables; `exportThirtyDayCsv()` exports CSV by explicit user action. It does not delete encrypted cloud token settings. |
| Cloud transfer consent | High | Partially implemented | `FirestoreSyncRepository.syncConsentedSummary()` returns early unless consent is enabled. Production auth/config UI is not implemented. |
| Release logging | Medium | No release log issue found | Log calls found only in debug source set. No `Log.*` calls found in `app/src/main` during this audit. |
| Debug/release difference | Medium | Documented risk | Debug source has exported `DebugExportReceiver` for ADB CSV export. It is absent from main/release source sets. |

## Residual Risks

- Real-device behavior for UsageStats, AccessibilityService, NotificationListenerService, CSV export, and lock flow is still unverified.
- `fallbackToDestructiveMigration(dropAllTables = true)` can delete local data on schema changes.
- `SecureTokenStore` provides encrypted token storage, but the app still has no production UI or auth flow for setting the Firestore token.
- Local Room still stores app usage metadata, mission reason text, and goal text. Backup is disabled/excluded, but physical device compromise is out of scope for this implementation.
- Debug receiver is intentionally exported in debug builds for test automation; do not ship debug builds as production releases.
