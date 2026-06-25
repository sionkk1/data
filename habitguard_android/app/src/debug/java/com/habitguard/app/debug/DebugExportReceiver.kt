package com.habitguard.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.habitguard.app.data.IgnoredPackages
import com.habitguard.app.data.ServiceLocator
import com.habitguard.app.data.UsageStatsRepository
import kotlinx.coroutines.runBlocking
import java.io.File

class DebugExportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_EXPORT_CSV) return

        val pendingResult = goAsync()
        Thread {
            try {
                val output = exportCsv(context.applicationContext)
                Log.i(TAG, "Debug CSV export written.")
            } catch (error: Throwable) {
                Log.e(TAG, "Debug CSV export failed", error)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun exportCsv(context: Context): File = runBlocking {
        val database = ServiceLocator.database(context)
        val usageRepository = UsageStatsRepository(context)
        if (usageRepository.hasUsageAccess()) {
            usageRepository.queryDailySummaries(days = 30).forEach {
                database.usageDailyDao().upsert(it)
            }
            database.appUsageDailyDao().upsertAll(
                usageRepository.queryDailyAppUsage(days = 30),
            )
        }

        val rows = database.appUsageDailyDao()
            .recent(1000)
            .filterNot { IgnoredPackages.isUsageNoise(it.packageName, context.packageName) }
            .reversed()
        val notifications = database.notificationDailyDao()
            .recent(1000)
            .associateBy { it.date to it.packageName }
        val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val csv = File(exportDir, "habitguard_30_day_app_usage.csv")
        val header = "date,package_name,app_name,category,usage_minutes,night_minutes,open_count,notification_count,first_open_time,last_time_used,captured_at"
        val body = rows.joinToString("\n") {
            val notificationCount = notifications[it.date to it.packageName]?.notificationCount ?: 0
            listOf(
                it.date,
                it.packageName,
                it.appName.replace("\"", "\"\""),
                it.category,
                it.usageMinutes,
                it.nightMinutes,
                it.openCount,
                notificationCount,
                it.firstOpenTime,
                it.lastTimeUsed,
                it.capturedAt,
            ).joinToString(",") { value -> "\"$value\"" }
        }
        csv.writeText("$header\n$body", Charsets.UTF_8)
        csv
    }

    companion object {
        const val ACTION_EXPORT_CSV = "com.habitguard.app.DEBUG_EXPORT_CSV"
        private const val TAG = "HabitGuardDebugExport"
    }
}
