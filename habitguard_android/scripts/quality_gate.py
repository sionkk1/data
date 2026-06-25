#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def run(command: list[str]) -> None:
    print(f"\n$ {' '.join(command)}", flush=True)
    completed = subprocess.run(command, cwd=ROOT)
    if completed.returncode != 0:
        raise SystemExit(completed.returncode)


def check_formatting() -> None:
    checked_suffixes = {".kt", ".kts", ".py", ".md", ".yml", ".yaml", ".xml", ".json"}
    ignored_dirs = {
        ".git",
        ".gradle",
        ".idea",
        "build",
        ".venv",
        "__pycache__",
    }
    failures: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix not in checked_suffixes:
            continue
        if any(part in ignored_dirs for part in path.parts):
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        for line_number, line in enumerate(text.splitlines(), start=1):
            if line.rstrip() != line:
                failures.append(f"{path.relative_to(ROOT)}:{line_number}: trailing whitespace")
            if "\t" in line and path.suffix in {".kt", ".kts", ".py", ".yml", ".yaml"}:
                failures.append(f"{path.relative_to(ROOT)}:{line_number}: tab indentation")
    if failures:
        print("Formatting check failed:")
        for item in failures[:50]:
            print(f"- {item}")
        if len(failures) > 50:
            print(f"- ... {len(failures) - 50} more")
        raise SystemExit(1)
    print("Formatting check passed.")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run HabitGuard local quality checks sequentially.")
    parser.add_argument("--skip-android", action="store_true", help="Skip Gradle checks.")
    parser.add_argument("--skip-python", action="store_true", help="Skip Python ML tests.")
    parser.add_argument("--skip-format", action="store_true", help="Skip whitespace/static formatting check.")
    parser.add_argument("--include-connected", action="store_true", help="Run connected Android instrumentation tests.")
    args = parser.parse_args(argv)

    if not args.skip_format:
        check_formatting()

    run([sys.executable, "scripts/security_check.py"])

    if not args.skip_python:
        run([sys.executable, "-m", "unittest", "tests\\test_train_from_phone_csv.py"])

    if not args.skip_android:
        gradlew = "gradlew.bat" if sys.platform.startswith("win") else "./gradlew"
        run([gradlew, "--no-daemon", ":app:testDebugUnitTest"])
        run([gradlew, "--no-daemon", ":app:assembleDebugAndroidTest"])
        run([gradlew, "--no-daemon", ":app:lintDebug"])
        run([gradlew, "--no-daemon", ":app:assembleDebug"])
        if args.include_connected:
            run([gradlew, "--no-daemon", ":app:connectedDebugAndroidTest"])

    print("\nQuality gate passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
