import unittest

import numpy as np
from PIL import Image

from compare_jarvis_reference import align_capture, metrics, rectangle


class ReferenceComparisonTest(unittest.TestCase):
    def test_mask_keeps_raw_failure_and_reports_excluded_area(self):
        reference = np.zeros((2, 2, 3), dtype=np.uint8)
        capture = reference.copy()
        capture[0, 0] = 255
        raw = metrics(reference, capture, np.ones((2, 2), dtype=bool))
        masked = metrics(reference, capture, np.array([[False, True], [True, True]]))
        self.assertEqual(63.75, raw["mean_absolute_rgb_error_0_255"])
        self.assertFalse(raw["identical_included_pixels"])
        self.assertTrue(masked["identical_included_pixels"])
        self.assertEqual(3, masked["included_pixels"])
        self.assertEqual(1, masked["excluded_pixels"])

    def test_alignment_never_stretches_or_silently_resamples(self):
        image = Image.new("RGB", (960, 640))
        with self.assertRaises(ValueError):
            align_capture(image, (1536, 1024), False)
        with self.assertRaises(ValueError):
            align_capture(image, (1536, 1152), True)
        aligned, operation = align_capture(image, (1536, 1024), True)
        self.assertEqual((1536, 1024), aligned.size)
        self.assertEqual(1.6, operation["scale"])
        self.assertEqual("uniform_resample", operation["operation"])

    def test_invalid_or_fully_hidden_regions_are_rejected(self):
        with self.assertRaises(ValueError):
            rectangle([0, 0, 11, 5], (10, 5))
        with self.assertRaises(ValueError):
            metrics(np.zeros((2, 2, 3)), np.zeros((2, 2, 3)), np.zeros((2, 2), dtype=bool))


if __name__ == "__main__":
    unittest.main()
