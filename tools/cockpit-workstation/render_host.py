"""Render the actual unified scene."""
import argparse
from array import array
import hashlib
import json
import math
from pathlib import Path
import sys

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Matrix, Vector


CONVERSION = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def matrix(values):
    return Matrix(tuple(tuple(values[column * 4 + row] for column in range(4)) for row in range(4)))


def converted(values):
    return CONVERSION @ matrix(values) @ CONVERSION.inverted()


def point(x, y, z):
    return Vector((x, -z, y))


def studio_environment(world, directory, metadata):
    if metadata['format'] != 'RGBA32F_LE' or metadata['colorSpace'] != 'LINEAR_SRGB' or \
            metadata['rows'] != 'BOTTOM_TO_TOP' or metadata['projection'] != 'BLENDER_EQUIRECTANGULAR':
        raise ValueError('Unsupported studio environment encoding')
    if metadata['source'] != 'CockpitStudioEnvironment.radiance' or metadata['file'] != 'studio-environment.rgba32f':
        raise ValueError('Production radiance export required')
    width, height = metadata['width'], metadata['height']
    if width != 256 or height != 128:
        raise ValueError('Unexpected studio environment dimensions')
    path = directory / metadata['file']
    if sha(path) != metadata['sha256'] or path.stat().st_size != width * height * 16:
        raise ValueError('Studio environment pin failed')
    values = array('f')
    values.frombytes(path.read_bytes())
    if sys.byteorder != 'little':
        values.byteswap()
    if any(not math.isfinite(value) or value < 0 for value in values):
        raise ValueError('Invalid studio radiance')
    image = bpy.data.images.new('ProductionStudioRadiance', width=width, height=height,
                                alpha=True, float_buffer=True)
    image.colorspace_settings.name = 'Non-Color'
    image.pixels.foreach_set(values)
    image.update()
    nodes, links = world.node_tree.nodes, world.node_tree.links
    texture = nodes.new('ShaderNodeTexEnvironment')
    texture.image = image
    texture.projection = 'EQUIRECTANGULAR'
    texture.interpolation = 'Linear'
    reflected = nodes.new('ShaderNodeBackground')
    reflected.inputs['Strength'].default_value = nodes['Background'].inputs['Strength'].default_value
    ray = nodes.new('ShaderNodeLightPath')
    combined = nodes.new('ShaderNodeMixShader')
    links.new(texture.outputs['Color'], reflected.inputs['Color'])
    links.new(ray.outputs['Is Glossy Ray'], combined.inputs[0])
    links.new(nodes['Background'].outputs[0], combined.inputs[1])
    links.new(reflected.outputs[0], combined.inputs[2])
    links.new(combined.outputs[0], nodes['World Output'].inputs['Surface'])


def setup(width, height, samples, denoise, directory, environment):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = samples
    scene.cycles.seed = 731
    scene.cycles.use_denoising = denoise
    if denoise:
        scene.cycles.denoiser = 'OPENIMAGEDENOISE'
    scene.render.threads_mode = 'FIXED'
    scene.render.threads = 2
    scene.render.resolution_x, scene.render.resolution_y = width, height
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.film_transparent = True
    scene.world = bpy.data.worlds.new('HostAmbient')
    scene.world.use_nodes = True
    background = scene.world.node_tree.nodes['Background']
    background.inputs[0].default_value = (.90, .98, 1.10, 1)
    background.inputs[1].default_value = .08
    studio_environment(scene.world, directory, environment)
    scene.view_settings.view_transform = 'Standard'
    scene.view_settings.look = 'None'
    scene.view_settings.exposure = 0
    return scene


def light(name, position, energy, color, kind='SUN'):
    data = bpy.data.lights.new(name, kind)
    data.energy, data.color = energy, color
    obj = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = point(*position)
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()


def import_avatar(path):
    previous = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(path))
    imported = set(bpy.data.objects) - previous
    roots = [obj for obj in imported if obj.parent not in imported]
    parent = bpy.data.objects.new('HostAvatarWorld', None)
    bpy.context.scene.collection.objects.link(parent)
    for obj in roots:
        rest = obj.matrix_world.copy()
        obj.parent = parent
        obj.matrix_parent_inverse = Matrix.Identity(4)
        obj.matrix_local = rest
    for obj in imported:
        if obj.name.startswith('Tooth'):
            obj.hide_render = True
    return parent


