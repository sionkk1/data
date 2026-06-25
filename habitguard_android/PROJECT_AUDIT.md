# HabitGuard Project Audit

Last verified: 2026-06-21 KST

This audit records only what was observed in the current worktree and by commands run from `C:\Users\User\StudioProjects\data\habitguard_android`. Status labels are limited to: `구현 완료`, `부분 구현`, `미구현`, `확인 불가`.

## Verified Commands

| Area | Command | Result |
| --- | --- | --- |
| Android debug build | `.\gradlew.bat --no-daemon :app:assembleDebug` | Passed on 2026-06-21 after local inference update. `BUILD SUCCESSFUL in 17s`. SDK XML version warning remains. |
| Android unit tests | `.\gradlew.bat --no-daemon :app:testDebugUnitTest` | Passed on 2026-06-21 after local inference update. JVM tests now cover Python/Kotlin local inference parity, feature-order validation, repository fallback, usage aggregation, Guard v2 restriction evaluation, and Screen Timing UI copy. SDK XML version warning remains. |
| Android lint | `.\gradlew.bat --no-daemon :app:lintDebug` | Passed on 2026-06-21 after local inference update. `BUILD SUCCESSFUL in 53s`. SDK XML version warning remains. |
| Python ML tests | `python -m unittest tests\test_train_from_phone_csv.py` | Passed on 2026-06-21 after local inference bundle export update. 6 tests ran. |
| Python syntax | `python -m py_compile ai\train_from_phone_csv.py tests\test_train_from_phone_csv.py` | Passed on 2026-06-21. |
| Android asset JSON validity | `python -m json.tool app\src\main\assets\habitguard_model_profile.json > $null`; `python -m json.tool app\src\main\assets\android_inference_bundle.json > $null`; `python -m json.tool ai\phone_outputs\android_inference_bundle.json > $null` | Passed on 2026-06-21 after local inference update. |
| Android prediction repository test | `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.habitguard.app.ai.PredictionRepositoryTest` | Passed on 2026-06-21 after RED compile failure verified the new API was missing. |
| Android security static check | `python scripts\security_check.py` | Passed on 2026-06-21. |
| Android test APK build | `.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest` | Passed on 2026-06-21 after Room migration update. `BUILD SUCCESSFUL in 13s`. Compose Semantics and Room migration tests compile into the androidTest APK. |
| Room migration RED check | `.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest` | Failed as expected before production migration code was added. Kotlin compile error: unresolved `HabitGuardDatabaseMigrations`. This verified the new migration test caught the missing migration path. |
| Local quality gate | `python scripts\quality_gate.py` | Passed on 2026-06-21 after local inference update. Runs formatting check, security check, Python ML tests, JVM tests, androidTest APK build, lint, and debug build sequentially. |

## Reference Files Checked

| File or area | Status | Evidence |
| --- | --- | --- |
| `AGENTS.md` | 구현 완료 | Found at `C:\Users\User\StudioProjects\data\AGENTS.md`; current project instructions require Android-first, factual documentation, and no overclaiming. |
| `PROJECT_AUDIT.md` | 구현 완료 | This file updated by the current audit. |
| `PROJECT_TODO.md` | 구현 완료 | Updated with remaining tasks only. |
| `POSTER_CLAIMS.md` | 구현 완료 | Updated to separate safe, limited, unsupported, and future claims. |
| `TECH_RISKS.md` | 구현 완료 | Updated using `Risk / Severity / Evidence / Impact / Mitigation / Current status`. |
| `DATA_DICTIONARY.md` | 미구현 | No `DATA_DICTIONARY.md` found under `habitguard_android`. A root-adjacent `..\screen_timing_data_dictionary.md` exists, but it is not the requested project file name. |
| `docs/` | 구현 완료 | `docs/android_device_test_plan.md`, `docs/auxiliary_datasets.md` exist. |
| `ai/` | 구현 완료 | `ai/train_from_phone_csv.py`, `ai/phone_outputs/*` exist. |
| `models/` | 미구현 | No `models/` directory found under `habitguard_android`. Model artifacts are currently under `ai/phone_outputs/`. |
| `data/` | 미구현 | No `data/` directory found under `habitguard_android`. |
| `app/` | 구현 완료 | Android app module exists. |
| `android/`, `ios/`, `cloud/`, `firebase/` | 미구현 | No such directories found under `habitguard_android`. |
| `scripts/` | 구현 완료 | `scripts/android_device_check.ps1` exists. |

