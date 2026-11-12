"""Build owned meshes in Blender."""

import bpy
import bmesh
import hashlib
import json
import math
import struct
import sys
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/cinema3d"
SOURCE = ROOT / "tools/cockpit-workstation/source/pr801-reference-workstation.glb"
SOURCE_SHA = "152669e9c7d8c5ea8627f09353fbd71bfca9ba93a6df6c751c7d44e2ea72de0d"
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
    sides.parent = room
    material("smoked-metal", (.026,.038,.058), .54,.27,coat=.24)
    silver = material("brushed-edge",(.15,.21,.28),.65,.24,coat=.15)
    material("smoked-glass",(.048,.11,.18),0.,.13,.24,coat=.35)
    accents = {name: material("button-"+name,color,.15,.26,emission=color,strength=.25,coat=.25)
               for name,color in PALETTE.items()}
    old = bpy.data.objects.get("keyboard-keys")
    if old:
        bpy.data.objects.remove(old,do_unlink=True)
    palette = ["cyan","cyan-soft","violet","magenta","violet-rim","cyan","green","amber","cyan-soft","violet","cyan"]
    for row in range(3):
        for column in range(11):
            x=(column-5)*.027+(row%2)*.004
            box("keyboard-raised-%d-%d"%(row,column),(x,-.4225,.093+row*.015),
                (.021,.003,.010),accents[palette[column]],room,.0013)
    box("keyboard-space",(0,-.4225,.139),(.13,.003,.010),silver,room,.0013)
    for side in (-1,1):
        for column in range(2):
            box("keyboard-control-%d-%d"%(side,column),(side*(.095+column*.03),-.4225,.139),
                (.022,.003,.010),accents["cyan" if side<0 else "violet"],room,.0013)
    blank=unlit("monitor-inactive-dark",(.001,.002,.004))
    for side in (-1,1):
        x=side*.38
        mesh("monitor-side-%d-idle"%side,[(x-.1004,-.0694,.001),(x-.1004,-.3706,.001),
            (x+.1004,-.3706,.001),(x+.1004,-.0694,.001)],[(0,1,2,3)],blank,sides,False)
    pixels = mesh("monitor-live-pixels",[(-.465,.38,.032),(-.465,-.445,.032),
        (.465,-.445,.032),(.465,.38,.032)],[(0,1,2,3)],
        unlit("monitor-live-pixels",(.001,.002,.004)),monitor,False)
    layer=pixels.data.uv_layers.new(name="TEXCOORD_0")
    uvs=[(0.,1.),(0.,0.),(1.,0.),(1.,1.)]
    for loop in pixels.data.loops:
        layer.data[loop.index].uv=uvs[loop.vertex_index]
    monitor["live_pixel_contract"]="Opaque unlit external texture; no fabricated app content"
    sides["content_contract"]="Inactive physical panels; no app source and no LIVE claim"
    room["provenance"]="Updated PR801 rounded geometry; Blender raised keycaps and real pixel plane"
    export(ASSETS/"cockpit-workstation.glb")


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
    source_path = ROOT/"tools/cockpit-workstation/source/original-orb-geometry.json"
    source = json.loads(source_path.read_text())
    for name, expected in source["source_hashes"].items():
        assert sha(ROOT/name) == expected, "Original orb parameters changed"
    root = empty("continuum-orb")
    root["coordinate_contract"] = "Sphere radius 1; glTF x right, y up, z toward camera"
    root["source"] = source["source"]
    root["source_geometry_sha256"] = sha(source_path)
    colors = {
        "cool": rgb("78E1FF"), "violet": rgb("AA82FF"), "pink": rgb("F078D7"),
        "halo": rgb("6E96F5"), "white": rgb("F0FCFF"), "blue": rgb("287DFF"),
        "logo-left": rgb("46DEFF"), "logo-mid": rgb("7892FF"), "logo-right": rgb("D664FF"),
        "logo-core": rgb("4650BE"),
    }
    neon = {name: material("orb-original-"+name, color, .08, .25, emission=color, strength=1.6)
            for name, color in colors.items()}
    soft = {name: material("orb-original-soft-"+name, color, 0., .32, .14, color, .8)
            for name, color in colors.items()}
    glass = material("orb-original-blue-glass", rgb("0C1A68"), 0., .16, .30, rgb("122C84"), .15, .35)
    core = material("orb-original-inner-glass", rgb("243EC6"), 0., .20, .55, rgb("2230B0"), .30, .25)
    sphere("orb-containment-shell", (0, 0, 0), (1, 1, 1), glass, root, 48, 24)
    sphere("orb-core", (0, 0, 0), (.4, .4, .4), core, root, 40, 20)
    sphere("orb-inner-volume", (0, -.05, 0), (.62, .62, .62), soft["blue"], root, 32, 16)
    rim = Batch("orb-original-inner-rim", neon["violet"], root)
    rim.tube([(.4*math.cos(TAU*i/128), .4*math.sin(TAU*i/128), .02) for i in range(128)], .01, True, 6)
    rim.finish()
    shell_rim = Batch("orb-original-containment-rim", soft["cool"], root)
    shell_rim.tube([(math.cos(TAU*i/144), math.sin(TAU*i/144), 0) for i in range(144)], .016, True, 5)
    shell_rim.finish()
    # Rest tilt belongs to group.
    for index, orbit in enumerate(source["orbits"]):
        parent = empty("orb-orbit-"+str(index), root)
        parent.rotation_euler[1] = math.radians(orbit["tilt"])
        parent["canvas_tilt_degrees"] = orbit["tilt"]
        parent["original_speed"] = orbit["speed"]
        a, b = orbit["a"], orbit["b"]
        depth = math.sqrt(a*a-b*b)
        def orbit_position(t):
            return (a*math.cos(t), -b*math.sin(t), depth*math.sin(t))
        for segment in range(4):
            color_index = 0 if segment in (1, 2) else 2
            color = rgb(f'{int(orbit["colors"][color_index]) & 0xffffff:06X}')
            mat = material("orb-orbit-%d-color-%d" % (index, color_index), color, .05, .28,
                           emission=color, strength=1.6)
            trace = Batch("orbit-%d-%d-core" % (index, segment), mat, parent)
            trace.tube([orbit_position(TAU*(segment+j/48)/4) for j in range(49)],
                       orbit["width"]/101*.30, sides=6)
            trace.finish()
        halo = Batch("orbit-%d-emission-volume" % index, soft["cool" if index%2==0 else "violet"], parent)
        halo.tube([orbit_position(TAU*i/144) for i in range(144)], orbit["width"]/101*1.6, True, 5)
        halo.finish()
    cloud = source["plexus"]
    volume = empty("orb-neural-volume", root)
    from mathutils import Matrix
    axis = Matrix(((1,0,0,0),(0,0,-1,0),(0,1,0,0),(0,0,0,1)))
    pitch = .3+.05*math.sin(.4*.21)
    volume.matrix_basis = axis @ Matrix.Rotation(-pitch,4,"X") @ Matrix.Rotation(.4*.16,4,"Y") @ axis.inverted()
    positions = [(x,-y,z) for x,y,z in zip(cloud["bx"],cloud["by"],cloud["bz"])]
    nodes = {name: Batch("orb-original-nodes-"+name, neon[name] if name != "halo" else soft[name], volume)
             for name in ("cool","violet","pink","halo")}
    links = {name: Batch("orb-original-links-"+name, soft[name], volume)
             for name in ("cool","violet","pink","halo")}
    for i, p in enumerate(positions):
        layer = cloud["layer"][i]
        name = "halo" if layer=="HALO" else "pink" if i%11==0 else "violet" if layer=="INNER" else "cool"
        nodes[name].ico(p, .009 if layer!="HALO" else .007)
    for i, (a,b) in enumerate(zip(cloud["edgeA"],cloud["edgeB"])):
        name = "halo" if cloud["layer"][a]=="HALO" else "violet" if (positions[a][2]+positions[b][2])<0 else "cool"
        links[name].tube([positions[a],positions[b]], .0032 if name!="halo" else .0024, sides=4)
    for batch in list(nodes.values())+list(links.values()):
        batch.finish()
    dust = {name: Batch("orb-original-dust-"+name, neon[name], volume) for name in ("cool","violet","pink")}
    for i,(x,y,z) in enumerate(zip(cloud["dx"],cloud["dy"],cloud["dz"])):
        name = ("cool","violet","pink")[cloud["dustTint"][i]]
        dust[name].ico((x,-y,z), cloud["dustSize"][i]/101*.85)
    for batch in dust.values():
        batch.finish()
    for i, (nx,ny,nz) in enumerate(zip(cloud["anx"],cloud["any"],cloud["anz"])):
        n=Vector((nx,-ny,nz)).normalized()
        u=Vector((0,0,1)).cross(n).normalized()
        if u.length < .0001:
            u=Vector((1,0,0))
        v=n.cross(u).normalized()
        start=math.radians(cloud["arcStart"][i]+.4*cloud["arcSpeed"][i])
        sweep=math.radians(cloud["arcSweep"][i])
        radius=cloud["arcRadius"][i]
        trace=Batch("orb-original-inner-arc-"+str(i),neon[("cool","blue","violet","pink")[i%4]],volume)
        trace.tube([tuple(radius*(u*math.cos(start+sweep*j/48)+v*math.sin(start+sweep*j/48)))
                    for j in range(49)], .0048, sides=5)
        trace.finish()
    # Original sine infinity geometry.
    logo = Batch("orb-original-infinity", neon["logo-mid"], root)
    infinity = [(.29*math.sin(TAU*i/96), -.02-.135*math.sin(2*TAU*i/96), .43) for i in range(96)]
    logo.tube(infinity, .0425, True, 8)
    logo.finish()
    highlight = Batch("orb-original-infinity-highlight", neon["white"], root)
    highlight.tube([(x,y,z+.038) for x,y,z in infinity], .0055, True, 5)
    highlight.finish()
    glow = Batch("orb-original-infinity-volume", soft["logo-left"], root)
    glow.tube(infinity,.085,True,5)
    glow.finish()
    # Physical original pedestal tiers.
    ratio=source["geometry"]["sphere"]
    pad=source["geometry"]["pad"]
    wall=source["geometry"]["wall"]
    steel=material("orb-original-pedestal-metal",rgb("0E2250"),.54,.28,coat=.2)
    for name,radius,top,height in (("flange",1.56,pad+.06+wall,.06),
                                   ("base",1.43,pad+.06,wall), ("pad",.97,pad,.07)):
        radius,top,height=radius/ratio,top/ratio,height/ratio
        bpy.ops.mesh.primitive_cylinder_add(vertices=96,radius=radius,depth=height,
            location=point((0,-top-height*.5,0)))
        obj=bpy.context.object
        obj.name="orb-original-pedestal-"+name
        obj.parent=root
        obj.data.materials.append(steel)
        bevel=obj.modifiers.new("Soft machined rim","BEVEL")
        bevel.width=.008
        bevel.segments=2
        obj.modifiers.new("Pedestal normals","WEIGHTED_NORMAL")
        trace=Batch("orb-original-pedestal-rim-"+name,neon["cool" if name!="base" else "violet"],root)
        trace.tube(ring_points(radius,-top+.006),.006,True,6)
        trace.finish()
    projection = Batch("orb-original-projection-beams", soft["cool"], root)
    for i in range(15):
        t=(i-7)/7
        projection.tube([(t*.5/ratio,-(pad-.05)/ratio,0),(t*.16,-.12,0)],.003,sides=4)
    projection.tube([(0,.98,0),(0,-pad/ratio,0)],.006,sides=5)
    projection.finish()
    export(ASSETS/"continuum-orb.glb")


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
    assert bpy.app.version[:2] == (4, 3), "Revalidate another Blender version"
    assert sha(SOURCE) == SOURCE_SHA, "PR801 source changed"
    assert sha(AVATAR) == AVATAR_SHA, "Original avatar changed"
    ASSETS.mkdir(exist_ok=True)
    workstation()
    orb()
    files = [record(ASSETS/name) for name in ("cockpit-workstation.glb", "continuum-orb.glb")]
    assert sha(AVATAR) == AVATAR_SHA, "Original avatar changed during build"
    sources = ["app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramRenderer.kt",
               "app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramPlexus.kt",
               "app/src/main/java/at/heinz/deviceai/ui/ContinuumOrbitalRing.kt",
               "app/src/main/java/at/heinz/deviceai/ui/JarvisDesign.kt",
               "app/src/main/java/at/heinz/deviceai/avatar3d/CockpitStageFrame.kt"]
    manifest = {"evidence": "BLENDER_MESH_BUILD_NOT_DEVICE_VERIFICATION", "blender": bpy.app.version_string,
        "builder_sha256": sha(Path(__file__)), "source_commit": "6c951be2a165b658f16fff63dced8a5a3a9e9ef7",
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
