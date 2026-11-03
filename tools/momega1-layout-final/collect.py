#!/usr/bin/env python3
"""Copy unmodified host captures and bind their hashes; no image generation/editing."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('captures', type=Path)
p.add_argument('output', type=Path)
p.add_argument('--source', required=True)
a = p.parse_args()
if len(a.source) != 40 or any(c not in '0123456789abcdef' for c in a.source):
    p.error('Full immutable source SHA is required')
root = Path(__file__).resolve().parents[2]
a.output.mkdir(parents=True, exist_ok=True)
sha = lambda f: hashlib.sha256(f.read_bytes()).hexdigest()
images = []
for label, size in [('phone-412x915', (412, 915)), ('fold-landscape-840x649', (840, 649)), ('fold-portrait-840x1050', (840, 1050))]:
    for area in ['home', 'chat']:
        name = f'{label}-{area}-orb-host'
        source = a.captures / (name + '.png')
        with Image.open(source) as image:
            if image.size != size:
                raise SystemExit(f'Unexpected image size: {name} {image.size}')
            colors = image.convert('RGB').getextrema()
            if all(lo == hi for lo, hi in colors):
                raise SystemExit(f'Blank image: {name}')
        target = a.output / source.name
        shutil.copyfile(source, target)
        if sha(source) != sha(target):
            raise SystemExit('Capture copy changed bytes')
        views = a.captures / (name + '.views.txt')
        shutil.copyfile(views, a.output / views.name)
        images.append({'file': source.name, 'width': size[0], 'height': size[1], 'sha256': sha(target),
                       'viewTreeSha256': sha(views), 'renderer': 'Robolectric API28 native Canvas, default orb',
                       'deviceCapture': False, 'filamentCapture': False})
refs = [{'file': f'docs/design_targets/continuum-2026-10-22/reference-{name}.png',
         'sha256': sha(root / f'docs/design_targets/continuum-2026-10-22/reference-{name}.png')}
        for name in ['phone', 'fold']]
fixture = root / 'tools/momega1-layout-final/ui/IndependentFullLayoutUiTest.kt'
(a.output / 'CAPTURES.json').write_text(json.dumps({'sourceCommit': a.source,
    'fixtureSha256': sha(fixture), 'classification': 'HOST_NATIVE_LAYOUT_CAPTURE',
    'visualApproval': 'PENDING_REVIEW', 'fold7': 'FOLD7_NOT_RUN',
    'images': images, 'references': refs}, indent=2) + '\n')
print(json.dumps({'imagesCopiedUnchanged': len(images), 'sourceCommit': a.source}))
