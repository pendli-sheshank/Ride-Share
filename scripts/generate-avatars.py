#!/usr/bin/env python3
"""
Generate the profile avatars for both platforms from one description.

The twelve avatars are flat vector portraits covering a range of genders, ages, skin tones and
hair. They are written twice — as Android VectorDrawables and as iOS SVGs — but authored once,
here, because the geometry is identical and the two formats share SVG path syntax. Editing the
art means editing AVATARS below and re-running this script; editing either output by hand means
the platforms drift, which is the whole thing this avoids.

    python3 scripts/generate-avatars.py

Outputs (both overwritten wholesale, both committed):
    app/src/main/res/drawable/avatar_NN.xml
    iosApp/iosApp/Assets.xcassets/AvatarNN.imageset/{AvatarNN.svg,Contents.json}

Neither output needs registering anywhere. Android resolves `R.drawable.avatar_NN` from the
folder, and the iOS asset catalogue is a single folder reference in the generated Xcode project,
so no pbxproj regeneration is required — unlike adding a .swift file.

Keep the key list in step with `SplitCruiserAvatars.ALL` in
`shared/src/commonMain/kotlin/com/splitcruiser/app/ui/theme/Avatars.kt`; that object is what the
two UIs actually iterate.
"""

import json
import pathlib

# Everything is drawn in a 96x96 box and clipped to a circle by the UI, so the art only has to
# fill the square — no per-platform rounding.
SIZE = 96

REPO = pathlib.Path(__file__).resolve().parent.parent
ANDROID_DRAWABLE = REPO / "app/src/main/res/drawable"
IOS_ASSETS = REPO / "iosApp/iosApp/Assets.xcassets"

# Shared palette. Skin and hair tones are deliberately a spread rather than a gradient of one
# hue; the brand tokens are not used here because these are illustration colours, not UI colours.
SKIN = {
    "light": "#F2C79B",
    "tan": "#D9A066",
    "olive": "#C68642",
    "brown": "#8D5524",
    "deep": "#5C3317",
}
HAIR = {
    "black": "#2B2118",
    "brown": "#5A3A22",
    "auburn": "#8C4A2F",
    "blonde": "#D8AE5E",
    "grey": "#B9BCC4",
    "white": "#E3E6EC",
}

# Backgrounds cycle so a row of avatars reads as a set rather than a gradient.
BACKGROUNDS = ["#D1E4FF", "#FFE0E6", "#DCF3E4", "#FFF0D6", "#E8E0FF", "#D9F1F5"]


def head(skin):
    """Face, ears and neck — the same base under every hairstyle."""
    return [
        # neck
        (f"M40 62 h16 v12 q-8 4 -16 0 z", skin),
        # ears
        (f"M28 44 a4 5 0 0 0 0 10 z", skin),
        (f"M68 44 a4 5 0 0 1 0 10 z", skin),
        # face
        (f"M48 20 c12 0 20 9 20 21 c0 13 -9 23 -20 23 c-11 0 -20 -10 -20 -23 c0 -12 8 -21 20 -21 z", skin),
    ]


def shoulders(color):
    return [(f"M22 96 c0 -14 11 -22 26 -22 c15 0 26 8 26 22 z", color)]


