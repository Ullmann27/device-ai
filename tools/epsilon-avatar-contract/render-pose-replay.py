"""Actual production mapper pose replay on a real GLB, at a fixed camera.

blender -b -t 1 --python this.py -- asset.glb independent-poses.json output anchors|idle|ablation
HOST_RENDER_NOT_DEVICE. Inputs are synthetic, never claimed as actual TTS/camera.
"""
import hashlib
import json
import math
import os
from pathlib import Path
import sys
import bpy
from mathutils import Matrix, Vector

asset, pose_file, out = map(Path, sys.argv[sys.argv.index("--") + 1:-1])
mode = sys.argv[-1]
assert mode in ["anchors", "idle", "ablation"]
poses = json.loads(pose_file.read_text())
expected_source = os.environ.get("AVATAR_POSE_SOURCE_SHA", "26555aa6c59fdd4dd2e190defcdf49b983305555")
expected_pose = os.environ.get("AVATAR_POSE_FILE_SHA256", "6c6ad2a59f881a0855cf19a5c6a8966e48b6ecdcb06bed13049bd3da466a6496")
assert len(expected_source) == 40 and len(expected_pose) == 64
assert poses["sourceSha"] == expected_source
assert hashlib.sha256(pose_file.read_bytes()).hexdigest() == expected_pose
frames = poses["frames"]
if mode == "anchors":
    selected = [frames[index] for index in [224, 228, 249, 253, 254]]
elif mode == "ablation":
    selected = []
    for label, aa, jaw, bone in [("AA_ONLY", 1, 0, 0), ("JAW_MORPH_ONLY", 0, 1, 0),
        ("JAW_BONE_ONLY", 0, 0, 12), ("AA_PLUS_JAW_MORPHS", 1, 1, 0), ("FULL_PRODUCTION_AA", 1, 1, 12)]:
        frame = json.loads(json.dumps(frames[249]))
        if label != "FULL_PRODUCTION_AA":
            frame["weights"] = {name: 0 for name in frame["weights"]}
            frame["weights"]["AA"] = aa
            frame["weights"]["JawOpen"] = jaw
            frame["jawPitch"] = bone
        frame["diagnosticComponent"] = label
        frame["isUnmodifiedProductionOutput"] = label == "FULL_PRODUCTION_AA"
        selected.append(frame)
else:
    idle_start = int(os.environ.get("AVATAR_REPLAY_START", "0"))
    idle_count = int(os.environ.get("AVATAR_REPLAY_COUNT", "112"))
    assert 0 <= idle_start < 224 and 1 <= idle_count <= 224 - idle_start
    selected = [frame for frame in frames if frame["sequence"] == "idle-24fps"
        and idle_start <= frame["frame"] < idle_start + idle_count]