## Implementation Status

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| Android native Kotlin + Jetpack Compose app | 구현 완료 | `settings.gradle.kts` includes `:app`; `app/build.gradle.kts` applies `com.android.application`, Kotlin Android, and Compose plugin; Compose UI is in `app/src/main/java/com/habitguard/app/ui/HabitGuardApp.kt`. No Flutter rewrite is present in this module. |
| Android buildability | 구현 완료 | `.\gradlew.bat --no-daemon :app:assembleDebug` passed on 2026-06-20. |
| Compose screen structure | 부분 구현 | `HabitGuardApp.kt` defines Compose screens and a bottom-tab enum: `Dashboard`, `Goal`, `GuardDebug`, `Settings`. It does not use AndroidX Navigation, `NavHost`, route arguments, or a back stack. |
| Dashboard screen | 구현 완료 | `DashboardScreen` in `HabitGuardApp.kt`; receives usage totals, risk label, notification count, prediction text, charts, model profile text, and active rule from `HabitGuardViewModel`. |
| Goal screen | 구현 완료 | `GoalScreen` in `HabitGuardApp.kt`; `GoalParser.parse` in `app/src/main/java/com/habitguard/app/ai/GoalParser.kt`; save is performed only through user action in `HabitGuardViewModel.saveRule`. |
| Rule screen / restriction rule creation | 부분 구현 | `RestrictionRuleEntity` and `RestrictionRuleDao` exist in `HabitGuardDatabase.kt`; `saveRule` persists active goal and rule. There is no full rule management UI for listing, editing, deleting, or disabling multiple rules. |
| Lock/Mission screen | 부분 구현 | `LockActivity.kt` implements timed missions, answer validation, success/abandon logging, and temporary unlock grant. It was build-verified but not real-device verified in this audit. |
| Settings screen | 구현 완료 | `SettingsScreen` in `HabitGuardApp.kt` opens usage/accessibility/notification settings, exports CSV, tests lock screen, clears local data, and exposes Firestore consent/sync controls. |
| Guard debug screen | 구현 완료 | `GuardDebugScreen` in `HabitGuardApp.kt` displays recent `GuardEventEntity` rows. |
| UsageStatsManager permission check | 구현 완료 | `UsageStatsRepository.hasUsageAccess()` checks `AppOpsManager.OPSTR_GET_USAGE_STATS`. Manifest declares `android.permission.PACKAGE_USAGE_STATS`. |
| UsageStatsManager usage collection | 구현 완료 | `UsageStatsRepository.queryUsage`, `queryTodayUsage`, `queryDailySummaries`, `queryDailyAppUsage`, and `queryTodayHourlyUsage` use `UsageStatsManager.queryUsageStats` and `queryEvents`. |
| Daily aggregation and background sync | 구현 완료 | `DailyUsageSyncWorker` schedules periodic 6-hour WorkManager sync and writes `usage_daily` and `app_usage_daily`. |
| Room database | 구현 완료 | `HabitGuardDatabase.kt` defines Room entities/DAOs for rules, mission logs, guard events, daily usage, app usage, notification counts, goals, and prediction results. |
| Room migration safety | Implemented | `HabitGuardDatabase.kt` now uses version 11 with `exportSchema = true`; `ServiceLocator.kt` opens `habitguard.db` with `.addMigrations(HabitGuardDatabaseMigrations.MIGRATION_10_11)` and no destructive fallback. Schemas exist at `app/schemas/com.habitguard.app.data.HabitGuardDatabase/10.json` and `11.json`. |
| AccessibilityService restriction detection | 부분 구현 | `HabitGuardAccessibilityService.kt` listens for foreground package events, checks enabled rules/time window/temporary unlocks, and launches `LockActivity`. This is a user-approved interruption flow, not true OS-level app blocking. |
| Accessibility content minimization | 구현 완료 | `habitguard_accessibility_service.xml` sets `android:canRetrieveWindowContent="false"`. Code uses `event.packageName`; searches did not find `rootInActiveWindow`, `AccessibilityNodeInfo`, `event.text`, or notification body extraction. |
| Notification listener | 부분 구현 | `HabitGuardNotificationListenerService.kt` increments daily per-package notification counts after listener access. It does not backfill history and does not store notification text/body. |
| `RestrictionRule` model | 구현 완료 | `RestrictionRule` data class in `Models.kt`; `RestrictionRuleEntity` and DAO in `HabitGuardDatabase.kt`; ViewModel maps entity to model. |
| `MissionAttempt` implementation | 부분 구현 | No class named `MissionAttempt` was found. Equivalent mission attempt records are stored as `MissionLogEntity` in `HabitGuardDatabase.kt` and written by `LockActivity.kt`. |
| `UnlockSession` implementation | 부분 구현 | No Room entity/class named `UnlockSession` was found. Temporary unlock state is stored in SharedPreferences by `GuardPreferences` using package-specific expiry timestamps. |
| User approval before rule application | 구현 완료 | Rules are saved only from the Goal screen save callback through `HabitGuardViewModel.saveRule`; parsed suggestions are not persisted unless the user taps save. |
| CSV export for model training | 구현 완료 | `MainActivity.exportThirtyDayCsv()` and debug-only `DebugExportReceiver.kt` write CSV with usage, night minutes, opens, notification counts, and timestamps. |
| Python AI training pipeline | 구현 완료 | `ai/train_from_phone_csv.py` supports HabitGuard CSV input, deterministic synthetic fallback, regression/classification training, baseline models, metrics, confusion matrix, plots, `.joblib`, and Android asset profile output. |
| Synthetic data use | 구현 완료 | `ai/train_from_phone_csv.py` generates deterministic synthetic data if no CSV is supplied. Current `ai/phone_outputs/training_manifest.json` and `app/src/main/assets/habitguard_model_profile.json` have `source_type: synthetic` and `evaluation_scope: synthetic evaluation`. |
| Real user model evaluation | 미구현 | No approved real HabitGuard CSV export is documented as the source of current metrics. Current generated metrics are synthetic-only evidence. |
| Root screen timing synthetic dataset | 부분 구현 | Root-adjacent `..\generate_screen_timing_synthetic_dataset.py`, `..\screen_timing_synthetic_10500.csv`, and `..\screen_timing_data_dictionary.md` exist and mark rows as synthetic. These are not the same as a real app export. |
| On-device ML inference | 구현 완료 | `ai/train_from_phone_csv.py` exports `android_inference_bundle.json`; `ModelBundleLoader.kt` validates the bundle; `TrainedLocalPredictionModel` computes missing-value fill, StandardScaler, Linear Regression, and Logistic Regression math in Kotlin. Android still does not read `.joblib` files. Current bundle is synthetic-only. |
| FastAPI server connection | 미구현 | No FastAPI app, endpoint, or dependency found under `habitguard_android`. |
| Firebase SDK integration | 미구현 | No Firebase Gradle dependency or `google-services.json` found. |
| Firestore REST sync | 부분 구현 | `FirestoreSyncRepository.kt` can build a summary-only REST PATCH to Firestore after consent and external `project_id`/`bearer_token` SharedPreferences. There is no UI to configure those values and no production auth flow. |
| Cloud Run integration | 미구현 | No Cloud Run service, deployment config, or server code found under `habitguard_android`. |
| Privacy defaults | 부분 구현 | Raw app usage is local by default; CSV export is user-initiated; backup/data-transfer excludes databases/shared preferences. Firestore summary sync exists but uses sensitive bearer-token preferences without a proper auth UI. |
| Android backup settings | 구현 완료 | `AndroidManifest.xml` sets `android:allowBackup="false"` and references `backup_rules.xml` / `data_extraction_rules.xml`, both excluding `database` and `sharedpref`. |
| Logs | 부분 구현 | Debug receiver logs CSV cache path with tag `HabitGuardDebugExport`; guard events are stored locally in Room. No sensitive notification/body logging was found. |
| Android tests | 부분 구현 | `app/src/test/java/com/habitguard/app/data/UsageEventAggregatorTest.kt` and `app/src/test/java/com/habitguard/app/guard/RestrictionEvaluatorTest.kt` exist. No instrumentation tests exist yet for real AccessibilityService/LockActivity flows. |
| Python tests | 구현 완료 | `tests/test_train_from_phone_csv.py` has 6 tests covering deterministic synthetic generation, target-leakage prevention, temporal split, missing CSV validation, artifact generation, and Android inference bundle export. |
| CI | 구현 완료 | `.github/workflows/android-quality.yml` runs `python scripts/quality_gate.py` on push, pull request, and manual dispatch. |
| Lint/static analysis | 부분 구현 | Android lint passes; `scripts/security_check.py` checks security/privacy rules including release/main sensitive logging; `scripts/quality_gate.py` adds whitespace formatting checks. No separate Detekt/Ktlint config exists. |
| Release signing / store readiness | 미구현 | No release signing config or Google Play review evidence found. |
| Real-device verification | 확인 불가 | `docs/android_device_test_plan.md` exists, but this audit did not run on a physical phone and no completed device result log was found. |

