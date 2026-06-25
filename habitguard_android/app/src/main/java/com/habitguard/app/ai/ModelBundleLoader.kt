package com.habitguard.app.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

data class AndroidInferenceBundle(
    val schemaVersion: Int,
    val modelVersion: String,
    val trainedAt: String,
    val sourceType: String,
    val evaluationScope: String,
    val numericFeatures: List<String>,
    val categoricalFeatures: List<String>,
    val categoryValues: Map<String, List<String>>,
    val featureNames: List<String>,
    val missingValueStrategy: String,
    val imputerValues: Map<String, Any>,
    val scalerMean: List<Double>,
    val scalerScale: List<Double>,
    val regressionModel: LinearRegressionBundle,
    val classificationModel: LogisticRegressionBundle,
    val trainingManifestHash: String,
    val validationFixture: AndroidInferenceValidationFixture,
)

data class LinearRegressionBundle(
    val intercept: Double,
    val coefficients: List<Double>,
)

data class LogisticRegressionBundle(
    val intercept: Double,
    val coefficients: List<Double>,
    val classes: List<String>,
    val positiveClass: String,
)

data class AndroidInferenceValidationFixture(
    val featureNames: List<String>,
    val rawFeatures: Map<String, Any>,
    val transformedFeatures: List<Double>,
    val regressionPrediction: Double,
    val classificationProbability: Double,
    val classificationLabel: String,
)

data class TrainedLocalPrediction(
    val predictedMinutes: Double,
    val roundedMinutes: Long,
    val overGoalProbability: Double,
    val goalRiskLabel: String,
    val topFactors: List<String>,
)

object ModelBundleLoader {
    private const val ASSET_NAME = "android_inference_bundle.json"
    private val targetLeakageTokens = setOf(
        "target_next_day_minutes",
        "target_goal_exceeded",
        "goal_risk_label",
        "user_type_label",
    )

    fun load(context: Context): AndroidInferenceBundle? =
        runCatching {
            val json = context.assets.open(ASSET_NAME)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parse(json)
        }.getOrNull()

    fun parse(json: String): AndroidInferenceBundle {
        val root = JSONObject(json)
        val numericFeatures = root.getStringArray("numeric_features")
        val categoricalFeatures = root.getStringArray("categorical_features")
        val categoryValues = root.getJSONObject("category_values").toStringListMap()
        val featureNames = root.getStringArray("feature_names")
        val imputerValues = root.getJSONObject("imputer_values").toValueMap()
        val scalerMean = root.getDoubleArray("scaler_mean")
        val scalerScale = root.getDoubleArray("scaler_scale")
        val regression = root.getJSONObject("regression_model")
        val classification = root.getJSONObject("classification_model")
        val fixture = root.getJSONObject("validation_fixture")

        val bundle = AndroidInferenceBundle(
            schemaVersion = root.getInt("schema_version"),
            modelVersion = root.getString("model_version"),
            trainedAt = root.getString("trained_at"),
            sourceType = root.getString("source_type"),
            evaluationScope = root.getString("evaluation_scope"),
            numericFeatures = numericFeatures,
            categoricalFeatures = categoricalFeatures,
            categoryValues = categoryValues,
            featureNames = featureNames,
            missingValueStrategy = root.getString("missing_value_strategy"),
            imputerValues = imputerValues,
            scalerMean = scalerMean,
            scalerScale = scalerScale,
            regressionModel = LinearRegressionBundle(
                intercept = regression.getDouble("intercept"),
                coefficients = regression.getDoubleArray("coefficients"),
            ),
            classificationModel = LogisticRegressionBundle(
                intercept = classification.getDouble("intercept"),
                coefficients = classification.getDoubleArray("coefficients"),
                classes = classification.getStringArray("classes"),
                positiveClass = classification.getString("positive_class"),
            ),
            trainingManifestHash = root.getString("training_manifest_hash"),
            validationFixture = AndroidInferenceValidationFixture(
                featureNames = fixture.getStringArray("feature_names"),
                rawFeatures = fixture.getJSONObject("raw_features").toValueMap(),
                transformedFeatures = fixture.getDoubleArray("transformed_features"),
                regressionPrediction = fixture.getDouble("regression_prediction"),
                classificationProbability = fixture.getDouble("classification_probability"),
                classificationLabel = fixture.getString("classification_label"),
            ),
        )
        bundle.validate()
        return bundle
    }

