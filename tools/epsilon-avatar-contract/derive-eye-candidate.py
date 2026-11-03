#!/usr/bin/env python3
"""Unaccepted small eye-aperture variant using the existing actual blink targets.

Unlike material-only variants this intentionally changes position/morph bytes.
Closed blink endpoints are preserved exactly (apart from Float32 rounding).
All triangle counts, joints, topology and source licenses remain unchanged.
"""
import hashlib
import json
from pathlib import Path
import struct
import sys

source, output = map(Path, sys.argv[1:])
assert source.resolve() != output.resolve()
raw = source.read_bytes()
provenance = json.loads(source.with_suffix(".json").read_text())
assert hashlib.sha256(raw).hexdigest() == provenance["candidateSha256"]
length = struct.unpack_from("<I", raw, 12)[0]
doc = json.loads(raw[20:20 + length])
binary = bytearray(raw[28 + length:])
relaxation = .15
changed = []
processed_positions = set()
processed_blinks = set()


def points(accessor_index):
    a = doc["accessors"][accessor_index]
    assert a["componentType"] == 5126 and a["type"] == "VEC3"
    view = doc["bufferViews"][a["bufferView"]]
    stride = view.get("byteStride", 12)
    offset = view.get("byteOffset", 0) + a.get("byteOffset", 0)
    return a, [offset + stride * i for i in range(a["count"])]


for mesh in doc["meshes"]:
    names = mesh.get("extras", {}).get("targetNames", [])
    if not {"BlinkLeft", "BlinkRight"} <= set(names):
        continue
    for primitive in mesh["primitives"]:
        position_index = primitive["attributes"]["POSITION"]
        if position_index in processed_positions:
            continue
        processed_positions.add(position_index)
        position_meta, position_offsets = points(position_index)
        blink_indices = [primitive["targets"][names.index(name)]["POSITION"] for name in ["BlinkLeft", "BlinkRight"]]
        blink_offsets = [points(index)[1] for index in blink_indices]
        assert all(len(offsets) == len(position_offsets) for offsets in blink_offsets)
        new_positions = []
        for offset, left, right in zip(position_offsets, *blink_offsets):
            p = struct.unpack_from("<fff", binary, offset)
            dl = struct.unpack_from("<fff", binary, left)
            dr = struct.unpack_from("<fff", binary, right)
            new = tuple(p[i] + relaxation * (dl[i] + dr[i]) for i in range(3))
            struct.pack_into("<fff", binary, offset, *new)
            new_positions.append(new)
        position_meta["min"] = [min(p[i] for p in new_positions) for i in range(3)]
        position_meta["max"] = [max(p[i] for p in new_positions) for i in range(3)]
        changed.append(position_index)
        for index, offsets in zip(blink_indices, blink_offsets):
            if index in processed_blinks:
                continue
            processed_blinks.add(index)
            for offset in offsets:
                old = struct.unpack_from("<fff", binary, offset)
                struct.pack_into("<fff", binary, offset, *(value * (1 - relaxation) for value in old))
            changed.append(index)
for node in doc["nodes"]:
    if node.get("name") in ["PupilLeft", "PupilRight"]:
        node["scale"] = [1.30, 1.30, 1.0]
encoded = json.dumps(doc, separators=(",", ":")).encode()
encoded += b" " * (-len(encoded) % 4)
result = struct.pack("<III", 0x46546C67, 2, 28 + len(encoded) + len(binary))
result += struct.pack("<II", len(encoded), 0x4E4F534A) + encoded
result += struct.pack("<II", len(binary), 0x004E4942) + binary
output.write_bytes(result)
record = {**provenance, "candidateSha256": hashlib.sha256(result).hexdigest(),
    "materialSourceSha256": hashlib.sha256(raw).hexdigest(),
    "positionsNormalsIndicesSkinningMorphBytesUnchanged": False,
    "neutralBlinkFraction": relaxation, "pupilXYScale": 1.30,
    "changedPositionOrBlinkAccessors": changed,
    "geometryStatus": "EXPERIMENT_NOT_ACCEPTED_REQUIRES_MORPH_ENDPOINT_REVIEW"}
record.pop("unchangedOriginalBinaryPrefixSha256", None)
output.with_suffix(".json").write_text(json.dumps(record, indent=2) + "\n")
print(json.dumps(record, indent=2))
