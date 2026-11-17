"""
Continuum Lokal: reproducible Blender scenes (headless) for the decorative 3D raster assets.

Run (Blender 4.2 LTS, CPU Cycles):
  blender -b --factory-startup -P tools/blender/continuum_scenes.py -- --part all --out /tmp/continuum-blender --samples 96

Parts: orb (glass sphere with brain on a light pedestal, RGBA), background (spaceship interior with depth of field,
wide + tall), tiles (one small hologram scene per dock target). No external textures, no reference images: every
mesh, light and material is generated here, so the output is fully reproducible from this file.
Metrics (orb centre/radius in image fractions) are written to <out>/metrics.json for the Kotlin manifest.
"""
import argparse, json, math, os, random, sys
import bpy, bmesh
from mathutils import Vector, Matrix
from bpy_extras.object_utils import world_to_camera_view

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument("--part", default="all")
ap.add_argument("--out", default="/tmp/continuum-blender")
ap.add_argument("--samples", type=int, default=96)
ap.add_argument("--scale", type=float, default=1.0, help="resolution multiplier (0.25 = quick draft)")
args = ap.parse_args(argv)
os.makedirs(args.out, exist_ok=True)
METRICS = {}

CYAN = (0.10, 0.80, 1.00); VIOLET = (0.55, 0.30, 1.00); AMBER = (1.00, 0.55, 0.12); ICE = (0.75, 0.93, 1.0)


def reset(samples, w, h, transparent=False):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = samples
    sc.cycles.use_denoising = True
    sc.cycles.seed = 52
    sc.cycles.max_bounces = 8; sc.cycles.transmission_bounces = 8; sc.cycles.glossy_bounces = 4
    sc.render.threads_mode = "AUTO"
    sc.render.resolution_x = int(w * args.scale); sc.render.resolution_y = int(h * args.scale)
    sc.render.resolution_percentage = 100
    sc.render.film_transparent = transparent
    sc.cycles.film_transparent_glass = transparent
    sc.view_settings.view_transform = "AgX"
    sc.view_settings.look = "AgX - Medium High Contrast"
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA" if transparent else "RGB"
    sc.render.image_settings.color_depth = "16"
    world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
    bg = world.node_tree.nodes["Background"]; bg.inputs[0].default_value = (0.004, 0.007, 0.016, 1); bg.inputs[1].default_value = 1
    return sc


def mat_emission(name, color, strength):
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear()
    e = nt.nodes.new("ShaderNodeEmission"); e.inputs[0].default_value = (*color, 1); e.inputs[1].default_value = strength
    o = nt.nodes.new("ShaderNodeOutputMaterial"); nt.links.new(e.outputs[0], o.inputs[0])
    return m


def mat_metal(name, color=(0.05, 0.07, 0.10), rough=0.28, metal=1.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1); b.inputs["Metallic"].default_value = metal
    b.inputs["Roughness"].default_value = rough
    return m


def mat_glass(name, tint=(0.85, 0.95, 1.0), rough=0.02):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*tint, 1); b.inputs["Transmission Weight"].default_value = 1.0
    b.inputs["Roughness"].default_value = rough; b.inputs["IOR"].default_value = 1.45
    return m


def assign(obj, mat):
    obj.data.materials.clear(); obj.data.materials.append(mat); return obj


def smooth(obj):
    for p in obj.data.polygons: p.use_smooth = True
    return obj


def cam(loc, target, lens=50, dof=None):
    bpy.ops.object.camera_add(location=loc)
    c = bpy.context.object
    c.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    c.data.lens = lens; c.data.sensor_fit = "AUTO"
    if dof:
        c.data.dof.use_dof = True; c.data.dof.focus_distance = dof[0]; c.data.dof.aperture_fstop = dof[1]
    bpy.context.scene.camera = c
    return c


def area(loc, target, size, energy, color=(1, 1, 1), camera_visible=False):
    bpy.ops.object.light_add(type="AREA", location=loc)
    l = bpy.context.object; l.data.shape = "DISK"; l.data.size = size; l.data.energy = energy; l.data.color = color
    l.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    l.visible_camera = camera_visible
    return l


def point(loc, energy, color, radius=0.05):
    bpy.ops.object.light_add(type="POINT", location=loc)
    l = bpy.context.object; l.data.energy = energy; l.data.color = color; l.data.shadow_soft_size = radius
    return l


