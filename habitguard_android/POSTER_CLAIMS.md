# HabitGuard Poster Claims

Use this file to keep presentation claims factual. Do not mix synthetic data with real user data.

## Safe to Say

- HabitGuard is a native Android Kotlin + Jetpack Compose prototype. Evidence: `app/build.gradle.kts`, `HabitGuardApp.kt`.
- The Android debug app currently builds. Evidence: `.\gradlew.bat --no-daemon :app:assembleDebug` passed on 2026-06-20.
- The app requests user-enabled Usage Access and reads app-level usage metadata through Android `UsageStatsManager` / `UsageEvents`. Evidence: `AndroidManifest.xml`, `UsageStatsRepository.kt`.
- The app stores local goals, restriction rules, mission logs, guard events, daily usage summaries, app-level daily usage, notification counts, and prediction summaries in Room. Evidence: `HabitGuardDatabase.kt`.
- The app has Compose screens for dashboard, goal/rule creation, guard debug, and settings, plus a separate mission/lock activity. Evidence: `HabitGuardApp.kt`, `LockActivity.kt`.
- The app UI separates measured values, AI prediction, and user-approved recommendation/rule status in the Screen Timing Compose flow. Evidence: `HabitGuardApp.kt`, `ScreenTimingDesignSystem.kt`, `docs/screen_timing_design/SCREEN_TIMING_DESIGN_REVIEW.md`.
- The Screen Timing UI uses shared Compose design tokens and common components for cards, buttons, status badges, and metric cards. Evidence: `ScreenTimingDesignSystem.kt`.
- Restriction rules are applied only after the user confirms/saves the rule. Evidence: `GoalScreen` save callback and `HabitGuardViewModel.saveRule`.
- Guard v2 has JVM-tested pure restriction evaluation for approved/enabled rules, overnight windows, daily/session limits, elapsed-realtime temporary unlock expiry, reboot invalidation, and exit reason classification. Evidence: `RestrictionEvaluator.kt`, `RestrictionEvaluatorTest.kt`.
- The accessibility service is configured not to retrieve window content. Evidence: `habitguard_accessibility_service.xml` has `canRetrieveWindowContent=false`.
- Current notification logic stores per-app daily notification counts, not notification body text. Evidence: `HabitGuardNotificationListenerService.kt`, `NotificationDailyEntity`.
- The app can export a CSV schema suitable for Python model training. Evidence: `MainActivity.exportThirtyDayCsv()` and `DebugExportReceiver.kt`.
- The Python pipeline compares previous-day and 7-day-average regression baselines against Linear Regression, RandomForestRegressor, and GradientBoostingRegressor. Evidence: `ai/train_from_phone_csv.py`, `ai/phone_outputs/regression_metrics.json`.
- The Python pipeline compares a majority-class classification baseline against LogisticRegression, DecisionTreeClassifier, RandomForestClassifier, and GradientBoostingClassifier. Evidence: `ai/train_from_phone_csv.py`, `ai/phone_outputs/classification_metrics.json`.
- The Python pipeline writes the required ML artifacts: feature schema, preprocessing artifact, model artifacts, metrics JSON, model card, training manifest, data snapshot hash, plots, model comparison CSV, and `poster_assets`. Evidence: `ai/phone_outputs/`.
- The Python pipeline exports Android-executable model math as JSON, not `.joblib`, in `android_inference_bundle.json`. Evidence: `ai/train_from_phone_csv.py`, `ai/phone_outputs/android_inference_bundle.json`, `app/src/main/assets/android_inference_bundle.json`.
- Python pipeline tests pass in this workspace. Evidence: `python -m unittest tests\test_train_from_phone_csv.py` ran 6 tests and passed.
- The app has an AI result connection layer that labels prediction source as remote model, local model, baseline, cache, or collecting-data state. Evidence: `PredictionRepository.kt`, `DashboardState`, `HabitGuardApp.kt`.
- Android local inference executes the exported Linear Regression and Logistic Regression coefficients in Kotlin after missing-value fill, StandardScaler, and one-hot feature transforms. Evidence: `ModelBundleLoader.kt`, `TrainedLocalPredictionModel`, `TrainedLocalPredictionModelTest`.
- Kotlin local inference is tested against a Python-generated validation fixture with regression tolerance `<= 0.1` minute and classification probability tolerance `<= 0.001`. Evidence: `TrainedLocalPredictionModelTest`.
- The app can refresh predictions offline from local Room daily/app usage rows through WorkManager and store non-collecting results in Room. Evidence: `DailyUsageSyncWorker.kt`, `LocalFirstPredictionRefreshEngine.kt`, `LocalFirstPredictionRefreshEngineTest`.
- The prediction request DTO excludes raw app package names, raw app timeline events, notification bodies, and free-form goal text. Evidence: `PredictionRequestDto.from()`, `PredictionRepositoryTest`.
- The project has a local quality gate for formatting, security/privacy checks, Python ML tests, Android JVM tests, androidTest APK build, lint, and debug build. Evidence: `scripts/quality_gate.py`.
- GitHub Actions is configured to run the quality gate on push, pull request, and manual dispatch. Evidence: `.github/workflows/android-quality.yml`.
- Basic Compose Semantics and current Room schema verification tests exist. Evidence: `ScreenTimingComposeSemanticsTest.kt`, `RoomMigrationVerificationTest.kt`.

