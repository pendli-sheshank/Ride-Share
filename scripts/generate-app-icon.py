#!/usr/bin/env python3
"""Render every app-icon asset for both platforms from one source artwork.

Replaces `iosApp/generate-app-icons.py`, which drew a flat two-colour car glyph in code.
The icon is now real artwork (`assets/app-icon-source.png`), so this resamples it instead
of drawing it.

Deliberately dependency-free, for the same reason the script it replaces was: Pillow is not
installed in CI or in the Claude Code container. PNG encoding is ~15 lines with zlib, and
downsampling is an area average over a summed-area table, which is O(1) per output pixel and
gives a clean result at every size.

Three things this does that a plain resize would not:

1. **The white margin becomes navy.** The source is a rounded-square badge floating on white.
   Both platforms mask icons to their own corner shape, so shipping the white would put a
   white frame around the badge with the mask cutting through it. A flood fill inward from
   the four corners replaces the connected white region with the badge's own background
   navy, leaving the artwork edge to edge. It is a flood fill rather than a colour swap so
   nothing light *inside* the artwork — the wheels, the neon — is touched.
2. **iOS gets no alpha channel.** Apple rejects an App Store icon that has one.
3. **Android's adaptive foreground is inset to the safe zone.** An adaptive icon's outer
   ~34% can be cropped by the launcher's mask, and this artwork is a self-contained badge
   with a glowing border — cropping it would slice the border off. The badge is scaled into
   the 66% safe zone over a solid navy background layer instead, so a circular launcher
   shows the whole badge inside a navy circle rather than a fragment of one.

Run from anywhere:  python3 scripts/generate-app-icon.py
Idempotent — safe to re-run; it overwrites.
"""

from __future__ import annotations

import array
import collections
import json
import pathlib
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "assets/app-icon-source.png"
ICONSET = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
RES = ROOT / "app/src/main/res"

# Sampled from the badge's own interior. Everything the artwork does not cover becomes this,
# so the corners a launcher rounds off are the icon's background rather than white.
NAVY = (0x09, 0x1B, 0x4E)

# A pixel is "margin" if every channel is at least this bright. The badge's lightest interior
# region is the silver wheel rim, which is well below it.
WHITE_FLOOR = 232

# Android density buckets: legacy launcher size, then the adaptive layer size (108dp).
DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}

# An adaptive icon's safe zone is the middle 72dp of its 108dp canvas.
SAFE_ZONE = 72 / 108


# --- PNG -------------------------------------------------------------------------------


