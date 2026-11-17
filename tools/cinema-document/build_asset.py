#!/usr/bin/env python3
"""Prepare the owner's modular screen geometry."""
import hashlib
import json
import math
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).with_name("scene_work_screens.source.glb")
OUTPUT = ROOT / "app/src/main/assets/cinema3d/document-workspace.glb"


def png_white():
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"\x00\xff\xff\xff\xff")) + chunk(b"IEND", b""))


def build():
    raw = SOURCE.read_bytes()
    length = struct.unpack_from("<I", raw, 12)[0]
    source = json.loads(raw[20:20 + length])
    binary = raw[28 + length:]
    data = bytearray()
    doc = {"asset": {"version": "2.0", "generator": "Continuum modular screen adaptation"},
           "scene": 0, "scenes": [{"nodes": [0]}], "nodes": [{"name": "workspace", "children": []}],
           "meshes": [], "materials": [], "bufferViews": [], "accessors": [],
           "extensionsUsed": ["KHR_materials_unlit"]}

    def append(blob, target=None):
        data.extend(b"\0" * (-len(data) % 4))
        view = {"buffer": 0, "byteOffset": len(data), "byteLength": len(blob)}
        if target:
            view["target"] = target
        doc["bufferViews"].append(view)
        data.extend(blob)
        return len(doc["bufferViews"]) - 1

    def values(accessor):
        a = source["accessors"][accessor]
        v = source["bufferViews"][a["bufferView"]]
        width = {"SCALAR": 1, "VEC3": 3}[a["type"]]
        fmt = {5126: "f", 5123: "H", 5125: "I"}[a["componentType"]]
        size = struct.calcsize(fmt) * width
        start = v.get("byteOffset", 0) + a.get("byteOffset", 0)
        stride = v.get("byteStride", size)
        return [struct.unpack_from("<" + fmt * width, binary, start + i * stride) for i in range(a["count"])]

    def attribute(rows, kind):
        flat = [v for row in rows for v in row]
        view = append(struct.pack("<" + "f" * len(flat), *flat), 34962)
        a = {"bufferView": view, "componentType": 5126, "count": len(rows), "type": kind}
        if kind == "VEC3":
            a.update(min=[min(r[i] for r in rows) for i in range(3)], max=[max(r[i] for r in rows) for i in range(3)])
        doc["accessors"].append(a)
        return len(doc["accessors"]) - 1

    selected = {"document_glass_surface": "document-glass", "document_border": "document-rim",
                "result_glass_surface": "result-glass", "result_border": "result-rim"}
    for node in source["nodes"]:
        name = node.get("name")
        if name not in selected:
            continue
        primitive = source["meshes"][node["mesh"]]["primitives"][0]
        points = values(primitive["attributes"]["POSITION"])
        indices = [i[0] for i in values(primitive["indices"])]
        center, size = ((-1.18, -.25, .5775), (1.8, 1.78)) if name.startswith("document") else ((1.4, -.23, .4975), (1.66, 1.58))
        # Keep authored bevel and thickness.
        vertices = [((points[i][0] - center[0]) / size[0], (points[i][1] - center[1]) / size[1],
                     (points[i][2] - center[2]) / min(size)) for i in indices]
        normals = []
        for i in range(0, len(vertices), 3):
            a, b, c = vertices[i:i + 3]
            u, v = [b[k] - a[k] for k in range(3)], [c[k] - a[k] for k in range(3)]
            n = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
            length = math.sqrt(sum(x*x for x in n)) or 1
            normals.extend([tuple(x/length for x in n)] * 3)
        material = dict(source["materials"][primitive.get("material", 0)])
        material["name"] = selected[name]
        material["extensions"] = {"KHR_materials_unlit": {}}
        material["doubleSided"] = True
        doc["materials"].append(material)
        doc["meshes"].append({"name": selected[name], "primitives": [{"attributes": {
            "POSITION": attribute(vertices, "VEC3"), "NORMAL": attribute(normals, "VEC3")},
            "material": len(doc["materials"])-1}]})
        doc["nodes"][0]["children"].append(len(doc["nodes"]))
        doc["nodes"].append({"name": selected[name], "mesh": len(doc["meshes"])-1})

    # Real PDF pixels replace white.
    image_view = append(png_white())
    doc["images"] = [{"bufferView": image_view, "mimeType": "image/png"}]
    doc["samplers"] = [{"magFilter": 9729, "minFilter": 9729, "wrapS": 33071, "wrapT": 33071}]
    doc["textures"] = [{"source": 0, "sampler": 0}]
    doc["materials"].append({"name": "pdf-page", "extensions": {"KHR_materials_unlit": {}}, "doubleSided": True,
        "pbrMetallicRoughness": {"baseColorTexture": {"index": 0}, "baseColorFactor": [1, 1, 1, 1]}})
    points = [(-.5, .5, 0), (-.5, -.5, 0), (.5, -.5, 0), (-.5, .5, 0), (.5, -.5, 0), (.5, .5, 0)]
    uv = [(0, 0), (0, 1), (1, 1), (0, 0), (1, 1), (1, 0)]
    doc["meshes"].append({"name": "pdf-page", "primitives": [{"attributes": {
        "POSITION": attribute(points, "VEC3"), "TEXCOORD_0": attribute(uv, "VEC2")}, "material": len(doc["materials"])-1}]})
    doc["nodes"][0]["children"].append(len(doc["nodes"]))
    doc["nodes"].append({"name": "pdf-page", "mesh": len(doc["meshes"])-1})
    data.extend(b"\0" * (-len(data) % 4))
    doc["buffers"] = [{"byteLength": len(data)}]
    header = json.dumps(doc, separators=(",", ":")).encode()
    header += b" " * (-len(header) % 4)
    result = (struct.pack("<4sII", b"glTF", 2, 28 + len(header) + len(data))
              + struct.pack("<I4s", len(header), b"JSON") + header
              + struct.pack("<I4s", len(data), b"BIN\0") + data)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(result)
    print(json.dumps({"source_sha256": hashlib.sha256(raw).hexdigest(), "asset_sha256": hashlib.sha256(result).hexdigest(),
                      "bytes": len(result), "meshes": len(doc["meshes"])}))


if __name__ == "__main__":
    build()
