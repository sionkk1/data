package com.habitguard.app.ai

import com.habitguard.app.model.AppUsageItem
import com.habitguard.app.model.ParsedGoal

object GoalParser {
    fun parse(rawText: String, installedApps: List<AppUsageItem>): ParsedGoal {
        val text = rawText.lowercase()
        val reasons = mutableListOf<String>()
        var confidence = 20
        val matchedApp = installedApps.firstOrNull { app ->
            val appName = app.appName.lowercase()
            val packageName = app.packageName.lowercase()
            (appName.length >= 2 && appName in text) || packageName in text
        }
        if (matchedApp != null) {
            confidence += 34
            reasons += "설치된 앱 이름과 직접 매칭"
        }
        val targetName = when {
            matchedApp != null -> matchedApp.appName
            "게임" in text -> {
                confidence += 20
                reasons += "게임 카테고리 키워드 감지"
                "게임"
            }
            "인스타" in text || "instagram" in text -> {
                confidence += 24
                reasons += "Instagram 키워드 감지"
                "Instagram"
            }
            "쇼츠" in text -> {
                confidence += 22
                reasons += "쇼츠 키워드로 YouTube 추정"
                "YouTube"
            }
            "유튜브" in text || "youtube" in text -> {
                confidence += 24
                reasons += "YouTube 키워드 감지"
                "YouTube"
            }
            else -> "앱 선택 필요"
        }
        val targetPackage = matchedApp?.packageName ?: installedApps.firstOrNull {
            it.appName.contains(targetName, ignoreCase = true) ||
                it.packageName.contains(targetName.lowercase(), ignoreCase = true)
        }?.packageName ?: when (targetName) {
            "Instagram" -> "com.instagram.android"
            "YouTube" -> "com.google.android.youtube"
            else -> ""
        }
        val targetCategory = matchedApp?.category ?: when (targetName) {
            "YouTube" -> "영상"
            "Instagram" -> "SNS"
            "게임" -> "게임"
            else -> "기타"
        }
        if (targetPackage.isNotBlank()) confidence += 8
        val minuteLimit = Regex("(\\d+)\\s*분").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val hourLimit = Regex("(\\d+)\\s*시간").find(text)?.groupValues?.get(1)?.toIntOrNull()?.times(60)
        val limit = minuteLimit ?: hourLimit
            ?: when {
                "게임" in text -> 30
                "공부" in text -> 15
                else -> 90
            }
        if (minuteLimit != null || hourLimit != null) {
            confidence += 18
            reasons += "제한 시간이 숫자로 명시됨"
        } else {
            reasons += "제한 시간은 기본 추천값 사용"
        }
        val timeRange = when {
            "자기" in text || "밤" in text -> {
                confidence += 10
                reasons += "야간/취침 전 시간대 감지"
                "밤 11시 이후"
            }
            "공부" in text -> {
                confidence += 10
                reasons += "공부 시간대 감지"
                "공부 시간"
            }
            "시험" in text -> {
                confidence += 10
                reasons += "시험기간 조건 감지"
                "시험기간"
            }
            else -> "매일"
        }
        val intensity = if (limit <= 30 || "안 보" in text || "시험" in text) "강함" else "보통"
        confidence += if (intensity == "강함") 6 else 4
        reasons += "제한 강도 $intensity 추천"
        val mission = when {
            "명언" in text || "따라" in text -> "1분 대기 + 명언 따라쓰기"
            intensity == "강함" -> "3분 대기 + 목표 확인"
            else -> "1분 대기 + 이유 입력"
        }
        reasons += "미션 $mission 추천"
        if (targetPackage.isBlank()) {
            confidence -= 18
            reasons += "제한할 실제 앱 패키지는 사용자 확인 필요"
        }
        val score = confidence.coerceIn(15, 96)
        return ParsedGoal(
            targetName = targetName,
            targetPackage = targetPackage,
            timeRange = timeRange,
            limitMinutes = limit,
            intensity = intensity,
            missionType = mission,
            rawText = rawText,
            targetCategory = targetCategory,
            confidenceScore = score,
            confidenceReasons = reasons,
            needsUserConfirmation = score < 70 || targetPackage.isBlank(),
        )
    }
}
