package com.habitguard.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPredictionPresenterTest {
    @Test
    fun collectingDataStateHidesPrecisePredictionAndShowsReadinessMessage() {
        val ui = DashboardPredictionPresenter.present(
            outcome = PredictionOutcome(
                source = PredictionSource.COLLECTING_DATA,
                predictedNextDayMinutes = null,
                goalExceedanceRiskPercent = null,
                goalRiskLabel = "collecting_data",
                primaryFactors = listOf("3 of 7 complete days"),
                modelVersion = "collecting",
                calculatedAtMillis = 1_700_000_000_000,
                dataQualityStatus = "COMPLETE",
                userMessage = "현재 3일의 기록이 수집되었습니다. 7일 이상 쌓이면 개인 기록을 입력으로 한 예측을 시작합니다.",
                recommendationText = "not ready",
            )
        )

        assertEquals(0, ui.predictedTomorrowMinutes)
        assertFalse(ui.personalizedPredictionReady)
        assertTrue(ui.predictionDisplayText.contains("3일"))
        assertFalse(ui.predictionDisplayText.contains("240"))
    }

    @Test
    fun readyStateSeparatesPredictionRiskModelAndRecommendationFields() {
        val ui = DashboardPredictionPresenter.present(
            outcome = PredictionOutcome(
                source = PredictionSource.LOCAL_MODEL,
                predictedNextDayMinutes = 260,
                goalExceedanceRiskPercent = 72,
                goalRiskLabel = "over_goal",
                primaryFactors = listOf("Recent 7-day average"),
                modelVersion = "screen-timing-linear-logistic-v1-test",
                calculatedAtMillis = 1_700_000_000_000,
                dataQualityStatus = "COMPLETE",
                userMessage = "synthetic caveat",
                recommendationText = "Approval required.",
                sourceType = "synthetic",
                evaluationScope = "synthetic evaluation",
            )
        )

        assertEquals(260, ui.predictedTomorrowMinutes)
        assertTrue(ui.personalizedPredictionReady)
        assertEquals("약 260분", ui.predictionDisplayText)
        assertEquals("Local model", ui.sourceLabel)
        assertEquals("screen-timing-linear-logistic-v1-test", ui.modelVersion)
        assertEquals("over_goal (72%)", ui.goalRiskText)
        assertEquals("COMPLETE", ui.dataQualityStatus)
        assertEquals("synthetic", ui.sourceType)
        assertEquals("synthetic evaluation", ui.evaluationScope)
        assertTrue(ui.recommendationText.contains("Approval"))
    }
}
