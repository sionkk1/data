package com.habitguard.app.guard

import android.content.Context
import android.os.SystemClock

object GuardPreferences {
    private const val PREFS = "habitguard_guard"
    private const val PREFIX_ISSUED_ELAPSED = "unlock_issued_elapsed_"
    private const val PREFIX_EXPIRES_ELAPSED = "unlock_expires_elapsed_"
    private const val PREFIX_REASON = "unlock_reason_"
    private const val KEY_GUARD_ENABLED = "guard_enabled"

    fun isGuardEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GUARD_ENABLED, true)

    fun setGuardEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GUARD_ENABLED, enabled)
            .apply()
    }

    fun temporaryUnlockUntil(context: Context, packageName: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(PREFIX_EXPIRES_ELAPSED + packageName, 0L)

    fun currentUnlockSession(context: Context, packageName: String): UnlockSessionSpec? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val issuedAt = prefs.getLong(PREFIX_ISSUED_ELAPSED + packageName, 0L)
        val expiresAt = prefs.getLong(PREFIX_EXPIRES_ELAPSED + packageName, 0L)
        if (issuedAt <= 0L || expiresAt <= issuedAt) return null
        val reason = runCatching {
            UnlockSessionReason.valueOf(
                prefs.getString(PREFIX_REASON + packageName, UnlockSessionReason.MissionSuccess.name)
                    ?: UnlockSessionReason.MissionSuccess.name,
            )
        }.getOrDefault(UnlockSessionReason.MissionSuccess)
        return UnlockSessionSpec(
            packageName = packageName,
            issuedAtElapsedRealtime = issuedAt,
            expiresAtElapsedRealtime = expiresAt,
            reason = reason,
        )
    }

    fun grantTemporaryUnlock(
        context: Context,
        packageName: String,
        minutes: Int,
        reason: UnlockSessionReason = UnlockSessionReason.MissionSuccess,
    ): UnlockSessionSpec {
        val safeMinutes = RestrictionRuleSpec.allowedUnlockMinutes(minutes)
        val issuedAt = SystemClock.elapsedRealtime()
        val expiresAt = issuedAt + safeMinutes * 60_000L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREFIX_ISSUED_ELAPSED + packageName, issuedAt)
            .putLong(PREFIX_EXPIRES_ELAPSED + packageName, expiresAt)
            .putString(PREFIX_REASON + packageName, reason.name)
            .apply()
        return UnlockSessionSpec(
            packageName = packageName,
            issuedAtElapsedRealtime = issuedAt,
            expiresAtElapsedRealtime = expiresAt,
            reason = reason,
        )
    }

    fun isTemporarilyUnlocked(context: Context, packageName: String): Boolean {
        val session = currentUnlockSession(context, packageName) ?: return false
        val isValid = session.isValidFor(packageName, SystemClock.elapsedRealtime())
        if (!isValid) clearTemporaryUnlock(context, packageName)
        return isValid
    }

    fun clearTemporaryUnlock(context: Context, packageName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(PREFIX_ISSUED_ELAPSED + packageName)
            .remove(PREFIX_EXPIRES_ELAPSED + packageName)
            .remove(PREFIX_REASON + packageName)
            .apply()
    }
}