## Broken or Unverified Areas

- No build-breaking Android issue was found by `assembleDebug` or `lintDebug` in this audit.
- Real-device behavior remains unverified for UsageStats retention, Accessibility foreground detection, lock launch reliability, mission abandon behavior, notification listener counting, and CSV sharing.
- Android core logic lacks automated Kotlin tests despite important behavior in `GoalParser`, `HabitAnalyzer`, `GuardPreferences`, `HabitGuardAccessibilityService`, `LockActivity`, and `FirestoreSyncRepository`.
- Current model metrics must be treated as synthetic pipeline verification only.

## Room Migration Update - 2026-06-21

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| Database version | Implemented | Version changed from `10` to `11` in `app/src/main/java/com/habitguard/app/data/HabitGuardDatabase.kt`. |
| Database file name | Implemented | Runtime Room builder still opens `habitguard.db` in `app/src/main/java/com/habitguard/app/data/ServiceLocator.kt`. |
| Entity and DAO scope | Implemented | Version 11 database includes `RestrictionRuleEntity`, `MissionLogEntity`, `GuardEventEntity`, `UnlockSessionEntity`, `UsageDailyEntity`, `AppUsageDailyEntity`, `NotificationDailyEntity`, `UserGoalEntity`, and `PredictionResultEntity`; DAOs remain exposed by `HabitGuardDatabase`. |
| Schema export | Implemented | `exportSchema = true` remains enabled; schema files exist for versions `10` and `11` under `app/schemas/com.habitguard.app.data.HabitGuardDatabase/`. |
| Destructive migration removal | Implemented | `ServiceLocator.kt` no longer calls `fallbackToDestructiveMigration(dropAllTables = true)`. Search result for `fallbackToDestructiveMigration` is empty in app data code after the change. |
| Explicit migration path | Implemented | `HabitGuardDatabaseMigrations.MIGRATION_10_11` is registered through `.addMigrations(...)`. It is a schema-preserving migration because version 11 does not change tables or columns. |
| Preserved tables | Implemented | `RoomMigrationVerificationTest.migration10To11PreservesUserDataTables()` inserts representative version 10 rows and validates preservation for `restriction_rule`, `mission_log`, `guard_event`, `unlock_session`, `usage_daily`, `app_usage_daily`, `notification_daily`, `user_goal`, and `prediction_result`. |
| Missing migration guard | Partially implemented | Future entity changes should fail schema/migration verification if the Room version changes without corresponding schema and migration test updates. The current automated check is compiled into androidTest; connected instrumentation execution was not run because `adb devices` listed no attached device at verification time. |
| Breaking-change deletion policy | Implemented | `ROOM_MIGRATION_POLICY.md` states that database deletion must not happen automatically and documents user notice, export/backup, confirmation, and risk-documentation requirements for any future non-preserving change. |

