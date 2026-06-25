package com.habitguard.app.guard

import java.time.DayOfWeek
import java.time.LocalTime

data class RestrictionRuleSpec(
    val targetPackage: String,
    val approved: Boolean,
    val enabled: Boolean,
    val activeDays: Set<DayOfWeek>,
    val startHour: Int,
    val endHour: Int,
    val dailyLimitMinutes: Int,
    val sessionLimitMinutes: Int,
    val unlockMinutes: Int,
    val maxUnlocksPerDay: Int,
) {
    fun isActiveAt(day: DayOfWeek, time: LocalTime): Boolean {
        if (day !in activeDays) return false
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(0, 23)
        val hour = time.hour
        return if (start <= end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }

    val safeUnlockMinutes: Int
        get() = allowedUnlockMinutes(unlockMinutes)

    companion object {
        fun allowedUnlockMinutes(minutes: Int): Int =
            listOf(5, 10, 30).lastOrNull { minutes >= it } ?: 5
    }
}

data class GuardCheckContext(
    val packageName: String,
    val nowDay: DayOfWeek,
    val nowTime: LocalTime,
    val nowElapsedRealtime: Long,
    val dailyUsageMinutes: Long,
    val currentSessionMinutes: Long,
    val unlockSession: UnlockSessionSpec?,
    val unlocksToday: Int,
    val guardEnabled: Boolean,
    val accessibilityEnabled: Boolean,
)

data class GuardDecision(
    val kind: GuardDecisionKind,
    val reason: GuardDecisionReason,
    val unlockMinutes: Int = 0,
    val normalUnlockAllowed: Boolean = true,
) {
    companion object {
        fun allow(reason: GuardDecisionReason): GuardDecision =
            GuardDecision(kind = GuardDecisionKind.Allow, reason = reason)

        fun lock(
            reason: GuardDecisionReason,
            unlockMinutes: Int,
            normalUnlockAllowed: Boolean = true,
        ): GuardDecision =
            GuardDecision(
                kind = GuardDecisionKind.Lock,
                reason = reason,
                unlockMinutes = unlockMinutes,
                normalUnlockAllowed = normalUnlockAllowed,
            )
    }
}

enum class GuardDecisionKind {
    Allow,
    Lock,
    Guidance,
}

enum class GuardDecisionReason {
    RuleNotApproved,
    RuleDisabled,
    PackageMismatch,
    OutsideSchedule,
    UnderLimit,
    TemporaryUnlockValid,
    DailyLimitExceeded,
    SessionLimitExceeded,
    DailyUnlockLimitReached,
    GuardDisabled,
    AccessibilityPermissionMissing,
}

data class UnlockSessionSpec(
    val packageName: String,
    val issuedAtElapsedRealtime: Long,
    val expiresAtElapsedRealtime: Long,
    val reason: UnlockSessionReason,
) {
    fun isValidFor(packageName: String, nowElapsedRealtime: Long): Boolean =
        this.packageName == packageName &&
            nowElapsedRealtime >= issuedAtElapsedRealtime &&
            nowElapsedRealtime < expiresAtElapsedRealtime
}

enum class UnlockSessionReason {
    MissionSuccess,
    Emergency,
}

enum class MissionExitReason(val code: String, val eventType: String) {
    BackPressed("back", "mission_abandoned_back"),
    HomeOrRecentApps("stop", "mission_abandoned_home_or_recent"),
    RestrictedAppReentered("reentry", "restricted_app_reentered"),
    Unknown("unknown", "mission_abandoned");

    companion object {
        fun from(code: String): MissionExitReason =
            values().firstOrNull { it.code == code } ?: Unknown
    }
}

object RestrictionEvaluator {
    fun evaluate(rule: RestrictionRuleSpec, context: GuardCheckContext): GuardDecision {
        if (!context.guardEnabled) {
            return GuardDecision(kind = GuardDecisionKind.Guidance, reason = GuardDecisionReason.GuardDisabled)
        }
        if (!context.accessibilityEnabled) {
            return GuardDecision(
                kind = GuardDecisionKind.Guidance,
                reason = GuardDecisionReason.AccessibilityPermissionMissing,
            )
        }
        if (!rule.approved) return GuardDecision.allow(GuardDecisionReason.RuleNotApproved)
        if (!rule.enabled) return GuardDecision.allow(GuardDecisionReason.RuleDisabled)
        if (rule.targetPackage != context.packageName) return GuardDecision.allow(GuardDecisionReason.PackageMismatch)

        val unlockSession = context.unlockSession
        if (unlockSession?.isValidFor(context.packageName, context.nowElapsedRealtime) == true) {
            return GuardDecision.allow(GuardDecisionReason.TemporaryUnlockValid)
        }
        if (!rule.isActiveAt(context.nowDay, context.nowTime)) {
            return GuardDecision.allow(GuardDecisionReason.OutsideSchedule)
        }

        val sessionExceeded = rule.sessionLimitMinutes > 0 &&
            context.currentSessionMinutes >= rule.sessionLimitMinutes
        val dailyExceeded = rule.dailyLimitMinutes > 0 &&
            context.dailyUsageMinutes >= rule.dailyLimitMinutes
        if (!sessionExceeded && !dailyExceeded) {
            return GuardDecision.allow(GuardDecisionReason.UnderLimit)
        }

        val normalUnlockAllowed = rule.maxUnlocksPerDay <= 0 || context.unlocksToday < rule.maxUnlocksPerDay
        val reason = when {
            !normalUnlockAllowed -> GuardDecisionReason.DailyUnlockLimitReached
            sessionExceeded -> GuardDecisionReason.SessionLimitExceeded
            else -> GuardDecisionReason.DailyLimitExceeded
        }
        return GuardDecision.lock(
            reason = reason,
            unlockMinutes = rule.safeUnlockMinutes,
            normalUnlockAllowed = normalUnlockAllowed,
        )
    }
}
