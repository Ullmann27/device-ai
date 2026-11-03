#!/usr/bin/env python3
"""Independent canonical glTF pose/orthographic occlusion diagnostic.

Uses actual exported production angles/morph weights, not the Blender replay.
Skinning: sum(weight * jointWorld * inverseBind * morphedPosition).
Rigid jaw: parentWorld * restLocal * Ry(yaw) * Rx(pitch), as production Filament.
Positive-Z camera. A tooth vertex ahead of a projected front face triangle is an
occlusion/penetration indicator, not a complete mesh-intersection certification.
"""
import os
os.environ["OPENBLAS_NUM_THREADS"] = "1"
os.environ["OMP_NUM_THREADS"] = "1"
import hashlib
import json
from pathlib import Path
import struct
import sys
import numpy as np

asset, pose_file, output = map(Path, sys.argv[1:])
raw = asset.read_bytes()
length, kind = struct.unpack_from("<II", raw, 12)
assert kind == 0x4E4F534A
doc = json.loads(raw[20:20 + length])
size, kind = struct.unpack_from("<II", raw, 20 + length)
assert kind == 0x004E4942
binary = raw[28 + length:28 + length + size]
parents = {}
for i, node in enumerate(doc["nodes"]):
    for child in node.get("children", []):
        assert child not in parents
        parents[child] = i


def accessor(index):
    a = doc["accessors"][index]
    assert "sparse" not in a
    v = doc["bufferViews"][a["bufferView"]]
    dtype = np.dtype({5121: "u1", 5123: "<u2", 5125: "<u4", 5126: "<f4"}[a["componentType"]])
    width = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}[a["type"]]
    stride = v.get("byteStride", width * dtype.itemsize)
    start = v.get("byteOffset", 0) + a.get("byteOffset", 0)
    return np.ndarray((a["count"], width), dtype=dtype, buffer=binary,
        offset=start, strides=(stride, dtype.itemsize)).copy()


def local(node):
    if "matrix" in node:
        return np.array(node["matrix"], dtype=np.float64).reshape(4, 4).T
    x, y, z, w = node.get("rotation", [0, 0, 0, 1])
    result = np.array([
        [1 - 2*y*y - 2*z*z, 2*x*y - 2*z*w, 2*x*z + 2*y*w, 0],
        [2*x*y + 2*z*w, 1 - 2*x*x - 2*z*z, 2*y*z - 2*x*w, 0],
        [2*x*z - 2*y*w, 2*y*z + 2*x*w, 1 - 2*x*x - 2*y*y, 0],
        [0, 0, 0, 1]], dtype=np.float64)
    result[:, :3] *= np.array(node.get("scale", [1, 1, 1]))
    result[:3, 3] = node.get("translation", [0, 0, 0])
    return result


def rotation(yaw, pitch):
    y, p = np.deg2rad([yaw, pitch])
    ry = np.array([[np.cos(y), 0, np.sin(y), 0], [0, 1, 0, 0],
        [-np.sin(y), 0, np.cos(y), 0], [0, 0, 0, 1]])
    rx = np.array([[1, 0, 0, 0], [0, np.cos(p), -np.sin(p), 0],
        [0, np.sin(p), np.cos(p), 0], [0, 0, 0, 1]])
    return ry @ rx


def worlds(pose):
    cache = {}
    def world(index):
        if index in cache:
            return cache[index]
        node = doc["nodes"][index]
        m = local(node)
        name = node.get("name")
        if name == "Head":
            m = m @ rotation(pose["headYaw"], pose["headPitch"])
        elif name in ["EyeLeft", "EyeRight"]:
            m = m @ rotation(pose["eyeYaw"], pose["eyePitch"])
        elif name == "Jaw":
            m = m @ rotation(0, pose["jawPitch"])
        cache[index] = world(parents[index]) @ m if index in parents else m
        return cache[index]
    return [world(i) for i in range(len(doc["nodes"]))]


