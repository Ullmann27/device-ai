"""Synthetic evidence exercises validation only."""
from pathlib import Path
import copy
import hashlib
import json
import struct
import tempfile
import unittest
import zlib

from native_hologram_evidence import png_pixels, verify_hologram


def png(rows, width=2, height=2, color=6):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, color, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


class HologramFixture:
    sha, tree = 'a' * 40, 'b' * 40
    apks = {'app-debug.apk': 'c' * 64, 'app-debug-androidTest.apk': 'd' * 64}

    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        (self.root / 'source-sha.txt').write_text(self.sha + '\n')
        (self.root / 'source-tree.txt').write_text(self.tree + '\n')
        (self.root / 'apk-sha256.txt').write_text(''.join(f'{digest}  app/build/{name}\n' for name, digest in self.apks.items()))
        view = {'identity': 10, 'class': 'at.heinz.deviceai.ContinuumHologramView', 'attached': True,
                'shown': True, 'intersects_display': True, 'visibility': 0, 'window_visibility': 0,
                'alpha': 1, 'scale_x': 1, 'scale_y': 1, 'width': 2, 'height': 2}
        sample = {'main_thread': 'main', 'hologram': view,
                  'texture': {**view, 'class': 'android.view.TextureView', 'identity': 11},
                  'ancestors': [{**view, 'class': 'com.android.internal.policy.DecorView', 'identity': 12}],
                  'surface_identity': 13, 'surface_texture_identity': 14,
                  'native_renderer_identity': 15, 'engine_identity': 16, 'asset_identity': 17,
                  'scene_identity': 18, 'renderer_identity': 19, 'cockpitStage_identity': 20,
                  'swapChain_identity': 21, 'consumer_generation': 1,
                  'worldBufferWidth': 2, 'worldBufferHeight': 2,
                  'viewport_width': 2, 'viewport_height': 2, 'scene_entities': 2}
        sample.update(dict.fromkeys(('texture_available', 'texture_parent_is_hologram', 'surface_valid',
                      'renderer_present', 'cockpit_ready', 'native3dDrawn', 'headEnabled', 'head3dEnabled',
                      'foregroundActive', 'motionAllowed', 'animationsEnabled'), True))
        sample.update(dict.fromkeys(('native3dFailed', 'cockpitWorldFailed', 'assetLoading'), False))
        samples = []
        for index, elapsed in enumerate((1_000_000_000, 3_000_000_000, 3_700_000_000)):
            samples.append({**copy.deepcopy(sample), 'elapsed_nanos': elapsed,
                            'monotonic_nanos': elapsed - 500_000_000,
                            'surface_texture_timestamp': (index + 1) * 100})
        self.report = {'schema': 1, 'scope': 'EXISTING_HOME_STAGE_DIAGNOSTIC_ONLY',
            'outcome': 'DIAGNOSTIC_CAPTURED', 'package': 'at.heinz.deviceai.mentorpaired',
            'sdk': 35, 'hardware': 'ranchu', 'pid': 100, 'loading_observation_complete': True,
            'comparison_first_sample': 0, 'comparison_second_sample': 1, 'samples': samples,
            'started_elapsed_nanos': 900_000_000, 'finished_elapsed_nanos': 8_000_000_000,
            'actions': [{'button': 'NEIN'}], 'texture_readback': {'thread': 'main',
                'queued_elapsed_nanos': 3_300_000_000, 'returned_elapsed_nanos': 3_400_000_000},
            'pixel_copy': {'status': 0, 'source_identity': 13, 'started_elapsed_nanos': 3_500_000_000,
                           'finished_elapsed_nanos': 3_600_000_000}}
        for key, name in [('screen', 'hologram-screen.png'), ('texture', 'hologram-texture.png'),
                          ('image', 'hologram-surface-copy.png')]:
            metadata = self.image(name)
            if key == 'image':
                self.report['pixel_copy'][key] = metadata
            else:
                self.report[key] = metadata
        self.report['screen'].update(started_elapsed_nanos=3_100_000_000, finished_elapsed_nanos=3_200_000_000)
        before = copy.deepcopy(samples[-1])
        before.update(elapsed_nanos=4_100_000_000, monotonic_nanos=3_600_000_000,
                      texture_is_texture_view=True)
        proof = {'status': 'PASSED', 'started_elapsed_nanos': 4_000_000_000,
                 'finished_elapsed_nanos': 7_000_000_000, 'before': before,
                 'unchanged': {'same_dimensions_preserved': True, 'monitor_pose_preserved': True},
                 'stage': {}, 'texture_layout': {}}
        previous = before
        for index, (group, changed_key, rendered_key) in enumerate((
                ('stage', 'changed', 'rendered'), ('stage', 'restored', 'restored_rendered'),
                ('texture_layout', 'changed', 'rendered'), ('texture_layout', 'restored', 'restored_rendered'))):
            changed = copy.deepcopy(previous)
            changed.update(elapsed_nanos=4_300_000_000 + index * 600_000_000,
                           monotonic_nanos=3_800_000_000 + index * 600_000_000,
                           swapChain_identity=30 + index, consumer_generation=2 + index,
                           consumer_updates=0, native3dDrawn=False,
                           worldBufferWidth=1 if index == 0 else 2)
            changed['texture'].update(alpha=0, width=1 if index == 2 else 2)
            first = copy.deepcopy(changed)
            first.update(elapsed_nanos=changed['elapsed_nanos'] + 100_000_000,
                         monotonic_nanos=changed['monotonic_nanos'] + 100_000_000,
                         native3dDrawn=True, consumer_updates=1,
                         viewport_width=changed['worldBufferWidth'],
                         surface_texture_timestamp=changed['monotonic_nanos'] + 20_000_000)
            first['texture']['alpha'] = 1
            rendered = copy.deepcopy(changed)
            rendered.update(elapsed_nanos=changed['elapsed_nanos'] + 300_000_000,
                            monotonic_nanos=changed['monotonic_nanos'] + 300_000_000,
                            native3dDrawn=True, consumer_updates=2, viewport_width=changed['worldBufferWidth'],
                            surface_texture_timestamp=changed['monotonic_nanos'] + 200_000_000)
            rendered['texture']['alpha'] = 1
            proof[group][changed_key], proof[group][rendered_key] = changed, rendered
            proof[group][rendered_key + '_progress'] = [first, rendered]
            previous = rendered
        self.report['resize_regression'] = proof
        self.save()

    def image(self, name, pixels=None):
        pixels = pixels if pixels is not None else bytes([20, 80, 150, 255] * 4)
        payload = png(b'\0' + pixels[:8] + b'\0' + pixels[8:])
        (self.root / name).write_bytes(payload)
        return {'file': name, 'width': 2, 'height': 2, 'bytes': len(payload),
                'sha256': hashlib.sha256(payload).hexdigest(),
                'nontransparent_pixels': sum(pixels[i] > 0 for i in range(3, 16, 4)),
                'nonblack_rgb_pixels': sum(any(pixels[i:i + 3]) for i in range(0, 16, 4))}

    def save(self):
        payload = json.dumps(self.report).encode()
        (self.root / 'hologram-state.json').write_bytes(payload)
        values = {'status': 'ok', 'outcome': 'DIAGNOSTIC_CAPTURED', 'pid': '100',
            'report_path': 'cache/code59-hologram-diagnostic/hologram-state.json',
            'report_sha256': hashlib.sha256(payload).hexdigest()}
        (self.root / 'native-hologram.txt').write_text(''.join(f'INSTRUMENTATION_RESULT: {key}={value}\n'
                                                    for key, value in values.items()) + 'INSTRUMENTATION_CODE: -1\n')

    def validate(self):
        self.save()
        return verify_hologram(self.root, self.sha, self.tree, self.apks)


