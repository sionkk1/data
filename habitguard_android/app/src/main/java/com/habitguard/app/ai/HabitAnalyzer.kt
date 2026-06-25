package com.habitguard.app.ai

import com.habitguard.app.model.AppUsageItem
import com.habitguard.app.model.DailyUsageSummary
import com.habitguard.app.model.HourlyUsageItem

object HabitAnalyzer {
    fun riskLabel(
        totalMinutes: Long,
        nightMinutes: Long,
        unlockCountEstimate: Long,
        notificationCount: Long = 0,
    ): String {
        var score = 0
        if (totalMinutes > 390) score += 34 else if (totalMinutes > 330) score += 22
        if (nightMinutes > 95) score += 28 else if (nightMinutes > 65) score += 16
        if (unlockCountEstimate > 80) score += 18
        if (notificationCount > 100) score += 14 else if (notificationCount > 60) score += 8
        return when {
            score >= 60 -> "높음"
            score >= 34 -> "보통"
            else -> "낮음"
        }
    }

    fun healthScore(
        totalMinutes: Long,
        nightMinutes: Long,
        hasActiveGoal: Boolean = false,
        goalAchievedToday: Boolean = false,
        missionSuccessRate: Int = 0,
        missionFailures: Int = 0,
    ): Int {
        val penalty = (totalMinutes * 0.08 + nightMinutes * 0.16).toInt()
        val goalBonus = if (!hasActiveGoal) 0 else if (goalAchievedToday) 10 else -8
        val missionAdjustment = when {
            missionSuccessRate >= 80 -> 6
            missionSuccessRate >= 50 -> 2
            missionFailures >= 3 -> -8
            else -> 0
        }
        return (100 - penalty + goalBonus + missionAdjustment).coerceIn(20, 100)
    }

    fun predictTomorrow(totalMinutes: Long, nightMinutes: Long): Long =
        (totalMinutes * 0.72 + nightMinutes * 0.35 + 38).toLong().coerceAtLeast(120)

    fun predictTomorrowFromHistory(
        dailySummaries: List<DailyUsageSummary>,
        fallbackTotalMinutes: Long,
        fallbackNightMinutes: Long,
    ): Long {
        if (dailySummaries.size < 4) return predictTomorrow(fallbackTotalMinutes, fallbackNightMinutes)
        val recent = dailySummaries.takeLast(7).map { it.totalMinutes }
        val previous = dailySummaries.dropLast(7).takeLast(7).map { it.totalMinutes }
        val recentAverage = recent.averageOrZero()
        val previousAverage = previous.averageOrZero(default = recentAverage)
        val trend = (recentAverage - previousAverage) * 0.35
        val lastDay = recent.lastOrNull()?.toDouble() ?: fallbackTotalMinutes.toDouble()
        return (recentAverage * 0.52 + lastDay * 0.34 + trend)
            .toLong()
            .coerceAtLeast(90)
    }

    fun successProbability(totalMinutes: Long, nightMinutes: Long, hasRule: Boolean): Int {
        var score = 76 - (totalMinutes / 18).toInt() - (nightMinutes / 12).toInt()
        if (hasRule) score += 22
        return score.coerceIn(18, 88)
    }

    fun simulatedReduction(totalMinutes: Long, hasRule: Boolean): Long {
        val ratio = if (hasRule) 0.24 else 0.12
        return (totalMinutes * ratio).toLong().coerceAtLeast(20)
    }

    fun weeklyBaselineAverage(dailySummaries: List<DailyUsageSummary>, fallbackMinutes: Long): Long {
        val recent = dailySummaries.takeLast(7).map { it.totalMinutes }
        if (recent.isEmpty()) return fallbackMinutes
        val trend = if (dailySummaries.size >= 14) {
            recent.average() - dailySummaries.dropLast(7).takeLast(7).map { it.totalMinutes }.averageOrZero(default = recent.average())
        } else {
            0.0
        }
        return (recent.average() + trend * 0.25).toLong().coerceAtLeast(60)
    }

    fun weeklyWithRuleAverage(baselineAverage: Long, hasRule: Boolean, missionSuccessRate: Int): Long {
        val ratio = when {
            !hasRule -> 0.08
            missionSuccessRate >= 80 -> 0.28
            missionSuccessRate >= 50 -> 0.22
            else -> 0.16
        }
        return (baselineAverage * (1.0 - ratio)).toLong().coerceAtLeast(45)
    }

