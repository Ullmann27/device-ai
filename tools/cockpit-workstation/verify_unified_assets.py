"""Check packaged scene geometry contracts."""
import argparse
import hashlib
import json
import math
import pathlib
import struct
from verify_scene import intersection_area

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT/"app/src/main/assets"
AVATAR_SHA = "c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Glb:
    def __init__(self, path):
        self.path, raw = path, path.read_bytes()
        assert struct.unpack_from("<III", raw) == (0x46546c67, 2, len(raw))
        size, kind = struct.unpack_from("<II", raw, 12)
        assert kind == 0x4e4f534a
        self.doc = json.loads(raw[20:20+size])
        start = 20+size
        binary_size, binary_kind = struct.unpack_from("<II", raw, start)
        assert binary_kind == 0x004e4942
        self.binary = raw[start+8:start+8+binary_size]
        assert len(self.binary) == binary_size
        assert not any("uri" in b for b in self.doc.get("buffers", []))
        assert not any("uri" in i for i in self.doc.get("images", []))
        self.parent = {c: i for i, node in enumerate(self.doc["nodes"]) for c in node.get("children", [])}

    def accessor(self, index):
        accessor = self.doc["accessors"][index]
        assert "sparse" not in accessor
        view = self.doc["bufferViews"][accessor["bufferView"]]
        count = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[accessor["type"]]
        fmt = {5121: "B", 5123: "H", 5125: "I", 5126: "f"}[accessor["componentType"]]
        packed = "<" + fmt*count
        stride = view.get("byteStride", struct.calcsize(packed))
        offset = view.get("byteOffset", 0)+accessor.get("byteOffset", 0)
        return [struct.unpack_from(packed, self.binary, offset+i*stride) for i in range(accessor["count"])]

    def node(self, name):
        found = [(i, node) for i, node in enumerate(self.doc["nodes"]) if node.get("name") == name]
        assert len(found) == 1, (name, len(found))
        return found[0]

    def point(self, index, point):
        node = self.doc["nodes"][index]
        p = list(point)
        if "matrix" in node:
            matrix = node["matrix"]
            p = [sum(matrix[j*4+i]*p[j] for j in range(3))+matrix[12+i] for i in range(3)]
        else:
            p = [p[i]*node.get("scale", [1, 1, 1])[i] for i in range(3)]
            x, y, z, w = node.get("rotation", [0, 0, 0, 1])
            q = [x, y, z]
            uv = [q[1]*p[2]-q[2]*p[1], q[2]*p[0]-q[0]*p[2], q[0]*p[1]-q[1]*p[0]]
            uuv = [q[1]*uv[2]-q[2]*uv[1], q[2]*uv[0]-q[0]*uv[2], q[0]*uv[1]-q[1]*uv[0]]
            p = [p[i]+2*(w*uv[i]+uuv[i])+node.get("translation", [0, 0, 0])[i] for i in range(3)]
        return self.point(self.parent[index], p) if index in self.parent else p

    def geometry(self):
        vertices, triangles = 0, 0
        for mesh in self.doc["meshes"]:
            for primitive in mesh["primitives"]:
                assert primitive.get("mode", 4) == 4
                positions = self.accessor(primitive["attributes"]["POSITION"])
                normals = self.accessor(primitive["attributes"]["NORMAL"])
                assert len(positions) == len(normals)
                assert all(math.isfinite(c) for p in positions+normals for c in p)
                assert all(.99 < sum(c*c for c in n) < 1.01 for n in normals)
                indices = [i[0] for i in self.accessor(primitive["indices"])]
                assert len(indices) % 3 == 0
                assert all(0 <= i < len(positions) for i in indices)
                for i in range(0, len(indices), 3):
                    assert len(set(indices[i:i+3])) == 3
                vertices += len(positions)
                triangles += len(indices)//3
        return {"unique_vertices": vertices, "unique_triangles": triangles,
                "meshes": len(self.doc["meshes"]), "nodes": len(self.doc["nodes"])}


def monitor_contract(glb):
    glb.node("cockpit-room")
    monitor_id, _ = glb.node("cockpit-monitor")
    index, node = glb.node("monitor-live-pixels")
    assert glb.parent[index] == monitor_id
    primitives = glb.doc["meshes"][node["mesh"]]["primitives"]
    assert len(primitives) == 1
    primitive = primitives[0]
    mat = glb.doc["materials"][primitive["material"]]
    assert mat["name"] == "monitor-live-pixels"
    assert mat.get("alphaMode", "OPAQUE") == "OPAQUE"
    assert "KHR_materials_unlit" in mat.get("extensions", {})
    positions = glb.accessor(primitive["attributes"]["POSITION"])
    uvs = glb.accessor(primitive["attributes"]["TEXCOORD_0"])
    expected = [((-.465, .38, .032), (0, 0)), ((-.465, -.445, .032), (0, 1)),
                ((.465, -.445, .032), (1, 1)), ((.465, .38, .032), (1, 0))]
    for position, uv in zip(positions, uvs):
        position = glb.point(index, position)
        assert any(max(abs(a-b) for a, b in zip(position, p)) < .00001 and
                   max(abs(a-b) for a, b in zip(uv, u)) < .00001 for p, u in expected), (position, uv)
    assert len(positions) == 4
    assert len(glb.accessor(primitive["indices"])) == 6


