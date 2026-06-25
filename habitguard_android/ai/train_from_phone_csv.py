#!/usr/bin/env python3
"""
Train HabitGuard screen-timing models from a HabitGuard CSV export.

The pipeline keeps synthetic and real data explicitly separated, uses
user-aware chronological splits, compares required baselines, and writes a
manifest/model card that states whether metrics are synthetic evaluation only.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Iterable

import joblib
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import (
    GradientBoostingClassifier,
    GradientBoostingRegressor,
    RandomForestClassifier,
    RandomForestRegressor,
)
from sklearn.linear_model import LinearRegression, LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    mean_absolute_error,
    precision_score,
    r2_score,
    recall_score,
    root_mean_squared_error,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.tree import DecisionTreeClassifier


REQUIRED_COLUMNS = {
    "date",
    "package_name",
    "app_name",
    "category",
    "usage_minutes",
    "night_minutes",
    "open_count",
    "notification_count",
}

CATEGORY_COLUMNS = ["video", "sns", "game", "browser", "productivity", "other"]
GOAL_RISK_LABELS = ["within_goal", "over_goal"]
USER_TYPE_LABELS = ["balanced", "night_heavy", "social_loop", "game_heavy", "productivity_focused"]
TARGET_COLUMNS = {
    "target_next_day_minutes",
    "target_goal_exceeded",
    "goal_risk_label",
    "user_type_label",
}


@dataclass(frozen=True)
class ModelResult:
    task: str
    name: str
    estimator: Pipeline | None
    prediction: np.ndarray
    metrics: dict[str, float | str | bool]


def normalize_category(value: object) -> str:
    text = str(value).strip().lower()
    if any(token in text for token in ["video", "youtube", "netflix", "tiktok", "\uc601\uc0c1"]):
        return "video"
    if any(token in text for token in ["sns", "social", "instagram", "kakao", "facebook", "whatsapp"]):
        return "sns"
    if any(token in text for token in ["game", "\uac8c\uc784"]):
        return "game"
    if any(token in text for token in ["browser", "chrome", "\ube0c\ub77c\uc6b0\uc800"]):
        return "browser"
    if any(token in text for token in ["productivity", "study", "education", "work", "\uc0dd\uc0b0\uc131"]):
        return "productivity"
    return "other"


def classify_user_type(row: pd.Series) -> str:
    if row["night_ratio"] >= 0.28:
        return "night_heavy"
    if row["sns"] >= max(row["video"], row["game"], row["productivity"], row["browser"], row["other"]):
        return "social_loop"
    if row["game"] >= max(row["video"], row["sns"], row["productivity"], row["browser"], row["other"]):
        return "game_heavy"
    if row["productivity"] >= max(row["video"], row["sns"], row["game"], row["browser"], row["other"]):
        return "productivity_focused"
    return "balanced"


def generate_sample_rows() -> pd.DataFrame:
    rng = np.random.default_rng(42)
    dates = pd.date_range("2026-01-01", periods=120, freq="D")
    users = [
        ("synthetic_user_001", 330, 1.0, 1.0),
        ("synthetic_user_002", 285, 0.8, 1.35),
        ("synthetic_user_003", 400, 1.28, 0.82),
    ]
    app_specs = [
        ("com.google.android.youtube", "YouTube", "video", 75),
        ("com.instagram.android", "Instagram", "sns", 55),
        ("com.kakao.talk", "KakaoTalk", "sns", 38),
        ("com.android.chrome", "Chrome", "browser", 32),
        ("com.notion.id", "Notion", "productivity", 22),
        ("com.example.game", "Puzzle Game", "game", 45),
        ("com.google.android.gm", "Gmail", "productivity", 18),
    ]
    rows: list[dict[str, object]] = []
    for user_id, goal_minutes, leisure_factor, productivity_factor in users:
        for date_index, date in enumerate(dates):
            weekday = date.weekday()
            weekend_boost = 1.22 if weekday >= 5 else 1.0
            exam_week_factor = 0.74 if 45 <= date_index <= 58 else 1.0
            for package_name, app_name, category, base_minutes in app_specs:
                category_factor = {
                    "video": 1.35 if weekday >= 4 else 1.0,
                    "sns": 1.15,
                    "game": 1.45 if weekday >= 5 else 0.85,
                    "browser": 1.0,
                    "productivity": 1.35 if weekday < 5 else 0.65,
                }[category]
                habit_factor = productivity_factor if category == "productivity" else leisure_factor
                noise = rng.normal(0, max(5, base_minutes * 0.16))
                usage = max(0, base_minutes * weekend_boost * exam_week_factor * category_factor * habit_factor + noise)
                night_ratio = {
                    "video": 0.32,
                    "sns": 0.24,
                    "game": 0.18,
                    "browser": 0.12,
                    "productivity": 0.04,
                }[category]
                night = max(0, usage * night_ratio + rng.normal(0, 4))
                open_count = max(1, int(usage / rng.uniform(5, 12) + rng.normal(2, 2)))
                notifications = max(0, int(open_count * {"sns": 2.2, "productivity": 1.4}.get(category, 0.45) + rng.normal(0, 3)))
                first_open = int(pd.Timestamp(date).timestamp() * 1000) + int(rng.integers(7, 23)) * 3_600_000
                rows.append(
                    {
                        "user_id": user_id,
                        "source_type": "synthetic",
                        "date": date.date().isoformat(),
                        "package_name": package_name,
                        "app_name": app_name,
                        "category": category,
                        "usage_minutes": round(float(usage), 2),
                        "night_minutes": round(float(night), 2),
                        "open_count": open_count,
                        "notification_count": notifications,
                        "personal_goal_minutes": goal_minutes,
                        "first_open_time": first_open,
                        "last_time_used": first_open + int(usage * 60_000),
                        "captured_at": first_open + 24 * 3_600_000,
                    }
                )
    return pd.DataFrame(rows)


def load_phone_csv(input_csv: Path | None, output_dir: Path) -> tuple[pd.DataFrame, str]:
    if input_csv is None:
        sample = generate_sample_rows()
        output_dir.mkdir(parents=True, exist_ok=True)
        sample.to_csv(output_dir / "sample_habitguard_phone_usage.csv", index=False)
        return sample, "synthetic"

    df = pd.read_csv(input_csv)
    missing = REQUIRED_COLUMNS - set(df.columns)
    if missing:
        raise ValueError(f"CSV is missing required columns: {sorted(missing)}")

    if "source_type" in df.columns:
        source_values = set(df["source_type"].dropna().astype(str).str.lower())
        if len(source_values) > 1:
            raise ValueError(f"CSV mixes source_type values: {sorted(source_values)}")
        source_type = next(iter(source_values), "real")
        if source_type not in {"real", "synthetic"}:
            raise ValueError(f"Unsupported source_type: {source_type}")
    else:
        source_type = "real"
        df["source_type"] = source_type
    if "user_id" not in df.columns:
        df["user_id"] = "local-device"
    if "personal_goal_minutes" not in df.columns:
        df["personal_goal_minutes"] = 240
    return df, source_type


def build_daily_features(rows: pd.DataFrame, source_type: str) -> pd.DataFrame:
    df = rows.copy()
    df["date"] = pd.to_datetime(df["date"])
    df["source_type"] = df.get("source_type", source_type)
    if set(df["source_type"].dropna().astype(str).str.lower()) - {source_type}:
        raise ValueError("Input rows contain a source_type that does not match the declared source_type")
    df["user_id"] = df.get("user_id", "local-device").fillna("local-device")
    df["category_norm"] = df["category"].map(normalize_category)
    df["personal_goal_minutes"] = pd.to_numeric(df.get("personal_goal_minutes", 240), errors="coerce").fillna(240)
    for column in ["usage_minutes", "night_minutes", "open_count", "notification_count"]:
        df[column] = pd.to_numeric(df[column], errors="coerce").fillna(0)

    category_minutes = (
        df.pivot_table(
            index=["user_id", "date"],
            columns="category_norm",
            values="usage_minutes",
            aggfunc="sum",
            fill_value=0,
        )
        .reindex(columns=CATEGORY_COLUMNS, fill_value=0)
        .reset_index()
    )
    daily = (
        df.groupby(["user_id", "date"], as_index=False)
        .agg(
            source_type=("source_type", "first"),
            personal_goal_minutes=("personal_goal_minutes", "median"),
            total_minutes=("usage_minutes", "sum"),
            night_minutes=("night_minutes", "sum"),
            open_count=("open_count", "sum"),
            notification_count=("notification_count", "sum"),
            app_count=("package_name", "nunique"),
            top_app_minutes=("usage_minutes", "max"),
        )
        .merge(category_minutes, on=["user_id", "date"], how="left")
        .sort_values(["user_id", "date"])
    )
    daily["weekday"] = daily["date"].dt.weekday.astype(str)
    daily["is_weekend"] = (daily["date"].dt.weekday >= 5).astype(str)
    daily["avg_session_minutes_proxy"] = (daily["total_minutes"] / daily["open_count"].replace(0, np.nan)).fillna(0)
    daily["night_ratio"] = (daily["night_minutes"] / daily["total_minutes"].replace(0, np.nan)).fillna(0)
    daily["notification_per_open"] = (daily["notification_count"] / daily["open_count"].replace(0, np.nan)).fillna(0)
    grouped = daily.groupby("user_id", group_keys=False)
    daily["previous_day_total"] = grouped["total_minutes"].shift(1)
    daily["rolling_7d_total"] = grouped["total_minutes"].shift(1).rolling(7, min_periods=1).mean().reset_index(level=0, drop=True)
    daily["rolling_3d_total"] = grouped["total_minutes"].shift(1).rolling(3, min_periods=1).mean().reset_index(level=0, drop=True)
    daily["previous_day_total"] = daily["previous_day_total"].fillna(daily["total_minutes"])
    daily["rolling_7d_total"] = daily["rolling_7d_total"].fillna(daily["previous_day_total"])
    daily["rolling_3d_total"] = daily["rolling_3d_total"].fillna(daily["previous_day_total"])
    daily["target_next_day_minutes"] = grouped["total_minutes"].shift(-1)
    daily["next_day_goal_minutes"] = grouped["personal_goal_minutes"].shift(-1)
    daily["target_goal_exceeded"] = daily["target_next_day_minutes"] > daily["next_day_goal_minutes"]
    daily["goal_risk_label"] = np.where(daily["target_goal_exceeded"], "over_goal", "within_goal")
    daily["user_type_label"] = daily.apply(classify_user_type, axis=1)
    return daily.dropna(subset=["target_next_day_minutes"]).reset_index(drop=True)


def build_feature_schema(daily: pd.DataFrame) -> dict[str, object]:
    numeric = [
        "total_minutes",
        "night_minutes",
        "open_count",
        "notification_count",
        "app_count",
        "top_app_minutes",
        "avg_session_minutes_proxy",
        "night_ratio",
        "notification_per_open",
        "rolling_3d_total",
        "rolling_7d_total",
        "previous_day_total",
        "personal_goal_minutes",
        *CATEGORY_COLUMNS,
    ]
    categorical = ["weekday", "is_weekend"]
    leakage = set(numeric + categorical) & TARGET_COLUMNS
    if leakage:
        raise ValueError(f"Feature schema includes target/leakage columns: {sorted(leakage)}")
    return {
        "numeric_features": [column for column in numeric if column in daily.columns],
        "categorical_features": categorical,
        "excluded_columns": sorted(TARGET_COLUMNS | {"date", "user_id", "source_type", "next_day_goal_minutes"}),
        "target_columns": sorted(TARGET_COLUMNS),
    }


def split_by_user_and_time(daily: pd.DataFrame) -> dict[str, pd.DataFrame]:
    frames: list[pd.DataFrame] = []
    for _, user_frame in daily.sort_values(["user_id", "date"]).groupby("user_id", sort=False):
        n = len(user_frame)
        if n < 15:
            raise ValueError(f"Need at least 15 daily rows per user for temporal split; got {n}")
        train_end = max(1, int(n * 0.6))
        validation_end = max(train_end + 1, int(n * 0.8))
        validation_end = min(validation_end, n - 1)
        annotated = user_frame.copy()
        annotated["split"] = "train"
        annotated.iloc[train_end:validation_end, annotated.columns.get_loc("split")] = "validation"
        annotated.iloc[validation_end:, annotated.columns.get_loc("split")] = "test"
        frames.append(annotated)
    all_splits = pd.concat(frames, ignore_index=True)
    return {
        "train": all_splits[all_splits["split"] == "train"].copy(),
        "validation": all_splits[all_splits["split"] == "validation"].copy(),
        "test": all_splits[all_splits["split"] == "test"].copy(),
        "all_splits": all_splits,
    }


def make_preprocessor(schema: dict[str, object]) -> ColumnTransformer:
    return ColumnTransformer(
        transformers=[
            ("numeric", StandardScaler(), schema["numeric_features"]),
            ("categorical", OneHotEncoder(handle_unknown="ignore"), schema["categorical_features"]),
        ]
    )


def x_y(frame: pd.DataFrame, schema: dict[str, object], target: str) -> tuple[pd.DataFrame, pd.Series]:
    features = frame[schema["numeric_features"] + schema["categorical_features"]]
    return features, frame[target]


def regression_model_defs(schema: dict[str, object]) -> dict[str, Pipeline]:
    return {
        "linear_regression": Pipeline([("preprocess", make_preprocessor(schema)), ("model", LinearRegression())]),
        "random_forest": Pipeline(
            [
                ("preprocess", make_preprocessor(schema)),
                ("model", RandomForestRegressor(n_estimators=180, min_samples_leaf=2, random_state=42)),
            ]
        ),
        "gradient_boosting": Pipeline(
            [
                ("preprocess", make_preprocessor(schema)),
                ("model", GradientBoostingRegressor(random_state=42)),
            ]
        ),
    }


def classification_model_defs(schema: dict[str, object]) -> dict[str, Pipeline]:
    return {
        "logistic_regression": Pipeline(
            [
                ("preprocess", make_preprocessor(schema)),
                ("model", LogisticRegression(max_iter=1000, class_weight="balanced", random_state=42)),
            ]
        ),
        "decision_tree": Pipeline(
            [
                ("preprocess", make_preprocessor(schema)),
                ("model", DecisionTreeClassifier(max_depth=5, random_state=42, class_weight="balanced")),
            ]
        ),
        "random_forest": Pipeline(
            [
                ("preprocess", make_preprocessor(schema)),
                ("model", RandomForestClassifier(n_estimators=180, min_samples_leaf=2, class_weight="balanced", random_state=42)),
            ]
        ),
        "gradient_boosting": Pipeline(
            [
                ("preprocess", make_preprocessor(schema)),
                ("model", GradientBoostingClassifier(random_state=42)),
            ]
        ),
    }


def regression_metrics(y_true: pd.Series, prediction: np.ndarray, baseline_mae: float | None = None) -> dict[str, float | str | bool]:
    mae = float(mean_absolute_error(y_true, prediction))
    rmse = float(root_mean_squared_error(y_true, prediction))
    improvement = 0.0 if not baseline_mae else (baseline_mae - mae) / baseline_mae * 100
    return {
        "mae": round(mae, 4),
        "rmse": round(rmse, 4),
        "r2": round(float(r2_score(y_true, prediction)), 4),
        "baseline_improvement_pct": round(float(improvement), 4),
    }


def classification_metrics(y_true: pd.Series, prediction: np.ndarray, baseline_f1: float | None = None) -> dict[str, float | str | bool]:
    macro_f1 = float(f1_score(y_true, prediction, average="macro", zero_division=0))
    improvement = 0.0 if not baseline_f1 else (macro_f1 - baseline_f1) / max(baseline_f1, 1e-9) * 100
    labels = GOAL_RISK_LABELS
    recalls = recall_score(y_true, prediction, labels=labels, average=None, zero_division=0)
    return {
        "accuracy": round(float(accuracy_score(y_true, prediction)), 4),
        "macro_f1": round(macro_f1, 4),
        "precision_macro": round(float(precision_score(y_true, prediction, average="macro", zero_division=0)), 4),
        "recall_macro": round(float(recall_score(y_true, prediction, average="macro", zero_division=0)), 4),
        "high_risk_recall": round(float(recalls[labels.index("over_goal")]), 4),
        "baseline_improvement_pct": round(float(improvement), 4),
    }


def evaluate_regression_models(splits: dict[str, pd.DataFrame], schema: dict[str, object]) -> list[ModelResult]:
    train = pd.concat([splits["train"], splits["validation"]], ignore_index=True)
    test = splits["test"]
    y_test = test["target_next_day_minutes"]
    baseline_predictions = {
        "baseline_previous_day": test["previous_day_total"].to_numpy(),
        "baseline_rolling_7d": test["rolling_7d_total"].to_numpy(),
    }
    baseline_results = [
        ModelResult("regression", name, None, pred, regression_metrics(y_test, pred))
        for name, pred in baseline_predictions.items()
    ]
    best_baseline_mae = min(result.metrics["mae"] for result in baseline_results)
    x_train, y_train = x_y(train, schema, "target_next_day_minutes")
    x_test, _ = x_y(test, schema, "target_next_day_minutes")
    model_results: list[ModelResult] = []
    for name, estimator in regression_model_defs(schema).items():
        estimator.fit(x_train, y_train)
        prediction = estimator.predict(x_test)
        model_results.append(
            ModelResult("regression", name, estimator, prediction, regression_metrics(y_test, prediction, best_baseline_mae))
        )
    return baseline_results + model_results


def evaluate_classification_models(splits: dict[str, pd.DataFrame], schema: dict[str, object]) -> list[ModelResult]:
    train = pd.concat([splits["train"], splits["validation"]], ignore_index=True)
    test = splits["test"]
    y_test = test["goal_risk_label"]
    majority = train["goal_risk_label"].mode().iloc[0]
    baseline_prediction = np.array([majority] * len(test))
    baseline = ModelResult(
        "classification",
        "baseline_majority",
        None,
        baseline_prediction,
        classification_metrics(y_test, baseline_prediction),
    )
    baseline_f1 = float(baseline.metrics["macro_f1"])
    x_train, y_train = x_y(train, schema, "goal_risk_label")
    x_test, _ = x_y(test, schema, "goal_risk_label")
    results = [baseline]
    for name, estimator in classification_model_defs(schema).items():
        estimator.fit(x_train, y_train)
        prediction = estimator.predict(x_test)
        results.append(
            ModelResult("classification", name, estimator, prediction, classification_metrics(y_test, prediction, baseline_f1))
        )
    return results


def train_user_type_classifier(splits: dict[str, pd.DataFrame], schema: dict[str, object]) -> ModelResult:
    train = pd.concat([splits["train"], splits["validation"]], ignore_index=True)
    test = splits["test"]
    x_train, y_train = x_y(train, schema, "user_type_label")
    x_test, y_test = x_y(test, schema, "user_type_label")
    estimator = Pipeline(
        [
            ("preprocess", make_preprocessor(schema)),
            ("model", RandomForestClassifier(n_estimators=120, min_samples_leaf=2, class_weight="balanced", random_state=42)),
        ]
    )
    estimator.fit(x_train, y_train)
    prediction = estimator.predict(x_test)
    return ModelResult(
        "user_type",
        "random_forest_auxiliary",
        estimator,
        prediction,
        {
            "accuracy": round(float(accuracy_score(y_test, prediction)), 4),
            "macro_f1": round(float(f1_score(y_test, prediction, average="macro", zero_division=0)), 4),
        },
    )


def best_non_baseline(results: list[ModelResult], metric: str, maximize: bool) -> ModelResult:
    candidates = [result for result in results if result.estimator is not None]
    return (max if maximize else min)(candidates, key=lambda result: result.metrics[metric])


def best_baseline(results: list[ModelResult], metric: str, maximize: bool) -> ModelResult:
    candidates = [result for result in results if result.estimator is None]
    return (max if maximize else min)(candidates, key=lambda result: result.metrics[metric])


def transformed_feature_names(estimator: Pipeline) -> list[str]:
    preprocessor = estimator.named_steps["preprocess"]
    return [name.split("__", 1)[-1] for name in preprocessor.get_feature_names_out()]


def feature_importance_rows(estimator: Pipeline) -> pd.DataFrame:
    model = estimator.named_steps["model"]
    names = transformed_feature_names(estimator)
    if hasattr(model, "feature_importances_"):
        values = model.feature_importances_
    elif hasattr(model, "coef_"):
        coefficients = np.abs(np.asarray(model.coef_))
        values = coefficients if coefficients.ndim == 1 else coefficients.mean(axis=0)
    else:
        values = np.zeros(len(names))
    return (
        pd.DataFrame({"feature": names, "importance": values})
        .sort_values("importance", ascending=False)
        .head(15)
        .reset_index(drop=True)
    )


def selected_model(results: list[ModelResult], name: str) -> ModelResult:
    for result in results:
        if result.name == name and result.estimator is not None:
            return result
    raise ValueError(f"Required trained model is missing: {name}")


def assert_no_target_leakage(feature_names: list[str]) -> None:
    leaked = [
        feature
        for feature in feature_names
        if feature in TARGET_COLUMNS or any(feature.startswith(f"{target}_") for target in TARGET_COLUMNS)
    ]
    if leaked:
        raise ValueError(f"Android inference feature bundle includes target/leakage columns: {leaked}")


def _json_number_list(values: Iterable[object]) -> list[float]:
    return [float(value) for value in np.asarray(list(values), dtype=float).ravel()]


def _json_scalar(value: object) -> float:
    return float(np.asarray(value, dtype=float).ravel()[0])


def _dense_row(matrix: object) -> list[float]:
    if hasattr(matrix, "toarray"):
        matrix = matrix.toarray()
    return _json_number_list(np.asarray(matrix)[0])


def export_android_inference_bundle(
    path: Path,
    source_type: str,
    schema: dict[str, object],
    splits: dict[str, pd.DataFrame],
    regression_result: ModelResult,
    classification_result: ModelResult,
    training_manifest_hash: str,
) -> dict[str, object]:
    if regression_result.name != "linear_regression":
        raise ValueError(f"Android bundle requires linear_regression; got {regression_result.name}")
    if classification_result.name != "logistic_regression":
        raise ValueError(f"Android bundle requires logistic_regression; got {classification_result.name}")
    if regression_result.estimator is None or classification_result.estimator is None:
        raise ValueError("Android bundle requires fitted sklearn Pipeline estimators")

    regression_preprocessor = regression_result.estimator.named_steps["preprocess"]
    classification_preprocessor = classification_result.estimator.named_steps["preprocess"]
    feature_names = transformed_feature_names(regression_result.estimator)
    if feature_names != transformed_feature_names(classification_result.estimator):
        raise ValueError("Regression and classification transformed feature order differ")
    assert_no_target_leakage(feature_names)

    numeric_features = list(schema["numeric_features"])
    categorical_features = list(schema["categorical_features"])
    scaler = regression_preprocessor.named_transformers_["numeric"]
    encoder = regression_preprocessor.named_transformers_["categorical"]
    classification_scaler = classification_preprocessor.named_transformers_["numeric"]
    classification_encoder = classification_preprocessor.named_transformers_["categorical"]
    if not np.allclose(scaler.mean_, classification_scaler.mean_) or not np.allclose(scaler.scale_, classification_scaler.scale_):
        raise ValueError("Regression and classification scaler parameters differ")
    if [list(values) for values in encoder.categories_] != [list(values) for values in classification_encoder.categories_]:
        raise ValueError("Regression and classification categorical encoders differ")

    category_values = {
        feature: [str(value) for value in values]
        for feature, values in zip(categorical_features, encoder.categories_, strict=True)
    }
    imputer_values: dict[str, float | str] = {
        feature: float(value)
        for feature, value in zip(numeric_features, scaler.mean_, strict=True)
    }
    imputer_values.update(
        {
            feature: values[0] if values else ""
            for feature, values in category_values.items()
        }
    )

    regression_model = regression_result.estimator.named_steps["model"]
    classification_model = classification_result.estimator.named_steps["model"]
    classes = [str(value) for value in classification_model.classes_]
    positive_class = "over_goal"
    if positive_class not in classes:
        raise ValueError(f"Classification classes do not include {positive_class}: {classes}")
    positive_index = classes.index(positive_class)
    raw_classification_coefficients = np.asarray(classification_model.coef_, dtype=float)[0]
    raw_classification_intercept = float(classification_model.intercept_[0])
    if positive_index == 0:
        classification_coefficients = -raw_classification_coefficients
        classification_intercept = -raw_classification_intercept
    else:
        classification_coefficients = raw_classification_coefficients
        classification_intercept = raw_classification_intercept

    fixture_row = splits["test"].iloc[0]
    fixture_x = fixture_row[numeric_features + categorical_features].to_frame().T
    transformed = _dense_row(regression_preprocessor.transform(fixture_x))
    class_probabilities = classification_result.estimator.predict_proba(fixture_x)[0]
    fixture_probability = float(class_probabilities[positive_index])
    fixture_payload = {
        "feature_names": feature_names,
        "raw_features": {
            feature: (
                str(fixture_row[feature])
                if feature in categorical_features
                else float(fixture_row[feature])
            )
            for feature in numeric_features + categorical_features
        },
        "transformed_features": transformed,
        "regression_prediction": float(regression_result.estimator.predict(fixture_x)[0]),
        "classification_probability": fixture_probability,
        "classification_label": positive_class if fixture_probability >= 0.5 else next(value for value in classes if value != positive_class),
    }

    evaluation_scope = "synthetic evaluation" if source_type == "synthetic" else "real export evaluation"
    bundle = {
        "schema_version": 1,
        "model_version": f"screen-timing-linear-logistic-v1-{training_manifest_hash[:8]}",
        "trained_at": datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "source_type": source_type,
        "evaluation_scope": evaluation_scope,
        "numeric_features": numeric_features,
        "categorical_features": categorical_features,
        "category_values": category_values,
        "feature_names": feature_names,
        "missing_value_strategy": "numeric_mean_categorical_first_seen",
        "imputer_values": imputer_values,
        "scaler_mean": _json_number_list(scaler.mean_),
        "scaler_scale": _json_number_list(scaler.scale_),
        "regression_model": {
            "name": "linear_regression",
            "intercept": _json_scalar(regression_model.intercept_),
            "coefficients": _json_number_list(regression_model.coef_),
        },
        "classification_model": {
            "name": "logistic_regression",
            "intercept": classification_intercept,
            "coefficients": _json_number_list(classification_coefficients),
            "classes": classes,
            "positive_class": positive_class,
        },
        "training_manifest_hash": training_manifest_hash,
        "validation_fixture": fixture_payload,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    write_json(path, bundle)
    return bundle


def data_snapshot_hash(rows: pd.DataFrame) -> str:
    stable = rows.sort_index(axis=1).sort_values(["user_id", "date", "package_name"], kind="mergesort")
    payload = stable.to_csv(index=False).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def save_plots(
    output_dir: Path,
    splits: dict[str, pd.DataFrame],
    regression_result: ModelResult,
    classification_result: ModelResult,
) -> None:
    test = splits["test"].copy()
    test["prediction"] = regression_result.prediction
    plt.figure(figsize=(8, 5))
    sns.scatterplot(data=test, x="target_next_day_minutes", y="prediction", hue="source_type")
    limit = max(test["target_next_day_minutes"].max(), test["prediction"].max())
    plt.plot([0, limit], [0, limit], color="black", linestyle="--", linewidth=1)
    plt.title("Actual vs predicted next-day screen time")
    plt.xlabel("Actual minutes")
    plt.ylabel("Predicted minutes")
    plt.tight_layout()
    plt.savefig(output_dir / "actual_vs_predicted.png", dpi=160)
    plt.close()

    importance = feature_importance_rows(regression_result.estimator)
    plt.figure(figsize=(9, 5))
    sns.barplot(data=importance, x="importance", y="feature")
    plt.title("Top regression feature importance")
    plt.tight_layout()
    plt.savefig(output_dir / "feature_importance.png", dpi=160)
    plt.close()

    cm = confusion_matrix(test["goal_risk_label"], classification_result.prediction, labels=GOAL_RISK_LABELS)
    plt.figure(figsize=(6, 5))
    sns.heatmap(cm, annot=True, fmt="d", xticklabels=GOAL_RISK_LABELS, yticklabels=GOAL_RISK_LABELS, cmap="Blues")
    plt.xlabel("Predicted")
    plt.ylabel("Actual")
    plt.title("Goal-exceedance risk confusion matrix")
    plt.tight_layout()
    plt.savefig(output_dir / "confusion_matrix.png", dpi=160)
    plt.close()


def write_json(path: Path, data: object) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def write_model_card(
    path: Path,
    source_type: str,
    regression_best: ModelResult,
    regression_baseline: ModelResult,
    classification_best: ModelResult,
    classification_baseline: ModelResult,
    deployable: bool,
    reason: str,
) -> None:
    evaluation_scope = "synthetic evaluation" if source_type == "synthetic" else "real export evaluation"
    lines = [
        "# HabitGuard Screen Timing Model Card",
        "",
        f"- Source type: `{source_type}`",
        f"- Evaluation scope: `{evaluation_scope}`",
        "- Split method: per-user chronological train/validation/test split, not random row 80:20.",
        "- Regression target: next-day total screen-time minutes.",
        "- Classification target: next-day personal goal exceedance risk.",
        "- Auxiliary model: user type classification for analysis only.",
        "",
        "## Baseline Comparison",
        "",
        f"- Best regression baseline: `{regression_baseline.name}` MAE={regression_baseline.metrics['mae']}",
        f"- Best regression model: `{regression_best.name}` MAE={regression_best.metrics['mae']}, improvement={regression_best.metrics['baseline_improvement_pct']}%",
        f"- Classification baseline: `{classification_baseline.name}` Macro F1={classification_baseline.metrics['macro_f1']}",
        f"- Best classification model: `{classification_best.name}` Macro F1={classification_best.metrics['macro_f1']}, improvement={classification_best.metrics['baseline_improvement_pct']}%",
        "",
        "## Deployment Decision",
        "",
        f"- Deployable to app asset: `{deployable}`",
        f"- Reason: {reason}",
        "",
        "## Android Local Inference Bundle",
        "",
        "- Android does not read `.joblib` files.",
        "- `android_inference_bundle.json` exports feature order, missing-value fill values, scaler parameters, Linear Regression coefficients, and Logistic Regression coefficients.",
        "- Kotlin validates feature order and computes the same mathematical inference offline.",
        "- If fewer than 7 complete daily records exist, the app remains in collecting-data state.",
        "",
        "## Limitations",
        "",
        "- Synthetic metrics are not real-user performance.",
        "- Real exports must not be mixed with synthetic rows or reported as synthetic.",
        "- Accessibility/lock behavior is separate from this offline training pipeline.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def save_outputs(
    output_dir: Path,
    rows: pd.DataFrame,
    daily: pd.DataFrame,
    source_type: str,
    schema: dict[str, object],
    splits: dict[str, pd.DataFrame],
    regression_results: list[ModelResult],
    classification_results: list[ModelResult],
    user_type_result: ModelResult,
    android_asset: Path | None,
) -> dict[str, object]:
    output_dir.mkdir(parents=True, exist_ok=True)
    poster_assets = output_dir / "poster_assets"
    poster_assets.mkdir(exist_ok=True)

    best_regression = best_non_baseline(regression_results, "mae", maximize=False)
    best_regression_baseline = best_baseline(regression_results, "mae", maximize=False)
    best_classification = best_non_baseline(classification_results, "macro_f1", maximize=True)
    best_classification_baseline = best_baseline(classification_results, "macro_f1", maximize=True)
    deployable = (
        float(best_regression.metrics["baseline_improvement_pct"]) > 1.0
        and float(best_classification.metrics["baseline_improvement_pct"]) > 1.0
    )
    deploy_reason = (
        "Best non-baseline regression and classification models beat their baselines."
        if deployable
        else "At least one best non-baseline model did not improve over baseline by more than 1%; app asset deployment skipped."
    )

    daily.to_csv(output_dir / "daily_features.csv", index=False)
    write_json(output_dir / "feature_schema.json", schema)
    snapshot_hash = data_snapshot_hash(rows)
    (output_dir / "data_snapshot_hash.txt").write_text(snapshot_hash + "\n", encoding="utf-8")

    joblib.dump(best_regression.estimator.named_steps["preprocess"], output_dir / "preprocessing.joblib")
    joblib.dump(best_regression.estimator, output_dir / "screen_time_regressor.joblib")
    joblib.dump(best_classification.estimator, output_dir / "goal_risk_classifier.joblib")
    joblib.dump(user_type_result.estimator, output_dir / "user_type_classifier.joblib")

    regression_top_features = feature_importance_rows(best_regression.estimator).to_dict(orient="records")
    classification_top_features = feature_importance_rows(best_classification.estimator).to_dict(orient="records")
    regression_payload = {
        "source_type": source_type,
        "evaluation_scope": "synthetic evaluation" if source_type == "synthetic" else "real export evaluation",
        "best_model": best_regression.name,
        "best_baseline": best_regression_baseline.name,
        **best_regression.metrics,
        "mae_minutes": best_regression.metrics["mae"],
        "rmse_minutes": best_regression.metrics["rmse"],
        "top_features": regression_top_features,
        "all_models": [{"model": result.name, **result.metrics} for result in regression_results],
    }
    classification_payload = {
        "source_type": source_type,
        "evaluation_scope": "synthetic evaluation" if source_type == "synthetic" else "real export evaluation",
        "best_model": best_classification.name,
        "best_baseline": best_classification_baseline.name,
        **best_classification.metrics,
        "f1_macro": best_classification.metrics["macro_f1"],
        "top_features": classification_top_features,
        "labels": GOAL_RISK_LABELS,
        "confusion_matrix": confusion_matrix(
            splits["test"]["goal_risk_label"],
            best_classification.prediction,
            labels=GOAL_RISK_LABELS,
        ).tolist(),
        "all_models": [{"model": result.name, **result.metrics} for result in classification_results],
    }
    write_json(output_dir / "regression_metrics.json", regression_payload)
    write_json(output_dir / "classification_metrics.json", classification_payload)

    comparison = pd.DataFrame(
        [{"task": result.task, "model": result.name, **result.metrics} for result in regression_results + classification_results]
        + [{"task": user_type_result.task, "model": user_type_result.name, **user_type_result.metrics}]
    )
    comparison.to_csv(output_dir / "model_comparison.csv", index=False)

    save_plots(output_dir, splits, best_regression, best_classification)
    write_model_card(
        output_dir / "model_card.md",
        source_type,
        best_regression,
        best_regression_baseline,
        best_classification,
        best_classification_baseline,
        deployable,
        deploy_reason,
    )
    for filename in ["actual_vs_predicted.png", "feature_importance.png", "confusion_matrix.png", "model_card.md"]:
        shutil.copyfile(output_dir / filename, poster_assets / filename)

    manifest = {
        "pipeline_version": 3,
        "source_type": source_type,
        "evaluation_scope": "synthetic evaluation" if source_type == "synthetic" else "real export evaluation",
        "used_random_row_split": False,
        "split_method": "per_user_chronological_60_20_20",
        "row_count": int(len(rows)),
        "daily_row_count": int(len(daily)),
        "user_count": int(daily["user_id"].nunique()),
        "date_min": str(daily["date"].min().date()),
        "date_max": str(daily["date"].max().date()),
        "data_snapshot_hash": snapshot_hash,
        "regression": regression_payload,
        "classification": classification_payload,
        "user_type_auxiliary": {"model": user_type_result.name, **user_type_result.metrics},
        "deployable_to_app": deployable,
        "deployment_decision": deploy_reason,
        "artifacts": {
            "feature_schema": "feature_schema.json",
            "preprocessor": "preprocessing.joblib",
            "regressor": "screen_time_regressor.joblib",
            "classifier": "goal_risk_classifier.joblib",
            "user_type_classifier": "user_type_classifier.joblib",
            "model_card": "model_card.md",
            "android_inference_bundle": "android_inference_bundle.json",
        },
    }
    manifest_hash = hashlib.sha256(
        json.dumps(manifest, ensure_ascii=False, sort_keys=True).encode("utf-8")
    ).hexdigest()
    android_bundle = export_android_inference_bundle(
        path=output_dir / "android_inference_bundle.json",
        source_type=source_type,
        schema=schema,
        splits=splits,
        regression_result=selected_model(regression_results, "linear_regression"),
        classification_result=selected_model(classification_results, "logistic_regression"),
        training_manifest_hash=manifest_hash,
    )
    manifest["android_inference_bundle"] = {
        "path": "android_inference_bundle.json",
        "model_version": android_bundle["model_version"],
        "training_manifest_hash": manifest_hash,
    }
    write_json(output_dir / "training_manifest.json", manifest)

    if android_asset is not None and deployable:
        android_asset.parent.mkdir(parents=True, exist_ok=True)
        android_profile = {
            "profile_version": 3,
            "summary": "HabitGuard offline training profile",
            "source_type": source_type,
            "evaluation_scope": manifest["evaluation_scope"],
            "deployable_to_app": deployable,
            "date_count": int(daily["date"].nunique()),
            "row_count": int(len(daily)),
            "source_rows": int(len(rows)),
            "regression": regression_payload,
            "classification": classification_payload,
            "user_type_auxiliary": manifest["user_type_auxiliary"],
            "caveat": (
                "Synthetic evaluation only; do not present as real-user performance."
                if source_type == "synthetic"
                else "Real export evaluation from user-supplied CSV; do not upload raw logs without consent."
            ),
        }
        write_json(android_asset, android_profile)
        android_bundle_asset = android_asset.with_name("android_inference_bundle.json")
        write_json(android_bundle_asset, android_bundle)
        manifest["android_asset_updated"] = str(android_asset)
        manifest["android_inference_bundle_asset_updated"] = str(android_bundle_asset)
    else:
        manifest["android_asset_updated"] = None
        manifest["android_inference_bundle_asset_updated"] = None
    write_json(output_dir / "training_manifest.json", manifest)
    return manifest


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Train HabitGuard screen-time regression and risk classification models.")
    parser.add_argument("input_csv", nargs="?", type=Path, help="HabitGuard CSV export. Omit to use deterministic synthetic sample data.")
    parser.add_argument("--output-dir", type=Path, default=Path("ai/phone_outputs"))
    parser.add_argument("--update-android-asset", action="store_true", help="Write app/src/main/assets/habitguard_model_profile.json only if models beat baselines.")
    args = parser.parse_args(argv)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    rows, source_type = load_phone_csv(args.input_csv, args.output_dir)
    daily = build_daily_features(rows, source_type=source_type)
    if len(daily) < 45:
        raise ValueError(f"Need at least 45 daily samples after aggregation; got {len(daily)}")

    schema = build_feature_schema(daily)
    splits = split_by_user_and_time(daily)
    regression_results = evaluate_regression_models(splits, schema)
    classification_results = evaluate_classification_models(splits, schema)
    user_type_result = train_user_type_classifier(splits, schema)
    android_asset = Path("app/src/main/assets/habitguard_model_profile.json") if args.update_android_asset else None
    manifest = save_outputs(
        output_dir=args.output_dir,
        rows=rows,
        daily=daily,
        source_type=source_type,
        schema=schema,
        splits=splits,
        regression_results=regression_results,
        classification_results=classification_results,
        user_type_result=user_type_result,
        android_asset=android_asset,
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
