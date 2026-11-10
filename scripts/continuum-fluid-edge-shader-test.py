#!/usr/bin/env python3
"""Software SkSL checks; no Android/GPU claim."""
import argparse
from pathlib import Path
import re
import struct
import sys
import unittest

import numpy as np
import skia

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/at/heinz/deviceai/ui/JarvisGlassRenderer.kt"
TEXT = SOURCE.read_text()
SHADER = re.search(r'const val FLUID_EDGE = """(.*?)"""', TEXT, re.S).group(1)
EFFECT = skia.RuntimeEffect.MakeForShader(SHADER)


def render(phase=.5, energy=1, seconds=.6, box=(24., 24., 376., 216.), radius=14., strips=True):
    surface = skia.Surface(400, 240)
    canvas = surface.getCanvas()
    canvas.clear(skia.ColorTRANSPARENT)
    values = struct.pack("8f", *box, radius, phase, energy, seconds)
    paint = skia.Paint(Shader=EFFECT.makeShader(skia.Data.MakeWithCopy(values)))
    left, top, right, bottom = box
    spread = 6.
    inner_x = min((right - left) / 2, radius + spread)
    inner_y = min((bottom - top) / 2, radius + spread)
    if strips:
        bounds = [(left - spread, top - spread, left + inner_x, bottom + spread),
                  (right - inner_x, top - spread, right + spread, bottom + spread)]
        if left + inner_x < right - inner_x:
            bounds += [(left + inner_x, top - spread, right - inner_x, top + inner_y),
                       (left + inner_x, bottom - inner_y, right - inner_x, bottom + spread)]
    else:
        bounds = [(left - spread, top - spread, right + spread, bottom + spread)]
    for rect in bounds:
        canvas.drawRect(skia.Rect.MakeLTRB(*rect), paint)
    image = surface.makeImageSnapshot()
    pixels = image.toarray(colorType=skia.ColorType.kRGBA_8888_ColorType,
                          alphaType=skia.AlphaType.kPremul_AlphaType)
    return image, pixels


class FluidEdgeTests(unittest.TestCase):
    def test_actual_shader_compiles(self):
        self.assertIsNotNone(EFFECT)

    def test_zero_energy_is_transparent(self):
        self.assertEqual(int(render(energy=0)[1].sum()), 0)

    def test_text_interior_stays_transparent(self):
        for phase in (0, .25, .5, .75, 1):
            pixels = render(phase=phase)[1]
            self.assertEqual(int(pixels[40:200, 40:360].sum()), 0)
            self.assertGreater(int(pixels[:, :, 3].sum()), 10000)

    def test_premultiplied_alpha_is_bounded(self):
        for phase in (0, .5, 1):
            pixels = render(phase=phase)[1].astype(int)
            self.assertTrue(np.all(pixels[:, :, :3] <= pixels[:, :, 3:4] + 1))
            self.assertLessEqual(int(pixels[:, :, 3].max()), 235)

    def test_same_event_time_reproduces_pixels(self):
        self.assertTrue(np.array_equal(render()[1], render()[1]))

    def test_time_and_phase_move_light(self):
        baseline = render()[1]
        self.assertGreater(int(np.count_nonzero(baseline != render(seconds=.8)[1])), 1000)
        self.assertGreater(int(np.count_nonzero(baseline != render(phase=.8)[1])), 1000)

    def test_strips_match_full_shader_without_seams(self):
        for box, radius in (((24., 24., 376., 216.), 14.),
                            ((24.2, 24.2, 376.2, 216.2), 14.),
                            ((24., 24., 100., 90.), 33.),
                            ((24., 24., 32., 32.), 4.)):
            with self.subTest(box=box):
                partial = render(box=box, radius=radius)[1]
                full = render(box=box, radius=radius, strips=False)[1]
                self.assertTrue(np.array_equal(partial, full))

    def test_pass_never_creates_offscreen_layers(self):
        method = TEXT.split('fun drawFluidEdge(c: Canvas', 1)[1].split('fun drawBackground(c:', 1)[0]
        self.assertNotRegex(method, r'RenderNode|RenderEffect|saveLayer|SystemClock|ValueAnimator')


def preview(directory):
    directory.mkdir(parents=True, exist_ok=True)
    for index in range(12):
        image, _ = render(phase=index / 11, seconds=index / 24)
        image.save(str(directory / f"edge-{index:02}.png"), skia.kPNG)
    (directory / "README.txt").write_text(
        f"Software Skia {skia.__version__}; actual FLUID_EDGE shader.\n"
        "No Android RuntimeShader, GPU, emulator or Fold7 acceptance.\n"
        "Transparent frames show only the native edge pass.\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview-dir", type=Path)
    args = parser.parse_args()
    if args.preview_dir:
        preview(args.preview_dir)
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(FluidEdgeTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
