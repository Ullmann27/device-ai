"""Reproject the owner correction onto the existing animated head atlas."""
from pathlib import Path
import argparse
import copy
import hashlib
import io
import json
import math
import struct

import numpy as np
from PIL import Image
from scipy.ndimage import distance_transform_edt, map_coordinates


BASE_SHA = "70b93bfdfb10c65662f6d37857e4a6a403dfb6ad950a9220acf91cb4145ab145"
REFERENCE_SHA = "f70460e42e7b89db5dd4c1f94228d3b7afa7c54a06774ebd57132ab27ecad7a4"
DTYPES = {5121: "u1", 5123: "<u2", 5125: "<u4", 5126: "<f4"}
WIDTHS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def smooth(lo, hi, value):
    t = np.clip((value - lo) / (hi - lo), 0, 1)
    return t * t * (3 - 2 * t)


def read_glb(path):
    raw = path.read_bytes()
    magic, version, total = struct.unpack_from("<III", raw)
    if (magic, version, total) != (0x46546C67, 2, len(raw)):
        raise ValueError("Invalid GLB envelope")
    size, kind = struct.unpack_from("<II", raw, 12)
    if kind != 0x4E4F534A:
        raise ValueError("Missing JSON chunk")
    doc = json.loads(raw[20:20 + size])
    length, kind = struct.unpack_from("<II", raw, 20 + size)
    if kind != 0x004E4942:
        raise ValueError("Missing binary chunk")
    return raw, doc, raw[28 + size:28 + size + length]


def accessor(doc, binary, index):
    a = doc["accessors"][index]
    view = doc["bufferViews"][a["bufferView"]]
    dtype = np.dtype(DTYPES[a["componentType"]])
    width = WIDTHS[a["type"]]
    return np.ndarray((a["count"], width), dtype=dtype, buffer=binary,
                      offset=view.get("byteOffset", 0) + a.get("byteOffset", 0),
                      strides=(view.get("byteStride", dtype.itemsize * width), dtype.itemsize)).copy()


def fragments(points, triangles, width, height):
    for triangle in triangles:
        p = points[triangle]
        low = np.maximum(0, np.ceil(p.min(axis=0)).astype(int))
        high = np.minimum([width - 1, height - 1], np.floor(p.max(axis=0)).astype(int))
        if np.any(low > high):
            continue
        yy, xx = np.mgrid[low[1]:high[1] + 1, low[0]:high[0] + 1]
        a, b, c = p
        denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(float(denominator)) < 1e-10:
            continue
        wa = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / denominator
        wb = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / denominator
        wc = 1 - wa - wb
        inside = (wa >= -1e-6) & (wb >= -1e-6) & (wc >= -1e-6)
        if inside.any():
            yield triangle, yy[inside], xx[inside], np.stack([wa[inside], wb[inside], wc[inside]], axis=1)


def projection(positions, mirrored=False):
    p = positions.copy()
    if mirrored:
        p[:, 0] *= -1
    yaw = math.radians(-20)
    view = np.array([math.sin(yaw), .018, math.cos(yaw)])
    view /= np.linalg.norm(view)
    right = np.array([math.cos(yaw), 0, -math.sin(yaw)])
    up = np.cross(view, right)
    qx, qy = p @ right, p @ up
    px = 1095 + 3340 * qx
    py = 1430 - 3340 * qy
    py -= 43 * np.exp(-((qy - .176) / .025) ** 2)
    ear = np.exp(-((px - 900) / 90) ** 4 - ((py - 533) / 105) ** 4)
    px += 50 * ear
    return np.stack([px, py], axis=1), p @ view, view


def sample(array, coords, outside=0):
    points = [coords[:, 1], coords[:, 0]]
    if array.ndim == 2:
        return map_coordinates(array, points, order=1, mode="constant", cval=outside)
    return np.stack([map_coordinates(array[:, :, i], points, order=1,
                                    mode="constant", cval=outside) for i in range(array.shape[2])], axis=1)


