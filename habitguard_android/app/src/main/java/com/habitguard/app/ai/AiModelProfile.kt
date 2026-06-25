package com.habitguard.app.ai

import android.content.Context
import org.json.JSONObject

data class AiModelProfile(
    val profileVersion: Int,
    val sourceType: String,
    val dateCount: Int,
    val rowCount: Int,
    val sourceRows: Int,
    val regressionMaeMinutes: Double,
    val regressionRmseMinutes: Double,
    val classificationAccuracy: Double,
    val classificationF1Macro: Double,
    val regressionTopFeatures: List<String>,
    val classificationTopFeatures: List<String>,
    val caveat: String,
) {
    val sourceLabel: String
        get() = when (sourceType) {
            "likely_real_habitguard_export" -> "실측 HabitGuard export"
            "partial_or_external_phone_csv" -> "부분/외부 CSV"
            "likely_sample_or_test_data" -> "샘플/테스트 데이터"
            else -> sourceType
        }

    val summary: String
        get() = "$sourceLabel ${dateCount}일/${rowCount}행 학습"

    val performanceSummary: String
        get() = "회귀 MAE ${regressionMaeMinutes}분, 분류 F1 ${classificationF1Macro}"

    val topFeatureSummary: String
        get() {
            val regression = regressionTopFeatures.take(3).joinToString(", ")
            val classification = classificationTopFeatures.take(3).joinToString(", ")
            return "예측 주요 feature: $regression / 위험도 주요 feature: $classification"
        }
}

object AiModelProfileLoader {
    private const val ASSET_NAME = "habitguard_model_profile.json"

    fun load(context: Context): AiModelProfile? =
        runCatching {
            val json = context.assets.open(ASSET_NAME)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val root = JSONObject(json)
            AiModelProfile(
                profileVersion = root.optInt("profile_version", 1),
                sourceType = root.getString("source_type"),
                dateCount = root.getInt("date_count"),
                rowCount = root.getInt("row_count"),
                sourceRows = root.getInt("source_rows"),
                regressionMaeMinutes = root.getJSONObject("regression").getDouble("mae_minutes"),
                regressionRmseMinutes = root.getJSONObject("regression").getDouble("rmse_minutes"),
                classificationAccuracy = root.getJSONObject("classification").getDouble("accuracy"),
                classificationF1Macro = root.getJSONObject("classification").getDouble("f1_macro"),
                regressionTopFeatures = root.getJSONObject("regression").featureNames(),
                classificationTopFeatures = root.getJSONObject("classification").featureNames(),
                caveat = root.getString("caveat"),
            )
        }.getOrNull()

    private fun JSONObject.featureNames(): List<String> {
        val features = getJSONArray("top_features")
        return (0 until features.length()).map { index ->
            features.getJSONObject(index).getString("feature")
        }
    }
}
