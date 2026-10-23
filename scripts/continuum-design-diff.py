#!/usr/bin/env python3
"""Measured comparison of a Continuum start-screen image with the owner's reference image.

Input: a rendering of the start screen (device snapshot from "Startbildschirm für Design-Abnahme sichern" with its
metrics JSON, or a JVM preview with --density/--scale), and reference-phone.png / reference-fold.png.
1. The rendering is resampled to the reference's pixel grid (phone 1.7306 px/dp, fold 1.54 px/dp; calibration of
   ContinuumHomeLayout) using density x layout scale.
2. Its position inside the reference (which shows a device frame) is FROZEN in registration.json next to the reference,
   so successive design iterations are compared at the same place. --search finds the position instead (edge
   correlation: a coarse grid proposes, full resolution decides; only full-resolution scores are compared and
   reported) and --freeze stores the found position.
3. Per layout slot (boxes copied from ContinuumHomeLayout at the commit of this script) the mean absolute colour
   difference and the edge correlation are reported; overlay, difference heat map and side-by-side images are written.
4. --texts <render>.texts.json (written by the JVM preview) masks drawn live values (texts with digits or the unknown
   value dash): the *Masked metrics leave those pixels out, so real measurements that differ from the reference's
   sample numbers do not count as a design difference.
5. Composition and colour character: meanAbsDiffLowFreq compares both images blurred by 4 dp (Gaussian), i.e. where
   light, colour and mass sit, independent of the exact position of fine lines and particles (which a different but
   equally good network can never reproduce pixel by pixel). Edges still measure the fine structure.
Numbers describe pixel agreement, not perceived quality; font rasterisation and the reference's photographic glow
always leave a residual difference.

usage: continuum-design-diff.py <render.png> <reference.png> <outDir> [--metrics m.json | --density D --scale U --wide]
       [--texts t.json] [--registration r.json] [--search] [--freeze]
"""
import json
import re
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
LIVE_VALUE = re.compile(r'[0-9—]')     # measured values contain digits; unknown values are drawn as an em dash
MASK_MARGIN_DP = 3.0
LOW_FREQ_DP = 4.0


def edges(img):
    g = np.asarray(img.convert('L').filter(ImageFilter.FIND_EDGES), dtype=np.float32)
    return (g - g.mean()) / (g.std() + 1e-6)


def ncc(a, b):
    a = a - a.mean(); b = b - b.mean()
    return float((a * b).sum() / (np.sqrt((a * a).sum() * (b * b).sum()) + 1e-9))