def reference_contract(glb):
    roots = {glb.doc["nodes"][index]["name"] for index in glb.doc["scenes"][0]["nodes"]}
    assert roots == {"cockpit-room", "cockpit-monitor", "cockpit-side-monitors"}
    side_id, side_root = glb.node("cockpit-side-monitors")
    extras = side_root["extras"]
    assert extras["native_plane_z"] == 0
    for key, expected in [("native_document_bounds", [.02, .57, .22, .87]),
                          ("native_draft_bounds", [.78, .57, .98, .87])]:
        assert max(abs(a-b) for a, b in zip(extras[key], expected)) < 1e-6
    apertures = {name: (-.465, -.445, .465, .38)
                 for name in ("monitor-bezel", "monitor-glass", "monitor-rim")}
    for side in (-1, 1):
        center = side * .38
        for suffix in ("bezel", "glass", "rim"):
            name = "side-%s-%s" % (side, suffix)
            index, _ = glb.node(name)
            assert glb.parent[index] == side_id
            apertures[name] = (center-.1, -.37, center+.1, -.07)
    for name, rectangle in apertures.items():
        index, node = glb.node(name)
        for primitive in glb.doc["meshes"][node["mesh"]]["primitives"]:
            points = [glb.point(index, point) for point in glb.accessor(primitive["attributes"]["POSITION"])]
            if name.endswith("-bezel") and name.startswith("side-"):
                assert all(abs(point[2]) < 1e-7 for point in points)
            indices = [value[0] for value in glb.accessor(primitive["indices"])]
            for offset in range(0, len(indices), 3):
                triangle = [points[i][:2] for i in indices[offset:offset+3]]
                assert intersection_area(triangle, rectangle) < 1e-9, (name, "native opening")
    assert not any(node.get("name", "").startswith(("key-bezel-", "key-inlay-", "arch-", "wing-"))
                   for node in glb.doc["nodes"])
    seed = Glb(ROOT/"tools/cockpit-workstation/source/pr801-workstation.glb")
    used = {p["material"] for mesh in seed.doc["meshes"] for p in mesh["primitives"]}
    actual = {entry["name"]: entry for entry in glb.doc["materials"]}
    for index in used:
        expected = seed.doc["materials"][index]
        entry = actual[expected["name"]]
        for key, default in (("alphaMode", "OPAQUE"), ("doubleSided", False)):
            assert entry.get(key, default) == expected.get(key, default), (expected["name"], key)
        for key, default in (("baseColorFactor", [1, 1, 1, 1]), ("metallicFactor", 1), ("roughnessFactor", 1)):
            left = expected["pbrMetallicRoughness"].get(key, default)
            right = entry["pbrMetallicRoughness"].get(key, default)
            if isinstance(left, list):
                assert max(abs(a-b) for a, b in zip(left, right)) < 1e-6, (expected["name"], key)
            else:
                assert abs(left-right) < 1e-6, (expected["name"], key)
        assert max(abs(a-b) for a, b in zip(expected.get("emissiveFactor", [0, 0, 0]),
                                           entry.get("emissiveFactor", [0, 0, 0]))) < 1e-6


def orb_contract(glb):
    root_id, root = glb.node("continuum-orb")
    assert root.get("translation", [0, 0, 0]) == [0, 0, 0]
    assert root.get("scale", [1, 1, 1]) == [1, 1, 1]
    for i in range(4):
        node_id, _ = glb.node("orb-orbit-" + str(i))
        assert glb.parent[node_id] == root_id
    for name in ("orb-original-infinity", "orb-containment-shell", "orb-core", "orb-spherical-grid",
                 "orb-brain-lobe--1", "orb-brain-lobe-1", "orb-neural-links-cyan", "orb-pedestal-rings"):
        _, node = glb.node(name)
        assert "mesh" in node
    index, node = glb.node("orb-original-infinity")
    primitive = glb.doc["meshes"][node["mesh"]]["primitives"][0]
    assert all(glb.point(index, p)[2] > .98 for p in glb.accessor(primitive["attributes"]["POSITION"]))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-manifest", action="store_true")
    args = parser.parse_args()
    assert sha(ASSETS/"avatar3d/continuum-avatar.glb") == AVATAR_SHA
    paths = {"workstation": "cinema3d/cockpit-workstation.glb", "orb": "cinema3d/continuum-orb.glb",
             "monitorMaterial": "cinema3d/live-monitor.filamat"}
    manifest, report = {"version": 1}, {}
    for key, path in paths.items():
        file = ASSETS/path
        size = file.stat().st_size
        assert 0 < size <= (1024*1024 if key == "monitorMaterial" else 2*1024*1024), (path, size)
        manifest[key] = {"path": path, "sha256": sha(file), "bytes": size}
        if file.suffix == ".glb":
            glb = Glb(file)
            report[key] = glb.geometry()
            (monitor_contract if key == "workstation" else orb_contract)(glb)
            if key == "workstation":
                reference_contract(glb)
    destination = ASSETS/"cinema3d/cockpit-world.json"
    if args.write_manifest:
        destination.write_text(json.dumps(manifest, indent=2)+"\n")
    else:
        assert json.loads(destination.read_text()) == manifest
    print(json.dumps({"checks": "PASS", "asset_manifest": manifest, "geometry": report,
                      "original_avatar": "SHA_UNCHANGED", "device": "NOT_RUN"}, indent=2))


if __name__ == "__main__":
    main()
