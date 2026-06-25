package com.habitguard.app.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrainedLocalPredictionModelTest {
    @Test
    fun kotlinInferenceMatchesPythonValidationFixture() {
        val bundle = ModelBundleLoader.parse(assetBundleJson())
        val fixture = bundle.validationFixture
        val prediction = TrainedLocalPredictionModel(bundle).predictRawFeatures(fixture.rawFeatures)

        assertEquals(fixture.regressionPrediction, prediction.predictedMinutes, 0.1)
        assertEquals(fixture.classificationProbability, prediction.overGoalProbability, 0.001)
        assertEquals(fixture.classificationLabel, prediction.goalRiskLabel)
    }

    @Test
    fun featureOrderMismatchFailsBundleValidation() {
        val root = JSONObject(assetBundleJson())
        val names = root.getJSONArray("feature_names")
        val first = names.getString(0)
        names.put(0, names.getString(1))
        names.put(1, first)

        assertThrowsIllegalArgument {
            ModelBundleLoader.parse(root.toString())
        }
    }

    @Test
    fun sourceTypeAndEvaluationScopeMismatchFailsBundleValidation() {
        val root = JSONObject(assetBundleJson())
        root.put("evaluation_scope", "real export evaluation")

        assertThrowsIllegalArgument {
            ModelBundleLoader.parse(root.toString())
        }
    }

    @Test
    fun missingRawFeatureUsesImputerValue() {
        val bundle = ModelBundleLoader.parse(assetBundleJson())
        val raw = bundle.validationFixture.rawFeatures.toMutableMap()
        raw.remove("notification_count")

        val prediction = TrainedLocalPredictionModel(bundle).predictRawFeatures(raw)

        assertTrue(prediction.predictedMinutes.isFinite())
        assertTrue(prediction.overGoalProbability in 0.0..1.0)
    }

    private fun assetBundleJson(): String =
        File("src/main/assets/android_inference_bundle.json").readText(Charsets.UTF_8)

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
