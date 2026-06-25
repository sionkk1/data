# HabitGuard Local Inference Model Card

Last updated: 2026-06-21 KST

## Scope

This card describes the Android local inference bundle generated from `ai/train_from_phone_csv.py`.

- Android asset: `app/src/main/assets/android_inference_bundle.json`
- Training output: `ai/phone_outputs/android_inference_bundle.json`
- Model version: `screen-timing-linear-logistic-v1-7170d82f`
- Source type: `synthetic`
- Evaluation scope: `synthetic evaluation`

The current metrics and coefficients come from deterministic synthetic scenario data. They are not real-user performance evidence.

## Models

- Regression: `linear_regression`
- Regression target: next-day total screen-time minutes
- Classification: `logistic_regression`
- Classification target: next-day personal-goal exceedance
- Positive class exported for Android probability: `over_goal`

## Android Inference

Android does not read `.joblib` files. The training pipeline exports scaler values, one-hot category values, coefficients, and intercepts into JSON. Kotlin loads that JSON through `ModelBundleLoader` and computes:

1. Missing-value fill from `imputer_values`
2. StandardScaler transform with `scaler_mean` and `scaler_scale`
3. Linear regression prediction
4. Logistic regression over-goal probability
5. Rounded next-day minutes in 10-minute units

The app keeps collecting-data state until at least 7 complete daily records exist.

## Validation

`TrainedLocalPredictionModelTest.kotlinInferenceMatchesPythonValidationFixture` checks the Python validation fixture in the bundle:

- Regression tolerance: `<= 0.1` minute
- Classification probability tolerance: `<= 0.001`
- Feature order mismatch fails bundle validation
- `source_type` / `evaluation_scope` mismatch fails bundle validation

## Limitations

- The current bundle is trained and evaluated only on synthetic data.
- Real HabitGuard CSV exports must be evaluated separately before claiming real-user performance.
- The model result does not automatically enable restriction rules; user approval remains required.
- Network services, FastAPI, Cloud Run, Firebase, and Firestore are not part of this local inference path.
