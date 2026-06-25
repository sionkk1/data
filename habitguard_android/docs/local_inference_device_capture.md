# Local Inference Device Capture Log

Last checked: 2026-06-21 KST

## Goal

Capture the Android prediction card after wiring the local `android_inference_bundle.json` inference path.

## Result

Not captured in this task.

## Evidence

Commands run:

- `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe devices`
- `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe start-server`
- `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe devices`

Both `adb devices` checks returned only:

```text
List of devices attached
```

No Android device serial was listed, so the app could not be installed/launched for a fresh prediction-card screenshot.

## Follow-up

Reconnect the tablet with USB debugging enabled, then run:

```powershell
.\gradlew.bat --no-daemon :app:installDebug
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb shell monkey -p com.habitguard.app 1
& $adb exec-out screencap -p > docs/local_inference_prediction_card.png
```

The screenshot should show the prediction card with local model source, model version, data quality, `source_type`, `evaluation_scope`, and the synthetic-model caveat.