## Safe with Limitation

- The app can interrupt selected foreground app launches with a mission screen, but this is an AccessibilityService-based interruption flow, not OS-level app disabling.
- The dashboard shows predicted next-day usage and risk information from the local JSON inference bundle when at least 7 complete days exist, but the current bundle is synthetic-only and must not be described as real-user performance.
- WorkManager can update offline predictions after daily aggregation, but the current model coefficients are still synthetic-only. Real collected usage is used as input features, not as proof of real-user model accuracy.
- The dashboard and rule review screen show prediction source, model version, calculation time, data quality, goal-risk text, `source_type`, `evaluation_scope`, and recommendation text. No production prediction backend is configured.
- Current model metrics are reproducible pipeline evidence on synthetic data only. Current source type: `synthetic`, evaluation scope: `synthetic evaluation`.
- The current synthetic evaluation beat baseline models, so Android synthetic-only model assets were updated, but this must not be presented as real-user model performance. Evidence: `ai/phone_outputs/training_manifest.json`, `app/src/main/assets/habitguard_model_profile.json`, `app/src/main/assets/android_inference_bundle.json`.
- Firestore sync is only a summary-only REST skeleton after opt-in consent; it is not production Firebase integration because there is no Firebase SDK, user auth, or project/token configuration UI.
- Android lint and build pass locally, but real-device permission/restriction behavior has not been recorded.
- The androidTest APK builds locally, but connected-device instrumentation execution was not run in the latest task because `adb devices` listed no connected device.
- Screen Timing UI screenshots and `uiautomator` dumps were saved from a connected tablet, but manual TalkBack, 200% font size, landscape, and dark-mode QA are still incomplete.
- The root-adjacent `screen_timing_synthetic_10500.csv` is synthetic scenario data only and must not be described as real user data.

## Do Not Say Yet

- Do not say the app fully blocks all apps or disables third-party apps at the OS level.
- Do not say the model accuracy/F1/RMSE reflects real users.
- Do not say real phone data has been used for the current generated model metrics unless a verified approved HabitGuard CSV export is supplied and documented.
- Do not say Firebase, FastAPI, Cloud Run, or production backend integration is complete.
- Do not say remote AI prediction is deployed; only DTO/client interfaces exist, while current working inference is local JSON-bundle inference/cache/baseline.
- Do not say the local model is trained on real user data. Current `android_inference_bundle.json` has `source_type=synthetic` and `evaluation_scope=synthetic evaluation`.
- Do not say Android guard behavior is fully device-verified; current automated coverage is JVM tests for usage aggregation and Guard v2 evaluator, not instrumentation tests for AccessibilityService/LockActivity.
- Do not say Google Play approval, App Store approval, clinical effectiveness, or medical benefit has been proven.
- Do not say iOS Screen Time support is implemented in this Android project.

## Future Plan Only

- Train and evaluate the pipeline with an approved real HabitGuard CSV export.
- Add Android unit/instrumentation tests for parser, risk logic, unlock expiry, mission validation, Room/Firestore payloads, and guard flows.
- Add real-device test result logs for UsageStats, AccessibilityService, notification listener, lock/mission flows, and CSV export.
- Add production-ready auth/configuration before any cloud sync claim.
- Prepare Google Play policy language and release signing documentation.
