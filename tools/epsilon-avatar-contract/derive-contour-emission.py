#!/usr/bin/env python3
"""Portable glTF material experiment, not an accepted appearance or runtime shader.

Bakes an object-space cyan/violet contour-emission atlas from the real Face
normals into the existing UVs. This is intentionally a fixed front-oriented
artistic light pattern, not view-dependent Fresnel or a simulated renderer.
Original geometry, morphs, rig, teeth and other material objects are unchanged.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import numpy as np
from PIL import Image, ImageFilter

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("source", type=Path)
parser.add_argument("output", type=Path)
parser.add_argument("--expected-sha256", required=True)
parser.add_argument("--variant", choices=["broad", "narrow"], default="broad")
parser.add_argument("--emissive-strength", type=float, default=1)
args = parser.parse_args()
assert 1 <= args.emissive_strength <= 8
assert args.source.resolve() != args.output.resolve()
raw = args.source.read_bytes()
source_sha = hashlib.sha256(raw).hexdigest()
assert source_sha == args.expected_sha256
length = struct.unpack_from("<I", raw, 12)[0]
doc = json.loads(raw[20:20 + length])
original_binary = raw[28 + length:]
binary = bytearray(original_binary)


def accessor(index):
    a = doc["accessors"][index]
    assert "sparse" not in a
    view = doc["bufferViews"][a["bufferView"]]
    width = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}[a["type"]]
    dtype = {5126: "<f4", 5123: "<u2", 5125: "<u4"}[a["componentType"]]
    item = np.dtype(dtype).itemsize
    start = view.get("byteOffset", 0) + a.get("byteOffset", 0)
    return np.ndarray((a["count"], width), dtype=dtype, buffer=original_binary,
        offset=start, strides=(view.get("byteStride", width * item), item)).copy()


face = next(n for n in doc["nodes"] if n.get("name") == "Face")
primitive = doc["meshes"][face["mesh"]]["primitives"][0]
uv = accessor(primitive["attributes"]["TEXCOORD_0"])
normals = accessor(primitive["attributes"]["NORMAL"])
positions = accessor(primitive["attributes"]["POSITION"])
normals /= np.maximum(1e-12, np.linalg.norm(normals, axis=1, keepdims=True))
indices = accessor(primitive["indices"]).reshape((-1, 3))
size = 1024
normal_map = np.zeros((size, size, 3), dtype=np.float32)
position_map = np.zeros((size, size, 3), dtype=np.float32)
coverage = np.zeros((size, size), dtype=bool)
for triangle in indices:
    points = uv[triangle] * (size - 1)
    if np.ptp(points[:, 0]) > size / 2:
        continue
    low = np.maximum(0, np.floor(points.min(axis=0)).astype(int))
    high = np.minimum(size - 1, np.ceil(points.max(axis=0)).astype(int))
    if np.any(high < low):
        continue
    yy, xx = np.mgrid[low[1]:high[1] + 1, low[0]:high[0] + 1]
    a, b, c = points
    denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
    if abs(denominator) < 1e-9:
        continue
    wa = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / denominator
    wb = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / denominator
    wc = 1 - wa - wb
    inside = (wa >= -1e-6) & (wb >= -1e-6) & (wc >= -1e-6)
    interpolated = (wa[..., None] * normals[triangle[0]]
        + wb[..., None] * normals[triangle[1]] + wc[..., None] * normals[triangle[2]])
    interpolated /= np.maximum(1e-12, np.linalg.norm(interpolated, axis=2, keepdims=True))
    view = normal_map[low[1]:high[1] + 1, low[0]:high[0] + 1]
    view[inside] = interpolated[inside]
    position_view = position_map[low[1]:high[1] + 1, low[0]:high[0] + 1]
    position_view[inside] = (wa[..., None] * positions[triangle[0]]
        + wb[..., None] * positions[triangle[1]] + wc[..., None] * positions[triangle[2]])[inside]
    coverage[low[1]:high[1] + 1, low[0]:high[0] + 1] |= inside

# Surface normals vary smoothly across triangles. The glow follows actual nose,
# lips, temples and shoulder curvature instead of drawing a new geometric cage.
nz = normal_map[..., 2]
front = np.clip((nz + .18) / .36, 0, 1)
rim = np.power(np.maximum(0, 1 - np.abs(nz)), 2.4 if args.variant == "broad" else 5) * front
rim = np.asarray(Image.fromarray((rim * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(.7))) / 255
if args.variant == "narrow":
    # Keep eyelid volume readable during full closure. The mask uses the actual
    # neutral rig eye centres; it neither moves geometry nor changes eye/teeth materials.
    parents = {child: parent for parent, node in enumerate(doc["nodes"]) for child in node.get("children", [])}
    def world_point(index):
        node = doc["nodes"][index]
        assert node.get("rotation", [0, 0, 0, 1]) == [0, 0, 0, 1]
        value = np.array(node.get("translation", [0, 0, 0]), dtype=np.float32)
        while index in parents:
            index = parents[index]
            parent = doc["nodes"][index]
            value = value * np.array(parent.get("scale", [1, 1, 1])) + np.array(parent.get("translation", [0, 0, 0]))
        return value
    eye_fade = np.ones((size, size), dtype=np.float32)
    for index, node in enumerate(doc["nodes"]):
        if node.get("name") not in ["EyeLeft", "EyeRight"]:
            continue
        centre = world_point(index)
        distance = np.square((position_map[..., 0] - centre[0]) / .024) + np.square((position_map[..., 1] - centre[1]) / .018)
        eye_fade *= np.clip((distance - .6) / .9, 0, 1)
    rim *= eye_fade
base = np.array([48, 20, 104] if args.variant == "broad" else [8, 3, 20], dtype=np.float32)
cyan = np.array([104, 229, 255], dtype=np.float32)
violet = np.array([124, 63, 239], dtype=np.float32)
horizontal_mix = np.clip((normal_map[..., 0] + .4) / .8, 0, 1)
edge = violet * (1 - horizontal_mix[..., None]) + cyan * horizontal_mix[..., None]
rgb = np.clip(base + (edge - base) * rim[..., None], 0, 255).astype(np.uint8)
rgb[~coverage] = base.astype(np.uint8)
encoded_texture = io.BytesIO()
Image.fromarray(rgb).save(encoded_texture, format="PNG")
png = encoded_texture.getvalue()
while len(binary) % 4:
    binary.append(0)
offset = len(binary)
binary.extend(png)
view_index = len(doc["bufferViews"])
doc["bufferViews"].append({"buffer": 0, "byteOffset": offset, "byteLength": len(png)})
image_index = len(doc["images"])
doc["images"].append({"bufferView": view_index, "mimeType": "image/png", "name": "BakedContourEmission"})
texture_index = len(doc["textures"])
doc["textures"].append({"source": image_index, "sampler": 0})
skin = doc["materials"][primitive["material"]]
assert skin["name"] == "DeepIndigoSkin" and skin["alphaMode"] == "OPAQUE"
skin["emissiveFactor"] = [1, 1, 1]
skin["emissiveTexture"] = {"index": texture_index}
if args.emissive_strength > 1:
    # glTF's standardized extension is handled by the pinned Filament 1.56
    # AssetLoader.cpp and Blender's actual importer. Not a custom host shader.
    used = doc.setdefault("extensionsUsed", [])
    if "KHR_materials_emissive_strength" not in used:
        used.append("KHR_materials_emissive_strength")
    skin.setdefault("extensions", {})["KHR_materials_emissive_strength"] = {"emissiveStrength": args.emissive_strength}
doc["buffers"][0]["byteLength"] = len(binary)
while len(binary) % 4:
    binary.append(0)
assert binary[:len(original_binary)] == original_binary
encoded = json.dumps(doc, separators=(",", ":")).encode()
encoded += b" " * (-len(encoded) % 4)
result = struct.pack("<III", 0x46546C67, 2, 28 + len(encoded) + len(binary))
result += struct.pack("<II", len(encoded), 0x4E4F534A) + encoded
result += struct.pack("<II", len(binary), 0x004E4942) + binary
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_bytes(result)
record = {"status": "EXPERIMENT_NOT_ACCEPTED", "sourceSha256": source_sha,
    "derivationScriptSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    "candidateSha256": hashlib.sha256(result).hexdigest(),
    "originalBinaryPrefixSha256": hashlib.sha256(original_binary).hexdigest(),
    "geometryMorphRigAndToothMaterialsUnchanged": True,
    "frontSurfaceAlphaMode": "OPAQUE", "texturePixels": [size, size],
    "variant": args.variant,
    "emissiveStrength": args.emissive_strength,
    "addedEmbeddedPngBytes": len(png), "coveredTexels": int(coverage.sum()),
    "runtimeRequirement": "glTF emissiveTexture and optional KHR_materials_emissive_strength; no custom shader or external resources",
    "limitations": ["Fixed object-space front-oriented contour light pattern, not view-dependent Fresnel",
        "Actual Filament appearance and cost NOT_RUN", "FOLD7_NOT_RUN"]}
args.output.with_suffix(".json").write_text(json.dumps(record, indent=2) + "\n")
print(json.dumps(record, indent=2))