def torus(major, minor, loc=(0, 0, 0), rot=(0, 0, 0), mat=None, seg=160):
    bpy.ops.mesh.primitive_torus_add(major_radius=major, minor_radius=minor, major_segments=seg, minor_segments=12,
                                     location=loc, rotation=rot)
    o = smooth(bpy.context.object)
    if mat: assign(o, mat)
    return o


def cyl(r, depth, loc, mat, verts=128):
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=depth, location=loc, vertices=verts)
    o = bpy.context.object
    bev = o.modifiers.new("b", "BEVEL"); bev.width = min(0.02, depth / 3); bev.segments = 3
    return assign(smooth(o), mat)


def box(size, loc, mat, rot=(0, 0, 0), bevel=0.01):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.object; o.scale = size
    if bevel:
        b = o.modifiers.new("b", "BEVEL"); b.width = bevel; b.segments = 2
    return assign(o, mat)


def brain_material():
    """Translucent neural tissue: dark-blue body, fresnel cyan rim, glowing gyri ridges, warm core light."""
    m = bpy.data.materials.new("brain"); m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear(); L = nt.links.new
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    lw = nt.nodes.new("ShaderNodeLayerWeight"); lw.inputs[0].default_value = 0.12
    noise = nt.nodes.new("ShaderNodeTexNoise"); noise.inputs["Scale"].default_value = 18; noise.inputs["Detail"].default_value = 6
    wave = nt.nodes.new("ShaderNodeTexWave"); wave.inputs["Scale"].default_value = 9; wave.inputs["Distortion"].default_value = 14
    wave.inputs["Detail"].default_value = 4
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.55; ramp.color_ramp.elements[0].color = (0, 0, 0, 1)
    ramp.color_ramp.elements[1].position = 0.95; ramp.color_ramp.elements[1].color = (1, 1, 1, 1)
    L(wave.outputs["Fac"], ramp.inputs[0])
    mix_mask = nt.nodes.new("ShaderNodeMath"); mix_mask.operation = "MAXIMUM"
    rim = nt.nodes.new("ShaderNodeMath"); rim.operation = "POWER"; rim.inputs[1].default_value = 2.5
    L(lw.outputs["Facing"], rim.inputs[0])
    ridge = nt.nodes.new("ShaderNodeMath"); ridge.operation = "MULTIPLY"; ridge.inputs[1].default_value = 0.8
    L(ramp.outputs[0], ridge.inputs[0])
    L(ridge.outputs[0], mix_mask.inputs[0]); L(rim.outputs[0], mix_mask.inputs[1])
    col = nt.nodes.new("ShaderNodeMix"); col.data_type = "RGBA"
    col.inputs[6].default_value = (0.0, 0.10, 1.0, 1); col.inputs[7].default_value = (0.15, 0.75, 1.0, 1)
    L(mix_mask.outputs[0], col.inputs[0])
    strength = nt.nodes.new("ShaderNodeMath"); strength.operation = "MULTIPLY_ADD"
    strength.inputs[1].default_value = 1.6; strength.inputs[2].default_value = 0.12
    L(mix_mask.outputs[0], strength.inputs[0])
    em = nt.nodes.new("ShaderNodeEmission"); L(col.outputs[2], em.inputs[0]); L(strength.outputs[0], em.inputs[1])
    glass = nt.nodes.new("ShaderNodeBsdfGlass"); glass.inputs[0].default_value = (0.1, 0.35, 1, 1); glass.inputs[1].default_value = 0.25
    add = nt.nodes.new("ShaderNodeAddShader"); L(em.outputs[0], add.inputs[0]); L(glass.outputs[0], add.inputs[1])
    L(add.outputs[0], out.inputs[0])
    return m


