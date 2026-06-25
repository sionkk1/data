package com.habitguard.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.habitguard.app.ai.GoalParser
import com.habitguard.app.ai.AiModelProfileLoader
import com.habitguard.app.ai.DashboardPredictionPresenter
import com.habitguard.app.ai.DefaultPredictionRepository
import com.habitguard.app.ai.HabitAnalyzer
import com.habitguard.app.ai.ModelBundleLoader
import com.habitguard.app.ai.PredictionInput
import com.habitguard.app.ai.PredictionOutcome
import com.habitguard.app.ai.PredictionSource
import com.habitguard.app.ai.TrainedLocalPredictionModel
import com.habitguard.app.data.DailyUsageSyncWorker
import com.habitguard.app.data.FirestoreSyncRepository
import com.habitguard.app.data.IgnoredPackages
import com.habitguard.app.data.PredictionResultEntity
import com.habitguard.app.data.RestrictionRuleEntity
import com.habitguard.app.data.ServiceLocator
import com.habitguard.app.data.UsageDataQuality
import com.habitguard.app.data.UserGoalEntity
import com.habitguard.app.data.UsageStatsRepository
import com.habitguard.app.guard.HabitGuardAccessibilityService
import com.habitguard.app.guard.HabitGuardNotificationListenerService
import com.habitguard.app.model.DashboardState
import com.habitguard.app.model.DailyUsageSummary
import com.habitguard.app.model.GuardDebugEvent
import com.habitguard.app.model.ParsedGoal
import com.habitguard.app.model.RestrictionRule
import com.habitguard.app.ui.HabitGuardApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var habitGuardViewModel: HabitGuardViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailyUsageSyncWorker.schedule(applicationContext)
        requestNotificationPostPermissionIfNeeded()
        setContent {
            val vm: HabitGuardViewModel = viewModel(
                factory = HabitGuardViewModel.factory(applicationContext)
            )
            habitGuardViewModel = vm
            HabitGuardApp(
                viewModel = vm,
                onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onOpenNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                onExportCsv = { exportThirtyDayCsv() },
                onTestLock = { launchLockTest() },
                onClearLocalData = { vm.clearLocalData() },
                onToggleFirestoreSync = { vm.setFirestoreSyncEnabled(it) },
                onSyncFirestore = { vm.syncFirestoreSummary() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        habitGuardViewModel?.refresh()
    }

    private fun launchLockTest() {
        lifecycleScope.launch {
            val rule = ServiceLocator.database(applicationContext).ruleDao().enabledRules().firstOrNull()
            val intent = Intent(this@MainActivity, LockActivity::class.java)
                .putExtra(LockActivity.EXTRA_PACKAGE_NAME, rule?.targetPackage ?: packageName)
                .putExtra(LockActivity.EXTRA_APP_NAME, rule?.targetName ?: "HabitGuard 테스트 앱")
                .putExtra(LockActivity.EXTRA_MISSION_TYPE, rule?.missionType ?: "30초 대기")
                .putExtra(LockActivity.EXTRA_UNLOCK_MINUTES, rule?.unlockMinutes ?: 5)
            startActivity(intent)
        }
    }

    private fun requestNotificationPostPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private fun exportThirtyDayCsv() {
        lifecycleScope.launch {
            val database = ServiceLocator.database(applicationContext)
            val rows = database.appUsageDailyDao()
                .recent(1000)
                .filterNot { IgnoredPackages.isUsageNoise(it.packageName, applicationContext.packageName) }
                .reversed()
            val notifications = database.notificationDailyDao()
                .recent(1000)
                .associateBy { it.date to it.packageName }
            val exportDir = File(cacheDir, "exports").also { it.mkdirs() }
            val csv = File(exportDir, "habitguard_30_day_app_usage.csv")
            val header = "date,package_name,app_name,category,usage_minutes,night_minutes,open_count,notification_count,first_open_time,last_time_used,captured_at"
            val body = rows.joinToString("\n") {
                val notificationCount = notifications[it.date to it.packageName]?.notificationCount ?: 0
                listOf(
                    it.date,
                    it.packageName,
                    it.appName.replace("\"", "\"\""),
                    it.category,
                    it.usageMinutes,
                    it.nightMinutes,
                    it.openCount,
                    notificationCount,
                    it.firstOpenTime,
                    it.lastTimeUsed,
                    it.capturedAt,
                ).joinToString(",") { value -> "\"$value\"" }
            }
            csv.writeText("$header\n$body", Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                this@MainActivity,
                "${applicationContext.packageName}.fileprovider",
                csv,
            )
            val shareIntent = Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(shareIntent, "30일 사용 데이터 내보내기"))
        }
    }
}

