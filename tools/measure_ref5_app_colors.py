#!/usr/bin/env python3
"""Measure native semantic color regions."""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import re

from PIL import Image


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_measure(repo):
    path = repo / "tools/measure_ref5_colors.py"
    spec = importlib.util.spec_from_file_location("measure_ref5_colors", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def action_box(path, tag):
    matches = []
    pattern = re.compile(r"bounds=Rect\(\s*(-?\d+),\s*(-?\d+)\s*-\s*(-?\d+),\s*(-?\d+)\s*\)")
    lines = Path(path).read_text().splitlines()
    if any("tag=jarvis-work-window shown=true" in line for line in lines):
        raise ValueError("Use an unobscured START capture for cockpit color sampling")
    for line in lines:
        if "ShellActionView " not in line or f"tag={tag} " not in line or "shown=true" not in line:
            continue
        match = pattern.search(line)
        if match:
            matches.append((tuple(int(v) for v in match.groups()), line))
    if len(matches) != 1:
        raise ValueError(f"Expected one visible native {tag}; found {len(matches)} in {path}")
    box, line = matches[0]
    return box, {"kind": "native_view_dump", "path": str(path), "sha256": sha256(path), "line": line}


def checked_box(image, box):
    box = tuple(int(v) for v in box)
    left, top, right, bottom = box
    if not (0 <= left < right <= image.width and 0 <= top < bottom <= image.height):
        raise ValueError(f"ROI outside {image.size}: {box}")
    return box


def luma(rgb):
    return sum(v * k for v, k in zip(rgb, (.2126, .7152, .0722)))


def linear_luma(rgb):
    values = [v / 255 for v in rgb]
    return sum((v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4) * k
               for v, k in zip(values, (.2126, .7152, .0722)))


def contrast(rgb, fill):
    a, b = sorted((linear_luma(rgb), linear_luma(fill)))
    return round((b + .05) / (a + .05), 3)


def sampled(module, image, method, box):
    box = checked_box(image, box)
    rgb = tuple(module.measure(image, method, box))
    return {"box_xyxy": list(box), "method": method, "rgb": list(rgb),
            "hex": "#%02X%02X%02X" % rgb, "luma_srgb_0_255": round(luma(rgb), 3)}


def app_regions(box, density, font):
    left, top, right, bottom = box
    width, height = right - left, bottom - top
    icon = min(26 * density, height * .5)
    center_y = (top + bottom) * .5
    cap = 13 * .711 * font * density
    pitch = (10 * .711 * font + 3) * density
    text_top = center_y - (cap + 2 * pitch) * .5
    def rounded(x0, y0, x1, y1):
        return (math.floor(x0), math.floor(y0), math.ceil(x1), math.ceil(y1))
    return {
        "tile.fill": ("dark", rounded(left + .58 * width, top + .30 * height,
                                      left + .82 * width, top + .90 * height)),
        "tile.rim": ("peak", rounded(left + .28 * width, top - 2 * density,
                                      left + .60 * width, top + 4 * density)),
        "tile.icon": ("peak", rounded(left + 10 * density, center_y - icon * .5,
                                       left + 10 * density + icon, center_y + icon * .5)),
        "text.primary": ("peak", rounded(left + 20 * density + icon, text_top - density,
                                          right - 7 * density, text_top + cap + 2 * density)),
    }


def glow_profile(module, image, rim_box, maximum=12):
    left, top, right, bottom = checked_box(image, rim_box)
    rows = [(y, sampled(module, image, "median", (left, y, right, y + 1))) for y in range(top, bottom)]
    anchor, _ = max(rows, key=lambda item: item[1]["luma_srgb_0_255"])
    result = []
    for distance in range(maximum + 1):
        row = sampled(module, image, "median", (left, anchor - distance, right, anchor - distance + 1))
        result.append({"outward_distance_px": distance, **row})
    outer = result[-1]["luma_srgb_0_255"]
    initial = result[0]["luma_srgb_0_255"] - outer
    for row in result:
        delta = row["luma_srgb_0_255"] - outer
        row["luma_delta_vs_outermost"] = round(delta, 3)
        row["normalized_excess"] = round(delta / initial, 4) if initial > 0 else None
    monotonic = all(a["luma_srgb_0_255"] + 2 >= b["luma_srgb_0_255"] for a, b in zip(result, result[1:]))
    crossing = next((row["outward_distance_px"] for row in result
                     if row["normalized_excess"] is not None and row["normalized_excess"] <= 1 / math.e), None)
    return {"semantic_role": "chat_tile_top_rim_outward", "anchor_y_px": anchor,
            "anchor_method": "maximum median-luma row inside semantic rim ROI",
            "monotonic_with_2_luma_tolerance": monotonic,
            "first_1_over_e_crossing_px": crossing if monotonic and initial > 5 else None,
            "rows": result,
            "limit": "Composite scene profile; outermost strip is not a verified unlit background. Neighboring light may contribute."}


def delta(a, b):
    values = [x - y for x, y in zip(a, b)]
    return {"delta_rgb": values, "rgb_l2": round(math.sqrt(sum(v * v for v in values)), 3)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--baseline-views", type=Path)
    parser.add_argument("--baseline-box", nargs=4, type=int)
    parser.add_argument("--baseline-box-note")
    parser.add_argument("--current", type=Path, required=True)
    parser.add_argument("--current-views", type=Path, required=True)
    parser.add_argument("--current-source-label", required=True)
    parser.add_argument("--density", type=float, default=1)
    parser.add_argument("--font-scale", type=float, default=1)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--summary-output", type=Path)
    args = parser.parse_args()
    if not all(math.isfinite(v) and v > 0 for v in (args.density, args.font_scale)):
        parser.error("density and font-scale must be positive")
    if bool(args.baseline_views) == bool(args.baseline_box):
        parser.error("Provide exactly one baseline geometry source")
    if args.baseline_box and not args.baseline_box_note:
        parser.error("Manually mapped baseline bounds require an explicit note")
    module = load_measure(args.repo)
    images = {key: Image.open(path).convert("RGB") for key, path in
              (("reference", args.reference), ("baseline", args.baseline), ("current", args.current))}
    if images["reference"].size != (1448, 1086):
        raise ValueError("Original reference must be 1448x1086; no resizing allowed")
    current_box, current_geometry = action_box(args.current_views, "jarvis-tile:chat")
    if args.baseline_views:
        baseline_box, baseline_geometry = action_box(args.baseline_views, "jarvis-tile:chat")
    else:
        baseline_box = tuple(args.baseline_box)
        baseline_geometry = {"kind": "explicit_manual_box", "note": args.baseline_box_note}
    for key, box in (("baseline", baseline_box), ("current", current_box)):
        checked_box(images[key], box)
    reference_regions = {key: (method, box) for key, method, box in module.SAMPLES}
    regions = {"reference": reference_regions,
               "baseline": app_regions(baseline_box, args.density, args.font_scale),
               "current": app_regions(current_box, args.density, args.font_scale)}
    fills = {key: sampled(module, image, *regions[key]["tile.fill"])["rgb"] for key, image in images.items()}
    measurements = []
    for role in ("tile.fill", "tile.rim", "tile.icon", "text.primary"):
        values = {key: sampled(module, image, *regions[key][role]) for key, image in images.items()}
        for key, record in values.items():
            record["sample_to_tile_fill_contrast"] = contrast(record["rgb"], fills[key])
        measurements.append({"role": role, **values,
                             "baseline_minus_reference": delta(values["baseline"]["rgb"], values["reference"]["rgb"]),
                             "current_minus_reference": delta(values["current"]["rgb"], values["reference"]["rgb"]),
                             "current_minus_baseline": delta(values["current"]["rgb"], values["baseline"]["rgb"])})
    paths = {"reference": args.reference, "baseline": args.baseline, "current": args.current}
    report = {
        "status": "HOST_NATIVE_PIXEL_SAMPLES_ONLY",
        "baseline_source": "b294fc9139f1a458fccf815bb931eb61b2f7b0e8",
        "current_source": args.current_source_label,
        "measurement_code": {"path": str(args.repo / "tools/measure_ref5_colors.py"),
                             "sha256": sha256(args.repo / "tools/measure_ref5_colors.py")},
        "comparison_code": {"path": str(Path(__file__).resolve()), "sha256": sha256(__file__)},
        "images": {key: {"path": str(path), "sha256": sha256(path),
                         "size": list(images[key].size)} for key, path in paths.items()},
        "geometry": {"baseline": {"chat_tile_bounds": list(baseline_box), **baseline_geometry},
                     "current": {"chat_tile_bounds": list(current_box), **current_geometry},
                     "reference": "Existing original-resolution SAMPLES from measure_ref5_colors.py"},
        "roi_method": {"fill": "Darkest30% inside chat body excludes most foreground text",
                       "rim": "Native box top edge, middle horizontal section; brightest10%",
                       "icon": "drawTile icon bounds: 10dp inset, min(26dp, half tile height)",
                       "title": "drawTile title band; estimated .711*13sp cap, 1dp above and 2dp below; no glyph mask",
                       "density": args.density, "font_scale": args.font_scale},
        "color_measurements": measurements,
        "glow_profiles": {key: glow_profile(module, image, regions[key]["tile.rim"][1])
                          for key, image in images.items()},
        "limits": ["No image scaling, warping, whole-image fidelity score, or device measurement.",
                   "Host screenshot uses native Android Canvas and a 2D head fallback; no Filament/GPU verdict.",
                   "API28 host captures do not validate device API31 blur or API33 AGSL output.",
                   "RGB distance is not perceptual DeltaE; local sample contrast is not a text accessibility audit.",
                   "Ambient neon phase and dynamic scene background are not synchronized across captures.",
                   "Four semantic color roles plus one local glow profile; no product-color edit follows automatically."]
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(args.output)
    if args.summary_output:
        summary = {
            "status": report["status"],
            "raw_report_sha256": sha256(args.output),
            "image_sha256": {key: value["sha256"] for key, value in report["images"].items()},
            "colors": [{"role": row["role"],
                        "reference": row["reference"]["hex"], "baseline": row["baseline"]["hex"],
                        "current": row["current"]["hex"],
                        "current_minus_reference_rgb": row["current_minus_reference"]["delta_rgb"],
                        "current_minus_baseline_rgb": row["current_minus_baseline"]["delta_rgb"],
                        "contrast_ref_base_current": [row[key]["sample_to_tile_fill_contrast"]
                                                       for key in ("reference", "baseline", "current")]}
                       for row in measurements],
            "glow_outward_luma_0_to_12_px": {key: [row["luma_srgb_0_255"] for row in value["rows"]]
                                               for key, value in report["glow_profiles"].items()},
            "baseline_roi_source": baseline_geometry["kind"],
            "current_roi_source": current_geometry["kind"],
            "limits": report["limits"],
        }
        args.summary_output.parent.mkdir(parents=True, exist_ok=True)
        args.summary_output.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n")
        print(args.summary_output)
    for row in measurements:
        print(row["role"], *(row[key]["hex"] for key in ("reference", "baseline", "current")))


if __name__ == "__main__":
    main()
