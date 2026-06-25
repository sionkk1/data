package com.habitguard.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UsageEventAggregatorTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun splitsSessionsAcrossMidnightAndCountsNightMinutes() {
        val result = aggregate(
            events = listOf(
                event("com.video", "2026-06-01T23:50:00", UsageEventKind.Foreground),
                event("com.video", "2026-06-02T00:20:00", UsageEventKind.Background),
            ),
            rangeStart = "2026-06-01T00:00:00",
            rangeEnd = "2026-06-03T00:00:00",
            category = UsageCategoryLabels.VIDEO,
        )

        assertEquals(10, result.daily.single { it.date == "2026-06-01" }.totalScreenTimeMinutes)
        assertEquals(20, result.daily.single { it.date == "2026-06-02" }.totalScreenTimeMinutes)
        assertEquals(10, result.daily.single { it.date == "2026-06-01" }.nightMinutes)
        assertEquals(20, result.daily.single { it.date == "2026-06-02" }.nightMinutes)
        assertEquals(UsageDataQuality.COMPLETE.name, result.daily.single { it.date == "2026-06-01" }.dataQuality)
    }

    @Test
    fun usesNightWindowFromElevenPmToSixAm() {
        val result = aggregate(
            events = listOf(
                event("com.sns", "2026-06-01T22:30:00", UsageEventKind.Foreground),
                event("com.sns", "2026-06-01T23:30:00", UsageEventKind.Background),
            ),
            rangeStart = "2026-06-01T00:00:00",
            rangeEnd = "2026-06-02T00:00:00",
            category = UsageCategoryLabels.SNS,
        )

        val day = result.daily.single()
        assertEquals(60, day.totalScreenTimeMinutes)
        assertEquals(30, day.nightMinutes)
        assertEquals(60, day.snsMinutes)
    }

    @Test
    fun ignoresDuplicateForegroundEventsAndRestoresSessionMetrics() {
        val result = aggregate(
            events = listOf(
                event("com.game", "2026-06-01T10:00:00", UsageEventKind.Foreground),
                event("com.game", "2026-06-01T10:00:00", UsageEventKind.Foreground),
                event("com.game", "2026-06-01T10:10:00", UsageEventKind.Background),
                event("com.game", "2026-06-01T10:20:00", UsageEventKind.Foreground),
                event("com.game", "2026-06-01T10:50:00", UsageEventKind.Background),
            ),
            rangeStart = "2026-06-01T00:00:00",
            rangeEnd = "2026-06-02T00:00:00",
            category = UsageCategoryLabels.GAME,
        )

        val day = result.daily.single()
        assertEquals(40, day.totalScreenTimeMinutes)
        assertEquals(2, day.unlockCountEstimate)
        assertEquals(2, day.sessionCount)
        assertEquals(20, day.averageSessionMinutes)
        assertEquals(30, day.maxSessionMinutes)
    }

    @Test
    fun marksMissingBackgroundAsAppTerminatedAndClosesAtRangeEnd() {
        val result = aggregate(
            events = listOf(event("com.video", "2026-06-01T23:40:00", UsageEventKind.Foreground)),
            rangeStart = "2026-06-01T00:00:00",
            rangeEnd = "2026-06-02T00:00:00",
            category = UsageCategoryLabels.VIDEO,
        )

        val day = result.daily.single()
        assertEquals(20, day.totalScreenTimeMinutes)
        assertEquals(UsageDataQuality.APP_TERMINATED.name, day.dataQuality)
        assertTrue(day.collectionNote.contains("missing_background"))
    }

    @Test
    fun recordsPermissionRevokedDaysSeparatelyFromZeroUsageDays() {
        val result = UsageEventAggregator.aggregate(
            events = emptyList(),
            rangeStartMillis = millis("2026-06-01T00:00:00"),
            rangeEndMillis = millis("2026-06-03T00:00:00"),
            zone = zone,
            categoryForPackage = { UsageCategoryLabels.OTHER },
            appNameForPackage = { it },
            hasUsagePermission = false,
        )

        assertEquals(2, result.daily.size)
        assertEquals(0, result.daily.sumOf { it.totalScreenTimeMinutes })
        assertTrue(result.daily.all { it.dataQuality == UsageDataQuality.PARTIAL_PERMISSION.name })
    }

    @Test
    fun marksBackgroundWithoutForegroundAsPartialPermission() {
        val result = aggregate(
            events = listOf(event("com.video", "2026-06-01T12:00:00", UsageEventKind.Background)),
            rangeStart = "2026-06-01T00:00:00",
            rangeEnd = "2026-06-02T00:00:00",
            category = UsageCategoryLabels.VIDEO,
        )

        val day = result.daily.single()
        assertEquals(0, day.totalScreenTimeMinutes)
        assertEquals(UsageDataQuality.PARTIAL_PERMISSION.name, day.dataQuality)
        assertTrue(day.collectionNote.contains("background_without_foreground"))
    }

    @Test
    fun recordsPlatformUnavailableAndUnknownErrorQualityStates() {
        val unavailable = UsageEventAggregator.aggregate(
            events = emptyList(),
            rangeStartMillis = millis("2026-06-01T00:00:00"),
            rangeEndMillis = millis("2026-06-02T00:00:00"),
            zone = zone,
            categoryForPackage = { UsageCategoryLabels.OTHER },
            appNameForPackage = { it },
            hasUsagePermission = true,
            sourceUnavailable = true,
        )
        val unknown = UsageEventAggregator.aggregate(
            events = emptyList(),
            rangeStartMillis = millis("2026-06-01T00:00:00"),
            rangeEndMillis = millis("2026-06-02T00:00:00"),
            zone = zone,
            categoryForPackage = { UsageCategoryLabels.OTHER },
            appNameForPackage = { it },
            hasUsagePermission = true,
            unknownError = true,
        )

        assertEquals(UsageDataQuality.PLATFORM_UNAVAILABLE.name, unavailable.daily.single().dataQuality)
        assertEquals(UsageDataQuality.UNKNOWN_ERROR.name, unknown.daily.single().dataQuality)
    }

    @Test
    fun keepsPermissionGrantedEmptyDaysAsCompleteZeroUsage() {
        val result = aggregate(
            events = emptyList(),
            rangeStart = "2026-06-01T00:00:00",
            rangeEnd = "2026-06-02T00:00:00",
            category = UsageCategoryLabels.OTHER,
        )

        assertEquals(0, result.daily.single().totalScreenTimeMinutes)
        assertEquals(UsageDataQuality.COMPLETE.name, result.daily.single().dataQuality)
    }

    @Test
    fun marksDeviceRebootWithoutDuplicatingUsage() {
        val result = aggregate(
            events = listOf(
                event("android", "2026-06-01T08:00:00", UsageEventKind.DeviceShutdown),
                event("android", "2026-06-01T08:02:00", UsageEventKind.DeviceStartup),
                event("com.productivity", "2026-06-01T09:00:00", UsageEventKind.Foreground),
                event("com.productivity", "2026-06-01T09:30:00", UsageEventKind.Background),
            ),
            rangeStart = "2026-06-01T00:00:00",
            rangeEnd = "2026-06-02T00:00:00",
            category = UsageCategoryLabels.PRODUCTIVITY,
        )

        val day = result.daily.single()
        assertEquals(30, day.totalScreenTimeMinutes)
        assertEquals(UsageDataQuality.DEVICE_REBOOTED.name, day.dataQuality)
    }

    @Test
    fun timezoneChangesDoNotChangeDatesWhenZoneIsExplicit() {
        val result = aggregate(
            events = listOf(
                event("system", "2026-06-01T12:00:00", UsageEventKind.TimezoneChanged),
                event("com.video", "2026-06-01T12:10:00", UsageEventKind.Foreground),
                event("com.video", "2026-06-01T12:40:00", UsageEventKind.Background),
            ),
            rangeStart = "2026-06-01T00:00:00",
            rangeEnd = "2026-06-02T00:00:00",
            category = UsageCategoryLabels.VIDEO,
        )

        assertEquals(LocalDate.parse("2026-06-01").toString(), result.daily.single().date)
        assertEquals(30, result.daily.single().totalScreenTimeMinutes)
        assertEquals(UsageDataQuality.PARTIAL_PERMISSION.name, result.daily.single().dataQuality)
    }

    private fun aggregate(
        events: List<UsageEventRecord>,
        rangeStart: String,
        rangeEnd: String,
        category: String,
    ): UsageAggregationResult =
        UsageEventAggregator.aggregate(
            events = events,
            rangeStartMillis = millis(rangeStart),
            rangeEndMillis = millis(rangeEnd),
            zone = zone,
            categoryForPackage = { category },
            appNameForPackage = { it },
            hasUsagePermission = true,
        )

    private fun event(packageName: String, localDateTime: String, kind: UsageEventKind): UsageEventRecord =
        UsageEventRecord(packageName, millis(localDateTime), kind)

    private fun millis(localDateTime: String): Long =
        LocalDateTime.parse(localDateTime).atZone(zone).toInstant().toEpochMilli()
}