class HabitGuardViewModel(application: Application) : AndroidViewModel(application) {
    @SuppressLint("StaticFieldLeak")
    private val context = application.applicationContext
    private val usageRepo = UsageStatsRepository(context)
    private val db = ServiceLocator.database(context)
    private val firestoreSyncRepository = FirestoreSyncRepository(context, db)
    private val aiModelProfile = AiModelProfileLoader.load(context)
    private val androidInferenceBundle = ModelBundleLoader.load(context)
    private val predictionRepository = DefaultPredictionRepository(
        localModel = androidInferenceBundle?.let { TrainedLocalPredictionModel(it) },
    )
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state
    val hasUsageAccess: Boolean get() = usageRepo.hasUsageAccess()

    fun refresh() {
        viewModelScope.launch {
            val aggregate = usageRepo.aggregateDailyUsage(days = 30)
            aggregate.daily.forEach { db.usageDailyDao().upsert(it) }
            val dateRange = aggregate.daily.map { it.date }
            if (dateRange.isNotEmpty()) {
                db.appUsageDailyDao().deleteBetween(dateRange.first(), dateRange.last())
                db.appUsageDailyDao().upsertAll(aggregate.appDaily)
            }

            val hasUsageAccess = usageRepo.hasUsageAccess()
            val usage = if (hasUsageAccess) usageRepo.queryTodayUsage().take(12) else emptyList()
            val hourlyUsage = if (hasUsageAccess) usageRepo.queryTodayHourlyUsage() else emptyList()
            val storedDaily = db.usageDailyDao().recent(30).reversed()
            val dailySummaries = storedDaily.map {
                DailyUsageSummary(
                    date = it.date,
                    totalMinutes = it.totalScreenTimeMinutes,
                    topAppName = it.topAppName,
                    topAppMinutes = it.topAppMinutes,
                    nightMinutes = it.nightMinutes,
                    sessionCount = it.sessionCount,
                    averageSessionMinutes = it.averageSessionMinutes,
                    maxSessionMinutes = it.maxSessionMinutes,
                    dataQuality = it.dataQuality,
                )
            }
            val completeDailySummaries = dailySummaries.filter { it.dataQuality == UsageDataQuality.COMPLETE.name }
            val collectedCompleteDays = completeDailySummaries.size
            val recent7DayAverage = completeDailySummaries.takeLast(7).averageMinutes()
            val personalBaselineMinutes = completeDailySummaries.takeLast(14).ifEmpty { completeDailySummaries }.averageMinutes()
            val hasEnoughCompleteDays = collectedCompleteDays >= 7
            val dataReadinessMessage = if (hasEnoughCompleteDays) {
                "최근 7일 평균 ${recent7DayAverage}분, 개인 기준선 ${personalBaselineMinutes}분을 실제 수집 데이터로 계산했습니다."
            } else {
                "현재 ${collectedCompleteDays}일의 기록이 수집되었습니다. 7일 이상 쌓이면 개인화 예측을 시작합니다."
            }

            val total = usage.sumOf { it.minutes }
            val night = usage.sumOf { it.nightMinutes }
            val openCount = usage.sumOf { it.openCount }
            val notificationCount = db.notificationDailyDao().totalForDate(LocalDate.now().toString())
            val rule = db.ruleDao().enabledRules().firstOrNull()?.toModel()
            val activeGoal = db.userGoalDao().active()
            val hasActiveGoal = activeGoal != null
            val targetUsageMinutes = activeGoal?.targetPackage
                ?.takeIf { it.isNotBlank() }
                ?.let { targetPackage -> usage.firstOrNull { it.packageName == targetPackage }?.minutes }
                ?: 0
            val goalAchievedToday = activeGoal?.let { targetUsageMinutes <= it.limitMinutes } ?: false
            val goalProgressText = activeGoal?.let {
                "${it.targetName} 오늘 ${targetUsageMinutes}분 / 목표 ${it.limitMinutes}분"
            }.orEmpty()
            val missionSince = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli()
            val missionAttempts = db.missionLogDao().countSince(missionSince)
            val missionSuccesses = db.missionLogDao().successCountSince(missionSince)
            val missionFailures = db.missionLogDao().failureCountSince(missionSince)
            val missionSuccessRate = if (missionAttempts > 0) {
                (missionSuccesses * 100 / missionAttempts)
            } else {
                0
            }
            val riskLabel = HabitAnalyzer.riskLabel(
                totalMinutes = total,
                nightMinutes = night,
                unlockCountEstimate = openCount.toLong(),
                notificationCount = notificationCount.toLong(),
            )
            val habitType = HabitAnalyzer.habitType(usage, total, night, notificationCount)
            val cachedPrediction = db.predictionResultDao().latest()?.toPredictionOutcome()
            val predictionOutcome = predictionRepository.predict(
                PredictionInput(
                    dailySummaries = dailySummaries,
                    todayUsage = usage,
                    hourlyUsage = hourlyUsage,
                    activeGoalLimitMinutes = activeGoal?.limitMinutes,
                    notificationCount = notificationCount,
                    cachedResult = cachedPrediction,
                    calculatedAtMillis = System.currentTimeMillis(),
                )
            )
            val personalizedPredictionReady = predictionOutcome.source != PredictionSource.COLLECTING_DATA
            val predictedTomorrow = predictionOutcome.predictedNextDayMinutes ?: 0
            val predictionUi = DashboardPredictionPresenter.present(
                outcome = predictionOutcome,
                calculatedAtText = formatPredictionTime(predictionOutcome.calculatedAtMillis),
            )
            val riskWindowText = HabitAnalyzer.riskWindow(hourlyUsage)
            val predictionReasons = if (predictionOutcome.primaryFactors.isNotEmpty()) {
                buildList {
                    if (personalizedPredictionReady) {
                        add("출처: ${predictionOutcome.source.displayLabel()}, 모델: ${predictionOutcome.modelVersion}")
                        if (predictionOutcome.sourceType.isNotBlank()) {
                            add("source_type=${predictionOutcome.sourceType}, evaluation_scope=${predictionOutcome.evaluationScope}")
                        }
                    }
                    aiModelProfile?.takeIf { personalizedPredictionReady }?.let {
                        add("실제 수집 데이터 기반 모델 참고: ${it.summary}, ${it.performanceSummary}")
                        add(it.topFeatureSummary)
                    }
                    addAll(predictionOutcome.primaryFactors)
                }
            } else {
                listOf(predictionOutcome.userMessage)
            }
            val weeklyBaselineAverage = if (hasEnoughCompleteDays) {
                HabitAnalyzer.weeklyBaselineAverage(completeDailySummaries, predictedTomorrow)
            } else {
                recent7DayAverage
            }
            val weeklyWithRuleAverage = HabitAnalyzer.weeklyWithRuleAverage(
                baselineAverage = weeklyBaselineAverage,
                hasRule = rule != null,
                missionSuccessRate = missionSuccessRate,
            )
            val weeklySavedMinutes = (weeklyBaselineAverage - weeklyWithRuleAverage).coerceAtLeast(0)
            if (personalizedPredictionReady && predictionOutcome.predictedNextDayMinutes != null) {
                db.predictionResultDao().upsert(
                    PredictionResultEntity(
                        date = LocalDate.now().toString(),
                        predictedNextDayMinutes = predictionOutcome.predictedNextDayMinutes,
                        riskLevel = predictionOutcome.goalRiskLabel,
                        habitType = habitType,
                        mainReason = predictionOutcome.primaryFactors.firstOrNull().orEmpty(),
                        createdAt = predictionOutcome.calculatedAtMillis,
                    )
                )
            }
            val latestPrediction = if (personalizedPredictionReady) db.predictionResultDao().latest() else null
            val guardDebugEvents = db.guardEventDao().recent(20).map {
                GuardDebugEvent(
                    packageName = it.packageName,
                    eventType = it.eventType,
                    blocked = it.blocked,
                    unlockTokenValid = it.unlockTokenValid,
                    missionResult = it.missionResult,
                    createdAt = it.createdAt,
                )
            }
            _state.value = DashboardState(
                totalMinutes = total,
                nightMinutes = night,
                openCount = openCount,
                notificationCount = notificationCount,
                hasUsageAccess = hasUsageAccess,
                hasAccessibilityAccess = hasAccessibilityAccess(),
                hasNotificationAccess = hasNotificationAccess(),
                riskLabel = riskLabel,
                habitType = habitType,
                habitTypeDescription = HabitAnalyzer.habitTypeDescription(habitType),
                healthScore = HabitAnalyzer.healthScore(
                    totalMinutes = total,
                    nightMinutes = night,
                    hasActiveGoal = hasActiveGoal,
                    goalAchievedToday = goalAchievedToday,
                    missionSuccessRate = missionSuccessRate,
                    missionFailures = missionFailures,
                ),
                missionAttempts7d = missionAttempts,
                missionSuccessRate7d = missionSuccessRate,
                missionFailures7d = missionFailures,
                hasActiveGoal = hasActiveGoal,
                goalAchievedToday = goalAchievedToday,
                goalProgressText = goalProgressText,
                appUsage = usage,
                dailySummaries = dailySummaries,
                hourlyUsage = hourlyUsage,
                riskWindowText = riskWindowText,
                weeklyBaselineAverage = weeklyBaselineAverage,
                weeklyWithRuleAverage = weeklyWithRuleAverage,
                weeklySavedMinutes = weeklySavedMinutes,
                collectedCompleteDays = collectedCompleteDays,
                recent7DayAverage = recent7DayAverage,
                personalBaselineMinutes = personalBaselineMinutes,
                dataReadinessMessage = dataReadinessMessage,
                personalizedPredictionReady = personalizedPredictionReady,
                aiPredictedTomorrowMinutes = predictionUi.predictedTomorrowMinutes,
                aiPredictionDisplayText = predictionUi.predictionDisplayText,
                aiPredictionSourceLabel = predictionUi.sourceLabel,
                aiPredictionModelVersion = predictionUi.modelVersion,
                aiPredictionCalculatedAtText = predictionUi.calculatedAtText,
                aiPredictionDataQualityStatus = predictionUi.dataQualityStatus,
                aiPredictionSourceType = predictionUi.sourceType,
                aiPredictionEvaluationScope = predictionUi.evaluationScope,
                aiGoalRiskText = predictionUi.goalRiskText,
                aiRecommendationText = predictionUi.recommendationText,
                aiModelSummary = if (personalizedPredictionReady) aiModelProfile?.summary.orEmpty() else "",
                aiModelPerformance = if (personalizedPredictionReady) aiModelProfile?.performanceSummary.orEmpty() else "",
                aiModelTopFeatures = if (personalizedPredictionReady) aiModelProfile?.topFeatureSummary.orEmpty() else "",
                aiModelCaveat = if (personalizedPredictionReady) {
                    predictionOutcome.userMessage.takeIf { predictionOutcome.sourceType == "synthetic" } ?: aiModelProfile?.caveat.orEmpty()
                } else {
                    ""
                },
                predictionReasons = predictionReasons,
                insights = HabitAnalyzer.insights(usage, total, night, notificationCount),
                activeGoalText = activeGoal?.let {
                    "${it.rawText.ifBlank { it.targetName }} -> ${it.targetName} ${it.limitMinutes}분 ${it.timeRange}, ${it.intensity}"
                }.orEmpty(),
                latestPredictionText = latestPrediction?.let {
                    "${it.date} 예측 저장: 내일 ${it.predictedNextDayMinutes}분, 위험도 ${it.riskLevel}, 유형 ${it.habitType}"
                }.orEmpty(),
                activeRule = rule,
                guardDebugEvents = guardDebugEvents,
                firestoreSyncEnabled = firestoreSyncRepository.isConsentEnabled,
                firestoreSyncStatus = _state.value.firestoreSyncStatus,
            )
        }
    }

