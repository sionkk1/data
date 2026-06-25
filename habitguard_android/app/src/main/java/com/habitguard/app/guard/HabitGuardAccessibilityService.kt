package com.habitguard.app.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.habitguard.app.LockActivity
import com.habitguard.app.data.GuardEventEntity
import com.habitguard.app.data.IgnoredPackages
import com.habitguard.app.data.RestrictionRuleEntity
import com.habitguard.app.data.ServiceLocator
import com.habitguard.app.data.UsageStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HabitGuardAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionStartedElapsedByPackage = mutableMapOf<String, Long>()
    private var lastForegroundPackage: String? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockAtElapsed: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (IgnoredPackages.isUsageNoise(packageName, this.packageName)) return
        val nowElapsed = SystemClock.elapsedRealtime()
        updateForegroundSession(packageName, nowElapsed)

        scope.launch {
            val database = ServiceLocator.database(this@HabitGuardAccessibilityService)
            val rule = database.ruleDao().enabledRuleFor(packageName) ?: return@launch
            val zone = ZoneId.systemDefault()
            val now = Instant.now().atZone(zone)
            val dayStartMillis = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val usedMinutes = UsageStatsRepository(this@HabitGuardAccessibilityService)
                .minutesForPackageToday(packageName)
            val unlocksToday = database.missionLogDao()
                .successCountForPackageSince(packageName, dayStartMillis)
            val decision = RestrictionEvaluator.evaluate(
                rule = rule.toSpec(),
                context = GuardCheckContext(
                    packageName = packageName,
                    nowDay = now.dayOfWeek,
                    nowTime = now.toLocalTime(),
                    nowElapsedRealtime = nowElapsed,
                    dailyUsageMinutes = usedMinutes,
                    currentSessionMinutes = currentSessionMinutes(packageName, nowElapsed),
                    unlockSession = GuardPreferences.currentUnlockSession(this@HabitGuardAccessibilityService, packageName),
                    unlocksToday = unlocksToday,
                    guardEnabled = GuardPreferences.isGuardEnabled(this@HabitGuardAccessibilityService),
                    accessibilityEnabled = true,
                ),
            )

            when (decision.kind) {
                GuardDecisionKind.Allow -> {
                    logGuardEvent(
                        packageName = packageName,
                        eventType = "foreground_allowed_${decision.reason.name}",
                        blocked = false,
                        unlockTokenValid = decision.reason == GuardDecisionReason.TemporaryUnlockValid,
                    )
                }
                GuardDecisionKind.Guidance -> {
                    logGuardEvent(
                        packageName = packageName,
                        eventType = "guard_guidance_${decision.reason.name}",
                        blocked = false,
                        unlockTokenValid = false,
                    )
                }
                GuardDecisionKind.Lock -> {
                    maybeStartLock(packageName, rule, decision, nowElapsed)
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun updateForegroundSession(packageName: String, nowElapsed: Long) {
        if (lastForegroundPackage != packageName) {
            lastForegroundPackage = packageName
            sessionStartedElapsedByPackage[packageName] = nowElapsed
        } else {
            sessionStartedElapsedByPackage.putIfAbsent(packageName, nowElapsed)
        }
    }

    private fun currentSessionMinutes(packageName: String, nowElapsed: Long): Long {
        val startedAt = sessionStartedElapsedByPackage[packageName] ?: nowElapsed
        return ((nowElapsed - startedAt).coerceAtLeast(0L)) / 60_000L
    }

    private suspend fun maybeStartLock(
        packageName: String,
        rule: RestrictionRuleEntity,
        decision: GuardDecision,
        nowElapsed: Long,
    ) {
        if (lastBlockedPackage == packageName && nowElapsed - lastBlockAtElapsed < 2_000L) {
            logGuardEvent(
                packageName = packageName,
                eventType = MissionExitReason.RestrictedAppReentered.eventType,
                blocked = true,
                unlockTokenValid = false,
            )
            return
        }
        lastBlockedPackage = packageName
        lastBlockAtElapsed = nowElapsed
        logGuardEvent(
            packageName = packageName,
            eventType = "lock_started_${decision.reason.name}",
            blocked = true,
            unlockTokenValid = false,
        )

        withContext(Dispatchers.Main) {
            val intent = Intent(this@HabitGuardAccessibilityService, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(LockActivity.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(LockActivity.EXTRA_APP_NAME, rule.targetName)
                .putExtra(LockActivity.EXTRA_MISSION_TYPE, rule.missionType)
                .putExtra(LockActivity.EXTRA_UNLOCK_MINUTES, decision.unlockMinutes)
                .putExtra(LockActivity.EXTRA_ALLOW_MISSION_UNLOCK, decision.normalUnlockAllowed)
                .putExtra(LockActivity.EXTRA_EMERGENCY_UNLOCK_MINUTES, rule.emergencyUnlockMinutes.coerceIn(0, 5))
            startActivity(intent)
        }
    }

    private suspend fun logGuardEvent(
        packageName: String,
        eventType: String,
        blocked: Boolean,
        unlockTokenValid: Boolean,
        missionResult: String = "not_started",
    ) {
        ServiceLocator.database(this@HabitGuardAccessibilityService).guardEventDao().insert(
            GuardEventEntity(
                packageName = packageName,
                eventType = eventType,
                blocked = blocked,
                unlockTokenValid = unlockTokenValid,
                missionResult = missionResult,
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}

private fun RestrictionRuleEntity.toSpec(): RestrictionRuleSpec =
    RestrictionRuleSpec(
        targetPackage = targetPackage,
        approved = approved,
        enabled = enabled,
        activeDays = activeDaysMask.toDays(),
        startHour = startHour,
        endHour = endHour,
        dailyLimitMinutes = limitMinutes,
        sessionLimitMinutes = sessionLimitMinutes,
        unlockMinutes = unlockMinutes,
        maxUnlocksPerDay = maxUnlocksPerDay,
    )

private fun Int.toDays(): Set<DayOfWeek> =
    DayOfWeek.values()
        .filter { day -> this and (1 shl (day.value - 1)) != 0 }
        .toSet()
        .ifEmpty { DayOfWeek.values().toSet() }
