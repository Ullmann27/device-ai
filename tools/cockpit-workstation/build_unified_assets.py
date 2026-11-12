"""Build owned meshes in Blender."""

import bpy
import bmesh
import argparse
import hashlib
import json
import math
import struct
import sys
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/cinema3d"
SOURCE = ROOT / "tools/cockpit-workstation/source/pr801-workstation.glb"
SOURCE_SHA = "f1edabf082ee4d30e275a862a375ac73dea65ab4e1f140ddb021845c87e6bcc3"
AVATAR = ROOT / "app/src/main/assets/avatar3d/continuum-avatar.glb"
AVATAR_SHA = "c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a"
TAU = math.tau
PINS = {}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def point(v):
    return (v[0], -v[2], v[1])


def linear(rgb):
    return tuple(c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in rgb)


def rgb(code):
    return linear(tuple(int(code[i:i + 2], 16) / 255 for i in (0, 2, 4)))


PALETTE = {
    "cyan": rgb("00E5FF"), "cyan-soft": rgb("78F6FB"),
    "violet": rgb("A43BFF"), "violet-rim": rgb("D881FF"),
    "magenta": rgb("F04DFF"), "amber": rgb("FFC45C"),
    "green": rgb("01FC7B"),
}


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def empty(name, parent=None):
    obj = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(obj)
    obj.parent = parent
    return obj


def material(name, color, metallic=0., roughness=.3, alpha=1., emission=None, strength=1., coat=0.):
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.node_tree.nodes.clear()
    output = mat.node_tree.nodes.new("ShaderNodeOutputMaterial")
    node = mat.node_tree.nodes.new("ShaderNodeBsdfPrincipled")
    node.inputs["Base Color"].default_value = (*color, 1.)
    node.inputs["Metallic"].default_value = metallic
    node.inputs["Roughness"].default_value = roughness
    node.inputs["Alpha"].default_value = alpha
    node.inputs["IOR"].default_value = 1.46
    node.inputs["Coat Weight"].default_value = coat
    node.inputs["Coat Roughness"].default_value = .16
    if emission is not None:
        node.inputs["Emission Color"].default_value = (*emission, 1.)
        node.inputs["Emission Strength"].default_value = strength
    mat.node_tree.links.new(node.outputs["BSDF"], output.inputs["Surface"])
    mat.diffuse_color = (*color, alpha)
    mat.use_backface_culling = alpha == 1.
    mat.surface_render_method = "BLENDED" if alpha < 1. else "DITHERED"
    PINS[name] = {"linear_base_color": list(color), "metallic": metallic, "roughness": roughness,
                  "alpha": alpha, "ior": 1.46, "clearcoat": coat,
                  "linear_emission": list(emission or (0., 0., 0.)), "emission_strength": strength}
    return mat


def unlit(name, color):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.node_tree.nodes.clear()
    output = mat.node_tree.nodes.new("ShaderNodeOutputMaterial")
    node = mat.node_tree.nodes.new("ShaderNodeEmission")
    node.inputs["Color"].default_value = (*color, 1.)
    node.inputs["Strength"].default_value = 1.
    lightpath = mat.node_tree.nodes.new("ShaderNodeLightPath")
    transparent = mat.node_tree.nodes.new("ShaderNodeBsdfTransparent")
    camera_only = mat.node_tree.nodes.new("ShaderNodeMixShader")
    mat.node_tree.links.new(lightpath.outputs["Is Camera Ray"], camera_only.inputs[0])
    mat.node_tree.links.new(transparent.outputs[0], camera_only.inputs[1])
    mat.node_tree.links.new(node.outputs[0], camera_only.inputs[2])
    mat.node_tree.links.new(camera_only.outputs[0], output.inputs["Surface"])
    mat.diffuse_color = (*color, 1.)
    mat.use_backface_culling = True
    PINS[name] = {"unlit": True, "linear_base_color": list(color), "alpha": 1.}
    return mat


