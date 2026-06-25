package com.habitguard.app.ai

import com.habitguard.app.data.AppUsageDailyEntity
import com.habitguard.app.data.PredictionResultEntity
import com.habitguard.app.data.UsageDailyEntity
import com.habitguard.app.data.UserGoalEntity
import com.habitguard.app.model.AppUsageItem
import com.habitguard.app.model.DailyUsageSummary

data class LocalFirstPredictionSnapshot(
    val targetDate: String,
    val dailyUsage: List<UsageDailyEntity>,
    val appUsage: List<AppUsageDailyEntity>,
    val activeGoal: UserGoalEntity?,
    val notificationCount: Int,
    val cachedPrediction: PredictionOutcome?,
    val habitType: String,
    val calculatedAtMillis: Long,
)

class LocalFirstPredictionRefreshEngine(
    private val predictionRepository: PredictionRepository,
) {
    suspend fun refresh(
        snapshot: LocalFirstPredictionSnapshot,
        savePrediction: suspend (PredictionResultEntity) -> Unit,
    ): PredictionOutcome {
        val outcome = predictionRepository.predict(
            PredictionInput(
                dailySummaries = snapshot.dailyUsage
                    .sortedBy { it.date }
                    .map { it.toDailyUsageSummary() },
                todayUsage = snapshot.appUsage
                    .filter { it.date == snapshot.targetDate }
                    .map { it.toAppUsageItem() },
                hourlyUsage = emptyList(),
                activeGoalLimitMinutes = snapshot.activeGoal?.limitMinutes,
                notificationCount = snapshot.notificationCount,
                cachedResult = snapshot.cachedPrediction,
                calculatedAtMillis = snapshot.calculatedAtMillis,
            )
        )
        val predictedMinutes = outcome.predictedNextDayMinutes
        if (outcome.source != PredictionSource.COLLECTING_DATA && predictedMinutes != null) {
            savePrediction(
                PredictionResultEntity(
                    date = snapshot.targetDate,
                    predictedNextDayMinutes = predictedMinutes,
                    riskLevel = outcome.goalRiskLabel,
                    habitType = snapshot.habitType,
                    mainReason = outcome.primaryFactors.firstOrNull().orEmpty(),
                    createdAt = outcome.calculatedAtMillis,
                )
            )
        }
        return outcome
    }
}

private fun UsageDailyEntity.toDailyUsageSummary(): DailyUsageSummary =
    DailyUsageSummary(
        date = date,
        totalMinutes = totalScreenTimeMinutes,
        topAppName = topAppName,
        topAppMinutes = topAppMinutes,
        nightMinutes = nightMinutes,
        sessionCount = sessionCount,
        averageSessionMinutes = averageSessionMinutes,
        maxSessionMinutes = maxSessionMinutes,
        dataQuality = dataQuality,
    )

private fun AppUsageDailyEntity.toAppUsageItem(): AppUsageItem =
    AppUsageItem(
        packageName = packageName,
        appName = appName,
        minutes = usageMinutes,
        nightMinutes = nightMinutes,
        openCount = openCount,
        lastTimeUsed = lastTimeUsed,
        category = category,
    )
