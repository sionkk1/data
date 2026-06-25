# HabitGuard Room Migration Policy

Last updated: 2026-06-21 KST

## Current Database

- Runtime database file: `habitguard.db`
- Room database class: `app/src/main/java/com/habitguard/app/data/HabitGuardDatabase.kt`
- Previous version: `10`
- Current version: `11`
- Exported schemas:
  - `app/schemas/com.habitguard.app.data.HabitGuardDatabase/10.json`
  - `app/schemas/com.habitguard.app.data.HabitGuardDatabase/11.json`
- Runtime builder: `ServiceLocator.database()` registers explicit migrations and does not use destructive migration fallback.

## Migration 10 to 11

`HabitGuardDatabaseMigrations.MIGRATION_10_11` is a schema-preserving migration. It exists to remove destructive fallback while keeping all version 10 tables and columns intact.

Preserved tables:

- `restriction_rule`
- `mission_log`
- `guard_event`
- `unlock_session`
- `usage_daily`
- `app_usage_daily`
- `notification_daily`
- `user_goal`
- `prediction_result`

## Test Requirement

Any future entity change must:

1. Increase `HabitGuardDatabase` version.
2. Export the new schema JSON under `app/schemas/`.
3. Add an explicit `Migration(oldVersion, newVersion)`.
4. Register the migration in `ServiceLocator.database()`.
5. Add or extend `RoomMigrationVerificationTest` with representative rows for user-owned data.
6. Run at least:
   - `.\gradlew.bat --no-daemon :app:testDebugUnitTest`
   - `.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest`
   - `.\gradlew.bat --no-daemon :app:lintDebug`
   - `.\gradlew.bat --no-daemon :app:assembleDebug`

## Breaking Change Policy

Do not automatically delete `habitguard.db` for breaking changes.

If a future change cannot preserve the existing database in place, the release plan must include:

1. A user-facing notice that explains what data cannot be migrated.
2. A local export path before upgrade, such as CSV export for usage summaries and rules.
3. A backup or rollback strategy for the old database file where technically possible.
4. A manual confirmation step before local data is removed.
5. A documented entry in `TECH_RISKS.md` and `PROJECT_AUDIT.md`.

This task did not identify a breaking schema change that requires deleting the database.