def mesh(name, vertices, faces, mat, parent, smooth=True):
    data = bpy.data.meshes.new(name)
    data.from_pydata([point(v) for v in vertices], [], faces)
    data.update()
    obj = bpy.data.objects.new(name, data)
    bpy.context.collection.objects.link(obj)
    obj.parent = parent
    data.materials.append(mat)
    for polygon in data.polygons:
        polygon.use_smooth = smooth
    return obj


class Batch:
    def __init__(self, name, mat, parent):
        self.name, self.mat, self.parent = name, mat, parent
        self.vertices, self.faces = [], []

    def tube(self, points, radius, closed=False, sides=5):
        points = [Vector(p) for p in points]
        offset = len(self.vertices)
        previous = None
        for i, center in enumerate(points):
            before = points[(i - 1) % len(points)] if closed or i else points[i]
            after = points[(i + 1) % len(points)] if closed or i < len(points) - 1 else points[i]
            tangent = (after - before).normalized()
            if previous is None:
                axis = Vector((0, 1, 0)) if abs(tangent.y) < .9 else Vector((1, 0, 0))
                normal = tangent.cross(axis).normalized()
            else:
                normal = (previous - tangent * previous.dot(tangent)).normalized()
            side = tangent.cross(normal).normalized()
            previous = normal
            for j in range(sides):
                self.vertices.append(tuple(center + radius * (normal * math.cos(TAU * j / sides) + side * math.sin(TAU * j / sides))))
        count = len(points) if closed else len(points) - 1
        for i in range(count):
            k = (i + 1) % len(points)
            for j in range(sides):
                n = (j + 1) % sides
                self.faces.append((offset + i * sides + j, offset + i * sides + n,
                                   offset + k * sides + n, offset + k * sides + j))
        if not closed:
            self.faces.append(tuple(offset + j for j in reversed(range(sides))))
            self.faces.append(tuple(offset + (len(points) - 1) * sides + j for j in range(sides)))

    def ico(self, center, radius, scale=(1, 1, 1)):
        offset = len(self.vertices)
        self.vertices.extend(tuple(center[i] + radius * v[i] * scale[i] for i in range(3)) for v in ICO_VERTS)
        self.faces.extend(tuple(offset + v for v in face) for face in ICO_FACES)

    def finish(self):
        if self.vertices:
            return mesh(self.name, self.vertices, self.faces, self.mat, self.parent)


bm = bmesh.new()
bmesh.ops.create_icosphere(bm, subdivisions=1, radius=1.)
bm.verts.ensure_lookup_table()
bm.verts.index_update()
ICO_VERTS = [tuple(v.co) for v in bm.verts]
ICO_FACES = [tuple(v.index for v in f.verts) for f in bm.faces]
bm.free()


def box(name, center, dimensions, mat, parent, bevel=.002):
    bpy.ops.mesh.primitive_cube_add(size=1., location=point(center))
    obj = bpy.context.object
    obj.name = name
    obj.scale = (dimensions[0], dimensions[2], dimensions[1])
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.parent = parent
    obj.data.materials.append(mat)
    mod = obj.modifiers.new("Machined edge", "BEVEL")
    mod.width = min(bevel, min(dimensions) * .32)
    mod.segments = 3
    mod.affect = "EDGES"
    normal = obj.modifiers.new("Face-weighted normals", "WEIGHTED_NORMAL")
    normal.keep_sharp = True
    return obj


def sphere(name, center, scale, mat, parent, segments=32, rings=16):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, radius=1., location=point(center))
    obj = bpy.context.object
    obj.name = name
    obj.scale = (scale[0], scale[2], scale[1])
    obj.parent = parent
    obj.data.materials.append(mat)
    for polygon in obj.data.polygons:
        polygon.use_smooth = True
    return obj


