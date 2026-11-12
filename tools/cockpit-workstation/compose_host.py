"""Composite unified render with production chrome."""
import json
from PIL import Image, ImageDraw, ImageFont


def compose(work, out, exported, layer_depths):
    names = {frame['layout'] for frame in exported['frames']}
    layouts = {name: json.loads((work / f'{name}-layout.json').read_text()) for name in names}
    for frame in exported['frames']:
        name, key = frame['layout'], frame['key']
        layout = layouts[name]
        result = Image.open(work / f'{name}-background.png').convert('RGBA')
        for layer in sorted(layer_depths, key=layer_depths.get):
            if layer == 'foreground':
                result.alpha_composite(Image.open(work / f'{name}-foreground.png').convert('RGBA'))
            else:
                result.alpha_composite(Image.open(work / f'{key}-stage.png').convert('RGBA'),
                                       (int(layout['scene']['left']), int(layout['scene']['top'])))
        footer = Image.new('RGB', (result.width, result.height + 70), '#040816')
        footer.paste(result, (0, 0), result)
        draw = ImageDraw.Draw(footer)
        font = ImageFont.load_default(size=12)
        draw.text((10, result.height + 9), f'HOSTRENDER | UNIFIED 3D | {name.upper()} | {frame["phase"]}',
                  font=font, fill='#8deeff')
        draw.text((10, result.height + 29), 'Produktionsassets + Kotlin-Matrizen | Blender + Java2D',
                  font=font, fill='#d9e4ef')
        content = 'VORSCHAU · BEISPIELINHALT' if exported['contentMode'] == 'example' else 'Monitor ohne Livebild'
        draw.text((10, result.height + 49), content + ' | kein Gerätebeleg', font=font, fill='#c2abc8')
        target = work if frame['phase'].startswith('sequence-') else out
        footer.save(target / f'{key}.png')


if __name__ == '__main__':
    raise SystemExit('Use preview.py to preserve source binding.')
