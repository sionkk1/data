package com.habitguard.app.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class RestrictionEvaluatorTest {
    private val baseRule = RestrictionRuleSpec(
        targetPackage = "com.example.video",
        approved = true,
        enabled = true,
        activeDays = DayOfWeek.values().toSet(),
        startHour = 23,
        endHour = 6,
        dailyLimitMinutes = 30,
        sessionLimitMinutes = 15,
        unlockMinutes = 10,
        maxUnlocksPerDay = 2,
    )

    @Test
    fun appliesOvernightTimeWindowFrom23To06() {
        assertTrue(baseRule.isActiveAt(DayOfWeek.MONDAY, LocalTime.of(23, 0)))
        assertTrue(baseRule.isActiveAt(DayOfWeek.TUESDAY, LocalTime.of(5, 59)))
        assertFalse(baseRule.isActiveAt(DayOfWeek.TUESDAY, LocalTime.of(6, 0)))
        assertFalse(baseRule.isActiveAt(DayOfWeek.TUESDAY, LocalTime.of(22, 59)))
    }

    @Test
    fun appliesSameDayTimeWindowWithoutCrossingMidnight() {
        val rule = baseRule.copy(startHour = 9, endHour = 18)

        assertFalse(rule.isActiveAt(DayOfWeek.MONDAY, LocalTime.of(8, 59)))
        assertTrue(rule.isActiveAt(DayOfWeek.MONDAY, LocalTime.of(9, 0)))
        assertTrue(rule.isActiveAt(DayOfWeek.MONDAY, LocalTime.of(17, 59)))
        assertFalse(rule.isActiveAt(DayOfWeek.MONDAY, LocalTime.of(18, 0)))
    }

    @Test
    fun clampsMissionUnlockMinutesToApprovedBuckets() {
        assertEquals(5, RestrictionRuleSpec.allowedUnlockMinutes(1))
        assertEquals(5, RestrictionRuleSpec.allowedUnlockMinutes(5))
        assertEquals(10, RestrictionRuleSpec.allowedUnlockMinutes(12))
        assertEquals(30, RestrictionRuleSpec.allowedUnlockMinutes(90))
    }

    @Test
    fun blocksOnlyApprovedEnabledTargetRule() {
        val context = checkContext(dailyUsageMinutes = 40)

        assertEquals(GuardDecisionKind.Lock, RestrictionEvaluator.evaluate(baseRule, context).kind)
        assertEquals(
            GuardDecisionKind.Allow,
            RestrictionEvaluator.evaluate(baseRule.copy(approved = false), context).kind,
        )
        assertEquals(
            GuardDecisionKind.Allow,
            RestrictionEvaluator.evaluate(baseRule.copy(enabled = false), context).kind,
        )
        assertEquals(
            GuardDecisionKind.Allow,
            RestrictionEvaluator.evaluate(baseRule, context.copy(packageName = "com.other")).kind,
        )
    }

    @Test
    fun blocksWhenDailyLimitOrSessionLimitIsExceededAcrossMidnightWindow() {
        val overDaily = checkContext(
            nowTime = LocalTime.of(0, 30),
            dailyUsageMinutes = 31,
            currentSessionMinutes = 3,
        )
        val overSession = checkContext(
            nowTime = LocalTime.of(0, 30),
            dailyUsageMinutes = 3,
            currentSessionMinutes = 16,
        )

        assertEquals(GuardDecisionReason.DailyLimitExceeded, RestrictionEvaluator.evaluate(baseRule, overDaily).reason)
        assertEquals(GuardDecisionReason.SessionLimitExceeded, RestrictionEvaluator.evaluate(baseRule, overSession).reason)
    }

    @Test
    fun allowsValidTemporaryUnlockUntilElapsedExpiry() {
        val session = UnlockSessionSpec(
            packageName = "com.example.video",
            issuedAtElapsedRealtime = 1_000,
            expiresAtElapsedRealtime = 601_000,
            reason = UnlockSessionReason.MissionSuccess,
        )

        assertTrue(session.isValidFor("com.example.video", nowElapsedRealtime = 600_999))
        assertFalse(session.isValidFor("com.example.video", nowElapsedRealtime = 601_000))
        assertEquals(
            GuardDecisionKind.Allow,
            RestrictionEvaluator.evaluate(baseRule, checkContext(dailyUsageMinutes = 90, unlockSession = session)).kind,
        )
    }

    @Test
    fun invalidatesTemporaryUnlockAfterDeviceReboot() {
        val sessionBeforeReboot = UnlockSessionSpec(
            packageName = "com.example.video",
            issuedAtElapsedRealtime = 500_000,
            expiresAtElapsedRealtime = 800_000,
            reason = UnlockSessionReason.MissionSuccess,
        )

        assertFalse(sessionBeforeReboot.isValidFor("com.example.video", nowElapsedRealtime = 10_000))
    }

    @Test
    fun deviceTimeChangeDoesNotExtendTemporaryUnlock() {
        val session = UnlockSessionSpec(
            packageName = "com.example.video",
            issuedAtElapsedRealtime = 10_000,
            expiresAtElapsedRealtime = 70_000,
            reason = UnlockSessionReason.MissionSuccess,
        )

        assertTrue(session.isValidFor("com.example.video", nowElapsedRealtime = 69_999))
        assertFalse(session.isValidFor("com.example.video", nowElapsedRealtime = 70_000))
    }

    @Test
    fun enforcesDailyUnlockLimitAndAllowsEmergencyUnlock() {
        val normalLimitReached = checkContext(dailyUsageMinutes = 60, unlocksToday = 2)
        val emergency = UnlockSessionSpec(
            packageName = "com.example.video",
            issuedAtElapsedRealtime = 1_000,
            expiresAtElapsedRealtime = 901_000,
            reason = UnlockSessionReason.Emergency,
        )

        assertEquals(GuardDecisionReason.DailyUnlockLimitReached, RestrictionEvaluator.evaluate(baseRule, normalLimitReached).reason)
        assertEquals(
            GuardDecisionKind.Allow,
            RestrictionEvaluator.evaluate(baseRule, normalLimitReached.copy(unlockSession = emergency)).kind,
        )
    }

    @Test
    fun returnsPermissionGuidanceWhenGuardUnavailable() {
        assertEquals(
            GuardDecisionReason.GuardDisabled,
            RestrictionEvaluator.evaluate(baseRule, checkContext(dailyUsageMinutes = 60, guardEnabled = false)).reason,
        )
        assertEquals(
            GuardDecisionReason.AccessibilityPermissionMissing,
            RestrictionEvaluator.evaluate(baseRule, checkContext(dailyUsageMinutes = 60, accessibilityEnabled = false)).reason,
        )
    }

    @Test
    fun classifiesMissionExitByBackHomeRecentOrReentry() {
        assertEquals(MissionExitReason.BackPressed, MissionExitReason.from("back"))
        assertEquals(MissionExitReason.HomeOrRecentApps, MissionExitReason.from("stop"))
        assertEquals(MissionExitReason.RestrictedAppReentered, MissionExitReason.from("reentry"))
    }

    private fun checkContext(
        nowDay: DayOfWeek = DayOfWeek.MONDAY,
        nowTime: LocalTime = LocalTime.of(23, 30),
        dailyUsageMinutes: Long = 0,
        currentSessionMinutes: Long = 0,
        unlockSession: UnlockSessionSpec? = null,
        unlocksToday: Int = 0,
        guardEnabled: Boolean = true,
        accessibilityEnabled: Boolean = true,
        packageName: String = "com.example.video",
    ): GuardCheckContext =
        GuardCheckContext(
            packageName = packageName,
            nowDay = nowDay,
            nowTime = nowTime,
            nowElapsedRealtime = 600_000,
            dailyUsageMinutes = dailyUsageMinutes,
            currentSessionMinutes = currentSessionMinutes,
            unlockSession = unlockSession,
            unlocksToday = unlocksToday,
            guardEnabled = guardEnabled,
            accessibilityEnabled = accessibilityEnabled,
        )
}
