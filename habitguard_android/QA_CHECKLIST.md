# HabitGuard QA Checklist

This checklist is for manual device verification. Do not mark an item passed without recording device model, Android version, build variant, date/time, and evidence path.

## Device Matrix

Target Android versions:
- Android 10
- Android 11
- Android 12
- Android 13
- Android 14
- Android 15
- Android 16

Device families:
- Samsung Galaxy phone
- Samsung tablet
- Google Pixel phone
- Google Pixel tablet or Pixel foldable if available

Minimum evidence per run:
- Device model and OS version
- App version/build variant
- Permission state before test
- Screenshot or screen recording path
- Logcat excerpt only if it does not include raw usage timeline, input text, notification body, token, or private content
- Pass/fail result and issue link or `TEST_FAILURES.md` entry for failures

## Install And Startup

- [ ] Install debug APK successfully.
- [ ] App launches without crash after fresh install.
- [ ] App relaunches after force stop.
- [ ] App relaunches after device reboot.
- [ ] App remains usable when Usage Access is denied.
- [ ] App remains usable when Accessibility permission is denied.
- [ ] App remains usable when Notification Listener access is denied.
- [ ] Permission guidance explains disabled functionality without forcing consent.

## Usage Collection

- [ ] Usage Access grant is detected after returning from Android settings.
- [ ] Usage Access denial shows a data collection guidance state.
- [ ] A day with real zero usage is shown differently from a day without permission.
- [ ] Daily total usage matches Android Digital Wellbeing or Settings within an acceptable tolerance.
- [ ] Category totals for video, SNS, game, productivity, and other are displayed.
- [ ] Night usage uses the 23:00-06:00 window.
- [ ] Sessions crossing midnight are split into the correct dates.
- [ ] Duplicate app foreground events do not double-count usage.
- [ ] Reboot day is marked with reboot/data-quality status.
- [ ] Timezone change does not move already collected usage into the wrong local date.

## Dashboard And AI Result Display

- [ ] With fewer than 7 complete days, dashboard shows the collection-stage message and no precise personalized prediction.
- [ ] With at least 7 complete days, dashboard separates measured values from prediction values.
- [ ] Prediction source is visible as remote, local, baseline, cache, or collecting data.
- [ ] Model version is visible when a prediction is displayed.
- [ ] Calculation time is visible.
- [ ] Data quality status is visible.
- [ ] Goal exceedance risk text is visible.
- [ ] Main factors are visible without exposing raw app timeline.
- [ ] Offline mode uses cache or baseline fallback without crashing.

## Goal And Rule Approval

- [ ] Natural-language goal parsing does not save a rule until the user approves.
- [ ] Recommended app, daily limit, time range, mission, and unlock duration can be reviewed.
- [ ] AI recommendation text is marked as reference only.
- [ ] Rejecting a recommendation does not create or enable a rule.
- [ ] Approving a rule creates an enabled and approved rule only for the selected target.
- [ ] Editing rule values before approval saves the edited values.

## Guard And Mission Flow

- [ ] Approved rule triggers the Lock/Mission screen for the target app.
- [ ] Unapproved or disabled rule does not trigger a lock.
- [ ] Rule applies inside a 23:00-06:00 overnight window.
- [ ] Rule does not apply outside the configured time window.
- [ ] Daily limit excess triggers lock.
- [ ] Session limit excess triggers lock.
- [ ] Mission success grants only an allowed temporary unlock duration, such as 5, 10, or 30 minutes.
- [ ] Temporary unlock expires by elapsed realtime.
- [ ] Temporary unlock is invalidated after reboot.
- [ ] Manual device time changes do not extend the unlock.
- [ ] Daily unlock limit is enforced.
- [ ] Emergency unlock works and is logged separately.
- [ ] Back button, home, recent apps, and restricted-app re-entry are logged.
- [ ] The app does not force stop, uninstall, or disable another app.

## Accessibility And Privacy

- [ ] Accessibility service is configured with window content retrieval disabled.
- [ ] Accessibility flow only uses foreground package/app events.
- [ ] No screen text, input text, message body, or notification body is displayed in logs or stored records.
- [ ] Notification listener records counts only.
- [ ] CSV export is user-initiated.
- [ ] CSV export does not include notification body, message text, token, or service key.
- [ ] Local data deletion clears the documented local tables.
- [ ] Auto Backup remains disabled or excludes private databases/shared preferences.

## UI And Accessibility Semantics

- [ ] Key metric cards have readable labels and source labels.
- [ ] Buttons have a click action and visible text.
- [ ] Permission cards have clear content descriptions.
- [ ] Risk indication is not color-only; icon/text are present.
- [ ] TalkBack reads permission cards, prediction card, rule approval controls, and mission timer in a useful order.
- [ ] 200% font scale does not hide essential controls.
- [ ] Landscape orientation remains usable.
- [ ] Dark mode remains readable.
- [ ] Tablet layout remains usable without overlapping UI elements.

## Release Readiness Checks

- [ ] Release build contains no debug-only export receiver.
- [ ] Release/main sources do not log usage timeline, package/app usage details, tokens, notification body, or free-form goal text.
- [ ] HTTPS-only network configuration remains active.
- [ ] No API key, service account key, bearer token, or `google-services.json` is committed.
- [ ] Google Play disclosure language describes the feature as a user-approved interruption/mission flow, not OS-level app blocking.
