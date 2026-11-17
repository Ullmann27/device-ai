#!/usr/bin/env python3
"""Compare a REAL Android Continuum snapshot with the owner's portrait reference.

Geometry, low-frequency colour and edge differences are measured per visual ROI.
This never labels a JVM mock-up a device screenshot. Telemetry values legitimately
differ and the reference includes fictitious sample numbers, so pixel equality is
not expected just because the layout is correct.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageStat

TARGET = (1144, 1375)
# Independently registered ROIs, derived from the 2026-10-27 reference image.
REGIONS = {
    "hero": (305, 75, 670, 565),
    "actions_left": (17, 104, 329, 506),
    "actions_right": (667, 103, 894, 506),
    "system_status": (900, 82, 1105, 569),
    "model": (16, 582, 378, 926),
    "learning": (389, 582, 751, 926),
    "storage": (764, 582, 1129, 926),
    "development": (16, 936, 378, 1283),
    "activity": (389, 936, 751, 1283),
    "workers": (764, 936, 1129, 1283),
}

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def mean_difference(a, b):
    delta = ImageChops.difference(a, b)
    return round(sum(ImageStat.Stat(delta).mean) / 3, 3)

def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("android_snapshot", type=Path, help="Actual on-device PNG, never a synthetic JVM preview")
    parser.add_argument("owner_reference", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--metrics", type=Path, help="ContinuumUiSnapshot JSON to remove system bar insets")
    args = parser.parse_args()
    ref = Image.open(args.owner_reference).convert("RGB")
    if ref.size != TARGET:
        raise SystemExit("REFERENCE_SIZE_MISMATCH: expected 1144x1375")
    image = Image.open(args.android_snapshot).convert("RGB")
    original = image.size
    crop = None
    if args.metrics:
        data = json.loads(args.metrics.read_text("utf-8"))
        if data.get("schema") != "continuum-ui-snapshot":
            raise SystemExit("INVALID_ANDROID_SNAPSHOT_METRICS")
        insets = data.get("insetsPx")
        if not isinstance(insets, dict):
            raise SystemExit("MISSING_ANDROID_SYSTEM_INSETS")
        l, t, r, b = (int(insets[k]) for k in ("left", "top", "right", "bottom"))
        crop = (l, t, image.width-r, image.height-b)
        if crop[2] <= crop[0] or crop[3] <= crop[1]:
            raise SystemExit("INVALID_INSETS_CROP")
        image = image.crop(crop)
    ratio_error = abs(image.width/image.height - TARGET[0]/TARGET[1]) / (TARGET[0]/TARGET[1])
    # A large aspect mismatch means no honest pixel-perfect geometry claim.
    geometry_comparable = ratio_error < 0.035
    # Explicit warp only for diagnostic visualization; flag mismatch rather than hiding it.
    scaled = image.resize(TARGET, Image.Resampling.LANCZOS)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    side = Image.new("RGB", (TARGET[0]*2+12, TARGET[1]))
    side.paste(ref, (0, 0))
    side.paste(scaled, (TARGET[0]+12, 0))
    side.save(args.output_dir/"side-by-side.png")
    ImageChops.difference(ref,scaled).save(args.output_dir/"pixel-difference.png")
    overlay = Image.blend(ref, scaled, .5)
    overlay.save(args.output_dir/"overlay.png")
    regions = {}
    drawer = ImageDraw.Draw(side)
    for name, box in REGIONS.items():
        a, b = ref.crop(box), scaled.crop(box)
        regions[name] = {
            "meanAbsRgb": mean_difference(a,b),
            "lowFrequencyMeanAbsRgb": mean_difference(
                a.filter(ImageFilter.GaussianBlur(6)),
                b.filter(ImageFilter.GaussianBlur(6))
            )
        }
        drawer.rectangle(box, outline="#00F5FF", width=2)
        drawer.rectangle((box[0]+TARGET[0]+12,box[1],box[2]+TARGET[0]+12,box[3]),
                         outline="#00F5FF",width=2)
    side.save(args.output_dir/"regions-side-by-side.png")
    report = {
        "schema":"continuum-fold7-portrait-visual-comparison-v1",
        "referenceSha256":digest(args.owner_reference),
        "androidSnapshotSha256":digest(args.android_snapshot),
        "referencePx":list(TARGET), "snapshotPx":list(original),
        "postInsetsCrop":list(crop) if crop else None,
        "aspectRatioError":round(ratio_error,6),
        "geometryComparable":geometry_comparable,
        "wholeImageMeanAbsRgb":mean_difference(ref,scaled),
        "regions":regions,
        "deviceVerified":False,
        "note":"Only device provenance/manifest can establish that the supplied PNG was real. "
               "Changing telemetry text cannot be interpreted as a UI-regression. "
               "This script compares submitted PNGs; it does not build or run Android."
    }
    (args.output_dir/"report.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n","utf-8")
    print(json.dumps({"geometryComparable":geometry_comparable,
                      "aspectRatioError":report["aspectRatioError"],
                      "wholeImageMeanAbsRgb":report["wholeImageMeanAbsRgb"],
                      "output":str(args.output_dir)},ensure_ascii=False))

if __name__ == "__main__":
    run()
