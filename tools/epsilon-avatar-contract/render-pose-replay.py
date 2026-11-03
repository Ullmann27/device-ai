"""Actual production mapper pose replay on a real GLB, at a fixed camera.

blender -b -t 1 --python this.py -- asset.glb independent-poses.json output anchors|idle
HOST_RENDER_NOT_DEVICE. Inputs are synthetic, never claimed as actual TTS/camera.
"""
import hashlib
import json
import math
from pathlib import Path
import sys
import bpy
from mathutils import Matrix, Vector

asset, pose_file, out = map(Path, sys.argv[sys.argv.index("--") + 1:-1])
mode = sys.argv[-1]
assert mode in ["anchors", "idle"]
poses = json.loads(pose_file.read_text())
assert poses["sourceSha"] == "26555aa6c59fdd4dd2e190defcdf49b983305555"
assert hashlib.sha256(pose_file.read_bytes()).hexdigest() == "6c6ad2a59f881a0855cf19a5c6a8966e48b6ecdcb06bed13049bd3da466a6496"
frames = poses["frames"]
if mode == "anchors":
    selected = [frames[index] for index in [224, 228, 249, 253, 254]]
else:
    selected = [frame for frame in frames if frame["sequence"] == "idle-24fps" and frame["frame"] < 112]
out.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(asset))
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = 16 if mode == "anchors" else 2
scene.cycles.seed = 11
scene.cycles.use_denoising = False
scene.render.threads_mode = "FIXED"
scene.render.threads = 1
scene.render.resolution_x = 384 if mode == "anchors" else 192
scene.render.resolution_y = 448 if mode == "anchors" else 224
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
    path = out / f"frame-{number:04d}.png"
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    records.append({"file": path.name, "imageSha256": hashlib.sha256(path.read_bytes()).hexdigest(), "pose": frame})
    print("ACTUAL_MAPPER_POSE_REPLAY", number, frame["sequence"], frame["frame"], flush=True)

(out / "replay-manifest.json").write_text(json.dumps({
    "evidenceClass": "HOST_RENDER_NOT_DEVICE", "backend": f"Blender {bpy.app.version_string}, Cycles CPU",
    "assetSha256": hashlib.sha256(asset.read_bytes()).hexdigest(),
    "poseFileSha256": hashlib.sha256(pose_file.read_bytes()).hexdigest(),
    "productionMapperSource": poses["sourceSha"], "inputOrigin": poses["inputOrigin"],
    "fixedCamera": {"targetBlender": list(target), "orthoScale": .34},
    "pixels": [scene.render.resolution_x, scene.render.resolution_y],
    "samples": scene.cycles.samples, "cpuThreads": 1, "framesPerSecond": 24 if mode == "idle" else None,
    "limitations": ["Scripted pose inputs", "Not actual TTS/audio/camera", "Not Filament or device rendering"],
    "frames": records,
}, indent=2) + "\n")