## Real Device Migration Crash Update - 2026-06-22

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| Connected device | Implemented | `adb devices -l` listed authorized Samsung `SM-F766N` (`R5CY745C8HB`) running Android 16 / SDK 36. |
| Launch crash root cause | Implemented | Device log at `docs/device_test_captures/crash_log_main_start.txt` showed `java.lang.IllegalStateException: A migration from 7 to 11 was required but not found`; the installed device DB had `PRAGMA user_version=7`. |
| 7-to-11 migration | Implemented | `HabitGuardDatabaseMigrations.MIGRATION_7_11` now adds missing v11 columns/tables without deleting existing data. Old `restriction_rule` rows get `approved=0` by default so migrated rules are not silently user-approved. |
| Runtime migration registration | Implemented | `ServiceLocator.kt` registers both `MIGRATION_7_11` and `MIGRATION_10_11` for `habitguard.db`. |
| Device DB migration result | Implemented | After reinstall/launch, schema-only check via temporary `run-as` DB copy reported `user_version=11` and presence of `restriction_rule`, `usage_daily`, `app_usage_daily`, `guard_event`, and `unlock_session`; temp copy was deleted. |
| Migration tests on device | Implemented | `.\gradlew.bat --no-daemon "-Pandroid.testInstrumentationRunnerArguments.class=com.habitguard.app.data.RoomMigrationVerificationTest" :app:connectedDebugAndroidTest` passed on `SM-F766N` with 2 tests. |
| Full connected instrumentation suite | Broken or unverified | `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest` failed in existing `ScreenTimingComposeSemanticsTest.primaryButtonHasAccessibleClickAction` with `No compose hierarchies found`; migration tests pass when filtered. |
| Guard v2 real-device scenarios | Partially implemented | `docs/android_device_test_results.md` records partial Usage Access allow/revoke evidence and the crash fix. Accessibility/Lock/Mission/temp-unlock/bypass/reboot/CSV scenarios were not completed because launch was initially blocked by the migration crash and reinstall reset permissions. |

## Screen Timing Compose UI Update - 2026-06-20

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| Native Android implementation preserved | Implemented | The update changed Kotlin/Jetpack Compose files under `app/src/main/java/com/habitguard/app/` and did not add Flutter code or dependencies. |
| Design system extraction | Implemented | `app/src/main/java/com/habitguard/app/ui/ScreenTimingDesignSystem.kt` defines shared light/dark color schemes, spacing tokens, 48dp minimum-touch buttons, cards, metric cards, status badges, and UI copy mapping. |
| Screen structure | Implemented | `HabitGuardApp.kt` now exposes Home, Analysis, Goal, Report, and Privacy tabs. Existing ViewModel data flow is preserved; screens receive state/callbacks and do not directly call DAO, network, or `UsageStatsManager`. |
| Onboarding and permission center | Implemented | `PermissionCenterCard` and `PermissionSetupDialog` show Usage Access, Accessibility Service, and Notification Access with status text and next actions. Captured at `docs/screen_timing_design/captures/home.png` and `privacy.png`. |
| Main dashboard | Implemented | `HomeScreen` separates measured values (`측정값`), AI prediction (`AI 예측`), risk text/icon, and approved-rule status. Captured at `docs/screen_timing_design/captures/home.png`. |
| Detailed analysis | Implemented | `AnalysisScreen` shows permission-denied/empty states, hourly chart, weekly chart, app usage rows, and analysis notes. Captured at `docs/screen_timing_design/captures/analysis.png`. |
| Natural-language goal input | Implemented | `GoalScreen` keeps the natural-language input flow and shows an empty state until the user generates a recommendation. Captured at `docs/screen_timing_design/captures/goal.png`. |
| AI recommendation review | Implemented | `RuleReviewCard` shows recommendation reasons, editable app/limit/time/mode/mission controls, reject, and approve actions. Rule persistence still occurs only through `HabitGuardViewModel.saveRule()`. |
| Lock/Mission UI | Partially implemented | `LockActivity.kt` now uses the shared Screen Timing theme and calmer copy. A direct mission test screen was captured at `docs/screen_timing_design/captures/lock-mission.png`; full AccessibilityService-triggered lock flow is still not device-verified. |
| Weekly report | Implemented | `WeeklyReportScreen` separates measured weekly averages, AI prediction estimate, goal status, data readiness, and mission summary. Captured at `docs/screen_timing_design/captures/report.png`. |
| Settings and privacy center | Implemented | `PrivacySettingsScreen` explains local-first storage, CSV export, Room data deletion scope, cloud sync limitation, and mission test entry. Captured at `docs/screen_timing_design/captures/privacy.png`. |
| Accessibility evidence | Partially implemented | `uiautomator dump` files were saved under `docs/screen_timing_design/captures/*-uiautomator.xml`. They show content descriptions for permission center, metric cards, risk icon/text, tabs, and mission timer. TalkBack listening, 200% font size, landscape, and dark mode were not fully captured in this task. |
| UI tests | Partially implemented | `app/src/test/java/com/habitguard/app/ui/ScreenTimingUiCopyTest.kt` covers permission status copy, prediction-readiness copy, and risk text/icon presentation. No Compose instrumentation semantics tests exist yet. |
| Screen Timing design documentation | Implemented | `docs/screen_timing_design/SCREEN_TIMING_DESIGN_REVIEW.md` records design tokens, common components, before/after comparison, capture paths, accessibility observations, Figma tool limitation, and remaining UI risks. |
| Verified commands | Implemented | `.\gradlew.bat --no-daemon :app:testDebugUnitTest`, `.\gradlew.bat --no-daemon :app:assembleDebug`, `.\gradlew.bat --no-daemon :app:lintDebug`, `python scripts\security_check.py`, `.\gradlew.bat --no-daemon :app:installDebug`, and `adb` screenshot/`uiautomator dump` commands ran on 2026-06-20. Final test/build/lint/security commands passed; SDK XML version warning remains for Gradle commands. |