def vertices(node_index, pose, transforms):
    node = doc["nodes"][node_index]
    mesh = doc["meshes"][node["mesh"]]
    primitive = mesh["primitives"][0]
    p = accessor(primitive["attributes"]["POSITION"]).astype(np.float64)
    names = mesh.get("extras", {}).get("targetNames", [])
    for name, target in zip(names, primitive.get("targets", [])):
        weight = pose["weights"].get(name, 0)
        if weight:
            p += accessor(target["POSITION"]) * weight
    p = np.column_stack([p, np.ones(len(p))])
    if "skin" in node:
        skin = doc["skins"][node["skin"]]
        inverse = accessor(skin["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1)
        joints = accessor(primitive["attributes"]["JOINTS_0"])
        weights = accessor(primitive["attributes"]["WEIGHTS_0"])
        matrices = np.array([transforms[j] @ inv for j, inv in zip(skin["joints"], inverse)])
        result = np.zeros_like(p)
        for slot in range(joints.shape[1]):
            result += np.einsum("nij,nj->ni", matrices[joints[:, slot]], p) * weights[:, slot, None]
        return result[:, :3]
    return (transforms[node_index] @ p.T).T[:, :3]


face_index = next(i for i, n in enumerate(doc["nodes"]) if n.get("name") == "Face")
face_primitive = doc["meshes"][doc["nodes"][face_index]["mesh"]]["primitives"][0]
triangles = accessor(face_primitive["indices"]).reshape(-1, 3)
teeth = [(i, n["name"]) for i, n in enumerate(doc["nodes"]) if n.get("name", "").startswith("Tooth")]
export = json.loads(pose_file.read_text())
selected = [f for f in export["frames"] if f["sequence"] == "speech-reachable"]
selected.insert(0, next(f for f in export["frames"] if f["sequence"] == "idle-anchors" and f["frame"] == 0))
records = []
for pose in selected:
    transforms = worlds(pose)
    face = vertices(face_index, pose, transforms)
    tri = face[triangles]
    a, b, c = tri[:, 0], tri[:, 1], tri[:, 2]
    denom = (b[:, 1] - c[:, 1]) * (a[:, 0] - c[:, 0]) + (c[:, 0] - b[:, 0]) * (a[:, 1] - c[:, 1])
    valid = np.abs(denom) > 1e-14
    safe_denom = np.where(valid, denom, 1)
    findings = []
    intersections = []
    face_min, face_max = tri.min(axis=1), tri.max(axis=1)
    total = 0
    for index, name in teeth:
        tooth_points = vertices(index, pose, transforms)
        tooth_primitive = doc["meshes"][doc["nodes"][index]["mesh"]]["primitives"][0]
        tooth_triangles = accessor(tooth_primitive["indices"]).reshape(-1, 3)
        edges = sorted({tuple(sorted((int(a), int(b)))) for triangle in tooth_triangles
            for a, b in [(triangle[0], triangle[1]), (triangle[1], triangle[2]), (triangle[2], triangle[0])]})
        nearby = np.flatnonzero(np.all(face_max >= tooth_points.min(axis=0) - 1e-8, axis=1) &
            np.all(face_min <= tooth_points.max(axis=0) + 1e-8, axis=1))
        near_tri = tri[nearby]
        if len(nearby):
            edge1, edge2 = near_tri[:, 1] - near_tri[:, 0], near_tri[:, 2] - near_tri[:, 0]
            for start, end in edges:
                origin = tooth_points[start]
                direction = tooth_points[end] - origin
                pvec = np.cross(np.broadcast_to(direction, edge2.shape), edge2)
                determinant = np.einsum("ij,ij->i", edge1, pvec)
                good = np.abs(determinant) > 1e-15
                inverse_det = np.divide(1., determinant, out=np.zeros_like(determinant), where=good)
                tvec = origin - near_tri[:, 0]
                bary_u = np.einsum("ij,ij->i", tvec, pvec) * inverse_det
                qvec = np.cross(tvec, edge1)
                bary_v = (qvec @ direction) * inverse_det
                distance = np.einsum("ij,ij->i", edge2, qvec) * inverse_det
                hits = good & (bary_u > 1e-7) & (bary_v > 1e-7) & (bary_u + bary_v < 1 - 1e-7) & \
                    (distance > 1e-6) & (distance < 1 - 1e-6)
                for hit in np.flatnonzero(hits):
                    intersections.append({"node": name, "edgeVertexIndices": [start, end],
                        "faceTriangle": int(nearby[hit]), "segmentFraction": float(distance[hit]),
                        "faceBarycentric": [float(1 - bary_u[hit] - bary_v[hit]), float(bary_u[hit]), float(bary_v[hit])],
                        "toothEdgeWorld": [origin.tolist(), tooth_points[end].tolist()],
                        "faceTriangleWorld": near_tri[hit].tolist(),
                        "intersectionWorld": (origin + direction * distance[hit]).tolist()})
        for vertex_index, point in enumerate(tooth_points):
            total += 1
            u = ((b[:, 1] - c[:, 1]) * (point[0] - c[:, 0]) + (c[:, 0] - b[:, 0]) * (point[1] - c[:, 1])) / safe_denom
            v = ((c[:, 1] - a[:, 1]) * (point[0] - c[:, 0]) + (a[:, 0] - c[:, 0]) * (point[1] - c[:, 1])) / safe_denom
            inside = valid & (u >= -1e-7) & (v >= -1e-7) & (u + v <= 1 + 1e-7)
            if not np.any(inside):
                continue
            depth = u * a[:, 2] + v * b[:, 2] + (1 - u - v) * c[:, 2]
            hit = int(np.argmax(np.where(inside, depth, -np.inf)))
            front_z = depth[hit]
            gap = point[2] - front_z
            # Canonical face front lies at positive Z. Exclude the rear skull seen through
            # the open mouth; retain the exact triangle/coordinates for independent review.
            if front_z > .09 and gap > .001:
                findings.append({"node": name, "vertex": vertex_index,
                    "toothWorld": point.tolist(), "frontFaceTriangle": hit,
                    "frontFaceZ": float(front_z), "toothAheadMetres": float(gap)})
    findings.sort(key=lambda f: f["toothAheadMetres"], reverse=True)
    frontmost_intersections = []
    for crossing in intersections:
        point = np.array(crossing["intersectionWorld"])
        u = ((b[:, 1] - c[:, 1]) * (point[0] - c[:, 0]) + (c[:, 0] - b[:, 0]) * (point[1] - c[:, 1])) / safe_denom
        v = ((c[:, 1] - a[:, 1]) * (point[0] - c[:, 0]) + (a[:, 0] - c[:, 0]) * (point[1] - c[:, 1])) / safe_denom
        inside = valid & (u >= -1e-7) & (v >= -1e-7) & (u + v <= 1 + 1e-7)
        depth = u * a[:, 2] + v * b[:, 2] + (1 - u - v) * c[:, 2]
        front_z = float(np.max(np.where(inside, depth, -np.inf)))
        if abs(point[2] - front_z) <= 1e-5:
            frontmost_intersections.append({**crossing, "frontmostSurfaceZ": front_z})
    records.append({"pose": pose, "testedToothVertices": total, "indicatorCount": len(findings),
        "maximumToothAheadMetres": max([f["toothAheadMetres"] for f in findings], default=0),
        "affectedNodes": sorted({f["node"] for f in findings}), "largestIndicators": findings[:12],
        "faceWorldFloat64Sha256": hashlib.sha256(face.tobytes()).hexdigest(),
        "strictToothEdgeFaceTriangleIntersections": len(intersections),
        "intersectingNodes": sorted({x["node"] for x in intersections}),
        "firstStrictIntersections": intersections[:20],
        "frontmostStrictIntersections": len(frontmost_intersections),
        "frontmostIntersectingNodes": sorted({x["node"] for x in frontmost_intersections}),
        "firstFrontmostIntersections": frontmost_intersections[:20],
        "jawWorldMatrixRowMajor": transforms[next(i for i, n in enumerate(doc["nodes"]) if n.get("name") == "Jaw")].tolist()})
result = {"schema": "independent-canonical-mouth-occlusion-diagnostic", "version": 1,
    "status": "FAIL_FRONTMOST_TOOTH_FACE_INTERSECTION" if any(x["frontmostStrictIntersections"] for x in records) else "PASS_SAMPLED_FRONTMOST_INTERSECTIONS",
    "assetSha256": hashlib.sha256(raw).hexdigest(), "poseSha256": hashlib.sha256(pose_file.read_bytes()).hexdigest(),
    "productionMapperSource": export["sourceSha"], "cameraDirection": "canonical +Z orthographic",
    "thresholds": {"frontFaceMinimumZMetres": .09, "minimumAheadMetres": .001,
        "frontmostIntersectionDepthToleranceMetres": 1e-5},
    "limits": ["Projection indicators include correctly visible teeth inside the aperture; do not classify them as penetration",
        "Strict noncoplanar edge/triangle intersections are separate; incomplete collision sampling is not proof of absence",
        "Independent canonical glTF math, not Blender/Filament execution",
        "Synthetic production mapper poses, no audio/device evidence"], "records": records}
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps({"asset": result["assetSha256"], "cases": [{"sequence": x["pose"]["sequence"],
    "frame": x["pose"]["frame"], "projectionIndicators": x["indicatorCount"],
    "strictIntersections": x["strictToothEdgeFaceTriangleIntersections"],
    "frontmostIntersections": x["frontmostStrictIntersections"]} for x in records]}))
sys.exit(1 if result["status"].startswith("FAIL") else 0)
