#!/usr/bin/env python3
"""Software SkSL checks; no Android/GPU claim."""
import argparse
import hashlib
import json
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


def render(phase=.5, energy=1, seconds=.6, box=(24., 24., 376., 216.), radius=14., strips=True, effect=EFFECT):
    surface = skia.Surface(400, 240)
    canvas = surface.getCanvas()
    canvas.clear(skia.ColorTRANSPARENT)
    values = struct.pack("8f", *box, radius, phase, energy, seconds)
    paint = skia.Paint(Shader=effect.makeShader(skia.Data.MakeWithCopy(values)))
    left, top, right, bottom = box
    spread = float(re.search(r"const val FLUID_SPREAD_DP = ([0-9.]+)f", TEXT).group(1))
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

    def test_adjacent_frames_and_reversal_are_continuous(self):
        frames = [render(phase=t / 1200, seconds=t / 1000)[1].astype(int)
                  for t in range(0, 1201, 16)]
        for first, second in zip(frames, frames[1:]):
            self.assertLess(int(np.abs(first - second).max()), 48)
        for index in range(len(frames) - 1, -1, -1):
            t = index * 16
            self.assertTrue(np.array_equal(frames[index], render(phase=t / 1200, seconds=t / 1000)[1]))

    def test_body_has_connected_width_instead_of_wire(self):
        alpha = render()[1][:, :, 3]
        widths = np.count_nonzero(alpha[44:196, 18:31] >= 50, axis=1)
        self.assertGreater(float(np.median(widths)), 3)
        self.assertGreater(int(np.count_nonzero(widths >= 5)), 40)

    def test_perimeter_energy_fades_without_content_flash(self):
        full = render()[1].astype(int)
        half = render(energy=.5)[1].astype(int)
        self.assertLessEqual(int(np.abs(full - 2 * half).max()), 1)
        self.assertEqual(int(np.count_nonzero(full[40:200, 40:360])), 0)

    def test_pass_never_creates_offscreen_layers(self):
        method = TEXT.split('fun drawFluidEdge(c: Canvas', 1)[1].split('fun drawBackground(c:', 1)[0]
        self.assertNotRegex(method, r'RenderNode|RenderEffect|saveLayer|SystemClock|ValueAnimator')


def preview(directory, baseline_source=None):
    directory.mkdir(parents=True, exist_ok=True)
    for index in range(12):
        image, _ = render(phase=index / 11, seconds=index / 24)
        image.save(str(directory / f"edge-{index:02}.png"), skia.kPNG)
    comparison(directory, baseline_source)
    (directory / "README.txt").write_text(
        f"Software Skia {skia.__version__}; actual FLUID_EDGE shader.\n"
        "No Android RuntimeShader, GPU, emulator or Fold7 acceptance.\n"
        "Transparent frames show only the native edge pass.\n")


def comparison(directory, baseline_source):
    baseline = None
    if baseline_source:
        shader = re.search(r'const val FLUID_EDGE = """(.*?)"""', baseline_source.read_text(), re.S).group(1)
        baseline = skia.RuntimeEffect.MakeForShader(shader)
    rows = [("BASE: thin filament", baseline), ("CANDIDATE: viscous edge", EFFECT)] if baseline else [
        ("CANDIDATE: viscous edge", EFFECT)]
    surface = skia.Surface(1200, 90 + 280 * len(rows))
    canvas = surface.getCanvas()
    canvas.clear(skia.ColorSetRGB(5, 13, 25))
    font = skia.Font(skia.Typeface.MakeFromName("DejaVu Sans", skia.FontStyle()), 18)
    label = skia.Paint(Color=skia.ColorSetRGB(200, 220, 240), AntiAlias=True)
    canvas.drawString("Actual embedded shader | Skia software | No Android GPU / Fold7 proof", 22, 30, font, label)
    metrics = {}
    for row, (name, effect) in enumerate(rows):
        top = 65 + row * 280
        canvas.drawString(name, 22, top, font, label)
        for col, phase in enumerate((.2, .5, .8)):
            image, pixels = render(phase=phase, seconds=phase * 1.2, effect=effect)
            canvas.drawImage(image, col * 400, top + 10)
            canvas.drawString(f"phase {phase:.1f} | transparent centre", col * 400 + 52, top + 131, font, label)
            alpha = pixels[:, :, 3]
            metrics[f"{name}:{phase}"] = {
                "lit_pixels_alpha_ge_50": int(np.count_nonzero(alpha >= 50)),
                "maximum_alpha": int(alpha.max()),
                "interior_nonzero_channels": int(np.count_nonzero(pixels[40:200, 40:360])),
            }
    surface.makeImageSnapshot().save(str(directory / "edge-comparison.png"), skia.kPNG)
    (directory / "metrics.json").write_text(json.dumps({
        "shader_sha256": hashlib.sha256(SHADER.encode()).hexdigest(),
        "renderer": f"Skia {skia.__version__} software raster",
        "Android_GPU_Fold7": "NOT_RUN", "frames": metrics}, indent=2) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview-dir", type=Path)
    parser.add_argument("--baseline-source", type=Path)
    args = parser.parse_args()
    if args.preview_dir:
        preview(args.preview_dir, args.baseline_source)
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(FluidEdgeTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
