package com.habitguard.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "restriction_rule")
data class RestrictionRuleEntity(
    @PrimaryKey val targetPackage: String,
    val targetName: String,
    val limitMinutes: Int,
    val startHour: Int,
    val endHour: Int,
    val restrictionMode: String = "한도 초과 시 미션",
    val missionType: String,
    val unlockMinutes: Int,
    val approved: Boolean = false,
    val activeDaysMask: Int = 127,
    val sessionLimitMinutes: Int = 0,
    val maxUnlocksPerDay: Int = 3,
    val emergencyUnlockMinutes: Int = 5,
    val enabled: Boolean,
)

@Entity(tableName = "mission_log")
data class MissionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetPackage: String,
    val missionType: String,
    val startedAt: Long,
    val completedAt: Long,
    val success: Boolean,
    val unlockMinutesGranted: Int,
    val reasonText: String,
)

@Entity(tableName = "guard_event")
data class GuardEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val eventType: String,
    val blocked: Boolean,
    val unlockTokenValid: Boolean,
    val missionResult: String,
    val createdAt: Long,
)

@Entity(tableName = "unlock_session")
data class UnlockSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val reason: String,
    val issuedAtElapsedRealtime: Long,
    val expiresAtElapsedRealtime: Long,
    val issuedAtWallClock: Long,
    val createdAt: Long,
)

@Entity(tableName = "usage_daily")
data class UsageDailyEntity(
    @PrimaryKey val date: String,
    val totalScreenTimeMinutes: Long,
    val nightMinutes: Long,
    val unlockCountEstimate: Int,
    val topAppPackage: String,
    val topAppName: String,
    val topAppMinutes: Long,
    val videoMinutes: Long = 0,
    val snsMinutes: Long = 0,
    val gameMinutes: Long = 0,
    val productivityMinutes: Long = 0,
    val otherMinutes: Long = 0,
    val sessionCount: Int = 0,
    val averageSessionMinutes: Long = 0,
    val maxSessionMinutes: Long = 0,
    val dataQuality: String = "UNKNOWN_ERROR",
    val collectionNote: String = "",
    val capturedAt: Long,
)

@Entity(tableName = "app_usage_daily", primaryKeys = ["date", "packageName"])
data class AppUsageDailyEntity(
    val date: String,
    val packageName: String,
    val appName: String,
    val category: String,
    val usageMinutes: Long,
    val nightMinutes: Long,
    val openCount: Int,
    val sessionCount: Int = 0,
    val averageSessionMinutes: Long = 0,
    val maxSessionMinutes: Long = 0,
    val firstOpenTime: Long,
    val lastTimeUsed: Long,
    val capturedAt: Long,
)

@Entity(tableName = "notification_daily", primaryKeys = ["date", "packageName"])
data class NotificationDailyEntity(
    val date: String,
    val packageName: String,
    val appName: String,
    val notificationCount: Int,
    val updatedAt: Long,
)

@Entity(tableName = "user_goal")
data class UserGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawText: String,
    val targetName: String,
    val targetPackage: String,
    val timeRange: String,
    val limitMinutes: Int,
    val intensity: String,
    val missionType: String,
    val createdAt: Long,
    val isActive: Boolean,
)

@Entity(tableName = "prediction_result")
data class PredictionResultEntity(
    @PrimaryKey val date: String,
    val predictedNextDayMinutes: Long,
    val riskLevel: String,
    val habitType: String,
    val mainReason: String,
    val createdAt: Long,
)

@Dao
interface RestrictionRuleDao {
    @Query("SELECT * FROM restriction_rule WHERE enabled = 1 AND approved = 1")
    suspend fun enabledRules(): List<RestrictionRuleEntity>

    @Query("SELECT * FROM restriction_rule WHERE targetPackage = :packageName AND enabled = 1 AND approved = 1 LIMIT 1")
    suspend fun enabledRuleFor(packageName: String): RestrictionRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RestrictionRuleEntity)

    @Query("UPDATE restriction_rule SET enabled = 0 WHERE targetPackage = :packageName")
    suspend fun disable(packageName: String)
}

@Dao
interface MissionLogDao {
    @Insert
    suspend fun insert(log: MissionLogEntity)

    @Query("SELECT COUNT(*) FROM mission_log WHERE startedAt >= :sinceMillis")
    suspend fun countSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM mission_log WHERE startedAt >= :sinceMillis AND success = 1")
    suspend fun successCountSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM mission_log WHERE startedAt >= :sinceMillis AND success = 0")
    suspend fun failureCountSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM mission_log WHERE targetPackage = :packageName AND startedAt >= :sinceMillis AND success = 1")
    suspend fun successCountForPackageSince(packageName: String, sinceMillis: Long): Int
}

@Dao
interface GuardEventDao {
    @Insert
    suspend fun insert(event: GuardEventEntity)

    @Query("SELECT * FROM guard_event ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 20): List<GuardEventEntity>
}

@Dao
interface UnlockSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UnlockSessionEntity)

    @Query("SELECT * FROM unlock_session WHERE packageName = :packageName ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForPackage(packageName: String): UnlockSessionEntity?
}

@Dao
interface UsageDailyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sample: UsageDailyEntity)

    @Query("SELECT * FROM usage_daily ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int = 30): List<UsageDailyEntity>
}