    private fun AndroidInferenceBundle.validate() {
        require(schemaVersion == 1) { "Unsupported Android inference bundle schema: $schemaVersion" }
        require(
            (sourceType == "synthetic" && evaluationScope == "synthetic evaluation") ||
                (sourceType == "real" && evaluationScope == "real export evaluation"),
        ) { "source_type and evaluation_scope mismatch: $sourceType / $evaluationScope" }
        require(missingValueStrategy == "numeric_mean_categorical_first_seen") {
            "Unsupported missing value strategy: $missingValueStrategy"
        }
        require(scalerMean.size == numericFeatures.size) { "scaler_mean length does not match numeric_features" }
        require(scalerScale.size == numericFeatures.size) { "scaler_scale length does not match numeric_features" }
        require(regressionModel.coefficients.size == featureNames.size) { "Regression coefficient length mismatch" }
        require(classificationModel.coefficients.size == featureNames.size) { "Classification coefficient length mismatch" }
        require(classificationModel.positiveClass in classificationModel.classes) { "Missing positive class" }
        require(targetLeakageTokens.none { token -> featureNames.any { it == token || it.startsWith("${token}_") } }) {
            "Target leakage feature found in Android bundle"
        }
        val expectedFeatureNames = numericFeatures + categoricalFeatures.flatMap { feature ->
            categoryValues.getValue(feature).map { value -> "${feature}_$value" }
        }
        require(featureNames == expectedFeatureNames) { "Feature order does not match preprocessing schema" }
        require(validationFixture.featureNames == featureNames) { "Validation fixture feature order mismatch" }
    }
}

class TrainedLocalPredictionModel(
    private val bundle: AndroidInferenceBundle,
) : LocalPredictionModel {
    override fun predict(
        input: PredictionInput,
        completeDailySummaries: List<com.habitguard.app.model.DailyUsageSummary>,
        dataQualityStatus: String,
    ): PredictionOutcome {
        val rawFeatures = buildRawFeatures(input, completeDailySummaries)
        val prediction = predictRawFeatures(rawFeatures)
        return PredictionOutcome(
            source = PredictionSource.LOCAL_MODEL,
            predictedNextDayMinutes = prediction.roundedMinutes,
            goalExceedanceRiskPercent = (prediction.overGoalProbability * 100).roundToInt().coerceIn(0, 100),
            goalRiskLabel = prediction.goalRiskLabel,
            primaryFactors = prediction.topFactors,
            modelVersion = bundle.modelVersion,
            calculatedAtMillis = input.calculatedAtMillis,
            dataQualityStatus = dataQualityStatus,
            userMessage = syntheticCaveat(bundle),
            recommendationText = recommendationForLocalModel(prediction.roundedMinutes, input.activeGoalLimitMinutes),
            sourceType = bundle.sourceType,
            evaluationScope = bundle.evaluationScope,
        )
    }

    fun predictRawFeatures(rawFeatures: Map<String, Any?>): TrainedLocalPrediction {
        val transformed = transform(rawFeatures)
        val predicted = dot(bundle.regressionModel.intercept, bundle.regressionModel.coefficients, transformed)
        val probability = sigmoid(dot(bundle.classificationModel.intercept, bundle.classificationModel.coefficients, transformed))
        val label = if (probability >= 0.5) {
            bundle.classificationModel.positiveClass
        } else {
            bundle.classificationModel.classes.first { it != bundle.classificationModel.positiveClass }
        }
        return TrainedLocalPrediction(
            predictedMinutes = predicted,
            roundedMinutes = roundToNearest10(predicted.coerceAtLeast(0.0)),
            overGoalProbability = probability,
            goalRiskLabel = label,
            topFactors = topFactors(),
        )
    }

    private fun transform(rawFeatures: Map<String, Any?>): List<Double> {
        val transformed = mutableListOf<Double>()
        bundle.numericFeatures.forEachIndexed { index, feature ->
            val raw = rawFeatures[feature] ?: bundle.imputerValues[feature]
            val value = raw.asDoubleOrNull() ?: bundle.imputerValues[feature].asDoubleOrNull() ?: 0.0
            val scale = bundle.scalerScale[index].takeIf { it != 0.0 } ?: 1.0
            transformed += (value - bundle.scalerMean[index]) / scale
        }
        bundle.categoricalFeatures.forEach { feature ->
            val raw = rawFeatures[feature] ?: bundle.imputerValues[feature]
            val value = raw?.toString().orEmpty()
            bundle.categoryValues.getValue(feature).forEach { category ->
                transformed += if (value == category) 1.0 else 0.0
            }
        }
        require(transformed.size == bundle.featureNames.size) { "Transformed feature length mismatch" }
        return transformed
    }

    private fun buildRawFeatures(
        input: PredictionInput,
        completeDailySummaries: List<com.habitguard.app.model.DailyUsageSummary>,
    ): Map<String, Any> {
        val currentTotal = input.todayUsage.sumOf { it.minutes }.takeIf { it > 0 }
            ?: completeDailySummaries.lastOrNull()?.totalMinutes
            ?: 0L
        val currentNight = input.todayUsage.sumOf { it.nightMinutes }.takeIf { it > 0 }
            ?: completeDailySummaries.lastOrNull()?.nightMinutes
            ?: 0L
        val openCount = input.todayUsage.sumOf { it.openCount }
        val topAppMinutes = input.todayUsage.maxOfOrNull { it.minutes }
            ?: completeDailySummaries.lastOrNull()?.topAppMinutes
            ?: 0L
        val categoryMinutes = input.todayUsage
            .groupBy { normalizeCategoryForModel(it.category) }
            .mapValues { (_, rows) -> rows.sumOf { it.minutes }.toDouble() }
        val previousDayTotal = completeDailySummaries.lastOrNull()?.totalMinutes?.toDouble() ?: currentTotal.toDouble()
        val weekday = java.time.LocalDate.now().dayOfWeek.value % 7
        return mutableMapOf<String, Any>(
            "total_minutes" to currentTotal.toDouble(),
            "night_minutes" to currentNight.toDouble(),
            "open_count" to openCount.toDouble(),
            "notification_count" to input.notificationCount.toDouble(),
            "app_count" to input.todayUsage.map { it.packageName }.distinct().size.toDouble(),
            "top_app_minutes" to topAppMinutes.toDouble(),
            "avg_session_minutes_proxy" to if (openCount > 0) currentTotal.toDouble() / openCount else 0.0,
            "night_ratio" to if (currentTotal > 0) currentNight.toDouble() / currentTotal else 0.0,
            "notification_per_open" to if (openCount > 0) input.notificationCount.toDouble() / openCount else 0.0,
            "rolling_3d_total" to averageTotal(completeDailySummaries.takeLast(3)),
            "rolling_7d_total" to averageTotal(completeDailySummaries.takeLast(7)),
            "previous_day_total" to previousDayTotal,
            "personal_goal_minutes" to (input.activeGoalLimitMinutes ?: 240).toDouble(),
            "weekday" to weekday.toString(),
            "is_weekend" to (weekday >= 5).toString().replaceFirstChar { it.uppercase() },
        ).apply {
            listOf("video", "sns", "game", "browser", "productivity", "other").forEach { category ->
                this[category] = categoryMinutes[category] ?: 0.0
            }
        }
    }

    private fun topFactors(): List<String> =
        bundle.featureNames
            .mapIndexed { index, feature ->
                feature to (abs(bundle.regressionModel.coefficients[index]) + abs(bundle.classificationModel.coefficients[index]))
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(4)
}

private fun JSONObject.getStringArray(name: String): List<String> =
    getJSONArray(name).toStringList()

private fun JSONObject.getDoubleArray(name: String): List<Double> =
    getJSONArray(name).toDoubleList()

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).map { index -> getString(index) }

