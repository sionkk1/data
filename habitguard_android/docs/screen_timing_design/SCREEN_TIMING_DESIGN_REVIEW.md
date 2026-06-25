# Screen Timing Compose Design Review

Last updated: 2026-06-20 KST

## Scope

This update keeps HabitGuard as a native Android Kotlin + Jetpack Compose app. It does not rewrite the app in Flutter and does not change data collection, model training, or restriction approval behavior.

Implemented scope:

- Onboarding and permission center
- Main dashboard
- Detailed analysis
- Natural-language goal input
- AI recommended rule review, edit, reject, and approve flow
- Restriction and mission screen tone
- Weekly report
- Settings and privacy center

## Design Tokens

Source: `app/src/main/java/com/habitguard/app/ui/ScreenTimingDesignSystem.kt`

- Color roles: blue for primary actions and data, green for stable/positive status, amber for notice/review states, rose for warning states, neutral ink/canvas/surface roles for light and dark mode.
- Spacing: `XSmall`, `Small`, `Medium`, `Large`, `XLarge`, and `MinTouch = 48.dp`.
- Shape: shared 8dp card/button radius.
- State tones: `Good`, `Notice`, `Warning`, `Neutral`.
- Theme: `ScreenTimingTheme()` provides light and dark `ColorScheme`.

## Common Components

Source: `app/src/main/java/com/habitguard/app/ui/ScreenTimingDesignSystem.kt`

- `ScreenTimingCard`
- `ScreenTimingPrimaryButton`
- `ScreenTimingSecondaryButton`
- `ScreenTimingStatusBadge`
- `ScreenTimingMetricCard`
- `ScreenTimingButtonRow`
- `ScreenTimingUiCopy`

These components centralize touch size, card border, state color, and repeated accessibility descriptions.

## Before / After

Before:

- `HabitGuardApp.kt` mixed tokens, screen layout, cards, charts, and tab UI in one file.
- Main app tabs were Dashboard, Goal, Debug, Settings, so detailed analysis and weekly report were embedded rather than clearly separated.
- Permission, measured values, prediction, and recommendation states were less consistently labeled.
- Lock/Mission UI used a separate local color system and did not share app design tokens.

After:

- `ScreenTimingDesignSystem.kt` owns design tokens and reusable components.
- `HabitGuardApp.kt` now presents Home, Analysis, Goal, Report, and Privacy tabs.
- Home separates measured values, AI prediction, risk status, and recommendation/rule status.
- Goal screen keeps AI recommendations in a review card with recommendation reason, edit controls, reject, and approve actions.
- Privacy center explicitly states local-first storage, CSV export behavior, cloud sync limitation, and mission-screen test entry.
- Lock/Mission screen uses calm copy, shared theme, 48dp+ actions, scroll layout, and non-alarming status text.

## Data Honesty

- No fake default usage numbers were added.
- If personalized prediction is not ready, the UI says that 7 complete days are required and does not display a fabricated prediction.
- AI prediction cards are labeled `AI 예측`.
- measured metric cards are labeled `측정값`.
- AI recommendation review is labeled `승인 전` until the user explicitly approves the rule.

## Accessibility Check

Checked with Android `uiautomator dump` from connected device `R54X400KP2N`.

Evidence:

- `docs/screen_timing_design/captures/home-uiautomator.xml`
- `docs/screen_timing_design/captures/analysis-uiautomator.xml`
- `docs/screen_timing_design/captures/goal-uiautomator.xml`
- `docs/screen_timing_design/captures/report-uiautomator.xml`
- `docs/screen_timing_design/captures/privacy-uiautomator.xml`
- `docs/screen_timing_design/captures/lock-mission-uiautomator.xml`

Observed:

- Permission center exposes content descriptions such as `온보딩 및 권한 센터`, `사용 기록 접근 권한 필요`, `접근성 서비스 필요`, and `알림 접근 권한 선택`.
- Metric cards expose combined descriptions such as `총 사용 시간, 0분, 측정값`.
- Risk status exposes text plus icon description, for example `위험도 안정적, 안정 아이콘`.
- Bottom tabs expose descriptions such as `홈 탭`, `분석 탭`, `목표 탭`, `리포트 탭`, and `개인정보 탭`.
- Mission timer exposes a dynamic description such as `남은 대기 시간 27초`.
- Primary/secondary action buttons have at least 48dp minimum height in shared components.

Not fully verified:

- TalkBack was not manually enabled and listened through end to end.
- 200% font size, landscape, and dark mode were not fully device-captured in this task.
- Instrumentation tests for Compose semantics were not added.

## Captures

- Home: `docs/screen_timing_design/captures/home.png`
- Analysis: `docs/screen_timing_design/captures/analysis.png`
- Goal input: `docs/screen_timing_design/captures/goal.png`
- Weekly report: `docs/screen_timing_design/captures/report.png`
- Privacy center: `docs/screen_timing_design/captures/privacy.png`
- Lock/Mission: `docs/screen_timing_design/captures/lock-mission.png`

## Figma Status

Figma was requested, but no callable Figma MCP tool was available in this session. The implementation was completed directly in Android Compose and verified on a connected Android device.

## Remaining UI Risks

- The app still needs manual QA at 200% font size, landscape orientation, and dark mode.
- Goal parsing strings and recommendation reasons in `GoalParser.kt` still need a separate Korean copy cleanup pass.
- Full rule management is still incomplete: users can create/approve a rule, but there is no complete multi-rule edit/delete screen yet.
- Real-device guard behavior still needs a structured run through the device test plan.
