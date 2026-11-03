#!/usr/bin/env python3
"""Encode only a complete, hash-verified actual-mapper frame sequence, without audio."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

folder, output = map(Path, sys.argv[1:])
manifests = [json.loads(path.read_text()) for path in sorted(folder.glob("replay-idle-*.json"))]
assert manifests
reference = manifests[0]
records = {}
for manifest in manifests:
    for key in ["assetSha256", "poseFileSha256", "productionMapperSource", "fixedCamera", "pixels", "samples", "framesPerSecond"]:
        assert manifest[key] == reference[key], "Inconsistent replay conditions: " + key
    for frame in manifest["frames"]:
        index = frame["pose"]["frame"]
        assert index not in records, "Duplicate source frame"
        assert frame["pose"]["sequence"] == "idle-24fps"
        path = folder / frame["file"]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == frame["imageSha256"]
        records[index] = frame
assert sorted(records) == list(range(224)), "Complete 224-frame/two-blink sequence required"
output.parent.mkdir(parents=True, exist_ok=True)
filters = "scale=384:448:flags=lanczos,pad=384:512:0:64:color=0x000A1D,drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='HOST_RENDER_NOT_DEVICE':fontcolor=white:fontsize=14:x=10:y=12,drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='Synthetic mapper replay - no actual audio or camera':fontcolor=0x7DDBFF:fontsize=10:x=10:y=37"
subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-threads", "1", "-filter_threads", "1",
    "-framerate", "24", "-i", str(folder / "frame-%04d.png"), "-frames:v", "224", "-vf", filters,
    "-an", "-c:v", "libx264", "-threads", "1", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(output)], check=True)
output.with_suffix(".json").write_text(json.dumps({
    "evidenceClass": "HOST_RENDER_NOT_DEVICE", "assetSha256": reference["assetSha256"],
    "productionMapperSource": reference["productionMapperSource"], "poseFileSha256": reference["poseFileSha256"],
    "fixedCamera": reference["fixedCamera"], "renderPixels": reference["pixels"],
    "presentationPixels": [384, 512], "frameCount": 224, "framesPerSecond": 24,
    "durationSeconds": 224 / 24, "audio": False, "syntheticInputs": True,
    "videoSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
    "limitations": ["Upscaled low-resolution host render", "No actual TTS/camera or Filament/device evidence"],
}, indent=2) + "\n")