def read_png(path: pathlib.Path) -> tuple[int, int, bytearray]:
    """Decode an 8-bit non-interlaced truecolour PNG to (w, h, RGB bytes)."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path} is not a PNG")

    pos, idat = 8, bytearray()
    width = height = channels = 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if depth != 8 or interlace or ctype not in (2, 6):
                raise SystemExit(
                    f"{path}: need an 8-bit non-interlaced RGB/RGBA PNG "
                    f"(got depth={depth} colour-type={ctype} interlace={interlace})"
                )
            channels = 3 if ctype == 2 else 4
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    pixels = bytearray(width * height * 3)
    previous = bytearray(stride)
    offset = 0

    for y in range(height):
        filter_type = raw[offset]
        offset += 1
        line = bytearray(raw[offset:offset + stride])
        offset += stride
        _unfilter(filter_type, line, previous, channels, stride)
        previous = line
        # Drop alpha by compositing onto the margin colour; the source has none today, but a
        # re-export with one should not silently produce black fringes.
        row = y * width * 3
        if channels == 3:
            pixels[row:row + width * 3] = line
        else:
            for x in range(width):
                r, g, b, a = line[x * 4:x * 4 + 4]
                i = row + x * 3
                pixels[i] = (r * a + NAVY[0] * (255 - a)) // 255
                pixels[i + 1] = (g * a + NAVY[1] * (255 - a)) // 255
                pixels[i + 2] = (b * a + NAVY[2] * (255 - a)) // 255

    return width, height, pixels


def _unfilter(filter_type: int, line: bytearray, previous: bytearray, bpp: int, stride: int) -> None:
    if filter_type == 0:
        return
    if filter_type == 1:
        for i in range(bpp, stride):
            line[i] = (line[i] + line[i - bpp]) & 255
    elif filter_type == 2:
        for i in range(stride):
            line[i] = (line[i] + previous[i]) & 255
    elif filter_type == 3:
        for i in range(stride):
            left = line[i - bpp] if i >= bpp else 0
            line[i] = (line[i] + ((left + previous[i]) >> 1)) & 255
    elif filter_type == 4:
        for i in range(stride):
            left = line[i - bpp] if i >= bpp else 0
            up_left = previous[i - bpp] if i >= bpp else 0
            line[i] = (line[i] + _paeth(left, previous[i], up_left)) & 255
    else:
        raise SystemExit(f"unknown PNG filter type {filter_type}")


def _paeth(a: int, b: int, c: int) -> int:
    pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def write_png(path: pathlib.Path, size: int, pixels: bytes, alpha: bool) -> None:
    """Write `pixels` as an 8-bit PNG — truecolour, or truecolour+alpha when `alpha`."""
    channels = 4 if alpha else 3
    stride = size * channels
    raw = bytearray()
    for y in range(size):
        raw.append(0)  # filter type 0 (None) per scanline
        raw += pixels[y * stride:(y + 1) * stride]

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    blob = b"\x89PNG\r\n\x1a\n"
    blob += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6 if alpha else 2, 0, 0, 0))
    blob += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    blob += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(blob)


# --- Preparing the source --------------------------------------------------------------


def fill_margin(width: int, height: int, pixels: bytearray) -> int:
    """Flood-fill the white surround inward from the corners. Returns pixels changed."""
    seen = bytearray(width * height)
    queue = collections.deque()
    for corner in ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)):
        queue.append(corner)

    changed = 0
    while queue:
        x, y = queue.popleft()
        if x < 0 or y < 0 or x >= width or y >= height:
            continue
        flat = y * width + x
        if seen[flat]:
            continue
        i = flat * 3
        if pixels[i] < WHITE_FLOOR or pixels[i + 1] < WHITE_FLOOR or pixels[i + 2] < WHITE_FLOOR:
            continue
        seen[flat] = 1
        pixels[i], pixels[i + 1], pixels[i + 2] = NAVY
        changed += 1
        queue.extend(((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))
    return changed


def content_box(width: int, height: int, pixels: bytearray) -> tuple[int, int, int, int]:
    """The square bounding box of everything that is not the margin colour, plus padding."""
    left, top, right, bottom = width, height, -1, -1
    for y in range(height):
        row = y * width * 3
        for x in range(width):
            i = row + x * 3
            if (pixels[i], pixels[i + 1], pixels[i + 2]) != NAVY:
                if x < left:
                    left = x
                if x > right:
                    right = x
                if y < top:
                    top = y
                if y > bottom:
                    bottom = y
    if right < 0:
        raise SystemExit("the source artwork is entirely background")

    # A little air around the badge, then squared off around its centre. Without the padding
    # the glow on the badge's border would be flush with the icon edge.
    span = max(right - left, bottom - top) + 1
    span = int(span * 1.04)
    cx, cy = (left + right) // 2, (top + bottom) // 2
    half = span // 2
    return cx - half, cy - half, span, span


class Sampler:
    """Area-average resampling over a summed-area table: O(1) per output pixel.

    Built once and reused for all 25-odd sizes. Reading outside the source returns the margin
    colour, so the square crop can extend past the artwork without a black edge.
    """

    def __init__(self, width: int, height: int, pixels: bytearray) -> None:
        self.width, self.height = width, height
        self.sums = []
        for channel in range(3):
            table = array.array("q", bytes(8 * (width + 1) * (height + 1)))
            for y in range(height):
                row_total = 0
                base = (y + 1) * (width + 1)
                above = y * (width + 1)
                for x in range(width):
                    row_total += pixels[(y * width + x) * 3 + channel]
                    table[base + x + 1] = table[above + x + 1] + row_total
            self.sums.append(table)

    def _region(self, channel: int, x0: int, y0: int, x1: int, y1: int) -> int:
        table, stride = self.sums[channel], self.width + 1
        return (table[y1 * stride + x1] - table[y0 * stride + x1]
                - table[y1 * stride + x0] + table[y0 * stride + x0])

    def average(self, x0: float, y0: float, x1: float, y1: float) -> tuple[int, int, int]:
        ix0, iy0 = max(0, int(x0)), max(0, int(y0))
        ix1, iy1 = min(self.width, max(ix0 + 1, int(round(x1)))), min(self.height, max(iy0 + 1, int(round(y1))))
        if ix0 >= self.width or iy0 >= self.height or ix1 <= 0 or iy1 <= 0:
            return NAVY
        count = (ix1 - ix0) * (iy1 - iy0)
        return tuple(self._region(c, ix0, iy0, ix1, iy1) // count for c in range(3))

    def render(self, size: int, box: tuple[int, int, int, int], inset: float, alpha: bool) -> bytes:
        """Resample `box` into a `size` square, the artwork occupying `inset` of the edge."""
        bx, by, bw, bh = box
        drawn = max(1, int(round(size * inset)))
        origin = (size - drawn) // 2
        channels = 4 if alpha else 3
        out = bytearray(size * size * channels)
        step_x, step_y = bw / drawn, bh / drawn

        for y in range(size):
            for x in range(size):
                i = (y * size + x) * channels
                inside = origin <= x < origin + drawn and origin <= y < origin + drawn
                if not inside:
                    if alpha:
                        continue  # left fully transparent
                    out[i], out[i + 1], out[i + 2] = NAVY
                    continue
                sx, sy = x - origin, y - origin
                r, g, b = self.average(
                    bx + sx * step_x, by + sy * step_y,
                    bx + (sx + 1) * step_x, by + (sy + 1) * step_y,
                )
                out[i], out[i + 1], out[i + 2] = r, g, b
                if alpha:
                    out[i + 3] = 255
        return bytes(out)


def circular_mask(size: int, pixels: bytes) -> bytes:
    """RGB in, RGBA out, masked to the inscribed circle with a one-pixel soft edge."""
    out = bytearray(size * size * 4)
    centre = (size - 1) / 2
    radius = size / 2
    for y in range(size):
        for x in range(size):
            dx, dy = x - centre, y - centre
            distance = (dx * dx + dy * dy) ** 0.5
            coverage = min(1.0, max(0.0, radius - distance))
            src, dst = (y * size + x) * 3, (y * size + x) * 4
            out[dst:dst + 3] = pixels[src:src + 3]
            out[dst + 3] = int(round(coverage * 255))
    return bytes(out)


# --- Targets ---------------------------------------------------------------------------


def ios_sizes() -> dict[str, int]:
    manifest = json.loads((ICONSET / "Contents.json").read_text())
    sizes = {}
    for image in manifest["images"]:
        filename = image.get("filename")
        if not filename:
            continue
        edge = float(image["size"].split("x")[0]) * float(image["scale"].rstrip("x"))
        sizes[filename] = int(round(edge))
    return sizes


def main() -> int:
    if not SOURCE.exists():
        raise SystemExit(f"missing source artwork: {SOURCE.relative_to(ROOT)}")

    width, height, pixels = read_png(SOURCE)
    filled = fill_margin(width, height, pixels)
    box = content_box(width, height, pixels)
    print(f"source {width}x{height}, margin repainted on {filled:,} px, crop {box[2]}x{box[3]}")

    sampler = Sampler(width, height, pixels)

    for filename, size in sorted(ios_sizes().items(), key=lambda kv: kv[1]):
        write_png(ICONSET / filename, size, sampler.render(size, box, 1.0, alpha=False), alpha=False)
        print(f"  ios     {filename:<32} {size}px")

    for bucket, (legacy, adaptive) in DENSITIES.items():
        square = sampler.render(legacy, box, 1.0, alpha=False)
        write_png(RES / f"mipmap-{bucket}/ic_launcher.png", legacy, square, alpha=False)
        write_png(RES / f"mipmap-{bucket}/ic_launcher_round.png", legacy,
                  circular_mask(legacy, square), alpha=True)
        write_png(RES / f"drawable-{bucket}/ic_launcher_fg.png", adaptive,
                  sampler.render(adaptive, box, SAFE_ZONE, alpha=True), alpha=True)
        print(f"  android {bucket:<8} launcher {legacy}px, adaptive foreground {adaptive}px")

    print("\nRegenerated the app icon for both platforms.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
