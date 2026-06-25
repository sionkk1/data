# HabitGuard Screen Timing Model Card

- Source type: `synthetic`
- Evaluation scope: `synthetic evaluation`
- Split method: per-user chronological train/validation/test split, not random row 80:20.
- Regression target: next-day total screen-time minutes.
- Classification target: next-day personal goal exceedance risk.
- Auxiliary model: user type classification for analysis only.

## Baseline Comparison

- Best regression baseline: `baseline_rolling_7d` MAE=40.6773
- Best regression model: `linear_regression` MAE=18.1632, improvement=55.3482%
- Classification baseline: `baseline_majority` Macro F1=0.395
- Best classification model: `logistic_regression` Macro F1=0.8495, improvement=115.0629%

## Deployment Decision

- Deployable to app asset: `True`
- Reason: Best non-baseline regression and classification models beat their baselines.

## Android Local Inference Bundle

- Android does not read `.joblib` files.
- `android_inference_bundle.json` exports feature order, missing-value fill values, scaler parameters, Linear Regression coefficients, and Logistic Regression coefficients.
- Kotlin validates feature order and computes the same mathematical inference offline.
- If fewer than 7 complete daily records exist, the app remains in collecting-data state.

## Limitations

- Synthetic metrics are not real-user performance.
- Real exports must not be mixed with synthetic rows or reported as synthetic.
- Accessibility/lock behavior is separate from this offline training pipeline.
