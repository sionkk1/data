package com.habitguard.app.ai

import com.habitguard.app.model.AppUsageItem
import com.habitguard.app.model.DailyUsageSummary
import com.habitguard.app.model.HourlyUsageItem
import kotlin.math.abs

interface PredictionRepository {
    suspend fun predict(input: PredictionInput): PredictionOutcome
}

enum class PredictionSource {
    REMOTE_MODEL,
    LOCAL_MODEL,
    BASELINE,
    CACHE,
    COLLECTING_DATA,
}

data class PredictionInput(
    val dailySummaries: List<DailyUsageSummary>,
    val todayUsage: List<AppUsageItem>,
    val hourlyUsage: List<HourlyUsageItem>,
    val activeGoalLimitMinutes: Int?,
    val notificationCount: Int = 0,
    val cachedResult: PredictionOutcome?,
    val calculatedAtMillis: Long,
)

data class PredictionOutcome(
    val source: PredictionSource,
    val predictedNextDayMinutes: Long?,
    val goalExceedanceRiskPercent: Int?,
    val goalRiskLabel: String,
    val primaryFactors: List<String>,
    val modelVersion: String,
    val calculatedAtMillis: Long,
    val dataQualityStatus: String,
    val userMessage: String,
    val recommendationText: String,
    val sourceType: String = "",
    val evaluationScope: String = "",
)

data class DashboardPredictionUi(
    val personalizedPredictionReady: Boolean,
    val predictedTomorrowMinutes: Long,
    val predictionDisplayText: String,
    val sourceLabel: String,
    val modelVersion: String,
    val calculatedAtText: String,
    val dataQualityStatus: String,
    val sourceType: String,
    val evaluationScope: String,
    val goalRiskText: String,
    val recommendationText: String,
)

object DashboardPredictionPresenter {
    fun present(outcome: PredictionOutcome, calculatedAtText: String = ""): DashboardPredictionUi {
        val ready = outcome.source != PredictionSource.COLLECTING_DATA && outcome.predictedNextDayMinutes != null
        return DashboardPredictionUi(
            personalizedPredictionReady = ready,
            predictedTomorrowMinutes = outcome.predictedNextDayMinutes ?: 0,
            predictionDisplayText = outcome.predictedNextDayMinutes?.let { "약 ${it}분" } ?: outcome.userMessage,
            sourceLabel = outcome.source.displayLabel(),
            modelVersion = outcome.modelVersion,
            calculatedAtText = calculatedAtText,
            dataQualityStatus = outcome.dataQualityStatus,
            sourceType = outcome.sourceType,
            evaluationScope = outcome.evaluationScope,
            goalRiskText = outcome.goalExceedanceRiskPercent?.let { "${outcome.goalRiskLabel} (${it}%)" } ?: outcome.goalRiskLabel,
            recommendationText = outcome.recommendationText,
        )
    }
}

fun PredictionSource.displayLabel(): String =
    when (this) {
        PredictionSource.REMOTE_MODEL -> "Remote model"
        PredictionSource.LOCAL_MODEL -> "Local model"
        PredictionSource.BASELINE -> "Baseline"
        PredictionSource.CACHE -> "Cache"
        PredictionSource.COLLECTING_DATA -> "Collecting data"
    }

interface RemotePredictionClient {
    suspend fun predict(request: PredictionRequestDto): RemotePredictionResponseDto
}

interface LocalPredictionModel {
    fun predict(input: PredictionInput, completeDailySummaries: List<DailyUsageSummary>, dataQualityStatus: String): PredictionOutcome
}

data class PredictionRequestDto(
    val schemaVersion: Int,
    val dailySummaries: List<PredictionDailySummaryDto>,
    val todayCategoryMinutes: Map<String, Long>,
    val activeGoalLimitMinutes: Int?,
    val notificationCount: Int,
    val dataQualityStatus: String,
    val clientCalculatedAtMillis: Long,
) {
    companion object {
        fun from(input: PredictionInput): PredictionRequestDto {
            val dataQualityStatus = input.dailySummaries.lastOrNull { it.dataQuality != "COMPLETE" }?.dataQuality ?: "COMPLETE"
            return PredictionRequestDto(
                schemaVersion = 1,
                dailySummaries = input.dailySummaries.takeLast(14).map {
                    PredictionDailySummaryDto(
                        date = it.date,
                        totalMinutes = it.totalMinutes,
                        nightMinutes = it.nightMinutes,
                        sessionCount = it.sessionCount,
                        averageSessionMinutes = it.averageSessionMinutes,
                        maxSessionMinutes = it.maxSessionMinutes,
                        dataQuality = it.dataQuality,
                    )
                },
                todayCategoryMinutes = input.todayUsage
                    .groupBy { it.category }
                    .mapValues { (_, rows) -> rows.sumOf { it.minutes } },
                activeGoalLimitMinutes = input.activeGoalLimitMinutes,
                notificationCount = input.notificationCount,
                dataQualityStatus = dataQualityStatus,
                clientCalculatedAtMillis = input.calculatedAtMillis,
            )
        }
    }
}

data class PredictionDailySummaryDto(
    val date: String,
    val totalMinutes: Long,
    val nightMinutes: Long,
    val sessionCount: Int,
    val averageSessionMinutes: Long,
    val maxSessionMinutes: Long,
    val dataQuality: String,
)

