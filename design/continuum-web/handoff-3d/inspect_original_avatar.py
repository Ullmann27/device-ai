"""Inspect and light the byte-identical Continuum avatar; never export a new GLB.

Run with Blender 4.3+: blender -b --factory-startup -t 4 --python
handoff-3d/inspect_original_avatar.py -- --source continuum-preview/assets/continuum-avatar.glb
--output handoff-3d --render
"""
import argparse
import hashlib
import json
import pathlib
import struct
import sys

import bpy
from mathutils import Vector


EXPECTED_SHA256 = "c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a"
EXPECTED_BLOB = "3498c003a8bf186a8abb47048e2d36be4810b88c"
SOURCE_REPO = "https://github.com/Ullmann27/device-ai"
SOURCE_COMMIT = "1b90611388cc2b1978ddfe32064a25dd3a08cee1"
SOURCE_PATH = "app/src/main/assets/avatar3d/continuum-avatar.glb"
EXPECTED_TARGETS = ["SIL", "PP", "FF", "TH", "DD", "KK", "CH", "SS", "NN", "RR", "AA", "E", "I", "O", "U", "BlinkLeft", "BlinkRight", "BrowUp", "BrowDown", "Smile", "JawOpen"]


def options():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--render", action="store_true")
    return parser.parse_args(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])


def source_metadata(path):
    raw = path.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    blob = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
    if digest != EXPECTED_SHA256 or blob != EXPECTED_BLOB:
        raise ValueError("Source asset differs from the inspected GitHub asset; original-preservation gate stopped the render.")
    magic, version, size = struct.unpack_from("<4sII", raw)
    if magic != b"glTF" or version != 2 or size != len(raw):
        raise ValueError("Invalid GLB header")
    json_length, chunk = struct.unpack_from("<II", raw, 12)
    if chunk != 0x4E4F534A:
        raise ValueError("First chunk is not JSON")
    gltf = json.loads(raw[20:20 + json_length])
    mesh_summary = []
    for mesh in gltf.get("meshes", []):
        names = mesh.get("extras", {}).get("targetNames", [])
        if names and names != EXPECTED_TARGETS:
            raise ValueError("Unexpected morph-target order")
        mesh_summary.append({
            "name": mesh.get("name"),
            "primitives": len(mesh.get("primitives", [])),
            "targets": names,
        })
    return {
        "source_repository": SOURCE_REPO,
        "source_commit": SOURCE_COMMIT,
        "source_path": SOURCE_PATH,
        "source_bytes": len(raw),
        "source_sha256": digest,
        "source_git_blob_sha1": blob,
        "coordinate_system": "glTF source: Y up, metre-scale. Blender import: Z up; front camera looks along Blender +Y.",
        "gltf_nodes": [{"index": i, "name": n.get("name"), "mesh": n.get("mesh"), "skin": n.get("skin")} for i, n in enumerate(gltf.get("nodes", []))],
        "gltf_skins": [{"name": s.get("name"), "joint_names": [gltf["nodes"][j].get("name") for j in s.get("joints", [])]} for s in gltf.get("skins", [])],
        "gltf_meshes": mesh_summary,
        "gltf_materials": gltf.get("materials", []),
        "gltf_animations": [a.get("name") for a in gltf.get("animations", [])],
        "gltf_embedded_images": len(gltf.get("images", [])),
        "notes": [
            "The source GLB is byte-identical to the inspected Code55 GitHub asset.",
            "No new face, geometry, rig, morph target, material, texture or GLB export is produced.",
            "Lighting and camera are separate Blender scene objects.",
            "The source atlas already contains portrait lighting and emissive color; this study preserves both.",
            "This is an offline Blender rendering study, not a native Android/Filament or Fold7 performance test.",
        ],
    }


def mesh_fingerprint(objects):
    digest = hashlib.sha256()
    for obj in sorted(objects, key=lambda o: o.name):
        if obj.type != "MESH":
            continue
        digest.update(obj.name.encode())
        for vertex in obj.data.vertices:
            digest.update(struct.pack("<3f", *vertex.co))
        for polygon in obj.data.polygons:
            digest.update(struct.pack("<I", len(polygon.vertices)))
            digest.update(struct.pack("<" + "I" * len(polygon.vertices), *polygon.vertices))
        if obj.data.shape_keys:
            for key in obj.data.shape_keys.key_blocks:
                digest.update(key.name.encode())
                digest.update(struct.pack("<f", key.value))
                for vertex in key.data:
                    digest.update(struct.pack("<3f", *vertex.co))
    return digest.hexdigest()


def aim(obj, target):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()


def area(name, position, target, color, power, size):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = power
    data.color = color
    data.shape = "DISK"
    data.size = size
    obj = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = position
    aim(obj, target)
    return obj


