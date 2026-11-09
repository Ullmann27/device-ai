"""Render the actual exported GLB in four diagnostic poses, without a backdrop portrait.

Blender 4.3 CPU: blender -b -t 2 --python render_reference_avatar.py -- MODEL OUTPUT
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import sys

import bpy
from mathutils import Quaternion, Vector


def canonical(x, y, z):
    return Vector((x, -z, y))


def rotate_control(name, radians):
    obj = bpy.data.objects.get(name)
    if obj is not None:
        obj.rotation_mode = "QUATERNION"
        obj.rotation_quaternion = Quaternion((1, 0, 0), radians)
        return "object:" + name
    for obj in bpy.data.objects:
        if obj.type == "ARMATURE" and name in obj.pose.bones:
            bone = obj.pose.bones[name]
            bone.rotation_mode = "QUATERNION"
            bone.rotation_quaternion = Quaternion((1, 0, 0), radians)
            return "bone:" + name
    raise ValueError("Missing actual GLB control " + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--width", type=int, default=384)
    parser.add_argument("--height", type=int, default=512)
    parser.add_argument("--samples", type=int, default=16)
    parser.add_argument("--views", default="front,quarter,blink,speaking")
    parser.add_argument("--ortho-scale", type=float, default=.44)
    parser.add_argument("--target-y", type=float, default=.232)
    parser.add_argument("--quarter-yaw", type=float, default=-24)
    parser.add_argument("--surface-gain", type=float, default=1.0,
                        help="Diagnostic multiplier of the authored face emission only; no texture or asset edits")
    config = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
    config.output.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(config.model.resolve()))
    if not 1.0 <= config.surface_gain <= 1.6:
        raise ValueError("Surface gain outside bounded diagnostic range")
    material = bpy.data.materials.get("DeepIndigoSkin")
    if material is None:
        raise ValueError("Original face material missing")
    shader = next(node for node in material.node_tree.nodes if node.type == "BSDF_PRINCIPLED")
    authored_emission = shader.inputs["Emission Strength"].default_value
    shader.inputs["Emission Strength"].default_value = authored_emission * config.surface_gain
    for obj in bpy.data.objects:
        if obj.name.startswith("Tooth"):
            obj.hide_render = True
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = config.samples
    scene.cycles.use_denoising = False
    scene.cycles.seed = 17
    scene.render.threads_mode = "FIXED"
    scene.render.threads = 2
    scene.render.resolution_x = config.width
    scene.render.resolution_y = config.height
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    scene.world = bpy.data.worlds.new("ReferenceAvatarBlackWorld")
    scene.world.use_nodes = True
    background = scene.world.node_tree.nodes["Background"]
    background.inputs["Color"].default_value = (.0001, .0002, .0006, 1)
    background.inputs["Strength"].default_value = .015
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1
    if not (.2 <= config.ortho_scale <= 1.0 and .1 <= config.target_y <= .4 and -75 <= config.quarter_yaw <= 75):
        raise ValueError("Inspection camera outside the bounded bust range")
    target = canonical(0, config.target_y, .06)
    for name, location, power, color in [
        ("SoftCyan", (.26, .44, .38), 1.2, (.18, .60, 1)),
        ("SoftViolet", (-.28, .36, .30), .75, (.50, .08, 1)),
        ("BackCyan", (.24, .42, -.15), 1.2, (.10, .72, 1)),
    ]:
        data = bpy.data.lights.new(name, "AREA")
        data.energy, data.color, data.size = power, color, .30
        obj = bpy.data.objects.new(name, data)
        scene.collection.objects.link(obj)
        obj.location = canonical(*location)
        obj.rotation_euler = (target - obj.location).to_track_quat("-Z", "Y").to_euler()
    camera_data = bpy.data.cameras.new("ActualMeshCamera")
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = config.ortho_scale
    camera = bpy.data.objects.new("ActualMeshCamera", camera_data)
    scene.collection.objects.link(camera)
    scene.camera = camera
    scene.use_nodes = True
    nodes, links = scene.node_tree.nodes, scene.node_tree.links
    nodes.clear()
    layer = nodes.new("CompositorNodeRLayers")
    glow = nodes.new("CompositorNodeGlare")
    glow.glare_type, glow.quality, glow.threshold, glow.size = "FOG_GLOW", "HIGH", .80, 7
    output = nodes.new("CompositorNodeComposite")
    links.new(layer.outputs["Image"], glow.inputs["Image"])
    links.new(glow.outputs["Image"], output.inputs["Image"])
    records = []
    for pose in config.views.split(","):
        if pose not in {"front", "quarter", "blink", "speaking"}:
            raise ValueError("Unexpected pose " + pose)
        weights = {"BlinkLeft": 1.0, "BlinkRight": 1.0} if pose == "blink" else \
            {"AA": .6, "JawOpen": .1125} if pose == "speaking" else {}
        for obj in bpy.data.objects:
            if obj.type == "MESH" and obj.data.shape_keys:
                for key in obj.data.shape_keys.key_blocks:
                    if key.name != "Basis":
                        key.value = weights.get(key.name, 0.0)
        jaw_pitch = 7.2 if pose == "speaking" else 0
        jaw_control = rotate_control("Jaw", math.radians(jaw_pitch))
        yaw = math.radians(config.quarter_yaw if pose == "quarter" else 0)
        camera.location = target + Vector((math.sin(yaw), -math.cos(yaw), .018)) * .95
        camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
        bpy.context.view_layer.update()
        scene.render.filepath = str((config.output / (pose + ".png")).resolve())
        bpy.ops.render.render(write_still=True)
        path = Path(scene.render.filepath)
        record = {"file": path.name, "pose": pose, "shapeWeights": weights,
                  "jawPitchDegrees": jaw_pitch, "jawControl": jaw_control,
                  "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        records.append(record)
        print("ACTUAL_GLB_RENDER", json.dumps(record), flush=True)
        if pose == "front":
            bpy.ops.file.pack_all()
            bpy.ops.wm.save_as_mainfile(filepath=str((config.output / "actual-reference-head-scene.blend").resolve()))
    manifest = {"evidenceClass": "ACTUAL_GLB_HOST_RENDER_NOT_DEVICE",
                "model": str(config.model), "modelSha256": hashlib.sha256(config.model.read_bytes()).hexdigest(),
                "backend": f"Blender {bpy.app.version_string} Cycles CPU", "samples": config.samples,
                "resolution": [config.width, config.height], "viewTransform": "Standard", "exposure": 0,
                "camera": {"projection": "ORTHOGRAPHIC", "orthoScaleMetres": config.ortho_scale,
                           "targetCanonicalMetres": [0, config.target_y, .06], "quarterYawDegrees": config.quarter_yaw,
                           "distanceMetres": .95, "elevationVectorComponent": .018},
                "glow": "Blender compositor Fog Glow, threshold .80; Android bloom must be tested independently",
                "faceEmission": {"authored": authored_emission, "gain": config.surface_gain,
                                 "material": "DeepIndigoSkin", "irisUnchanged": True},
                "portraitBackdrop": False, "frames": records,
                "limitations": ["Host render is not Android/Filament GPU output", "Not owner likeness acceptance"]}
    (config.output / "render-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