    private fun List<DailyUsageSummary>.averageMinutes(): Long =
        if (isEmpty()) 0 else sumOf { it.totalMinutes } / size

    fun parseGoal(text: String): ParsedGoal {
        val apps = usageRepo.launchableApps()
        return GoalParser.parse(text, apps)
    }

    fun saveRule(parsed: ParsedGoal, limit: Int, startHour: Int, endHour: Int, restrictionMode: String, unlockMinutes: Int) {
        viewModelScope.launch {
            db.userGoalDao().deactivateAll()
            db.userGoalDao().insert(
                UserGoalEntity(
                    rawText = parsed.rawText,
                    targetName = parsed.targetName,
                    targetPackage = parsed.targetPackage,
                    timeRange = parsed.timeRange,
                    limitMinutes = limit,
                    intensity = parsed.intensity,
                    missionType = parsed.missionType,
                    createdAt = System.currentTimeMillis(),
                    isActive = true,
                )
            )
            db.ruleDao().upsert(
                RestrictionRuleEntity(
                    targetPackage = parsed.targetPackage,
                    targetName = parsed.targetName,
                    limitMinutes = limit,
                    startHour = startHour,
                    endHour = endHour,
                    restrictionMode = restrictionMode,
                    missionType = parsed.missionType,
                    unlockMinutes = unlockMinutes,
                    approved = true,
                    activeDaysMask = 127,
                    sessionLimitMinutes = 0,
                    maxUnlocksPerDay = 3,
                    emergencyUnlockMinutes = 5,
                    enabled = true,
                )
            )
            refresh()
        }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            val maintenance = db.maintenanceDao()
            maintenance.clearRestrictionRules()
            maintenance.clearMissionLogs()
            maintenance.clearUsageDaily()
            maintenance.clearAppUsageDaily()
            maintenance.clearNotificationDaily()
            maintenance.clearUserGoals()
            maintenance.clearPredictionResults()
            maintenance.clearGuardEvents()
            maintenance.clearUnlockSessions()
            _state.value = DashboardState(
                hasUsageAccess = usageRepo.hasUsageAccess(),
                hasAccessibilityAccess = hasAccessibilityAccess(),
                hasNotificationAccess = hasNotificationAccess(),
                firestoreSyncEnabled = firestoreSyncRepository.isConsentEnabled,
                firestoreSyncStatus = _state.value.firestoreSyncStatus,
            )
        }
    }

    fun setFirestoreSyncEnabled(enabled: Boolean) {
        firestoreSyncRepository.setConsentEnabled(enabled)
        _state.value = _state.value.copy(
            firestoreSyncEnabled = enabled,
            firestoreSyncStatus = if (enabled) {
                "동의됨: 목표, 제한 규칙, 주간 요약만 업로드 대상입니다."
            } else {
                "동의 해제됨: Firestore 업로드를 하지 않습니다."
            },
        )
    }

    fun syncFirestoreSummary() {
        viewModelScope.launch {
            val result = firestoreSyncRepository.syncConsentedSummary()
            _state.value = _state.value.copy(
                firestoreSyncEnabled = firestoreSyncRepository.isConsentEnabled,
                firestoreSyncStatus = result.message,
            )
        }
    }

    fun predictedTomorrow(): Long =
        _state.value.aiPredictedTomorrowMinutes

    private fun hasAccessibilityAccess(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val serviceName = ComponentName(
            context,
            HabitGuardAccessibilityService::class.java,
        ).flattenToString()
        return enabledServices.split(':').any { it.equals(serviceName, ignoreCase = true) }
    }

    private fun hasNotificationAccess(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val listenerName = ComponentName(
            context,
            HabitGuardNotificationListenerService::class.java,
        ).flattenToString()
        return enabledListeners.split(':').any { it.equals(listenerName, ignoreCase = true) }
    }

    companion object {
        fun factory(context: android.content.Context) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HabitGuardViewModel(context.applicationContext as Application) as T
        }
    }
}

