#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def fail(message: str, failures: list[str]) -> None:
    failures.append(message)


def attr(element: ET.Element, name: str) -> str | None:
    return element.attrib.get(ANDROID_NS + name)


def require_contains(text: str, needle: str, label: str, failures: list[str]) -> None:
    if needle not in text:
        fail(f"{label}: missing `{needle}`", failures)


def check_manifest(failures: list[str]) -> None:
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    application = manifest.find("application")
    if application is None:
        fail("AndroidManifest.xml: missing <application>", failures)
        return

    expected_app_attrs = {
        "allowBackup": "false",
        "usesCleartextTraffic": "false",
        "networkSecurityConfig": "@xml/network_security_config",
    }
    for name, expected in expected_app_attrs.items():
        actual = attr(application, name)
        if actual != expected:
            fail(f"AndroidManifest.xml: application {name} expected {expected}, got {actual}", failures)

    exported = {
        attr(component, "name"): attr(component, "exported")
        for component in list(application)
        if component.tag in {"activity", "service", "provider", "receiver"}
    }
    expected_exported = {
        ".MainActivity": "true",
        ".LockActivity": "false",
        "androidx.core.content.FileProvider": "false",
        ".guard.HabitGuardAccessibilityService": "true",
        ".guard.HabitGuardNotificationListenerService": "true",
    }
    for component, expected in expected_exported.items():
        if exported.get(component) != expected:
            fail(f"AndroidManifest.xml: {component} exported expected {expected}, got {exported.get(component)}", failures)

    manifest_text = read("app/src/main/AndroidManifest.xml")
    if "android.intent.category.BROWSABLE" in manifest_text or 'android.intent.action.VIEW"' in manifest_text:
        fail("AndroidManifest.xml: unexpected deep link / browsable VIEW intent found", failures)


def check_network_security(failures: list[str]) -> None:
    path = ROOT / "app/src/main/res/xml/network_security_config.xml"
    if not path.exists():
        fail("network_security_config.xml: missing HTTPS-only network security config", failures)
        return
    xml = read("app/src/main/res/xml/network_security_config.xml")
    require_contains(xml, '<base-config cleartextTrafficPermitted="false">', "network_security_config.xml", failures)
    require_contains(xml, '<trust-anchors>', "network_security_config.xml", failures)


def check_backup(failures: list[str]) -> None:
    backup = read("app/src/main/res/xml/backup_rules.xml")
    extraction = read("app/src/main/res/xml/data_extraction_rules.xml")
    for label, text in [("backup_rules.xml", backup), ("data_extraction_rules.xml", extraction)]:
        require_contains(text, 'domain="database"', label, failures)
        require_contains(text, 'domain="sharedpref"', label, failures)


def check_sensitive_sources(failures: list[str]) -> None:
    main_sources = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / "app/src/main").rglob("*.kt"))
    if "http://" in main_sources:
        fail("app/src/main: cleartext http:// URL found", failures)

    firestore = read("app/src/main/java/com/habitguard/app/data/FirestoreSyncRepository.kt")
    if "prefs.getString(KEY_BEARER_TOKEN" in firestore:
        fail("FirestoreSyncRepository.kt: bearer token is read from plain SharedPreferences", failures)
    require_contains(firestore, "SecureTokenStore", "FirestoreSyncRepository.kt", failures)
    if '"rawText" to it.rawText' in firestore:
        fail("FirestoreSyncRepository.kt: free-form goal text is included in cloud payload", failures)

    debug_receiver = read("app/src/debug/java/com/habitguard/app/debug/DebugExportReceiver.kt")
    if ".absolutePath" in debug_receiver:
        fail("DebugExportReceiver.kt: debug log exposes an absolute CSV path", failures)

    hardcoded_secret_pattern = re.compile(
        r"(?i)(api[_-]?key|private[_-]?key|service[_-]?account|bearer[_-]?token)\s*=\s*\"[A-Za-z0-9_\-+/=.]{20,}\""
    )
    for path in (ROOT / "app/src").rglob("*"):
        if path.is_file() and path.suffix in {".kt", ".xml", ".json", ".properties"}:
            if path.name == "security_check.py":
                continue
            if hardcoded_secret_pattern.search(path.read_text(encoding="utf-8", errors="ignore")):
                fail(f"{path.relative_to(ROOT)}: possible hardcoded secret", failures)


def check_accessibility_and_notifications(failures: list[str]) -> None:
    accessibility_xml = read("app/src/main/res/xml/habitguard_accessibility_service.xml")
    require_contains(accessibility_xml, 'android:canRetrieveWindowContent="false"', "habitguard_accessibility_service.xml", failures)

    accessibility_service = read("app/src/main/java/com/habitguard/app/guard/HabitGuardAccessibilityService.kt")
    forbidden_accessibility = [
        "rootInActiveWindow",
        "AccessibilityNodeInfo",
        "event.text",
        "event.contentDescription",
        "source",
    ]
    for token in forbidden_accessibility:
        if token in accessibility_service:
            fail(f"HabitGuardAccessibilityService.kt: forbidden content access token `{token}` found", failures)

    notification_service = read("app/src/main/java/com/habitguard/app/guard/HabitGuardNotificationListenerService.kt")
    forbidden_notification = [
        ".notification.extras",
        "Notification.EXTRA_TEXT",
        "Notification.EXTRA_TITLE",
        "bigText",
        "tickerText",
    ]
    for token in forbidden_notification:
        if token in notification_service:
            fail(f"HabitGuardNotificationListenerService.kt: forbidden notification body token `{token}` found", failures)


def check_release_logging(failures: list[str]) -> None:
    sensitive_terms = [
        "usage",
        "notification",
        "token",
        "bearer",
        "rawText",
        "packageName",
        "appName",
    ]
    release_scopes = [ROOT / "app/src/main", ROOT / "app/src/release"]
    for scope in release_scopes:
        if not scope.exists():
            continue
        for path in scope.rglob("*.kt"):
            text = path.read_text(encoding="utf-8", errors="ignore")
            for line_number, line in enumerate(text.splitlines(), start=1):
                if "Log." not in line and "println(" not in line:
                    continue
                if any(term.lower() in line.lower() for term in sensitive_terms):
                    fail(
                        f"{path.relative_to(ROOT)}:{line_number}: release/main source logs sensitive usage/token/app data",
                        failures,
                    )


def main() -> int:
    failures: list[str] = []
    check_manifest(failures)
    check_network_security(failures)
    check_backup(failures)
    check_sensitive_sources(failures)
    check_accessibility_and_notifications(failures)
    check_release_logging(failures)

    if failures:
        print("Security check failed:")
        for item in failures:
            print(f"- {item}")
        return 1

    print("Security check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