def main():
    args = options()
    source = args.source.resolve()
    destination = args.output.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    report = source_metadata(source)

    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    bpy.ops.import_scene.gltf(filepath=str(source), import_pack_images=True)
    imported = list(bpy.context.scene.objects)
    before = mesh_fingerprint(imported)

    meshes = [obj for obj in imported if obj.type == "MESH"]
    bones = []
    for obj in imported:
        if obj.type == "ARMATURE":
            bones.append({"object": obj.name, "bones": [bone.name for bone in obj.data.bones]})
    report["blender_version"] = bpy.app.version_string
    report["blender_import"] = {
        "objects": len(imported),
        "meshes": [{"name": obj.name, "vertices": len(obj.data.vertices), "polygons": len(obj.data.polygons), "shape_keys": [key.name for key in obj.data.shape_keys.key_blocks] if obj.data.shape_keys else [], "materials": [slot.material.name if slot.material else None for slot in obj.material_slots]} for obj in meshes],
        "armatures": bones,
        "bounds": {
            axis: [min((obj.matrix_world @ vertex.co)[i] for obj in meshes for vertex in obj.data.vertices), max((obj.matrix_world @ vertex.co)[i] for obj in meshes for vertex in obj.data.vertices)]
            for i, axis in enumerate(["x", "y", "z"])
        },
    }

    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 128
    # The installed Blender build has no OpenImageDenoise backend.
    # Use genuine accumulated Cycles samples without an unavailable postprocess.
    scene.cycles.use_denoising = False
    scene.cycles.max_bounces = 6
    scene.render.resolution_x = 1280
    scene.render.resolution_y = 960
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.color_depth = "8"
    scene.render.film_transparent = True
    scene.render.fps = 24

    world = bpy.data.worlds.new("Continuum_Studio_World")
    scene.world = world
    world.use_nodes = True
    background = world.node_tree.nodes.get("Background")
    background.inputs["Color"].default_value = (0.11, 0.15, 0.2, 1)
    background.inputs["Strength"].default_value = 0.045
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.exposure = -0.25

    camera_data = bpy.data.cameras.new("Originalavatar_Studio_Camera")
    camera = bpy.data.objects.new("Originalavatar_Studio_Camera", camera_data)
    scene.collection.objects.link(camera)
    camera.location = (0, -1.45, 0.215)
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = 0.6
    camera_data.clip_start = 0.01
    camera_data.clip_end = 10
    camera_data.dof.use_dof = False
    aim(camera, (0, -0.025, 0.215))
    scene.camera = camera
    area("Neutral_Soft_Key", (-0.38, -0.6, 0.62), (0, 0, 0.23), (0.9, 0.95, 1), 12, 0.5)
    area("Cool_Fill", (0.4, -0.3, 0.35), (0, 0, 0.22), (0.34, 0.65, 1), 4, 0.4)
    area("Cyan_Rim", (0.34, 0.22, 0.43), (0, 0, 0.25), (0.05, 0.7, 1), 5, 0.26)
    area("Violet_Rim", (-0.34, 0.15, 0.38), (0, 0, 0.24), (0.4, 0.12, 1), 3.5, 0.26)

    report["preservation"] = {
        "imported_mesh_and_morph_fingerprint_before": before,
        "imported_mesh_and_morph_fingerprint_after": mesh_fingerprint(imported),
        "source_sha256_after": hashlib.sha256(source.read_bytes()).hexdigest(),
        "materials_modified": False,
        "source_glb_reexported": False,
    }
    if report["preservation"]["imported_mesh_and_morph_fingerprint_after"] != before:
        raise RuntimeError("Unexpected geometry or morph modification")
    if report["preservation"]["source_sha256_after"] != EXPECTED_SHA256:
        raise RuntimeError("Source GLB changed")
    report_path = destination / "Originalavatar_Diagnose.json"
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    bpy.ops.file.pack_all()
    blend_path = destination / "Continuum_Originalavatar_Lichtstudie.blend"
    bpy.ops.wm.save_as_mainfile(filepath=str(blend_path), compress=True)
    print("ORIGINAL_AVATAR_VERIFIED", json.dumps({"sha256": EXPECTED_SHA256, "git_blob": EXPECTED_BLOB, "imported_mesh_fingerprint": before, "blend": str(blend_path), "report": str(report_path)}))
    if args.render:
        scene.render.filepath = str(destination / "Continuum_Originalavatar_Beleuchtung.png")
        bpy.ops.render.render(write_still=True)
        print("ORIGINAL_AVATAR_RENDERED", scene.render.filepath)


if __name__ == "__main__":
    main()
