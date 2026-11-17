#!/usr/bin/env python3
"""Reproducible raster differences; reports measurements, never aesthetic acceptance."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rectangle(value: list[int], size: tuple[int, int]) -> tuple[int, int, int, int]:
    if len(value) != 4 or any(type(v) is not int for v in value):
        raise ValueError(f"Rectangle needs four integer coordinates: {value}")
    left, top, right, bottom = value
    if not (0 <= left < right <= size[0] and 0 <= top < bottom <= size[1]):
        raise ValueError(f"Rectangle outside {size}: {value}")
    return left, top, right, bottom


def align_capture(image: Image.Image, size: tuple[int, int], allow_uniform_resize: bool):
    if image.size == size:
        return image, {"operation": "none", "source_size": list(image.size), "scale": 1.0}
    width, height = image.size
    if not allow_uniform_resize:
        raise ValueError("Different raster sizes; use --allow-uniform-resize explicitly if the aspect ratios match")
    if width * size[1] != height * size[0]:
        raise ValueError("Aspect ratios differ: stretching, padding and automatic cropping are prohibited")
    return image.resize(size, Image.Resampling.LANCZOS), {
        "operation": "uniform_resample", "source_size": [width, height],
        "target_size": list(size), "scale": size[0] / width, "kernel": "Pillow LANCZOS",
    }


def metrics(reference: np.ndarray, capture: np.ndarray, included: np.ndarray) -> dict:
    if reference.shape != capture.shape or reference.shape[:2] != included.shape:
        raise ValueError("Metrics require matching RGB rasters and a matching mask")
    count = int(np.count_nonzero(included))
    if count == 0:
        raise ValueError("Mask excludes the whole region")
    delta = capture.astype(np.float64)[included] - reference.astype(np.float64)[included]
    mse = float(np.mean(delta * delta))
    return {
        "included_pixels": count,
        "excluded_pixels": int(included.size - count),
        "included_fraction": count / included.size,
        "mean_absolute_rgb_error_0_255": float(np.mean(np.abs(delta))),
        "root_mean_square_rgb_error_0_255": math.sqrt(mse),
        "psnr_db": None if mse == 0 else 10.0 * math.log10(255.0 * 255.0 / mse),
        "identical_included_pixels": mse == 0,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--capture", type=Path, required=True)
    parser.add_argument("--capture-manifest", type=Path, required=True)
    parser.add_argument("--regions", type=Path, default=Path(__file__).with_name("jarvis-master-regions.json"))
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--allow-uniform-resize", action="store_true")
    parser.add_argument("--crop", type=int, nargs=4, metavar=("LEFT", "TOP", "RIGHT", "BOTTOM"))
    args = parser.parse_args()
    configuration = json.loads(args.regions.read_text())
    manifest = json.loads(args.capture_manifest.read_text())
    capture_hash = digest(args.capture)
    reference_hash = digest(args.reference)
    if manifest.get("png_sha256") != capture_hash:
        raise ValueError("Capture manifest does not identify these image bytes")
    if not manifest.get("source_commit") or not manifest.get("fidelity"):
        raise ValueError("Capture manifest needs an exact source_commit and an explicit fidelity label")
    if reference_hash != configuration["reference_sha256"]:
        raise ValueError("Reference bytes differ from the pinned master")
    reference_image = Image.open(args.reference).convert("RGB")
    capture_image = Image.open(args.capture).convert("RGB")
    original_size = capture_image.size
    if list(reference_image.size) != configuration["raster_size"]:
        raise ValueError("Reference raster size differs from the region contract")
    if original_size != (manifest.get("pixel_width"), manifest.get("pixel_height")):
        raise ValueError("Capture dimensions differ from its manifest")
    crop = None
    if args.crop is not None:
        crop = rectangle(args.crop, capture_image.size)
        capture_image = capture_image.crop(crop)
    capture_image, alignment = align_capture(capture_image, reference_image.size, args.allow_uniform_resize)
    reference = np.asarray(reference_image)
    capture = np.asarray(capture_image)
    included = np.ones(reference.shape[:2], dtype=bool)
    for mask in configuration["masks"]:
        left, top, right, bottom = rectangle(mask["box"], reference_image.size)
        if not mask.get("reason"):
            raise ValueError("Every exclusion must document its reason")
        included[top:bottom, left:right] = False
    measurements = []
    for region in configuration["regions"]:
        left, top, right, bottom = rectangle(region["box"], reference_image.size)
        a = reference[top:bottom, left:right]
        b = capture[top:bottom, left:right]
        selected = included[top:bottom, left:right]
        measurements.append({
            "name": region["name"], "box": region["box"],
            "all_pixels": metrics(a, b, np.ones(a.shape[:2], dtype=bool)),
            "declared_masks_only": metrics(a, b, selected),
        })
    args.output_dir.mkdir(parents=True, exist_ok=True)
    # This is a diagnostic image, not an edited product screenshot.
    absolute = np.max(np.abs(capture.astype(np.int16) - reference.astype(np.int16)), axis=2)
    heat = np.zeros_like(reference)
    heat[:, :, 0] = absolute.astype(np.uint8)
    heat[:, :, 1] = (absolute // 4).astype(np.uint8)
    Image.fromarray(heat).save(args.output_dir / "absolute-difference.png")
    mask_visual = np.where(included, 255, 0).astype(np.uint8)
    Image.fromarray(mask_visual).save(args.output_dir / "included-pixels.png")
    report = {
        "schema_version": 1,
        "purpose": "Diagnostic numerical differences; no automatic aesthetic or device pass",
        "reference_sha256": reference_hash, "capture_sha256": capture_hash,
        "region_contract_sha256": digest(args.regions),
        "capture_manifest_sha256": digest(args.capture_manifest),
        "capture_manifest": manifest, "reference_raster": list(reference_image.size),
        "capture_original_raster": list(original_size), "explicit_crop": crop,
        "alignment": alignment, "masks": configuration["masks"], "regions": measurements,
        "all_pixels": metrics(reference, capture, np.ones(reference.shape[:2], dtype=bool)),
        "declared_masks_only": metrics(reference, capture, included),
        "interpretation": "Live values and translated labels may differ honestly. Unmasked and masked measurements are both retained. Pixel errors do not prove usability, identity, animation or native GPU fidelity.",
    }
    path = args.output_dir / "comparison.json"
    path.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n")
    print(json.dumps({"report": str(path), "source_commit": manifest["source_commit"],
                      "alignment": alignment, "all_pixels": report["all_pixels"]}, indent=2))


if __name__ == "__main__":
    main()
