package com.habitguard.app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ScreenTimingComposeSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun metricCardExposesMeasurementSourceThroughSemantics() {
        compose.setContent {
            ScreenTimingTheme {
                ScreenTimingMetricCard(
                    label = "총 사용 시간",
                    value = "2시간 10분",
                    sourceLabel = "측정값",
                )
            }
        }

        compose.onNodeWithContentDescription("총 사용 시간, 2시간 10분, 측정값")
            .assertIsDisplayed()
        compose.onNodeWithText("측정값").assertIsDisplayed()
    }

    @Test
    fun primaryButtonHasAccessibleClickAction() {
        compose.setContent {
            ScreenTimingTheme {
                ScreenTimingPrimaryButton(text = "규칙 승인", onClick = {})
            }
        }

        compose.onNodeWithText("규칙 승인")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