private fun JSONArray.toDoubleList(): List<Double> =
    (0 until length()).map { index -> getDouble(index) }

private fun JSONObject.toStringListMap(): Map<String, List<String>> =
    keys().asSequence().associateWith { key -> getJSONArray(key).toStringList() }

private fun JSONObject.toValueMap(): Map<String, Any> =
    keys().asSequence().associateWith { key -> get(key) }

private fun Any?.asDoubleOrNull(): Double? =
    when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }

private fun dot(intercept: Double, coefficients: List<Double>, features: List<Double>): Double {
    require(coefficients.size == features.size) { "Coefficient and feature length mismatch" }
    return coefficients.zip(features).fold(intercept) { total, (coefficient, feature) -> total + coefficient * feature }
}

private fun sigmoid(value: Double): Double =
    when {
        value >= 0 -> 1.0 / (1.0 + exp(-value))
        else -> {
            val z = exp(value)
            z / (1.0 + z)
        }
    }

private fun averageTotal(days: List<com.habitguard.app.model.DailyUsageSummary>): Double =
    if (days.isEmpty()) 0.0 else days.map { it.totalMinutes }.average()

private fun normalizeCategoryForModel(value: String): String {
    val text = value.trim().lowercase()
    return when {
        listOf("video", "youtube", "netflix", "tiktok", "영상").any { it in text } -> "video"
        listOf("sns", "social", "instagram", "kakao", "facebook", "whatsapp").any { it in text } -> "sns"
        listOf("game", "게임").any { it in text } -> "game"
        listOf("browser", "chrome", "브라우저").any { it in text } -> "browser"
        listOf("productivity", "study", "education", "work", "생산성").any { it in text } -> "productivity"
        else -> "other"
    }
}

private fun roundToNearest10(value: Double): Long =
    ((value + 5.0) / 10.0).toLong() * 10L

private fun recommendationForLocalModel(predicted: Long, goal: Int?): String {
    if (goal == null || goal <= 0) return "예측값을 확인한 뒤 사용자가 직접 제한 규칙을 승인해야 합니다."
    val gap = predicted - goal
    return if (gap > 0) {
        "추천 기준: 목표보다 약 ${roundToNearest10(abs(gap).toDouble())}분 높습니다. 제한 규칙은 자동 적용되지 않고 사용자 승인 후에만 저장됩니다."
    } else {
        "현재 목표는 도달 가능 범위입니다. 제한 규칙은 선택 사항이며 사용자 승인 후에만 저장됩니다."
    }
}

private fun syntheticCaveat(bundle: AndroidInferenceBundle): String =
    if (bundle.sourceType == "synthetic") {
        "현재 예측은 합성 시나리오로 학습된 초기 모델입니다. 실제 사용 기록이 쌓이면 개인 데이터 기반으로 다시 검증합니다."
    } else {
        "로컬 학습 모델이 개인 기록 요약 feature로 계산한 결과입니다."
    }
