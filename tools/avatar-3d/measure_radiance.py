"""Compare identically framed original-GLB host renders, not Filament or device pixels.

python measure_radiance.py BASELINE BOUNDED AGGRESSIVE OUTPUT
"""
import hashlib
import json
from pathlib import Path
import sys

from PIL import Image, ImageDraw, ImageFont


paths = [Path(arg) for arg in sys.argv[1:4]]
output = Path(sys.argv[4])
output.mkdir(parents=True, exist_ok=True)
images = [Image.open(path).convert("RGB") for path in paths]
assert all(image.size == (384, 512) for image in images)
labels = ["Authored baseline 1.00x", "Bounded peak 1.32x", "Rejected probe 1.60x"]
sheet = Image.new("RGB", (1152, 568), (3, 5, 12))
draw = ImageDraw.Draw(sheet)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
draw.text((12, 8), "ORIGINAL GLB - Blender CPU / not Filament, not Fold7 - identical camera, lights, seed", font=font)
records = []
for index, (image, label, path) in enumerate(zip(images, labels, paths)):
    sheet.paste(image, (384 * index, 30))
    draw.text((384 * index + 10, 546), label, font=font)
    pixels = list(image.crop((90, 65, 295, 325)).getdata())
    luma = [.2126 * r + .7152 * g + .0722 * b for r, g, b in pixels]
    records.append({
        "label": label, "file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "faceRoi": [90, 65, 295, 325],
        "meanLuma8bit": sum(luma) / len(luma),
        "whiteClipShare": sum(min(rgb) >= 250 for rgb in pixels) / len(pixels),
        "anyChannelClipShare": sum(max(rgb) >= 250 for rgb in pixels) / len(pixels),
    })
sheet.save(output / "radiance-comparison.png")
(output / "radiance-measurements.json").write_text(json.dumps({
    "evidenceClass": "ACTUAL_GLB_HOST_RENDER_NOT_DEVICE",
    "scope": "Fixed ROI diagnostic, not reference fidelity or Android exposure equivalence",
    "frames": records,
}, indent=2) + "\n")
print(json.dumps(records, indent=2))
