"""Package untouched Blender render frames into an annotated review sheet.

This never reads or modifies the binding 2D portrait reference.
"""
import pathlib, json, hashlib
from PIL import Image, ImageDraw, ImageFont
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/avatar-live/geometry-work'
log=(OUT/'build-final-review-30views.log').read_text()
assert log.count('RENDER_COMPLETE view-')==30, 'Wait for all 30 final same-geometry renders'
manifest=json.loads((OUT/'asset-manifest.json').read_text())
paths=sorted(OUT.glob('view-*.png'))
assert len(paths)==30
fontpath='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font=ImageFont.truetype(fontpath,13)
canvas=Image.new('RGB',(1920,1740),(5,7,17));draw=ImageDraw.Draw(canvas)
draw.text((18,12),'ONE CANONICAL 3D BUST · 24 orbit views + 6 expressions · research candidate',font=font,fill=(210,226,245))
records=[]
for i,path in enumerate(paths):
    im=Image.open(path).convert('RGB');assert im.size==(512,512), (path,im.size)
    im.thumbnail((320,320));x=(i%6)*320;y=40+(i//6)*340
    canvas.paste(im,(x,y));label=path.stem.removeprefix('view-').replace('neutral-','angle ').replace('-',' ')
    draw.text((x+8,y+320),label,font=font,fill=(175,194,218))
    records.append({'file':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size,'pixels':[512,512]})
canvas.save(OUT/'contact-sheet-30.png')
report=json.loads((OUT/'render-manifest.json').read_text())
report.update(contactSheet='contact-sheet-30.png',frames=records,referenceImageProcessed=False)
(OUT/'render-manifest.json').write_text(json.dumps(report,indent=2)+'\n')
print('CONTACT_SHEET_COMPLETE',len(records))
