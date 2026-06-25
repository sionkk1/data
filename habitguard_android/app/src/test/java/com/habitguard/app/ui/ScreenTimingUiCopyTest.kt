package com.habitguard.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimingUiCopyTest {
    @Test
    fun permissionCenterShowsMissingPermissionActions() {
        val items = ScreenTimingUiCopy.permissionItems(
            hasUsageAccess = false,
            hasAccessibilityAccess = true,
            hasNotificationAccess = false,
        )

        assertEquals(3, items.size)
        assertEquals("사용 기록 접근", items[0].title)
        assertEquals("필요", items[0].statusLabel)
        assertTrue(items[0].actionLabel.contains("설정"))
        assertEquals("켜짐", items[1].statusLabel)
        assertEquals("선택", items[2].statusLabel)
    }

    @Test
    fun predictionReadinessDoesNotShowFakeNumbersWhenDataIsInsufficient() {
        val message = ScreenTimingUiCopy.predictionReadiness(
            personalizedPredictionReady = false,
            collectedCompleteDays = 3,
            predictedTomorrowMinutes = 240,
        )

        assertEquals("측정값 3일 수집됨", message.title)
        assertTrue(message.body.contains("7일 이상"))
        assertTrue(message.actionLabel.contains("사용 기록"))
        assertFalse(message.body.contains("240"))
    }

    @Test
    fun riskPresentationUsesTextAndIconLabelBeyondColor() {
        val high = ScreenTimingUiCopy.riskPresentation("high")

        assertEquals("주의 필요", high.title)
        assertEquals("상승 아이콘", high.iconDescription)
        assertTrue(high.body.contains("색상만으로"))
    }
}
