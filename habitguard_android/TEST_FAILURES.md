# HabitGuard Test Failure Log

Record every non-TDD verification failure here. TDD RED failures can be summarized when they affect the final quality gate.

## 2026-06-21 - Parallel Gradle Unit Test Cache Collision

Failure cause:
- Two `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests ...` commands were run in parallel.
- Kotlin incremental compilation tried to write the same `app/build/kotlin/kaptGenerateStubsDebugUnitTestKotlin` cache files from two Gradle processes.

Reproduction:
- Run `RestrictionEvaluatorTest` and `UsageEventAggregatorTest` in two separate Gradle processes at the same time.

Impacted screen or feature:
- No app runtime feature was affected.
- Local verification was affected because one Gradle process failed before executing the requested tests.

Fix status:
- Fixed operationally by running Gradle verification sequentially.
- `scripts/quality_gate.py` runs Gradle commands sequentially to avoid this cache collision.
- Sequential reruns of `RestrictionEvaluatorTest` and `UsageEventAggregatorTest` passed.

## Current Known Test Gaps

- Full AccessibilityService and LockActivity bypass flows still need connected-device or instrumentation verification.
- Room has current-schema verification assets, but explicit version-to-version migrations are not implemented because the app still uses destructive fallback migration.
- Compose Semantics tests compile as androidTest APK; connected-device execution must be run separately.
