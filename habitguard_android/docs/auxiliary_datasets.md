# Auxiliary Dataset Review

This project should prefer real HabitGuard CSV exports from the user's own phone. External datasets are only auxiliary references unless license, privacy, and schema fit are verified.

## Candidate Sources

| Source | Fit | Privacy/license status | Decision |
| --- | --- | --- | --- |
| Kaggle: Screen Time and App Usage Dataset (iOS/Android) | Has simulated anonymized app/category screen-time rows. It can help demonstrate EDA and category modeling. | Search metadata reported MIT License, but the Kaggle page must be manually checked before use. | Do not import yet. Use only after manual license confirmation. |
| GTS/Kaggle: Mobile Device Usage and User Behavior Dataset | Contains daily app usage time, screen-on time, installed apps, data usage, and behavior class. Useful for behavior-classification framing. | GTS page says it is sourced from Kaggle and lists 700 samples, but download terms require manual review. | Do not import yet. Use as schema inspiration only. |
| Avicenna app-usage reference | Not a dataset, but it documents privacy-preserving app-usage metadata: app identity, duration, notification and pickup metadata, no content capture. | Public documentation. | Safe as methodological support and privacy comparison. |
| Android official UsageStatsManager docs | Confirms app usage/event queries require `PACKAGE_USAGE_STATS`. | Public official API documentation. | Safe as technical basis for Android implementation. |

## Excluded Data

- Any raw phone usage logs from another person.
- Any dataset containing message content, URLs with personal identity, precise location traces, phone numbers, account IDs, or device identifiers that cannot be removed.
- Any Kaggle/GitHub CSV without clear license terms.
- Any scraped Digital Wellbeing private database dump.

## Mapping To HabitGuard CSV

External data can only be attached if it can be transformed into this non-content metadata schema:

```text
date, package_name, app_name, category, usage_minutes, night_minutes,
open_count, notification_count, first_open_time, last_time_used, captured_at
```

If a dataset lacks `night_minutes`, `open_count`, or `notification_count`, those fields must be marked as missing/imputed in the analysis and not described as measured.

## Sources Checked

- Android UsageStatsManager: https://developer.android.com/reference/android/app/usage/UsageStatsManager
- Android AccessibilityService: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Avicenna app usage reference: https://forum.avicennaresearch.com/t/app-usage/1247
- GTS mobile device usage dataset page: https://gts.ai/dataset-download/mobile-device-usage-and-user-behavior-dataset/
- Kaggle screen time/app usage candidate: https://www.kaggle.com/datasets/khushikyad001/screen-time-and-app-usage-dataset-iosandroid
