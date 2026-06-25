package com.habitguard.app.guard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object GuardNotifier {
    private const val CHANNEL_ID = "habitguard_restriction_nudges"
    private const val CHANNEL_NAME = "HabitGuard 제한 알림"
    private const val MIN_INTERVAL_MS = 90_000L
    private val lastShownAt = mutableMapOf<String, Long>()

    fun showLightNudge(
        context: Context,
        packageName: String,
        appName: String,
        usedMinutes: Long,
        limitMinutes: Int,
    ) {
        val now = System.currentTimeMillis()
        val last = lastShownAt[packageName] ?: 0L
        if (now - last < MIN_INTERVAL_MS) return
        lastShownAt[packageName] = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)
        val body = if (limitMinutes > 0) {
            "오늘 ${usedMinutes}분 사용했습니다. 목표 ${limitMinutes}분을 넘기지 않게 확인하세요."
        } else {
            "지금 이 앱을 여는 이유를 한 번 확인하세요."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$appName 사용 확인")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(packageName.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "가벼운 알림형 제한 규칙이 앱 사용을 확인할 때 표시됩니다."
            }
        )
    }
}