def export(path):
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.export_scene.gltf(filepath=str(path), export_format="GLB", use_selection=True,
        export_yup=True, export_apply=True, export_texcoords=True, export_normals=True,
        export_tangents=False, export_materials="EXPORT", export_cameras=False,
        export_lights=False, export_animations=False, export_extras=True)


def workstation():
    reset()
    bpy.ops.import_scene.gltf(filepath=str(SOURCE))
    room = bpy.data.objects["cockpit-room"]
    monitor = bpy.data.objects["cockpit-monitor"]
    sides = bpy.data.objects["cockpit-side-monitors"]
    sides["native_plane_z"] = 0.
    sides["native_document_bounds"] = [.02, .57, .22, .87]
    sides["native_draft_bounds"] = [.78, .57, .98, .87]
    raw = SOURCE.read_bytes()
    size = struct.unpack_from("<I", raw, 12)[0]
    for entry in json.loads(raw[20:20+size])["materials"]:
        PINS[entry["name"]] = {"source_gltf": entry}
    # UV origin is top-left.
    pixels = mesh("monitor-live-pixels", [(-.465, .38, .032), (-.465, -.445, .032),
                  (.465, -.445, .032), (.465, .38, .032)], [(0, 1, 2, 3)],
                  unlit("monitor-live-pixels", (.001, .002, .004)), monitor, False)
    layer = pixels.data.uv_layers.new(name="TEXCOORD_0")
    blender_uvs = [(0., 1.), (0., 0.), (1., 0.), (1., 1.)]
    for loop in pixels.data.loops:
        layer.data[loop.index].uv = blender_uvs[loop.vertex_index]
    monitor["live_pixel_contract"] = "Opaque unlit external texture; no fabricated app content"
    room["provenance"] = "Reference rounded desk seed; imported geometry and materials preserved"
    export(ASSETS / "cockpit-workstation.glb")


def hash_unit(i, j):
    h = (i * 374761393 + j * 668265263) & 0xffffffff
    h = ((h ^ (h >> 13)) * 1274126177) & 0xffffffff
    return ((h ^ (h >> 16)) & 0xffff) / 65535.


def orbit_point(angle, radius, tilt, yaw):
    x = math.cos(angle) * radius
    y = math.sin(angle) * math.sin(tilt) * radius
    z = math.sin(angle) * math.cos(tilt) * radius
    return (x * math.cos(yaw) - y * math.sin(yaw), -(x * math.sin(yaw) + y * math.cos(yaw)) - .10, z)


def ring_points(radius, y, count=96):
    return [(math.cos(TAU * i / count) * radius, y, math.sin(TAU * i / count) * radius) for i in range(count)]


def cubic(a, b, c, d, count=24):
    return [tuple((1-t)**3*a[k] + 3*(1-t)**2*t*b[k] + 3*(1-t)*t*t*c[k] + t**3*d[k] for k in range(3))
            for t in (i/count for i in range(count))]


