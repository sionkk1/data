import json
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path

import pandas as pd

from ai import train_from_phone_csv as pipeline


class TrainFromPhoneCsvTests(unittest.TestCase):
    def test_sample_generation_is_deterministic_and_marks_synthetic_source(self):
        first = pipeline.generate_sample_rows()
        second = pipeline.generate_sample_rows()

        self.assertGreaterEqual(len(first), 600)
        pd.testing.assert_frame_equal(first, second)
        self.assertTrue(pipeline.REQUIRED_COLUMNS.issubset(first.columns))
        self.assertEqual(set(first["source_type"]), {"synthetic"})

    def test_daily_features_use_next_day_targets_without_feature_leakage(self):
        rows = pipeline.generate_sample_rows()
        daily = pipeline.build_daily_features(rows, source_type="synthetic")
        feature_schema = pipeline.build_feature_schema(daily)
        all_features = set(feature_schema["numeric_features"] + feature_schema["categorical_features"])

        self.assertIn("target_next_day_minutes", daily.columns)
        self.assertIn("target_goal_exceeded", daily.columns)
        self.assertIn("user_type_label", daily.columns)
        self.assertNotIn("target_next_day_minutes", all_features)
        self.assertNotIn("target_goal_exceeded", all_features)
        self.assertNotIn("goal_risk_label", all_features)
        self.assertNotIn("user_type_label", all_features)

    def test_split_is_user_grouped_and_temporal_not_random_rows(self):
        daily = pipeline.build_daily_features(pipeline.generate_sample_rows(), source_type="synthetic")
        split = pipeline.split_by_user_and_time(daily)

        self.assertEqual(set(split), {"train", "validation", "test", "all_splits"})
        for user_id, group in daily.groupby("user_id"):
            ordered = group.sort_values("date")
            split_by_date = split["all_splits"].query("user_id == @user_id").sort_values("date")
            split_names = split_by_date["split"].tolist()
            self.assertEqual(list(ordered["date"]), list(split_by_date["date"]))
            self.assertLess(split_names.index("validation"), split_names.index("test"))
            self.assertTrue(all(name in {"train", "validation", "test"} for name in split_names))
            max_train = split["train"].query("user_id == @user_id")["date"].max()
            min_validation = split["validation"].query("user_id == @user_id")["date"].min()
            min_test = split["test"].query("user_id == @user_id")["date"].min()
            self.assertLess(max_train, min_validation)
            self.assertLess(min_validation, min_test)

    def test_missing_required_csv_columns_raise_clear_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            csv_path = Path(tmp) / "bad.csv"
            csv_path.write_text("date,package_name\n2026-01-01,com.example\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "missing required columns"):
                pipeline.load_phone_csv(csv_path, Path(tmp))

    def test_pipeline_outputs_required_artifacts_and_marks_synthetic_evaluation(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp) / "outputs"
            with redirect_stdout(StringIO()):
                exit_code = pipeline.main(["--output-dir", str(output_dir)])

            self.assertEqual(exit_code, 0)
            required = {
                "feature_schema.json",
                "preprocessing.joblib",
                "screen_time_regressor.joblib",
                "goal_risk_classifier.joblib",
                "user_type_classifier.joblib",
                "regression_metrics.json",
                "classification_metrics.json",
                "model_card.md",
                "training_manifest.json",
                "data_snapshot_hash.txt",
                "actual_vs_predicted.png",
                "feature_importance.png",
                "confusion_matrix.png",
                "model_comparison.csv",
            }
            self.assertTrue(required.issubset({path.name for path in output_dir.iterdir()}))
            self.assertTrue((output_dir / "poster_assets").is_dir())

            manifest = json.loads((output_dir / "training_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["source_type"], "synthetic")
            self.assertEqual(manifest["evaluation_scope"], "synthetic evaluation")
            self.assertFalse(manifest["used_random_row_split"])
            self.assertIn("data_snapshot_hash", manifest)
            self.assertIn("baseline_improvement_pct", manifest["regression"])
            regression_metrics = json.loads((output_dir / "regression_metrics.json").read_text(encoding="utf-8"))
            classification_metrics = json.loads((output_dir / "classification_metrics.json").read_text(encoding="utf-8"))
            self.assertIn("mae_minutes", regression_metrics)
            self.assertIn("rmse_minutes", regression_metrics)
            self.assertIn("top_features", regression_metrics)
            self.assertIn("f1_macro", classification_metrics)
            self.assertIn("high_risk_recall", classification_metrics)
            self.assertIn("top_features", classification_metrics)

    def test_android_inference_bundle_exports_coefficients_and_validation_fixture(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp) / "outputs"
            with redirect_stdout(StringIO()):
                exit_code = pipeline.main(["--output-dir", str(output_dir)])

            self.assertEqual(exit_code, 0)
            bundle_path = output_dir / "android_inference_bundle.json"
            self.assertTrue(bundle_path.exists())
            bundle = json.loads(bundle_path.read_text(encoding="utf-8"))

            required = {
                "schema_version",
                "model_version",
                "trained_at",
                "source_type",
                "evaluation_scope",
                "feature_names",
                "missing_value_strategy",
                "imputer_values",
                "scaler_mean",
                "scaler_scale",
                "regression_model",
                "classification_model",
                "training_manifest_hash",
                "validation_fixture",
            }
            self.assertTrue(required.issubset(bundle))
            self.assertEqual(bundle["source_type"], "synthetic")
            self.assertEqual(bundle["evaluation_scope"], "synthetic evaluation")
            self.assertEqual(bundle["regression_model"]["name"], "linear_regression")
            self.assertEqual(bundle["classification_model"]["name"], "logistic_regression")
            self.assertEqual(bundle["classification_model"]["positive_class"], "over_goal")
            self.assertEqual(len(bundle["feature_names"]), len(bundle["regression_model"]["coefficients"]))
            self.assertEqual(len(bundle["feature_names"]), len(bundle["classification_model"]["coefficients"]))

            forbidden = set(bundle["feature_names"]) & pipeline.TARGET_COLUMNS
            self.assertEqual(forbidden, set())
            self.assertEqual(bundle["feature_names"], bundle["validation_fixture"]["feature_names"])
            self.assertIn("regression_prediction", bundle["validation_fixture"])
            self.assertIn("classification_probability", bundle["validation_fixture"])


if __name__ == "__main__":
    unittest.main()
