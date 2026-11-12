"""Composite source renders; explicitly no Android/device claim."""
import json
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

root, source, target = map(Path, sys.argv[1:])
target.mkdir(parents=True, exist_ok=True)
data = json.loads((source / 'host-render-geometry.json').read_text())
font = ImageFont.truetype(str(root / 'app/src/main/assets/fonts/Roboto-Medium.ttf'), 24)
small = ImageFont.truetype(str(root / 'app/src/main/assets/fonts/Roboto-Regular.ttf'), 19)

for record in data['records']:
    name, g = record['name'], record['layout']
    w, h = round(g['width'] * 2), round(g['height'] * 2)
    stage = Image.new('RGBA', (w, h), (3, 7, 18, 255))

    def layer(filename, bounds, avatar=False):
        x, y, width, height = bounds
        pixels = Image.open(source / filename).convert('RGBA').resize(
            (round(width * 2), round(height * 2)), Image.Resampling.LANCZOS)
        if avatar:
            rgba = np.asarray(pixels).copy()
            ph, pw = rgba.shape[:2]
            yy, xx = np.mgrid[:ph, :pw]
            bottom = np.clip((ph - yy) / (ph * .26), 0, 1)
            side = np.minimum(np.clip(xx / (pw * .09), 0, 1),
                              np.clip((pw - xx) / (pw * .09), 0, 1))
            side = np.where(yy < ph * .6, 1, side)
            rgba[:, :, 3] = (rgba[:, :, 3] * bottom * side * data['avatarOpacity']).astype('uint8')
            pixels = Image.fromarray(rgba)
        stage.alpha_composite(pixels, (round(x * 2), round(y * 2)))

    layer('original-orb-source.png', g['orb'])
    layer(name + '-avatar.png', g['head'], avatar=True)
    stage.alpha_composite(Image.open(source / (name + '-room.png')))
    proof = Image.new('RGB', (w, h + 106), (3, 7, 18))
    proof.paste(stage, (0, 0))
    ink = ImageDraw.Draw(proof)
    ink.line((18, h + 9, w - 18, h + 9), fill=(53, 89, 112), width=1)
    ink.text((22, h + 23), f'HOST-RENDER · Original-GLB · Layout {g["fullWidth"]} × {g["fullHeight"]} dp',
             fill=(204, 226, 240), font=font)
    ink.text((22, h + 61), 'Szeneausschnitt · keine Live-Aufnahme · kein Android-/Fold7-Nachweis',
             fill=(159, 177, 196), font=small)
    proof.save(target / (name + '-host-scene.png'), optimize=True)

(target / 'host-render-geometry.json').write_text(json.dumps(data, indent=2) + '\n')