# Each avatar states the colour of every layer it draws. An earlier version inferred the colour
# from whether `hair` or `scarf` was set, which quietly painted avatar 06's turban in hair-black.
# Layers are (path, colour); `behind` sits under the face, `front` over it.
AVATARS = [
    # 01 young woman, long dark hair
    dict(skin=SKIN["light"], top="#4F7CC4",
         behind=[("M24 46 c0 -18 10 -30 24 -30 c14 0 24 12 24 30 v26 h-8 v-26 c0 -12 -6 -18 -16 -18 c-10 0 -16 6 -16 18 v26 h-8 z", HAIR["black"])],
         front=[("M28 38 c2 -14 10 -20 20 -20 c10 0 18 6 20 20 c-6 -8 -12 -11 -20 -11 c-8 0 -14 3 -20 11 z", HAIR["black"])]),
    # 02 young man, short hair
    dict(skin=SKIN["tan"], top="#3E8E7E",
         behind=[],
         front=[("M27 40 c0 -15 9 -23 21 -23 c12 0 21 8 21 23 c-4 -9 -11 -13 -21 -13 c-10 0 -17 4 -21 13 z", HAIR["brown"])]),
    # 03 woman, curly hair
    dict(skin=SKIN["olive"], top="#C2557A",
         behind=[("M22 44 a10 10 0 0 1 6 -18 a11 11 0 0 1 20 -8 a11 11 0 0 1 20 8 a10 10 0 0 1 6 18 a9 9 0 0 1 -6 16 v-14 c0 -13 -8 -20 -20 -20 c-12 0 -20 7 -20 20 v14 a9 9 0 0 1 -6 -16 z", HAIR["auburn"])],
         front=[]),
    # 04 man, beard
    dict(skin=SKIN["olive"], top="#5D6470",
         behind=[],
         front=[("M27 40 c0 -15 9 -23 21 -23 c12 0 21 8 21 23 c-4 -9 -11 -13 -21 -13 c-10 0 -17 4 -21 13 z", HAIR["black"]),
                ("M30 48 c0 14 8 22 18 22 c10 0 18 -8 18 -22 c0 10 -6 14 -18 14 c-12 0 -18 -4 -18 -14 z", HAIR["black"])]),
    # 05 woman, headscarf
    dict(skin=SKIN["tan"], top="#8E6FC7",
         behind=[("M22 50 c0 -20 11 -34 26 -34 c15 0 26 14 26 34 c0 12 -4 20 -10 24 h-32 c-6 -4 -10 -12 -10 -24 z", "#B892E8")],
         front=[("M30 34 c4 -8 10 -12 18 -12 c8 0 14 4 18 12 c-5 -5 -11 -7 -18 -7 c-7 0 -13 2 -18 7 z", "#9E77D4")]),
    # 06 man, turban
    dict(skin=SKIN["brown"], top="#4F7CC4",
         behind=[],
         front=[("M26 38 c0 -16 10 -24 22 -24 c12 0 22 8 22 24 c-3 -4 -6 -6 -10 -7 c-4 -6 -12 -9 -12 -9 c0 0 -8 3 -12 9 c-4 1 -7 3 -10 7 z", "#D98E3A")]),
    # 07 older woman, grey hair and glasses
    dict(skin=SKIN["light"], top="#6C8AA6",
         behind=[("M24 46 c0 -18 10 -28 24 -28 c14 0 24 10 24 28 c0 8 -3 12 -6 14 v-16 c0 -12 -7 -18 -18 -18 c-11 0 -18 6 -18 18 v16 c-3 -2 -6 -6 -6 -14 z", HAIR["grey"])],
         front=[], glasses=True),
    # 08 older man, grey beard
    dict(skin=SKIN["light"], top="#7A8290",
         behind=[],
         front=[("M28 40 c0 -14 9 -22 20 -22 c11 0 20 8 20 22 c-5 -8 -11 -12 -20 -12 c-9 0 -15 4 -20 12 z", HAIR["white"]),
                ("M30 48 c0 15 8 24 18 24 c10 0 18 -9 18 -24 c0 11 -6 16 -18 16 c-12 0 -18 -5 -18 -16 z", HAIR["white"])]),
    # 09 teenage girl, ponytail
    dict(skin=SKIN["light"], top="#E0A03C",
         behind=[("M26 44 c0 -17 10 -27 22 -27 c12 0 22 10 22 27 v10 h-6 v-10 c0 -12 -6 -18 -16 -18 c-10 0 -16 6 -16 18 v10 h-6 z", HAIR["blonde"]),
                 ("M68 40 c8 4 10 14 6 22 c-2 4 -6 6 -8 4 c-2 -2 0 -6 1 -10 c1 -6 0 -12 -3 -16 z", HAIR["blonde"])],
         front=[("M29 38 c3 -12 10 -18 19 -18 c9 0 16 6 19 18 c-6 -7 -12 -10 -19 -10 c-7 0 -13 3 -19 10 z", HAIR["blonde"])]),
    # 10 teenage boy, cap
    dict(skin=SKIN["tan"], top="#3E8E7E",
         behind=[],
         front=[("M28 40 c0 -14 9 -22 20 -22 c11 0 20 8 20 22 c-5 -8 -11 -12 -20 -12 c-9 0 -15 4 -20 12 z", HAIR["black"])],
         cap="#C2453F"),
    # 11 woman, short afro
    dict(skin=SKIN["deep"], top="#D98E3A",
         behind=[("M24 42 a14 14 0 0 1 48 0 c0 6 -2 10 -4 12 v-8 c0 -13 -8 -20 -20 -20 c-12 0 -20 7 -20 20 v8 c-2 -2 -4 -6 -4 -12 z", HAIR["black"])],
         front=[]),
    # 12 man, locs
    dict(skin=SKIN["brown"], top="#5D6470",
         behind=[("M26 42 c0 -16 10 -25 22 -25 c12 0 22 9 22 25 v4 h-4 v10 h-4 v-14 c0 -12 -6 -18 -14 -18 c-8 0 -14 6 -14 18 v14 h-4 v-10 h-4 z", HAIR["black"])],
         front=[]),
]