def make_brain(center, size):
    """Two displaced ellipsoid hemispheres with a longitudinal fissure, cerebellum and stem."""
    parts = []
    tex = bpy.data.textures.new("gyri", "CLOUDS"); tex.noise_scale = 0.16; tex.noise_depth = 2; tex.noise_basis = "BLENDER_ORIGINAL"
    for side in (-1, 1):
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=6, radius=1, location=(0, 0, 0))
        o = bpy.context.object
        bm = bmesh.new(); bm.from_mesh(o.data)
        for v in bm.verts:
            x, y, z = v.co
            if x * side < 0: v.co.x = x * 0.25  # flatten the inner face (fissure)
            v.co.x *= 0.50; v.co.y *= 0.82; v.co.z *= 0.62
            if v.co.z < 0: v.co.z *= 0.75 + 0.25 * max(0.0, -v.co.y)  # flatter base, deeper temporal lobe
            v.co.x += side * 0.06
        bm.to_mesh(o.data); bm.free()
        d = o.modifiers.new("gyri", "DISPLACE"); d.texture = tex; d.strength = 0.09; d.mid_level = 0.5
        parts.append(o)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1, location=(0, 0.52, -0.42), segments=48, ring_count=24)
    cb = bpy.context.object; cb.scale = (0.40, 0.26, 0.20)
    d = cb.modifiers.new("folia", "DISPLACE"); d.texture = tex; d.strength = 0.03
    parts.append(cb)
    bpy.ops.mesh.primitive_cylinder_add(radius=0.09, depth=0.55, location=(0, 0.25, -0.62), rotation=(math.radians(-25), 0, 0))
    parts.append(bpy.context.object)
    mat = brain_material()
    tilt = Matrix.Rotation(math.radians(28), 4, "X") @ Matrix.Rotation(math.radians(-18), 4, "Z")
    for o in parts:
        smooth(o); assign(o, mat)
        o.matrix_world = Matrix.Translation(Vector(center)) @ Matrix.Scale(size, 4) @ tilt @ o.matrix_world
    return parts


def neural_points(n, radius, mat, seed=7):
    random.seed(seed)
    for _ in range(n):
        while True:
            p = Vector((random.uniform(-1, 1), random.uniform(-1, 1), random.uniform(-1, 1)))
            if p.length <= 1: break
        bpy.ops.mesh.primitive_uv_sphere_add(radius=random.uniform(0.006, 0.014), location=p * radius, segments=8, ring_count=6)
        assign(bpy.context.object, mat)


def lat_long_net(radius, mat):
    """Thin latitude/longitude filaments on an inner shell (the 'knot net' of the vector orb)."""
    for i in range(1, 6):
        lat = -math.pi / 2 + i * math.pi / 6
        torus(radius * math.cos(lat), 0.0022, loc=(0, 0, radius * math.sin(lat)), mat=mat, seg=128)
    for i in range(6):
        torus(radius, 0.0022, rot=(math.pi / 2, 0, i * math.pi / 6), mat=mat, seg=128)