def linear(rgb):
    return np.where(rgb <= .04045, rgb / 12.92, ((rgb + .055) / 1.055) ** 2.4)


def srgb(rgb):
    rgb = np.maximum(rgb, 0)
    return np.where(rgb <= .0031308, rgb * 12.92, 1.055 * rgb ** (1 / 2.4) - .055)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("reference", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    raw, doc, binary = read_glb(args.source)
    if sha(raw) != BASE_SHA or sha(args.reference.read_bytes()) != REFERENCE_SHA:
        raise ValueError("Uncalibrated input identity")
    reference = np.asarray(Image.open(args.reference).convert("RGB"), dtype=np.float32) / 255
    if reference.shape != (1152, 1536, 3):
        raise ValueError("Unexpected reference dimensions")
    reference = linear(reference) / 1.35
    if len(doc["images"]) != 1:
        raise ValueError("Expected one embedded atlas")
    image_view = doc["bufferViews"][doc["images"][0]["bufferView"]]
    image_offset = image_view["byteOffset"]
    if image_offset + image_view["byteLength"] != doc["buffers"][0]["byteLength"]:
        raise ValueError("Atlas must be the final payload")
    atlas_bytes = binary[image_offset:image_offset + image_view["byteLength"]]
    original = np.asarray(Image.open(io.BytesIO(atlas_bytes)).convert("RGB"))
    height, width = original.shape[:2]
    face = next(n for n in doc["nodes"] if n.get("name") == "Face")
    primitive = doc["meshes"][face["mesh"]]["primitives"][0]
    pos = accessor(doc, binary, primitive["attributes"]["POSITION"])
    normal = accessor(doc, binary, primitive["attributes"]["NORMAL"])
    uv = accessor(doc, binary, primitive["attributes"]["TEXCOORD_0"])
    triangles = accessor(doc, binary, primitive["indices"]).reshape(-1, 3)
    xyz = np.zeros((height, width, 3), dtype=np.float32)
    normals = np.zeros_like(xyz)
    valid = np.zeros((height, width), dtype=bool)
    for tri, yy, xx, weights in fragments(uv * [width - 1, height - 1], triangles, width, height):
        xyz[yy, xx] = weights @ pos[tri]
        normals[yy, xx] = weights @ normal[tri]
        valid[yy, xx] = True
    ys, xs = np.nonzero(valid)
    points, ns = xyz[ys, xs], normals[ys, xs]
    ns /= np.maximum(np.linalg.norm(ns, axis=1, keepdims=True), 1e-8)
    colors, weights = [], []
    for mirrored in (False, True):
        projected, vertex_depth, view = projection(pos, mirrored)
        depth_map = np.full(reference.shape[:2], -.5, dtype=np.float32)
        for tri, yy, xx, bary in fragments(projected, triangles, 1536, 1152):
            depth_map[yy, xx] = np.maximum(depth_map[yy, xx], bary @ vertex_depth[tri])
        coords, depth, _ = projection(points, mirrored)
        observed_depth = sample(depth_map, coords, outside=.5)
        visibility = 1 - smooth(.0007, .0035, observed_depth - depth)
        direction = view.copy()
        if mirrored:
            direction[0] *= -1
        confidence = smooth(-.15, .24, ns @ direction) * visibility
        confidence *= ((coords[:, 0] > 796) & (coords[:, 0] < 1508)
                       & (coords[:, 1] > 115) & (coords[:, 1] < 1100))
        color = sample(reference, coords)
        if mirrored:
            color = color[:, [1, 0, 2]]
        colors.append(color)
        weights.append(confidence)
    side_mix = smooth(-.012, .025, points[:, 0])
    primary = weights[0] * (1 - side_mix)
    secondary = weights[1] * side_mix
    total = primary + secondary
    color = (colors[0] * primary[:, None] + colors[1] * secondary[:, None]) / np.maximum(total[:, None], 1e-8)
    head_weight = smooth(.145, .190, points[:, 1]) * total
    mouth = smooth(.202, .211, points[:, 1]) * (1 - smooth(.234, .244, points[:, 1]))
    mouth *= (1 - smooth(.021, .035, np.abs(points[:, 0]))) * smooth(.12, .145, points[:, 2])
    head_weight *= 1 - mouth
    old = linear(original[ys, xs].astype(np.float32) / 255)
    mixed = old * (1 - head_weight[:, None]) + color * head_weight[:, None]
    result = original.copy()
    result[ys, xs] = np.uint8(np.clip(srgb(mixed) * 255 + .5, 0, 255))
    distance, nearest = distance_transform_edt(~valid, return_indices=True)
    margin = (~valid) & (distance <= 5) & (xyz[nearest[0], nearest[1], 1] > .145)
    result[margin] = result[nearest[0][margin], nearest[1][margin]]
    protected = valid & (xyz[:, :, 1] <= .145)
    if not np.array_equal(result[protected], original[protected]):
        raise AssertionError("Lower torso texels changed")
    texture = io.BytesIO()
    Image.fromarray(result).save(texture, format="PNG", compress_level=9)
    texture_bytes = texture.getvalue()
    output_doc = copy.deepcopy(doc)
    output_doc["bufferViews"][doc["images"][0]["bufferView"]]["byteLength"] = len(texture_bytes)
    payload = binary[:image_offset] + texture_bytes
    output_doc["buffers"][0]["byteLength"] = len(payload)
    output_doc.setdefault("extras", {})["appearanceRevision"] = "owner-head-correction-20261104"
    output_doc["extras"]["headCorrectionReferenceSha256"] = REFERENCE_SHA
    payload += b"\0" * (-len(payload) % 4)
    encoded = json.dumps(output_doc, separators=(",", ":")).encode()
    encoded += b" " * (-len(encoded) % 4)
    packed = (struct.pack("<III", 0x46546C67, 2, 28 + len(encoded) + len(payload))
              + struct.pack("<II", len(encoded), 0x4E4F534A) + encoded
              + struct.pack("<II", len(payload), 0x004E4942) + payload)
    if len(packed) >= 16 * 1024 * 1024:
        raise ValueError("Runtime size limit exceeded")
    output = args.output / "continuum-head-corrected.glb"
    output.write_bytes(packed)
    (args.output / "head-corrected-atlas.png").write_bytes(texture_bytes)
    _, check_doc, check_bin = read_glb(output)
    for index in range(len(doc["accessors"])):
        if not np.array_equal(accessor(doc, binary, index), accessor(check_doc, check_bin, index)):
            raise AssertionError(f"Accessor {index} changed")
    for key in ("nodes", "skins", "meshes", "materials", "accessors"):
        if doc[key] != check_doc[key]:
            raise AssertionError(f"Protected {key} changed")
    manifest = {
        "schema": "continuum-owner-head-surface-v1", "sourceSha256": sha(raw),
        "referenceSha256": REFERENCE_SHA, "builderSha256": sha(Path(__file__).read_bytes()),
        "candidateSha256": sha(packed), "candidateBytes": len(packed),
        "atlasSha256": sha(texture_bytes), "atlasPixels": [width, height],
        "allAccessorPayloadsUnchanged": True, "accessorCount": len(doc["accessors"]),
        "rigMorphGeometryMaterialsUnchanged": True,
        "protectedLowerTorsoTexels": int(protected.sum()), "protectedTexelChanges": 0,
        "headInteriorTexelsChanged": int(np.any(result[ys, xs] != original[ys, xs], axis=1).sum()),
        "boundaryMetres": [.145, .190], "cameraYawDegrees": -20,
        "method": "Visibility-weighted quarter projection; mirrored cyan continuation; original mouth, UV and geometry",
        "newImageGeneration": False, "deviceRender": False,
        "limitations": ["Owner image supplies surface appearance, not measured anatomy",
                        "Unseen side uses mirrored color-adjusted continuation",
                        "Native Filament/Fold7 appearance remains unmeasured"],
    }
    (args.output / "candidate-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
