#!/usr/bin/env python3
"""Measure the Jarvis colour table from ref5 (Fold7 open) pixel by pixel.

Usage: python3 tools/measure_ref5_colors.py [/workspace/jarvis-design-referenz/ref5-fold7-offen.png]
The reference image itself is not checked in. Coordinates are full-resolution pixels of the
original (1448 x 1086). Methods per box:
  median - per-channel median of every pixel in the box (flat areas)
  dark   - median of the darkest 30 % (panel fill behind text/icons)
  peak   - median of the brightest 10 % (thin strokes: text, 1 px rims, icons, glow core)
  violet - only violet pixels (B > G + 40, R > G + 20), brightest-saturated 10 %
  chroma - median of the 10 % with the highest saturation (coloured strokes with white-hot centres)
"""
import sys
from statistics import median
from PIL import Image

SAMPLES = [
    # key, method, (x0, y0, x1, y1)
    ("bg.top", "median", (268, 270, 278, 290)),
    ("bg.mid", "median", (268, 470, 278, 490)),
    ("bg.low", "median", (268, 590, 278, 610)),
    ("bg.violetAmbient", "median", (268, 875, 278, 885)),
    ("glass.fill", "dark", (585, 830, 660, 880)),
    ("glass.rim", "peak", (330, 748, 520, 756)),
    ("cyan.icon", "peak", (110, 210, 140, 235)),
    ("cyan.text", "peak", (310, 395, 440, 410)),
    ("violet.icon", "violet", (320, 668, 350, 700)),
    ("violet.hair", "violet", (540, 180, 640, 240)),
    ("violet.pillRim", "violet", (880, 105, 915, 140)),
    ("text.primary", "peak", (375, 577, 415, 595)),
    ("text.secondary", "peak", (380, 603, 480, 625)),
    ("tile.fill", "dark", (440, 590, 500, 640)),
    ("tile.rim", "peak", (360, 566, 440, 572)),
    ("tile.icon", "peak", (315, 580, 350, 610)),
    ("button.fill.active", "dark", (180, 212, 255, 235)),
    ("button.rim.active", "peak", (120, 198, 250, 204)),
    ("button.fill", "dark", (180, 273, 255, 295)),
    ("pill.fill", "dark", (830, 115, 870, 132)),
    ("pill.rim", "peak", (775, 108, 870, 113)),
    ("status.ok", "peak", (955, 115, 970, 132)),
    ("status.okCheck", "peak", (1295, 245, 1315, 262)),
    ("glow.core", "peak", (92, 205, 100, 240)),
    ("glow.halo", "median", (82, 205, 90, 240)),
]


def measure(img, method, box):
    px = list(img.crop(box).get_flattened_data()) if hasattr(img, "get_flattened_data") else list(img.crop(box).getdata())
    if method == "violet":
        px = [c for c in px if c[2] > c[1] + 40 and c[0] > c[1] + 20]
        px.sort(key=lambda c: (max(c) - min(c)) * max(c))
        return tuple(int(median(c[i] for c in px[-max(1, len(px) // 10):])) for i in range(3))
    if method == "chroma":
        px.sort(key=lambda c: max(c) - min(c))
        return tuple(int(median(c[i] for c in px[-max(1, len(px) // 10):])) for i in range(3))
    px.sort(key=lambda c: 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2])
    n = len(px)
    if method == "dark":
        px = px[: max(1, n * 3 // 10)]
    elif method == "peak":
        px = px[-max(1, n // 10):]
    return tuple(int(median(c[i] for c in px)) for i in range(3))


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "/workspace/jarvis-design-referenz/ref5-fold7-offen.png"
    img = Image.open(path).convert("RGB")
    print(f"# {path} {img.size[0]}x{img.size[1]}")
    for key, method, box in SAMPLES:
        r, g, b = measure(img, method, box)
        print(f"{key:20s} #{r:02X}{g:02X}{b:02X}  {method:6s} {box}")


if __name__ == "__main__":
    main()