def render(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print("rendered", path)


def project(cam_obj, p):
    sc = bpy.context.scene
    bpy.context.view_layer.update()
    v = world_to_camera_view(sc, cam_obj, Vector(p))
    return v.x, 1 - v.y


# ---------------------------------------------------------------------------------------------------------------- orb
def orb():
    sc = reset(args.samples, 1024, 1024, transparent=True)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1.0, segments=128, ring_count=64)
    glass = assign(smooth(bpy.context.object), mat_glass("orbGlass"))
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.965, segments=128, ring_count=64)
    inner = assign(smooth(bpy.context.object), mat_glass("orbInner", tint=(0.8, 0.94, 1.0), rough=0.015))
    for f in inner.data.polygons: f.flip()  # inward normals: visible wall thickness
    make_brain((0, 0, 0.10), 0.80)
    lat_long_net(0.93, mat_emission("net", CYAN, 1.4))
    neural_points(90, 0.85, mat_emission("neuron", ICE, 6))
    # warm core under the brain and the equatorial bands (ref-d amber + cyan rings)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.07, location=(0, 0, -0.30))
    assign(bpy.context.object, mat_emission("core", AMBER, 8))
    point((0, 0, -0.30), 25, AMBER, 0.1)
    point((0, 0, 0.15), 18, CYAN, 0.2)
    torus(1.10, 0.006, rot=(math.radians(82), 0, math.radians(8)), mat=mat_emission("ringA", AMBER, 4))
    torus(1.16, 0.004, rot=(math.radians(74), math.radians(-14), math.radians(-20)), mat=mat_emission("ringC", CYAN, 4))
    torus(1.23, 0.0035, rot=(math.radians(96), math.radians(18), math.radians(35)), mat=mat_emission("ringV", VIOLET, 5))
    # pedestal: three stepped metal discs with emissive edge rings and a glowing contact disc
    metal = mat_metal("pedestal", rough=0.22)
    cyl(0.62, 0.10, (0, 0, -1.03), metal); cyl(0.95, 0.09, (0, 0, -1.12), metal); cyl(1.32, 0.08, (0, 0, -1.20), metal)
    for r, z, s in ((0.62, -0.978, 22), (0.95, -1.073, 16), (1.32, -1.158, 12)):
        torus(r, 0.010, loc=(0, 0, z), mat=mat_emission("pedRing%.2f" % r, CYAN, s))
    bpy.ops.mesh.primitive_circle_add(vertices=96, radius=0.36, fill_type="NGON", location=(0, 0, -0.975))
    assign(bpy.context.object, mat_emission("contact", CYAN, 2.2))
    # light beam: emissive cone fading upward (transparent mix by height)
    bpy.ops.mesh.primitive_cone_add(vertices=96, radius1=0.55, radius2=0.95, depth=1.2, location=(0, 0, -0.38), end_fill_type="NOTHING")
    beam = bpy.context.object
    m = bpy.data.materials.new("beam"); m.use_nodes = True; nt = m.node_tree; nt.nodes.clear()
    o = nt.nodes.new("ShaderNodeOutputMaterial"); tc = nt.nodes.new("ShaderNodeTexCoord"); sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(tc.outputs["Generated"], sep.inputs[0])
    ramp = nt.nodes.new("ShaderNodeValToRGB"); ramp.color_ramp.elements[0].color = (1, 1, 1, 1); ramp.color_ramp.elements[1].color = (0, 0, 0, 1)
    nt.links.new(sep.outputs["Z"], ramp.inputs[0])
    tr = nt.nodes.new("ShaderNodeBsdfTransparent"); em = nt.nodes.new("ShaderNodeEmission")
    em.inputs[0].default_value = (*CYAN, 1); em.inputs[1].default_value = 0.06
    mix = nt.nodes.new("ShaderNodeMixShader"); nt.links.new(ramp.outputs[0], mix.inputs[0])
    nt.links.new(tr.outputs[0], mix.inputs[1]); nt.links.new(em.outputs[0], mix.inputs[2]); nt.links.new(mix.outputs[0], o.inputs[0])
    m.blend_method = "BLEND"; assign(beam, m)
    # studio lights: key upper-left (white Fresnel sickle), cyan rim right-back, amber kicker right
    area((-4, -3, 4), (0, 0, 0), 0.9, 700, (1, 1, 1))
    area((4, 2, 1.5), (0, 0, 0), 1.2, 400, CYAN)
    area((3.5, -2.5, -0.5), (0, 0, 0), 0.8, 200, AMBER)
    area((0, 0, 5), (0, 0, 0), 1.5, 200, ICE)
    c = cam((0, -6.4, 0.75), (0, 0, -0.18), lens=56)
    cx, cy = project(c, (0, 0, 0)); ex, _ = project(c, (1, 0, 0))
    METRICS["orb"] = {"centerX": round(cx, 4), "centerY": round(cy, 4), "radius": round(abs(ex - cx), 4)}
    render(os.path.join(args.out, "orb.png"))


