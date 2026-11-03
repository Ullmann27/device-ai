"""Blender CPU A/B of actual GLBs. HOST_RENDER_NOT_DEVICE; not Filament pixels.

Usage: blender -b -t 1 --python this.py -- source.glb candidate.glb output
No source mesh, private image, video, stand-in head or generative model is used.
"""
import hashlib
import json
import math
from pathlib import Path
import sys
import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
assert len(args) == 3
source, candidate, out = map(Path, args)
out.mkdir(parents=True, exist_ok=True)
candidate_record = json.loads(candidate.with_suffix(".json").read_text())
assert hashlib.sha256(source.read_bytes()).hexdigest() == candidate_record["sourceSha256"]
assert hashlib.sha256(candidate.read_bytes()).hexdigest() == candidate_record["candidateSha256"]
records = []


def point(x, y, z):
    return Vector((x, -z, y))  # Canonical glTF Y-up to Blender Z-up.


for label, asset in [("baseline", source), ("candidate", candidate)]:
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(asset))
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 8
    scene.cycles.use_denoising = False
    scene.cycles.seed = 11
    scene.render.threads_mode = "FIXED"
    scene.render.threads = 1
    scene.render.resolution_x = 384
    scene.render.resolution_y = 448
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    scene.world = bpy.data.worlds.new("ContinuumReviewWorld")
    scene.world.use_nodes = True
    scene.world.node_tree.nodes["Background"].inputs[0].default_value = (.0, .003, .012, 1)
    scene.world.node_tree.nodes["Background"].inputs[1].default_value = .25
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.exposure = 0
    target = point(-.0003228597, .286276497, .06414049)
    for name, position, energy, color in [
        ("Key", (.35, .48, .62), 5, (.66, .85, 1)),
        ("Fill", (-.36, .30, .36), 2, (.58, .45, 1)),
        ("Rim", (.28, .38, -.12), 3, (.25, .75, 1)),
    ]:
        light = bpy.data.lights.new(name, "AREA")
        light.energy = energy
        light.shape = "DISK"
        light.size = .35
        light.color = color
        obj = bpy.data.objects.new(name, light)
        scene.collection.objects.link(obj)
        obj.location = point(*position)
        obj.rotation_euler = (target - obj.location).to_track_quat("-Z", "Y").to_euler()
    camera_data = bpy.data.cameras.new("ReviewCamera")
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = .287
    camera = bpy.data.objects.new("ReviewCamera", camera_data)
    scene.collection.objects.link(camera)
    scene.camera = camera
    for view, yaw, pitch in [("front", 0, 0), ("slight-yaw-pitch", 18, 8), ("profile", 90, 0)]:
        azimuth, elevation = map(math.radians, (yaw, pitch))
        camera.location = target + Vector((math.sin(azimuth) * math.cos(elevation),
            -math.cos(azimuth) * math.cos(elevation), math.sin(elevation))) * .9
        camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
        path = out / f"{label}-{view}.png"
        scene.render.filepath = str(path)
        bpy.ops.render.render(write_still=True)
        record = {"file": path.name, "assetSha256": hashlib.sha256(asset.read_bytes()).hexdigest(),
            "imageSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "cameraYawDegrees": yaw, "cameraPitchDegrees": pitch}
        records.append(record)
        print("ACTUAL_GLB_RENDER", json.dumps(record), flush=True)

(out / "render-manifest.json").write_text(json.dumps({
    "evidenceClass": "HOST_RENDER_NOT_DEVICE",
    "backend": f"Blender {bpy.app.version_string}, Cycles CPU",
    "pixels": [384, 448], "samples": 8, "cpuThreads": 1,
    "sameCameraLightingExposureAndSeed": True,
    "syntheticInput": "Neutral real GLB; camera moves, no invented model or tool activity",
    "limitations": ["Not Filament pixel equivalence", "Not reference identity acceptance",
        "Not camera tracking", "No device FPS or resource claim"],
    "frames": records,
}, indent=2) + "\n")
