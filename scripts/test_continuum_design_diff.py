"""Unit tests for continuum-design-diff.py: registration at full resolution, frozen offsets and live-value masks.

Run: python3 -m unittest discover -s scripts -p 'test_continuum_*.py'
"""
import importlib.util
import unittest
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

_spec = importlib.util.spec_from_file_location('design_diff', Path(__file__).with_name('continuum-design-diff.py'))
diff = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(diff)


def scene(w=420, h=320, seed=7):
    """A dark synthetic screen with shapes and a few hard edges."""
    rng = np.random.default_rng(seed)
    img = Image.new('RGB', (w, h), (6, 12, 34))
    d = ImageDraw.Draw(img)
    for _ in range(40):
        x, y = int(rng.integers(0, w - 40)), int(rng.integers(0, h - 40))
        s = int(rng.integers(6, 38))
        color = tuple(int(c) for c in rng.integers(40, 255, 3))
        (d.ellipse if rng.random() < .5 else d.rectangle)([x, y, x + s, y + s], outline=color, width=2)
    return img


class LocateTest(unittest.TestCase):
    def test_finds_the_true_offset_and_reports_a_full_resolution_score(self):
        reference = scene()
        render = reference.crop((37, 23, 37 + 240, 23 + 180))
        score, x, y = diff.locate(render, reference)
        self.assertEqual((x, y), (37, 23))
        full = diff.ncc(diff.edges(render), diff.edges(reference)[23:23 + 180, 37:37 + 240])
        self.assertAlmostEqual(score, full, places=5)
        self.assertLessEqual(score, 1.0 + 1e-6)

    def test_refinement_is_not_blocked_by_a_coarse_score(self):
        # Like a real rendering, the image differs from the reference in fine detail (noise): downsampling averages
        # that out, so coarse scores are higher than every full-resolution score. The refinement must still decide,
        # find an offset that is not a multiple of the coarse step and report the full-resolution score.
        reference = scene(seed=11)
        crop = np.asarray(reference.crop((41, 30, 41 + 220, 30 + 170)), dtype=np.float32)
        noisy = crop + np.random.default_rng(5).normal(0, 70, crop.shape)
        render = Image.fromarray(np.clip(noisy, 0, 255).astype(np.uint8))
        score, x, y = diff.locate(render, reference)
        self.assertEqual((x, y), (41, 30))
        full = diff.ncc(diff.edges(render), diff.edges(reference)[30:30 + 170, 41:41 + 220])
        self.assertAlmostEqual(score, full, places=5)


class CompareTest(unittest.TestCase):
    def setUp(self):
        self.reference = scene(900, 1700, seed=3)
        self.f = 1.0

    def crop(self, ox, oy, w=600, h=1400):
        return self.reference.crop((ox, oy, ox + w, oy + h))

    def test_frozen_offset_is_used_as_given(self):
        report, *_ = diff.compare(self.crop(10, 5), self.reference, 'phone', self.f, offset=(10, 5))
        self.assertEqual(report['registration'], 'frozen')
        self.assertEqual(report['offsetPx'], [10, 5])
        self.assertEqual(report['meanAbsDiff'], 0.0)

    def test_live_values_are_masked_and_labels_are_not(self):
        render = self.crop(10, 5).copy()
        d = ImageDraw.Draw(render)
        d.rectangle([100, 700, 180, 730], fill=(255, 255, 255))     # a different "number" than in the reference
        k = diff.PX_PER_DP['phone']
        texts = [{'text': '42 %', 'boxDp': [100 / k, 700 / k, 180 / k, 730 / k]},
                 {'text': 'MODELLSTATUS', 'boxDp': [10 / k, 10 / k, 300 / k, 40 / k]}]
        report, *_ = diff.compare(render, self.reference, 'phone', self.f, offset=(10, 5), texts=texts)
        self.assertGreater(report['meanAbsDiff'], 0.0)
        self.assertEqual(report['meanAbsDiffMasked'], 0.0)
        self.assertLess(report['maskedFraction'], .01)              # only the value box (with margin), not the label

    def test_low_frequency_difference_ignores_fine_shifts_but_not_brightness(self):
        base = self.crop(10, 5)
        shifted = Image.fromarray(np.roll(np.asarray(base), 2, axis=1))     # same content, lines 2 px off
        darker = Image.fromarray((np.asarray(base) * .6).astype(np.uint8))  # same lines, less light
        r_shift, *_ = diff.compare(shifted, self.reference, 'phone', self.f, offset=(10, 5))
        r_dark, *_ = diff.compare(darker, self.reference, 'phone', self.f, offset=(10, 5))
        self.assertLess(r_shift['meanAbsDiffLowFreq'], r_shift['meanAbsDiff'] / 2)
        self.assertGreater(r_dark['meanAbsDiffLowFreq'], r_shift['meanAbsDiffLowFreq'])

    def test_live_value_pattern(self):
        for text in ('62 %', '—', '3,1 GB', 'vor 6 Min'):
            self.assertTrue(diff.LIVE_VALUE.search(text), text)
        for text in ('Lernen & Forschung', 'MODELLSTATUS', 'Continuum'):
            self.assertFalse(diff.LIVE_VALUE.search(text), text)


if __name__ == '__main__':
    unittest.main()