# --------------------------------------------------------------------------------------------------------- background
def corridor():
    metal = mat_metal("hull", (0.035, 0.05, 0.075), 0.32)
    dark = mat_metal("panel", (0.018, 0.025, 0.04), 0.45, 0.6)
    strip = mat_emission("strip", CYAN, 9)
    warm = mat_emission("warm", AMBER, 7)
    ceil_light = mat_emission("ceil", ICE, 1.6)
    floor = mat_metal("floor", (0.03, 0.04, 0.06), 0.12)
    box((7, 40, 0.1), (0, 18, -0.05), floor, bevel=0)
    for side in (-1, 1):
        box((0.06, 40, 0.025), (side * 0.9, 18, 0.02), strip, bevel=0)
    for i in range(16):
        y = 1.5 + i * 1.7
        for side in (-1, 1):
            box((0.45, 0.42, 4.2), (side * 2.9, y, 2.1), metal, rot=(0, 0, side * 0.12), bevel=0.04)  # rib pillar
            box((0.04, 0.05, 2.6), (side * 2.66, y - 0.2, 1.7), warm if (side > 0 and i % 3 == 1) else strip, bevel=0)
            box((0.12, 1.2, 1.0), (side * 3.1, y + 0.85, 1.2), dark, bevel=0.02)  # wall consoles
            box((0.02, 0.9, 0.05), (side * 3.03, y + 0.85, 1.55 + 0.1 * (i % 2)), warm if side > 0 else strip, bevel=0)
        box((6.2, 0.42, 0.45), (0, y, 4.15), metal, rot=(0, 0, 0), bevel=0.04)  # ceiling beam
        box((1.6, 1.0, 0.04), (0, y + 0.85, 4.1), ceil_light, bevel=0)
        box((0.6, 0.6, 0.6), (-1.9 + (i % 3) * 0.2, y + 0.5, 0.3), dark, bevel=0.05)  # crates / equipment
    # far end: observation window with nebula glow
    m = bpy.data.materials.new("window"); m.use_nodes = True; nt = m.node_tree; nt.nodes.clear()
    o = nt.nodes.new("ShaderNodeOutputMaterial"); em = nt.nodes.new("ShaderNodeEmission")
    n = nt.nodes.new("ShaderNodeTexNoise"); n.inputs["Scale"].default_value = 3; n.inputs["Detail"].default_value = 8
    cr = nt.nodes.new("ShaderNodeValToRGB")
    cr.color_ramp.elements[0].position = 0.35; cr.color_ramp.elements[0].color = (0.0, 0.01, 0.04, 1)
    cr.color_ramp.elements[1].position = 0.75; cr.color_ramp.elements[1].color = (0.15, 0.45, 1.0, 1)
    nt.links.new(n.outputs["Fac"], cr.inputs[0]); nt.links.new(cr.outputs[0], em.inputs[0]); em.inputs[1].default_value = 3
    nt.links.new(em.outputs[0], o.inputs[0])
    box((6, 0.1, 4.2), (0, 29, 2.1), m, bevel=0)
    point((0, 26, 3), 400, CYAN, 1.0)
    point((2.5, 12, 2.5), 150, AMBER, 0.5)


def background():
    for name, (w, h), lens in (("bg-wide", (1600, 1200), 22), ("bg-tall", (900, 1800), 18)):
        reset(args.samples, w, h)
        corridor()
        cam((0, -1.2, 1.7), (0, 20, 2.0), lens=lens, dof=(2.2, 0.8))
        render(os.path.join(args.out, name + ".png"))


# -------------------------------------------------------------------------------------------------------------- tiles
TILE_ACCENT = {"chat": (0.12, 0.84, 0.69), "files": CYAN, "research": (0.36, 0.37, 1.0), "agents": VIOLET,
               "memory": (0.63, 0.36, 1.0), "device": (1.0, 0.43, 0.38), "settings": CYAN, "home": CYAN,
               "web": (0.18, 0.53, 1.0)}