## Data Science Status

Current verified app pipeline:

- Input schema: HabitGuard CSV export from app code.
- Fallback data: deterministic synthetic sample from `ai/train_from_phone_csv.py` with `source_type=synthetic`.
- Regression target: `target_next_day_minutes`.
- Classification target: next-day personal goal exceedance, stored as `goal_risk_label` (`within_goal`, `over_goal`).
- Auxiliary target: `user_type_label` for analysis only.
- Split method: per-user chronological train/validation/test split. The current pipeline does not use random row 80:20 splitting.
- Regression baselines: previous-day total and recent 7-day average.
- Regression comparison models: Linear Regression, RandomForestRegressor, GradientBoostingRegressor.
- Classification baseline: majority-class baseline.
- Classification comparison models: LogisticRegression, DecisionTreeClassifier, RandomForestClassifier, GradientBoostingClassifier.
- Outputs: `feature_schema.json`, `preprocessing.joblib`, `screen_time_regressor.joblib`, `goal_risk_classifier.joblib`, `user_type_classifier.joblib`, `regression_metrics.json`, `classification_metrics.json`, `model_card.md`, `training_manifest.json`, `data_snapshot_hash.txt`, `actual_vs_predicted.png`, `feature_importance.png`, `confusion_matrix.png`, `model_comparison.csv`, and `ai/phone_outputs/poster_assets/`.

Current generated metrics:

- Source: `synthetic`.
- Evaluation scope: `synthetic evaluation`.
- Data snapshot hash: `e9b81ae5892b2939c6af9c1992d9c1e4f48b71b7b625fc157a67d63b1c51814d`.
- Regression best model: `linear_regression`.
- Regression MAE/RMSE/R2: `18.1632 / 23.8108 / 0.8774`.
- Regression improvement over best baseline: `55.3482%` versus `baseline_rolling_7d`.
- Classification best model: `logistic_regression`.
- Classification Accuracy/Macro F1/Precision/Recall/High-risk recall: `0.8611 / 0.8495 / 0.8444 / 0.8562 / 0.84`.
- Classification improvement over baseline: `115.0629%` versus `baseline_majority`.
- Confusion matrix labels: `within_goal`, `over_goal`; matrix `[[41, 6], [4, 21]]`.
- Android model profile asset updated at `app/src/main/assets/habitguard_model_profile.json` because both non-baseline best models improved over their baselines. The asset is still labeled synthetic-only.

These metrics are not real-user performance evidence. No approved real HabitGuard CSV export has been used for the current generated metrics.

## Local Inference Bundle Update - 2026-06-21

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| Android inference bundle export | 구현 완료 | `ai/train_from_phone_csv.py` writes `ai/phone_outputs/android_inference_bundle.json` and, with `--update-android-asset`, `app/src/main/assets/android_inference_bundle.json`. Bundle model version is `screen-timing-linear-logistic-v1-7170d82f`. |
| Exported model math | 구현 완료 | Bundle includes `feature_names`, `imputer_values`, `scaler_mean`, `scaler_scale`, Linear Regression intercept/coefficients, Logistic Regression intercept/coefficients/classes, and `positive_class=over_goal`. |
| Feature leakage guard | 구현 완료 | `build_feature_schema()` excludes target columns; `export_android_inference_bundle()` rejects target/leakage columns; `test_android_inference_bundle_exports_coefficients_and_validation_fixture` covers this. |
| Kotlin bundle validation | 구현 완료 | `ModelBundleLoader.kt` checks schema version, `source_type`/`evaluation_scope`, missing-value strategy, scaler lengths, coefficient lengths, positive class, feature order, and validation fixture feature order. |
| Kotlin local inference | 구현 완료 | `TrainedLocalPredictionModel` computes missing-value fill, StandardScaler, one-hot categorical features, linear regression minutes, logistic regression over-goal probability, 10-minute rounded display value, and top coefficient factors. |
| Python/Kotlin parity | 구현 완료 | `TrainedLocalPredictionModelTest.kotlinInferenceMatchesPythonValidationFixture` verifies regression prediction within `0.1` minute and classification probability within `0.001` using the Python-generated fixture. |
| Repository fallback | 구현 완료 | `DefaultPredictionRepository` keeps collecting-data state below 7 complete days; if trained local inference fails it returns cache, then baseline. `PredictionRepositoryTest` covers local failure with cache and local failure without cache. |
| UI metadata | 구현 완료 | `DashboardPredictionPresenter`, `DashboardState`, and `PredictionCard` carry/display source, model version, calculated time, data quality, `source_type`, `evaluation_scope`, recommendation text, and synthetic caveat. |
| Synthetic limitation | 구현 완료 | Bundle and UI keep `source_type=synthetic` and `evaluation_scope=synthetic evaluation`; `MODEL_CARD.md`, `DATA_DICTIONARY.md`, and `POSTER_CLAIMS.md` state this is not real-user performance evidence. |
| Network scope | 구현 완료 | This update did not add FastAPI, Cloud Run, Firebase, Firestore, or remote model execution. Local inference works from Android assets. |
| Real-device capture | 확인 불가 | Required connected-device prediction-card capture was attempted but not completed. `adb devices` returned no attached serial before and after `adb start-server`; see `docs/local_inference_device_capture.md`. |

