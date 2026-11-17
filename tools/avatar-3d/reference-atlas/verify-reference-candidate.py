#!/usr/bin/env python3
"""Independent GLB geometry-preservation and sampled UV-overlap review.

Allows UV seams to duplicate/reorder vertices and triangle order to change.
Every rendered triangle must retain its winding, positions, normals, skin
influences and all morph displacements. No derivation code is imported.

UV review is an explicitly sampled gate, not a proof of all continuous UV
intersections. The archived baseline is expected to fail that separate gate.
"""
from __future__ import annotations

import argparse
from collections import Counter
import copy
import hashlib
import json
from pathlib import Path
import struct

import numpy as np
from scipy.spatial import cKDTree


MAX_BYTES = 16 * 1024 * 1024
TARGETS = "SIL PP FF TH DD KK CH SS NN RR AA E I O U BlinkLeft BlinkRight BrowUp BrowDown Smile JawOpen".split()
WIDTH = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
DTYPE = {5121: "u1", 5123: "<u2", 5125: "<u4", 5126: "<f4"}
POSITION_TOLERANCE = 2e-7
NORMAL_WEIGHT_TOLERANCE = 2e-6


def require(condition, message):
    if not condition:
        raise ValueError(message)


class Glb:
    def __init__(self, raw):
        self.raw = raw
        require(28 <= len(raw) <= MAX_BYTES, "Asset exceeds the unchanged 16 MiB budget")
        require(struct.unpack_from("<III", raw) == (0x46546C67, 2, len(raw)), "Invalid GLB header")
        length, kind = struct.unpack_from("<II", raw, 12)
        require(kind == 0x4E4F534A and length % 4 == 0 and 20 + length + 8 <= len(raw), "Invalid JSON chunk")
        self.doc = json.loads(raw[20:20 + length])
        binary_length, binary_kind = struct.unpack_from("<II", raw, 20 + length)
        require(binary_kind == 0x004E4942 and 28 + length + binary_length == len(raw), "Invalid BIN chunk")
        self.binary = raw[28 + length:]
        require(self.doc["asset"]["version"] == "2.0", "Unexpected glTF version")
        buffers = self.doc["buffers"]
        require(len(buffers) == 1 and "uri" not in buffers[0], "External/multiple buffers")
        require(0 <= len(self.binary) - buffers[0]["byteLength"] < 4, "Invalid buffer length")
        require(not self.doc.get("animations"), "Runtime must retain event-driven animation")
        require(not self.doc.get("extensionsRequired"), "Unexpected required extension")
        require(all("uri" not in image for image in self.doc.get("images", [])), "External image resource")
        self.names = [node.get("name") for node in self.doc["nodes"]]
        require(len(self.names) == len(set(self.names)) and all(self.names), "Node names must remain unique")
        require(len(self.names) <= 64, "Node budget exceeded")
        self.cache = {}

    def accessor(self, index):
        if index in self.cache:
            return self.cache[index]
        item = self.doc["accessors"][index]
        require("sparse" not in item, "Sparse accessor not supported by this contract")
        view = self.doc["bufferViews"][item["bufferView"]]
        require(view["buffer"] == 0, "Unknown buffer")
        dtype = np.dtype(DTYPE[item["componentType"]])
        width = WIDTH[item["type"]]
        stride = view.get("byteStride", width * dtype.itemsize)
        offset = item.get("byteOffset", 0)
        start = view.get("byteOffset", 0)
        count = item["count"]
        require(0 < count <= 600000 and width * dtype.itemsize <= stride <= 252, "Invalid accessor dimensions")
        require(start >= 0 and offset >= 0 and stride % dtype.itemsize == 0, "Invalid accessor alignment")
        require(start + view["byteLength"] <= len(self.binary), "Buffer view outside BIN")
        require(offset + (count - 1) * stride + width * dtype.itemsize <= view["byteLength"], "Accessor outside view")
        value = np.ndarray((count, width), dtype=dtype, buffer=self.binary,
            offset=start + offset, strides=(stride, dtype.itemsize)).copy()
        require(np.isfinite(value).all(), "Nonfinite accessor")
        self.cache[index] = value
        return value

    def node(self, name):
        return self.doc["nodes"][self.names.index(name)]

    def mesh(self, name):
        return self.doc["meshes"][self.node(name)["mesh"]]


def semantic_nodes(glb):
    result = {}
    for node in glb.doc["nodes"]:
        value = copy.deepcopy(node)
        if "mesh" in value:
            value["mesh"] = glb.doc["meshes"][value["mesh"]]["name"]
        if "skin" in value:
            value["skin"] = glb.doc["skins"][value["skin"]].get("name")
        if "children" in value:
            value["children"] = sorted(glb.names[index] for index in value["children"])
        result[node["name"]] = value
    return result


