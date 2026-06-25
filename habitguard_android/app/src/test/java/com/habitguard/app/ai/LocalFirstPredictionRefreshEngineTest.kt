package com.habitguard.app.ai

import com.habitguard.app.data.AppUsageDailyEntity
import com.habitguard.app.data.PredictionResultEntity
import com.habitguard.app.data.UsageDailyEntity
import com.habitguard.app.data.UserGoalEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFirstPredictionRefreshEngineTest {
    @Test
    fun savesLocalModelPredictionFromRoomDailySnapshot() = runBlocking {
        val repository = object : PredictionRepository {
            override suspend fun predict(input: PredictionInput): PredictionOutcome {
                assertEquals(7, input.dailySummaries.size)
                assertEquals(2, input.todayUsage.size)
                assertEquals(180, input.activeGoalLimitMinutes)
                assertEquals(5, input.notificationCount)
                return PredictionOutcome(
                    source = PredictionSource.LOCAL_MODEL,
                    predictedNextDayMinutes = 210,
                    goalExceedanceRiskPercent = 64,
                    goalRiskLabel = "over_goal",
                    primaryFactors = listOf("rolling_7d_total"),
                    modelVersion = "local-test",
                    calculatedAtMillis = input.calculatedAtMillis,
                    dataQualityStatus = "COMPLETE",
                    userMessage = "synthetic caveat",
                    recommendationText = "user approval required",
                    sourceType = "synthetic",
                    evaluationScope = "synthetic evaluation",
                )
            }
        }
        val saved = mutableListOf<PredictionResultEntity>()
        val engine = LocalFirstPredictionRefreshEngine(repository)

        val outcome = engine.refresh(
            snapshot = LocalFirstPredictionSnapshot(
                targetDate = "2026-06-22",
                dailyUsage = completeUsageDays(),
                appUsage = appUsageForToday(),
                activeGoal = activeGoal(),
                notificationCount = 5,
                cachedPrediction = null,
                habitType = "night_video",
                calculatedAtMillis = 1_771_234_000_000,
            ),
            savePrediction = { saved += it },
        )

        assertEquals(PredictionSource.LOCAL_MODEL, outcome.source)
        assertEquals(1, saved.size)
        assertEquals("2026-06-22", saved.single().date)
        assertEquals(210, saved.single().predictedNextDayMinutes)
        assertEquals("over_goal", saved.single().riskLevel)
        assertEquals("night_video", saved.single().habitType)
        assertEquals("rolling_7d_total", saved.single().mainReason)
    }

    @Test
    fun doesNotSaveCollectingDataOutcome() = runBlocking {
        val repository = object : PredictionRepository {
            override suspend fun predict(input: PredictionInput): PredictionOutcome =
                PredictionOutcome(
                    source = PredictionSource.COLLECTING_DATA,
                    predictedNextDayMinutes = null,
                    goalExceedanceRiskPercent = null,
                    goalRiskLabel = "collecting_data",
                    primaryFactors = emptyList(),
                    modelVersion = "collecting",
                    calculatedAtMillis = input.calculatedAtMillis,
                    dataQualityStatus = "COMPLETE",
                    userMessage = "collecting",
                    recommendationText = "disabled",
                )
        }
        val saved = mutableListOf<PredictionResultEntity>()
        val engine = LocalFirstPredictionRefreshEngine(repository)

        engine.refresh(
            snapshot = LocalFirstPredictionSnapshot(
                targetDate = "2026-06-22",
                dailyUsage = completeUsageDays().take(3),
                appUsage = appUsageForToday(),
                activeGoal = activeGoal(),
                notificationCount = 5,
                cachedPrediction = null,
                habitType = "night_video",
                calculatedAtMillis = 1_771_234_000_000,
            ),
            savePrediction = { saved += it },
        )

        assertTrue(saved.isEmpty())
    }

    private fun completeUsageDays(): List<UsageDailyEntity> =
        (16..22).map { day ->
            UsageDailyEntity(
                date = "2026-06-$day",
                totalScreenTimeMinutes = 120L + day,
                nightMinutes = 20,
                unlockCountEstimate = 4,
                topAppPackage = "com.example.video",
                topAppName = "Video App",
                topAppMinutes = 70,
                videoMinutes = 80,
                snsMinutes = 20,
                gameMinutes = 0,
                productivityMinutes = 10,
                otherMinutes = 30,
                sessionCount = 4,
                averageSessionMinutes = 30,
                maxSessionMinutes = 55,
                dataQuality = "COMPLETE",
                capturedAt = 1_771_000_000_000 + day,
            )
        }

    private fun appUsageForToday(): List<AppUsageDailyEntity> =
        listOf(
            AppUsageDailyEntity(
                date = "2026-06-22",
                packageName = "com.example.video",
                appName = "Video App",
                category = "video",
                usageMinutes = 80,
                nightMinutes = 15,
                openCount = 3,
                firstOpenTime = 1,
                lastTimeUsed = 2,
                capturedAt = 3,
            ),
            AppUsageDailyEntity(
                date = "2026-06-22",
                packageName = "com.example.chat",
                appName = "Chat App",
                category = "sns",
                usageMinutes = 25,
                nightMinutes = 5,
                openCount = 2,
                firstOpenTime = 1,
                lastTimeUsed = 2,
                capturedAt = 3,
            ),
        )

    private fun activeGoal(): UserGoalEntity =
        UserGoalEntity(
            rawText = "Video 180 minutes",
            targetName = "Video App",
            targetPackage = "com.example.video",
            timeRange = "all day",
            limitMinutes = 180,
            intensity = "medium",
            missionType = "math",
            createdAt = 1_771_000_000_000,
            isActive = true,
        )
}
