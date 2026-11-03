#!/usr/bin/env python3
"""Unaccepted holographic surface experiment on the existing licensed GLB.

Adds only UVs and one procedural embedded texture. Existing positions, normals,
indices, joint weights and all morph deltas retain their exact original bytes.
No downloaded graphics, private image, new geometry or model weights are used.
"""
import hashlib
import io
import json
import math
import os
from pathlib import Path
import struct
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

source, output = map(Path, sys.argv[1:])
assert source.resolve() != output.resolve()
raw = source.read_bytes()
source_sha = hashlib.sha256(raw).hexdigest()
assert source_sha == "f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e"
json_length = struct.unpack_from("<I", raw, 12)[0]
doc = json.loads(raw[20:20 + json_length])
original_binary = raw[28 + json_length:]
binary = bytearray(original_binary)


def append_view(data):
    while len(binary) % 4:
        binary.append(0)
    offset = len(binary)
    binary.extend(data)
    index = len(doc["bufferViews"])
    doc["bufferViews"].append({"buffer": 0, "byteOffset": offset, "byteLength": len(data)})
    return index


face = next(node for node in doc["nodes"] if node.get("name") == "Face")
primitive = doc["meshes"][face["mesh"]]["primitives"][0]
position = doc["accessors"][primitive["attributes"]["POSITION"]]
view = doc["bufferViews"][position["bufferView"]]
offset = view.get("byteOffset", 0) + position.get("byteOffset", 0)
stride = view.get("byteStride", 12)
uv = bytearray()
uv_points = []
for i in range(position["count"]):
    x, y, z = struct.unpack_from("<fff", original_binary, offset + i * stride)
    # Front seam is avoided; the wrap lies on the back. UVs deform with actual morphs.
    u = (math.atan2(x, z - .04) / (2 * math.pi) + .5) % 1
    v = max(0, min(1, y / .393))
    uv_points.append((u, v))
    uv.extend(struct.pack("<ff", u, v))
uv_view = append_view(uv)
uv_accessor = len(doc["accessors"])
doc["accessors"].append({"bufferView": uv_view, "componentType": 5126,
    "count": position["count"], "type": "VEC2"})
primitive["attributes"]["TEXCOORD_0"] = uv_accessor

size = 1024
v, u = np.mgrid[0:size, 0:size].astype(np.float32) / (size - 1)
# Fine continuous curves, not new topology. The texture has no task text or data.
horizontal = np.exp(-np.square(np.sin(2 * np.pi * (v * 48 + .22 * np.sin(u * 2 * np.pi))) / .15))
diagonal = np.exp(-np.square(np.sin(2 * np.pi * (u * 44 + v * 10)) / .11))
thread = np.maximum(horizontal, diagonal * .78)
pattern = os.environ.get("AVATAR_SURFACE_PATTERN", "grid")
assert pattern in ["grid", "topology"]
if pattern == "topology":
    # The same actual source triangle edges follow nose, eye sockets and lips.
    # Only the texture is new; no tube mesh or triangle budget is added.
    mask = Image.new("L", (size, size))
    draw = ImageDraw.Draw(mask)
    index = doc["accessors"][primitive["indices"]]
    index_view = doc["bufferViews"][index["bufferView"]]
    index_offset = index_view.get("byteOffset", 0) + index.get("byteOffset", 0)
    code, width = {5123: ("H", 2), 5125: ("I", 4)}[index["componentType"]]
    indices = struct.unpack_from("<" + code * index["count"], original_binary, index_offset)
    edges = set()
    for i in range(0, len(indices), 3):
        a, b, c = indices[i:i + 3]
        edges.update((min(x, y), max(x, y)) for x, y in [(a, b), (b, c), (c, a)])
    for a, b in sorted(edges):
        ua, va = uv_points[a]
        ub, vb = uv_points[b]
        if abs(ua - ub) > .5:
            continue
        draw.line((ua * (size - 1), va * (size - 1), ub * (size - 1), vb * (size - 1)), fill=255, width=1)
    core = np.asarray(mask, dtype=np.float32) / 255
    glow = np.asarray(mask.filter(ImageFilter.GaussianBlur(1.1)), dtype=np.float32) / 255
    density = np.asarray(mask.filter(ImageFilter.GaussianBlur(5)), dtype=np.float32) / 255
    # Anatomically dense eyelid/lip triangles must not become an opaque white mask.
    # Normalize local coverage, retaining thin edge positions and shaded facial volume.
    local_gain = np.minimum(1, .11 / (density + .015))
    thread = np.clip((core * .72 + glow * .38) * local_gain, 0, .65)
