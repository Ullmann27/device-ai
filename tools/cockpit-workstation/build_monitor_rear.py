"""Deterministic rear-housing detail meshes for the main cockpit monitor (Code56).

Owned procedural geometry, no downloaded assets, no Blender needed. Coordinates are the LOCAL space of the workstation
GLB's `cockpit-monitor` group (bezel ±0.5, front +z, rear housing surface at z ≈ -0.033); Filament gives the root node
`monitor-rear-details` the same matrix as `cockpit-monitor` every frame, so the details stay attached through the
Code56 presentation turn. Everything lies behind z = -0.032 and can never cover the live pixel plane (z = +0.032).

Writes app/src/main/assets/cinema3d/monitor-rear-details.glb and the pin file monitor-rear-details.json.
"""
import hashlib
import json
import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/cinema3d/monitor-rear-details.glb"
PIN = OUT.with_suffix(".json")
REAR_SURFACE_Z = -0.032

blob = bytearray(); views = []; accessors = []; meshes = []; nodes = []


def data(values, components, kind):
    while len(blob) % 4: blob.append(0)
    offset = len(blob)
    blob.extend(struct.pack('<' + ('f' if kind == 5126 else 'H') * len(values), *values))
    views.append({'buffer': 0, 'byteOffset': offset, 'byteLength': len(blob) - offset})
    accessor = {'bufferView': len(views) - 1, 'componentType': kind, 'count': len(values) // components,
                'type': {1: 'SCALAR', 3: 'VEC3'}[components]}
    if components == 3:
        accessor['min'] = [min(values[c::3]) for c in range(3)]
        accessor['max'] = [max(values[c::3]) for c in range(3)]
    accessors.append(accessor)
    return len(accessors) - 1


def bevelled_box(bevel=.12):
    """Unit box (±0.5) with chamfered edges and corners: specular highlights on every edge."""
    pos = []; norm = []; ind = []
    inner = .5 - bevel

    def face(points, normal):
        u = [points[1][c] - points[0][c] for c in range(3)]
        v = [points[2][c] - points[0][c] for c in range(3)]
        cross = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
        if sum(cross[c] * normal[c] for c in range(3)) < 0: points = points[::-1]
        first = len(pos) // 3
        for point in points: pos.extend(point); norm.extend(normal)
        for j in range(1, len(points) - 1): ind.extend((first, first + j, first + j + 1))

    for axis in range(3):
        others = [j for j in range(3) if j != axis]
        for sign in (-1, 1):
            normal = [0., 0., 0.]; normal[axis] = sign
            pts = []
            for s, t in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                p = [0., 0., 0.]; p[axis] = sign * .5; p[others[0]] = s * inner; p[others[1]] = t * inner
                pts.append(p)
            face(pts, normal)
    for free in range(3):
        fixed = [j for j in range(3) if j != free]
        for s in (-1, 1):
            for t in (-1, 1):
                normal = [0., 0., 0.]; normal[fixed[0]] = s / math.sqrt(2); normal[fixed[1]] = t / math.sqrt(2)
                pts = []
                for k, outer in ((-1, 0), (1, 0), (1, 1), (-1, 1)):
                    p = [0., 0., 0.]; p[free] = k * inner
                    p[fixed[0]] = s * (.5 if outer == 0 else inner)
                    p[fixed[1]] = t * (inner if outer == 0 else .5)
                    pts.append(p)
                face(pts, normal)
    for sx in (-1, 1):
        for sy in (-1, 1):
            for sz in (-1, 1):
                signs = (sx, sy, sz)
                normal = [s / math.sqrt(3) for s in signs]
                face([[signs[c] * (.5 if c == axis else inner) for c in range(3)] for axis in range(3)], normal)
    return data(pos, 3, 5126), data(norm, 3, 5126), data(ind, 1, 5123)


def cylinder(sides=12):
    """Unit cylinder along local Y (radius 0.5, height 1) with caps."""
    pos = []; norm = []; ind = []
    for i in range(sides):
        a0 = 2 * math.pi * i / sides; a1 = 2 * math.pi * (i + 1) / sides
        first = len(pos) // 3
        for a, y in ((a0, -.5), (a1, -.5), (a1, .5), (a0, .5)):
            pos.extend((.5 * math.cos(a), y, .5 * math.sin(a))); norm.extend((math.cos(a), 0, math.sin(a)))
        ind.extend((first, first + 2, first + 1, first, first + 3, first + 2))
    for y, ny in ((.5, 1), (-.5, -1)):
        centre = len(pos) // 3
        pos.extend((0, y, 0)); norm.extend((0, ny, 0))
        for i in range(sides + 1):
            a = 2 * math.pi * i / sides
            pos.extend((.5 * math.cos(a), y, .5 * math.sin(a))); norm.extend((0, ny, 0))
        for i in range(sides):
            tri = (centre, centre + 1 + i, centre + 2 + i)
            ind.extend(tri if ny < 0 else (tri[0], tri[2], tri[1]))
    return data(pos, 3, 5126), data(norm, 3, 5126), data(ind, 1, 5123)