def tile_object(target, glow, solid):
    if target == "chat":
        bpy.ops.mesh.primitive_cylinder_add(radius=0.55, depth=0.12, location=(0, 0, 0.75), rotation=(math.pi / 2, 0, 0), vertices=64)
        o = bpy.context.object; o.scale = (1.25, 1, 0.85); assign(o, solid)
        torus(0.56, 0.012, loc=(0, -0.07, 0.75), rot=(math.pi / 2, 0, 0), mat=glow).scale = (1.25, 0.85, 1)
        for i, hgt in enumerate((0.15, 0.32, 0.45, 0.28, 0.18)):
            box((0.05, 0.02, hgt), (-0.3 + i * 0.15, -0.08, 0.75), glow, bevel=0)
    elif target == "files":
        for i in range(3):
            box((0.62, 0.04, 0.82), (-0.12 + i * 0.12, 0.1 - i * 0.12, 0.7 + i * 0.03), solid, rot=(0, 0, 0.25 - i * 0.12), bevel=0.01)
            for k in range(4):
                box((0.36, 0.005, 0.03), (-0.14 + i * 0.12, 0.07 - i * 0.12 - 0.025, 0.85 - k * 0.12 + i * 0.03), glow, rot=(0, 0, 0.25 - i * 0.12), bevel=0)
    elif target == "research":
        for i in range(26):
            t = i / 25; a = t * math.pi * 3.2; z = 0.15 + t * 1.2
            for ph in (0, math.pi):
                bpy.ops.mesh.primitive_uv_sphere_add(radius=0.05, location=(0.32 * math.cos(a + ph), 0.32 * math.sin(a + ph), z))
                assign(bpy.context.object, glow if ph == 0 else solid)
            if i % 2 == 0:
                bpy.ops.mesh.primitive_cylinder_add(radius=0.012, depth=0.64, location=(0, 0, z), rotation=(0, math.pi / 2, a))
                assign(bpy.context.object, glow)
    elif target == "agents":
        for x, s in ((-0.45, 0.8), (0.45, 0.8), (0, 1.0)):
            bpy.ops.mesh.primitive_uv_sphere_add(radius=0.16 * s, location=(x, 0.1 * (1 - s) * 5, 0.95 * s + 0.12))
            assign(smooth(bpy.context.object), glow)
            bpy.ops.mesh.primitive_uv_sphere_add(radius=0.3 * s, location=(x, 0.1 * (1 - s) * 5, 0.42 * s))
            b = smooth(bpy.context.object); b.scale = (1, 0.7, 1.15); assign(b, solid)
    elif target == "memory":
        for i in range(3):
            cyl(0.48, 0.24, (0, 0, 0.3 + i * 0.32), solid)
            torus(0.485, 0.012, loc=(0, 0, 0.42 + i * 0.32), mat=glow)
    elif target == "device":
        box((1.1, 0.06, 0.68), (0, 0, 0.85), solid, bevel=0.02)
        box((1.0, 0.01, 0.58), (0, -0.04, 0.85), glow, bevel=0)
        box((0.08, 0.06, 0.4), (0, 0.05, 0.35), solid, bevel=0.01); box((0.5, 0.3, 0.04), (0, 0.05, 0.15), solid, bevel=0.01)
    elif target == "settings":
        cyl(0.42, 0.14, (0, 0, 0.75), solid).rotation_euler = (math.pi / 2, 0, 0)
        for i in range(10):
            a = i * math.pi / 5
            box((0.14, 0.14, 0.14), (0.48 * math.cos(a), 0, 0.75 + 0.48 * math.sin(a)), solid, rot=(0, -a, 0), bevel=0.01)
        torus(0.2, 0.025, loc=(0, -0.08, 0.75), rot=(math.pi / 2, 0, 0), mat=glow)
    elif target == "web":
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.55, location=(0, 0, 0.75), segments=24, ring_count=12)
        g = bpy.context.object; w = g.modifiers.new("w", "WIREFRAME"); w.thickness = 0.012; assign(g, glow)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.53, location=(0, 0, 0.75)); assign(smooth(bpy.context.object), solid)
    else:  # home
        box((0.8, 0.6, 0.5), (0, 0, 0.45), solid, bevel=0.02)
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.68, depth=0.45, location=(0, 0, 0.93), rotation=(0, 0, math.pi / 4))
        o = bpy.context.object; o.scale = (1, 0.8, 1); assign(o, glow)
        box((0.18, 0.01, 0.3), (0, -0.305, 0.35), glow, bevel=0)


def tiles():
    for target, accent in TILE_ACCENT.items():
        reset(max(32, args.samples // 2), 320, 240)
        glow = mat_emission("glow", accent, 5)
        solid = mat_glass("holo", tint=tuple(0.5 + 0.5 * c for c in accent), rough=0.12)
        floor = mat_metal("deck", (0.02, 0.03, 0.05), 0.15)
        box((12, 12, 0.05), (0, 0, -0.025), floor, bevel=0)
        torus(0.75, 0.012, loc=(0, 0, 0.01), mat=glow)
        tile_object(target, glow, solid)
        random.seed(hash(target) & 0xFFFF)
        for _ in range(14):  # out-of-focus bokeh lights behind the object
            bpy.ops.mesh.primitive_uv_sphere_add(radius=0.05, location=(random.uniform(-4, 4), random.uniform(3, 7), random.uniform(0.2, 3)))
            assign(bpy.context.object, glow)
        area((-2, -2.5, 3), (0, 0, 0.7), 2, 250, (1, 1, 1))
        area((2, 1.5, 1.5), (0, 0, 0.7), 2, 200, accent)
        cam((0, -3.2, 1.25), (0, 0, 0.62), lens=50, dof=(3.3, 1.0))
        render(os.path.join(args.out, "tile-%s.png" % target))


parts = ["orb", "background", "tiles"] if args.part == "all" else args.part.split(",")
for part in parts:
    {"orb": orb, "background": background, "tiles": tiles}[part]()
mpath = os.path.join(args.out, "metrics.json")
old = json.load(open(mpath)) if os.path.exists(mpath) else {}
old.update(METRICS); old["blender"] = bpy.app.version_string; old["samples"] = args.samples
json.dump(old, open(mpath, "w"), indent=2)
print("metrics", old)