def pixel_material(obj):
    material = bpy.data.materials.new('HostExampleUnlit')
    material.use_nodes = True
    nodes = material.node_tree.nodes
    nodes.clear()
    output = nodes.new('ShaderNodeOutputMaterial')
    emission = nodes.new('ShaderNodeEmission')
    emission.inputs['Color'].default_value = (.001, .002, .004, 1)
    emission.inputs['Strength'].default_value = 1
    transparent = nodes.new('ShaderNodeBsdfTransparent')
    path = nodes.new('ShaderNodeLightPath')
    mix = nodes.new('ShaderNodeMixShader')
    texture = nodes.new('ShaderNodeTexImage')
    texture.interpolation = 'Linear'
    texture.extension = 'CLIP'
    links = material.node_tree.links
    links.new(path.outputs['Is Camera Ray'], mix.inputs[0])
    links.new(transparent.outputs[0], mix.inputs[1])
    links.new(emission.outputs[0], mix.inputs[2])
    links.new(mix.outputs[0], output.inputs['Surface'])
    obj.data.materials.clear()
    obj.data.materials.append(material)
    return texture, emission, links


def pixel_corners(obj):
    corners = {}
    uv = obj.data.uv_layers.active
    if uv is None:
        raise ValueError('Live pixel mesh has no UVs')
    for loop in obj.data.loops:
        key = tuple(round(value, 5) for value in uv.data[loop.index].uv)
        corners[key] = obj.data.vertices[loop.vertex_index].co.copy()
    ordered = ((0, 1), (1, 1), (1, 0), (0, 0))
    if any(key not in corners for key in ordered):
        raise ValueError('Live pixel UV contract changed')
    return [corners[key] for key in ordered]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--samples', type=int, default=8)
    parser.add_argument('--video-samples', type=int)
    parser.add_argument('--denoise', action='store_true')
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:])
    root, out = args.repo_root.resolve(), args.work_dir.resolve()
    if not 1 <= args.samples <= 64 or args.video_samples is not None and not 1 <= args.video_samples <= 64:
        raise ValueError('Samples outside review bounds')
    assets = root / 'app/src/main/assets'
    workstation = assets / 'cinema3d/cockpit-workstation.glb'
    orb_asset = assets / 'cinema3d/continuum-orb.glb'
    avatar = out / 'avatar-runtime.glb'
    exported = json.loads((out / 'scene-export.json').read_text())
    if exported['schema'] != 'cockpit-unified-review-v2' or exported['orbRingCount'] != 5:
        raise ValueError('Unified scene export required')
    if sha(avatar) != exported['avatarRuntimeVariantSha256']:
        raise ValueError('Runtime avatar differs from Kotlin export')
    frames = exported['frames']
    records = []
    for name in sorted({frame['layout'] for frame in frames}):
        layout = json.loads((out / f'{name}-layout.json').read_text())
        group = [frame for frame in frames if frame['layout'] == name]
        first = group[0]['frame']
        width, height = int(first['width']), int(first['height'])
        scene = setup(width, height, args.samples, args.denoise, out, exported['studioEnvironment'])
        bpy.ops.import_scene.gltf(filepath=str(workstation))
        room = bpy.data.objects['cockpit-room']
        monitor = bpy.data.objects['cockpit-monitor']
        sides = bpy.data.objects.get('cockpit-side-monitors')
        room_rest, monitor_rest = room.matrix_world.copy(), monitor.matrix_world.copy()
        side_rest = sides.matrix_world.copy() if sides else None
        pixels = bpy.data.objects['monitor-live-pixels']
        local_pixels = pixel_corners(pixels)
        texture, emission, links = pixel_material(pixels)
        if exported['contentMode'] == 'example':
            links.new(texture.outputs['Color'], emission.inputs['Color'])
        bpy.ops.import_scene.gltf(filepath=str(orb_asset))
        orb = bpy.data.objects['continuum-orb']
        orb_rest = orb.matrix_world.copy()
        expected_rings = {f'orb-orbit-{i}' for i in range(exported['orbRingCount'])}
        actual_rings = {obj.name for obj in bpy.data.objects if obj.name.startswith('orb-orbit-')}
        if actual_rings != expected_rings:
            raise ValueError('Packaged original five-ring nodes differ from Kotlin export')
        rings = [(bpy.data.objects[f'orb-orbit-{i}'],
                  bpy.data.objects[f'orb-orbit-{i}'].matrix_local.copy()) for i in range(exported['orbRingCount'])]
        avatar_root = import_avatar(avatar)
        data = bpy.data.cameras.new('UnifiedHostCamera')
        camera = bpy.data.objects.new('UnifiedHostCamera', data)
        scene.collection.objects.link(camera)
        scene.camera = camera
        distance = first['distance'] * first['worldScale']
        camera.location = point(0, 0, distance)
        camera.rotation_euler = (-camera.location).to_track_quat('-Z', 'Y').to_euler()
        data.sensor_fit, data.sensor_width = 'HORIZONTAL', 36
        data.lens = 36 * distance / (width * first['worldScale'])
        data.clip_start, data.clip_end = first['near'], first['far']
        light('HostKey', (.35, .40, 1), 3.5, (.50, .82, 1))
        light('HostViolet', (-.38, .32, .38), 4, (.55, .24, 1), 'POINT')
        light('HostCore', (.32, .35, -.16), 3, (.18, .82, 1), 'POINT')
        for item in group:
            f, world = item['frame'], item['world']
            scene.cycles.samples = (args.video_samples or args.samples) if item['phase'].startswith('sequence-') else args.samples
            room.matrix_world = converted(world['room']) @ room_rest
            monitor.matrix_world = converted(f['monitorMatrix']) @ monitor_rest
            if sides:
                sides.matrix_world = converted(world['room']) @ side_rest
                for obj in [sides] + list(sides.children_recursive):
                    obj.hide_render = f['monitorRight'] - f['monitorLeft'] > f['width'] * .70
            avatar_root.matrix_world = converted(world['avatar'])
            orb.matrix_world = converted(world['orb']) @ orb_rest
            if len(world['ringRotations']) != len(rings):
                raise ValueError('Missing production ring rotations')
            for (ring, rest), rotation in zip(rings, world['ringRotations']):
                ring.matrix_local = rest @ converted(rotation)
            if exported['contentMode'] == 'example':
                texture.image = bpy.data.images.load(str(out / f'{item["key"]}-example.png'), check_existing=True)
                texture.image.colorspace_settings.name = 'sRGB'
            bpy.context.view_layer.update()
            projected = []
            for vertex in local_pixels:
                clip = world_to_camera_view(scene, camera, pixels.matrix_world @ vertex)
                projected.extend((clip.x * width, (1 - clip.y) * height))
            error = max(abs(a - b) for a, b in zip(projected, f['quad']))
            if error > .08:
                raise ValueError(f'Actual mesh projection mismatch: {item["key"]} {error:.4f}px')
            file = out / f'{item["key"]}-stage.png'
            scene.render.filepath = str(file)
            bpy.ops.render.render(write_still=True)
            record = {'key': item['key'], 'image': file.name, 'imageSha256': sha(file),
                      'samples': scene.cycles.samples, 'projectionMaxErrorPixels': error,
                      'projectedContentQuad': projected, 'orbPhase': world['orbPhase'],
                      'ringPoses': world['ringPoses'], 'ringRotations': world['ringRotations'],
                      'denoising': scene.cycles.use_denoising}
            records.append(record)
            print('COCKPIT_UNIFIED_HOST_FRAME', json.dumps(record), flush=True)
    (out / 'blender-manifest.json').write_text(json.dumps({
        'evidenceClass': 'HOST_UNIFIED_3D_RENDER', 'renderer': f'Blender {bpy.app.version_string} Cycles CPU',
        'stillsSamples': args.samples, 'sequenceSamples': args.video_samples or args.samples,
        'threads': 2, 'denoising': args.denoise,
        'denoiser': 'OPENIMAGEDENOISE' if args.denoise else None, 'seed': 731,
        'assets': {'cockpit': sha(workstation), 'orb': sha(orb_asset), 'avatarRuntime': sha(avatar),
                   'avatarOriginal': sha(assets / 'avatar3d/continuum-avatar.glb')},
        'composition': 'All three GLBs share one scene and camera; five native Kotlin ring matrices',
        'projectionCheck': 'Actual packaged monitor-live-pixels vertices against Kotlin quad',
        'lighting': 'Runtime light colors and locations; host intensities are not Filament photometric equivalence',
        'studioEnvironment': exported['studioEnvironment'],
        'reflections': 'Compiled production radiance on glossy world rays; Cycles filtering, host strength 0.08',
        'diffuseEnvironment': 'Previous constant host ambient retained; no Filament irradiance equivalence',
        'colorManagement': 'Standard sRGB display, no look, exposure zero; sample pixels unlit',
        'avatarPose': 'Original neutral rest pose; runtime-hidden tooth nodes excluded; no 2D avatar mask',
        'frames': records, 'Filament': 'NOT_RUN', 'Fold7': 'NOT_RUN',
        'deviceFps': 'NOT_MEASURED', 'deviceThermal': 'NOT_MEASURED',
    }, indent=2) + '\n')


if __name__ == '__main__':
    main()
