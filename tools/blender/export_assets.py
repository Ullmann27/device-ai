#!/usr/bin/env python3
"""
Post-process the Blender renders (tools/blender/continuum_scenes.py) into the packaged WebP assets.

  python3 tools/blender/export_assets.py <render-dir> app/src/main/assets/jarvis-reference/blender

orb: premultiplied bloom so the glow survives on a transparent background (alpha grows with the bloom light),
     1024 (Fold7 inner display) + 512 (phones).
bg:  darkened for panel contrast, soft vignette, light extra blur (depth haze), wide 1600x1200 + tall 900x1800.
tiles: 320x240, slightly defocused, lower third darkened for the label.
Writes manifest.json with size, sha256, render metrics and provenance. Needs Pillow (WebP) and numpy.
"""
import hashlib, json, os, sys
import numpy as np
from PIL import Image, ImageFilter

src, dst = sys.argv[1], sys.argv[2]
os.makedirs(dst, exist_ok=True)
metrics = json.load(open(os.path.join(src, "metrics.json")))
entries = []


def arr(img):
    return np.asarray(img, dtype=np.float32) / 255.0


def blur(a, radius):
    chans = [np.asarray(Image.fromarray(np.clip(a[..., i] * 255, 0, 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(radius)), dtype=np.float32) / 255.0 for i in range(a.shape[2])]
    return np.stack(chans, -1)


def save(img, name, kind, lossless=False, **extra):
    path = os.path.join(dst, name)
    if lossless:
        img.save(path, "WEBP", lossless=True, quality=100, method=6)
    else:
        img.save(path, "WEBP", quality=86, method=6, alpha_quality=92)
    data = open(path, "rb").read()
    entries.append({"asset": "jarvis-reference/blender/" + name, "kind": kind, "dimensions": list(img.size),
                    "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(), **extra})
    print(name, img.size, len(data))


# ------------------------------------------------------------------------------------------------------------- orb
rgba = arr(Image.open(os.path.join(src, "orb.png")).convert("RGBA"))
rgb, a = rgba[..., :3], rgba[..., 3:4]
pm = rgb * a
w = pm.shape[1]
bright = np.clip(pm - 0.55, 0, None)
bloom = 0.55 * blur(bright, w * 0.012) + 0.45 * blur(bright, w * 0.045)
pm2 = np.clip(pm + bloom * 1.0, 0, 1)
lum = (pm2 - pm) @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)
a2 = np.clip(a[..., 0] + lum * 1.6, 0, 1)[..., None]
straight = np.where(a2 > 1e-4, np.clip(pm2 / np.maximum(a2, 1e-4), 0, 1), 0)
out = Image.fromarray((np.concatenate([straight, a2], -1) * 255 + .5).astype(np.uint8), "RGBA")
for size in (1024, 512):
    save(out.resize((size, size), Image.LANCZOS), "orb-%d.webp" % size, "orb", metrics=metrics.get("orb"))

# ------------------------------------------------------------------------------------------------------ background
for name, size in (("bg-wide", (1600, 1200)), ("bg-tall", (900, 1800))):
    im = Image.open(os.path.join(src, name + ".png")).convert("RGB").resize(size, Image.LANCZOS)
    im = im.filter(ImageFilter.GaussianBlur(1.2))
    x = arr(im)
    h, w = x.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2)
    vignette = np.clip(1.08 - 0.42 * d ** 1.6, 0.45, 1.0)[..., None]
    x = np.clip(x, 0, 1)
    x = x / (1 + 1.6 * x) * 2.6  # soft knee: ceiling panels no longer dominate the avatar stage
    x = np.power(np.clip(x, 0, 1), 1.15) * 0.60 * vignette
    save(Image.fromarray((x * 255 + .5).astype(np.uint8), "RGB"), "%s-%dx%d.webp" % (name, *size), "background")

# ----------------------------------------------------------------------------------------------------------- tiles
for f in sorted(os.listdir(src)):
    if not (f.startswith("tile-") and f.endswith(".png")): continue
    # slight defocus: the scene reads as depth behind the vector icon, not as a second icon
    x = arr(Image.open(os.path.join(src, f)).convert("RGB").resize((320, 240), Image.LANCZOS).filter(ImageFilter.GaussianBlur(2.2)))
    ramp = np.clip((np.arange(240, dtype=np.float32) - 140) / 100, 0, 1)[:, None, None]
    x = x * (1 - 0.65 * ramp)
    save(Image.fromarray((x * 255 + .5).astype(np.uint8), "RGB"), f.replace(".png", ".webp"), "tile")

manifest = {
    "generator": "tools/blender/continuum_scenes.py + tools/blender/export_assets.py",
    "blender": metrics.get("blender"), "cycles_samples": metrics.get("samples"),
    "reference_images_used": False,
    "contains_runtime_status": False,
    "note": "All meshes, lights and materials are procedural in the script; no owner reference image is embedded.",
    "assets": entries,
}
json.dump(manifest, open(os.path.join(dst, "manifest.json"), "w"), indent=2)
print("total bytes", sum(e["bytes"] for e in entries))
