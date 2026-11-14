"""Verify captured surface frames independently."""
from pathlib import Path
import hashlib
import json
import re
import struct
import sys
import zlib


def png_pixels(payload):
    """Decode Android's RGB/RGBA PNG output."""
    # https://www.w3.org/TR/png-3/#9Filters
    assert 32 < len(payload) <= 64 * 1024 * 1024
    assert payload[:8] == b'\x89PNG\r\n\x1a\n', 'Invalid PNG signature'
    offset, header, chunks, ended, data_ended = 8, None, [], False, False
    while offset < len(payload):
        assert offset + 12 <= len(payload), 'Truncated PNG chunk'
        size, kind = struct.unpack('>I4s', payload[offset:offset + 8])
        end = offset + 12 + size
        assert end <= len(payload), 'Truncated PNG payload'
        data = payload[offset + 8:end - 4]
        assert zlib.crc32(kind + data) == struct.unpack('>I', payload[end - 4:end])[0], 'PNG CRC mismatch'
        assert header is not None or kind == b'IHDR', 'IHDR must be first'
        if kind == b'IHDR':
            assert offset == 8 and size == 13, 'Invalid IHDR'
            header = struct.unpack('>IIBBBBB', data)
        elif kind == b'IDAT':
            assert not data_ended, 'Nonconsecutive IDAT'
            chunks.append(data)
        elif kind == b'IEND':
            assert size == 0 and end == len(payload) and chunks, 'Invalid IEND'
            ended = True
        else:
            assert kind not in (b'tRNS', b'acTL', b'fcTL', b'fdAT'), 'Unsupported transparency or animation'
            assert kind[0] & 32, 'Unsupported critical PNG chunk'
            if chunks:
                data_ended = True
        offset = end
    assert ended and header is not None, 'Incomplete PNG'
    width, height, depth, color, compression, filtering, interlace = header
    assert 0 < width * height <= 16_777_216 and width > 0 and height > 0
    assert depth == 8 and color in (2, 6), 'Expected 8-bit RGB/RGBA'
    assert (compression, filtering, interlace) == (0, 0, 0), 'Unsupported PNG encoding'
    channels = 3 if color == 2 else 4
    stride = width * channels
    expected = height * (stride + 1)
    inflater = zlib.decompressobj()
    raw = inflater.decompress(b''.join(chunks), expected + 1)
    assert len(raw) == expected and inflater.eof and not inflater.unused_data and not inflater.unconsumed_tail, 'Invalid PNG stream'
    previous = bytearray(stride)
    nontransparent = colored = visible_colored = 0
    for y in range(height):
        start = y * (stride + 1)
        mode = raw[start]
        assert 0 <= mode <= 4, 'Invalid PNG filter'
        row = bytearray(raw[start + 1:start + stride + 1])
        for i in range(stride):
            left = row[i - channels] if i >= channels else 0
            above = previous[i]
            diagonal = previous[i - channels] if i >= channels else 0
            if mode == 1:
                row[i] = (row[i] + left) & 255
            elif mode == 2:
                row[i] = (row[i] + above) & 255
            elif mode == 3:
                row[i] = (row[i] + (left + above) // 2) & 255
            elif mode == 4:
                projected = left + above - diagonal
                distances = (abs(projected - left), abs(projected - above), abs(projected - diagonal))
                predicted = (left, above, diagonal)[distances.index(min(distances))]
                row[i] = (row[i] + predicted) & 255
        for i in range(0, stride, channels):
            opaque = channels == 3 or row[i + 3] != 0
            rgb = any(row[i:i + 3])
            nontransparent += opaque
            colored += rgb
            visible_colored += opaque and rgb
        previous = row
    return {'width': width, 'height': height, 'nontransparent_pixels': nontransparent,
            'nonblack_rgb_pixels': colored, 'visible_rgb_pixels': visible_colored}


def bound_image(directory, metadata, name):
    assert metadata['file'] == name
    payload = (directory / name).read_bytes()
    assert len(payload) == metadata['bytes']
    assert hashlib.sha256(payload).hexdigest() == metadata['sha256'], f'Image hash mismatch: {name}'
    actual = png_pixels(payload)
    assert all(actual[key] == metadata[key] for key in ('width', 'height'))
    for key in ('nontransparent_pixels', 'nonblack_rgb_pixels'):
        if metadata.get(key) is not None:
            assert actual[key] == metadata[key], f'Pixel count mismatch: {name}/{key}'
    return actual


def visible(view):
    return (isinstance(view, dict)
            and all(view.get(key) is True for key in ('attached', 'shown', 'intersects_display'))
            and view.get('visibility') == 0 and view.get('window_visibility') == 0
            and view.get('alpha', 0) > 0 and view.get('scale_x', 0) > 0 and view.get('scale_y', 0) > 0
            and view.get('width', 0) > 0 and view.get('height', 0) > 0)


def verify_resize(report, baseline):
    proof = report['resize_regression']
    assert proof['status'] == 'PASSED' and not proof.get('restore_error'), 'Resize did not complete'
    assert all(proof['unchanged'][key] is True for key in
               ('same_dimensions_preserved', 'monitor_pose_preserved'))
    before = proof['before']
    stable = ('native_renderer_identity', 'engine_identity', 'asset_identity', 'scene_identity',
              'renderer_identity', 'cockpitStage_identity', 'surface_identity', 'surface_texture_identity')
    for key in stable:
        assert type(before[key]) is int and before[key] != 0
        assert before[key] == baseline[key], 'Resize uses a different renderer or surface'
    sizes = lambda state: (state['worldBufferWidth'], state['worldBufferHeight'])
    texture_size = lambda state: (state['texture']['width'], state['texture']['height'])
    previous = before
    times = [baseline['elapsed_nanos'], proof['started_elapsed_nanos'], before['elapsed_nanos']]
    for group, changed_key, rendered_key in (
            ('stage', 'changed', 'rendered'), ('stage', 'restored', 'restored_rendered'),
            ('texture_layout', 'changed', 'rendered'), ('texture_layout', 'restored', 'restored_rendered')):
        changed, rendered = proof[group][changed_key], proof[group][rendered_key]
        for state in (changed, rendered):
            assert all(state[key] == before[key] for key in stable), 'Resize replaced renderer or surface'
            assert state['hologram']['identity'] == before['hologram']['identity']
            assert state['texture']['identity'] == before['texture']['identity']
            assert state['texture_is_texture_view'] is True and state['main_thread'] == 'main'
            assert state['surface_valid'] is True and state['renderer_present'] is True
            assert all(state[key] is False for key in ('native3dFailed', 'cockpitWorldFailed', 'assetLoading'))
            assert all(type(value) is int and value > 0 for value in sizes(state))
        assert changed['swapChain_identity'] != previous['swapChain_identity'], 'Resize retained old SwapChain'
        assert type(changed['swapChain_identity']) is int and changed['swapChain_identity'] != 0
        assert rendered['swapChain_identity'] == changed['swapChain_identity'], 'Unstable resized SwapChain'
        assert changed['consumer_generation'] > previous['consumer_generation'], 'Stale resize generation'
        assert rendered['consumer_generation'] == changed['consumer_generation']
        assert changed['native3dDrawn'] is False and changed['texture']['alpha'] == 0
        assert rendered['native3dDrawn'] is True and visible(rendered['texture'])
        assert rendered['surface_texture_timestamp'] > changed['surface_texture_timestamp']
        assert changed['surface_texture_timestamp'] >= previous['surface_texture_timestamp']
        assert sizes(rendered) == sizes(changed) == (rendered['viewport_width'], rendered['viewport_height'])
        if group == 'stage' and changed_key == 'changed':
            assert sizes(changed) != sizes(before), 'Stage buffer did not change'
        else:
            assert sizes(changed) == sizes(before), 'Stage buffer was not restored'
        expected_texture = ((texture_size(before)[0] - 1, texture_size(before)[1])
                            if group == 'texture_layout' and changed_key == 'changed' else texture_size(before))
        assert texture_size(changed) == texture_size(rendered) == expected_texture
        times.extend((changed['elapsed_nanos'], rendered['elapsed_nanos']))
        previous = rendered
    times.extend((proof['finished_elapsed_nanos'], report['finished_elapsed_nanos']))
    assert all(type(value) is int and value > 0 for value in times) and times == sorted(times), 'Resize time sequence mismatch'


def verify_hologram(directory, source_sha, source_tree, expected_apks=None):
    directory = Path(directory)
    assert re.fullmatch('[0-9a-f]{40}', source_sha) and re.fullmatch('[0-9a-f]{40}', source_tree)
    assert (directory / 'source-sha.txt').read_text().strip() == source_sha
    assert (directory / 'source-tree.txt').read_text().strip() == source_tree
    pairs = re.findall(r'^([0-9a-f]{64})\s+(\S+)\s*$', (directory / 'apk-sha256.txt').read_text(), re.M)
    apks = {Path(path).name: digest for digest, path in pairs}
    assert len(pairs) == len(apks) == 2 and set(apks) == {'app-debug.apk', 'app-debug-androidTest.apk'}
    if expected_apks is not None:
        assert apks == expected_apks, 'Hologram APK mismatch'
    raw = (directory / 'native-hologram.txt').read_text()
    assert re.findall(r'^INSTRUMENTATION_CODE: (.*?)\r?$', raw, re.M) == ['-1']
    assert 'INSTRUMENTATION_FAILED' not in raw
    fields = re.findall(r'^INSTRUMENTATION_RESULT: ([a-z][a-z0-9_]*)=(.*?)\r?$', raw, re.M)
    assert len(fields) == len(dict(fields)), 'Duplicate instrumentation result'
    values = dict(fields)
    assert values['status'] == 'ok' and values['outcome'] == 'DIAGNOSTIC_CAPTURED'
    assert values['report_path'] == 'cache/code59-hologram-diagnostic/hologram-state.json'
    payload = (directory / 'hologram-state.json').read_bytes()
    assert hashlib.sha256(payload).hexdigest() == values['report_sha256'], 'Report hash mismatch'
    report = json.loads(payload)
    assert report['schema'] == 1 and report['scope'] == 'EXISTING_HOME_STAGE_DIAGNOSTIC_ONLY'
    assert report['outcome'] == 'DIAGNOSTIC_CAPTURED' and not report.get('error')
    assert report['package'] == 'at.heinz.deviceai.mentorpaired'
    assert report['sdk'] == 35 and report['hardware'] in ('ranchu', 'goldfish')
    assert report['pid'] == int(values['pid']) and report['pid'] > 0
    assert report['loading_observation_complete'] is True
    samples = report['samples']
    assert 2 <= len(samples) <= 100
    assert all(s['hologram']['class'] == 'at.heinz.deviceai.ContinuumHologramView' for s in samples)
    assert len({s['hologram']['identity'] for s in samples}) == 1
    assert all(s['main_thread'] == 'main' for s in samples)
    assert all(a['elapsed_nanos'] <= b['elapsed_nanos'] for a, b in zip(samples, samples[1:]))
    first_index, second_index = report['comparison_first_sample'], report['comparison_second_sample']
    assert type(first_index) is int and type(second_index) is int and 0 <= first_index < second_index < len(samples)
    first, second = samples[first_index], samples[second_index]
    assert second['elapsed_nanos'] - first['elapsed_nanos'] >= 1_600_000_000
    assert report['started_elapsed_nanos'] <= samples[0]['elapsed_nanos'] <= samples[-1]['elapsed_nanos'] <= report['finished_elapsed_nanos']
    observed = samples[first_index:]
    for key in ('surface_identity', 'surface_texture_identity'):
        assert first[key] != 0 and all(sample[key] == first[key] for sample in observed), f'Replaced {key}'
    assert len({sample['texture']['identity'] for sample in observed}) == 1, 'Replaced TextureView'
    assert 0 < first['surface_texture_timestamp'] < second['surface_texture_timestamp'], 'No consumed frame progression'
    assert all(a['surface_texture_timestamp'] <= b['surface_texture_timestamp'] for a, b in zip(observed, observed[1:]))
    for sample in observed:
        assert visible(sample['hologram']) and visible(sample['texture']), 'Stage is not visible'
        texture_type = sample.get('texture_is_texture_view',
                                  sample['texture']['class'] == 'android.view.TextureView')
        assert texture_type is True, 'View is not a verified TextureView'
        assert sample['ancestors'] and all(visible(parent) for parent in sample['ancestors'])
        assert all(sample[key] is True for key in ('texture_available', 'texture_parent_is_hologram',
                   'surface_valid', 'renderer_present', 'cockpit_ready', 'native3dDrawn',
                   'headEnabled', 'head3dEnabled', 'foregroundActive', 'motionAllowed', 'animationsEnabled'))
        assert all(sample[key] is False for key in ('native3dFailed', 'cockpitWorldFailed', 'assetLoading'))
        assert sample['viewport_width'] > 0 and sample['viewport_height'] > 0 and sample['scene_entities'] > 0
    screen = bound_image(directory, report['screen'], 'hologram-screen.png')
    texture = bound_image(directory, report['texture'], 'hologram-texture.png')
    copy = report['pixel_copy']
    assert copy.get('status') == 0 and not copy.get('error'), 'PixelCopy did not succeed'
    assert copy['source_identity'] == first['surface_identity'], 'PixelCopy source mismatch'
    copied = bound_image(directory, copy['image'], 'hologram-surface-copy.png')
    assert all(image['visible_rgb_pixels'] > 0 for image in (screen, texture, copied)), 'Empty visible pixel output'
    assert (texture['width'], texture['height']) == (first['texture']['width'], first['texture']['height'])
    times = [report['started_elapsed_nanos'], second['elapsed_nanos'],
             report['screen']['started_elapsed_nanos'], report['screen']['finished_elapsed_nanos'],
             report['texture_readback']['queued_elapsed_nanos'], report['texture_readback']['returned_elapsed_nanos'],
             copy['started_elapsed_nanos'], copy['finished_elapsed_nanos'], report['finished_elapsed_nanos']]
    assert all(type(value) is int and value > 0 for value in times) and times == sorted(times), 'Capture sequence mismatch'
    assert report['texture_readback']['thread'] == 'main'
    assert all(action['button'] in ('SCHLIESSEN', 'NEIN') for action in report['actions'])
    verify_resize(report, observed[-1])
    return {'status': 'SURFACE_FRAMES_VERIFIED', 'source_sha': source_sha, 'source_tree': source_tree,
            'apk_sha256': apks, 'texture_visible_rgb_pixels': texture['visible_rgb_pixels'],
            'copied_visible_rgb_pixels': copied['visible_rgb_pixels'],
            'texture_size': [texture['width'], texture['height']],
            'copied_surface_size': [copied['width'], copied['height']],
            'renderer_viewport': [first['viewport_width'], first['viewport_height']],
            'resize': 'SWAPCHAIN_RESIZE_VERIFIED',
            'avatar_content': 'REQUIRES_IMAGE_REVIEW'}


if __name__ == '__main__':
    assert len(sys.argv) == 4, 'Expected evidence directory, source SHA, source tree'
    print(json.dumps(verify_hologram(*sys.argv[1:]), sort_keys=True))