## AI Result Connection Layer Update - 2026-06-21

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| PredictionRepository interface | 구현 완료 | `app/src/main/java/com/habitguard/app/ai/PredictionRepository.kt` defines `PredictionRepository.predict(input)`. |
| Remote / Local / Baseline / Cache source separation | 구현 완료 | `PredictionSource` separates `REMOTE_MODEL`, `LOCAL_MODEL`, `BASELINE`, `CACHE`, and `COLLECTING_DATA`. No production remote backend client is configured; runtime now uses trained local JSON bundle inference, cache, or baseline paths. |
| Remote request/response DTO separation | 부분 구현 | `PredictionRequestDto`, `PredictionDailySummaryDto`, and `RemotePredictionResponseDto` exist in `PredictionRepository.kt`. No FastAPI/Firebase/Cloud Run prediction endpoint exists in this project. |
| Privacy-minimized prediction request | 구현 완료 | `PredictionRequestDto.from()` sends daily summary fields and category totals only. `PredictionRepositoryTest.remoteRequestOmitsRawTimelineNotificationBodyAndFreeText` verifies package name/app name are not included. There is no notification body or free-text goal field in the DTO. |
| Server failure fallback | 구현 완료 | `DefaultPredictionRepository` catches remote failures and returns a cached result if supplied, then local model or baseline. `PredictionRepositoryTest.usesCacheWhenRemoteFailsAndNoLocalModelIsAvailable` passed. |
| 7-day data readiness gate | 구현 완료 | `DefaultPredictionRepository` returns `COLLECTING_DATA` with no exact prediction when fewer than 7 complete days exist. `PredictionRepositoryTest.returnsCollectingStateWithoutPrecisePredictionBeforeSevenCompleteDays` passed. |
| Baseline fallback precision | 구현 완료 | Baseline predictions are rounded to 10-minute increments and labeled as approximate. `PredictionRepositoryTest.baselineFallbackRoundsPredictionWhenNoModelResultExists` passed. |
| Dashboard AI result display | 구현 완료 | `DashboardState` now carries predicted minutes text, goal-risk text, source label, model version, calculation time, data quality, and recommendation text. `PredictionCard` in `HabitGuardApp.kt` displays these separately from measured metrics. |
| Recommendation rule approval retained | 구현 완료 | `RuleReviewCard` displays AI recommendation context but still saves only through the existing approval button and `HabitGuardViewModel.saveRule()`. No rule is auto-enabled by model output. |
| On-device trained model inference | 미구현 | `AssetLocalPredictionModel` uses the model profile asset plus existing local history heuristic. It does not execute `.joblib` model artifacts on Android. |
| Backend auth/rate limit/environment separation | 미구현 | No backend prediction endpoint, auth middleware, rate limiting, or dev/staging/prod environment config exists in this project. |

## Quality Verification System Update - 2026-06-21

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| RestrictionEvaluator unit tests | 구현 완료 | `RestrictionEvaluatorTest.kt` covers approved/enabled rules, target mismatch, daily/session limits, same-day and 23:00-06:00 overnight time windows, unlock duration buckets, temporary unlock expiry, reboot invalidation, device time changes, daily unlock limits, emergency unlock, permission guidance, and mission exit reasons. |
| Daily aggregation tests | 구현 완료 | `UsageEventAggregatorTest.kt` covers midnight split, 23:00-06:00 night usage, duplicate foreground events, missing background/app termination, background-without-foreground, permission denied vs zero usage, reboot, timezone change, platform unavailable, and unknown error quality states. |
| Dashboard/ViewModel-adjacent unit tests | 구현 완료 | `DashboardPredictionPresenterTest.kt` tests dashboard prediction display fields separately from Android ViewModel dependencies; `PredictionRepositoryTest.kt` covers data readiness, offline/cache fallback, baseline precision, and privacy-minimized DTOs. |
| Room migration/schema verification | 부분 구현 | `HabitGuardDatabase` now exports schema to `app/schemas/com.habitguard.app.data.HabitGuardDatabase/10.json`; `RoomMigrationVerificationTest.kt` verifies current exported schema can open with runtime Room database. Explicit version-to-version migrations are still not implemented because `ServiceLocator.kt` still uses destructive fallback migration. |
| Compose UI and accessibility Semantics tests | 부분 구현 | `ScreenTimingComposeSemanticsTest.kt` verifies metric-card content descriptions and button click Semantics. The tests compile into the androidTest APK; connected-device execution was not run because no device was listed by `adb devices` during this task. |
| Permission denied / data empty / offline state tests | 부분 구현 | JVM tests cover usage permission denied, zero usage, collecting-data prediction state, and offline/cache/baseline prediction fallback. Full device-level permission revocation/offline UX was not run. |
| Release sensitive logging check | 구현 완료 | `scripts/security_check.py` now fails if release/main Kotlin sources log sensitive usage, notification, token, package, app, or free-form goal fields. |
| Lint / formatting / static analysis | 부분 구현 | `scripts/quality_gate.py` runs whitespace formatting checks, `scripts/security_check.py`, Python ML tests, Android JVM tests, androidTest APK build, `lintDebug`, and `assembleDebug` sequentially. Detekt/Ktlint are not configured. |
| CI automation | 구현 완료 | `.github/workflows/android-quality.yml` runs the quality gate on push, pull request, and manual dispatch. |
| Failure documentation | 구현 완료 | `TEST_FAILURES.md` records the Gradle parallel cache collision with cause, reproduction, impacted area, and fix status. |
| Manual Android 10-16 QA checklist | 구현 완료 | `QA_CHECKLIST.md` defines manual coverage for Android 10-16 and Samsung/Pixel device families, including permissions, UsageStats, Dashboard AI results, Guard/Mission, accessibility/privacy, UI Semantics, and release readiness. |

