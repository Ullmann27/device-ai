"""Host illustration of the Code56 camera flight around the real workstation GLB.

Evidence class: HOST_BLENDER_CYCLES_ILLUSTRATION – NOT Filament, NOT device pixels. The camera stays in front of the
room opening and the monitor group makes the presentation turn, exactly as CockpitCameraFlight does. The avatar, the original orb and
live monitor pixels are not part of this asset and are not shown. Camera math mirrors
app/src/main/java/at/heinz/deviceai/avatar3d/CockpitCameraFlight.kt (same constants); simplification: the orbit pivot
and the rest target are both the monitor centre, and the rest distance is a fixed multiple of the monitor width.

Usage: blender -b --factory-startup -P tools/cockpit-workstation/flight_preview.py -- <out_dir> [width height samples]
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Quaternion, Vector

ROOT = Path(__file__).resolve().parents[2]
GLB = ROOT / "app/src/main/assets/cinema3d/cockpit-workstation.glb"
import os
START_YAW = float(os.environ.get("FLIGHT_START_YAW", "60.0"))
START_PITCH = float(os.environ.get("FLIGHT_START_PITCH", "-10.0"))
START_RADIUS_WIDTHS = float(os.environ.get("FLIGHT_START_RADIUS", "1.6"))
START_FRUSTUM_SCALE = 2.4
START_MONITOR_YAW = -100.0
STAGE_WIDTH_IN_MONITOR_WIDTHS = 1 / .64  # Fold7 portrait stage width / monitor world width
ONLY_START = os.environ.get("FLIGHT_ONLY_START") == "1"
REST_DISTANCE_WIDTHS = 4.7  # ≈ distance/monitor width of the Fold7 portrait stage (3.0 / 0.64)


def quat_axis(axis, degrees):
    half = math.radians(degrees) * .5
    s = math.sin(half)
    return (axis[0] * s, axis[1] * s, axis[2] * s, math.cos(half))


def qmul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw, aw * bw - ax * bx - ay * by - az * bz)


def slerp(a, b, t):
    dot = sum(x * y for x, y in zip(a, b))
    if dot < 0:
        b, dot = tuple(-x for x in b), -dot
    if dot > .9995:
        q = tuple(x + (y - x) * t for x, y in zip(a, b))
    else:
        theta = math.acos(min(1., max(-1., dot)))
        s = math.sin(theta)
        q = tuple(x * math.sin((1 - t) * theta) / s + y * math.sin(t * theta) / s for x, y in zip(a, b))
    n = math.sqrt(sum(x * x for x in q))
    return tuple(x / n for x in q)


def rotate(q, v):
    x, y, z, w = q
    cx, cy, cz = y * v[2] - z * v[1], z * v[0] - x * v[2], x * v[1] - y * v[0]
    dx, dy, dz = y * cz - z * cy, z * cx - x * cz, x * cy - y * cx
    return (v[0] + 2 * (w * cx + dx), v[1] + 2 * (w * cy + dy), v[2] + 2 * (w * cz + dz))


def smootherstep(t):
    t = min(1., max(0., t))
    return t * t * t * (t * (t * 6 - 15) + 10)


def pose(progress, pivot, width, rest):
    e = smootherstep(progress)
    start = qmul(quat_axis((0, 1, 0), START_YAW), quat_axis((1, 0, 0), START_PITCH))
    q = slerp(start, (0., 0., 0., 1.), e)
    radius = min(rest, width * START_RADIUS_WIDTHS) + (rest - min(rest, width * START_RADIUS_WIDTHS)) * e
    offset, up = rotate(q, (0, 0, radius)), rotate(q, (0, 1, 0))
    eye = tuple(p + o for p, o in zip(pivot, offset))
    frustum = 1.0 if e >= 1 else START_FRUSTUM_SCALE + (1 - START_FRUSTUM_SCALE) * e
    turn = 0.0 if e >= 1 else START_MONITOR_YAW * (1 - e)
    return eye, pivot, up, frustum, turn


def to_blender(v):  # glTF/Filament (x right, y up, z toward camera) -> Blender (x, -z, y)
    return Vector((v[0], -v[2], v[1]))


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    out = Path(argv[0]) if argv else ROOT / "flight-preview"
    width, height, samples = (int(argv[1]), int(argv[2]), int(argv[3])) if len(argv) >= 4 else (960, 540, 32)
    out.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(GLB))
    monitor = bpy.data.objects["cockpit-monitor"]
    pivot_b = monitor.matrix_world.translation.copy()
    pivot = (pivot_b.x, pivot_b.z, -pivot_b.y)
    monitor_width = 1.0
    monitor.rotation_mode = "XYZ"
    monitor_rest_rotation = tuple(monitor.rotation_euler)
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = samples
    scene.cycles.use_denoising = False
    scene.render.resolution_x, scene.render.resolution_y = width, height
    scene.render.image_settings.file_format = "PNG"
    world = bpy.data.worlds.new("preview-world")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (.012, .018, .03, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
    scene.world = world
    for name, location, energy, color in [("key", (2.5, 3.0, 2.0), 900, (.75, .9, 1.)),
                                          ("rim", (-2.0, -3.0, 1.6), 700, (.95, .55, 1.)),
                                          ("fill", (0.0, 2.5, -0.5), 250, (.6, .8, 1.))]:
        light = bpy.data.lights.new(name, "AREA")
        light.energy, light.color, light.size = energy, color, 2.0
        obj = bpy.data.objects.new(name, light)
        obj.location = Vector(location)
        obj.rotation_euler = (to_blender(pivot) - obj.location).to_track_quat("-Z", "Y").to_euler()
        scene.collection.objects.link(obj)
    cam_data = bpy.data.cameras.new("flight")
    cam = bpy.data.objects.new("flight", cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    rest = monitor_width * REST_DISTANCE_WIDTHS
    for label, progress in [("0-rear", 0.0), ("1-rear-side", .3), ("2-side", .5), ("3-front-approach", .75),
                            ("4-rest-front", 1.0)][:1 if ONLY_START else 5]:
        eye, target, up, frustum, turn = pose(progress, pivot, monitor_width, rest)
        # Presentation turn of the monitor group about its stand axis (glTF +Y = Blender +Z).
        monitor.rotation_mode = "XYZ"
        monitor.rotation_euler = (monitor_rest_rotation[0], monitor_rest_rotation[1],
                                  monitor_rest_rotation[2] + math.radians(turn))
        # Same projection as FilamentAvatarRenderer: rest half-width/d, widened by the dolly-zoom frustum scale.
        cam_data.sensor_fit = "HORIZONTAL"
        cam_data.angle_x = 2 * math.atan(.5 * STAGE_WIDTH_IN_MONITOR_WIDTHS * monitor_width / rest * frustum)
        cam.location = to_blender(eye)
        forward = (to_blender(target) - cam.location).normalized()
        up_b = to_blender(up).normalized()
        right = forward.cross(up_b).normalized()
        true_up = right.cross(forward)
        cam.matrix_world = Matrix((
            (right.x, true_up.x, -forward.x, cam.location.x),
            (right.y, true_up.y, -forward.y, cam.location.y),
            (right.z, true_up.z, -forward.z, cam.location.z),
            (0, 0, 0, 1)))
        scene.render.filepath = str(out / f"flight-{label}.png")
        bpy.ops.render.render(write_still=True)
        print(f"FLIGHT_FRAME {label} progress={progress} eye={tuple(round(c, 3) for c in eye)} "
              f"fov_x={math.degrees(cam_data.angle_x):.1f} monitor_turn={turn:.1f}")


main()
