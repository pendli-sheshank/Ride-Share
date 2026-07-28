#!/usr/bin/env python3
"""Render every PNG declared by AppIcon.appiconset/Contents.json.

The catalog listed 18 filenames and contained none of them. `actool` treats that as a
warning, not an error, so the build stayed green and App Store Connect rejected the upload
instead — four of the five validation errors on build 12 were this one omission:

    Missing required icon file ... '120x120' / '152x152' / '167x167'
    Missing Info.plist value ... 'CFBundleIconName'

CFBundleIconName is emitted by actool itself once it actually compiles an app icon, so
producing the images fixes that key too. Nothing needs to set it by hand.

Deliberately dependency-free: Pillow is not installed in CI or in the Claude Code container,
and adding an image library to render a flat two-colour glyph is not worth the install. PNG
encoding is ~15 lines with zlib, and shapes are rasterised by supersampling.

Icons are written as RGB with no alpha channel. Apple rejects an App Store icon that has
one, and an opaque icon is correct for every other slot as well.

Run from anywhere:  python3 iosApp/generate-app-icons.py
Idempotent — safe to re-run; it overwrites.
"""

from __future__ import annotations

import json
import math
import pathlib
import struct
import sys
import zlib

ICONSET = pathlib.Path(__file__).parent / "iosApp/Assets.xcassets/AppIcon.appiconset"

# Brand colours. The blue matches the `.blue` accent and `car.fill` glyph the SwiftUI
# views already use, so the icon and the first screen agree.
TOP = (0x30, 0x7A, 0xF5)
BOTTOM = (0x16, 0x3E, 0xB0)
WHITE = (0xFF, 0xFF, 0xFF)


def write_png(path: pathlib.Path, size: int, pixels: bytearray) -> None:
    """Write `pixels` (RGB, size*size*3 bytes) as an 8-bit truecolour PNG."""
    stride = size * 3
    raw = bytearray()
    for y in range(size):
        raw.append(0)  # filter type 0 (None) for each scanline
        raw += pixels[y * stride:(y + 1) * stride]

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)


def in_rrect(x: float, y: float, x0: float, y0: float, x1: float, y1: float, r: float) -> bool:
    """Point-in-rounded-rectangle: clamp to the inset rect, then test radius."""
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= r * r


def in_circle(x: float, y: float, cx: float, cy: float, r: float) -> bool:
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= r * r


def shade(x: float, y: float) -> tuple[int, int, int]:
    """Colour of one sample in normalised icon space (0..1 on both axes).

    A side-on car: body, cabin, two wheels with cut-out hubs. Kept to a handful of large
    shapes on purpose — the smallest slot renders at 20x20, where anything finer is mud.
    """
    # Wheels first: they overlap the body and their hubs punch back through to the
    # background colour, so they have to win.
    for wx in (0.325, 0.675):
        if in_circle(x, y, wx, 0.660, 0.078):
            gy = int(BOTTOM[0] + (TOP[0] - BOTTOM[0]) * (1 - y))
            if in_circle(x, y, wx, 0.660, 0.033):
                return (gy, int(0x3E + (0x7A - 0x3E) * (1 - y)), int(0xB0 + (0xF5 - 0xB0) * (1 - y)))
            return WHITE

    if in_rrect(x, y, 0.180, 0.505, 0.820, 0.665, 0.058):   # body
        return WHITE
    if in_rrect(x, y, 0.305, 0.350, 0.695, 0.530, 0.052):   # cabin
        return WHITE

    t = 1.0 - y
    return (
        int(BOTTOM[0] + (TOP[0] - BOTTOM[0]) * t),
        int(BOTTOM[1] + (TOP[1] - BOTTOM[1]) * t),
        int(BOTTOM[2] + (TOP[2] - BOTTOM[2]) * t),
    )


def render(size: int) -> bytearray:
    """Rasterise one icon, supersampled for antialiasing."""
    # Cap total samples so the 1024 slot stays quick while tiny slots still get 4x4.
    ss = max(2, min(4, math.ceil(512 / size)))
    inv = 1.0 / (size * ss)
    n = ss * ss
    out = bytearray(size * size * 3)
    i = 0
    for py in range(size):
        for px in range(size):
            r = g = b = 0
            for sy in range(ss):
                fy = (py * ss + sy + 0.5) * inv
                for sx in range(ss):
                    cr, cg, cb = shade((px * ss + sx + 0.5) * inv, fy)
                    r += cr
                    g += cg
                    b += cb
            out[i] = r // n
            out[i + 1] = g // n
            out[i + 2] = b // n
            i += 3
    return out


def main() -> int:
    contents = ICONSET / "Contents.json"
    if not contents.is_file():
        print(f"error: {contents} not found", file=sys.stderr)
        return 1

    images = json.loads(contents.read_text())["images"]
    seen: dict[str, int] = {}
    for entry in images:
        filename = entry.get("filename")
        if not filename:
            continue
        side = float(entry["size"].split("x")[0])
        scale = int(entry["scale"].rstrip("x"))
        seen[filename] = round(side * scale)

    # Several slots share a pixel size (60@2x and 40@3x are both 120). Render each
    # distinct size once and copy, rather than rasterising the same image twice.
    cache: dict[int, bytearray] = {}
    for filename, px in sorted(seen.items(), key=lambda kv: kv[1]):
        if px not in cache:
            cache[px] = render(px)
        write_png(ICONSET / filename, px, cache[px])
        print(f"  {filename:34s} {px}x{px}")

    print(f"{len(seen)} icons written to {ICONSET}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
