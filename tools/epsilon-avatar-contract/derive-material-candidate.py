#!/usr/bin/env python3
"""Reproducible material-only experiment; does not replace the packaged asset.

The GLB BIN chunk (all geometry, skinning and morphs) is preserved byte for byte.
No new asset, texture, network request or authored animation is introduced.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

BASE_SHA256 = "f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e"


def derive(source: bytes):
    assert hashlib.sha256(source).hexdigest() == BASE_SHA256, "Wrong source revision"
    length, kind = struct.unpack_from("<II", source, 12)
    assert kind == 0x4E4F534A
    doc = json.loads(source[20:20 + length])
    binary_chunk = source[20 + length:]
    materials = {item["name"]: item for item in doc["materials"]}
    # Existing Continuum cyan/violet in linear RGB. Dashboard/theme source is unchanged.
    cyan = [.04817, .57758, 1.0]
    violet = [.28744, .10702, 1.0]
    skin = materials["DeepIndigoSkin"]
    skin["pbrMetallicRoughness"].update(baseColorFactor=[.015, .035, .105, 1.0],
        metallicFactor=.25, roughnessFactor=.48)
    skin["emissiveFactor"] = [.006, .016, .052]
    for name, color in [("CyanThreads", cyan), ("VioletThreads", violet)]:
        materials[name]["pbrMetallicRoughness"]["baseColorFactor"] = [*color, 1.0]
        materials[name]["emissiveFactor"] = color
    materials["CyanIris"]["pbrMetallicRoughness"]["baseColorFactor"] = [.012, .18, .42, 1.0]
    materials["CyanIris"]["emissiveFactor"] = [.012, .18, .42]
    encoded = json.dumps(doc, separators=(",", ":")).encode()
    encoded += b" " * (-len(encoded) % 4)
    result = struct.pack("<III", 0x46546C67, 2, 20 + len(encoded) + len(binary_chunk))
    result += struct.pack("<II", len(encoded), 0x4E4F534A) + encoded + binary_chunk
    assert result[20 + len(encoded):] == binary_chunk
    return result, {
        "status": "EXPERIMENT_NOT_ACCEPTED",
        "sourceSha256": BASE_SHA256,
        "candidateSha256": hashlib.sha256(result).hexdigest(),
        "unchangedBinaryChunkSha256": hashlib.sha256(binary_chunk).hexdigest(),
        "changedMaterials": ["DeepIndigoSkin", "CyanThreads", "VioletThreads", "CyanIris"],
        "geometrySkinningMorphsUnchanged": True,
        "appearanceRequiresIndependentReview": True,
        "device": "FOLD7_NOT_RUN",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    assert args.source.resolve() != args.output.resolve(), "Never overwrite source"
    result, record = derive(args.source.read_bytes())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(result)
    args.output.with_suffix(".json").write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps(record, indent=2))
