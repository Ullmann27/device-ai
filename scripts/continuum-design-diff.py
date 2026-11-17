#!/usr/bin/env python3
"""Reference-bound visual QA, not a perceptual-quality or device acceptance score.

Existing render/reference/outDir and --metrics / --density --scale --wide usage
is retained. Freeze --offset-x/--offset-y once, then use --alignment BASE/report.json
for candidates. Automatic alignment is exploratory and candidate-dependent.
Regions describe the frozen 2026-10-22 target, never the candidate's current layout.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, __version__ as PIL_VERSION
try:
    import scipy
    from scipy.ndimage import distance_transform_edt
except ImportError:
    scipy = None
    distance_transform_edt = None

PHONE = {  # frozen target dp; reference ROI contract 2026-10-22-v1
    'BRAND': (0, 0, 276, 68.8), 'HERO': (0, 68.8, 412, 375.6), 'MODEL': (13.3, 380.3, 398.8, 469.4),
    'LEARNING': (13.3, 479.6, 215.5, 661.6), 'STORAGE': (224.8, 479.6, 398.8, 661.6), 'DEVELOPMENT': (13.3, 673.2, 398.8, 773.2),
    'NAV': (0, 787, 412, 861)}
WIDE = {  # frozen target dp; independent of candidate layout changes
    'RAIL': (0, 0, 76, 649), 'BRAND': (80, 30, 420, 86), 'HERO': (212, 0, 622, 291), 'SYSTEM_STATUS': (657.1, 84.4, 829.2, 276),
    'MODEL': (87.7, 290.9, 335.7, 475.3), 'LEARNING': (345.5, 290.9, 613, 475.3), 'STORAGE': (624.7, 290.9, 829.2, 475.3),
    'DEVELOPMENT': (87.7, 485.7, 492.9, 642.9), 'SYSTEM_ACTIVITY': (504.5, 485.7, 829.2, 642.9)}
PX_PER_DP = {'phone': 1.7306, 'wide': 1.54}


ROI_VERSION = 'continuum-2026-10-22-v1'
METRIC_DEFINITION = {'brightThreshold': 64, 'edgeThreshold': 24,
                     'luminance': 'Pillow RGB-to-L (0..255)',
                     'edgeDistance': 'symmetric mean Euclidean pixels; FIND_EDGES border excluded',
                     'edgeBackend': 'scipy' if scipy else 'UNAVAILABLE'}


def edges(img):
    g = np.asarray(img.convert('L').filter(ImageFilter.FIND_EDGES), dtype=np.float32)
    return (g - g.mean()) / (g.std() + 1e-6)


def ncc(a, b):
    a = a - a.mean()
    b = b - b.mean()
    return float((a * b).sum() / (np.sqrt((a * a).sum() * (b * b).sum()) + 1e-9))


def locate(render, reference, step=4):
    """Exploratory coarse-to-fine registration; final score is full resolution."""
    rw, rh = render.size
    W, H = reference.size
    if rw > W or rh > H or min(rw, rh, W, H) < 2 or step < 1:
        raise ValueError('render must fit entirely inside reference; minimum image dimension is 2')
    small_r = edges(render.resize((max(1, rw // step), max(1, rh // step))))
    small_R = edges(reference.resize((max(1, W // step), max(1, H // step))))
    best = (-2, 0, 0)
    for y in range(small_R.shape[0] - small_r.shape[0] + 1):
        for x in range(small_R.shape[1] - small_r.shape[1] + 1):
            score = ncc(small_r, small_R[y:y + small_r.shape[0], x:x + small_r.shape[1]])
            if score > best[0]:
                best = (score, x * step, y * step)
    _, x0, y0 = best
    x0, y0 = min(x0, W - rw), min(y0, H - rh)
    er, eR = edges(render), edges(reference)
    # Coarse scores and full-resolution scores are not comparable.
    best = (-2, x0, y0)
    for y in range(max(0, y0 - step), min(H - rh, y0 + step) + 1):
        for x in range(max(0, x0 - step), min(W - rw, x0 + step) + 1):
            score = ncc(er, eR[y:y + rh, x:x + rw])
            if score > best[0]:
                best = (score, x, y)
    return best


def shape_metrics(render, reference):
    """Fixed-threshold light occupancy and distribution; not semantic segmentation."""
    if render.size != reference.size:
        raise ValueError('shape metric images must have identical dimensions')
    a, b = (np.asarray(im.convert('L'), dtype=np.float64) for im in (render, reference))
    am, bm = a >= METRIC_DEFINITION['brightThreshold'], b >= METRIC_DEFINITION['brightThreshold']
    union = int((am | bm).sum())
    hist_a = np.bincount(a.astype(np.uint8).ravel(), minlength=256) / a.size
    hist_b = np.bincount(b.astype(np.uint8).ravel(), minlength=256) / b.size
    profiles = [np.abs(a.mean(axis=axis) - b.mean(axis=axis)).mean() / 255 for axis in (0, 1)]
    masks = []
    for im in (render, reference):
        mask = np.asarray(im.convert('L').filter(ImageFilter.FIND_EDGES)) >= METRIC_DEFINITION['edgeThreshold']
        mask[[0, -1], :] = False
        mask[:, [0, -1]] = False
        masks.append(mask)
    ae, be = masks
    distance = None
    if distance_transform_edt is not None:
        if ae.any() and be.any():
            distance = float((distance_transform_edt(~be)[ae].mean() + distance_transform_edt(~ae)[be].mean()) / 2)
        elif not ae.any() and not be.any():
            distance = 0.0
    return {'brightMaskIoU': round(float((am & bm).sum()) / union, 6) if union else None,
            'renderBrightCoverage': round(float(am.mean()), 6),
            'referenceBrightCoverage': round(float(bm.mean()), 6),
            'luminanceHistogramWasserstein': round(float(np.abs(np.cumsum(hist_a - hist_b)).sum()), 6),
            'luminanceProfileL1': round(float(np.mean(profiles)), 6),
            'edgeDistancePx': round(distance, 6) if distance is not None else None,
            'renderEdgeCoverage': round(float(ae.mean()), 6),
            'referenceEdgeCoverage': round(float(be.mean()), 6)}


def fingerprint(path, data=None):
    return {'path': str(path), 'sha256': hashlib.sha256(path.read_bytes() if data is None else data).hexdigest()}


def read_image(path):
    data = path.read_bytes()
    with Image.open(io.BytesIO(data)) as image:
        image.load()
        if min(image.size) < 2:
            raise ValueError('minimum image dimension is 2')
        return image.convert('RGB'), fingerprint(path, data)


def read_json(path):
    data = path.read_bytes()
    value = json.loads(data)
    if not isinstance(value, dict):
        raise ValueError(f'{path}: expected a JSON object')
    return value, fingerprint(path, data)


def positive_number(value, name):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
        raise ValueError(f'{name} must be a finite positive number')
    return float(value)


def snapshot_geometry(metadata, size, insets_policy):
    if metadata.get('schema') != 'continuum-ui-snapshot' or type(metadata.get('version')) is not int or metadata['version'] != 1:
        raise ValueError('unsupported snapshot schema/version')
    window = metadata.get('windowPx')
    if not isinstance(window, str) or not re.fullmatch(r'[1-9][0-9]*x[1-9][0-9]*', window):
        raise ValueError('metrics.windowPx must identify the full snapshot size')
    if tuple(map(int, window.split('x'))) != size:
        raise ValueError('metrics.windowPx does not match render dimensions (already cropped or wrong snapshot)')
    density = positive_number(metadata.get('density'), 'metrics.density')
    scale = positive_number(metadata.get('layoutScale'), 'metrics.layoutScale')
    if type(metadata.get('wideLayout')) is not bool:
        raise ValueError('metrics.wideLayout must be boolean')
    crop = [0, 0, *size]
    insets = metadata.get('insetsPx')
    if insets is not None:
        if not isinstance(insets, dict) or any(type(insets.get(key)) is not int or insets[key] < 0
                                               for key in ('left', 'top', 'right', 'bottom')):
            raise ValueError('insetsPx requires four non-negative integer sides')
        if insets['left'] + insets['right'] >= size[0] or insets['top'] + insets['bottom'] >= size[1]:
            raise ValueError('insetsPx leaves no content')
    if insets_policy == 'system-bars':
        if insets is None:
            raise ValueError('insetsPx missing; use --insets none only for verified uncropped content')
        crop = [insets['left'], insets['top'], size[0] - insets['right'], size[1] - insets['bottom']]
    return density, scale, metadata['wideLayout'], crop


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('render', type=Path)
    p.add_argument('reference', type=Path)
    p.add_argument('out', type=Path)
    p.add_argument('--metrics', type=Path)
    p.add_argument('--density', type=float)
    p.add_argument('--scale', type=float)
    p.add_argument('--wide', action='store_true')
    p.add_argument('--insets', choices=('system-bars', 'none'), help='metrics default: system-bars, all four sides')
    p.add_argument('--offset-x', type=int)
    p.add_argument('--offset-y', type=int)
    p.add_argument('--alignment', type=Path, help='reuse a v2 baseline report; requires identical geometry/reference/ROIs')
    return p


def compare(args):
    render, render_source = read_image(args.render)
    reference, reference_source = read_image(args.reference)
    sources = {'render': render_source, 'reference': reference_source, 'script': fingerprint(Path(__file__))}
    if args.metrics:
        if args.density is not None or args.scale is not None or args.wide:
            raise ValueError('--metrics cannot be combined with --density, --scale or --wide')
        metadata, sources['metrics'] = read_json(args.metrics)
        policy = args.insets or 'system-bars'
        density, scale, wide, crop = snapshot_geometry(metadata, render.size, policy)
    else:
        if args.insets == 'system-bars':
            raise ValueError('--insets system-bars requires --metrics')
        policy = 'none'
        density = positive_number(args.density if args.density is not None else 2, 'density')
        scale = positive_number(args.scale if args.scale is not None else 1, 'scale')
        wide, crop = args.wide, [0, 0, *render.size]
    kind = 'wide' if wide else 'phone'
    boxes = WIDE if wide else PHONE
    f = PX_PER_DP[kind] / (density * scale)
    target_size = [round((crop[2] - crop[0]) * f), round((crop[3] - crop[1]) * f)]
    if min(target_size) < 2 or target_size[0] > reference.width or target_size[1] > reference.height:
        raise ValueError(f'rescaled image {target_size} must fit reference {reference.size}; no automatic clipping')
    geometry = {'sourceSizePx': list(render.size), 'cropBoxPx': crop, 'insetsPolicy': policy,
                'resizedSizePx': target_size, 'referenceSizePx': list(reference.size),
                'density': density, 'layoutScale': scale, 'scaleToReference': f}
    roi = {'version': ROI_VERSION, 'coordinateSystem': 'render origin in reference pixels',
           'pixelsPerDp': PX_PER_DP[kind], 'boxesDp': {key: list(box) for key, box in boxes.items()}}
    resized = render.crop(crop).resize(target_size, Image.Resampling.LANCZOS)
    if (args.offset_x is None) != (args.offset_y is None):
        raise ValueError('--offset-x and --offset-y must be supplied together')
    if args.alignment and args.offset_x is not None:
        raise ValueError('--alignment cannot be combined with offsets')
    if args.alignment:
        baseline, sources['alignment'] = read_json(args.alignment)
        if baseline.get('schemaVersion') != 2 or baseline.get('kind') != kind:
            raise ValueError('alignment schema/kind mismatch')
        if baseline.get('geometry') != geometry or baseline.get('roiContract') != roi:
            raise ValueError('alignment geometry/ROI mismatch')
        if baseline.get('metricDefinition') != METRIC_DEFINITION:
            raise ValueError('alignment metric definition/backend mismatch')
        for key in ('reference', 'script'):
            if baseline.get('sources', {}).get(key, {}).get('sha256') != sources[key]['sha256']:
                raise ValueError(f'alignment {key} hash mismatch')
        offset = baseline.get('offsetPx')
        if not isinstance(offset, list) or len(offset) != 2 or any(type(v) is not int for v in offset):
            raise ValueError('alignment offsetPx requires two integers')
        ox, oy = offset
        mode = 'replay'
    elif args.offset_x is not None:
        ox, oy, mode = args.offset_x, args.offset_y, 'fixed'
    else:
        _, ox, oy = locate(resized, reference)
        mode = 'auto-exploratory'
    if ox < 0 or oy < 0 or ox + resized.width > reference.width or oy + resized.height > reference.height:
        raise ValueError('offset places render outside reference; no padding or clipping allowed')
    region = reference.crop((ox, oy, ox + resized.width, oy + resized.height))
    a, b = np.asarray(resized, dtype=np.float32), np.asarray(region, dtype=np.float32)
    delta = np.abs(a - b).mean(axis=2)
    slots = {}
    marked = region.copy()
    drawing = ImageDraw.Draw(marked)
    for name, box in boxes.items():
        bounds = [int(v * PX_PER_DP[kind]) for v in box]
        x0, y0, x1, y1 = bounds
        if x1 > resized.width or y1 > resized.height:
            slots[name] = {'status': 'OUTSIDE_RENDER', 'boundsPx': bounds}
            continue
        ra, rb = resized.crop(bounds), region.crop(bounds)
        slots[name] = {'status': 'MEASURED', 'boundsPx': bounds,
                       'meanAbsDiff': round(float(np.abs(a[y0:y1, x0:x1] - b[y0:y1, x0:x1]).mean()), 1),
                       'edgeCorrelation': round(ncc(edges(ra), edges(rb)), 6), **shape_metrics(ra, rb)}
        drawing.rectangle(bounds, outline=(255, 80, 80))
        drawing.text((x0 + 3, y0 + 3), name, fill=(255, 80, 80))
    report = {'schemaVersion': 2, 'kind': kind, 'scaleToReference': round(f, 4), 'offsetPx': [ox, oy],
              'alignment': {'mode': mode, 'candidateDependent': mode == 'auto-exploratory'},
              'geometry': geometry, 'roiContract': roi, 'sources': sources,
              'metricDefinition': METRIC_DEFINITION,
              'tools': {'python': sys.version.split()[0], 'numpy': np.__version__, 'Pillow': PIL_VERSION,
                        'scipy': scipy.__version__ if scipy else 'UNAVAILABLE'},
              'edgeCorrelation': round(ncc(edges(resized), edges(region)), 6),
              'meanAbsDiff': round(float(delta.mean()), 1), **shape_metrics(resized, region), 'slots': slots}
    args.out.mkdir(parents=True, exist_ok=True)
    Image.blend(region, resized, .5).save(args.out / 'overlay.png')
    Image.fromarray(np.clip(delta * 2, 0, 255).astype(np.uint8)).convert('RGB').save(args.out / 'difference.png')
    side = Image.new('RGB', (resized.width * 2 + 10, resized.height))
    side.paste(region, (0, 0))
    side.paste(resized, (resized.width + 10, 0))
    side.save(args.out / 'side-by-side.png')
    marked.save(args.out / 'reference-slots.png')
    (args.out / 'report.json').write_text(json.dumps(report, indent=1, allow_nan=False) + '\n')
    return report


def main():
    p = parser()
    args = p.parse_args()
    try:
        report = compare(args)
    except (ValueError, OSError, KeyError, TypeError, OverflowError) as error:
        p.error(str(error))
    print(json.dumps(report, indent=1, allow_nan=False))


if __name__ == '__main__':
    main()
