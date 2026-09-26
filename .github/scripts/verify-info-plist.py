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
import re
import sys
from pathlib import Path

# The deployment target is arm64-only; every 64-bit iPhone reports arm64, and a 32-bit-only
# requirement on a 64-bit-only binary is an "Invalid Bundle" rejection.
FORBIDDEN_CAPABILITIES = {"armv7", "armv7s"}

# An AdMob *application* id: `ca-app-pub-<digits>~<digits>`. The tilde is what distinguishes it
# from an ad *unit* id, which uses a slash. Swapping the two is easy and the failure is a
# launch-time GADInvalidInitializationException, not a warning.
ADMOB_APP_ID = re.compile(r"^ca-app-pub-\d+~\d+$")


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

    # --- Ads -------------------------------------------------------------------------------
    # Deliberately *not* checked here: whether the application id is still Google's sample. This
    # script runs early in ios-release.yml, before the step that writes the real id from
    # ADMOB_IOS_APP_ID, so a "not the sample" check here would fail every release. That check
    # belongs in the workflow's own write step, next to the GoogleService-Info.plist one.
    app_id = plist.get("GADApplicationIdentifier")
    if not app_id:
        problems.append(
            "GADApplicationIdentifier is missing. With the Google Mobile Ads SDK linked, "
            "MobileAds.shared.start raises GADInvalidInitializationException on a missing id — "
            "the app dies on the feed rather than showing no ads."
        )
    elif not ADMOB_APP_ID.match(str(app_id)):
        problems.append(
            f"GADApplicationIdentifier {app_id!r} is not of the form ca-app-pub-<digits>~<digits>. "
            "An ad *unit* id (which uses a slash) in this key fails the same way a missing one does."
        )

    if not str(plist.get("NSUserTrackingUsageDescription") or "").strip():
        problems.append(
            "NSUserTrackingUsageDescription is missing or empty. The ATT prompt then never "
            "appears — iOS suppresses it silently — and App Store review rejects a binary that "
            "links an ads SDK without it."
        )

    skad = plist.get("SKAdNetworkItems")
    if not isinstance(skad, list) or not skad:
        problems.append(
            "SKAdNetworkItems is missing or empty, so install attribution is lost whenever the "
            "IDFA is unavailable. This never fails a build and no other check can see it. "
            "Refresh the list from https://developers.google.com/admob/ios/ios14."
        )
    else:
        malformed = [
            index
            for index, entry in enumerate(skad)
            if not isinstance(entry, dict)
            or not str(entry.get("SKAdNetworkIdentifier") or "").strip()
        ]
        if malformed:
            problems.append(
                f"SKAdNetworkItems entries at {malformed} are not dicts carrying a non-empty "
                "SKAdNetworkIdentifier."
            )

    if problems:
        print(f"FAIL: {path}")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print(f"OK: {path} passes {6 + len(FORBIDDEN_CAPABILITIES)} preflight checks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
