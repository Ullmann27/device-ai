"""Render actual before/after GLBs with identical host camera and light.

blender -b -t 1 --python tools/avatar-3d/render_holographic_bust.py -- BASE CANDIDATE OUTPUT
These are Blender CPU asset inspections, not Android/Filament screenshots.
"""
import hashlib
import json
import math
import os
from pathlib import Path
import sys

import bpy
from mathutils import Vector


source, candidate, output = map(Path, sys.argv[sys.argv.index("--") + 1:])
output.mkdir(parents=True, exist_ok=True)
requested = os.environ.get("AVATAR_REVIEW_VIEWS", "front,quarter,blink,speaking").split(",")
if not requested or not set(requested) <= {"front", "quarter", "blink", "speaking"}:
    raise ValueError("Unexpected review pose")
records = []


def point(x, y, z):
    return Vector((x, -z, y))


for label, asset in [("baseline", source), ("candidate", candidate)]:
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(asset))
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 12
    scene.cycles.use_denoising = False
    scene.cycles.seed = 11
    scene.render.threads_mode = "FIXED"
    scene.render.threads = 1
    scene.render.resolution_x = 512
    scene.render.resolution_y = 576
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    scene.world = bpy.data.worlds.new("HolographicReviewWorld")
    scene.world.use_nodes = True
    scene.world.node_tree.nodes["Background"].inputs[0].default_value = (.002, .004, .012, 1)
    scene.world.node_tree.nodes["Background"].inputs[1].default_value = .18
    scene.view_settings.view_transform = "AgX" if bpy.app.version >= (4, 0, 0) else "Filmic"
    scene.view_settings.exposure = 0
    target = point(0, .227, .045)
    for name, location, energy, color in [
        ("CyanKey", (.32, .47, .52), 7.0, (.34, .84, 1)),
        ("VioletFill", (-.38, .34, .42), 5.0, (.58, .27, 1)),
        ("CyanRim", (.30, .42, -.16), 6.0, (.18, .76, 1)),
    ]:
        light = bpy.data.lights.new(name, "AREA")
        light.energy = energy
        light.shape = "DISK"
        light.size = .30
        light.color = color
        obj = bpy.data.objects.new(name, light)
        scene.collection.objects.link(obj)
        obj.location = point(*location)
        obj.rotation_euler = (target - obj.location).to_track_quat("-Z", "Y").to_euler()
    camera_data = bpy.data.cameras.new("ReviewCamera")
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = .44
    camera = bpy.data.objects.new("ReviewCamera", camera_data)
    scene.collection.objects.link(camera)
    scene.camera = camera
    for pose in requested:
        for obj in bpy.data.objects:
            if obj.type == "MESH" and obj.data.shape_keys:
                for key in obj.data.shape_keys.key_blocks:
                    if key.name != "Basis":
                        key.value = 0
                names = {key.name for key in obj.data.shape_keys.key_blocks}
                weights = {"BlinkLeft": 1, "BlinkRight": 1} if pose == "blink" else \
                    {"AA": .5, "JawOpen": .22} if pose == "speaking" else {}
                for name, value in weights.items():
                    if name in names:
                        obj.data.shape_keys.key_blocks[name].value = value
        yaw = math.radians(18 if pose == "quarter" else 0)
        camera.location = target + Vector((math.sin(yaw), -math.cos(yaw), .03)) * .95
        camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = str(output / f"{label}-{pose}.png")
        bpy.ops.render.render(write_still=True)
        path = Path(scene.render.filepath)
        records.append({"file": path.name, "assetSha256": hashlib.sha256(asset.read_bytes()).hexdigest(),
            "imageSha256": hashlib.sha256(path.read_bytes()).hexdigest(), "pose": pose})
        print("ACTUAL_GLB_RENDER", json.dumps(records[-1]), flush=True)

(output / "render-manifest.json").write_text(json.dumps({
    "evidenceClass": "HOST_RENDER_NOT_DEVICE", "backend": f"Blender {bpy.app.version_string} Cycles CPU",
    "sameCameraLightingExposureSeedAndPose": True, "cpuThreads": 1, "samples": 12,
    "pixels": [512, 576], "frames": records,
    "limitations": ["Not Filament pixel equivalence", "No device FPS/thermal claim",
        "Speaking frame exercises mesh morphs; exact runtime mapper/jaw-bone replay is tested separately"],
}, indent=2) + "\n")
