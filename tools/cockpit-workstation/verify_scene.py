"""Validate the bounded cockpit seed."""
import hashlib
import json
import math
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT / 'tools/cockpit-workstation/source/pr801-workstation.glb'
METADATA = ASSET.with_suffix('.json')


def intersection_area(triangle, rectangle):
    polygon = list(triangle)
    for axis, limit, greater in [(0, rectangle[0], True), (1, rectangle[1], True),
                                 (0, rectangle[2], False), (1, rectangle[3], False)]:
        clipped = []
        for index, current in enumerate(polygon):
            previous = polygon[index - 1]
            inside = (current[axis] >= limit) if greater else (current[axis] <= limit)
            was_inside = (previous[axis] >= limit) if greater else (previous[axis] <= limit)
            if inside != was_inside:
                fraction = (limit - previous[axis]) / (current[axis] - previous[axis])
                clipped.append(tuple(previous[k] + fraction * (current[k] - previous[k]) for k in range(2)))
            if inside:
                clipped.append(current)
        polygon = clipped
    return abs(sum(p[0] * polygon[(i + 1) % len(polygon)][1] -
                   p[1] * polygon[(i + 1) % len(polygon)][0]
                   for i, p in enumerate(polygon))) / 2


def verify():
    payload = ASSET.read_bytes()
    assert len(payload) <= 32768, 'Asset exceeds runtime budget'
    assert struct.unpack_from('<III', payload) == (0x46546C67, 2, len(payload)), 'Invalid GLB header'
    json_length, json_type = struct.unpack_from('<II', payload, 12)
    assert json_type == 0x4E4F534A and json_length % 4 == 0
    document = json.loads(payload[20:20 + json_length])
    binary_length, binary_type = struct.unpack_from('<II', payload, 20 + json_length)
    binary = payload[28 + json_length:]
    assert binary_type == 0x004E4942 and binary_length == len(binary) and binary_length % 4 == 0
    assert len(document['buffers']) == 1 and 'uri' not in document['buffers'][0]
    assert document['buffers'][0]['byteLength'] <= len(binary)
    assert not document.get('images') and not document.get('textures')
    names = [node['name'] for node in document['nodes']]
    assert len(names) == len(set(names)), 'Ambiguous transform anchors'
    anchors = {document['nodes'][index]['name'] for index in document['scenes'][0]['nodes']}
    assert anchors == {'cockpit-room', 'cockpit-monitor', 'cockpit-side-monitors'}
    assert not any(name.startswith(('arch-', 'wing-', 'glass-fin-')) for name in names)

    def accessor(index):
        entry = document['accessors'][index]
        view = document['bufferViews'][entry['bufferView']]
        components = {'SCALAR': 1, 'VEC3': 3}[entry['type']]
        kind, size = {5123: ('H', 2), 5126: ('f', 4)}[entry['componentType']]
        count = entry['count'] * components
        start = view.get('byteOffset', 0) + entry.get('byteOffset', 0)
        assert start + count * size <= view.get('byteOffset', 0) + view['byteLength'] <= len(binary)
        flat = struct.unpack_from('<' + kind * count, binary, start)
        values = [flat[i:i + components] for i in range(0, count, components)]
        assert all(math.isfinite(value) for row in values for value in row)
        if components == 3:
            for axis in range(3):
                assert abs(min(row[axis] for row in values) - entry['min'][axis]) < 1e-6
                assert abs(max(row[axis] for row in values) - entry['max'][axis]) < 1e-6
        return values

    openings = {
        'monitor-metal-bezel': (-.465, -.445, .465, .38),
        'monitor-glass-bezel': (-.465, -.445, .465, .38),
        'monitor-light-rim': (-.465, -.445, .465, .38),
        'side-metal-bezel': (-.5, -.5, .5, .5),
        'side-glass-bezel': (-.5, -.5, .5, .5),
        'side-light-rim': (-.5, -.5, .5, .5),
    }
    for mesh in document['meshes']:
        for primitive in mesh['primitives']:
            positions = accessor(primitive['attributes']['POSITION'])
            normals = accessor(primitive['attributes']['NORMAL'])
            indices = [value[0] for value in accessor(primitive['indices'])]
            assert len(positions) == len(normals) and len(indices) % 3 == 0
            assert all(0 <= index < len(positions) for index in indices)
            assert all(abs(sum(v * v for v in normal) - 1) < 1e-5 for normal in normals)
            for offset in range(0, len(indices), 3):
                triangle = indices[offset:offset + 3]
                a, b, c = [positions[index] for index in triangle]
                u = [b[k] - a[k] for k in range(3)]
                v = [c[k] - a[k] for k in range(3)]
                cross = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
                assert sum(value * value for value in cross) > 1e-18, (mesh['name'], 'degenerate')
                assert all(sum(cross[k] * normals[index][k] for k in range(3)) > 0 for index in triangle), (mesh['name'], 'winding')
                if mesh['name'] in openings:
                    assert intersection_area([a[:2], b[:2], c[:2]], openings[mesh['name']]) < 1e-10, (mesh['name'], 'content obstruction')
    digest = hashlib.sha256(payload).hexdigest()
    metadata = json.loads(METADATA.read_text())
    assert metadata['sha256'] == digest and metadata['bytes'] == len(payload), 'Seed pin mismatch'
    assert metadata['source_path'] == str(ASSET.relative_to(ROOT))
    assert hashlib.sha256((ROOT / metadata['generator_path']).read_bytes()).hexdigest() == metadata['generator_sha256']
    renderables = [node for node in document['nodes'] if 'mesh' in node]
    primitives = [primitive for node in renderables for primitive in document['meshes'][node['mesh']]['primitives']]
    print(json.dumps({'result': 'PASS', 'bytes': len(payload), 'sha256': digest,
                      'renderables': len(renderables), 'draw_primitives': len(primitives),
                      'instanced_triangles': sum(document['accessors'][p['indices']]['count'] // 3 for p in primitives),
                      'checks': ['binary/accessors', 'finite/unit normals', 'outward winding',
                                 'native openings', 'root contract', 'budget/pin']}, indent=2))


if __name__ == '__main__':
    verify()
