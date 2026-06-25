# HabitGuard Model and Analysis Summary

- Source status: synthetic_sample_generated_by_pipeline
- Regression best model: random_forest
- Regression MAE/RMSE: 25.778 / 30.862 minutes
- Classification best model: decision_tree
- Classification Accuracy/Precision/Recall/F1: 0.957 / 0.933 / 0.978 / 0.951
- Notification-open correlation: 0.736
- Night-next-day correlation: 0.46
- Top usage category: sns

Poster-safe wording:
- The pipeline trains and compares baseline, decision tree, logistic regression/classifier, and random forest models.
- Confusion matrix and feature-importance plots are generated reproducibly from the input CSV.

Do not claim as real-user performance unless the source status is `habitguard_export_csv` and the CSV is from an approved real export.
