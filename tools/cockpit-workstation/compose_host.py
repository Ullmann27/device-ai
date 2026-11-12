"""Compose exact source renders and labelled examples."""
import json
import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFont


def example_plane(work, frame, layout, size):
    source = Image.open(work / f'{frame["key"]}-example.png').convert('RGBA')
    points = np.asarray(frame['frame']['quad'], dtype=float).reshape(4, 2)
    points += (layout['scene']['left'], layout['scene']['top'])
    targets = ((0, 0), (source.width, 0), (source.width, source.height), (0, source.height))
    rows, values = [], []
    for (x, y), (u, v) in zip(points, targets):
        rows.extend(((x, y, 1, 0, 0, 0, -u*x, -u*y),
                     (0, 0, 0, x, y, 1, -v*x, -v*y)))
        values.extend((u, v))
    transform = np.linalg.solve(np.asarray(rows), np.asarray(values))
    return source.transform(size, Image.Transform.PERSPECTIVE, transform,
                            resample=Image.Resampling.BICUBIC)


def compose(work, out, exported, layer_depths):
    layouts = {name: json.loads((work / f'{name}-layout.json').read_text()) for name in ('fold', 'phone')}
    for frame in exported['frames']:
        name, key = frame['layout'], frame['key']
        layout = layouts[name]
        result = Image.open(work / f'{name}-background.png').convert('RGBA')
        result.alpha_composite(Image.open(work / f'{key}-orb.png').convert('RGBA'))
        avatar = Image.open(work / f'{name}-avatar.png').convert('RGBA')
        mask = Image.open(work / f'{name}-head-mask.png').getchannel('A')
        avatar.putalpha(ImageChops.multiply(avatar.getchannel('A'), mask))
        result.alpha_composite(avatar, (int(layout['head']['left']), int(layout['head']['top'])))
        result.alpha_composite(Image.open(work / f'{name}-head-front.png').convert('RGBA'))
        for layer in sorted(layer_depths, key=layer_depths.get):
            if layer == 'foreground':
                result.alpha_composite(Image.open(work / f'{name}-foreground.png').convert('RGBA'))
            elif layer == 'stage':
                result.alpha_composite(Image.open(work / f'{key}-stage.png').convert('RGBA'),
                                       (int(layout['scene']['left']), int(layout['scene']['top'])))
            elif exported['contentMode'] == 'example':
                result.alpha_composite(example_plane(work, frame, layout, result.size))
        footer = Image.new('RGB', (result.width, result.height + 70), '#040816')
        footer.paste(result, (0, 0), result)
        draw = ImageDraw.Draw(footer)
        font = ImageFont.load_default(size=12)
        draw.text((10, result.height + 9), f'HOSTRENDER | {name.upper()} | {frame["phase"]}',
                  font=font, fill='#8deeff')
        draw.text((10, result.height + 29), 'Originale Assets + Kotlin-Geometrie | Blender + Java2D',
                  font=font, fill='#d9e4ef')
        content_label = 'VORSCHAU · BEISPIELINHALT' if exported['contentMode'] == 'example' else 'Monitor ohne Livebild'
        draw.text((10, result.height + 49), content_label + ' | kein Gerätebeleg',
                  font=font, fill='#c2abc8')
        target = work if frame['phase'].startswith('sequence-') else out
        footer.save(target / f'{key}.png')


if __name__ == "__main__":
    raise SystemExit("Use preview.py to preserve source binding.")