data class RemotePredictionResponseDto(
    val modelVersion: String,
    val predictedNextDayMinutes: Long,
    val goalExceedanceRiskPercent: Int,
    val goalRiskLabel: String,
    val primaryFactors: List<String>,
    val recommendationText: String,
    val calculatedAtMillis: Long,
) {
    fun toOutcome(dataQualityStatus: String): PredictionOutcome =
        PredictionOutcome(
            source = PredictionSource.REMOTE_MODEL,
            predictedNextDayMinutes = predictedNextDayMinutes,
            goalExceedanceRiskPercent = goalExceedanceRiskPercent.coerceIn(0, 100),
            goalRiskLabel = goalRiskLabel,
            primaryFactors = primaryFactors,
            modelVersion = modelVersion,
            calculatedAtMillis = calculatedAtMillis,
            dataQualityStatus = dataQualityStatus,
            userMessage = "Remote model result from approved summary features.",
            recommendationText = recommendationText,
        )
}

class DefaultPredictionRepository(
    private val remoteClient: RemotePredictionClient? = null,
    private val localModel: LocalPredictionModel? = null,
) : PredictionRepository {
    override suspend fun predict(input: PredictionInput): PredictionOutcome {
        val complete = input.dailySummaries.filter { it.dataQuality == "COMPLETE" }
        val dataQualityStatus = input.dailySummaries.lastOrNull { it.dataQuality != "COMPLETE" }?.dataQuality ?: "COMPLETE"
        if (complete.size < MIN_PERSONALIZED_DAYS) {
            return collectingOutcome(input, complete.size, dataQualityStatus)
        }

        val remote = remoteClient
        if (remote != null) {
            runCatching {
                remote.predict(PredictionRequestDto.from(input)).toOutcome(dataQualityStatus)
            }.getOrNull()?.let { return it }
            input.cachedResult?.let { return it.copy(source = PredictionSource.CACHE, dataQualityStatus = dataQualityStatus) }
        }

        localModel?.let { model ->
            runCatching { model.predict(input, complete, dataQualityStatus) }
                .getOrNull()
                ?.let { return it }
        }
        input.cachedResult?.let { return it.copy(source = PredictionSource.CACHE, dataQualityStatus = dataQualityStatus) }
        return baselineOutcome(input, complete, dataQualityStatus)
    }

    private fun collectingOutcome(input: PredictionInput, completeDays: Int, dataQualityStatus: String): PredictionOutcome =
        PredictionOutcome(
            source = PredictionSource.COLLECTING_DATA,
            predictedNextDayMinutes = null,
            goalExceedanceRiskPercent = null,
            goalRiskLabel = "collecting_data",
            primaryFactors = listOf("Complete daily records: $completeDays of $MIN_PERSONALIZED_DAYS required."),
            modelVersion = "collecting",
            calculatedAtMillis = input.calculatedAtMillis,
            dataQualityStatus = dataQualityStatus,
            userMessage = "현재 ${completeDays}일의 기록이 수집되었습니다. 7일 이상 쌓이면 개인 기록을 입력으로 한 예측을 시작합니다.",
            recommendationText = "Recommendation is disabled until enough measured data is available.",
        )

    private fun baselineOutcome(input: PredictionInput, complete: List<DailyUsageSummary>, dataQualityStatus: String): PredictionOutcome {
        val recentAverage = complete.takeLast(7).map { it.totalMinutes }.average().toLong()
        val previousAverage = complete.dropLast(7).takeLast(7).map { it.totalMinutes }.averageOrNull() ?: recentAverage.toDouble()
        val trend = ((recentAverage - previousAverage) * 0.25).toLong()
        val predicted = roundToNearest10((recentAverage + trend).coerceAtLeast(0))
        val goal = input.activeGoalLimitMinutes
        val riskPercent = goal?.let { riskPercent(predicted, it) }
        return PredictionOutcome(
            source = PredictionSource.BASELINE,
            predictedNextDayMinutes = predicted,
            goalExceedanceRiskPercent = riskPercent,
            goalRiskLabel = riskPercent.label(),
            primaryFactors = listOf("Recent 7-day average", "Trend from previous week"),
            modelVersion = "baseline-7d",
            calculatedAtMillis = input.calculatedAtMillis,
            dataQualityStatus = dataQualityStatus,
            userMessage = "No trained local model result is available; showing an approximately rounded baseline estimate.",
            recommendationText = recommendationFor(predicted, goal),
        )
    }

    private fun List<Long>.averageOrNull(): Double? =
        if (isEmpty()) null else average()

    companion object {
        const val MIN_PERSONALIZED_DAYS = 7
    }
}

private fun riskPercent(predicted: Long, goal: Int): Int {
    if (goal <= 0) return 50
    val delta = predicted - goal
    return (50 + (delta * 100 / goal)).toInt().coerceIn(5, 95)
}

private fun Int?.label(): String =
    when {
        this == null -> "unknown"
        this >= 70 -> "over_goal_high"
        this >= 45 -> "over_goal_medium"
        else -> "within_goal"
    }

private fun recommendationFor(predicted: Long, goal: Int?): String {
    if (goal == null || goal <= 0) return "Review the estimate before creating any restriction rule."
    val gap = predicted - goal
    return if (gap > 0) {
        "Recommended limit: reduce about ${roundToNearest10(abs(gap))} minutes from the riskiest app. User approval is still required."
    } else {
        "Current goal looks reachable. Keep any restriction rule optional and user-approved."
    }
}

private fun roundToNearest10(value: Long): Long =
    ((value + 5) / 10) * 10
