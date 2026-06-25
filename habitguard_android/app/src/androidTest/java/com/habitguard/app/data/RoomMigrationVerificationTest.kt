package com.habitguard.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationVerificationTest {
    @Test
    fun migration7To11PreservesExistingDeviceDataAndAddsSafeDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB_V7)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB_V7), null).apply {
            createVersion7Schema()
            insertVersion7UserData()
            close()
        }

        val roomDb = openMigratedRoomDatabase(context, TEST_DB_V7)
        val migrated = roomDb.openHelper.writableDatabase
        migrated.assertVersion7UserDataPreservedWithSafeDefaults()
        roomDb.close()
        context.deleteDatabase(TEST_DB_V7)
    }

    @Test
    fun migration10To11PreservesUserDataTables() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null).apply {
            createSchemaFromExportedRoomAsset(PREVIOUS_VERSION)
            insertVersion10UserData()
            close()
        }

        val roomDb = openMigratedRoomDatabase(context, TEST_DB)
        val migrated = roomDb.openHelper.writableDatabase
        migrated.assertVersion10UserDataPreserved()

        assertNotNull(roomDb.ruleDao())
        assertNotNull(roomDb.predictionResultDao())
        roomDb.close()
        context.deleteDatabase(TEST_DB)
    }

    private fun openMigratedRoomDatabase(context: Context, name: String): HabitGuardDatabase {
        return Room.databaseBuilder(context, HabitGuardDatabase::class.java, name)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(
                HabitGuardDatabaseMigrations.MIGRATION_7_11,
                HabitGuardDatabaseMigrations.MIGRATION_10_11,
            )
            .build()
    }

    private fun SQLiteDatabase.createSchemaFromExportedRoomAsset(version: Int) {
        val assetPath = "com.habitguard.app.data.HabitGuardDatabase/$version.json"
        val schemaJson = InstrumentationRegistry.getInstrumentation().context.assets
            .open(assetPath)
            .bufferedReader()
            .use { it.readText() }
        val database = JSONObject(schemaJson).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val tableName = entity.getString("tableName")
            val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
            execSQL(createSql)
        }
        execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        execSQL(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, ?)",
            arrayOf(database.getString("identityHash")),
        )
        this.version = version
    }

    private fun SQLiteDatabase.createVersion7Schema() {
        execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        execSQL(
            """
            CREATE TABLE `restriction_rule` (
                `targetPackage` TEXT NOT NULL,
                `targetName` TEXT NOT NULL,
                `limitMinutes` INTEGER NOT NULL,
                `startHour` INTEGER NOT NULL,
                `endHour` INTEGER NOT NULL,
                `restrictionMode` TEXT NOT NULL,
                `missionType` TEXT NOT NULL,
                `unlockMinutes` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                PRIMARY KEY(`targetPackage`)
            )
            """.trimIndent(),
        )
        execSQL(
            """
            CREATE TABLE `usage_daily` (
                `date` TEXT NOT NULL,
                `totalScreenTimeMinutes` INTEGER NOT NULL,
                `nightMinutes` INTEGER NOT NULL,
                `unlockCountEstimate` INTEGER NOT NULL,
                `topAppPackage` TEXT NOT NULL,
                `topAppName` TEXT NOT NULL,
                `topAppMinutes` INTEGER NOT NULL,
                `capturedAt` INTEGER NOT NULL,
                PRIMARY KEY(`date`)
            )
            """.trimIndent(),
        )
        execSQL(
            """
            CREATE TABLE `app_usage_daily` (
                `date` TEXT NOT NULL,
                `packageName` TEXT NOT NULL,
                `appName` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `usageMinutes` INTEGER NOT NULL,
                `nightMinutes` INTEGER NOT NULL,
                `openCount` INTEGER NOT NULL,
                `firstOpenTime` INTEGER NOT NULL,
                `lastTimeUsed` INTEGER NOT NULL,
                `capturedAt` INTEGER NOT NULL,
                PRIMARY KEY(`date`, `packageName`)
            )
            """.trimIndent(),
        )
        execSQL(
            """
            CREATE TABLE `mission_log` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `targetPackage` TEXT NOT NULL,
                `missionType` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `completedAt` INTEGER NOT NULL,
                `success` INTEGER NOT NULL,
                `unlockMinutesGranted` INTEGER NOT NULL,
                `reasonText` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        execSQL(
            """
            CREATE TABLE `notification_daily` (
                `date` TEXT NOT NULL,
                `packageName` TEXT NOT NULL,
                `appName` TEXT NOT NULL,
                `notificationCount` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`date`, `packageName`)
            )
            """.trimIndent(),
        )
        execSQL(
            """
            CREATE TABLE `user_goal` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `rawText` TEXT NOT NULL,
                `targetName` TEXT NOT NULL,
                `targetPackage` TEXT NOT NULL,
                `timeRange` TEXT NOT NULL,
                `limitMinutes` INTEGER NOT NULL,
                `intensity` TEXT NOT NULL,
                `missionType` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        execSQL(
            """
            CREATE TABLE `prediction_result` (
                `date` TEXT NOT NULL,
                `predictedNextDayMinutes` INTEGER NOT NULL,
                `riskLevel` TEXT NOT NULL,
                `habitType` TEXT NOT NULL,
                `mainReason` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`date`)
            )
            """.trimIndent(),
        )
        this.version = 7
    }

    private fun SQLiteDatabase.insertVersion7UserData() {
        execSQL(
            "INSERT INTO restriction_rule VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>("com.example.video", "Video App", 45, 23, 6, "limit", "math", 10, 1),
        )
        execSQL(
            "INSERT INTO usage_daily VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>("2026-06-20", 120L, 30L, 9, "com.example.video", "Video App", 60L, 6000L),
        )
        execSQL(
            "INSERT INTO app_usage_daily VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>("2026-06-20", "com.example.video", "Video App", "video", 60L, 20L, 4, 100L, 200L, 6000L),
        )
        execSQL(
            "INSERT INTO mission_log VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(1L, "com.example.video", "math", 1000L, 2000L, 1, 10, "passed"),
        )
        execSQL(
            "INSERT INTO notification_daily VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any>("2026-06-20", "com.example.video", "Video App", 5, 7000L),
        )
        execSQL(
            "INSERT INTO user_goal VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(1L, "Video 45 minutes", "Video App", "com.example.video", "23:00-06:00", 45, "medium", "math", 8000L, 1),
        )
        execSQL(
            "INSERT INTO prediction_result VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>("2026-06-21", 130L, "high", "night_video", "night usage", 9000L),
        )
    }

    private fun SQLiteDatabase.insertVersion10UserData() {
        execSQL(
            """
            INSERT INTO restriction_rule (
                targetPackage, targetName, limitMinutes, startHour, endHour,
                restrictionMode, missionType, unlockMinutes, approved,
                activeDaysMask, sessionLimitMinutes, maxUnlocksPerDay,
                emergencyUnlockMinutes, enabled
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                "com.example.video",
                "Video App",
                45,
                23,
                6,
                "limit_exceeded_then_mission",
                "math",
                10,
                1,
                127,
                20,
                3,
                5,
                1,
            ),
        )
        execSQL(
            """
            INSERT INTO mission_log (
                id, targetPackage, missionType, startedAt, completedAt,
                success, unlockMinutesGranted, reasonText
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(1L, "com.example.video", "math", 1000L, 2000L, 1, 10, "passed"),
        )
        execSQL(
            """
            INSERT INTO guard_event (
                id, packageName, eventType, blocked, unlockTokenValid,
                missionResult, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(1L, "com.example.video", "lock_shown", 1, 0, "started", 3000L),
        )
        execSQL(
            """
            INSERT INTO unlock_session (
                id, packageName, reason, issuedAtElapsedRealtime,
                expiresAtElapsedRealtime, issuedAtWallClock, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(1L, "com.example.video", "mission_success", 4000L, 7000L, 5000L, 5000L),
        )
        execSQL(
            """
            INSERT INTO usage_daily (
                date, totalScreenTimeMinutes, nightMinutes, unlockCountEstimate,
                topAppPackage, topAppName, topAppMinutes, videoMinutes,
                snsMinutes, gameMinutes, productivityMinutes, otherMinutes,
                sessionCount, averageSessionMinutes, maxSessionMinutes,
                dataQuality, collectionNote, capturedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                "2026-06-20",
                120L,
                30L,
                9,
                "com.example.video",
                "Video App",
                60L,
                60L,
                20L,
                10L,
                15L,
                15L,
                8,
                15L,
                40L,
                "COMPLETE",
                "captured",
                6000L,
            ),
        )
        execSQL(
            """
            INSERT INTO app_usage_daily (
                date, packageName, appName, category, usageMinutes, nightMinutes,
                openCount, sessionCount, averageSessionMinutes,
                maxSessionMinutes, firstOpenTime, lastTimeUsed, capturedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                "2026-06-20",
                "com.example.video",
                "Video App",
                "video",
                60L,
                20L,
                4,
                3,
                20L,
                35L,
                100L,
                200L,
                6000L,
            ),
        )
        execSQL(
            """
            INSERT INTO notification_daily (
                date, packageName, appName, notificationCount, updatedAt
            ) VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("2026-06-20", "com.example.video", "Video App", 5, 7000L),
        )
        execSQL(
            """
            INSERT INTO user_goal (
                id, rawText, targetName, targetPackage, timeRange, limitMinutes,
                intensity, missionType, createdAt, isActive
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                1L,
                "YouTube 45분 이하",
                "Video App",
                "com.example.video",
                "23:00-06:00",
                45,
                "medium",
                "math",
                8000L,
                1,
            ),
        )
        execSQL(
            """
            INSERT INTO prediction_result (
                date, predictedNextDayMinutes, riskLevel, habitType,
                mainReason, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("2026-06-21", 130L, "high", "night_video", "night usage", 9000L),
        )
    }

    private fun SupportSQLiteDatabase.assertVersion10UserDataPreserved() {
        assertEquals(1, countRows("restriction_rule"))
        assertEquals(1, countRows("mission_log"))
        assertEquals(1, countRows("guard_event"))
        assertEquals(1, countRows("unlock_session"))
        assertEquals(1, countRows("usage_daily"))
        assertEquals(1, countRows("app_usage_daily"))
        assertEquals(1, countRows("notification_daily"))
        assertEquals(1, countRows("user_goal"))
        assertEquals(1, countRows("prediction_result"))

        query("SELECT targetPackage, approved, enabled FROM restriction_rule").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("com.example.video", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(1, cursor.getInt(2))
        }
        query("SELECT totalScreenTimeMinutes, dataQuality FROM usage_daily WHERE date = '2026-06-20'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(120L, cursor.getLong(0))
            assertEquals("COMPLETE", cursor.getString(1))
        }
        query("SELECT predictedNextDayMinutes, riskLevel FROM prediction_result WHERE date = '2026-06-21'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(130L, cursor.getLong(0))
            assertEquals("high", cursor.getString(1))
        }
    }

    private fun SupportSQLiteDatabase.assertVersion7UserDataPreservedWithSafeDefaults() {
        assertEquals(1, countRows("restriction_rule"))
        assertEquals(1, countRows("mission_log"))
        assertEquals(0, countRows("guard_event"))
        assertEquals(0, countRows("unlock_session"))
        assertEquals(1, countRows("usage_daily"))
        assertEquals(1, countRows("app_usage_daily"))
        assertEquals(1, countRows("notification_daily"))
        assertEquals(1, countRows("user_goal"))
        assertEquals(1, countRows("prediction_result"))

        query("SELECT targetPackage, approved, enabled, activeDaysMask FROM restriction_rule").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("com.example.video", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(127, cursor.getInt(3))
        }
        query("SELECT totalScreenTimeMinutes, dataQuality, sessionCount FROM usage_daily WHERE date = '2026-06-20'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(120L, cursor.getLong(0))
            assertEquals("UNKNOWN_ERROR", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
        query("SELECT usageMinutes, sessionCount FROM app_usage_daily WHERE date = '2026-06-20'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(60L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
        }
    }

    private fun SupportSQLiteDatabase.countRows(tableName: String): Int {
        query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private companion object {
        const val TEST_DB = "habitguard-migration-test"
        const val TEST_DB_V7 = "habitguard-v7-migration-test"
        const val PREVIOUS_VERSION = 10
        const val CURRENT_VERSION = 11
    }
}
