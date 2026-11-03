#!/usr/bin/env python3
"""Hash-checked contact sheet and actual numeric motion ranges; no retouching."""
import hashlib
import json
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont

folder, output = map(Path, sys.argv[1:])
manifests = [json.loads(p.read_text()) for p in sorted(folder.glob("replay-idle-*.json"))]
assert manifests
reference = manifests[0]
frames = {}
for manifest in manifests:
    for key in ["assetSha256", "poseFileSha256", "productionMapperSource", "fixedCamera", "pixels", "samples", "framesPerSecond"]:
        assert manifest[key] == reference[key]
    for frame in manifest["frames"]:
        index = frame["pose"]["frame"]
        assert index not in frames
        assert hashlib.sha256((folder / frame["file"]).read_bytes()).hexdigest() == frame["imageSha256"]
        frames[index] = frame
assert sorted(frames) == list(range(224))
selected = [0, 24, 48, 72, 96, 108, 120, 144, 168, 192, 219, 223]
width, height = reference["pixels"]
sheet = Image.new("RGB", (width * 4, 72 + (height + 24) * 3), (0, 10, 29))
draw = ImageDraw.Draw(sheet)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)
small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10)
draw.text((12, 10), "HOST_RENDER_NOT_DEVICE | 224 actual mapper frames | Fixed camera", font=font, fill="white")
draw.text((12, 30), "Synthetic inputs - no actual TTS, camera or device claim", font=font, fill=(125, 219, 255))
draw.text((12, 50), "Mapper " + reference["productionMapperSource"][:8] + " | Asset " + reference["assetSha256"][:16], font=small, fill="white")
for cell, index in enumerate(selected):
    x, y = (cell % 4) * width, 72 + (cell // 4) * (height + 24)
    frame = frames[index]
    with Image.open(folder / frame["file"]) as source:
        assert source.size == (width, height)
        sheet.paste(source.convert("RGB"), (x, y))
    draw.text((x + 4, y + height + 4), f"Frame {index:03d} | t={index / 24:.3f}s", font=small, fill=(125, 219, 255))
output.parent.mkdir(parents=True, exist_ok=True)
sheet.save(output)
ranges = {key: [min(f["pose"][key] for f in frames.values()), max(f["pose"][key] for f in frames.values())]
    for key in ["headYaw", "headPitch", "eyeYaw", "eyePitch", "jawPitch"]}
output.with_suffix(".json").write_text(json.dumps({
    "evidenceClass": "HOST_RENDER_NOT_DEVICE", "method": "Original render pixels pasted without rescaling or color adjustment",
    "frameCount": 224, "selectedFrames": selected, "poseFileSha256": reference["poseFileSha256"],
    "productionMapperSource": reference["productionMapperSource"], "assetSha256": reference["assetSha256"],
    "observedAngularRangesDegrees": ranges,
    "sheetSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
    "limitations": ["2-sample low-resolution host motion preview", "Synthetic inputs", "No naturalness or reference-match self-acceptance"],
}, indent=2) + "\n")