def mesh(name, geometry, material):
    position, normal, indices = geometry
    meshes.append({'name': name, 'primitives': [
        {'attributes': {'POSITION': position, 'NORMAL': normal}, 'indices': indices, 'material': material}]})
    return len(meshes) - 1


materials = [
    {'name': 'rear-smoked-metal', 'pbrMetallicRoughness': {'baseColorFactor': [.026, .034, .052, 1],
     'metallicFactor': .86, 'roughnessFactor': .30},
     'extensions': {'KHR_materials_clearcoat': {'clearcoatFactor': .35, 'clearcoatRoughnessFactor': .18}}},
    {'name': 'rear-brushed-edge', 'pbrMetallicRoughness': {'baseColorFactor': [.22, .26, .32, 1],
     'metallicFactor': .94, 'roughnessFactor': .20}},
    {'name': 'rear-vent-dark', 'pbrMetallicRoughness': {'baseColorFactor': [.004, .005, .008, 1],
     'metallicFactor': .2, 'roughnessFactor': .85}},
    {'name': 'rear-port-black', 'pbrMetallicRoughness': {'baseColorFactor': [.008, .009, .012, 1],
     'metallicFactor': .45, 'roughnessFactor': .55}},
    {'name': 'rear-cyan-led', 'pbrMetallicRoughness': {'baseColorFactor': [.03, .55, .72, 1],
     'metallicFactor': .1, 'roughnessFactor': .35}, 'emissiveFactor': [.02, .50, .70]},
    {'name': 'rear-violet-strip', 'pbrMetallicRoughness': {'baseColorFactor': [.32, .10, .60, 1],
     'metallicFactor': .1, 'roughnessFactor': .35}, 'emissiveFactor': [.28, .05, .50]},
    {'name': 'rear-cable', 'pbrMetallicRoughness': {'baseColorFactor': [.015, .016, .02, 1],
     'metallicFactor': 0, 'roughnessFactor': .62}},
]
SMOKED, BRUSHED, VENT, PORT, CYAN, VIOLET, CABLE = range(len(materials))

box = bevelled_box(); round_box = bevelled_box(.30); tube = cylinder()
meshes_by = {
    'housing': mesh('rear-housing', round_box, SMOKED), 'trim': mesh('rear-trim', box, BRUSHED),
    'vent': mesh('rear-vent-field', box, VENT), 'fin': mesh('rear-fin', box, BRUSHED),
    'port_panel': mesh('rear-port-panel', box, PORT), 'port': mesh('rear-port', box, BRUSHED),
    'led': mesh('rear-status-led', box, CYAN), 'strip': mesh('rear-accent-strip', box, VIOLET),
    'cyan_strip': mesh('rear-cyan-strip', box, CYAN), 'screw': mesh('rear-screw', tube, BRUSHED),
    'hinge': mesh('rear-hinge', tube, BRUSHED), 'cable': mesh('rear-cable', tube, CABLE),
    'arm': mesh('rear-stand-arm', box, SMOKED),
}
children = []


def part(name, kind, xyz, scale, rotation=None):
    node = {'name': name, 'mesh': meshes_by[kind], 'translation': [round(v, 6) for v in xyz],
            'scale': [round(v, 6) for v in scale]}
    if rotation: node['rotation'] = rotation
    nodes.append(node); children.append(len(nodes) - 1)


x_axis_roll = [0, 0, math.sin(math.pi / 4), math.cos(math.pi / 4)]  # cylinder Y -> X
z_axis_tilt = [math.sin(math.pi / 4), 0, 0, math.cos(math.pi / 4)]  # cylinder Y -> Z
HOUSING_DEPTH = .045
HOUSING_BACK = REAR_SURFACE_Z - HOUSING_DEPTH  # -0.077

# Tapered rear housing: real depth behind the panel, two stacked chamfered slabs.
part('rear-housing-main', 'housing', [0, .03, REAR_SURFACE_Z - HOUSING_DEPTH / 2 - .0005], [.72, .60, HOUSING_DEPTH])
part('rear-housing-cap', 'housing', [0, .04, HOUSING_BACK - .006], [.54, .44, .014])
part('rear-housing-trim-top', 'trim', [0, .332, REAR_SURFACE_Z - .022], [.70, .008, .036])
part('rear-housing-trim-bottom', 'trim', [0, -.272, REAR_SURFACE_Z - .022], [.70, .008, .036])