mix = np.clip((u - .35) / .3, 0, 1)
cyan = np.array([62, 200, 255], dtype=np.float32)
violet = np.array([146, 92, 255], dtype=np.float32)
color = violet[None, None, :] * (1 - mix[..., None]) + cyan[None, None, :] * mix[..., None]
background = np.array([5, 13, 35] if pattern == "grid" else [20, 16, 46], dtype=np.float32)
rgba = np.zeros((size, size, 4), dtype=np.uint8)
rgba[..., :3] = np.clip(background + (color - background) * thread[..., None], 0, 255).astype(np.uint8)
base_alpha = 110 if pattern == "grid" else 170
rgba[..., 3] = np.clip(base_alpha + thread * (255 - base_alpha), 0, 255).astype(np.uint8)
texture_buffer = io.BytesIO()
Image.fromarray(rgba).save(texture_buffer, format="PNG")
texture_bytes = texture_buffer.getvalue()
image_view = append_view(texture_bytes)
image_index = len(doc.setdefault("images", []))
doc["images"].append({"bufferView": image_view, "mimeType": "image/png", "name": "ProceduralContinuumSurface"})
sampler_index = len(doc.setdefault("samplers", []))
doc["samplers"].append({"magFilter": 9729, "minFilter": 9987, "wrapS": 10497, "wrapT": 33071})
texture_index = len(doc.setdefault("textures", []))
doc["textures"].append({"source": image_index, "sampler": sampler_index})
materials = {material["name"]: material for material in doc["materials"]}
skin = materials["DeepIndigoSkin"]
skin["pbrMetallicRoughness"].update(baseColorFactor=[1, 1, 1, 1],
    baseColorTexture={"index": texture_index}, metallicFactor=.15 if pattern == "grid" else .05,
    roughnessFactor=.48 if pattern == "grid" else .62)
skin["emissiveFactor"] = [.65, .65, .65] if pattern == "grid" else [.18, .18, .18]
skin["emissiveTexture"] = {"index": texture_index}
skin["alphaMode"] = "BLEND" if pattern == "grid" else "OPAQUE"
for name in ["CyanThreads", "VioletThreads"]:
    materials[name]["pbrMetallicRoughness"]["baseColorFactor"][3] = .22
    materials[name]["emissiveFactor"] = [value * .22 for value in materials[name]["emissiveFactor"]]
    materials[name]["alphaMode"] = "BLEND"
# The original subdued iris is kept; only sclera becomes less stark against the hologram.
materials["NaturalSclera"]["pbrMetallicRoughness"]["baseColorFactor"] = [.045, .075, .12, 1]
doc["buffers"][0]["byteLength"] = len(binary)
while len(binary) % 4:
    binary.append(0)
assert binary[:len(original_binary)] == original_binary
encoded = json.dumps(doc, separators=(",", ":")).encode()
encoded += b" " * (-len(encoded) % 4)
result = struct.pack("<III", 0x46546C67, 2, 28 + len(encoded) + len(binary))
result += struct.pack("<II", len(encoded), 0x4E4F534A) + encoded
result += struct.pack("<II", len(binary), 0x004E4942) + binary
output.parent.mkdir(parents=True, exist_ok=True)
output.write_bytes(result)
record = {"status": "EXPERIMENT_NOT_ACCEPTED", "sourceSha256": source_sha,
    "candidateSha256": hashlib.sha256(result).hexdigest(),
    "unchangedOriginalBinaryPrefixSha256": hashlib.sha256(original_binary).hexdigest(),
    "positionsNormalsIndicesSkinningMorphBytesUnchanged": True,
    "addedUvVertices": position["count"], "addedEmbeddedProceduralTexturePixels": [size, size],
    "addedTexturePngBytes": len(texture_bytes), "surfacePattern": pattern,
    "frontSurfaceAlphaMode": skin["alphaMode"], "device": "FOLD7_NOT_RUN",
    "limitations": ["Transparency sorting and mobile GPU cost require actual runtime testing",
        "Not accepted appearance", "Not a new humanoid mesh"]}
output.with_suffix(".json").write_text(json.dumps(record, indent=2) + "\n")
print(json.dumps(record, indent=2))