def protected_payload(glb, mesh, primitive):
    attributes = primitive["attributes"]
    keys = sorted(key for key in attributes if not key.startswith("TEXCOORD_"))
    columns, tolerances = [], []
    for key in keys:
        array = glb.accessor(attributes[key])
        columns.append(array.astype(np.float64))
        tolerance = 0.0 if key.startswith("JOINTS_") else NORMAL_WEIGHT_TOLERANCE if key in ("NORMAL", "WEIGHTS_0") else POSITION_TOLERANCE
        tolerances.extend([tolerance] * array.shape[1])
    targets = mesh.get("extras", {}).get("targetNames", [])
    require(len(targets) == len(primitive.get("targets", [])), "Morph name/count mismatch")
    if targets:
        require(len(targets) == 21 and set(targets) == set(TARGETS), "Required facial target set changed")
    for target in primitive.get("targets", []):
        require(set(target) == {"POSITION"}, "Unexpected morph attribute change")
        array = glb.accessor(target["POSITION"])
        columns.append(array.astype(np.float64))
        tolerances.extend([POSITION_TOLERANCE] * array.shape[1])
    sizes = {len(column) for column in columns}
    require(len(sizes) == 1, "Vertex/skin/morph count mismatch")
    return keys, targets, np.hstack(columns), np.array(tolerances)


def match_vertices(old_positions, old_payload, new_positions, new_payload, tolerances):
    require(old_payload.shape[1] == new_payload.shape[1], "Protected vertex width changed")
    representatives = {}
    old_ids = np.empty(len(old_payload), dtype=np.int64)
    for index, row in enumerate(old_payload):
        signature = row.tobytes()
        old_ids[index] = representatives.setdefault(signature, index)
    tree = None
    new_ids = np.empty(len(new_payload), dtype=np.int64)
    maximum_error = np.zeros(old_payload.shape[1])
    for index, row in enumerate(new_payload):
        exact = representatives.get(row.tobytes())
        if exact is not None:
            new_ids[index] = exact
            continue
        if tree is None:
            tree = cKDTree(old_positions)
        nearby = tree.query_ball_point(new_positions[index], POSITION_TOLERANCE * 1.75)
        matched = None
        for source_index in sorted(nearby):
            difference = np.abs(row - old_payload[source_index])
            if np.all(difference <= tolerances):
                matched = source_index
                maximum_error = np.maximum(maximum_error, difference)
                break
        require(matched is not None, f"Vertex {index}: geometry, normal, skin or facial morph changed")
        new_ids[index] = old_ids[matched]
    return old_ids, new_ids, float(maximum_error.max(initial=0.0))


def oriented_triangles(glb, primitive, identities):
    require(primitive.get("mode", 4) == 4, "Non-triangle primitive")
    indices = glb.accessor(primitive["indices"]).reshape(-1)
    require(len(indices) % 3 == 0 and int(indices.max()) < len(identities), "Invalid triangle indices")
    triangles = identities[indices].reshape(-1, 3)
    # Cyclic rotation preserves winding; sorting all three corners would hide reversal.
    return Counter(min((int(a), int(b), int(c)), (int(b), int(c), int(a)), (int(c), int(a), int(b)))
        for a, b, c in triangles)