out.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(asset))
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = 2 if mode == "idle" else 16
scene.cycles.seed = 11
scene.cycles.use_denoising = False
scene.render.threads_mode = "FIXED"
scene.render.threads = 1
scene.render.resolution_x = 192 if mode == "idle" else 384
scene.render.resolution_y = 224 if mode == "idle" else 448
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = "PNG"
scene.world = bpy.data.worlds.new("FixedReplayWorld")
scene.world.use_nodes = True
scene.world.node_tree.nodes["Background"].inputs[0].default_value = (.0, .003, .012, 1)
scene.world.node_tree.nodes["Background"].inputs[1].default_value = .25
scene.view_settings.view_transform = "AgX"
scene.view_settings.exposure = 0
target = Vector((-.0003228597, -.06414049, .265))
for name, location, energy, color in [
    ("Key", (.35, -.62, .48), 5, (.66, .85, 1)),
    ("Fill", (-.36, -.36, .30), 2, (.58, .45, 1)),
    ("Rim", (.28, .12, .38), 3, (.25, .75, 1)),
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
camera_data = bpy.data.cameras.new("FixedReplayCamera")
camera_data.type = "ORTHO"
camera_data.ortho_scale = .34
camera = bpy.data.objects.new("FixedReplayCamera", camera_data)
scene.collection.objects.link(camera)
scene.camera = camera
camera.location = target + Vector((0, -.9, 0))
camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
arm = next(obj for obj in bpy.data.objects if obj.type == "ARMATURE")
controls = {name: bpy.data.objects[name] for name in ["EyeLeft", "EyeRight", "Jaw"]}
rest = {name: obj.matrix_basis.copy() for name, obj in controls.items()}
morph_meshes = [bpy.data.objects[name] for name in ["Face", "NeuralThreads"]]
records = []


def rotation(yaw, pitch):
    return Matrix.Rotation(math.radians(yaw), 4, "Z") @ Matrix.Rotation(math.radians(pitch), 4, "X")


for number, frame in enumerate(selected):
    for bone in arm.pose.bones:
        bone.matrix_basis = Matrix.Identity(4)
    for name, obj in controls.items():
        obj.matrix_basis = rest[name].copy()
    for obj in morph_meshes:
        for key in obj.data.shape_keys.key_blocks:
            key.value = frame["weights"].get(key.name, 0)
    bpy.context.view_layer.update()
    head_rotation = rotation(frame["headYaw"], frame["headPitch"])
    bone = arm.pose.bones["Head"]
    world = arm.matrix_world @ bone.matrix
    pivot = world.translation.copy()
    bone.matrix = arm.matrix_world.inverted() @ Matrix.Translation(pivot) @ head_rotation @ Matrix.Translation(-pivot) @ world
    bpy.context.view_layer.update()
    for name in controls:
        obj = controls[name]
        local_rotation = rotation(0, frame["jawPitch"]) if name == "Jaw" else rotation(frame["eyeYaw"], frame["eyePitch"])
        world_rotation = head_rotation @ local_rotation @ head_rotation.inverted()
        world = obj.matrix_world.copy()
        pivot = world.translation.copy()
        obj.matrix_world = Matrix.Translation(pivot) @ world_rotation @ Matrix.Translation(-pivot) @ world
        bpy.context.view_layer.update()
    output_index = frame["frame"] if mode == "idle" else number
    path = out / f"frame-{output_index:04d}.png"
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    records.append({"file": path.name, "imageSha256": hashlib.sha256(path.read_bytes()).hexdigest(), "pose": frame,
        "jawWorldMatrixBlenderRowMajor": [value for row in controls["Jaw"].matrix_world for value in row]})
    print("ACTUAL_MAPPER_POSE_REPLAY", number, frame["sequence"], frame["frame"], flush=True)

manifest_name = "replay-manifest.json" if mode != "idle" else f"replay-idle-{idle_start:04d}-{idle_start + idle_count - 1:04d}.json"
(out / manifest_name).write_text(json.dumps({
    "evidenceClass": "HOST_RENDER_NOT_DEVICE", "backend": f"Blender {bpy.app.version_string}, Cycles CPU",
    "renderScriptSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    "assetSha256": hashlib.sha256(asset.read_bytes()).hexdigest(),
    "poseFileSha256": hashlib.sha256(pose_file.read_bytes()).hexdigest(),
    "productionMapperSource": poses["sourceSha"], "inputOrigin": poses["inputOrigin"],
    "poseInputModifiedForAblation": mode == "ablation",
    "fixedCamera": {"targetBlender": list(target), "orthoScale": .34},
    "pixels": [scene.render.resolution_x, scene.render.resolution_y],
    "samples": scene.cycles.samples, "cpuThreads": 1, "framesPerSecond": 24 if mode == "idle" else None,
    "idleSourceFrameCount": 224 if mode == "idle" else None,
    "selectedSourceFrameIndices": [frame["frame"] for frame in selected],
    "sequenceComplete": mode == "anchors" or len(selected) == 224,
    "limitations": ["Scripted pose inputs", "Not actual TTS/audio/camera", "Not Filament or device rendering"],
    "frames": records,
}, indent=2) + "\n")
