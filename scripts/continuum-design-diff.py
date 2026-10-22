#!/usr/bin/env python3
"""Measured comparison of a Continuum start-screen image with the owner's reference image.

Input: a rendering of the start screen (device snapshot from "Startbildschirm für Design-Abnahme sichern" with its
metrics JSON, or a JVM preview with --density/--scale), and reference-phone.png / reference-fold.png.
1. The rendering is resampled to the reference's pixel grid (phone 1.7306 px/dp, fold 1.54 px/dp; calibration of
   ContinuumHomeLayout) using density x layout scale.
2. Its position inside the reference (which shows a device frame) is found by maximising the normalised cross-
   correlation of edge maps, coarse to fine; the offset is reported, not assumed.
3. Per layout slot (boxes copied from ContinuumHomeLayout at the commit of this script) the mean absolute colour
   difference and the edge correlation are reported; overlay, difference heat map and side-by-side images are written.
Numbers describe pixel agreement, not perceived quality; font rasterisation and the reference's photographic glow
always leave a residual difference.

usage: continuum-design-diff.py <render.png> <reference.png> <outDir> [--metrics m.json | --density D --scale U --wide]
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

PHONE = {  # reference dp, from ContinuumHomeLayout.phoneReference
    'BRAND': (0, 0, 276, 68.8), 'HERO': (0, 68.8, 412, 375.6), 'MODEL': (13.3, 380.3, 398.8, 469.4),
    'LEARNING': (13.3, 479.6, 215.5, 661.6), 'STORAGE': (224.8, 479.6, 398.8, 661.6), 'DEVELOPMENT': (13.3, 673.2, 398.8, 773.2),
    'NAV': (0, 787, 412, 861)}
WIDE = {  # reference dp, from ContinuumHomeLayout.wideReference
    'RAIL': (0, 0, 76, 649), 'BRAND': (80, 30, 420, 86), 'HERO': (212, 0, 622, 291), 'SYSTEM_STATUS': (657.1, 84.4, 829.2, 276),
    'MODEL': (87.7, 290.9, 335.7, 475.3), 'LEARNING': (345.5, 290.9, 613, 475.3), 'STORAGE': (624.7, 290.9, 829.2, 475.3),
    'DEVELOPMENT': (87.7, 485.7, 492.9, 642.9), 'SYSTEM_ACTIVITY': (504.5, 485.7, 829.2, 642.9)}
PX_PER_DP = {'phone': 1.7306, 'wide': 1.54}


def edges(img):
    g = np.asarray(img.convert('L').filter(ImageFilter.FIND_EDGES), dtype=np.float32)
    return (g - g.mean()) / (g.std() + 1e-6)


def ncc(a, b):
    a = a - a.mean(); b = b - b.mean()
    return float((a * b).sum() / (np.sqrt((a * a).sum() * (b * b).sum()) + 1e-9))


def locate(render, reference, step=4):
    """Offset (x, y) of render inside reference maximising edge correlation: coarse grid, then refinement."""
    rw, rh = render.size; W, H = reference.size
    small_r = edges(render.resize((max(1, rw // step), max(1, rh // step))))
    small_R = edges(reference.resize((max(1, W // step), max(1, H // step))))
    best = (-2, 0, 0)
    for y in range(0, small_R.shape[0] - small_r.shape[0] + 1):
        for x in range(0, small_R.shape[1] - small_r.shape[1] + 1):
            s = ncc(small_r, small_R[y:y + small_r.shape[0], x:x + small_r.shape[1]])
            if s > best[0]: best = (s, x * step, y * step)
    er, eR = edges(render), edges(reference)
    s0, x0, y0 = best
    for y in range(max(0, y0 - step), min(H - rh, y0 + step) + 1):
        for x in range(max(0, x0 - step), min(W - rw, x0 + step) + 1):
            s = ncc(er, eR[y:y + rh, x:x + rw])
            if s > best[0]: best = (s, x, y)
    return best


def main():
    args = sys.argv[1:]
    render_path, ref_path, out = Path(args[0]), Path(args[1]), Path(args[2])
    opt = {args[i][2:]: (args[i + 1] if i + 1 < len(args) and not args[i + 1].startswith('--') else True)
           for i in range(3, len(args)) if args[i].startswith('--')}
    if 'metrics' in opt:
        m = json.loads(Path(opt['metrics']).read_text())
        density, scale, wide = m['density'], m.get('layoutScale') or 1.0, bool(m.get('wideLayout'))
        top_inset = (m.get('insetsPx') or {}).get('top', 0)
    else:
        density, scale, wide, top_inset = float(opt.get('density', 2)), float(opt.get('scale', 1)), 'wide' in opt, 0
    kind = 'wide' if wide else 'phone'
    boxes = WIDE if wide else PHONE
    render = Image.open(render_path).convert('RGB')
    if top_inset and kind == 'phone':
        render = render.crop((0, top_inset, render.width, render.height))   # phone reference origin: below the status bar
    reference = Image.open(ref_path).convert('RGB')
    f = PX_PER_DP[kind] / (density * scale)
    resized = render.resize((round(render.width * f), round(render.height * f)), Image.LANCZOS)
    if resized.width > reference.width or resized.height > reference.height:
        resized = resized.crop((0, 0, min(resized.width, reference.width), min(resized.height, reference.height)))
    score, ox, oy = locate(resized, reference)
    out.mkdir(parents=True, exist_ok=True)
    region = reference.crop((ox, oy, ox + resized.width, oy + resized.height))
    a, b = np.asarray(resized, dtype=np.float32), np.asarray(region, dtype=np.float32)
    diff = np.abs(a - b).mean(axis=2)
    heat = Image.fromarray(np.clip(diff * 2, 0, 255).astype(np.uint8)).convert('RGB')
    Image.blend(region, resized, .5).save(out / 'overlay.png')
    heat.save(out / 'difference.png')
    side = Image.new('RGB', (resized.width * 2 + 10, resized.height), (0, 0, 0))
    side.paste(region, (0, 0)); side.paste(resized, (resized.width + 10, 0)); side.save(out / 'side-by-side.png')
    k = PX_PER_DP[kind]
    slots = {}
    for name, (l, t, r, btm) in boxes.items():
        x0, y0, x1, y1 = (int(v * k) for v in (l, t, r, btm))
        x1 = min(x1, resized.width); y1 = min(y1, resized.height)
        if x1 - x0 < 8 or y1 - y0 < 8: continue
        ra, rb = a[y0:y1, x0:x1], b[y0:y1, x0:x1]
        slots[name] = {'meanAbsDiff': round(float(np.abs(ra - rb).mean()), 1),
                       'edgeCorrelation': round(ncc(edges(resized.crop((x0, y0, x1, y1))), edges(region.crop((x0, y0, x1, y1)))), 3)}
    marked = region.copy(); d = ImageDraw.Draw(marked)
    for name, (l, t, r, btm) in boxes.items():
        d.rectangle([l * k, t * k, r * k, btm * k], outline=(255, 80, 80)); d.text((l * k + 3, t * k + 3), name, fill=(255, 80, 80))
    marked.save(out / 'reference-slots.png')
    report = {'kind': kind, 'scaleToReference': round(f, 4), 'offsetPx': [ox, oy], 'edgeCorrelation': round(score, 3),
              'meanAbsDiff': round(float(diff.mean()), 1), 'slots': slots}
    (out / 'report.json').write_text(json.dumps(report, indent=1))
    print(json.dumps(report, indent=1))


if __name__ == '__main__':
    main()
