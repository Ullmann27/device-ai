#!/usr/bin/env python3
"""Derive the cockpit's portable holographic material from the shipped rig.

The original BIN prefix, positions, normals, indices, joint weights, all 21
morphs and every node transform are preserved byte for byte. Only a cylindrical
UV attribute and two embedded procedural PNG material maps are added. These are
surface materials on the animated mesh, never a portrait or a rendered head.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import struct

import numpy as np
from PIL import Image


SOURCE_SHA256 = "6b199c5989640b516b8c45401ececeadb8ed1baae1d1e1ab5db20d19e966aa1e"
TEXTURE_SIZE = 1024


def derive(raw):
    source_hash = hashlib.sha256(raw).hexdigest()
    if source_hash != SOURCE_SHA256:
        raise ValueError("Expected the shipped, jaw-corrected avatar revision")
    length, kind = struct.unpack_from("<II", raw, 12)
    if kind != 0x4E4F534A:
        raise ValueError("Missing GLB JSON chunk")
    doc = json.loads(raw[20:20 + length])
    original_binary = raw[28 + length:]
    binary = bytearray(original_binary)

    def append_view(data, target=None):
        binary.extend(b"\0" * (-len(binary) % 4))
        item = {"buffer": 0, "byteOffset": len(binary), "byteLength": len(data)}
        if target is not None:
            item["target"] = target
        binary.extend(data)
        index = len(doc["bufferViews"])
        doc["bufferViews"].append(item)
        return index

    def accessor(index):
        a = doc["accessors"][index]
        view = doc["bufferViews"][a["bufferView"]]
        width = {"SCALAR": 1, "VEC3": 3}[a["type"]]
        dtype = {5126: "<f4", 5123: "<u2", 5125: "<u4"}[a["componentType"]]
        scalar = np.dtype(dtype).itemsize
        return np.ndarray((a["count"], width), dtype=dtype, buffer=original_binary,
            offset=view.get("byteOffset", 0) + a.get("byteOffset", 0),
            strides=(view.get("byteStride", scalar * width), scalar)).copy()

    face = next(n for n in doc["nodes"] if n.get("name") == "Face")
    primitive = doc["meshes"][face["mesh"]]["primitives"][0]
    positions = accessor(primitive["attributes"]["POSITION"])
    normals = accessor(primitive["attributes"]["NORMAL"])
    normals /= np.maximum(1e-9, np.linalg.norm(normals, axis=1, keepdims=True))
    indices = accessor(primitive["indices"]).reshape(-1, 3)
    low_y, high_y = float(positions[:, 1].min()), float(positions[:, 1].max())
    # The wrap is behind the head. UVs stay attached during every existing morph.
    uv = np.empty((len(positions), 2), dtype="<f4")
    uv[:, 0] = np.mod(np.arctan2(positions[:, 0], positions[:, 2] - .04) / (2 * math.pi) + .5, 1)
    uv[:, 1] = 1 - np.clip((positions[:, 1] - low_y) / (high_y - low_y), 0, 1)
    uv_view = append_view(uv.tobytes(), 34962)
    uv_accessor = len(doc["accessors"])
    doc["accessors"].append({"bufferView": uv_view, "componentType": 5126,
        "count": len(positions), "type": "VEC2"})
    primitive["attributes"]["TEXCOORD_0"] = uv_accessor

    size = TEXTURE_SIZE
    normal_map = np.zeros((size, size, 3), dtype=np.float32)
    covered = np.zeros((size, size), dtype=bool)
    for triangle in indices:
        points = uv[triangle] * (size - 1)
        if float(np.ptp(uv[triangle, 0])) > .5:
            continue  # Do not paint across the rear wrap seam.
        lo = np.maximum(0, np.floor(points.min(axis=0)).astype(int))
        hi = np.minimum(size - 1, np.ceil(points.max(axis=0)).astype(int))
        yy, xx = np.mgrid[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1]
        a, b, c = points
        denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(float(denominator)) < 1e-8:
            continue
        wa = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / denominator
        wb = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / denominator
        wc = 1 - wa - wb
        inside = (wa >= -1e-5) & (wb >= -1e-5) & (wc >= -1e-5)
        values = (wa[..., None] * normals[triangle[0]] + wb[..., None] * normals[triangle[1]]
            + wc[..., None] * normals[triangle[2]])
        values /= np.maximum(1e-9, np.linalg.norm(values, axis=2, keepdims=True))
        normal_map[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1][inside] = values[inside]
        covered[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1] |= inside

    v, u = np.mgrid[0:size, 0:size].astype(np.float32) / (size - 1)
    horizontal = np.clip((u - .30) / .40, 0, 1)
    cyan = np.array([55, 207, 250], dtype=np.float32)
    violet = np.array([145, 81, 239], dtype=np.float32)
    hue = violet * (1 - horizontal[..., None]) + cyan * horizontal[..., None]
    # Continuous, fine flowing surface lines, not a triangle cage. Both scales
    # are baked once; there is no per-frame texture generation or new clock.
    flow = (1 - v) * 67 + .82 * np.sin(u * math.pi * 4 + v * 7)
    flow += .28 * np.sin(u * math.pi * 11 - v * 13)
    line = np.exp(-np.square(np.sin(math.pi * flow) / .18))
    fine = np.exp(-np.square(np.sin(math.pi * (flow * 1.9 + u * 3)) / .12)) * .18
    threads = np.maximum(line, fine)
    front = np.clip((normal_map[..., 2] + .12) / .55, 0, 1)
    contour = np.power(np.clip(1 - np.abs(normal_map[..., 2]), 0, 1), 2.8) * front
    contour[~covered] = 0
    # A readable blue/violet body remains between luminous lines. Opaque skin
    # preserves correct eyelid/teeth occlusion and the existing depth ordering.
    base_gain = .31 + .32 * threads + .28 * contour
    emission_gain = .035 + .35 * threads + .63 * contour
    diffuse = np.clip(hue * base_gain[..., None], 0, 255).astype(np.uint8)
    emissive = np.clip(hue * emission_gain[..., None], 0, 255).astype(np.uint8)
    sampler = len(doc.setdefault("samplers", []))
    doc["samplers"].append({"magFilter": 9729, "minFilter": 9987, "wrapS": 10497, "wrapT": 33071})

    def texture(pixels, name):
        encoded = io.BytesIO()
        Image.fromarray(pixels).save(encoded, format="PNG", optimize=False)
        view = append_view(encoded.getvalue())
        index = len(doc.setdefault("images", []))
        doc["images"].append({"bufferView": view, "mimeType": "image/png", "name": name})
        result = len(doc.setdefault("textures", []))
        doc["textures"].append({"source": index, "sampler": sampler})
        return result

    diffuse_texture = texture(diffuse, "ContinuumHolographicSurface")
    emission_texture = texture(emissive, "ContinuumHolographicContours")
    materials = {m["name"]: m for m in doc["materials"]}
    skin = materials["DeepIndigoSkin"]
    skin["pbrMetallicRoughness"].update(baseColorFactor=[1, 1, 1, 1],
        baseColorTexture={"index": diffuse_texture}, metallicFactor=.12, roughnessFactor=.42)
    skin["alphaMode"] = "OPAQUE"
    skin["emissiveFactor"] = [1, 1, 1]
    skin["emissiveTexture"] = {"index": emission_texture}
    skin["extensions"] = {"KHR_materials_emissive_strength": {"emissiveStrength": 2.6}}
    doc["extensionsUsed"] = sorted(set(doc.get("extensionsUsed", [])) | {"KHR_materials_emissive_strength"})
    for name, color in [("CyanThreads", [.025, .38, .64]), ("VioletThreads", [.28, .055, .58])]:
        materials[name]["pbrMetallicRoughness"].update(baseColorFactor=[*color, 1],
            metallicFactor=.05, roughnessFactor=.48)
        materials[name]["emissiveFactor"] = [c * .32 for c in color]
    materials["NaturalSclera"]["pbrMetallicRoughness"]["baseColorFactor"] = [.045, .105, .145, 1]
    materials["CyanIris"]["pbrMetallicRoughness"]["baseColorFactor"] = [.025, .24, .42, 1]
    materials["CyanIris"]["emissiveFactor"] = [.012, .12, .25]
    doc["buffers"][0]["byteLength"] = len(binary)
    binary.extend(b"\0" * (-len(binary) % 4))
    encoded = json.dumps(doc, separators=(",", ":")).encode()
    encoded += b" " * (-len(encoded) % 4)
    result = struct.pack("<III", 0x46546C67, 2, 28 + len(encoded) + len(binary))
    result += struct.pack("<II", len(encoded), 0x4E4F534A) + encoded
    result += struct.pack("<II", len(binary), 0x004E4942) + binary
    if binary[:len(original_binary)] != original_binary:
        raise AssertionError("Original geometry/animation bytes changed")
    return result, {
        "schema": "continuum-holographic-bust-derivation-v1",
        "sourceSha256": source_hash,
        "candidateSha256": hashlib.sha256(result).hexdigest(),
        "originalBinaryPrefixSha256": hashlib.sha256(original_binary).hexdigest(),
        "sourceBytes": len(raw), "candidateBytes": len(result),
        "geometryRigMorphsAndTransformsUnchanged": True,
        "textureSize": [size, size], "textureCount": 2,
        "surface": "Opaque animated mesh with procedural embedded surface and contour maps",
        "limitations": ["Fixed object-space contour design, not a view-dependent Fresnel shader",
            "Host render does not establish Android Filament GPU appearance or performance"],
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()
    if args.source.resolve() == args.output.resolve():
        raise ValueError("Preserve the source asset")
    result, record = derive(args.source.read_bytes())
    record["derivationScriptSha256"] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(result)
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps(record, indent=2))
