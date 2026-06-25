package com.habitguard.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitguard.app.HabitGuardViewModel
import com.habitguard.app.model.AppUsageItem
import com.habitguard.app.model.DailyUsageSummary
import com.habitguard.app.model.GuardDebugEvent
import com.habitguard.app.model.HourlyUsageItem
import com.habitguard.app.model.ParsedGoal
import com.habitguard.app.model.RestrictionRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val label: String) {
    Home("홈"),
    Analysis("분석"),
    Goal("목표"),
    Report("리포트"),
    Privacy("개인정보"),
}

@Composable
fun HabitGuardApp(
    viewModel: HabitGuardViewModel,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onExportCsv: () -> Unit,
    onTestLock: () -> Unit,
    onClearLocalData: () -> Unit,
    onToggleFirestoreSync: (Boolean) -> Unit,
    onSyncFirestore: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.Home) }
    var parsedGoal by remember { mutableStateOf<ParsedGoal?>(null) }
    var showPermissionDialog by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LaunchedEffect(state.hasUsageAccess, state.hasAccessibilityAccess, state.hasNotificationAccess) {
        if (!state.hasUsageAccess || !state.hasAccessibilityAccess || !state.hasNotificationAccess) {
            showPermissionDialog = true
        }
    }

    ScreenTimingTheme {
        if (showPermissionDialog && (!state.hasUsageAccess || !state.hasAccessibilityAccess || !state.hasNotificationAccess)) {
            PermissionSetupDialog(
                hasUsageAccess = state.hasUsageAccess,
                hasAccessibilityAccess = state.hasAccessibilityAccess,
                hasNotificationAccess = state.hasNotificationAccess,
                onDismiss = { showPermissionDialog = false },
                onOpenUsageAccess = onOpenUsageAccess,
                onOpenAccessibility = onOpenAccessibility,
                onOpenNotificationAccess = onOpenNotificationAccess,
            )
        }

        Scaffold(
            bottomBar = { ScreenTimingTabBar(selected = tab, onSelect = { tab = it }) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = MaterialTheme.colorScheme.background,
            ) {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        state = state,
                        predictedTomorrow = viewModel.predictedTomorrow(),
                        onRefresh = viewModel::refresh,
                        onOpenUsageAccess = onOpenUsageAccess,
                        onOpenAccessibility = onOpenAccessibility,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        onGoGoal = { tab = Tab.Goal },
                        onGoAnalysis = { tab = Tab.Analysis },
                    )
                    Tab.Analysis -> AnalysisScreen(
                        apps = state.appUsage,
                        hourlyUsage = state.hourlyUsage,
                        dailySummaries = state.dailySummaries,
                        insights = state.insights,
                        riskWindowText = state.riskWindowText,
                        hasUsageAccess = state.hasUsageAccess,
                        onOpenUsageAccess = onOpenUsageAccess,
                    )
                    Tab.Goal -> GoalScreen(
                        parsedGoal = parsedGoal,
                        candidateApps = state.appUsage,
                        predictionRecommendationText = state.aiRecommendationText,
                        predictionSourceLabel = state.aiPredictionSourceLabel,
                        predictionDataQualityStatus = state.aiPredictionDataQualityStatus,
                        onParsed = { parsedGoal = viewModel.parseGoal(it) },
                        onReject = { parsedGoal = null },
                        onSave = { parsed, limit, start, end, mode, unlock ->
                            viewModel.saveRule(parsed, limit, start, end, mode, unlock)
                            tab = Tab.Home
                        },
                    )
                    Tab.Report -> WeeklyReportScreen(
                        dailySummaries = state.dailySummaries,
                        weeklyBaselineAverage = state.weeklyBaselineAverage,
                        weeklyWithRuleAverage = state.weeklyWithRuleAverage,
                        weeklySavedMinutes = state.weeklySavedMinutes,
                        missionAttempts7d = state.missionAttempts7d,
                        missionSuccessRate7d = state.missionSuccessRate7d,
                        missionFailures7d = state.missionFailures7d,
                        hasActiveGoal = state.hasActiveGoal,
                        goalProgressText = state.goalProgressText,
                        dataReadinessMessage = state.dataReadinessMessage,
                    )
                    Tab.Privacy -> PrivacySettingsScreen(
                        hasUsageAccess = state.hasUsageAccess,
                        hasAccessibilityAccess = state.hasAccessibilityAccess,
                        hasNotificationAccess = state.hasNotificationAccess,
                        onOpenUsageAccess = onOpenUsageAccess,
                        onOpenAccessibility = onOpenAccessibility,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        onExportCsv = onExportCsv,
                        onTestLock = onTestLock,
                        onClearLocalData = onClearLocalData,
                        firestoreSyncEnabled = state.firestoreSyncEnabled,
                        firestoreSyncStatus = state.firestoreSyncStatus,
                        onToggleFirestoreSync = onToggleFirestoreSync,
                        onSyncFirestore = onSyncFirestore,
                        guardEvents = state.guardDebugEvents,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: com.habitguard.app.model.DashboardState,
    predictedTomorrow: Long,
    onRefresh: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onGoGoal: () -> Unit,
    onGoAnalysis: () -> Unit,
) {
    val readiness = ScreenTimingUiCopy.predictionReadiness(
        personalizedPredictionReady = state.personalizedPredictionReady,
        collectedCompleteDays = state.collectedCompleteDays,
        predictedTomorrowMinutes = predictedTomorrow,
    )
    val risk = ScreenTimingUiCopy.riskPresentation(state.riskLabel)

    ScreenContainer {
        item {
            ScreenHeader(
                title = "HabitGuard",
                subtitle = "혼내지 않고, 측정값과 선택지를 차분히 보여주는 사용 습관 도우미입니다.",
            )
        }
        item {
            PermissionCenterCard(
                hasUsageAccess = state.hasUsageAccess,
                hasAccessibilityAccess = state.hasAccessibilityAccess,
                hasNotificationAccess = state.hasNotificationAccess,
                onOpenUsageAccess = onOpenUsageAccess,
                onOpenAccessibility = onOpenAccessibility,
                onOpenNotificationAccess = onOpenNotificationAccess,
            )
        }
        item {
            SectionLabel("오늘의 측정값")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
                ScreenTimingMetricCard("총 사용 시간", formatScreenTimingMinutes(state.totalMinutes), "측정값", Modifier.weight(1f))
                ScreenTimingMetricCard("야간 사용", formatScreenTimingMinutes(state.nightMinutes), "측정값", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
                ScreenTimingMetricCard("앱 실행", "${state.openCount}회", "측정값", Modifier.weight(1f))
                ScreenTimingMetricCard("알림 수", "${state.notificationCount}개", "측정값", Modifier.weight(1f))
            }
        }
        item {
            RiskCard(risk)
        }
        item {
            PredictionCard(
                readiness = readiness,
                predictionDisplayText = state.aiPredictionDisplayText,
                goalRiskText = state.aiGoalRiskText,
                recommendationText = state.aiRecommendationText,
                sourceLabel = state.aiPredictionSourceLabel,
                modelVersion = state.aiPredictionModelVersion,
                calculatedAtText = state.aiPredictionCalculatedAtText,
                dataQualityStatus = state.aiPredictionDataQualityStatus,
                sourceType = state.aiPredictionSourceType,
                evaluationScope = state.aiPredictionEvaluationScope,
                reasons = state.predictionReasons,
                caveat = state.aiModelCaveat,
            )
        }
        item {
            ActiveRuleCard(activeRule = state.activeRule, activeGoalText = state.activeGoalText)
        }
        item {
            ScreenTimingButtonRow {
                ScreenTimingPrimaryButton("목표와 규칙 만들기", onGoGoal, Modifier.weight(1f))
                ScreenTimingSecondaryButton("상세 분석 보기", onGoAnalysis, Modifier.weight(1f))
            }
        }
        item {
            ScreenTimingSecondaryButton("새로고침", onRefresh)
        }
    }
}

@Composable
private fun AnalysisScreen(
    apps: List<AppUsageItem>,
    hourlyUsage: List<HourlyUsageItem>,
    dailySummaries: List<DailyUsageSummary>,
    insights: List<String>,
    riskWindowText: String,
    hasUsageAccess: Boolean,
    onOpenUsageAccess: () -> Unit,
) {
    ScreenContainer {
        item {
            ScreenHeader(
                title = "상세 분석",
                subtitle = "앱별 측정값, 시간대별 흐름, 데이터 품질을 분리해서 확인합니다.",
            )
        }
        if (!hasUsageAccess) {
            item {
                EmptyStateCard(
                    title = "사용 기록 접근이 꺼져 있습니다",
                    body = "실제 앱별 사용 시간을 불러오려면 Android 설정에서 사용 기록 접근을 켜야 합니다.",
                    actionLabel = "사용 기록 설정",
                    onAction = onOpenUsageAccess,
                )
            }
        } else {
            item {
                SectionLabel("시간대별 사용")
                ChartCard("시간대별 사용 그래프", "막대가 높을수록 해당 시간대의 사용 시간이 깁니다. 야간 시간은 별도 색으로 표시합니다.") {
                    HourlyUsageChart(hourlyUsage)
                    Text(
                        text = riskWindowText.ifBlank { "아직 뚜렷한 위험 시간대가 계산되지 않았습니다." },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionLabel("최근 7일")
                ChartCard("최근 7일 사용 그래프", "각 막대는 하루 총 사용 시간입니다. 그래프 아래에는 날짜가 표시됩니다.") {
                    WeeklyUsageChart(dailySummaries)
                }
            }
            item {
                SectionLabel("앱별 측정값")
            }
            if (apps.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "아직 표시할 앱 사용 기록이 없습니다",
                        body = "권한을 켠 뒤 하루 이상 사용 기록이 쌓이면 앱별 시간이 여기에 표시됩니다.",
                        actionLabel = "새로 수집 기다리기",
                        onAction = null,
                    )
                }
            } else {
                items(apps, key = { it.packageName }) { app ->
                    AppUsageRow(app, apps.maxOfOrNull { it.minutes } ?: 1)
                }
            }
            if (insights.isNotEmpty()) {
                item { SectionLabel("분석 메모") }
                items(insights) { insight ->
                    StateInfoCard("측정 기반 메모", insight)
                }
            }
        }
    }
}

@Composable
private fun GoalScreen(
    parsedGoal: ParsedGoal?,
    candidateApps: List<AppUsageItem>,
    predictionRecommendationText: String,
    predictionSourceLabel: String,
    predictionDataQualityStatus: String,
    onParsed: (String) -> Unit,
    onReject: () -> Unit,
    onSave: (ParsedGoal, Int, Int, Int, String, Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    ScreenContainer {
        item {
            ScreenHeader(
                title = "자연어 목표 입력",
                subtitle = "원하는 변화를 문장으로 적으면 앱이 제한 규칙 초안을 만듭니다. 규칙은 승인 전에는 적용되지 않습니다.",
            )
        }
        item {
            ScreenTimingCard {
                Column(
                    modifier = Modifier.padding(ScreenTimingSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "자연어 목표 입력란" },
                        minLines = 3,
                        label = { Text("예: 밤 11시 이후 유튜브를 20분 이하로 줄이고 싶어요") },
                        supportingText = { Text("앱 이름, 시간대, 제한 시간을 포함하면 추천 정확도가 올라갑니다.") },
                    )
                    ScreenTimingPrimaryButton(
                        text = "추천 규칙 만들기",
                        enabled = text.isNotBlank(),
                        onClick = { onParsed(text) },
                    )
                }
            }
        }
        if (parsedGoal == null) {
            item {
                EmptyStateCard(
                    title = "아직 추천 규칙이 없습니다",
                    body = "목표 문장을 입력하면 추천 이유와 수정 가능한 제한값을 먼저 보여줍니다.",
                    actionLabel = "목표 문장 입력",
                    onAction = null,
                )
            }
        } else {
            item {
                RuleReviewCard(
                    parsedGoal = parsedGoal,
                    candidateApps = candidateApps,
                    predictionRecommendationText = predictionRecommendationText,
                    predictionSourceLabel = predictionSourceLabel,
                    predictionDataQualityStatus = predictionDataQualityStatus,
                    onReject = onReject,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun RuleReviewCard(
    parsedGoal: ParsedGoal,
    candidateApps: List<AppUsageItem>,
    predictionRecommendationText: String,
    predictionSourceLabel: String,
    predictionDataQualityStatus: String,
    onReject: () -> Unit,
    onSave: (ParsedGoal, Int, Int, Int, String, Int) -> Unit,
) {
    var limit by remember(parsedGoal) { mutableFloatStateOf(parsedGoal.limitMinutes.coerceAtLeast(5).toFloat()) }
    var start by remember(parsedGoal) { mutableIntStateOf(23) }
    var end by remember(parsedGoal) { mutableIntStateOf(6) }
    var unlock by remember(parsedGoal) { mutableFloatStateOf(10f) }
    var mode by remember(parsedGoal) { mutableStateOf(restrictionModeOptions().first()) }
    var missionType by remember(parsedGoal) { mutableStateOf(parsedGoal.missionType) }
    var targetName by remember(parsedGoal) { mutableStateOf(parsedGoal.targetName) }
    var targetPackage by remember(parsedGoal) { mutableStateOf(parsedGoal.targetPackage) }

    ScreenTimingCard(contentDescription = "AI 추천 규칙 검토 카드") {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("AI 추천 규칙 검토", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                ScreenTimingStatusBadge("승인 전", ScreenTimingUiCopy.Tone.Notice)
            }
            StateInfoCard(
                title = "추천 이유",
                body = parsedGoal.confidenceReasons.ifEmpty {
                    listOf("입력 문장에서 앱, 시간대, 제한 시간을 추정했습니다. 정확하지 않으면 수정하거나 거절하세요.")
                }.joinToString("\n"),
            )
            if (predictionRecommendationText.isNotBlank()) {
                StateInfoCard(
                    title = "AI result reference",
                    body = "Source: $predictionSourceLabel\nData quality: $predictionDataQualityStatus\nRecommendation: $predictionRecommendationText\nRules are not applied until you approve them.",
                )
            }
            if (candidateApps.isNotEmpty()) {
                Text("대상 앱 수정", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
                    items(candidateApps.take(12), key = { it.packageName }) { app ->
                        Button(
                            onClick = {
                                targetName = app.appName
                                targetPackage = app.packageName
                            },
                            shape = RoundedCornerShape(ScreenTimingSpacing.CardRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetPackage == app.packageName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (targetPackage == app.packageName) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.heightIn(min = ScreenTimingSpacing.MinTouch),
                        ) {
                            Text(app.appName)
                        }
                    }
                }
            }
            SliderBlock("하루 제한 시간", formatScreenTimingMinutes(limit.toLong()), limit, 5f, 240f) { limit = it }
            SliderBlock("미션 뒤 임시 해제", formatScreenTimingMinutes(unlock.toLong()), unlock, 5f, 30f) { unlock = it }
            NumberAdjustRow("시작 시간", "${start}시", onMinus = { start = (start + 23) % 24 }, onPlus = { start = (start + 1) % 24 })
            NumberAdjustRow("종료 시간", "${end}시", onMinus = { end = (end + 23) % 24 }, onPlus = { end = (end + 1) % 24 })
            OptionGroup("제한 방식", restrictionModeOptions(), mode) { mode = it }
            OptionGroup("미션", missionOptions(), missionType) { missionType = it }
            StateInfoCard(
                title = "적용 방식",
                body = "대상: ${targetName.ifBlank { "선택 필요" }}\n규칙은 아래 승인 버튼을 누른 뒤에만 저장되고 접근성 서비스 감지에 사용됩니다.",
            )
            ScreenTimingButtonRow {
                ScreenTimingSecondaryButton("거절", onReject, Modifier.weight(1f))
                ScreenTimingPrimaryButton(
                    text = "수정한 규칙 승인",
                    enabled = targetPackage.isNotBlank(),
                    onClick = {
                        onSave(
                            parsedGoal.copy(
                                targetName = targetName,
                                targetPackage = targetPackage,
                                missionType = missionType,
                            ),
                            limit.toInt(),
                            start,
                            end,
                            mode,
                            unlock.toInt(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeeklyReportScreen(
    dailySummaries: List<DailyUsageSummary>,
    weeklyBaselineAverage: Long,
    weeklyWithRuleAverage: Long,
    weeklySavedMinutes: Long,
    missionAttempts7d: Int,
    missionSuccessRate7d: Int,
    missionFailures7d: Int,
    hasActiveGoal: Boolean,
    goalProgressText: String,
    dataReadinessMessage: String,
) {
    ScreenContainer {
        item {
            ScreenHeader(
                title = "주간 리포트",
                subtitle = "실제 측정값과 규칙 적용 후 예상 효과를 분리해서 보여줍니다.",
            )
        }
        item {
            ChartCard("주간 사용 그래프", "최근 최대 7일의 총 사용 시간 막대 그래프입니다.") {
                WeeklyUsageChart(dailySummaries)
            }
        }
        item {
            SectionLabel("측정값과 예상 효과")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
                ScreenTimingMetricCard("기준 평균", formatScreenTimingMinutes(weeklyBaselineAverage), "측정값", Modifier.weight(1f))
                ScreenTimingMetricCard("규칙 적용 예상", formatScreenTimingMinutes(weeklyWithRuleAverage), "AI 예측", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
                ScreenTimingMetricCard("예상 절감", formatScreenTimingMinutes(weeklySavedMinutes), "AI 예측", Modifier.weight(1f))
                ScreenTimingMetricCard("미션 성공률", "${missionSuccessRate7d}%", "측정값", Modifier.weight(1f))
            }
        }
        item {
            StateInfoCard(
                title = if (hasActiveGoal) "현재 목표" else "목표가 아직 없습니다",
                body = if (hasActiveGoal) goalProgressText.ifBlank { "활성 목표가 있습니다." } else "목표를 만들면 이 리포트가 목표 기준으로 더 구체화됩니다.",
            )
        }
        item {
            StateInfoCard(
                title = "데이터 상태",
                body = dataReadinessMessage.ifBlank { "완전한 사용 기록이 더 쌓이면 주간 비교가 더 안정적입니다." },
            )
        }
        item {
            StateInfoCard(
                title = "미션 기록",
                body = "최근 7일 미션 시도 ${missionAttempts7d}회, 완료하지 못한 흐름 ${missionFailures7d}회입니다. 이 숫자는 판단이 아니라 다음 규칙 조정의 참고값입니다.",
            )
        }
    }
}

@Composable
private fun PrivacySettingsScreen(
    hasUsageAccess: Boolean,
    hasAccessibilityAccess: Boolean,
    hasNotificationAccess: Boolean,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onExportCsv: () -> Unit,
    onTestLock: () -> Unit,
    onClearLocalData: () -> Unit,
    firestoreSyncEnabled: Boolean,
    firestoreSyncStatus: String,
    onToggleFirestoreSync: (Boolean) -> Unit,
    onSyncFirestore: () -> Unit,
    guardEvents: List<GuardDebugEvent>,
) {
    var clearArmed by remember { mutableStateOf(false) }

    ScreenContainer {
        item {
            ScreenHeader(
                title = "설정과 개인정보 센터",
                subtitle = "민감한 사용 기록은 기본적으로 기기에 저장됩니다. 클라우드 전송은 동의 후 요약값만 대상으로 합니다.",
            )
        }
        item {
            PermissionCenterCard(
                hasUsageAccess = hasUsageAccess,
                hasAccessibilityAccess = hasAccessibilityAccess,
                hasNotificationAccess = hasNotificationAccess,
                onOpenUsageAccess = onOpenUsageAccess,
                onOpenAccessibility = onOpenAccessibility,
                onOpenNotificationAccess = onOpenNotificationAccess,
            )
        }
        item {
            SectionLabel("데이터 내보내기와 삭제")
            ScreenTimingCard {
                Column(
                    modifier = Modifier.padding(ScreenTimingSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
                ) {
                    Text("CSV 내보내기는 사용자가 직접 공유할 때만 실행됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ScreenTimingPrimaryButton("30일 CSV 내보내기", onExportCsv)
                    ScreenTimingSecondaryButton(
                        text = if (clearArmed) "한 번 더 누르면 로컬 데이터 삭제" else "로컬 분석 데이터 삭제",
                        onClick = {
                            if (clearArmed) {
                                onClearLocalData()
                                clearArmed = false
                            } else {
                                clearArmed = true
                            }
                        },
                    )
                    if (clearArmed) {
                        Text(
                            "삭제는 Room에 저장된 목표, 규칙, 사용 요약, 미션 기록을 대상으로 합니다. Android 권한 설정 자체는 바뀌지 않습니다.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        item {
            SectionLabel("클라우드 동기화")
            ScreenTimingCard {
                Column(
                    modifier = Modifier.padding(ScreenTimingSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
                ) {
                    Text("현재 Firestore 동기화는 운영 인증 UI가 없는 요약 전송 골격입니다. 실제 원본 사용 로그는 기본 업로드 대상이 아닙니다.")
                    ScreenTimingButtonRow {
                        ScreenTimingSecondaryButton(
                            text = if (firestoreSyncEnabled) "동의 해제" else "요약 동기화 동의",
                            onClick = { onToggleFirestoreSync(!firestoreSyncEnabled) },
                            modifier = Modifier.weight(1f),
                        )
                        ScreenTimingPrimaryButton(
                            text = "요약 동기화",
                            enabled = firestoreSyncEnabled,
                            onClick = onSyncFirestore,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        firestoreSyncStatus.ifBlank { "현재 상태: ${if (firestoreSyncEnabled) "동의 켜짐" else "동의 꺼짐"}" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SectionLabel("제한 및 미션 화면")
            ScreenTimingCard {
                Column(
                    modifier = Modifier.padding(ScreenTimingSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
                ) {
                    Text("미션 화면은 차분한 안내 화면으로 열리며, 실제 앱 차단이 아니라 사용자 승인 규칙에 따른 사용 중단 흐름입니다.")
                    ScreenTimingPrimaryButton("미션 화면 테스트", onTestLock)
                }
            }
        }
        item {
            SectionLabel("운영 점검")
        }
        if (guardEvents.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "아직 제한 이벤트가 없습니다",
                    body = "승인된 규칙을 만든 뒤 대상 앱을 열면 최근 이벤트가 여기에 표시됩니다.",
                    actionLabel = "기록 대기",
                    onAction = null,
                )
            }
        } else {
            items(guardEvents) { event ->
                GuardEventRow(event)
            }
        }
    }
}

@Composable
private fun PermissionSetupDialog(
    hasUsageAccess: Boolean,
    hasAccessibilityAccess: Boolean,
    hasNotificationAccess: Boolean,
    onDismiss: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    val items = ScreenTimingUiCopy.permissionItems(hasUsageAccess, hasAccessibilityAccess, hasNotificationAccess)
    val next = items.firstOrNull { it.statusLabel != "켜짐" }
    val nextAction = when (next?.title) {
        "사용 기록 접근" -> onOpenUsageAccess
        "접근성 서비스" -> onOpenAccessibility
        else -> onOpenNotificationAccess
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("권한 센터") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
                Text("필요한 권한을 사용자가 직접 켠 뒤에만 측정과 제한 흐름이 동작합니다.")
                items.forEach { item ->
                    Text("${item.title}: ${item.statusLabel}")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    nextAction()
                },
                enabled = next != null,
            ) {
                Text(next?.actionLabel ?: "모두 확인됨")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("나중에") }
        },
    )
}

@Composable
private fun PermissionCenterCard(
    hasUsageAccess: Boolean,
    hasAccessibilityAccess: Boolean,
    hasNotificationAccess: Boolean,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    val items = ScreenTimingUiCopy.permissionItems(hasUsageAccess, hasAccessibilityAccess, hasNotificationAccess)
    val actions = listOf(onOpenUsageAccess, onOpenAccessibility, onOpenNotificationAccess)

    ScreenTimingCard(contentDescription = "온보딩 및 권한 센터") {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
        ) {
            SectionLabel("온보딩 및 권한 센터")
            items.forEachIndexed { index, item ->
                PermissionRow(item = item, onClick = actions[index])
            }
        }
    }
}

@Composable
private fun PermissionRow(item: ScreenTimingUiCopy.PermissionItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = item.contentDescription },
        horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, fontWeight = FontWeight.Bold)
                ScreenTimingStatusBadge(
                    text = item.statusLabel,
                    tone = if (item.statusLabel == "켜짐") ScreenTimingUiCopy.Tone.Good else ScreenTimingUiCopy.Tone.Notice,
                )
            }
            Text(item.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = onClick,
            modifier = Modifier.heightIn(min = ScreenTimingSpacing.MinTouch),
            shape = RoundedCornerShape(ScreenTimingSpacing.CardRadius),
        ) {
            Text("설정")
        }
    }
}

@Composable
private fun RiskCard(risk: ScreenTimingUiCopy.RiskPresentation) {
    ScreenTimingCard(
        tone = risk.tone,
        contentDescription = "위험도 ${risk.title}, ${risk.iconDescription}",
    ) {
        Row(
            modifier = Modifier.padding(ScreenTimingSpacing.Large),
            horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = risk.iconDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text(risk.iconText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
            Column(verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.XSmall)) {
                Text("위험도: ${risk.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(risk.body)
            }
        }
    }
}

@Composable
private fun PredictionCard(
    readiness: ScreenTimingUiCopy.StateMessage,
    predictionDisplayText: String,
    goalRiskText: String,
    recommendationText: String,
    sourceLabel: String,
    modelVersion: String,
    calculatedAtText: String,
    dataQualityStatus: String,
    sourceType: String,
    evaluationScope: String,
    reasons: List<String>,
    caveat: String,
) {
    ScreenTimingCard(contentDescription = "AI 예측 카드") {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                Text(readiness.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                ScreenTimingStatusBadge("AI 예측", ScreenTimingUiCopy.Tone.Notice)
            }
            Text(readiness.body)
            StateInfoCard(
                title = "로컬 학습 모델 예측",
                body = "Next-day screen time: ${predictionDisplayText.ifBlank { readiness.body }}\nGoal risk: ${goalRiskText.ifBlank { "not available" }}",
            )
            StateInfoCard(
                title = "데이터 품질과 모델 버전",
                body = "Source: ${sourceLabel.ifBlank { "not available" }}\nsource_type: ${sourceType.ifBlank { "not available" }}\nevaluation_scope: ${evaluationScope.ifBlank { "not available" }}\nModel version: ${modelVersion.ifBlank { "not available" }}\nCalculated at: ${calculatedAtText.ifBlank { "not available" }}\nData quality: ${dataQualityStatus.ifBlank { "not available" }}",
            )
            if (recommendationText.isNotBlank()) {
                StateInfoCard(
                title = "추천값",
                    body = "$recommendationText\n모델 결과만으로 제한 규칙을 자동 적용하지 않습니다.",
                )
            }
            reasons.take(3).forEach { reason ->
                Text("이유: $reason", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (caveat.isNotBlank()) {
                Text("제한: $caveat", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActiveRuleCard(activeRule: RestrictionRule?, activeGoalText: String) {
    ScreenTimingCard {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                Text("추천 규칙 상태", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                ScreenTimingStatusBadge(if (activeRule == null) "없음" else "사용자 승인됨", if (activeRule == null) ScreenTimingUiCopy.Tone.Neutral else ScreenTimingUiCopy.Tone.Good)
            }
            if (activeRule == null) {
                Text("아직 승인된 제한 규칙이 없습니다. 추천은 자동 적용되지 않습니다.")
            } else {
                Text("${activeRule.targetName}: 하루 ${formatScreenTimingMinutes(activeRule.limitMinutes.toLong())}, ${activeRule.startHour}시-${activeRule.endHour}시")
                Text(activeGoalText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppUsageRow(app: AppUsageItem, max: Long) {
    val ratio = app.minutes.toFloat() / max.coerceAtLeast(1)
    ScreenTimingCard(contentDescription = "${app.appName}, ${formatScreenTimingMinutes(app.minutes)}, ${app.category}") {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.appName, fontWeight = FontWeight.Bold)
                Text(formatScreenTimingMinutes(app.minutes))
            }
            Text(
                "${app.category} · 실행 ${app.openCount}회 · 야간 ${formatScreenTimingMinutes(app.nightMinutes)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .semantics { contentDescription = "${app.appName} 사용 비율 막대" },
            ) {
                drawRoundRect(color = Color(0xFFE3EAF4), size = size)
                drawRoundRect(
                    color = ScreenTimingColors.Green,
                    size = Size(width = size.width * ratio.coerceIn(0f, 1f), height = size.height),
                )
            }
        }
    }
}

@Composable
private fun HourlyUsageChart(hours: List<HourlyUsageItem>) {
    val normalized = if (hours.isEmpty()) (0..23).map { HourlyUsageItem(it, 0) } else hours
    val max = normalized.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics { contentDescription = "24시간 사용량 막대 그래프" },
    ) {
        val gap = size.width / 24f
        normalized.take(24).forEach { item ->
            val barHeight = (item.minutes.toFloat() / max) * (size.height - 10.dp.toPx())
            val isNight = item.hour >= 22 || item.hour < 6
            drawRoundRect(
                color = if (isNight) ScreenTimingColors.Amber else ScreenTimingColors.Blue,
                topLeft = Offset(item.hour * gap + 1.dp.toPx(), size.height - barHeight),
                size = Size((gap - 2.dp.toPx()).coerceAtLeast(2.dp.toPx()), barHeight),
            )
        }
    }
}

@Composable
private fun WeeklyUsageChart(days: List<DailyUsageSummary>) {
    val normalized = days.takeLast(7)
    val max = normalized.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .semantics { contentDescription = "최근 7일 사용 시간 막대 그래프" },
        ) {
            if (normalized.isEmpty()) {
                drawLine(
                    color = ScreenTimingColors.Hairline,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 4.dp.toPx(),
                )
                return@Canvas
            }
            val gap = size.width / normalized.size.coerceAtLeast(1)
            normalized.forEachIndexed { index, day ->
                val ratio = day.totalMinutes.toFloat() / max
                val barHeight = (ratio * (size.height - 18.dp.toPx())).coerceAtLeast(8.dp.toPx())
                drawRoundRect(
                    color = when {
                        day.dataQuality != "COMPLETE" -> ScreenTimingColors.Amber
                        ratio >= 0.85f -> ScreenTimingColors.Blue
                        else -> ScreenTimingColors.Green
                    },
                    topLeft = Offset(index * gap + 4.dp.toPx(), size.height - barHeight),
                    size = Size((gap - 8.dp.toPx()).coerceAtLeast(8.dp.toPx()), barHeight),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            normalized.forEach { day ->
                Text(day.date.takeLast(5), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SliderBlock(
    label: String,
    value: String,
    current: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.XSmall)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(value)
        }
        Slider(value = current, onValueChange = onChange, valueRange = min..max)
    }
}

@Composable
private fun NumberAdjustRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ScreenTimingSecondaryButton("-", onMinus, Modifier.weight(0.35f))
        ScreenTimingSecondaryButton("+", onPlus, Modifier.weight(0.35f))
    }
}

@Composable
private fun OptionGroup(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
        Text(title, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
            items(options) { option ->
                Button(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(ScreenTimingSpacing.CardRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (option == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.heightIn(min = ScreenTimingSpacing.MinTouch),
                ) {
                    Text(option)
                }
            }
        }
    }
}

@Composable
private fun GuardEventRow(event: GuardDebugEvent) {
    ScreenTimingCard(tone = if (event.blocked) ScreenTimingUiCopy.Tone.Notice else ScreenTimingUiCopy.Tone.Neutral) {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.XSmall),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(event.eventType, fontWeight = FontWeight.Bold)
                Text(formatTimestamp(event.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(event.packageName)
            Text(
                "blocked=${event.blocked}, unlockTokenValid=${event.unlockTokenValid}, missionResult=${event.missionResult}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, description: String, content: @Composable () -> Unit) {
    ScreenTimingCard(contentDescription = "$title. $description") {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String, actionLabel: String, onAction: (() -> Unit)?) {
    ScreenTimingCard(contentDescription = "$title. $body") {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(body)
            if (onAction != null) {
                ScreenTimingPrimaryButton(actionLabel, onAction)
            } else {
                ScreenTimingStatusBadge(actionLabel, ScreenTimingUiCopy.Tone.Neutral)
            }
        }
    }
}

@Composable
private fun StateInfoCard(title: String, body: String) {
    ScreenTimingCard {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.XSmall),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScreenTimingTabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenTimingSpacing.Small, vertical = ScreenTimingSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tab.entries.forEach { item ->
                Button(
                    onClick = { onSelect(item) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = ScreenTimingSpacing.MinTouch)
                        .semantics { contentDescription = "${item.label} 탭" },
                    shape = RoundedCornerShape(ScreenTimingSpacing.CardRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected == item) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                ) {
                    Text(item.label, fontWeight = if (selected == item) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.semantics { heading() },
        )
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ScreenContainer(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp)
                .padding(ScreenTimingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
            content = content,
        )
    }
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun missionOptions(): List<String> =
    listOf(
        "30초 대기",
        "1분 대기 + 이유 입력",
        "1분 대기 + 간단 계산",
        "1분 대기 + 대체 행동 입력",
        "3분 대기 + 목표 확인",
        "1분 대기 + 문장 따라쓰기",
    )

private fun restrictionModeOptions(): List<String> =
    listOf(
        "한도 초과 시 미션",
        "시간대 안에서는 즉시 미션",
        "앱 실행마다 짧은 확인",
        "가벼운 알림만",
    )
