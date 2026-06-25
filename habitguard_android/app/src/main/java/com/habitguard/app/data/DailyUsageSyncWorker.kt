package com.habitguard.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.habitguard.app.ai.DefaultPredictionRepository
import com.habitguard.app.ai.LocalFirstPredictionRefreshEngine
import com.habitguard.app.ai.LocalFirstPredictionSnapshot
import com.habitguard.app.ai.ModelBundleLoader
import com.habitguard.app.ai.PredictionOutcome
import com.habitguard.app.ai.PredictionSource
import com.habitguard.app.ai.TrainedLocalPredictionModel
import com.habitguard.app.ai.HabitAnalyzer
import com.habitguard.app.model.AppUsageItem
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DailyUsageSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = UsageStatsRepository(applicationContext)
        val database = ServiceLocator.database(applicationContext)
        val aggregate = repository.aggregateDailyUsage(days = 30)
        aggregate.daily.forEach { database.usageDailyDao().upsert(it) }
        val dateRange = aggregate.daily.map { it.date }
        if (dateRange.isNotEmpty()) {
            database.appUsageDailyDao().deleteBetween(dateRange.first(), dateRange.last())
            database.appUsageDailyDao().upsertAll(aggregate.appDaily)
        }
        refreshOfflinePrediction(database)
        return Result.success()
    }

    private suspend fun refreshOfflinePrediction(database: HabitGuardDatabase) {
        val targetDate = LocalDate.now().toString()
        val daily = database.usageDailyDao().recent(30)
        val appDaily = database.appUsageDailyDao().recent(1000)
        val todayAppUsage = appDaily
            .filter { it.date == targetDate }
            .map {
                AppUsageItem(
                    packageName = it.packageName,
                    appName = it.appName,
                    minutes = it.usageMinutes,
                    nightMinutes = it.nightMinutes,
                    openCount = it.openCount,
                    lastTimeUsed = it.lastTimeUsed,
                    category = it.category,
                )
            }
        val total = todayAppUsage.sumOf { it.minutes }
        val night = todayAppUsage.sumOf { it.nightMinutes }
        val notificationCount = database.notificationDailyDao().totalForDate(targetDate)
        val predictionRepository = DefaultPredictionRepository(
            localModel = ModelBundleLoader.load(applicationContext)?.let { TrainedLocalPredictionModel(it) },
        )
        LocalFirstPredictionRefreshEngine(predictionRepository).refresh(
            snapshot = LocalFirstPredictionSnapshot(
                targetDate = targetDate,
                dailyUsage = daily,
                appUsage = appDaily,
                activeGoal = database.userGoalDao().active(),
                notificationCount = notificationCount,
                cachedPrediction = database.predictionResultDao().latest()?.toCachedPredictionOutcome(),
                habitType = HabitAnalyzer.habitType(todayAppUsage, total, night, notificationCount),
                calculatedAtMillis = System.currentTimeMillis(),
            ),
            savePrediction = { database.predictionResultDao().upsert(it) },
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "daily_usage_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyUsageSyncWorker>(6, TimeUnit.HOURS)
                .addTag(UNIQUE_WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

private fun PredictionResultEntity.toCachedPredictionOutcome(): PredictionOutcome =
    PredictionOutcome(
        source = PredictionSource.CACHE,
        predictedNextDayMinutes = predictedNextDayMinutes,
        goalExceedanceRiskPercent = null,
        goalRiskLabel = riskLevel,
        primaryFactors = listOf(mainReason).filter { it.isNotBlank() },
        modelVersion = "cached-result",
        calculatedAtMillis = createdAt,
        dataQualityStatus = "UNKNOWN_ERROR",
        userMessage = "Showing the most recent stored prediction because a fresh model result is not available.",
        recommendationText = "Review the cached result before approving any rule.",
    )
