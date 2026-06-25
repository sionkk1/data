param(
    [switch]$InstallApk,
    [switch]$OpenApp,
    [switch]$ExportCsv,
    [string]$ApkPath = "app\build\outputs\apk\debug\app-debug.apk",
    [string]$PackageName = "com.habitguard.app"
)

$ErrorActionPreference = "Stop"

function Require-Command($Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is not available on PATH. Add Android SDK platform-tools to PATH first."
    }
}

function Invoke-Adb($Arguments) {
    Write-Host "adb $Arguments"
    adb $Arguments
}

Require-Command "adb"

$devices = adb devices | Select-String "`tdevice$"
if (-not $devices) {
    throw "No online Android device found. Connect a device with USB debugging enabled."
}

Write-Host "Connected devices:"
adb devices

if ($InstallApk) {
    if (-not (Test-Path $ApkPath)) {
        throw "APK not found: $ApkPath. Run .\gradlew.bat :app:assembleDebug first."
    }
    Invoke-Adb "install -r `"$ApkPath`""
}

if ($OpenApp) {
    Invoke-Adb "shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1"
}

Write-Host ""
Write-Host "Permission setup still requires manual Android Settings actions:"
Write-Host "1. Usage access: Settings > Special app access > Usage access > HabitGuard"
Write-Host "2. Accessibility: Settings > Accessibility > HabitGuard"
Write-Host "3. Notification access: Settings > Notifications > Device & app notifications or Notification access"

if ($ExportCsv) {
    Invoke-Adb "shell am broadcast -a $PackageName.DEBUG_EXPORT_CSV"
    Write-Host "Debug receiver writes CSV inside the app cache and logs its path with tag HabitGuardDebugExport."
    Write-Host "Run: adb logcat -d -s HabitGuardDebugExport"
}