private fun RestrictionRuleEntity.toModel(): RestrictionRule =
    RestrictionRule(
        targetPackage = targetPackage,
        targetName = targetName,
        limitMinutes = limitMinutes,
        startHour = startHour,
        endHour = endHour,
        restrictionMode = restrictionMode,
        missionType = missionType,
        unlockMinutes = unlockMinutes,
        enabled = enabled,
    )

private fun PredictionResultEntity.toPredictionOutcome(): PredictionOutcome =
    PredictionOutcome(
        source = PredictionSource.CACHE,
        predictedNextDayMinutes = predictedNextDayMinutes,
        goalExceedanceRiskPercent = null,
        goalRiskLabel = riskLevel,
        primaryFactors = listOf(mainReason).filter { it.isNotBlank() },
        modelVersion = "cached-result",
        calculatedAtMillis = createdAt,
        dataQualityStatus = "UNKNOWN_ERROR",
        userMessage = "Showing the most recent stored prediction because a fresh model result is not available.",
        recommendationText = "Review the cached result before approving any rule.",
    )

private fun PredictionSource.displayLabel(): String =
    when (this) {
        PredictionSource.REMOTE_MODEL -> "Remote model"
        PredictionSource.LOCAL_MODEL -> "Local model"
        PredictionSource.BASELINE -> "Baseline"
        PredictionSource.CACHE -> "Cache"
        PredictionSource.COLLECTING_DATA -> "Collecting data"
    }

private fun PredictionOutcome.displayPredictionText(): String =
    predictedNextDayMinutes?.let { "약 ${it}분" } ?: userMessage

private fun PredictionOutcome.goalRiskText(): String =
    goalExceedanceRiskPercent?.let { "${goalRiskLabel} (${it}%)" } ?: goalRiskLabel

private fun formatPredictionTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(millis))
