#!/usr/bin/env python3
"""A labeled scientific comparison sheet; source render pixels are not retouched."""
import hashlib
import json
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont

original, candidate, output = map(Path, sys.argv[1:])
records = [json.loads((path / "replay-manifest.json").read_text()) for path in [original, candidate]]
assert records[0]["fixedCamera"] == records[1]["fixedCamera"]
assert records[0]["pixels"] == records[1]["pixels"]
assert records[0]["samples"] == records[1]["samples"]
assert [r["pose"] for r in records[0]["frames"]] == [r["pose"] for r in records[1]["frames"]]
width, height = 200, 234
sheet = Image.new("RGB", (width * 5, 650), (0, 10, 29))
draw = ImageDraw.Draw(sheet)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
draw.text((12, 10), "HOST_RENDER_NOT_DEVICE | Blender/Cycles | Fixed camera | Synthetic inputs", font=font, fill="white")
draw.text((12, 32), "Actual production mapper 26555aa6; no real TTS, camera or Filament/device claim", font=small, fill=(125, 219, 255))
labels = ["Neutral idle", "Actual full blink", "Reachable AA + jaw", "Reachable O + jaw", "Speech stopped"]
for row, (path, manifest) in enumerate(zip([original, candidate], records)):
    y = 64 + row * 290
    title = "Original" if row == 0 else "Experimental candidate - NOT ACCEPTED"
    draw.text((12, y), title + " | asset " + manifest["assetSha256"][:16], font=font, fill="white")
    for column, (frame, label) in enumerate(zip(manifest["frames"], labels)):
        source = path / frame["file"]
        assert hashlib.sha256(source.read_bytes()).hexdigest() == frame["imageSha256"]
        with Image.open(source) as image:
            sheet.paste(image.convert("RGB").resize((width, height), Image.Resampling.LANCZOS), (column * width, y + 24))
        draw.text((column * width + 8, y + height + 30), label, font=small, fill=(125, 219, 255))
output.parent.mkdir(parents=True, exist_ok=True)
sheet.save(output)
output.with_suffix(".json").write_text(json.dumps({
    "evidenceClass": "HOST_RENDER_NOT_DEVICE", "method": "Equal scale only; no color/contrast retouching",
    "sourceManifests": [str(path / "replay-manifest.json") for path in [original, candidate]],
    "assetSha256": [record["assetSha256"] for record in records],
    "sheetSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
}, indent=2) + "\n")
