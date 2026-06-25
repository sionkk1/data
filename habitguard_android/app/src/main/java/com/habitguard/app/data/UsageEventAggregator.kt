package com.habitguard.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class UsageEventKind {
    Foreground,
    Background,
    DeviceShutdown,
    DeviceStartup,
    TimezoneChanged,
}

enum class UsageDataQuality {
    COMPLETE,
    PARTIAL_PERMISSION,
    APP_TERMINATED,
    DEVICE_REBOOTED,
    PLATFORM_UNAVAILABLE,
    UNKNOWN_ERROR,
}

data class UsageEventRecord(
    val packageName: String,
    val timestampMillis: Long,
    val kind: UsageEventKind,
)

data class RestoredUsageSession(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
    val inferredEnd: Boolean,
)

data class UsageAggregationResult(
    val daily: List<UsageDailyEntity>,
    val appDaily: List<AppUsageDailyEntity>,
    val sessions: List<RestoredUsageSession>,
)

object UsageCategoryLabels {
    const val VIDEO = "\uC601\uC0C1"
    const val SNS = "SNS"
    const val GAME = "\uAC8C\uC784"
    const val PRODUCTIVITY = "\uC0DD\uC0B0\uC131"
    const val OTHER = "\uAE30\uD0C0"
}

object UsageEventAggregator {
    fun aggregate(
        events: List<UsageEventRecord>,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        zone: ZoneId,
        categoryForPackage: (String) -> String,
        appNameForPackage: (String) -> String,
        hasUsagePermission: Boolean,
        sourceUnavailable: Boolean = false,
        unknownError: Boolean = false,
        capturedAt: Long = System.currentTimeMillis(),
    ): UsageAggregationResult {
        val dates = datesBetween(rangeStartMillis, rangeEndMillis, zone)
        if (!hasUsagePermission) {
            return UsageAggregationResult(
                daily = dates.map {
                    emptyDaily(
                        date = it,
                        quality = UsageDataQuality.PARTIAL_PERMISSION,
                        note = "usage_permission_missing",
                        capturedAt = capturedAt,
                    )
                },
                appDaily = emptyList(),
                sessions = emptyList(),
            )
        }

        val qualityByDate = dates.associateWith { UsageDataQuality.COMPLETE }.toMutableMap()
        val notesByDate = dates.associateWith { mutableSetOf<String>() }.toMutableMap()
        if (sourceUnavailable || unknownError) {
            dates.forEach {
                qualityByDate[it] = if (unknownError) UsageDataQuality.UNKNOWN_ERROR else UsageDataQuality.PLATFORM_UNAVAILABLE
                notesByDate[it]?.add(if (unknownError) "usage_query_error" else "usage_source_unavailable")
            }
        }

        val sessions = restoreSessions(events, rangeStartMillis, rangeEndMillis, zone, qualityByDate, notesByDate)
        val appAccumulators = linkedMapOf<Pair<LocalDate, String>, AppDayAccumulator>()

        sessions.forEach { session ->
            splitSessionByDay(session, zone).forEach { portion ->
                val minutes = millisToMinutes(portion.durationMillis)
                if (minutes <= 0) return@forEach

                val date = portion.date
                val key = date to session.packageName
                val accumulator = appAccumulators.getOrPut(key) {
                    AppDayAccumulator(
                        date = date,
                        packageName = session.packageName,
                        appName = appNameForPackage(session.packageName),
                        category = categoryForPackage(session.packageName),
                    )
                }
                accumulator.usageMinutes += minutes
                accumulator.nightMinutes += millisToMinutes(nightOverlapMillis(portion.startMillis, portion.endMillis, zone))
                accumulator.firstOpenTime = minNonZero(accumulator.firstOpenTime, portion.startMillis)
                accumulator.lastTimeUsed = maxOf(accumulator.lastTimeUsed, portion.endMillis)
                accumulator.sessionCount += 1
                accumulator.totalSessionMinutes += minutes
                accumulator.maxSessionMinutes = maxOf(accumulator.maxSessionMinutes, minutes)

                if (session.inferredEnd) {
                    qualityByDate[date] = worseQuality(qualityByDate[date], UsageDataQuality.APP_TERMINATED)
                    notesByDate[date]?.add("missing_background")
                }
            }
        }

        val appDaily = appAccumulators.values
            .filter { it.usageMinutes > 0 }
            .map { it.toEntity(capturedAt) }
            .sortedWith(compareBy<AppUsageDailyEntity> { it.date }.thenByDescending { it.usageMinutes })

        val appsByDate = appDaily.groupBy { LocalDate.parse(it.date) }
        val daily = dates.map { date ->
            val apps = appsByDate[date].orEmpty()
            val top = apps.maxByOrNull { it.usageMinutes }
            val total = apps.sumOf { it.usageMinutes }
            val sessionCount = apps.sumOf { it.sessionCount }
            UsageDailyEntity(
                date = date.toString(),
                totalScreenTimeMinutes = total,
                nightMinutes = apps.sumOf { it.nightMinutes },
                unlockCountEstimate = apps.sumOf { it.openCount },
                topAppPackage = top?.packageName.orEmpty(),
                topAppName = top?.appName.orEmpty(),
                topAppMinutes = top?.usageMinutes ?: 0,
                videoMinutes = apps.filter { it.category == UsageCategoryLabels.VIDEO }.sumOf { it.usageMinutes },
                snsMinutes = apps.filter { it.category == UsageCategoryLabels.SNS }.sumOf { it.usageMinutes },
                gameMinutes = apps.filter { it.category == UsageCategoryLabels.GAME }.sumOf { it.usageMinutes },
                productivityMinutes = apps.filter { it.category == UsageCategoryLabels.PRODUCTIVITY }.sumOf { it.usageMinutes },
                otherMinutes = apps.filterNot {
                    it.category in setOf(
                        UsageCategoryLabels.VIDEO,
                        UsageCategoryLabels.SNS,
                        UsageCategoryLabels.GAME,
                        UsageCategoryLabels.PRODUCTIVITY,
                    )
                }.sumOf { it.usageMinutes },
                sessionCount = sessionCount,
                averageSessionMinutes = if (sessionCount == 0) 0 else total / sessionCount,
                maxSessionMinutes = apps.maxOfOrNull { it.maxSessionMinutes } ?: 0,
                dataQuality = qualityByDate[date]?.name ?: UsageDataQuality.UNKNOWN_ERROR.name,
                collectionNote = notesByDate[date].orEmpty().joinToString(";"),
                capturedAt = capturedAt,
            )
        }

        return UsageAggregationResult(daily = daily, appDaily = appDaily, sessions = sessions)
    }

