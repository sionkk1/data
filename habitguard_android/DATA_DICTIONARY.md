# HabitGuard Data Dictionary

Last updated: 2026-06-22 KST

This file documents the Android CSV export and local inference bundle fields. It separates real app-collected data from synthetic training data.

## Source Labels

| Field | Values | Meaning |
| --- | --- | --- |
| `source_type` | `synthetic`, `real` | `synthetic` means generated scenario data. `real` means a user-approved HabitGuard export. Current model artifacts are `synthetic`. |
| `evaluation_scope` | `synthetic evaluation`, `real export evaluation` | Current evaluation scope is `synthetic evaluation`; do not present it as real-user performance. |

## Android CSV Export

Export path is user-initiated from the app. The export contains app-level daily rows, not notification bodies or free-form goal text.

| Column | Type | Description |
| --- | --- | --- |
| `date` | string | Local date for the app usage row. |
| `package_name` | string | Android app package name. Used for local analysis/export; not sent by the prediction DTO. |
| `app_name` | string | Display name for the app. |
| `category` | string | App category used for aggregate features such as video/SNS/game/productivity. |
| `usage_minutes` | number | App usage minutes for the date. |
| `night_minutes` | number | Usage minutes during the night window. |
| `open_count` | integer | Estimated app foreground/session count. |
| `notification_count` | integer | Daily count only; notification title/body text is not stored. |
| `first_open_time` | integer | First observed open timestamp in milliseconds when available. |
| `last_time_used` | integer | Last observed usage timestamp in milliseconds. |
| `captured_at` | integer | Export or collection timestamp in milliseconds. |

## Android Inference Bundle

Bundle paths:

- `ai/phone_outputs/android_inference_bundle.json`
- `app/src/main/assets/android_inference_bundle.json`

Required top-level fields:

| Field | Type | Description |
| --- | --- | --- |
| `schema_version` | integer | Bundle schema version. Current value: `1`. |
| `model_version` | string | Android local inference model version. |
| `trained_at` | string | UTC timestamp for bundle export. |
| `source_type` | string | Current value: `synthetic`. |
| `evaluation_scope` | string | Current value: `synthetic evaluation`. |
| `numeric_features` | string array | Raw numeric feature order before scaling. |
| `categorical_features` | string array | Raw categorical feature names. |
| `category_values` | object | One-hot category order per categorical feature. |
| `feature_names` | string array | Final transformed feature order used by coefficients. |
| `missing_value_strategy` | string | Current value: `numeric_mean_categorical_first_seen`. |
| `imputer_values` | object | Fill values used by Kotlin when an input feature is missing. |
| `scaler_mean` | number array | StandardScaler means for numeric features. |
| `scaler_scale` | number array | StandardScaler scales for numeric features. |
| `regression_model` | object | Linear regression intercept and coefficients. |
| `classification_model` | object | Logistic regression intercept, coefficients, classes, and positive class. |
| `training_manifest_hash` | string | SHA-256 hash of the training manifest payload used for bundle versioning. |
| `validation_fixture` | object | Python-generated fixture used to verify Kotlin math parity. |

Current transformed feature order:

`total_minutes`, `night_minutes`, `open_count`, `notification_count`, `app_count`, `top_app_minutes`, `avg_session_minutes_proxy`, `night_ratio`, `notification_per_open`, `rolling_3d_total`, `rolling_7d_total`, `previous_day_total`, `personal_goal_minutes`, `video`, `sns`, `game`, `browser`, `productivity`, `other`, `weekday_0`, `weekday_1`, `weekday_2`, `weekday_3`, `weekday_4`, `weekday_5`, `weekday_6`, `is_weekend_False`, `is_weekend_True`.

Target columns excluded from features:

- `target_next_day_minutes`
- `target_goal_exceeded`
- `goal_risk_label`
- `user_type_label`

## Privacy Boundary

The local inference path uses daily summary features and category aggregates. It does not send data over the network and does not read notification body text, screen content, or free-form user goal text for prediction requests.

## Local-First Prediction Refresh

The offline Android path is:

1. `DailyUsageSyncWorker` collects/aggregates usage into `usage_daily` and `app_usage_daily`.
2. `LocalFirstPredictionRefreshEngine` maps recent Room rows into `PredictionInput`.
3. `DefaultPredictionRepository` uses the local JSON bundle first when at least 7 `COMPLETE` daily rows exist.
4. `TrainedLocalPredictionModel` computes missing-value fill, StandardScaler, Linear Regression minutes, and Logistic Regression goal-exceedance probability.
5. Non-collecting outcomes are stored in `prediction_result`.

Fallback order after the 7-day gate is local model, cache, then baseline. If fewer than 7 complete days exist, the app remains in collecting-data state and does not store a precise model prediction.