def compare_geometry(source, candidate):
    require(semantic_nodes(source) == semantic_nodes(candidate), "Node hierarchy, rig pivot or rest transform changed")
    require(source.doc.get("scene") == candidate.doc.get("scene"), "Active scene changed")
    old_scenes = [[source.names[i] for i in scene["nodes"]] for scene in source.doc["scenes"]]
    new_scenes = [[candidate.names[i] for i in scene["nodes"]] for scene in candidate.doc["scenes"]]
    require(old_scenes == new_scenes, "Scene roots changed")
    require(len(source.doc["skins"]) == len(candidate.doc["skins"]), "Skin count changed")
    for old, new in zip(source.doc["skins"], candidate.doc["skins"]):
        require([source.names[i] for i in old["joints"]] == [candidate.names[i] for i in new["joints"]], "Skin joints changed")
        require(source.names[old["skeleton"]] == candidate.names[new["skeleton"]], "Skin skeleton changed")
        require(np.array_equal(source.accessor(old["inverseBindMatrices"]), candidate.accessor(new["inverseBindMatrices"])), "Inverse bind matrices changed")
    old_meshes = {mesh["name"]: mesh for mesh in source.doc["meshes"]}
    new_meshes = {mesh["name"]: mesh for mesh in candidate.doc["meshes"]}
    require(set(old_meshes) == set(new_meshes), "Mesh names/count changed")
    records = []
    total_triangles = 0
    total_vertex_references = 0
    for name, old_mesh in old_meshes.items():
        new_mesh = new_meshes[name]
        require(old_mesh.get("weights") == new_mesh.get("weights"), "Default morph pose changed")
        require(len(old_mesh["primitives"]) == len(new_mesh["primitives"]), "Primitive count changed")
        for ordinal, (old, new) in enumerate(zip(old_mesh["primitives"], new_mesh["primitives"])):
            old_keys, old_targets, old_payload, tolerances = protected_payload(source, old_mesh, old)
            new_keys, new_targets, new_payload, _ = protected_payload(candidate, new_mesh, new)
            require(old_keys == new_keys and old_targets == new_targets, "Protected attributes/target ordering changed")
            old_positions = source.accessor(old["attributes"]["POSITION"])
            new_positions = candidate.accessor(new["attributes"]["POSITION"])
            old_ids, new_ids, maximum_error = match_vertices(old_positions, old_payload, new_positions, new_payload, tolerances)
            before = oriented_triangles(source, old, old_ids)
            after = oriented_triangles(candidate, new, new_ids)
            require(before == after, f"Rendered triangle topology/winding changed: {name}/{ordinal}")
            triangles = sum(after.values())
            total_triangles += triangles
            total_vertex_references += len(new_positions)
            records.append({"mesh": name, "primitive": ordinal,
                "sourceVertices": len(old_positions), "candidateVertices": len(new_positions),
                "triangles": triangles, "morphTargets": len(new_targets),
                "maximumProtectedScalarDifference": maximum_error})
    require(total_triangles <= 200000 and total_vertex_references <= 100000, "Runtime geometry budget exceeded")
    return {"status": "PASS", "method": "Oriented rendered triangles and all protected vertex/morph/skin attributes; seam duplication permitted",
        "nodeAndRigTransformsUnchanged": True, "inverseBindMatricesUnchanged": True,
        "positionAndMorphToleranceMetres": POSITION_TOLERANCE,
        "normalAndWeightTolerance": NORMAL_WEIGHT_TOLERANCE,
        "triangles": total_triangles, "primitiveVertexReferences": total_vertex_references, "primitives": records}


def inspect_uv(glb, size):
    primitive = glb.mesh("Face")["primitives"][0]
    positions = glb.accessor(primitive["attributes"]["POSITION"])
    uv = glb.accessor(primitive["attributes"]["TEXCOORD_0"])
    indices = glb.accessor(primitive["indices"]).reshape(-1, 3)
    require(uv.shape == (len(positions), 2) and np.isfinite(uv).all(), "Invalid face UV array")
    require(np.all(uv >= 0) and np.all(uv <= 1), "Non-atlas UV range")
    occupied = np.zeros((size, size), dtype=bool)
    owner = np.full((size, size), -1, dtype=np.int32)
    ambiguous = np.zeros_like(occupied)
    position_map = np.zeros((size, size, 3), dtype=np.float32)
    maximum_conflict = 0.0
    degenerate = 0
    sampled_triangles = 0
    conflict_pairs = Counter()
    for triangle_index, triangle in enumerate(indices):
        points = uv[triangle] * (size - 1)
        low = np.maximum(0, np.floor(points.min(axis=0)).astype(int))
        high = np.minimum(size - 1, np.ceil(points.max(axis=0)).astype(int))
        a, b, c = points
        denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(float(denominator)) < 1e-8:
            degenerate += 1
            continue
        yy, xx = np.mgrid[low[1]:high[1] + 1, low[0]:high[0] + 1]
        wa = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / denominator
        wb = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / denominator
        wc = 1 - wa - wb
        inside = (wa > 1e-4) & (wb > 1e-4) & (wc > 1e-4)
        sampled_triangles += bool(inside.any())
        region = np.s_[low[1]:high[1] + 1, low[0]:high[0] + 1]
        values = wa[..., None] * positions[triangle[0]] + wb[..., None] * positions[triangle[1]] + wc[..., None] * positions[triangle[2]]
        collision = inside & occupied[region]
        distance = np.linalg.norm(values - position_map[region], axis=-1)
        conflicting = collision & (distance > 0.001)
        if collision.any():
            maximum_conflict = max(maximum_conflict, float(distance[collision].max()))
        if conflicting.any():
            prior, counts = np.unique(owner[region][conflicting], return_counts=True)
            for prior_index, count in zip(prior, counts):
                conflict_pairs[(int(prior_index), triangle_index)] += int(count)
        ambiguous[region] |= conflicting
        position_map[region][inside] = values[inside]
        occupied[region] |= inside
        owner[region][inside] = triangle_index
    return {"status": "PASS" if not ambiguous.any() and degenerate == 0 else "FAIL",
        "samplingOnly": True, "rasterPixels": [size, size], "triangleCount": len(indices),
        "sampledTriangles": sampled_triangles, "coveredTexels": int(occupied.sum()),
        "conflictingTexelsOverOneMillimetre": int(ambiguous.sum()),
        "maximumConflictingPositionDifferenceMetres": maximum_conflict,
        "degenerateUvTriangles": degenerate,
        "conflictingTrianglePairs": [{"earlierTriangle": a, "laterTriangle": b, "sampledConflicts": count}
            for (a, b), count in sorted(conflict_pairs.items())],
        "criterion": "Strict triangle interior (barycentric > 1e-4); repeated texel with surface positions differing > 1 mm",
        "limitations": ["Finite raster sampling does not prove absence of all continuous/subtexel UV intersections", "No texture likeness or device rendering acceptance"]}