    private fun restoreSessions(
        events: List<UsageEventRecord>,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        zone: ZoneId,
        qualityByDate: MutableMap<LocalDate, UsageDataQuality>,
        notesByDate: MutableMap<LocalDate, MutableSet<String>>,
    ): List<RestoredUsageSession> {
        val open = mutableMapOf<String, Long>()
        val sessions = mutableListOf<RestoredUsageSession>()
        events
            .asSequence()
            .filter { it.timestampMillis >= rangeStartMillis && it.timestampMillis < rangeEndMillis }
            .distinctBy { Triple(it.packageName, it.timestampMillis, it.kind) }
            .sortedBy { it.timestampMillis }
            .forEach { event ->
                val date = Instant.ofEpochMilli(event.timestampMillis)
                    .atZone(zone)
                    .toLocalDate()
                when (event.kind) {
                    UsageEventKind.Foreground -> open.putIfAbsent(event.packageName, event.timestampMillis)
                    UsageEventKind.Background -> {
                        val startedAt = open.remove(event.packageName)
                        if (startedAt != null && event.timestampMillis > startedAt) {
                            sessions += RestoredUsageSession(event.packageName, startedAt, event.timestampMillis, inferredEnd = false)
                        } else {
                            qualityByDate[date] = worseQuality(qualityByDate[date], UsageDataQuality.PARTIAL_PERMISSION)
                            notesByDate[date]?.add("background_without_foreground")
                        }
                    }
                    UsageEventKind.DeviceShutdown,
                    UsageEventKind.DeviceStartup -> {
                        qualityByDate[date] = worseQuality(qualityByDate[date], UsageDataQuality.DEVICE_REBOOTED)
                        notesByDate[date]?.add("device_reboot")
                    }
                    UsageEventKind.TimezoneChanged -> {
                        qualityByDate[date] = worseQuality(qualityByDate[date], UsageDataQuality.PARTIAL_PERMISSION)
                        notesByDate[date]?.add("timezone_changed")
                    }
                }
            }

        open.forEach { (packageName, startedAt) ->
            if (rangeEndMillis > startedAt) {
                sessions += RestoredUsageSession(packageName, startedAt, rangeEndMillis, inferredEnd = true)
            }
        }
        return sessions
    }

