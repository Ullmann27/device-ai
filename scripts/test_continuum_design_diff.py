"""Deterministic geometry/provenance regressions for the standalone visual QA CLI."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image, ImageDraw

SCRIPT = Path(__file__).with_name('continuum-design-diff.py')
spec = importlib.util.spec_from_file_location('design_diff', SCRIPT)
diff = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diff)


class AlignmentTest(unittest.TestCase):
    def test_refinement_score_is_measured_at_full_resolution(self):
        rng = np.random.default_rng(6)
        render = Image.fromarray(rng.integers(0, 256, (28, 24, 3), dtype=np.uint8))
        reference = Image.new('RGB', (43, 49))
        reference.paste(render, (7, 9))
        score, x, y = diff.locate(render, reference)
        measured = diff.ncc(diff.edges(render), diff.edges(reference)[y:y+28, x:x+24])
        self.assertAlmostEqual(score, measured, places=5)

    def test_known_translation_and_boundary(self):
        render = Image.new('RGB', (40, 40))
        d = ImageDraw.Draw(render)
        d.ellipse((7, 5, 30, 34), fill=(150, 230, 255))
        d.rectangle((10, 16, 24, 23), fill=(10, 25, 30))
        for offset in ((7, 9), (0, 0), (19, 17)):
            with self.subTest(offset=offset):
                reference = Image.new('RGB', (59, 57))
                reference.paste(render, offset)
                self.assertEqual(diff.locate(render, reference)[1:], offset)

    def test_oversized_locate_fails(self):
        with self.assertRaises(ValueError):
            diff.locate(Image.new('RGB', (20, 20)), Image.new('RGB', (19, 20)))


class CliTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.render = Image.new('RGB', (40, 40))
        ImageDraw.Draw(self.render).ellipse((8, 7, 30, 33), fill=(80, 220, 255))
        self.render.save(self.root / 'render.png')
        reference = Image.new('RGB', (60, 60))
        reference.paste(self.render, (7, 9))
        reference.save(self.root / 'reference.png')

    def run_cli(self, *extra, out='out'):
        return subprocess.run([sys.executable, str(SCRIPT), str(self.root/'render.png'),
                               str(self.root/'reference.png'), str(self.root/out),
                               '--density', '1.7306', *extra], capture_output=True, text=True)

    def report(self, name='out'):
        return json.loads((self.root/name/'report.json').read_text())

    def test_fixed_offset_is_honoured_and_has_source_hashes(self):
        result = self.run_cli('--offset-x', '3', '--offset-y', '4')
        self.assertEqual(result.returncode, 0, result.stderr)
        report = self.report()
        self.assertEqual(report['offsetPx'], [3, 4])
        self.assertEqual(report['alignment']['mode'], 'fixed')
        self.assertEqual(len(report['sources']['render']['sha256']), 64)
        self.assertEqual(len(report['sources']['script']['sha256']), 64)

    def test_invalid_offset_or_scale_fails_without_report(self):
        for args in [('--offset-x','7'), ('--offset-x','-1','--offset-y','0'),
                     ('--offset-x','21','--offset-y','0'), ('--scale','0'),
                     ('--scale','nan'), ('--density','inf')]:
            with self.subTest(args=args):
                self.assertNotEqual(self.run_cli(*args).returncode, 0)
                self.assertFalse((self.root/'out'/'report.json').exists())

    def test_oversized_image_is_rejected_without_silent_crop(self):
        self.render.resize((80, 40)).save(self.root/'render.png')
        self.assertNotEqual(self.run_cli().returncode, 0)
        self.assertFalse((self.root/'out'/'report.json').exists())

    def test_alignment_replay_freezes_candidate_position_and_checks_reference(self):
        self.assertEqual(self.run_cli('--offset-x','7','--offset-y','9').returncode, 0)
        self.render.transpose(Image.Transpose.FLIP_LEFT_RIGHT).save(self.root/'render.png')
        result = self.run_cli('--alignment',str(self.root/'out'/'report.json'),out='candidate')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.report('candidate')['offsetPx'], [7, 9])
        Image.new('RGB', (60, 60), 'red').save(self.root/'reference.png')
        self.assertNotEqual(self.run_cli('--alignment',str(self.root/'out'/'report.json'),out='bad').returncode, 0)
        self.assertFalse((self.root/'bad'/'report.json').exists())

    def test_alignment_rejects_changed_candidate_geometry(self):
        self.assertEqual(self.run_cli('--offset-x','7','--offset-y','9').returncode, 0)
        self.render.resize((41,40)).save(self.root/'render.png')
        self.assertNotEqual(self.run_cli('--alignment',str(self.root/'out'/'report.json'),out='bad').returncode, 0)

    def test_metrics_validate_window_insets_and_types(self):
        valid = {'schema':'continuum-ui-snapshot','version':1,'windowPx':'40x40',
                 'density':1.7306,'layoutScale':1,'wideLayout':False,
                 'insetsPx':{'left':2,'top':3,'right':4,'bottom':5}}
        path=self.root/'metrics.json'
        path.write_text(json.dumps(valid))
        # Explicit density is incompatible with --metrics: no ambiguous precedence.
        result=subprocess.run([sys.executable,str(SCRIPT),str(self.root/'render.png'),
                               str(self.root/'reference.png'),str(self.root/'good'),
                               '--metrics',str(path),'--offset-x','7','--offset-y','9'],
                              capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(self.report('good')['geometry']['cropBoxPx'],[2,3,36,35])
        for patch in [{'windowPx':'41x40'},{'density':0},{'layoutScale':None},
                      {'wideLayout':'false'},{'insetsPx':{'top':41}},
                      {'insetsPx':{'left':0,'top':0,'right':-1,'bottom':0}}]:
            with self.subTest(patch=patch):
                path.write_text(json.dumps({**valid,**patch}))
                result=subprocess.run([sys.executable,str(SCRIPT),str(self.root/'render.png'),
                                       str(self.root/'reference.png'),str(self.root/'bad'),
                                       '--metrics',str(path)],capture_output=True,text=True)
                self.assertNotEqual(result.returncode,0)
                self.assertFalse((self.root/'bad'/'report.json').exists())


class ShapeMetricTest(unittest.TestCase):
    def test_shape_metrics_detect_shift_and_brightness_without_mae(self):
        a = Image.new('RGB',(64,64))
        ImageDraw.Draw(a).ellipse((16,16,40,40),fill='white')
        shifted = Image.new('RGB',(64,64))
        shifted.paste(a,(6,0))
        same = diff.shape_metrics(a,a)
        moved = diff.shape_metrics(a,shifted)
        self.assertEqual(same['brightMaskIoU'],1)
        self.assertEqual(same['edgeDistancePx'],0)
        self.assertLess(moved['brightMaskIoU'],same['brightMaskIoU'])
        self.assertGreater(moved['edgeDistancePx'],0)
        self.assertGreater(moved['luminanceProfileL1'],0)
        dark = Image.fromarray((np.asarray(a)*.5).astype(np.uint8))
        self.assertGreater(diff.shape_metrics(a,dark)['luminanceHistogramWasserstein'],0)

    def test_empty_bright_masks_are_reported_explicitly(self):
        a=Image.new('RGB',(20,20))
        report=diff.shape_metrics(a,a)
        self.assertIsNone(report['brightMaskIoU'])
        self.assertEqual(report['renderBrightCoverage'],0)


if __name__ == '__main__':
    unittest.main()
