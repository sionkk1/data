package com.habitguard.app.ai

import com.habitguard.app.model.AppUsageItem
import com.habitguard.app.model.DailyUsageSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionRepositoryTest {
    @Test
    fun returnsCollectingStateWithoutPrecisePredictionBeforeSevenCompleteDays() = runBlocking {
        val repository = DefaultPredictionRepository()
        val result = repository.predict(
            PredictionInput(
                dailySummaries = completeDays(3),
                todayUsage = emptyList(),
                hourlyUsage = emptyList(),
                activeGoalLimitMinutes = 240,
                cachedResult = null,
                calculatedAtMillis = 1_700_000_000_000,
            )
        )

        assertEquals(PredictionSource.COLLECTING_DATA, result.source)
        assertNull(result.predictedNextDayMinutes)
        assertEquals("COMPLETE", result.dataQualityStatus)
        assertTrue(result.userMessage.contains("3"))
        assertTrue(result.userMessage.contains("7"))
    }

    @Test
    fun usesCacheWhenRemoteFailsAndNoLocalModelIsAvailable() = runBlocking {
        val repository = DefaultPredictionRepository(
            remoteClient = object : RemotePredictionClient {
                override suspend fun predict(request: PredictionRequestDto): RemotePredictionResponseDto {
                    error("server unavailable")
                }
            },
        )
        val cached = PredictionOutcome(
            source = PredictionSource.LOCAL_MODEL,
            predictedNextDayMinutes = 250,
            goalExceedanceRiskPercent = 63,
            goalRiskLabel = "over_goal",
            primaryFactors = listOf("cached factor"),
            modelVersion = "local-profile-v3",
            calculatedAtMillis = 1_699_999_999_000,
            dataQualityStatus = "COMPLETE",
            userMessage = "cached",
            recommendationText = "cached recommendation",
        )

        val result = repository.predict(
            PredictionInput(
                dailySummaries = completeDays(10),
                todayUsage = emptyList(),
                hourlyUsage = emptyList(),
                activeGoalLimitMinutes = 240,
                cachedResult = cached,
                calculatedAtMillis = 1_700_000_000_000,
            )
        )

        assertEquals(PredictionSource.CACHE, result.source)
        assertEquals(250L, result.predictedNextDayMinutes)
        assertEquals("cached factor", result.primaryFactors.single())
    }

    @Test
    fun usesCacheWhenLocalModelFailsValidationOrInference() = runBlocking {
        val repository = DefaultPredictionRepository(
            localModel = object : LocalPredictionModel {
                override fun predict(
                    input: PredictionInput,
                    completeDailySummaries: List<DailyUsageSummary>,
                    dataQualityStatus: String,
                ): PredictionOutcome {
                    error("damaged bundle")
                }
            },
        )
        val cached = PredictionOutcome(
            source = PredictionSource.LOCAL_MODEL,
            predictedNextDayMinutes = 230,
            goalExceedanceRiskPercent = 55,
            goalRiskLabel = "over_goal_medium",
            primaryFactors = listOf("stored local result"),
            modelVersion = "cached",
            calculatedAtMillis = 1_699_999_999_000,
            dataQualityStatus = "COMPLETE",
            userMessage = "cached",
            recommendationText = "cached recommendation",
        )

        val result = repository.predict(
            PredictionInput(
                dailySummaries = completeDays(9),
                todayUsage = emptyList(),
                hourlyUsage = emptyList(),
                activeGoalLimitMinutes = 220,
                cachedResult = cached,
                calculatedAtMillis = 1_700_000_000_000,
            )
        )

        assertEquals(PredictionSource.CACHE, result.source)
        assertEquals(230L, result.predictedNextDayMinutes)
    }

    @Test
    fun usesBaselineWhenLocalModelFailsAndCacheIsMissing() = runBlocking {
        val repository = DefaultPredictionRepository(
            localModel = object : LocalPredictionModel {
                override fun predict(
                    input: PredictionInput,
                    completeDailySummaries: List<DailyUsageSummary>,
                    dataQualityStatus: String,
                ): PredictionOutcome {
                    error("damaged bundle")
                }
            },
        )

        val result = repository.predict(
            PredictionInput(
                dailySummaries = completeDays(9),
                todayUsage = emptyList(),
                hourlyUsage = emptyList(),
                activeGoalLimitMinutes = 220,
                cachedResult = null,
                calculatedAtMillis = 1_700_000_000_000,
            )
        )

        assertEquals(PredictionSource.BASELINE, result.source)
        assertEquals(0, result.predictedNextDayMinutes!! % 10)
    }

    @Test
    fun baselineFallbackRoundsPredictionWhenNoModelResultExists() = runBlocking {
        val repository = DefaultPredictionRepository()
        val result = repository.predict(
            PredictionInput(
                dailySummaries = completeDays(8),
                todayUsage = emptyList(),
                hourlyUsage = emptyList(),
                activeGoalLimitMinutes = 220,
                cachedResult = null,
                calculatedAtMillis = 1_700_000_000_000,
            )
        )

        assertEquals(PredictionSource.BASELINE, result.source)
        assertEquals("baseline-7d", result.modelVersion)
        assertEquals(0, result.predictedNextDayMinutes!! % 10)
        assertTrue(result.userMessage.contains("approximately"))
    }

    @Test
    fun remoteRequestOmitsRawTimelineNotificationBodyAndFreeText() {
        val request = PredictionRequestDto.from(
            PredictionInput(
                dailySummaries = completeDays(8),
                todayUsage = listOf(
                    AppUsageItem(
                        packageName = "com.example.private",
                        appName = "Private Messenger",
                        minutes = 42,
                        lastTimeUsed = 1_700_000_000_000,
                        category = "SNS",
                    )
                ),
                hourlyUsage = emptyList(),
                activeGoalLimitMinutes = 240,
                notificationCount = 17,
                cachedResult = null,
                calculatedAtMillis = 1_700_000_000_000,
            )
        )
        val serialized = request.toString()

        assertFalse(serialized.contains("com.example.private"))
        assertFalse(serialized.contains("Private Messenger"))
        assertFalse(serialized.contains("notification body", ignoreCase = true))
        assertFalse(serialized.contains("raw goal", ignoreCase = true))
        assertEquals(42L, request.todayCategoryMinutes["SNS"])
        assertEquals(17, request.notificationCount)
    }

    private fun completeDays(count: Int): List<DailyUsageSummary> =
        (1..count).map { day ->
            DailyUsageSummary(
                date = "2026-06-${day.toString().padStart(2, '0')}",
                totalMinutes = 180L + day * 10L,
                topAppName = "Top App",
                topAppMinutes = 60,
                nightMinutes = 20,
                dataQuality = "COMPLETE",
            )
        }
}