@Dao
interface AppUsageDailyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<AppUsageDailyEntity>)

    @Query("DELETE FROM app_usage_daily WHERE date BETWEEN :startDate AND :endDate")
    suspend fun deleteBetween(startDate: String, endDate: String)

    @Query("SELECT * FROM app_usage_daily ORDER BY date DESC, usageMinutes DESC LIMIT :limit")
    suspend fun recent(limit: Int = 500): List<AppUsageDailyEntity>
}

@Dao
interface NotificationDailyDao {
    @Query("SELECT * FROM notification_daily WHERE date = :date AND packageName = :packageName LIMIT 1")
    suspend fun find(date: String, packageName: String): NotificationDailyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sample: NotificationDailyEntity)

    @Query("SELECT COALESCE(SUM(notificationCount), 0) FROM notification_daily WHERE date = :date")
    suspend fun totalForDate(date: String): Int

    @Query("SELECT * FROM notification_daily ORDER BY date DESC, notificationCount DESC LIMIT :limit")
    suspend fun recent(limit: Int = 500): List<NotificationDailyEntity>
}

@Dao
interface UserGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: UserGoalEntity)

    @Query("UPDATE user_goal SET isActive = 0")
    suspend fun deactivateAll()

    @Query("SELECT * FROM user_goal WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun active(): UserGoalEntity?

    @Query("SELECT * FROM user_goal ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 20): List<UserGoalEntity>
}

@Dao
interface PredictionResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prediction: PredictionResultEntity)

    @Query("SELECT * FROM prediction_result ORDER BY date DESC LIMIT 1")
    suspend fun latest(): PredictionResultEntity?

    @Query("SELECT * FROM prediction_result ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int = 30): List<PredictionResultEntity>
}

@Dao
interface MaintenanceDao {
    @Query("DELETE FROM restriction_rule")
    suspend fun clearRestrictionRules()

    @Query("DELETE FROM mission_log")
    suspend fun clearMissionLogs()

    @Query("DELETE FROM usage_daily")
    suspend fun clearUsageDaily()

    @Query("DELETE FROM app_usage_daily")
    suspend fun clearAppUsageDaily()

    @Query("DELETE FROM notification_daily")
    suspend fun clearNotificationDaily()

    @Query("DELETE FROM user_goal")
    suspend fun clearUserGoals()

    @Query("DELETE FROM prediction_result")
    suspend fun clearPredictionResults()

    @Query("DELETE FROM guard_event")
    suspend fun clearGuardEvents()

    @Query("DELETE FROM unlock_session")
    suspend fun clearUnlockSessions()
}

@Database(
    entities = [
        RestrictionRuleEntity::class,
        MissionLogEntity::class,
        GuardEventEntity::class,
        UnlockSessionEntity::class,
        UsageDailyEntity::class,
        AppUsageDailyEntity::class,
        NotificationDailyEntity::class,
        UserGoalEntity::class,
        PredictionResultEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class HabitGuardDatabase : RoomDatabase() {
    abstract fun ruleDao(): RestrictionRuleDao
    abstract fun missionLogDao(): MissionLogDao
    abstract fun guardEventDao(): GuardEventDao
    abstract fun unlockSessionDao(): UnlockSessionDao
    abstract fun usageDailyDao(): UsageDailyDao
    abstract fun appUsageDailyDao(): AppUsageDailyDao
    abstract fun notificationDailyDao(): NotificationDailyDao
    abstract fun userGoalDao(): UserGoalDao
    abstract fun predictionResultDao(): PredictionResultDao
    abstract fun maintenanceDao(): MaintenanceDao
}

object HabitGuardDatabaseMigrations {
    val MIGRATION_7_11 = object : Migration(7, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateTo11(db)
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateTo11(db)
        }
    }

    private fun migrateTo11(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("restriction_rule", "approved", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("restriction_rule", "activeDaysMask", "INTEGER NOT NULL DEFAULT 127")
        db.addColumnIfMissing("restriction_rule", "sessionLimitMinutes", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("restriction_rule", "maxUnlocksPerDay", "INTEGER NOT NULL DEFAULT 3")
        db.addColumnIfMissing("restriction_rule", "emergencyUnlockMinutes", "INTEGER NOT NULL DEFAULT 5")

        db.addColumnIfMissing("usage_daily", "videoMinutes", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("usage_daily", "snsMinutes", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("usage_daily", "gameMinutes", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("usage_daily", "productivityMinutes", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("usage_daily", "otherMinutes", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("usage_daily", "sessionCount", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("usage_daily", "averageSessionMinutes", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("usage_daily", "maxSessionMinutes", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("usage_daily", "dataQuality", "TEXT NOT NULL DEFAULT 'UNKNOWN_ERROR'")
        db.addColumnIfMissing("usage_daily", "collectionNote", "TEXT NOT NULL DEFAULT ''")

        db.addColumnIfMissing("app_usage_daily", "sessionCount", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("app_usage_daily", "averageSessionMinutes", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("app_usage_daily", "maxSessionMinutes", "INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `guard_event` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `packageName` TEXT NOT NULL,
                `eventType` TEXT NOT NULL,
                `blocked` INTEGER NOT NULL,
                `unlockTokenValid` INTEGER NOT NULL,
                `missionResult` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `unlock_session` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `packageName` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `issuedAtElapsedRealtime` INTEGER NOT NULL,
                `expiresAtElapsedRealtime` INTEGER NOT NULL,
                `issuedAtWallClock` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.addColumnIfMissing(tableName: String, columnName: String, definition: String) {
        if (hasColumn(tableName, columnName)) return
        execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $definition")
    }

    private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) return true
            }
        }
        return false
    }
}