    private fun splitSessionByDay(session: RestoredUsageSession, zone: ZoneId): List<SessionPortion> {
        val portions = mutableListOf<SessionPortion>()
        var cursor = session.startMillis
        while (cursor < session.endMillis) {
            val cursorDateTime = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextMidnight = cursorDateTime.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(session.endMillis, nextMidnight)
            portions += SessionPortion(cursorDateTime.toLocalDate(), cursor, end)
            cursor = end
        }
        return portions
    }

    private fun datesBetween(startMillis: Long, endMillis: Long, zone: ZoneId): List<LocalDate> {
        val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val endAnchor = (endMillis - 1).coerceAtLeast(startMillis)
        val end = Instant.ofEpochMilli(endAnchor).atZone(zone).toLocalDate()
        val dates = mutableListOf<LocalDate>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            dates += cursor
            cursor = cursor.plusDays(1)
        }
        return dates
    }

    private fun emptyDaily(
        date: LocalDate,
        quality: UsageDataQuality,
        note: String,
        capturedAt: Long,
    ): UsageDailyEntity =
        UsageDailyEntity(
            date = date.toString(),
            totalScreenTimeMinutes = 0,
            nightMinutes = 0,
            unlockCountEstimate = 0,
            topAppPackage = "",
            topAppName = "",
            topAppMinutes = 0,
            videoMinutes = 0,
            snsMinutes = 0,
            gameMinutes = 0,
            productivityMinutes = 0,
            otherMinutes = 0,
            sessionCount = 0,
            averageSessionMinutes = 0,
            maxSessionMinutes = 0,
            dataQuality = quality.name,
            collectionNote = note,
            capturedAt = capturedAt,
        )

    private fun nightOverlapMillis(startMillis: Long, endMillis: Long, zone: ZoneId): Long {
        if (endMillis <= startMillis) return 0
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        val end = Instant.ofEpochMilli(endMillis).atZone(zone)
        var cursor = start.toLocalDate().minusDays(1)
        val lastDate = end.toLocalDate()
        var total = 0L
        while (!cursor.isAfter(lastDate)) {
            total += overlapMillis(
                start = start,
                end = end,
                windowStart = cursor.atTime(23, 0).atZone(zone),
                windowEnd = cursor.plusDays(1).atTime(6, 0).atZone(zone),
            )
            cursor = cursor.plusDays(1)
        }
        return total
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

    private fun millisToMinutes(millis: Long): Long = millis / 60_000

    private fun minNonZero(current: Long, candidate: Long): Long =
        if (current == 0L) candidate else minOf(current, candidate)

    private fun worseQuality(current: UsageDataQuality?, candidate: UsageDataQuality): UsageDataQuality {
        val currentRank = current?.rank ?: UsageDataQuality.COMPLETE.rank
        return if (candidate.rank > currentRank) candidate else current ?: UsageDataQuality.COMPLETE
    }

    private val UsageDataQuality.rank: Int
        get() = when (this) {
            UsageDataQuality.COMPLETE -> 0
            UsageDataQuality.PARTIAL_PERMISSION -> 1
            UsageDataQuality.APP_TERMINATED -> 2
            UsageDataQuality.DEVICE_REBOOTED -> 3
            UsageDataQuality.PLATFORM_UNAVAILABLE -> 4
            UsageDataQuality.UNKNOWN_ERROR -> 5
        }

    private data class SessionPortion(
        val date: LocalDate,
        val startMillis: Long,
        val endMillis: Long,
    ) {
        val durationMillis: Long get() = endMillis - startMillis
    }

    private data class AppDayAccumulator(
        val date: LocalDate,
        val packageName: String,
        val appName: String,
        val category: String,
        var usageMinutes: Long = 0,
        var nightMinutes: Long = 0,
        var firstOpenTime: Long = 0,
        var lastTimeUsed: Long = 0,
        var sessionCount: Int = 0,
        var totalSessionMinutes: Long = 0,
        var maxSessionMinutes: Long = 0,
    ) {
        fun toEntity(capturedAt: Long): AppUsageDailyEntity =
            AppUsageDailyEntity(
                date = date.toString(),
                packageName = packageName,
                appName = appName,
                category = category,
                usageMinutes = usageMinutes,
                nightMinutes = nightMinutes,
                openCount = sessionCount,
                sessionCount = sessionCount,
                averageSessionMinutes = if (sessionCount == 0) 0 else totalSessionMinutes / sessionCount,
                maxSessionMinutes = maxSessionMinutes,
                firstOpenTime = firstOpenTime,
                lastTimeUsed = lastTimeUsed,
                capturedAt = capturedAt,
            )
    }
}
