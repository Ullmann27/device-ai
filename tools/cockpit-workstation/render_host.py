"""Render exact assets; never device evidence."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Matrix, Vector


def point(x, y, z):
    return Vector((x, -z, y))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def setup(width, height, samples):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = samples
    scene.cycles.seed = 731
    scene.cycles.use_denoising = False
    scene.render.threads_mode = 'FIXED'
    scene.render.threads = 2
    scene.render.resolution_x, scene.render.resolution_y = width, height
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.film_transparent = True
    scene.world = bpy.data.worlds.new('HostReviewAmbient')
    scene.world.use_nodes = True
    background = scene.world.node_tree.nodes['Background']
    background.inputs[0].default_value = (.20, .28, .40, 1)
    background.inputs[1].default_value = .35
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Medium High Contrast'
    scene.view_settings.exposure = 0
    return scene


def light(name, position, energy, color, kind='SUN', target=(0, 0, 0)):
    data = bpy.data.lights.new(name, kind)
    data.energy, data.color = energy, color
    obj = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = point(*position)
    obj.rotation_euler = (point(*target) - obj.location).to_track_quat('-Z', 'Y').to_euler()
    return obj


def camera(scene, position, target):
    data = bpy.data.cameras.new('HostReviewCamera')
    obj = bpy.data.objects.new('HostReviewCamera', data)
    scene.collection.objects.link(obj)
    obj.location = point(*position)
    obj.rotation_euler = (point(*target) - obj.location).to_track_quat('-Z', 'Y').to_euler()
    scene.camera = obj
    return obj


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--samples', type=int, default=12)
    parser.add_argument('--frames', default='all')
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:])
    if not 1 <= args.samples <= 64:
        raise ValueError('Samples outside review bounds')
    root, out = args.repo_root.resolve(), args.work_dir.resolve()
    asset = root / 'app/src/main/assets/cinema3d/cockpit-workstation.glb'
    avatar = root / 'app/src/main/assets/avatar3d/continuum-avatar.glb'
    exported = json.loads((out / 'scene-export.json').read_text())
    if exported['schema'] != 'cockpit-host-review-v1':
        raise ValueError('Unsupported export schema')
    frames = exported['frames']
    if args.frames != 'all':
        selected = set(args.frames.split(','))
        frames = [frame for frame in frames if frame['key'] in selected]
        if {frame['key'] for frame in frames} != selected:
            raise ValueError('Unknown requested frame')
    conversion = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
    records = []
    for name in sorted({frame['layout'] for frame in frames}):
        layout = json.loads((out / f'{name}-layout.json').read_text())
        group = [frame for frame in frames if frame['layout'] == name]
        width, height = [int(group[0]['frame'][key]) for key in ('width', 'height')]
        scene = setup(width, height, args.samples)
        bpy.ops.import_scene.gltf(filepath=str(asset))
        room = bpy.data.objects['cockpit-room']
        monitor = bpy.data.objects['cockpit-monitor']
        room_rest, monitor_rest = room.matrix_world.copy(), monitor.matrix_world.copy()
        room_matrix = Matrix.Diagonal((width, height, min(width, height), 1))
        room.matrix_world = conversion @ room_matrix @ conversion.inverted() @ room_rest
        distance = group[0]['frame']['distance']
        cam = camera(scene, (0, 0, distance), (0, 0, 0))
        cam.data.sensor_fit = 'HORIZONTAL'
        cam.data.sensor_width = 36
        cam.data.lens = 36 * distance / width
        cam.data.clip_start, cam.data.clip_end = 1, distance * 4
        light('HostKey', (.32, .55, 1), 4.5, (.62, .80, 1))
        for item in group:
            f = item['frame']
            mw, mh = f['monitorRight'] - f['monitorLeft'], f['monitorBottom'] - f['monitorTop']
            cx = (f['monitorLeft'] + f['monitorRight'] - width) / 2
            cy = (height - f['monitorTop'] - f['monitorBottom']) / 2
            model = (Matrix.Translation((cx, cy, f['depthOffset']))
                     @ Matrix.Rotation(math.radians(f['yawDegrees']), 4, 'Y')
                     @ Matrix.Rotation(math.radians(f['pitchDegrees']), 4, 'X')
                     @ Matrix.Diagonal((mw, mh, min(mw, mh), 1)))
            monitor.matrix_world = conversion @ model @ conversion.inverted() @ monitor_rest
            bpy.context.view_layer.update()
            projected = []
            for u, v in ((.035, .12), (.965, .12), (.965, .945), (.035, .945)):
                world = conversion @ model @ Vector((u - .5, .5 - v, .032, 1))
                clip = world_to_camera_view(scene, cam, world.to_3d())
                projected.extend((clip.x * width, (1 - clip.y) * height))
            error = max(abs(a - b) for a, b in zip(projected, f['quad']))
            if error > .08:
                raise ValueError(f'Projection mismatch: {item["key"]} {error:.4f}px')
            file = out / f'{item["key"]}-stage.png'
            scene.render.filepath = str(file)
            bpy.ops.render.render(write_still=True)
            record = {'key': item['key'], 'image': file.name, 'imageSha256': sha(file),
                      'projectionMaxErrorPixels': error, 'projectedContentQuad': projected}
            records.append(record)
            print('COCKPIT_HOST_FRAME', json.dumps(record), flush=True)
        head = layout['head']
        hw, hh = int(head['right'] - head['left']), int(head['bottom'] - head['top'])
        scene = setup(hw, hh, args.samples)
        bpy.ops.import_scene.gltf(filepath=str(avatar))
        for obj in bpy.data.objects:
            if obj.name.startswith('Tooth'):
                obj.hide_render = True
        focus, win = layout['avatarFocus'], layout['avatarWindow']
        target = (focus['centerX'], win['cameraCenterY'], focus['centerZ'])
        azimuth = math.radians(focus['azimuthDegrees'])
        cam = camera(scene, (target[0] + math.sin(azimuth) * 2,
                            target[1], target[2] + math.cos(azimuth) * 2), target)
        cam.data.type = 'ORTHO'
        cam.data.ortho_scale = 2 * win['halfHeight'] * hw / hh
        cam.data.sensor_fit = 'HORIZONTAL'
        cam.data.shift_x = (win['left'] + win['right']) / (2 * (win['right'] - win['left']))
        light('HostAvatarKey', (.35, .40, 1), 3.5, (.50, .82, 1), target=target)
        light('HostAvatarViolet', (-.38, .32, .38), 4.0, (.55, .24, 1), kind='POINT', target=target)
        light('HostAvatarRim', (.32, .35, -.16), 3.0, (.18, .82, 1), kind='POINT', target=target)
        file = out / f'{name}-avatar.png'
        scene.render.filepath = str(file)
        bpy.ops.render.render(write_still=True)
        records.append({'key': f'{name}-avatar-neutral', 'image': file.name, 'imageSha256': sha(file)})
    (out / 'blender-manifest.json').write_text(json.dumps({
        'evidenceClass': 'HOST_ASSET_RENDER', 'renderer': f'Blender {bpy.app.version_string} Cycles CPU',
        'samples': args.samples, 'threads': 2, 'denoising': False, 'seed': 731,
        'assets': {'cockpit': sha(asset), 'avatar': sha(avatar)},
        'lighting': 'Fixed host inspection studio; not Filament photometric equivalence',
        'avatarPose': 'Original GLB neutral rest pose; runtime-hidden tooth nodes excluded',
        'frames': records, 'Filament': 'NOT_RUN', 'Fold7': 'NOT_RUN',
        'deviceFps': 'NOT_MEASURED', 'deviceThermal': 'NOT_MEASURED',
    }, indent=2) + '\n')


if __name__ == '__main__':
    main()