class PngEvidenceTest(unittest.TestCase):
    def test_all_five_png_filters_preserve_visible_pixels(self):
        rows = [
            ([10, 20, 30, 255, 70, 80, 90, 255], [1, 2, 3, 0, 50, 100, 150, 255]),
            ([10, 20, 30, 255, 60, 60, 60, 0], [1, 2, 3, 0, 49, 98, 147, 255]),
            ([10, 20, 30, 255, 70, 80, 90, 255], [247, 238, 229, 1, 236, 20, 60, 0]),
            ([10, 20, 30, 255, 65, 70, 75, 128], [252, 248, 244, 129, 15, 59, 104, 128]),
            ([10, 20, 30, 255, 60, 60, 60, 0], [247, 238, 229, 1, 236, 20, 60, 255]),
        ]
        for mode, (first, second) in enumerate(rows):
            with self.subTest(mode=mode):
                result = png_pixels(png(bytes([mode] + first + [mode] + second)))
                self.assertEqual(result, {'width': 2, 'height': 2, 'nontransparent_pixels': 3,
                                         'nonblack_rgb_pixels': 4, 'visible_rgb_pixels': 3})

    def test_rgb_has_no_implicit_transparency(self):
        result = png_pixels(png(bytes([0, 0, 0, 0, 20, 80, 150]), width=2, height=1, color=2))
        self.assertEqual((result['nontransparent_pixels'], result['visible_rgb_pixels']), (2, 1))

    def test_truncated_corrupt_or_trailing_png_is_rejected(self):
        image = png(bytes([0] + [20, 80, 150, 255] * 2) * 2)
        for payload in (image[:-1], image + b'garbage', image[:45] + bytes([image[45] ^ 1]) + image[46:]):
            with self.subTest(payload=payload):
                with self.assertRaises(AssertionError):
                    png_pixels(payload)

    def test_expansion_beyond_declared_dimensions_is_rejected(self):
        with self.assertRaises(AssertionError):
            png_pixels(png(b'\0' * 1000))


class HologramEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture = HologramFixture(self.temp.name)

    def test_consumed_frames_require_separate_image_review(self):
        result = self.fixture.validate()
        self.assertEqual(result['status'], 'SURFACE_FRAMES_VERIFIED')
        self.assertEqual(result['avatar_content'], 'REQUIRES_IMAGE_REVIEW')
        self.assertEqual(result['resize'], 'SWAPCHAIN_RESIZE_VERIFIED')

    def test_missing_or_incomplete_resize_evidence_fails(self):
        proof = self.fixture.report.pop('resize_regression')
        with self.assertRaises(KeyError):
            self.fixture.validate()
        self.fixture.report['resize_regression'] = proof
        for status in ('STARTED', 'FAILED', None):
            proof['status'] = status
            with self.assertRaises(AssertionError):
                self.fixture.validate()

    def test_replaced_engine_or_retained_swapchain_fails(self):
        state = self.fixture.report['resize_regression']['stage']['changed']
        for key, value in (('engine_identity', 99), ('native_renderer_identity', 99),
                           ('swapChain_identity', 21), ('consumer_generation', 1)):
            with self.subTest(key=key):
                old = state[key]
                state[key] = value
                with self.assertRaises(AssertionError):
                    self.fixture.validate()
                state[key] = old

    def test_resized_submission_without_new_consumed_frame_fails(self):
        stage = self.fixture.report['resize_regression']['stage']
        stage['rendered']['surface_texture_timestamp'] = stage['changed']['surface_texture_timestamp']
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_resize_cannot_credit_queued_old_timestamps(self):
        stage = self.fixture.report['resize_regression']['stage']
        boundary = stage['changed']['monotonic_nanos']
        for index, state in enumerate(stage['rendered_progress']):
            state['surface_texture_timestamp'] = boundary - 2 + index
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_resize_requires_two_distinct_new_consumptions(self):
        original = copy.deepcopy(self.fixture.report['resize_regression'])
        for fault in ('missing_second', 'same_timestamp', 'same_update_count'):
            with self.subTest(fault=fault):
                self.fixture.report['resize_regression'] = copy.deepcopy(original)
                stage = self.fixture.report['resize_regression']['stage']
                first, second = stage['rendered_progress']
                if fault == 'missing_second':
                    stage['rendered_progress'] = [second]
                elif fault == 'same_timestamp':
                    second['surface_texture_timestamp'] = first['surface_texture_timestamp']
                else:
                    second['consumer_updates'] = first['consumer_updates']
                with self.assertRaises(AssertionError):
                    self.fixture.validate()

    def test_resize_progress_cannot_change_generation(self):
        stage = self.fixture.report['resize_regression']['stage']
        stage['rendered_progress'][0]['consumer_generation'] += 99
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_resize_timestamp_cannot_borrow_elapsed_clock(self):
        proof = self.fixture.report['resize_regression']
        previous = proof['before']['surface_texture_timestamp']
        for group, changed, rendered in (
                ('stage', 'changed', 'rendered'), ('stage', 'restored', 'restored_rendered'),
                ('texture_layout', 'changed', 'rendered'), ('texture_layout', 'restored', 'restored_rendered')):
            proof[group][changed]['surface_texture_timestamp'] = previous
            for state in proof[group][rendered + '_progress']:
                state['surface_texture_timestamp'] = state['elapsed_nanos'] - 1
            previous = proof[group][rendered]['surface_texture_timestamp']
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_incomplete_resize_restore_fails(self):
        proof = self.fixture.report['resize_regression']
        proof['restore_error'] = 'Restore failed'
        with self.assertRaises(AssertionError):
            self.fixture.validate()
        del proof['restore_error']
        proof['texture_layout']['restored_rendered']['texture']['width'] = 1
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_resize_after_report_finished_fails(self):
        self.fixture.report['resize_regression']['finished_elapsed_nanos'] = 9_000_000_000
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_pixelcopy_source_size_is_separate_from_viewport(self):
        for sample in self.fixture.report['samples']:
            sample['viewport_width'] = 1
        result = self.fixture.validate()
        self.assertEqual(result['copied_surface_size'], [2, 2])
        self.assertEqual(result['renderer_viewport'], [1, 2])

    def test_verified_texture_view_subclass_is_supported(self):
        for sample in self.fixture.report['samples']:
            sample['texture']['class'] = 'at.heinz.deviceai.ContinuumHologramView$texture$1'
            sample['texture_is_texture_view'] = True
        self.assertEqual(self.fixture.validate()['status'], 'SURFACE_FRAMES_VERIFIED')

    def test_subclass_name_without_runtime_type_evidence_fails(self):
        self.fixture.report['samples'][0]['texture']['class'] = 'example.TextureView'
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_false_or_nonboolean_runtime_type_evidence_fails(self):
        sample = self.fixture.report['samples'][0]
        for evidence in (False, None, 1, 'true'):
            with self.subTest(evidence=evidence):
                sample['texture_is_texture_view'] = evidence
                with self.assertRaises(AssertionError):
                    self.fixture.validate()

    def test_submission_without_consumer_progress_fails(self):
        for timestamps in ((0, 0, 0), (0, 100, 200), (100, 100, 100), (100, 200, 0)):
            with self.subTest(timestamps=timestamps):
                for sample, timestamp in zip(self.fixture.report['samples'], timestamps):
                    sample['surface_texture_timestamp'] = timestamp
                with self.assertRaises(AssertionError):
                    self.fixture.validate()

    def test_replaced_surface_cannot_lend_previous_evidence(self):
        for key in ('surface_identity', 'surface_texture_identity'):
            with self.subTest(key=key):
                last = self.fixture.report['samples'][-1]
                original = last[key]
                last[key] = original + 1
                with self.assertRaises(AssertionError):
                    self.fixture.validate()
                last[key] = original

    def test_pixelcopy_failure_wrong_source_and_bad_size_fail(self):
        value = self.fixture.report['pixel_copy']
        for key, replacement in [('status', 3), ('source_identity', 42)]:
            with self.subTest(key=key):
                original = value[key]
                value[key] = replacement
                with self.assertRaises(AssertionError):
                    self.fixture.validate()
                value[key] = original
        value['image']['width'] = 3
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_independent_pixel_counts_reject_fabricated_metadata(self):
        self.fixture.report['texture']['nontransparent_pixels'] = 3
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_hardware_null_counts_still_decode_actual_png(self):
        self.fixture.report['screen']['nontransparent_pixels'] = None
        self.fixture.report['screen']['nonblack_rgb_pixels'] = None
        self.assertEqual(self.fixture.validate()['status'], 'SURFACE_FRAMES_VERIFIED')

    def test_separate_alpha_and_rgb_counts_do_not_prove_visibility(self):
        pixels = bytes([0, 0, 0, 255, 80, 120, 160, 0] * 2)
        self.fixture.report['texture'] = self.fixture.image('hologram-texture.png', pixels)
        self.assertEqual(self.fixture.report['texture']['nontransparent_pixels'], 2)
        self.assertEqual(self.fixture.report['texture']['nonblack_rgb_pixels'], 2)
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_all_transparent_or_black_pixels_fail(self):
        for pixel in ([0, 0, 0, 0], [0, 0, 0, 255]):
            self.fixture.report['pixel_copy']['image'] = self.fixture.image('hologram-surface-copy.png', bytes(pixel * 4))
            with self.assertRaises(AssertionError):
                self.fixture.validate()

    def test_hidden_stage_or_parent_fails(self):
        for target in (self.fixture.report['samples'][0]['texture'], self.fixture.report['samples'][1]['ancestors'][0]):
            target['alpha'] = 0
            with self.assertRaises(AssertionError):
                self.fixture.validate()
            target['alpha'] = 1

    def test_unbound_report_image_or_apk_fails(self):
        path = self.fixture.root / 'hologram-state.json'
        path.write_bytes(path.read_bytes() + b' ')
        with self.assertRaises(AssertionError):
            verify_hologram(self.fixture.root, self.fixture.sha, self.fixture.tree, self.fixture.apks)
        self.fixture.save()
        with self.assertRaises(AssertionError):
            verify_hologram(self.fixture.root, self.fixture.sha, self.fixture.tree, {**self.fixture.apks, 'app-debug.apk': 'f' * 64})
        image = self.fixture.root / 'hologram-texture.png'
        image.write_bytes(image.read_bytes() + b' ')
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_capture_before_comparison_or_short_window_fails(self):
        self.fixture.report['comparison_second_sample'] = 0
        with self.assertRaises(AssertionError):
            self.fixture.validate()
        self.fixture.report['comparison_second_sample'] = 1
        self.fixture.report['samples'][1]['elapsed_nanos'] = 2_000_000_000
        with self.assertRaises(AssertionError):
            self.fixture.validate()
        self.fixture.report['samples'][1]['elapsed_nanos'] = 3_200_000_000
        with self.assertRaises(AssertionError):
            self.fixture.validate()

    def test_duplicate_or_failed_instrumentation_fails(self):
        path = self.fixture.root / 'native-hologram.txt'
        original = path.read_text()
        for suffix in ('INSTRUMENTATION_RESULT: status=ok\n', 'INSTRUMENTATION_CODE: -1\n', 'INSTRUMENTATION_FAILED: bad\n'):
            path.write_text(original + suffix)
            with self.assertRaises(AssertionError):
                verify_hologram(self.fixture.root, self.fixture.sha, self.fixture.tree)


if __name__ == '__main__':
    unittest.main()