assert len(AVATARS) == 12, "SplitCruiserAvatars.ALL expects exactly twelve"


def paths_for(spec, background):
    """The full ordered (path, fill, stroke) list for one avatar."""
    out = [(f"M0 0 h{SIZE} v{SIZE} h-{SIZE} z", background, None)]
    out += [(d, colour, None) for d, colour in spec["behind"]]
    out += [(d, colour, None) for d, colour in head(spec["skin"])]
    out += [(d, colour, None) for d, colour in shoulders(spec["top"])]
    out += [(d, colour, None) for d, colour in spec["front"]]

    if spec.get("cap"):
        out.append(("M26 36 c0 -14 10 -21 22 -21 c12 0 22 7 22 21 z", spec["cap"], None))
        out.append(("M24 36 h48 v5 h-48 z", spec["cap"], None))

    if spec.get("glasses"):
        out.append(("M34 41 a6 5 0 1 0 12 0 a6 5 0 1 0 -12 0 z", None, "#3A3F47"))
        out.append(("M50 41 a6 5 0 1 0 12 0 a6 5 0 1 0 -12 0 z", None, "#3A3F47"))
        out.append(("M46 41 h4", None, "#3A3F47"))

    # Eyes and mouth last, so nothing paints over the face.
    out.append(("M40 41 a2.2 2.2 0 1 0 0.1 0 z", "#2B2118", None))
    out.append(("M56 41 a2.2 2.2 0 1 0 0.1 0 z", "#2B2118", None))
    out.append(("M43 52 q5 4 10 0", None, "#8A4B3C"))
    return out


ANDROID_TEMPLATE = """<!-- Generated by scripts/generate-avatars.py. Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{size}"
    android:viewportHeight="{size}">
{paths}
</vector>
"""

SVG_TEMPLATE = """<!-- Generated by scripts/generate-avatars.py. Do not edit by hand. -->
<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">
{paths}
</svg>
"""


def android_path(d, fill, stroke):
    bits = [f'        android:pathData="{d}"']
    if fill:
        bits.insert(0, f'        android:fillColor="{fill}"')
    if stroke:
        bits.append(f'        android:strokeColor="{stroke}"')
        bits.append('        android:strokeWidth="2"')
        bits.append('        android:strokeLineCap="round"')
    return "    <path\n" + "\n".join(bits) + " />"


def svg_path(d, fill, stroke):
    attrs = [f'd="{d}"']
    attrs.append(f'fill="{fill}"' if fill else 'fill="none"')
    if stroke:
        attrs.append(f'stroke="{stroke}" stroke-width="2" stroke-linecap="round"')
    return "    <path " + " ".join(attrs) + " />"


def main():
    ANDROID_DRAWABLE.mkdir(parents=True, exist_ok=True)
    IOS_ASSETS.mkdir(parents=True, exist_ok=True)

    for index, spec in enumerate(AVATARS, start=1):
        key = f"avatar_{index:02d}"
        ios_name = f"Avatar{index:02d}"
        background = BACKGROUNDS[(index - 1) % len(BACKGROUNDS)]
        paths = paths_for(spec, background)

        (ANDROID_DRAWABLE / f"{key}.xml").write_text(
            ANDROID_TEMPLATE.format(
                size=SIZE,
                paths="\n".join(android_path(*p) for p in paths),
            )
        )

        imageset = IOS_ASSETS / f"{ios_name}.imageset"
        imageset.mkdir(parents=True, exist_ok=True)
        (imageset / f"{ios_name}.svg").write_text(
            SVG_TEMPLATE.format(
                size=SIZE,
                paths="\n".join(svg_path(*p) for p in paths),
            )
        )
        (imageset / "Contents.json").write_text(
            json.dumps(
                {
                    "images": [{"filename": f"{ios_name}.svg", "idiom": "universal"}],
                    "info": {"author": "xcode", "version": 1},
                    "properties": {"preserves-vector-representation": True},
                },
                indent=2,
            )
            + "\n"
        )
        print(f"  {key}  ->  drawable/{key}.xml, {ios_name}.imageset")

    print(f"\nGenerated {len(AVATARS)} avatars for both platforms.")


if __name__ == "__main__":
    main()