    fun predictionReasons(
        dailySummaries: List<DailyUsageSummary>,
        topApps: List<AppUsageItem>,
        hourlyUsage: List<HourlyUsageItem> = emptyList(),
    ): List<String> {
        val recent = dailySummaries.takeLast(7).map { it.totalMinutes }
        val previous = dailySummaries.dropLast(7).takeLast(7).map { it.totalMinutes }
        val recentAverage = recent.averageOrZero()
        val previousAverage = previous.averageOrZero(default = recentAverage)
        val diff = (recentAverage - previousAverage).toLong()
        val topApp = topApps.firstOrNull()
        val riskWindow = riskWindow(hourlyUsage)
        return listOfNotNull(
            if (dailySummaries.size >= 14) {
                "최근 7일 평균은 ${recentAverage.toLong()}분으로 전주 대비 ${signed(diff)}분 변화했습니다."
            } else {
                "30일 데이터가 충분히 쌓이면 최근 7일과 전주 평균을 비교해 예측합니다."
            },
            topApp?.let { "오늘 가장 큰 영향 요인은 ${it.appName} ${it.minutes}분 사용입니다." },
            riskWindow.takeIf { it.isNotBlank() }?.let { "오늘 과사용 가능 시간대는 $it 입니다." },
            "예측은 최근 평균, 마지막 사용일, 전주 대비 추세를 함께 반영합니다.",
        )
    }

    fun riskWindow(hourlyUsage: List<HourlyUsageItem>): String {
        val top = hourlyUsage.maxByOrNull { it.minutes } ?: return ""
        if (top.minutes < 15) return "아직 뚜렷하지 않음"
        val nextHour = (top.hour + 1) % 24
        return "${top.hour}시~${nextHour}시 (${top.minutes}분)"
    }

    fun habitType(
        apps: List<AppUsageItem>,
        totalMinutes: Long,
        nightMinutes: Long,
        notificationCount: Int,
    ): String {
        val youtubeMinutes = apps
            .filter { it.appName.contains("YouTube", true) || it.packageName.contains("youtube", true) }
            .sumOf { it.minutes }
        val gameMinutes = apps
            .filter { it.appName.contains("game", true) || it.packageName.contains("game", true) }
            .sumOf { it.minutes }
        val snsMinutes = apps
            .filter {
                it.appName.contains("Instagram", true) ||
                    it.appName.contains("Kakao", true) ||
                    it.appName.contains("Facebook", true) ||
                    it.packageName.contains("instagram", true)
            }
            .sumOf { it.minutes }
        return when {
            nightMinutes > 90 && youtubeMinutes > totalMinutes * 0.22 -> "밤샘 쇼츠형"
            gameMinutes > 70 || gameMinutes > totalMinutes * 0.28 -> "게임 몰입형"
            notificationCount > 80 && snsMinutes > 40 -> "알림 확인형"
            snsMinutes > totalMinutes * 0.24 -> "SNS 반복 확인형"
            totalMinutes > 360 -> "전반적 과사용형"
            else -> "균형 관리형"
        }
    }

    fun habitTypeDescription(type: String): String =
        when (type) {
            "밤샘 쇼츠형" -> "야간 영상 사용이 많아 취침 전 영상 앱 제한과 긴 미션이 효과적입니다."
            "게임 몰입형" -> "게임 사용 시간이 몰려 있어 평일/주말 제한 시간을 분리하는 편이 좋습니다."
            "알림 확인형" -> "알림 이후 앱 실행 가능성이 높아 알림 접근 분석과 공부 시간 제한이 중요합니다."
            "SNS 반복 확인형" -> "짧은 SNS 확인이 반복되는 유형이라 실행 전 대기 미션이 효과적입니다."
            "전반적 과사용형" -> "특정 앱 하나보다 전체 사용량이 높아 상위 앱 2~3개를 함께 제한해야 합니다."
            else -> "현재 패턴은 비교적 안정적입니다. 약한 제한으로 습관을 유지하는 것이 좋습니다."
        }

    fun insights(
        apps: List<AppUsageItem>,
        totalMinutes: Long,
        nightMinutes: Long,
        notificationCount: Int = 0,
    ): List<String> {
        val top = apps.firstOrNull()
        val topShare = if (top != null && totalMinutes > 0) (top.minutes * 100 / totalMinutes) else 0
        val habitType = habitType(apps, totalMinutes, nightMinutes, notificationCount)
        return listOfNotNull(
            "현재 사용 유형은 $habitType 입니다.",
            top?.let { "${it.appName} 사용량이 ${it.minutes}분으로 가장 높고, 전체의 ${topShare}%입니다." },
            if (nightMinutes > 70) "밤 시간대 사용량이 높아 취침 전 제한 규칙이 효과적입니다." else "야간 사용량은 아직 낮지만 반복 패턴 확인이 필요합니다.",
            if (notificationCount > 60) "오늘 알림이 ${notificationCount}개 수집되어 알림 확인 습관이 위험도에 반영되었습니다." else null,
            if (apps.any { it.appName.contains("YouTube", true) && it.minutes > 80 }) "영상 앱 사용이 예측 모델의 주요 증가 요인입니다." else null,
        )
    }

    private fun List<Long>.averageOrZero(default: Double = 0.0): Double =
        if (isEmpty()) default else average()

    private fun signed(value: Long): String =
        if (value >= 0) "+$value" else value.toString()
}
