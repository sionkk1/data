# HabitGuard 예측 모델 Colab

Android 앱과 분리해서 예측 모델 학습, 평가, 시각화, Android 로컬 추론 bundle export만 실행하는 Colab 노트북입니다.

[Colab에서 열기](https://colab.research.google.com/github/sionkk1/data/blob/master/habitguard_android/notebooks/habitguard_prediction_model_colab.ipynb)

## 기능

- HabitGuard CSV export 업로드 또는 synthetic sample 실행
- 다음 날 총 스크린타임 회귀 모델 평가
- 다음 날 목표 초과 위험 분류 모델 평가
- baseline 비교, confusion matrix, feature importance 출력
- `android_inference_bundle.json` 생성
- 결과 zip 다운로드

## 주의

CSV를 업로드하지 않으면 합성 데이터로 실행됩니다. 이 경우 결과는 `source_type=synthetic`, `evaluation_scope=synthetic evaluation`이며 실제 사용자 성능으로 주장하면 안 됩니다.
