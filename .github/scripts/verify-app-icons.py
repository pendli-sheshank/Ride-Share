#!/usr/bin/env python3
"""Check that every image an .appiconset declares actually exists, at the right size.

Build 12 was rejected by App Store Connect with four icon errors. The asset catalog
declared 18 filenames and shipped none of them; `actool` downgrades that to a warning, so
the archive built, signed and exported clean and the failure only appeared at upload —
about eight minutes into the run, after a build number had been consumed.

This is the cheap version of that check. It reads Contents.json, confirms each declared
file is present, and parses the PNG header to confirm the pixel dimensions match
size x scale. It also rejects an alpha channel on the ios-marketing (1024) icon, which
Apple refuses separately.

Usage: verify-app-icons.py <path/to/AppIcon.appiconset>
"""

from __future__ import annotations

import json
import pathlib
import struct
import sys

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
# PNG colour types that carry an alpha channel.
ALPHA_TYPES = {4, 6}


def png_header(path: pathlib.Path) -> tuple[int, int, int]:
    """Return (width, height, colour_type) from a PNG's IHDR."""
    data = path.read_bytes()
    if data[:8] != PNG_MAGIC:
        raise ValueError("not a PNG")
    width, height = struct.unpack(">II", data[16:24])
    colour_type = data[25]
    return width, height, colour_type


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2

    iconset = pathlib.Path(argv[1])
    contents = iconset / "Contents.json"
    if not contents.is_file():
        print(f"error: {contents} not found", file=sys.stderr)
        return 1

    images = json.loads(contents.read_text())["images"]
    errors: list[str] = []
    checked = 0

    for entry in images:
        filename = entry.get("filename")
        if not filename:
            # A slot with no filename is an intentionally empty one; actool allows it.
            continue

        path = iconset / filename
        if not path.is_file():
            errors.append(f"{filename}: declared in Contents.json but missing from disk")
            continue

        side = float(entry["size"].split("x")[0])
        scale = int(entry["scale"].rstrip("x"))
        expected = round(side * scale)

        try:
            width, height, colour_type = png_header(path)
        except (ValueError, struct.error, IndexError) as exc:
            errors.append(f"{filename}: unreadable PNG ({exc})")
            continue

        if (width, height) != (expected, expected):
            errors.append(
                f"{filename}: is {width}x{height}, but {entry['size']} @{entry['scale']} "
                f"requires {expected}x{expected}"
            )

        if entry.get("idiom") == "ios-marketing" and colour_type in ALPHA_TYPES:
            errors.append(
                f"{filename}: App Store icon has an alpha channel; Apple rejects this"
            )

        checked += 1

    if errors:
        print(f"App icon check FAILED ({len(errors)} problem(s)) in {iconset}:")
        for err in errors:
            print(f"  - {err}")
        print("\nRegenerate with: python3 scripts/generate-app-icon.py")
        return 1

    print(f"App icon check passed: {checked} icons present and correctly sized.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
