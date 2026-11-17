#!/usr/bin/env python3
"""Isolated rig hypothesis: raise jaw hinge, preserve every rigid child's neutral pose.

No mesh/morph/material bytes change. This is an unaccepted experiment until the
independent canonical intersection checker and actual pose renders pass.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("source", type=Path)
parser.add_argument("output", type=Path)
parser.add_argument("--expected-sha256", required=True)
parser.add_argument("--raise-metres", type=float, default=.04)
args = parser.parse_args()
assert args.source.resolve() != args.output.resolve()
assert 0 < args.raise_metres <= .06
raw = args.source.read_bytes()
source_sha = hashlib.sha256(raw).hexdigest()
assert source_sha == args.expected_sha256
length = struct.unpack_from("<I", raw, 12)[0]
doc = json.loads(raw[20:20 + length])
binary_chunk = raw[20 + length:]
matches = [(i, node) for i, node in enumerate(doc["nodes"]) if node.get("name") == "Jaw"]
assert len(matches) == 1
index, jaw = matches[0]
assert all(index not in skin["joints"] for skin in doc.get("skins", [])), "Skinned jaw needs a different rebind"
assert jaw.get("rotation", [0, 0, 0, 1]) == [0, 0, 0, 1]
assert jaw.get("scale", [1, 1, 1]) == [1, 1, 1]
assert "matrix" not in jaw
original_translation = jaw["translation"].copy()
jaw["translation"][1] += args.raise_metres
children = []
maximum_neutral_error = 0.0
for child_index in jaw["children"]:
    child = doc["nodes"][child_index]
    assert "matrix" not in child
    original_child = child.get("translation", [0, 0, 0]).copy()
    child["translation"] = original_child.copy()
    child["translation"][1] -= args.raise_metres
    # Parent/child rest translations compose identically, including the unchanged
    # parent Head transform. Descendants inherit this same neutral transform.
    for axis in range(3):
        before = original_translation[axis] + original_child[axis]
        after = jaw["translation"][axis] + child["translation"][axis]
        maximum_neutral_error = max(maximum_neutral_error, abs(before - after))
    children.append(child["name"])
assert maximum_neutral_error < 1e-12
encoded = json.dumps(doc, separators=(",", ":")).encode()
encoded += b" " * (-len(encoded) % 4)
result = struct.pack("<III", 0x46546C67, 2, 20 + len(encoded) + len(binary_chunk))
result += struct.pack("<II", len(encoded), 0x4E4F534A) + encoded + binary_chunk
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_bytes(result)
record = {"status": "EXPERIMENT_NOT_ACCEPTED", "sourceSha256": source_sha,
    "candidateSha256": hashlib.sha256(result).hexdigest(),
    "rigChange": "Raise Jaw hinge and inversely rebase rigid child translations",
    "raiseMetres": args.raise_metres, "rebasedChildren": children,
    "unchangedBinaryChunkSha256": hashlib.sha256(binary_chunk).hexdigest(),
    "meshMorphMaterialBytesUnchanged": True, "maximumNeutralRestTransformErrorMetres": maximum_neutral_error,
    "poseSource": "No pose encoded in this rig-only derivative; replay manifest identifies the actual mapper",
    "limitations": ["Hypothesis pending independent intersection and visual checks", "Not a device result"]}
args.output.with_suffix(".json").write_text(json.dumps(record, indent=2) + "\n")
print(json.dumps(record, indent=2))
