#!/usr/bin/env python3
"""Reproducible decorative cutouts explicitly authorized by the owner (Directive IV).

No dashboard screenshot or face cutout becomes a functional UI. Only the supplied
brand mark, empty rail light trails and text-free tile corner reflections are used.
"""
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs/design-screenshots/jarvis-master-20261104.jpg"
EXPECTED = "e0afbdd199f619e0e8ee4ed791c8f930d34dd7769c3f0a9b154ccbfbb80749de"
OUT = ROOT / "app/src/main/assets/jarvis-reference"


def extract(name, box, kind):
    image = np.asarray(Image.open(SOURCE).convert("RGB").crop(box), dtype=np.float32)
    height, width, _ = image.shape
    y, x = np.mgrid[:height, :width]
    u, v = x / max(1, width - 1), y / max(1, height - 1)
    # Remove the dark navy base, retaining chromatic light with soft alpha.
    rgb = np.maximum(image - np.array([2., 8., 17.]), 0.)
    a = rgb.max(axis=2) / 255.
    if kind == "tile":
        # This lower-right wedge lies beyond every source label and icon.
        region = (v > .32) & (u > 1.02 - .24 * v)
        a *= region
        a *= np.minimum(1., np.maximum(0., (u - (1.02 - .24 * v)) * 36))
    elif kind == "rail":
        a *= np.minimum(1., np.minimum.reduce([u, 1-u, v, 1-v]) * 24.)
    else:
        a *= np.minimum(1., np.minimum.reduce([u, 1-u, v, 1-v]) * 14.)
    # Straight alpha: color * alpha reproduces the extracted light on a dark base.
    straight = np.divide(rgb, np.maximum(rgb.max(axis=2, keepdims=True) / 255., .001))
    rgba = np.dstack([straight.clip(0, 255), (a * 255).clip(0, 255)]).astype(np.uint8)
    path = OUT / name
    Image.fromarray(rgba).save(path, optimize=True)
    return {"asset": "jarvis-reference/" + name, "source_box": box,
            "kind": kind, "dimensions": [width, height], "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


if __name__ == "__main__":
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == EXPECTED, "Wrong reference revision"
    OUT.mkdir(parents=True, exist_ok=True)
    records = [extract("device-ai-mark.png", [189, 8, 267, 83], "brand"),
               extract("rail-energy.png", [18, 737, 239, 891], "rail"),
               extract("tile-cyan-energy.png", [258, 504, 508, 591], "tile"),
               extract("tile-violet-energy.png", [520, 504, 758, 591], "tile")]
    manifest = {"source_sha256": EXPECTED, "source": str(SOURCE.relative_to(ROOT)),
                "authorization": "Owner-supplied image; explicit extraction instruction in Master Directive IV",
                "contains_full_dashboard": False, "contains_runtime_status": False, "assets": records}
    (OUT / "provenance.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))
