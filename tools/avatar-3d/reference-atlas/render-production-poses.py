"""Replay exported production Kotlin poses on the actual GLB with verified skinning.

Run with Blender 4.3+ in background mode. Synthetic inputs and a Blender studio
are host evidence only. All expression and gaze values come from the exported
production poses. The renderer adds no expression or inferred speech.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import sys

import bpy
from mathutils import Matrix, Vector
import numpy as np


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset", type=Path, required=True)
    parser.add_argument("--poses", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-asset-sha256", required=True)
    parser.add_argument("--expected-poses-sha256", required=True)
    parser.add_argument("--frames", default="neutral-listening,blink,speaking-aa,after-interruption")
    parser.add_argument("--width", type=int, default=384)
    parser.add_argument("--height", type=int, default=512)
    parser.add_argument("--samples", type=int, default=12)
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--no-render", action="store_true")
    args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
    require(128 <= args.width <= 1024 and 128 <= args.height <= 1024 and 1 <= args.samples <= 64, "Bounded render parameters required")
    require(1 <= args.threads <= 8, "Bounded CPU threads required")
    raw = args.asset.read_bytes()
    pose_raw = args.poses.read_bytes()
    asset_hash = hashlib.sha256(raw).hexdigest()
    pose_hash = hashlib.sha256(pose_raw).hexdigest()
    require(asset_hash == args.expected_asset_sha256, "Asset pin mismatch")
    require(pose_hash == args.expected_poses_sha256, "Actual mapper pose export pin mismatch")
    poses = json.loads(pose_raw)
    require(poses["schema"] == "continuum-production-mapper-review-poses-v1", "Unexpected pose schema")
    if args.frames == "all":
        wanted = [frame["name"] for frame in poses["frames"]]
    elif args.frames == "idle":
        wanted = [frame["name"] for frame in poses["frames"] if frame["name"].startswith("idle-f")]
    else:
        wanted = args.frames.split(",")
    selected = [frame for frame in poses["frames"] if frame["name"] in wanted]
    require(wanted and set(wanted) == {frame["name"] for frame in selected}, "Requested pose absent")
    args.output.mkdir(parents=True, exist_ok=True)
    length = struct.unpack_from("<I", raw, 12)[0]
    document = json.loads(raw[20:20 + length])
    binary = raw[28 + length:]
    names = [node["name"] for node in document["nodes"]]
    parent = [-1] * len(names)
    for index, node in enumerate(document["nodes"]):
        for child in node.get("children", []):
            parent[child] = index

    def accessor(index):
        item = document["accessors"][index]
        view = document["bufferViews"][item["bufferView"]]
        width = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}[item["type"]]
        dtype = np.dtype({5121: "u1", 5123: "<u2", 5125: "<u4", 5126: "<f4"}[item["componentType"]])
        return np.ndarray((item["count"], width), dtype=dtype, buffer=binary,
            offset=view.get("byteOffset", 0) + item.get("byteOffset", 0),
            strides=(view.get("byteStride", width * dtype.itemsize), dtype.itemsize)).copy()

    def rotation(yaw, pitch):
        return Matrix.Rotation(math.radians(yaw), 4, "Y") @ Matrix.Rotation(math.radians(pitch), 4, "X")

    def global_matrices(frame=None):
        overrides = {} if frame is None else {
            "Head": rotation(frame["headYaw"], frame["headPitch"]),
            "EyeLeft": rotation(frame["eyeYaw"], frame["eyePitch"]),
            "EyeRight": rotation(frame["eyeYaw"], frame["eyePitch"]),
            "Jaw": rotation(0, frame["jawPitch"]),
        }
        cache = {}

        def world(index):
            if index in cache:
                return cache[index]
            node = document["nodes"][index]
            require("matrix" not in node and node.get("rotation", [0, 0, 0, 1]) == [0, 0, 0, 1], "Unexpected rest transform")
            scale = node.get("scale", [1, 1, 1])
            local = Matrix.Translation(Vector(node.get("translation", [0, 0, 0]))) @ Matrix.Diagonal(Vector((*scale, 1)))
            local = local @ overrides.get(node["name"], Matrix.Identity(4))
            cache[index] = world(parent[index]) @ local if parent[index] >= 0 else local
            return cache[index]

        return [world(i) for i in range(len(names))]

    # Explicit canonical glTF (+Y up,+Z front) to Blender (+Z up,-Y front).
    conversion = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
    inverse_conversion = conversion.inverted()
    original_world = global_matrices()
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(args.asset.resolve()))
    scene = bpy.context.scene
    armatures = [obj for obj in bpy.data.objects if obj.type == "ARMATURE"]
    require(len(armatures) == 1, "One imported skin rig required")
    armature = armatures[0]
    head = armature.pose.bones["Head"]
    bpy.context.view_layer.update()
    rest_head = armature.matrix_world @ head.matrix
    controls = {name: bpy.data.objects[name] for name in ("EyeLeft", "EyeRight", "Jaw")}
    rest_control_world = {name: obj.matrix_world.copy() for name, obj in controls.items()}
    rest_control_basis = {name: obj.matrix_basis.copy() for name, obj in controls.items()}
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH" and obj.data.shape_keys is not None]
    face_node = document["nodes"][names.index("Face")]
    face_mesh = document["meshes"][face_node["mesh"]]
    face_primitive = face_mesh["primitives"][0]
    # Blender's importer discards source vertices not referenced by any triangle.
    # Keep their ascending source order, and verify every resulting posed vertex.
    used_face_vertices = np.unique(accessor(face_primitive["indices"]).reshape(-1))
    face_positions = accessor(face_primitive["attributes"]["POSITION"])[used_face_vertices].astype(np.float64)
    face_deltas = {name: accessor(target["POSITION"])[used_face_vertices].astype(np.float64)
        for name, target in zip(face_mesh["extras"]["targetNames"], face_primitive["targets"])}
    face_joints = accessor(face_primitive["attributes"]["JOINTS_0"])[used_face_vertices]
    face_weights = accessor(face_primitive["attributes"]["WEIGHTS_0"])[used_face_vertices]
    skin = document["skins"][face_node["skin"]]
    inverse_bind = accessor(skin["inverseBindMatrices"]).reshape(-1, 4, 4).transpose(0, 2, 1).astype(np.float64)
    face_object = bpy.data.objects["Face"]

    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = args.samples
    scene.cycles.seed = 731
    scene.cycles.use_denoising = False
    scene.render.threads_mode = "FIXED"
    scene.render.threads = args.threads
    scene.render.use_persistent_data = True
    scene.render.resolution_x, scene.render.resolution_y = args.width, args.height
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.film_transparent = False
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0.0
    scene.world = bpy.data.worlds.new("PoseReviewBlackStudio")
    scene.world.use_nodes = True
    scene.world.node_tree.nodes["Background"].inputs[0].default_value = (0, 0, 0, 1)
    scene.world.node_tree.nodes["Background"].inputs[1].default_value = 0
    focus = poses["focus"]
    target = Vector((focus["centerX"], focus["centerY"], focus["centerZ"]))
    for name, position, energy, color, size in [
        ("VioletLeft", (-.32, .42, .35), 3.0, (.40, .075, 1.0), .22),
        ("CyanRight", (.33, .39, .28), 3.5, (.035, .50, 1.0), .20),
        ("VioletBack", (-.20, .44, -.25), 4.0, (.35, .035, 1.0), .16),
        ("CyanBack", (.22, .40, -.25), 4.0, (.02, .55, 1.0), .16),
    ]:
        light = bpy.data.lights.new(name, "AREA")
        light.energy, light.color, light.size, light.shape = energy, color, size, "DISK"
        obj = bpy.data.objects.new(name, light)
        scene.collection.objects.link(obj)
        obj.location = conversion @ Vector(position)
        obj.rotation_euler = ((conversion @ target) - obj.location).to_track_quat("-Z", "Y").to_euler()
    camera_data = bpy.data.cameras.new("ActualPortraitCamera")
    camera_data.type = "ORTHO"
    camera = bpy.data.objects.new("ActualPortraitCamera", camera_data)
    scene.collection.objects.link(camera)
    scene.camera = camera
    scene.use_nodes = True
    scene.node_tree.nodes.clear()
    layer = scene.node_tree.nodes.new("CompositorNodeRLayers")
    glow = scene.node_tree.nodes.new("CompositorNodeGlare")
    glow.glare_type, glow.quality, glow.threshold, glow.size = "FOG_GLOW", "HIGH", .7, 8
    composite = scene.node_tree.nodes.new("CompositorNodeComposite")
    scene.node_tree.links.new(layer.outputs["Image"], glow.inputs["Image"])
    scene.node_tree.links.new(glow.outputs["Image"], composite.inputs["Image"])
    records = []
    for frame in selected:
        for bone in armature.pose.bones:
            bone.matrix_basis = Matrix.Identity(4)
        for name, obj in controls.items():
            obj.matrix_basis = rest_control_basis[name].copy()
        for obj in meshes:
            for key in obj.data.shape_keys.key_blocks:
                if key.name != "Basis":
                    require(key.name in frame["weights"], "Morph missing from actual mapper export")
                    key.value = frame["weights"][key.name]
        bpy.context.view_layer.update()
        worlds = global_matrices(frame)
        head_index = names.index("Head")
        change = conversion @ worlds[head_index] @ original_world[head_index].inverted() @ inverse_conversion
        desired_head = change @ rest_head
        head.matrix = armature.matrix_world.inverted() @ desired_head
        bpy.context.view_layer.update()
        expected_controls = {}
        for name, obj in controls.items():
            index = names.index(name)
            change = conversion @ worlds[index] @ original_world[index].inverted() @ inverse_conversion
            expected_controls[name] = change @ rest_control_world[name]
            obj.matrix_world = expected_controls[name]
            bpy.context.view_layer.update()
        maximum_control_error = max(abs(expected_controls[name][r][c] - obj.matrix_world[r][c])
            for name, obj in controls.items() for r in range(4) for c in range(4))
        require(maximum_control_error < 2e-6, "Blender control does not match canonical runtime transform")
        shaped = face_positions.copy()
        for name, delta in face_deltas.items():
            shaped += delta * frame["weights"][name]
        homogeneous = np.column_stack((shaped, np.ones(len(shaped))))
        canonical_world = np.zeros((len(shaped), 4))
        for slot in range(4):
            for joint in np.unique(face_joints[:, slot]):
                chosen = face_joints[:, slot] == joint
                transform = np.array(worlds[skin["joints"][int(joint)]]).astype(np.float64) @ inverse_bind[int(joint)]
                canonical_world[chosen] += (homogeneous[chosen] @ transform.T) * face_weights[chosen, slot, None]
        expected_positions = canonical_world[:, :3] @ np.array(conversion)[:3, :3].T
        evaluated = face_object.evaluated_get(bpy.context.evaluated_depsgraph_get())
        actual_positions = np.array([list(evaluated.matrix_world @ vertex.co) for vertex in evaluated.data.vertices])
        require(actual_positions.shape == expected_positions.shape, "Importer changed face vertex order/count; cannot certify direct replay")
        maximum_skin_error = float(np.max(np.linalg.norm(actual_positions - expected_positions, axis=1)))
        require(maximum_skin_error < 2e-6, "Imported face differs from canonical runtime skin/morph pose")
        # Replay current camera framing.
        vertical = max(focus["halfHeight"], focus["halfWidth"] / (args.width / args.height)) * 1.08
        camera_data.ortho_scale = vertical * 2
        seconds = frame["seconds"]
        dy = math.sin(seconds * (2 * math.pi / 4)) * 2.5 * 2.6 * (2 * vertical / args.height) if frame["motion"] else 0
        distance = 2 + math.sin(seconds * .21) * .06 if frame["motion"] else 2
        angle = math.radians(focus["azimuthDegrees"])
        look = target + Vector((0, -dy, 0))
        location = look + Vector((math.sin(angle) * distance, 0, math.cos(angle) * distance))
        camera.location = conversion @ location
        camera.rotation_euler = ((conversion @ look) - camera.location).to_track_quat("-Z", "Y").to_euler()
        record = {"name": frame["name"], "actualMapperPose": frame,
            "maximumControlMatrixError": maximum_control_error,
            "maximumFaceSkinPositionErrorMetres": maximum_skin_error,
            "jawWorldMatrixGltfRowMajor": [list(row) for row in worlds[names.index("Jaw")]],
            "cameraAzimuthDegrees": focus["azimuthDegrees"], "cameraWorldGltf": list(location)}
        if not args.no_render:
            output_image = args.output / (frame["name"] + ".png")
            scene.render.filepath = str(output_image.resolve())
            bpy.ops.render.render(write_still=True)
            record.update(file=output_image.name, imageSha256=hashlib.sha256(output_image.read_bytes()).hexdigest())
        records.append(record)
        print("VERIFIED_PRODUCTION_POSE " + json.dumps(record), flush=True)
    require(hashlib.sha256(args.asset.read_bytes()).hexdigest() == asset_hash, "Asset changed while rendering")
    require(hashlib.sha256(args.poses.read_bytes()).hexdigest() == pose_hash, "Pose export changed while rendering")
    manifest = {"schema": "continuum-exact-production-pose-host-replay-v1", "status": "PASS",
        "evidenceClass": "HOST_RENDER_NOT_DEVICE" if not args.no_render else "HOST_SKINNING_REPLAY_NOT_DEVICE",
        "assetSha256": asset_hash, "poseExportSha256": pose_hash,
        "renderScriptSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "mapperSourceCommit": poses["sourceCommit"], "mapperSourceHashes": poses["sourceHashes"],
        "backend": "Blender " + bpy.app.version_string + " Cycles CPU", "cpuThreads": args.threads,
        "pixels": [args.width, args.height], "samples": args.samples, "denoising": False,
        "rendered": not args.no_render, "frames": records,
        "limitations": ["Actual compiled mapper with synthetic inputs; no actual TTS/camera session",
            "Blender materials, studio lights and glow are not native Filament pixel/performance evidence",
            "Camera-relative gaze and smile are replayed only when present in exported poses",
            "Reference likeness still requires visual review"]}
    (args.output / "pose-replay-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