## Usage Collection Pipeline Update - 2026-06-20

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| Usage event aggregation unit tests | 구현 완료 | `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.habitguard.app.data.UsageEventAggregatorTest` passed. Tests cover midnight split, 23:00-06:00 night usage, duplicate foreground events, missing background/app termination, permission revoked, zero-usage day, device reboot, and explicit-zone timezone handling. |
| Raw UsageStats event to session restoration | 구현 완료 | `app/src/main/java/com/habitguard/app/data/UsageEventAggregator.kt` restores sessions from foreground/background events and closes missing background sessions at the aggregation range end with `APP_TERMINATED`. |
| Daily aggregation from restored sessions | 구현 완료 | `UsageEventAggregator.aggregate()` writes `UsageDailyEntity` totals for total minutes, night minutes, open count estimate, top app, category minutes, session count, average session length, max session length, data quality, and collection note. |
| App category daily totals | 부분 구현 | `UsageStatsRepository.appCategory()` maps package/app labels into 영상, SNS, 게임, 생산성, 기타 heuristically. This is deterministic but not a verified Play Store category classifier. |
| Permission missing vs real zero usage | 구현 완료 | With permission missing, daily rows are stored as `PARTIAL_PERMISSION`; with permission granted and no events, daily rows remain `COMPLETE` with 0 minutes. Unit tests cover both cases. |
| Data quality states | 부분 구현 | `UsageDataQuality` defines `COMPLETE`, `PARTIAL_PERMISSION`, `APP_TERMINATED`, `DEVICE_REBOOTED`, `PLATFORM_UNAVAILABLE`, `UNKNOWN_ERROR`. Aggregator records permission loss, missing background, reboot, source/query errors, and timezone-change records supplied to it. Runtime timezone-change detection from Android events is not fully implemented. |
| Duplicate collection/idempotent daily writes | 구현 완료 | `UsageEventAggregator` de-duplicates identical event triples; `MainActivity.refresh()` and `DailyUsageSyncWorker.doWork()` delete `app_usage_daily` rows for the refreshed date range before upserting aggregate rows. `usage_daily` uses date primary key replace. |
| Dashboard actual-data readiness | 구현 완료 | `DashboardState.dataReadinessMessage` and `HabitGuardApp.kt` show `현재 N일의 기록이 수집되었습니다. 7일 이상 쌓이면 개인화 예측을 시작합니다.` when fewer than 7 complete daily records exist. `MainActivity.refresh()` suppresses stored prediction writes and AI model profile display until 7 complete days are present. |
| Recent 7-day average and personal baseline | 구현 완료 | `MainActivity.refresh()` computes `recent7DayAverage` from complete daily summaries and `personalBaselineMinutes` from up to 14 complete daily summaries. |
| Connected tablet availability | 확인 불가 | `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe devices` listed `R54X400KP2N	device`. This confirms a connected device is available, but this task did not install the app or run permission/reboot/date-boundary flows on the tablet. |

## Security and Privacy Baseline Update

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| Security audit document | 구현 완료 | `SECURITY_AUDIT.md` was added on 2026-06-20 with severity-ranked findings. |
| Privacy architecture document | 구현 완료 | `PRIVACY_ARCHITECTURE.md` was added on 2026-06-20 with data sources, storage, backup, export/delete, and cloud transfer rules. |
| HTTPS-only network baseline | 구현 완료 | `AndroidManifest.xml` sets `android:usesCleartextTraffic="false"` and `android:networkSecurityConfig="@xml/network_security_config"`; `network_security_config.xml` sets base cleartext traffic to false. |
| Firestore bearer token storage | 부분 구현 | `SecureTokenStore.kt` stores token values with Android Keystore-backed AES-GCM and `FirestoreRestGateway` reads the bearer token through it. There is still no production UI/auth flow to set the token. |
| Firestore privacy payload | 부분 구현 | `FirestoreSyncRepository.kt` no longer includes `UserGoalEntity.rawText` in the summary upload payload. It still uploads summary goal/rule/weekly fields after user consent. |
| Sensitive debug logging | 부분 구현 | `DebugExportReceiver.kt` no longer logs the absolute CSV cache path. Debug receiver remains exported in debug builds only for ADB testing. |
| Security check script | 구현 완료 | `scripts/security_check.py` checks manifest export settings, HTTPS-only config, backup exclusions, token read path, debug path logging, accessibility content access, and notification body access. |

