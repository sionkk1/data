package com.habitguard.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.habitguard.app.model.AppUsageItem
import com.habitguard.app.model.HourlyUsageItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageStatsRepository(private val context: Context) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun queryTodayUsage(): List<AppUsageItem> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return queryUsage(start, System.currentTimeMillis())
    }

    fun queryTodayHourlyUsage(): List<HourlyUsageItem> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val buckets = LongArray(24)
        val foregroundStarts = mutableMapOf<String, Long>()

        queryEventRecords(start, end).events
            .filter { it.kind == UsageEventKind.Foreground || it.kind == UsageEventKind.Background }
            .forEach { event ->
                when (event.kind) {
                    UsageEventKind.Foreground -> foregroundStarts.putIfAbsent(event.packageName, event.timestampMillis)
                    UsageEventKind.Background -> {
                        val startedAt = foregroundStarts.remove(event.packageName)
                        if (startedAt != null && event.timestampMillis > startedAt) {
                            addHourlyOverlap(startedAt, event.timestampMillis, buckets, zone)
                        }
                    }
                    else -> Unit
                }
            }

        foregroundStarts.values.forEach { startedAt ->
            addHourlyOverlap(startedAt, end, buckets, zone)
        }

        return (0..23).map { hour ->
            HourlyUsageItem(hour = hour, minutes = buckets[hour] / 60_000)
        }
    }

    fun aggregateDailyUsage(days: Int): UsageAggregationResult {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.minusDays((days - 1).coerceAtLeast(0).toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = System.currentTimeMillis()
        val records = queryEventRecords(start, end)
        return UsageEventAggregator.aggregate(
            events = records.events,
            rangeStartMillis = start,
            rangeEndMillis = end,
            zone = zone,
            categoryForPackage = ::appCategory,
            appNameForPackage = ::appLabel,
            hasUsagePermission = hasUsageAccess(),
            sourceUnavailable = records.sourceUnavailable,
            unknownError = records.unknownError,
        )
    }

    fun queryDailySummaries(days: Int): List<UsageDailyEntity> =
        aggregateDailyUsage(days).daily

    fun queryDailyAppUsage(days: Int): List<AppUsageDailyEntity> =
        aggregateDailyUsage(days).appDaily

    fun queryUsage(startMillis: Long, endMillis: Long): List<AppUsageItem> {
        val zone = ZoneId.systemDefault()
        val records = queryEventRecords(startMillis, endMillis)
        val result = UsageEventAggregator.aggregate(
            events = records.events,
            rangeStartMillis = startMillis,
            rangeEndMillis = endMillis,
            zone = zone,
            categoryForPackage = ::appCategory,
            appNameForPackage = ::appLabel,
            hasUsagePermission = hasUsageAccess(),
            sourceUnavailable = records.sourceUnavailable,
            unknownError = records.unknownError,
        )
        return result.appDaily
            .map {
                AppUsageItem(
                    packageName = it.packageName,
                    appName = it.appName,
                    minutes = it.usageMinutes,
                    lastTimeUsed = it.lastTimeUsed,
                    openCount = it.openCount,
                    firstOpenTime = it.firstOpenTime,
                    nightMinutes = it.nightMinutes,
                    sessionCount = it.sessionCount,
                    averageSessionMinutes = it.averageSessionMinutes,
                    maxSessionMinutes = it.maxSessionMinutes,
                    category = it.category,
                )
            }
            .sortedByDescending { it.minutes }
    }

    fun minutesForPackageToday(packageName: String): Long =
        queryTodayUsage().firstOrNull { it.packageName == packageName }?.minutes ?: 0

    fun launchableApps(): List<AppUsageItem> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map {
                val packageName = it.activityInfo.packageName
                AppUsageItem(
                    packageName = packageName,
                    appName = appLabel(packageName),
                    minutes = 0,
                    lastTimeUsed = 0,
                    category = appCategory(packageName),
                )
            }
            .filterNot { isIgnoredPackage(it.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    private fun queryEventRecords(startMillis: Long, endMillis: Long): UsageEventQueryResult {
        if (!hasUsageAccess()) return UsageEventQueryResult(events = emptyList())

        return runCatching {
            val records = mutableListOf<UsageEventRecord>()
            val events = usageStatsManager.queryEvents(startMillis, endMillis)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val kind = event.eventType.toUsageEventKind() ?: continue
                val packageName = event.packageName?.takeIf { it.isNotBlank() } ?: SYSTEM_EVENT_PACKAGE
                if (kind.isAppSessionEvent && isIgnoredPackage(packageName)) continue
                records += UsageEventRecord(
                    packageName = packageName,
                    timestampMillis = event.timeStamp.coerceIn(startMillis, endMillis),
                    kind = kind,
                )
            }
            UsageEventQueryResult(events = records)
        }.getOrElse {
            UsageEventQueryResult(events = emptyList(), sourceUnavailable = true, unknownError = true)
        }
    }

    private fun Int.toUsageEventKind(): UsageEventKind? =
        when {
            isForegroundEvent(this) -> UsageEventKind.Foreground
            isBackgroundEvent(this) -> UsageEventKind.Background
            this == EVENT_DEVICE_SHUTDOWN -> UsageEventKind.DeviceShutdown
            this == EVENT_DEVICE_STARTUP -> UsageEventKind.DeviceStartup
            else -> null
        }

    private val UsageEventKind.isAppSessionEvent: Boolean
        get() = this == UsageEventKind.Foreground || this == UsageEventKind.Background

    private fun isForegroundEvent(eventType: Int): Boolean =
        eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && eventType == UsageEvents.Event.ACTIVITY_RESUMED)

    private fun isBackgroundEvent(eventType: Int): Boolean =
        eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && eventType == UsageEvents.Event.ACTIVITY_PAUSED)

    private fun appLabel(packageName: String): String =
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)

    private fun appCategory(packageName: String): String {
        val label = appLabel(packageName).lowercase()
        val packageText = packageName.lowercase()
        return when {
            "youtube" in packageText || "netflix" in packageText || "video" in packageText ||
                "tving" in packageText || "wavve" in packageText -> UsageCategoryLabels.VIDEO
            "instagram" in packageText || "facebook" in packageText || "kakao" in packageText ||
                "twitter" in packageText || "xhs" in packageText || "sns" in label -> UsageCategoryLabels.SNS
            "game" in packageText || "\uAC8C\uC784" in label -> UsageCategoryLabels.GAME
            "docs" in packageText || "notion" in packageText || "classroom" in packageText ||
                "todo" in packageText || "calendar" in packageText || "study" in packageText ->
                UsageCategoryLabels.PRODUCTIVITY
            else -> UsageCategoryLabels.OTHER
        }
    }

    private fun isIgnoredPackage(packageName: String): Boolean =
        IgnoredPackages.isUsageNoise(packageName, context.packageName)

    private fun addHourlyOverlap(startMillis: Long, endMillis: Long, buckets: LongArray, zone: ZoneId) {
        if (endMillis <= startMillis) return
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        val end = Instant.ofEpochMilli(endMillis).atZone(zone)
        var cursor = start.toLocalDate().atTime(start.hour, 0).atZone(zone)
        while (cursor.isBefore(end)) {
            val next = cursor.plusHours(1)
            if (cursor.toLocalDate() == LocalDate.now(zone)) {
                buckets[cursor.hour] += overlapMillis(start, end, cursor, next)
            }
            cursor = next
        }
    }

    private fun overlapMillis(
        start: ZonedDateTime,
        end: ZonedDateTime,
        windowStart: ZonedDateTime,
        windowEnd: ZonedDateTime,
    ): Long {
        val overlapStart = maxOf(start.toInstant().toEpochMilli(), windowStart.toInstant().toEpochMilli())
        val overlapEnd = minOf(end.toInstant().toEpochMilli(), windowEnd.toInstant().toEpochMilli())
        return (overlapEnd - overlapStart).coerceAtLeast(0)
    }

    private data class UsageEventQueryResult(
        val events: List<UsageEventRecord>,
        val sourceUnavailable: Boolean = false,
        val unknownError: Boolean = false,
    )

    private companion object {
        private const val SYSTEM_EVENT_PACKAGE = "android"
        private const val EVENT_DEVICE_SHUTDOWN = 26
        private const val EVENT_DEVICE_STARTUP = 27
    }
}