# Cooling: dark vent field with brushed louvre fins in front of it.
cap_back = HOUSING_BACK - .013
part('rear-vent-field', 'vent', [0, .18, cap_back - .0006], [.40, .15, .0012])
for i in range(8):
    part(f'rear-fin-{i}', 'fin', [0, .118 + i * .018, cap_back - .0035], [.38, .0055, .006])
for side in (-1, 1):
    part(f'rear-side-vent-{side}', 'vent', [side * .3, .03, HOUSING_BACK - .0006], [.05, .34, .0012])
    for i in range(7):
        part(f'rear-side-fin-{side}-{i}', 'fin', [side * .3, -.11 + i * .046, HOUSING_BACK - .0032],
             [.046, .006, .005])

# VESA 100 mount with four screws, hinge and arm down to the existing stand column (z -0.08, top y -0.49).
part('rear-vesa-plate', 'trim', [0, -.07, cap_back - .006], [.16, .16, .012])
for sx in (-1, 1):
    for sy in (-1, 1):
        part(f'rear-vesa-screw-{sx}-{sy}', 'screw', [sx * .05, -.07 + sy * .05, cap_back - .0135],
             [.016, .004, .016], z_axis_tilt)
part('rear-hinge', 'hinge', [0, -.12, cap_back - .022], [.05, .11, .05], x_axis_roll)
part('rear-stand-arm', 'arm', [0, -.305, -.088], [.058, .37, .03])

# I/O panel on the flat lower rear, left of the arm: USB-C ×3, HDMI, power, status LED, cable.
io_z = REAR_SURFACE_Z - .003
part('rear-io-panel', 'port_panel', [-.22, -.39, io_z], [.22, .062, .006])
for i in range(3):
    part(f'rear-port-usbc-{i}', 'port', [-.30 + i * .03, -.39, io_z - .0035], [.021, .0075, .0035])
part('rear-port-hdmi', 'port', [-.19, -.39, io_z - .0035], [.034, .012, .0035])
part('rear-port-power', 'screw', [-.14, -.39, io_z - .004], [.016, .005, .016], z_axis_tilt)
part('rear-status-led', 'led', [-.115, -.39, io_z - .0035], [.012, .004, .002])
part('rear-cable-hdmi', 'cable', [-.19, -.455, io_z - .012], [.011, .13, .011])
part('rear-cable-power', 'cable', [-.14, -.455, io_z - .012], [.011, .13, .011])

# Accent light strips framing the housing (emissive, kept thin).
part('rear-violet-strip', 'strip', [0, .318, HOUSING_BACK - .0008], [.62, .005, .0016])
part('rear-cyan-strip', 'cyan_strip', [0, -.258, HOUSING_BACK - .0008], [.62, .004, .0016])

nodes.append({'name': 'monitor-rear-details', 'children': children,
              'extras': {'attach': 'cockpit-monitor', 'rear_surface_z': REAR_SURFACE_Z,
                         'provenance': 'Owned procedural rear housing; build_monitor_rear.py'}})
root = {'asset': {'version': '2.0', 'generator': 'Continuum owned procedural monitor rear 20261113'},
        'scene': 0, 'scenes': [{'nodes': [len(nodes) - 1]}], 'nodes': nodes, 'meshes': meshes,
        'materials': materials, 'extensionsUsed': ['KHR_materials_clearcoat'],
        'buffers': [{'byteLength': len(blob)}], 'bufferViews': views, 'accessors': accessors}
js = json.dumps(root, separators=(',', ':')).encode(); js += b' ' * ((-len(js)) % 4)
blob += b'\0' * ((-len(blob)) % 4)
out = struct.pack('<III', 0x46546c67, 2, 12 + 8 + len(js) + 8 + len(blob)) + \
    struct.pack('<II', len(js), 0x4e4f534a) + js + struct.pack('<II', len(blob), 0x004e4942) + blob
assert len(out) <= 64_000, 'Rear detail asset budget exceeded'
OUT.write_bytes(out)
PIN.write_text(json.dumps({'version': 1, 'monitorRear': {
    'path': 'cinema3d/monitor-rear-details.glb', 'sha256': hashlib.sha256(out).hexdigest(), 'bytes': len(out)},
    'generator_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}, indent=2) + '\n')
print(OUT.relative_to(ROOT), len(out), hashlib.sha256(out).hexdigest(), 'parts', len(children))
