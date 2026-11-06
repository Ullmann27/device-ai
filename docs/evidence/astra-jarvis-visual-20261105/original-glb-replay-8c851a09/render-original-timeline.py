"""Replay validated mapper frames on the unchanged production GLB.

HOST_BLENDER_NOT_FILAMENT_DEVICE. No generated or retouched imagery.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET

import bpy
from mathutils import Matrix, Quaternion, Vector
from bpy_extras.object_utils import world_to_camera_view

ASSET_SHA = "c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a"
TARGETS = ["SIL", "PP", "FF", "TH", "DD", "KK", "CH", "SS", "NN", "RR", "AA", "E", "I", "O", "U",
           "BlinkLeft", "BlinkRight", "BrowUp", "BrowDown", "Smile", "JawOpen"]
EVIDENCE_CLASS = "HOST_BLENDER_NOT_FILAMENT_DEVICE"
EXPECTED_TESTS = {
    "conversationSceneDrivesEyesHeadMouthAndStopsCleanly",
    "windowIntentLeadsWithEyesAndRestoresCurrentCameraGaze",
    "windowIntentExpiresAndClearRejectsQueuedReplay",
    "listeningSpeechAndStopClearWindowIntentWithoutInventingAudio",
    "explicitConfirmationMakesOneBoundedNodWithCooldown",
    "confirmationRespectsFramingBoundsAndReducedMotion",
    "thinkingHasSubtleMotionWithoutOpeningMouth",
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def serial_matrix(matrix):
    return [[float(value) for value in row] for row in matrix]


def local_rest(node):
    if "matrix" in node:
        values = node["matrix"]
        return Matrix([[values[col * 4 + row] for col in range(4)] for row in range(4)])
    x, y, z, w = node.get("rotation", [0, 0, 0, 1])
    rotation = Quaternion((w, x, y, z)).to_matrix().to_4x4()
    scale = Matrix.Diagonal(Vector((*node.get("scale", [1, 1, 1]), 1)))
    return Matrix.Translation(Vector(node.get("translation", [0, 0, 0]))) @ rotation @ scale


def rotation(yaw, pitch):
    return Matrix.Rotation(math.radians(yaw), 4, "Y") @ Matrix.Rotation(math.radians(pitch), 4, "X")


def material_signature():
    result = []
    for mat in bpy.data.materials:
        item = {"name": mat.name, "diffuse": list(mat.diffuse_color), "metallic": mat.metallic, "roughness": mat.roughness}
        if mat.use_nodes:
            item["nodes"] = []
            for node in mat.node_tree.nodes:
                values = []
                for socket in node.inputs:
                    value = getattr(socket, "default_value", None)
                    if isinstance(value, (str, bool, int, float)) or value is None:
                        rendered = value
                    else:
                        try:
                            rendered = list(value)
                        except TypeError:
                            rendered = str(value)
                    values.append([socket.identifier, rendered])
                item["nodes"].append([node.name, node.bl_idname, values, getattr(getattr(node, "image", None), "name", None)])
            item["links"] = sorted([link.from_node.name, link.from_socket.identifier, link.to_node.name, link.to_socket.identifier]
                                   for link in mat.node_tree.links)
        result.append(item)
    return result


parser = argparse.ArgumentParser()
parser.add_argument("--asset", type=Path, required=True)
parser.add_argument("--timeline", type=Path, required=True)
parser.add_argument("--timeline-sha256", required=True)
parser.add_argument("--test-report", type=Path, required=True)
parser.add_argument("--repo", type=Path, required=True)
parser.add_argument("--source-binding", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--validate-only", action="store_true")
args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
binding = json.loads(args.source_binding.read_text())
assert binding["assetSha256"] == ASSET_SHA
assert binding["timelineSha256"] == args.timeline_sha256
assert binding["testReportSha256"] == sha(args.test_report)
assert all(sha(args.repo / name) == digest for name, digest in binding["sourceFileSha256"].items()), "Source binding mismatch"
assert sha(args.asset) == ASSET_SHA, "Original asset hash mismatch"
assert sha(args.timeline) == args.timeline_sha256, "Timeline hash mismatch"
report = ET.parse(args.test_report).getroot()
assert report.tag == "testsuite" and int(report.attrib.get("failures", -1)) == 0 and int(report.attrib.get("errors", -1)) == 0
assert EXPECTED_TESTS <= {item.attrib["name"] for item in report.findall("testcase")}, "Incomplete motion tests"
assert not report.findall(".//failure") and not report.findall(".//error") and not report.findall(".//skipped")
assert args.test_report.stat().st_mtime >= args.timeline.stat().st_mtime, "Report predates timeline"
timeline = json.loads(args.timeline.read_text())
assert timeline["renderScope"] == "HOST_RENDER_NOT_FILAMENT/DEVICE"
assert timeline["targets"] == TARGETS and len(TARGETS) == 21
assert abs(timeline["neckShare"] - .65) < 1e-6
frames = timeline["frames"]
assert frames and all(frames[i]["t"] < frames[i + 1]["t"] for i in range(len(frames) - 1))
for frame in frames:
    assert frame["inputSource"] == "HOST_SCRIPTED_DIRECTOR_EVENTS"
    assert len(frame["w"]) == 21 and all(math.isfinite(v) and 0 <= v <= 1 for v in frame["w"])
    for name in ["headYaw", "headPitch", "eyeYaw", "eyePitch", "jawPitch", "t"]:
        assert math.isfinite(frame[name]), name
    assert abs(frame["headPitch"] - frame["headTotal"][0]) < 1e-5
    assert abs(frame["headYaw"] - frame["headTotal"][1]) < 1e-5
    assert abs(frame["headPitch"] * timeline["neckShare"] - frame["neck"][0]) < 1e-5
    assert abs(frame["headYaw"] * timeline["neckShare"] - frame["neck"][1]) < 1e-5


def phase(name):
    found = [(index, frame) for index, frame in enumerate(frames) if frame["phase"] == name]
    assert found, "Missing phase: " + name
    return found


def middle(items):
    return items[len(items) // 2]


window_frames = phase("fensterblick")
nod_frames = phase("bestaetigung")
selected = [
    ("01-ruhe", middle(phase("ruhe")[:91])),
    ("02-zuhoeren", max(phase("zuhoeren"), key=lambda item: abs(item[1]["eyeYaw"]))),
    ("03-denken", middle(phase("denken"))),
    ("04-fenster-augen-vor-kopf", window_frames[0]),
    ("05-fenster-kopf-folgt", window_frames[-1]),
    ("06-bestaetigung-nicken", max(nod_frames, key=lambda item: item[1]["headPitch"])),
    ("07-bestaetigung-ende", nod_frames[-1]),
    ("08-sprechen", max(phase("sprechen"), key=lambda item: item[1]["jawPitch"])),
    ("09-stop", phase("stop")[0]),
]
assert selected[-1][1][1]["jawPitch"] == 0
assert all(weight == 0 for weight in selected[-1][1][1]["w"][:15])
args.output.mkdir(parents=True, exist_ok=True)
blob = args.asset.read_bytes()
magic, version, size = struct.unpack_from("<III", blob)
length, kind = struct.unpack_from("<II", blob, 12)
assert magic == 0x46546C67 and version == 2 and size == len(blob) and kind == 0x4E4F534A
gltf = json.loads(blob[20:20 + length])
nodes = gltf["nodes"]
node_index = {node["name"]: index for index, node in enumerate(nodes)}
parents = {child: index for index, node in enumerate(nodes) for child in node.get("children", [])}
rest_local = [local_rest(node) for node in nodes]
for mesh in gltf["meshes"]:
    if any(primitive.get("targets") for primitive in mesh["primitives"]):
        assert mesh["extras"]["targetNames"] == TARGETS


def world_matrices(rotations):
    result = {}
    def world(index):
        if index not in result:
            local = rest_local[index] @ rotations.get(nodes[index]["name"], Matrix.Identity(4))
            result[index] = world(parents[index]) @ local if index in parents else local
        return result[index]
    for index in range(len(nodes)):
        world(index)
    return result


rest_world = world_matrices({})
conversion = Matrix.Rotation(math.pi / 2, 4, "X")
conversion_inverse = conversion.inverted()
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(args.asset.resolve()))
bpy.context.view_layer.update()
arm = next(obj for obj in bpy.data.objects if obj.type == "ARMATURE")
controls = {name: bpy.data.objects[name] for name in ["EyeLeft", "EyeRight", "Jaw"]}
morphs = {name: bpy.data.objects[name] for name in ["Face", "NeuralThreads"]}
for obj in morphs.values():
    assert [key.name for key in obj.data.shape_keys.key_blocks][1:] == TARGETS
imported_bone_rest = {name: arm.matrix_world @ arm.pose.bones[name].matrix for name in ["Neck", "Head"]}
imported_object_rest = {name: obj.matrix_world.copy() for name, obj in controls.items()}
object_basis_rest = {name: obj.matrix_basis.copy() for name, obj in controls.items()}
materials_before = material_signature()
face = morphs["Face"]
head_indices = [vertex.index for vertex in face.data.vertices if (face.matrix_world @ vertex.co).z >= .18]
assert len(head_indices) > 1000


def apply(frame):
    for bone in arm.pose.bones:
        bone.matrix_basis = Matrix.Identity(4)
    for name, obj in controls.items():
        obj.matrix_basis = object_basis_rest[name].copy()
    for obj in morphs.values():
        for name, value in zip(TARGETS, frame["w"]):
            obj.data.shape_keys.key_blocks[name].value = value
    bpy.context.view_layer.update()
    neck_pitch, neck_yaw = frame["neck"]
    world = world_matrices({
        "Neck": rotation(neck_yaw, neck_pitch),
        "Head": rotation(frame["headYaw"] - neck_yaw, frame["headPitch"] - neck_pitch),
        "EyeLeft": rotation(frame["eyeYaw"], frame["eyePitch"]),
        "EyeRight": rotation(frame["eyeYaw"], frame["eyePitch"]),
        "Jaw": rotation(0, frame["jawPitch"]),
    })
    expected = {}
    for name in ["Neck", "Head", "EyeLeft", "EyeRight", "Jaw"]:
        index = node_index[name]
        delta = conversion @ world[index] @ rest_world[index].inverted() @ conversion_inverse
        original = imported_bone_rest[name] if name in imported_bone_rest else imported_object_rest[name]
        expected[name] = delta @ original
        if name in imported_bone_rest:
            arm.pose.bones[name].matrix = arm.matrix_world.inverted() @ expected[name]
        else:
            controls[name].matrix_world = expected[name]
        bpy.context.view_layer.update()
    actual = {name: arm.matrix_world @ arm.pose.bones[name].matrix for name in imported_bone_rest}
    actual.update({name: obj.matrix_world.copy() for name, obj in controls.items()})
    error = max(abs(actual[name][row][col] - expected[name][row][col])
                for name in expected for row in range(4) for col in range(4))
    assert error < 2e-5, f"Runtime transform mismatch: {error}"
    evaluated = face.evaluated_get(bpy.context.evaluated_depsgraph_get())
    points = [evaluated.matrix_world @ evaluated.data.vertices[index].co for index in head_indices]
    bounds = [[min(point[axis] for point in points), max(point[axis] for point in points)] for axis in range(3)]
    return {"matrixMaxAbsoluteError": error, "gltfWorldMatrices": {name: serial_matrix(world[node_index[name]]) for name in expected},
            "blenderWorldMatrices": {name: serial_matrix(actual[name]) for name in expected}, "posedHeadBoundsBlender": bounds}


scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = 8
scene.cycles.seed = 11
scene.cycles.use_denoising = False
scene.render.threads_mode = "FIXED"
scene.render.threads = 1
scene.render.resolution_x = 384
scene.render.resolution_y = 448
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = "PNG"
scene.render.film_transparent = False
scene.world = bpy.data.worlds.new("OriginalAvatarHostWorld")
scene.world.use_nodes = True
scene.world.node_tree.nodes["Background"].inputs[0].default_value = (.0, .003, .012, 1)
scene.world.node_tree.nodes["Background"].inputs[1].default_value = .25
scene.view_settings.view_transform = "AgX"
scene.view_settings.exposure = 0
scene.use_nodes = False
preview = [(label, index, frame, apply(frame)) for label, (index, frame) in selected]
low_x = min(record[3]["posedHeadBoundsBlender"][0][0] for record in preview)
high_x = max(record[3]["posedHeadBoundsBlender"][0][1] for record in preview)
low_z = min(.109, min(record[3]["posedHeadBoundsBlender"][2][0] for record in preview))
high_z = max(record[3]["posedHeadBoundsBlender"][2][1] for record in preview)
target = Vector(((low_x + high_x) / 2, -timeline["focus"]["centerZ"], (low_z + high_z) / 2))
camera_data = bpy.data.cameras.new("OriginalAvatarHostCamera")
camera_data.type = "ORTHO"
camera_data.ortho_scale = max(high_z - low_z, (high_x - low_x) * 448 / 384) * 1.12
camera = bpy.data.objects.new("OriginalAvatarHostCamera", camera_data)
scene.collection.objects.link(camera)
scene.camera = camera
camera.location = target + Vector((0, -.9, 0))
camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
bpy.context.view_layer.update()
for name, location, energy, color in [
    ("HostKey", (.35, -.62, .48), 5, (.66, .85, 1)),
    ("HostFill", (-.36, -.36, .30), 2, (.58, .45, 1)),
    ("HostRim", (.28, .12, .38), 3, (.25, .75, 1)),
]:
    light = bpy.data.lights.new(name, "AREA")
    light.energy = energy
    light.shape = "DISK"
    light.size = .35
    light.color = color
    obj = bpy.data.objects.new(name, light)
    scene.collection.objects.link(obj)
    obj.location = location
    obj.rotation_euler = (target - obj.location).to_track_quat("-Z", "Y").to_euler()
records = []
for label, index, frame, validation in preview:
    current = apply(frame)
    bounds = current["posedHeadBoundsBlender"]
    corners = [Vector((x, y, z)) for x in bounds[0] for y in bounds[1] for z in bounds[2]]
    projected = [world_to_camera_view(scene, camera, corner) for corner in corners]
    assert all(.015 <= p.x <= .985 and .015 <= p.y <= .985 for p in projected), "Host head crop"
    assert material_signature() == materials_before, "Imported material changed"
    record = {"label": label, "sourceFrameIndex": index, "unmodifiedMapperFrame": frame, **current,
              "headViewportBounds": [min(p.x for p in projected), min(p.y for p in projected), max(p.x for p in projected), max(p.y for p in projected)]}
    if not args.validate_only:
        path = args.output / (label + ".png")
        scene.render.filepath = str(path.resolve())
        bpy.ops.render.render(write_still=True)
        record.update({"file": path.name, "imageSha256": sha(path)})
    records.append(record)
    print(EVIDENCE_CLASS, label, index, frame["t"], current["matrixMaxAbsoluteError"], flush=True)
assert sha(args.asset) == ASSET_SHA and sha(args.timeline) == args.timeline_sha256
assert all(sha(args.repo / name) == digest for name, digest in binding["sourceFileSha256"].items()), "Source changed during replay"
source_files = ["app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt",
                "app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt",
                "app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt",
                "app/src/main/java/at/heinz/deviceai/avatar3d/FilamentAvatarRenderer.kt",
                "app/src/test/java/at/heinz/deviceai/avatar3d/AvatarMotionEvidenceTest.kt"]
source_head = subprocess.check_output(["git", "-C", str(args.repo), "rev-parse", "HEAD"], text=True).strip()
source_dirty = bool(subprocess.check_output(["git", "-C", str(args.repo), "status", "--porcelain"], text=True).strip())
manifest = {
    "status": "VALIDATED_NO_RENDER" if args.validate_only else "RENDERED",
    "evidenceClass": EVIDENCE_CLASS,
    "assetSha256": ASSET_SHA, "assetPath": str(args.asset.resolve()),
    "timelineSha256": args.timeline_sha256, "timelinePath": str(args.timeline.resolve()),
    "testReportSha256": sha(args.test_report), "testReportPath": str(args.test_report.resolve()),
    "sourceHeadAtReplay": source_head, "sourceDirtyAtReplay": source_dirty,
    "sourceBinding": binding, "sourceBindingSha256": sha(args.source_binding),
    "sourceFileSha256": {name: sha(args.repo / name) for name in source_files},
    "scriptSha256": sha(Path(__file__)), "backend": "Blender " + bpy.app.version_string + ", Cycles CPU",
    "pixels": [384, 448], "samples": 8, "cpuThreads": 1, "denoising": False,
    "targets": TARGETS, "neckShare": timeline["neckShare"], "timelineFrameCount": len(frames),
    "runtimeTransforms": "local = glTF_rest * Ry(yaw) * Rx(pitch); Neck = timeline.neck; Head = total - Neck; eyes/jaw local",
    "gltfToBlender": "Rx(+90 degrees); imported bone rest orientations preserved",
    "camera": {"targetBlender": list(target), "orthoScale": camera_data.ortho_scale,
               "policy": "Fixed host portrait fitting selected posed head plus upper neck; no cockpit compositing"},
    "originalGltfMaterials": gltf["materials"], "importedMaterialsUnchanged": material_signature() == materials_before,
    "inputOrigin": timeline["simulated"],
    "limitations": ["Scripted Director, camera and TTS inputs", "No actual PCM or device run",
                    "Blender lighting/color pipeline differs from Filament", "No runtime glow or cockpit overlay reproduced",
                    "Selected source frames, no interpolated or invented pose", "No PNG retouching or image generation"],
    "frames": records,
}
name = "validation-manifest.json" if args.validate_only else "replay-manifest.json"
(args.output / name).write_text(json.dumps(manifest, indent=2, allow_nan=False) + "\n")
print(EVIDENCE_CLASS, manifest["status"], str(args.output / name), flush=True)