def locate(render, reference, step=4):
    """Offset (score, x, y) of render inside reference maximising the full-resolution edge correlation.

    A grid at 1/step resolution proposes a position; the refinement around it compares full-resolution scores only.
    (Mixing the two scales let a coarse score, which is systematically higher, block every refinement and be
    reported as if it were a full-resolution correlation.)"""
    rw, rh = render.size; W, H = reference.size
    small_r = edges(render.resize((max(1, rw // step), max(1, rh // step))))
    small_R = edges(reference.resize((max(1, W // step), max(1, H // step))))
    coarse = (-2.0, 0, 0)
    for y in range(0, small_R.shape[0] - small_r.shape[0] + 1):
        for x in range(0, small_R.shape[1] - small_r.shape[1] + 1):
            s = ncc(small_r, small_R[y:y + small_r.shape[0], x:x + small_r.shape[1]])
            if s > coarse[0]: coarse = (s, x * step, y * step)
    er, eR = edges(render), edges(reference)
    best = None
    _, x0, y0 = coarse
    for y in range(max(0, y0 - step), min(H - rh, y0 + step) + 1):
        for x in range(max(0, x0 - step), min(W - rw, x0 + step) + 1):
            s = ncc(er, eR[y:y + rh, x:x + rw])
            if best is None or s > best[0]: best = (s, x, y)
    return best


def live_value_mask(texts, size, px_per_window_dp):
    """Boolean mask (True = excluded) over the resampled render for drawn texts that show live values."""
    mask = Image.new('1', size, 0)
    d = ImageDraw.Draw(mask)
    m = MASK_MARGIN_DP * px_per_window_dp
    for t in texts:
        if not LIVE_VALUE.search(t['text']):
            continue
        l, top, r, b = (v * px_per_window_dp for v in t['boxDp'])
        d.rectangle([l - m, top - m, r + m, b + m], fill=1)
    return np.asarray(mask, dtype=bool)


def slot_metrics(a, b, ea, eb, keep=None):
    """Mean absolute colour difference and edge correlation over the pixels in [keep] (all when None)."""
    if keep is None:
        return round(float(np.abs(a - b).mean()), 1), round(ncc(ea, eb), 3)
    if keep.sum() < 64:
        return None, None
    return round(float(np.abs(a - b)[keep].mean()), 1), round(ncc(ea[keep], eb[keep]), 3)


def compare(render, reference, kind, f, offset=None, texts=None, scale=1.0):
    """Report dict and the aligned (resampled render, reference region, difference) images."""
    boxes = WIDE if kind == 'wide' else PHONE
    resized = render.resize((round(render.width * f), round(render.height * f)), Image.LANCZOS)
    if resized.width > reference.width or resized.height > reference.height:
        resized = resized.crop((0, 0, min(resized.width, reference.width), min(resized.height, reference.height)))
    if offset is None:
        score, ox, oy = locate(resized, reference)
        registration = 'searched'
    else:
        ox, oy = offset
        registration = 'frozen'
    region = reference.crop((ox, oy, ox + resized.width, oy + resized.height))
    a, b = np.asarray(resized, dtype=np.float32), np.asarray(region, dtype=np.float32)
    ea, eb = edges(resized), edges(region)
    k = PX_PER_DP[kind]
    blur = ImageFilter.GaussianBlur(LOW_FREQ_DP * k)
    la, lb = np.asarray(resized.filter(blur), dtype=np.float32), np.asarray(region.filter(blur), dtype=np.float32)
    score = ncc(ea, eb)
    diff = np.abs(a - b).mean(axis=2)
    excluded = live_value_mask(texts, resized.size, k / scale) if texts is not None else None
    slots = {}
    for name, (l, t, r, btm) in boxes.items():
        x0, y0, x1, y1 = (int(v * k) for v in (l, t, r, btm))
        x1 = min(x1, resized.width); y1 = min(y1, resized.height)
        if x1 - x0 < 8 or y1 - y0 < 8:
            continue
        sa, sb, sea, seb = a[y0:y1, x0:x1], b[y0:y1, x0:x1], ea[y0:y1, x0:x1], eb[y0:y1, x0:x1]
        mad, edge = slot_metrics(sa, sb, sea, seb)
        slots[name] = {'meanAbsDiff': mad, 'edgeCorrelation': edge,
                       'meanAbsDiffLowFreq': round(float(np.abs(la[y0:y1, x0:x1] - lb[y0:y1, x0:x1]).mean()), 1)}
        if excluded is not None:
            keep = ~excluded[y0:y1, x0:x1]
            mmad, medge = slot_metrics(sa, sb, sea, seb, keep)
            slots[name].update({'meanAbsDiffMasked': mmad, 'edgeCorrelationMasked': medge,
                                'maskedFraction': round(float(1 - keep.mean()), 3)})
    report = {'kind': kind, 'scaleToReference': round(f, 4), 'registration': registration, 'offsetPx': [ox, oy],
              'edgeCorrelation': round(score, 3), 'meanAbsDiff': round(float(diff.mean()), 1),
              'meanAbsDiffLowFreq': round(float(np.abs(la - lb).mean()), 1), 'slots': slots}
    if excluded is not None:
        keep = ~excluded
        report['meanAbsDiffMasked'] = round(float(diff[keep].mean()), 1)
        report['edgeCorrelationMasked'] = round(ncc(ea[keep], eb[keep]), 3)
        report['maskedFraction'] = round(float(excluded.mean()), 4)
    return report, resized, region, diff, excluded


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
    render = Image.open(render_path).convert('RGB')
    if top_inset and kind == 'phone':
        render = render.crop((0, top_inset, render.width, render.height))   # phone reference origin: below the status bar
    reference = Image.open(ref_path).convert('RGB')
    registration_path = Path(opt['registration']) if 'registration' in opt else ref_path.parent / 'registration.json'
    registrations = json.loads(registration_path.read_text()) if registration_path.exists() else {}
    frozen = registrations.get(kind, {}).get('offsetPx')
    texts = json.loads(Path(opt['texts']).read_text()) if 'texts' in opt else None
    f = PX_PER_DP[kind] / (density * scale)
    report, resized, region, diff, excluded = compare(render, reference, kind, f,
                                                      None if ('search' in opt or frozen is None) else tuple(frozen), texts, scale)
    if 'freeze' in opt:
        registrations[kind] = {'offsetPx': report['offsetPx'], 'reference': ref_path.name,
                               'fullResolutionEdgeCorrelation': report['edgeCorrelation'],
                               'determinedFrom': render_path.name}
        registration_path.write_text(json.dumps(registrations, indent=1) + '\n')
    out.mkdir(parents=True, exist_ok=True)
    heat = Image.fromarray(np.clip(diff * 2, 0, 255).astype(np.uint8)).convert('RGB')
    if excluded is not None:
        heat.paste((40, 40, 90), mask=Image.fromarray((excluded * 255).astype(np.uint8)))
    Image.blend(region, resized, .5).save(out / 'overlay.png')
    heat.save(out / 'difference.png')
    side = Image.new('RGB', (resized.width * 2 + 10, resized.height), (0, 0, 0))
    side.paste(region, (0, 0)); side.paste(resized, (resized.width + 10, 0)); side.save(out / 'side-by-side.png')
    k = PX_PER_DP[kind]
    marked = region.copy(); d = ImageDraw.Draw(marked)
    for name, (l, t, r, btm) in (WIDE if wide else PHONE).items():
        d.rectangle([l * k, t * k, r * k, btm * k], outline=(255, 80, 80)); d.text((l * k + 3, t * k + 3), name, fill=(255, 80, 80))
    marked.save(out / 'reference-slots.png')
    (out / 'report.json').write_text(json.dumps(report, indent=1))
    print(json.dumps(report, indent=1))


if __name__ == '__main__':
    main()