## Guard v2 Restriction Engine Update - 2026-06-20

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| RestrictionEvaluator pure function | 구현 완료 | `app/src/main/java/com/habitguard/app/guard/RestrictionEvaluator.kt` defines `RestrictionEvaluator.evaluate()` with pure inputs/outputs. `RestrictionEvaluatorTest` passed. |
| Approved-only rule application | 구현 완료 | `RestrictionRuleEntity` now has `approved`; `RestrictionRuleDao.enabledRuleFor()` and `enabledRules()` require `approved = 1 AND enabled = 1`. `HabitGuardViewModel.saveRule()` sets `approved = true` only when the user saves the rule. |
| Target app, day, time, daily limit, session limit checks | 구현 완료 | `RestrictionRuleSpec` includes package, active days, overnight time windows, daily limit, and session limit. Unit tests cover target mismatch, 23:00-06:00 window, daily limit, and session limit. |
| UnlockSession storage | 구현 완료 | `UnlockSessionEntity` and `UnlockSessionDao` were added to Room; `LockActivity` inserts sessions after mission or emergency unlock. `GuardPreferences` stores the current session for runtime checks. |
| Elapsed-realtime unlock expiry | 구현 완료 | `GuardPreferences` stores `issuedAtElapsedRealtime` and `expiresAtElapsedRealtime`; `UnlockSessionSpec.isValidFor()` rejects expired sessions and sessions whose issue time is ahead of current elapsed time after reboot. Unit tests cover expiry, reboot, and device time change. |
| Mission success unlock duration limit | 구현 완료 | `RestrictionRuleSpec.allowedUnlockMinutes()` clamps granted time to fixed 5/10/30 minute buckets; `LockActivity` logs the actual clamped grant. |
| Daily max unlock count | 부분 구현 | `MissionLogDao.successCountForPackageSince()` feeds `RestrictionEvaluator`; when max is reached the normal mission unlock is disabled and emergency unlock remains available. No UI for configuring the max count yet. |
| Emergency unlock | 부분 구현 | `LockActivity` has an emergency unlock action and logs `emergency_unlock_granted`; it grants a short elapsed-realtime unlock. Separate per-day emergency limit UI/policy is not yet implemented. |
| Re-entry and mission bypass logs | 구현 완료 | Accessibility service logs `restricted_app_reentered`; `LockActivity` logs `mission_abandoned_back`, `mission_abandoned_home_or_recent`, and generic abandon events. Unit tests cover exit reason classification. |
| Accessibility content minimization after Guard v2 | 구현 완료 | `HabitGuardAccessibilityService.kt` still uses only `event.packageName`; `scripts/security_check.py` passed after the Guard v2 changes. |
| Real-device Guard v2 verification | 확인 불가 | `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe devices` listed `R54X400KP2N	device`, but this task did not install/run Guard v2 flows on the tablet. |

## Local-First Offline Prediction Update - 2026-06-22

| Scope | Status | Evidence and judgment |
| --- | --- | --- |
| Offline inference asset | 구현 완료 | `python ai\train_from_phone_csv.py --output-dir ai\phone_outputs --update-android-asset` regenerated `ai/phone_outputs/android_inference_bundle.json` and `app/src/main/assets/android_inference_bundle.json` with `source_type=synthetic`, `evaluation_scope=synthetic evaluation`, selected `linear_regression` and `logistic_regression`, feature order, imputer values, scaler values, coefficients, and intercepts. |
| Room snapshot to prediction input | 구현 완료 | `LocalFirstPredictionRefreshEngine.kt` maps `UsageDailyEntity` and same-day `AppUsageDailyEntity` rows into `PredictionInput` daily summaries and app usage features without network calls. |
| Prediction result persistence | 구현 완료 | `LocalFirstPredictionRefreshEngine.refresh()` stores `PredictionResultEntity` only when the outcome is not `COLLECTING_DATA` and has a concrete predicted minute value. |
| WorkManager prediction refresh | 구현 완료 | `DailyUsageSyncWorker.doWork()` now runs usage aggregation, upserts daily/app rows, loads `android_inference_bundle.json`, constructs `DefaultPredictionRepository(localModel=TrainedLocalPredictionModel(...))`, applies repository fallback, and upserts `prediction_result`. |
| 7-day data gate | 구현 완료 | `DefaultPredictionRepository` still returns `COLLECTING_DATA` when fewer than 7 complete daily records exist; the new refresh engine does not persist collecting-data outcomes. |
| Fallback order | 구현 완료 | Existing repository behavior is retained: local model failure or bundle absence falls through to cached result, then baseline, while fewer than 7 complete days returns collecting-data before precise prediction. |
| Python/Kotlin parity | 구현 완료 | `TrainedLocalPredictionModelTest.kotlinInferenceMatchesPythonValidationFixture` verifies Python/Kotlin regression prediction within `0.1` minute and classification probability within `0.001`. |
| WorkManager unit coverage | 구현 완료 | `LocalFirstPredictionRefreshEngineTest` verifies Room-like daily/app snapshots feed `PredictionRepository`, save local model predictions, and skip saving collecting-data outcomes. |
| Server scope | 구현 완료 | No FastAPI, Cloud Run, Firebase, Firestore, or remote prediction dependency was added in this update. Prediction refresh works from local Room rows and bundled JSON. |
| Model validity limitation | 부분 구현 | Current local model coefficients are synthetic-only. The app can use real collected daily summaries as input features, but the trained model has not been fitted or evaluated on real user data. |
