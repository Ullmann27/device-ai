#!/usr/bin/env python3
"""Explicit diagnostic V2; actual V1 FAIL remains a separate, unchanged result.

The actual hinge is checked before a labeled, neutral-preserving coordinate view
is passed to the original morph-localization checks. The view is not a new asset.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import struct

ORIGINAL_SHA = "f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e"
LEGACY_SHA = "4550fb6deaa92d92401f482baad1dff80636c71d6e516cb46a7dc4fdc8fc445e"


def require(value, reason):
    if not value:
        raise ValueError(reason)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def load_legacy(path):
    require(sha(Path(path).read_bytes()) == LEGACY_SHA, "Unpinned legacy validator")
    spec = importlib.util.spec_from_file_location("epsilon_legacy_v1", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def encode(doc, binary):
    encoded = json.dumps(doc, separators=(",", ":")).encode()
    encoded += b" " * (-len(encoded) % 4)
    return (struct.pack("<III", 0x46546C67, 2, 28 + len(encoded) + len(binary)) +
            struct.pack("<II", len(encoded), 0x4E4F534A) + encoded +
            struct.pack("<II", len(binary), 0x004E4942) + binary)


def max_matrix_error(a, b):
    return max(abs(x-y) for row_a, row_b in zip(a, b) for x, y in zip(row_a, row_b))


def validate(original_bytes, candidate_bytes, legacy):
    require(sha(original_bytes) == ORIGINAL_SHA, "Unpinned original landmark source")
    original, actual = legacy.Glb(original_bytes), legacy.Glb(candidate_bytes)
    require(original.names == actual.names and original.parents == actual.parents,
            "Node identity or parent hierarchy changed")
    jaw = actual.names.index("Jaw")
    require(actual.descendant("Jaw", "Head"), "Jaw must descend from Head")
    require(all(jaw not in skin["joints"] for skin in actual.doc.get("skins", [])),
            "Actual Jaw may not skin the exterior")
    original_world = [original.world(i) for i in range(len(original.names))]
    actual_world = [actual.world(i) for i in range(len(actual.names))]
    # Every other control/rigid child remains in its original neutral world pose.
    neutral_error = max(max_matrix_error(a, b) for i, (a, b) in
                        enumerate(zip(original_world, actual_world)) if i != jaw)
    require(neutral_error < 1e-7, "Neutral child/control transform changed")
    jaw_node = actual.doc["nodes"][jaw]
    require(jaw_node.get("rotation", [0, 0, 0, 1]) == [0, 0, 0, 1] and
            jaw_node.get("scale", [1, 1, 1]) == [1, 1, 1], "Jaw rest basis changed")
    actual_hinge = legacy.transform(actual_world[jaw], (0, 0, 0))
    original_hinge = legacy.transform(original_world[jaw], (0, 0, 0))
    require(abs(actual_hinge[0]-original_hinge[0]) < 1e-7 and
            abs(actual_hinge[2]-original_hinge[2]) < 1e-7,
            "This revision changes only hinge height, not its lateral/depth axis")
    left, right = [legacy.transform(actual_world[actual.names.index(n)], (0, 0, 0))
                   for n in ("EyeLeft", "EyeRight")]
    require(abs(actual_hinge[0] - (left[0]+right[0])/2) < 1e-7,
            "Hinge is not centered between eyes")
    # Derive the oral support from the actual face and all oral targets. The
    # unchanged original V1 localization independently guards against moving it.
    face_index = actual.names.index("Face")
    face = actual.doc["nodes"][face_index]
    mesh = actual.doc["meshes"][face["mesh"]]
    primitive = mesh["primitives"][0]
    points = [legacy.transform(actual_world[face_index], p)
              for p in actual.accessor(primitive["attributes"]["POSITION"])]
    oral_indices = set()
    oral_names = set(legacy.TARGETS[:15]) - {"SIL"}
    oral_names.add("JawOpen")
    for name, target in zip(mesh["extras"]["targetNames"], primitive["targets"]):
        if name in oral_names:
            oral_indices.update(i for i, d in enumerate(actual.accessor(target["POSITION"]))
                                if math.sqrt(sum(x*x for x in d)) > 1e-7)
    require(oral_indices, "No actual oral deformation support")
    oral_top = max(points[i][1] for i in oral_indices)
    require(oral_top + 1e-7 < actual_hinge[1] < min(left[1], right[1]) - 1e-7,
            "Actual hinge must sit above oral support and below the eyes")
    bounds = [(min(p[a] for p in points), max(p[a] for p in points)) for a in range(3)]
    require(all(lo < actual_hinge[a] < hi for a, (lo, hi) in enumerate(bounds)),
            "Actual hinge outside rest face bounds")
    tooth_points = []
    for i, name in enumerate(actual.names):
        # Preserve actual mouth-interior/tooth meshes, not just empty controls.
        if not (name.startswith("Tooth") or name == "MouthInterior"):
            continue
        a_node, o_node = actual.doc["nodes"][i], original.doc["nodes"][i]
        ap = actual.doc["meshes"][a_node["mesh"]]["primitives"]
        op = original.doc["meshes"][o_node["mesh"]]["primitives"]
        require(len(ap) == len(op), "Rigid mouth primitive count changed")
        for a, o in zip(ap, op):
            require(actual.accessor(a["attributes"]["POSITION"]) ==
                    original.accessor(o["attributes"]["POSITION"]), "Rigid mouth vertices changed")
            require(actual.accessor(a["indices"]) == original.accessor(o["indices"]),
                    "Rigid mouth topology changed")
        if name.startswith("Tooth"):
            # Exported rigid meshes can bake the tooth position into vertices;
            # their empty node origin is not the tooth's anatomical location.
            for primitive in ap:
                tooth_points.extend(legacy.transform(actual_world[i], point) for point in
                                    actual.accessor(primitive["attributes"]["POSITION"]))
    require(tooth_points and actual_hinge[2] < min(p[2] for p in tooth_points),
            "Actual hinge must be behind all teeth")
    # An explicit diagnostic coordinate view: undo hinge height, inversely
    # compensate its rigid children, and retain the candidate's actual mesh data.
    diagnostic = copy.deepcopy(actual.doc)
    old_local = original.doc["nodes"][jaw].get("translation", [0, 0, 0])
    actual_local = jaw_node.get("translation", [0, 0, 0])
    delta = [actual_local[a]-old_local[a] for a in range(3)]
    diagnostic["nodes"][jaw]["translation"] = old_local.copy()
    for child in jaw_node.get("children", []):
        t = diagnostic["nodes"][child].get("translation", [0, 0, 0])
        diagnostic["nodes"][child]["translation"] = [t[a]+delta[a] for a in range(3)]
    diagnostic_bytes = encode(diagnostic, actual.binary)
    diagnostic_glb = legacy.Glb(diagnostic_bytes)
    normalized_error = max(max_matrix_error(actual_world[i], diagnostic_glb.world(i))
                           for i in range(len(actual.names)) if i != jaw)
    require(normalized_error < 1e-7, "Diagnostic normalization changed neutral geometry")
    localized = legacy.validate(diagnostic_bytes)
    require(localized["status"] == "PASS", "Fixed-region legacy checks failed: " +
            json.dumps(localized.get("failures", [])))
    return {"status": "PASS_SEPARATE_HINGE_AND_FIXED_REGION_V2",
            "actualAssetSha256": sha(candidate_bytes), "originalLandmarkSha256": sha(original_bytes),
            "actualHinge": actual_hinge, "actualOralSupportMaximumY": oral_top,
            "actualToothMinimumZ": min(p[2] for p in tooth_points),
            "actualEyeY": [left[1], right[1]], "neutralWorldMaximumError": neutral_error,
            "diagnosticNeutralMaximumError": normalized_error,
            "diagnosticViewSha256": sha(diagnostic_bytes),
            "diagnosticViewLegacyResult": localized,
            "limits": ["Diagnostic view is not the actual candidate; actual V1 outcome remains separate.",
                       "Functional stylized hinge constraints, not measured human TMJ anatomy.",
                       "Actual-hinge pose intersections, visual appearance and device checks remain separate."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("legacy", type=Path)
    parser.add_argument("original", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    legacy = load_legacy(args.legacy)
    original, candidate = args.original.read_bytes(), args.candidate.read_bytes()
    result = {"legacySourceSha256": LEGACY_SHA,
              "originalV1": legacy.validate(original), "actualCandidateV1": legacy.validate(candidate)}
    try:
        result["v2"] = validate(original, candidate, legacy)
        code = 0
    except (ValueError, KeyError, IndexError, TypeError, struct.error) as error:
        result["v2"] = {"status": "FAIL", "reason": str(error)}
        code = 1
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"originalV1": result["originalV1"]["status"],
                      "actualCandidateV1": result["actualCandidateV1"]["status"], "v2": result["v2"]["status"]}))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
