#!/usr/bin/env python3
"""Preflight checks on iosApp/Info.plist that App Store validation would otherwise catch.

The pattern this exists for is in the release skill's log more than once: a bundle problem that
`xcodebuild` is perfectly happy with, and that only surfaces at `altool --validate-app` — after a
TestFlight build number has been consumed and cannot be reused. The icon-set bug cost build 12
exactly this way.

Run cost is milliseconds. Add a check here whenever a validation failure teaches us a new one.
"""
from __future__ import annotations

import plistlib
import sys
from pathlib import Path

# The deployment target is arm64-only; every 64-bit iPhone reports arm64, and a 32-bit-only
# requirement on a 64-bit-only binary is an "Invalid Bundle" rejection.
FORBIDDEN_CAPABILITIES = {"armv7", "armv7s"}


def main() -> int:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "iosApp/iosApp/Info.plist")
    if not path.is_file():
        print(f"FAIL: {path} does not exist")
        return 1

    with path.open("rb") as handle:
        try:
            plist = plistlib.load(handle)
        except Exception as error:  # noqa: BLE001 - report any malformed plist the same way
            print(f"FAIL: {path} is not a readable plist: {error}")
            return 1

    problems: list[str] = []

    capabilities = set(plist.get("UIRequiredDeviceCapabilities") or [])
    forbidden = capabilities & FORBIDDEN_CAPABILITIES
    if forbidden:
        problems.append(
            f"UIRequiredDeviceCapabilities contains {sorted(forbidden)}, but the app is built "
            "arm64-only for iOS 16+. App Store validation rejects the bundle."
        )

    if "ITSAppUsesNonExemptEncryption" not in plist:
        problems.append(
            "ITSAppUsesNonExemptEncryption is missing, so every TestFlight upload stalls on the "
            "manual export-compliance question in an otherwise automated pipeline."
        )

    for key in ("CFBundleIdentifier", "CFBundleShortVersionString", "CFBundleVersion"):
        if not plist.get(key):
            problems.append(f"{key} is missing or empty.")

    if problems:
        print(f"FAIL: {path}")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print(f"OK: {path} passes {3 + len(FORBIDDEN_CAPABILITIES)} preflight checks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
