#!/usr/bin/env python3
"""Make a diagnostic contact sheet without changing source color or aspect ratio.

Recipe and output may contain private references: keep both outside Git.
Committed receipts contain hashes, labels and crop geometry, never media bytes.
Each crop is uniformly resized to a shared crown-to-chin height. This is a manual
visual alignment, not inferred 3D anatomy or automatic similarity acceptance.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("recipe", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
recipe = json.loads(args.recipe.read_text())
items = recipe["items"]
columns = recipe.get("columns", 3)
cell_width, cell_height = 384, 472
head_height = 380
sheet = Image.new("RGB", (columns * cell_width, ((len(items) + columns - 1) // columns) * cell_height), (18, 21, 28))
draw = ImageDraw.Draw(sheet)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
records = []
for index, item in enumerate(items):
    source = Path(item["path"])
    full = Image.open(source).convert("RGB")
    box = tuple(item["crop"])
    crop = full.crop(box)
    scale = head_height / crop.height
    target = (round(crop.width * scale), head_height)
    assert target[0] <= cell_width, "Crop is too wide for shared head-height alignment"
    resized = crop.resize(target, Image.Resampling.LANCZOS)
    x, y = (index % columns) * cell_width, (index // columns) * cell_height
    sheet.paste(resized, (x + (cell_width - target[0]) // 2, y + 48))
    for line, text in enumerate(item["label"].split("\n")):
        draw.text((x + 10, y + 8 + line * 18), text, fill=(230, 233, 241), font=font)
    draw.text((x + 10, y + 443), item["pose"], fill=(176, 184, 200), font=font)
    records.append({"sourceFile": source.name, "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "sourcePixels": full.size, "manualCrop": box, "resizedPixels": target,
        "label": item["label"], "pose": item["pose"]})
args.output.parent.mkdir(parents=True, exist_ok=True)
sheet.save(args.output)
receipt = {"schema": "private-reference-pose-contact", "version": 1,
    "privateReferenceMediaCommitted": False, "sameHeadHeightPixels": head_height,
    "manualAlignmentNotGroundTruth": True, "uniformScalingOnly": True,
    "colorExposureContrastUnchanged": True, "items": records,
    "contactSha256": hashlib.sha256(args.output.read_bytes()).hexdigest()}
args.output.with_suffix(".json").write_text(json.dumps(receipt, indent=2) + "\n")
print(json.dumps({"output": str(args.output), "sha256": receipt["contactSha256"]}))
