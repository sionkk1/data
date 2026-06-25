package com.habitguard.app.guard

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.habitguard.app.data.IgnoredPackages
import com.habitguard.app.data.NotificationDailyEntity
import com.habitguard.app.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class HabitGuardNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        if (IgnoredPackages.isUsageNoise(packageName, this.packageName)) return

        scope.launch {
            val dao = ServiceLocator.database(this@HabitGuardNotificationListenerService)
                .notificationDailyDao()
            val date = LocalDate.now().toString()
            val existing = dao.find(date, packageName)
            dao.upsert(
                NotificationDailyEntity(
                    date = date,
                    packageName = packageName,
                    appName = appLabel(packageName),
                    notificationCount = (existing?.notificationCount ?: 0) + 1,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun appLabel(packageName: String): String =
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
}