def orb():
    reset()
    root = empty("continuum-orb")
    root["coordinate_contract"] = "Origin=sphere center; sphere radius=1; glTF +z faces camera"
    root["source"] = "ContinuumBrainOrbRenderer.kt / ContinuumOrbitalRing.kt / JarvisDesign.kt"
    neon = {name: material("orb-" + name, color, .1, .24, emission=color, strength=1.3)
            for name, color in PALETTE.items()}
    soft = {name: material("orb-soft-" + name, color, 0., .4, .13, color, .6)
            for name, color in PALETTE.items()}
    glass = material("orb-obsidian-glass", rgb("091529"), 0., .13, .065, coat=.38)
    brain_glass = material("orb-brain-glass", rgb("24728F"), 0., .24, .12, coat=.2)
    core_glass = material("orb-core-glass", rgb("05798C"), 0., .19, .16, PALETTE["cyan"], .25)
    steel = material("orb-pedestal-metal", (.035, .047, .075), .62, .27, coat=.25)
    sphere("orb-containment-shell", (0, 0, 0), (.995, .995, .995), glass, root, 48, 24)
    sphere("orb-core", (0, -.10, 0), (.42, .42, .42), core_glass, root, 28, 16)
    # Four original orbital planes.
    angles = [(.24, -.20), (-.49, .27), (.78, -.42), (-.31, .55)]
    pairs = [("magenta", "violet-rim"), ("cyan", "violet"),
             ("violet-rim", "cyan"), ("amber", "magenta")]
    for index, ((tilt, yaw), colors) in enumerate(zip(angles, pairs)):
        parent = empty("orb-orbit-" + str(index), root)
        parent["source_inclination"] = tilt
        parent["source_azimuth"] = yaw
        radius = 1.25 + index * .07
        for half, color in enumerate(colors):
            points = [orbit_point((half + j / 96) * math.pi, radius, tilt, yaw) for j in range(97)]
            line = Batch("orbit-%d-%d-core" % (index, half), neon[color], parent)
            line.tube(points, .007, sides=6)
            line.finish()
            aura = Batch("orbit-%d-%d-volume" % (index, half), soft[color], parent)
            aura.tube(points, .017, sides=5)
            aura.finish()
        bead = Batch("orbit-%d-bead" % index, neon[colors[0]], parent)
        bead.ico(orbit_point(index * 2.1, radius, tilt, yaw), .024)
        bead.finish()
    # Spherical neural grid.
    grid = Batch("orb-spherical-grid", soft["cyan"], root)
    points = Batch("orb-grid-nodes", neon["cyan-soft"], root)
    tilt = .32
    def globe(theta, phi):
        x, y, z = math.sin(theta) * math.cos(phi), math.cos(theta), math.sin(theta) * math.sin(phi)
        return (.97*x, .97*(y*math.cos(tilt)-z*math.sin(tilt)), .97*(y*math.sin(tilt)+z*math.cos(tilt)))
    for i in range(1, 7):
        theta = math.pi * i / 7
        grid.tube([globe(theta, TAU*j/96) for j in range(96)], .0042, True, 4)
        for j in range(14):
            points.ico(globe(theta, TAU*j/14), .010)
    for j in range(14):
        grid.tube([globe(math.pi*i/64, TAU*j/14) for i in range(65)], .0037, False, 4)
    grid.finish()
    points.finish()
    # Two physical brain hemispheres.
    for side in (-1, 1):
        sphere("orb-brain-lobe-%s" % side, (side*.285, .33, .025), (.40, .43, .32), brain_glass, root, 28, 18)
        folds = Batch("orb-brain-folds-%s" % side, neon["cyan" if side < 0 else "violet-rim"], root)
        for k in range(8):
            v = (k + 1) / 9 * math.pi
            line = []
            for j in range(64):
                u = TAU * j / 64
                ripple = .023 * math.sin(7*u + k*.7)
                line.append((side*.285 + (.40+ripple)*math.sin(v)*math.cos(u),
                             .33 + (.43+ripple)*math.cos(v),
                             .025 + (.32+ripple)*math.sin(v)*math.sin(u)))
            folds.tube(line, .0055, True, 4)
        folds.finish()
    neurons = []
    neural = [Batch("orb-neurons-" + c, neon[c], root) for c in ("magenta", "violet-rim", "cyan")]
    links = [Batch("orb-neural-links-" + c, soft[c], root) for c in ("magenta", "violet-rim", "cyan")]
    for i in range(36):
        theta, phi, w = hash_unit(i, 3)*math.pi, hash_unit(i, 7)*TAU, hash_unit(i, 13)
        radius = .22 + .70*w
        p = (math.sin(theta)*math.cos(phi)*.82*radius + (-.08 if i % 2 == 0 else .08),
             -math.cos(theta)*.72*radius + .10, math.sin(theta)*math.sin(phi)*.82*radius)
        neurons.append(Vector(p))
        neural[i % 3].ico(p, .016 + .020*w)
    for i, a in enumerate(neurons):
        for j in range(i+1, len(neurons)):
            if (a-neurons[j]).length <= .48:
                links[(i+j) % 3].tube([a, neurons[j]], .0032, sides=4)
    for batch in neural + links:
        batch.finish()
    dust = [Batch("orb-particles-" + c, soft[c], root) for c in ("cyan", "magenta", "amber")]
    for i in range(70):
        angle = hash_unit(i, 11)*TAU
        radius = .2 + 1.35*hash_unit(i, 23)
        depth = (hash_unit(i, 37)-.5)*1.1
        dust[i % 3].ico((math.cos(angle)*radius, math.sin(angle)*radius, depth), .005+.009*hash_unit(i, 41))
    for batch in dust:
        batch.finish()
    # Original infinity, actual tube.
    iw, ih, z = .19, .09, 1.012
    origin = (0., -.18, z)
    curves = [
        (origin, (iw*.5, -.18+ih*1.4, z), (iw*1.15, -.18+ih*1.1, z), (iw*1.15, -.18, z)),
        ((iw*1.15, -.18, z), (iw*1.15, -.18-ih*1.1, z), (iw*.5, -.18-ih*1.4, z), origin),
        (origin, (-iw*.5, -.18+ih*1.4, z), (-iw*1.15, -.18+ih*1.1, z), (-iw*1.15, -.18, z)),
        ((-iw*1.15, -.18, z), (-iw*1.15, -.18-ih*1.1, z), (-iw*.5, -.18-ih*1.4, z), origin),
    ]
    logo = Batch("orb-original-infinity", neon["cyan-soft"], root)
    logo.tube([p for curve in curves for p in cubic(*curve)], .014, True, 8)
    logo.finish()
    font = bpy.data.fonts.load(str(ROOT/"app/src/main/assets/fonts/Exo2-Medium.ttf"))
    for label, y in (("Continuum", -.52), ("Gehirn", -.70)):
        data = bpy.data.curves.new("orb-original-label-" + label.lower(), "FONT")
        data.body, data.font, data.size = label, font, .11
        data.align_x, data.align_y = "CENTER", "CENTER"
        data.extrude, data.bevel_depth = .0015, .0006
        data.resolution_u = 4
        data.bevel_resolution = 1
        obj = bpy.data.objects.new(data.name, data)
        bpy.context.collection.objects.link(obj)
        obj.parent = root
        obj.location = point((0, y, 1.012))
        obj.rotation_euler[0] = math.pi / 2
        data.materials.append(neon["cyan-soft"])
        bpy.context.view_layer.objects.active = obj
        obj.select_set(True)
        bpy.ops.object.convert(target="MESH")
        obj.select_set(False)
    # Original concentric pedestal tiers.
    pedestal = Batch("orb-pedestal-rings", steel, root)
    amber = Batch("orb-pedestal-amber", neon["amber"], root)
    cyan = Batch("orb-pedestal-cyan", neon["cyan"], root)
    for tier in range(3):
        y, radius = -1.10-tier*.06, .95+tier*.14
        pedestal.tube(ring_points(radius, y), .025, True, 6)
        amber.tube(ring_points(radius*.86, y+.012), .006, True, 5)
        cyan.tube(ring_points(radius*.70, y+.012), .005, True, 5)
    amber.tube(ring_points(1.40, -1.24), .008, True, 5)
    for batch in (pedestal, amber, cyan):
        batch.finish()
    for index in (-1, 0, 1):
        box("orb-emitter-" + str(index), (index*.18, -1.40, .65), (.11, .03, .05), neon["cyan"], root, .007)
    cone = Batch("orb-projection-volume", soft["cyan"], root)
    for i in range(9):
        t = i/8
        cone.tube(ring_points(1.18*(1-t)+.32*t, -1.02+.96*t, 72), .004, True, 4)
    for i in range(-2, 3):
        cone.tube([(i*.16, -1.04, -.20), (i*.08, -.10, -.20)], .004, sides=4)
    cone.finish()
    export(ASSETS / "continuum-orb.glb")