def negative_controls(source, candidate):
    controls = []
    face = candidate.mesh("Face")["primitives"][0]
    first_vertex = int(candidate.accessor(face["indices"])[0, 0])
    definitions = [
        ("face_position_plus_1mm", lambda g: g.accessor(face["attributes"]["POSITION"]).__setitem__((first_vertex, 0), g.accessor(face["attributes"]["POSITION"])[first_vertex, 0] + .001)),
        ("aa_morph_plus_1mm", lambda g: g.accessor(face["targets"][TARGETS.index("AA")]["POSITION"]).__setitem__((first_vertex, 1), g.accessor(face["targets"][TARGETS.index("AA")]["POSITION"])[first_vertex, 1] + .001)),
        ("jaw_pivot_plus_1mm", lambda g: g.node("Jaw")["translation"].__setitem__(1, g.node("Jaw")["translation"][1] + .001)),
        ("triangle_winding_reversed", lambda g: g.accessor(face["indices"]).__setitem__(slice(0, 3), g.accessor(face["indices"])[[0, 2, 1]].copy())),
        ("skin_weight_corrupted", lambda g: g.accessor(face["attributes"]["WEIGHTS_0"]).__setitem__((first_vertex, 0), .314159)),
    ]
    for name, mutate in definitions:
        changed = copy.deepcopy(candidate)
        mutate(changed)
        try:
            compare_geometry(source, changed)
        except ValueError as error:
            controls.append({"mutation": name, "status": "REJECTED_AS_EXPECTED", "reason": str(error)})
        else:
            raise ValueError("Negative control was accepted: " + name)
    changed = copy.deepcopy(candidate)
    uv = changed.accessor(face["attributes"]["TEXCOORD_0"])
    uv[:] = .5
    require(inspect_uv(changed, 128)["status"] == "FAIL", "Collapsed UV negative control was accepted")
    controls.append({"mutation": "all_face_uv_collapsed", "status": "REJECTED_AS_EXPECTED"})
    return controls


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--uv-size", type=int, default=1024)
    parser.add_argument("--negative-controls", action="store_true")
    args = parser.parse_args()
    require(args.uv_size in (128, 256, 512, 1024, 2048), "Choose a bounded power-of-two UV raster")
    require(args.output.resolve() not in (args.source.resolve(), args.candidate.resolve()), "Report must not overwrite an input")
    source_raw, candidate_raw = args.source.read_bytes(), args.candidate.read_bytes()
    record = {"schema": "continuum-reference-candidate-independent-review-v1",
        "sourceSha256": hashlib.sha256(source_raw).hexdigest(),
        "candidateSha256": hashlib.sha256(candidate_raw).hexdigest(),
        "sourceBytes": len(source_raw), "candidateBytes": len(candidate_raw),
        "verifierSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "evidenceClass": "HOST_NUMERICAL_NOT_DEVICE", "status": "FAIL"}
    try:
        source, candidate = Glb(source_raw), Glb(candidate_raw)
        record["geometry"] = compare_geometry(source, candidate)
        record["uv"] = inspect_uv(candidate, args.uv_size)
        if args.negative_controls:
            record["negativeControls"] = negative_controls(source, candidate)
        record["status"] = "PASS" if record["uv"]["status"] == "PASS" else "FAIL"
    except (ValueError, KeyError, IndexError, TypeError, struct.error) as error:
        record["failure"] = str(error)
    record["limitations"] = ["No native Filament, Fold7, performance, speech synchronization or reference-likeness certification", "Historical independent V1 rig-window result remains separate and unchanged"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps({key: value for key, value in record.items() if key not in ("geometry", "limitations")}))
    return 0 if record["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