def record(path):
    raw = path.read_bytes()
    size = struct.unpack_from("<I", raw, 12)[0]
    gltf = json.loads(raw[20:20+size])
    triangles = sum(gltf["accessors"][p["indices"]]["count"]//3
                    for m in gltf["meshes"] for p in m["primitives"])
    return {"path": str(path.relative_to(ROOT)), "sha256": sha(path), "bytes": len(raw),
            "meshes": len(gltf["meshes"]), "triangles_unique_meshes": triangles,
            "nodes": len(gltf["nodes"]), "materials": gltf["materials"],
            "extensions_used": gltf.get("extensionsUsed", [])}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--workstation-only", action="store_true")
    args = parser.parse_args(sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else [])
    assert bpy.app.version[:2] == (4, 3), "Revalidate another Blender version"
    assert sha(SOURCE) == SOURCE_SHA, "PR801 source changed"
    source_metadata = json.loads(SOURCE.with_suffix(".json").read_text())
    assert source_metadata["sha256"] == SOURCE_SHA
    assert source_metadata["bytes"] == SOURCE.stat().st_size <= 32768
    assert sha(AVATAR) == AVATAR_SHA, "Original avatar changed"
    ASSETS.mkdir(exist_ok=True)
    preserved_orb = None
    if args.workstation_only:
        previous = json.loads((ASSETS/"unified-assets-manifest.json").read_text())
        preserved_orb = next(entry for entry in previous["files"] if entry["path"].endswith("/continuum-orb.glb"))
        assert sha(ASSETS/"continuum-orb.glb") == preserved_orb["sha256"]
        PINS.update({name: value for name, value in previous["material_parameters"].items() if name.startswith("orb-")})
    workstation()
    if not args.workstation_only:
        orb()
    elif preserved_orb:
        assert sha(ASSETS/"continuum-orb.glb") == preserved_orb["sha256"]
    files = [record(ASSETS/name) for name in ("cockpit-workstation.glb", "continuum-orb.glb")]
    assert sha(AVATAR) == AVATAR_SHA, "Original avatar changed during build"
    sources = ["app/src/main/java/at/heinz/deviceai/ui/ContinuumBrainOrbRenderer.kt",
               "app/src/main/java/at/heinz/deviceai/ui/ContinuumOrbitalRing.kt",
               "app/src/main/java/at/heinz/deviceai/ui/JarvisDesign.kt",
               "app/src/main/java/at/heinz/deviceai/avatar3d/CockpitStageFrame.kt"]
    manifest = {"evidence": "BLENDER_MESH_BUILD_NOT_DEVICE_VERIFICATION", "blender": bpy.app.version_string,
        "build_scope": "workstation_only" if args.workstation_only else "all_assets",
        "builder_sha256": sha(Path(__file__)), "source_metadata": source_metadata,
        "source_sha256": SOURCE_SHA, "original_avatar_sha256": AVATAR_SHA,
        "parameter_sources": {p: sha(ROOT/p) for p in sources},
        "coordinates": "glTF x right, y up, z toward camera; orb sphere radius 1",
        "screen": {"node": "monitor-live-pixels", "uv_origin": "top-left", "opaque": True,
                   "local_bounds": [-.465, -.445, .032, .465, .38, .032]},
        "material_parameters": PINS, "files": files}
    (ASSETS/"unified-assets-manifest.json").write_text(json.dumps(manifest, indent=2)+"\n")
    for file in files:
        print("ASSET_BUILT", file["path"], file["bytes"], file["sha256"], flush=True)


if __name__ == "__main__":
    main()
