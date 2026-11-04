#!/usr/bin/env python3
"""Arrange exact host render frames with factual labels; no image content edits."""
from pathlib import Path
import json
import hashlib
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'renders'
manifest=json.loads((out/'render-manifest.json').read_text())
fontfile='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font=ImageFont.truetype(fontfile,22);small=ImageFont.truetype(fontfile,18)
w,h=512,576;pad=16;header=90;footer=44;cellh=h+40
sheet=Image.new('RGB',(3*w+4*pad,header+2*cellh+3*pad+footer),(4,7,17));draw=ImageDraw.Draw(sheet)
draw.text((pad,12),'ECHTER GLB | BLENDER CPU | HOST, KEIN ANDROID-BELEG',font=font,fill=(180,235,250))
draw.text((pad,45),'Gleiches Modell, Material, Licht und Kamera. Mundformen aus #491 / #503. Keine neue Gesichtsform.',font=small,fill=(180,190,215))
for i,record in enumerate(manifest['poses']):
    x=pad+(i%3)*(w+pad);y=header+pad+(i//3)*(cellh+pad)
    sheet.paste(Image.open(ROOT/record['file']).convert('RGB'),(x,y))
    draw.text((x+8,y+h+7),record['label'],font=font,fill=(195,230,245))
draw.text((pad,sheet.height-32),'Geometrie-/Morphvergleich; keine Aussage zu Fold7-FPS, Lippensynchronität oder endgültiger Referenztreue.',font=small,fill=(180,190,215))
sheet.save(out/'contact-sheet.png')
sheet.save(out/'contact-sheet.jpg',format='JPEG',quality=85)
files=[]
for name in ['contact-sheet.png','contact-sheet.jpg']:
    path=out/name
    files.append({'file':str(path.relative_to(ROOT)),'bytes':path.stat().st_size,
        'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
        'encoding':'PNG original technical montage' if path.suffix=='.png' else 'JPEG quality 85; same montage, lossy review copy'})
(out/'contact-sheet-manifest.json').write_text(json.dumps({'evidenceClass':'HOST_RENDER_NOT_DEVICE',
    'pixels':list(sheet.size),'sameMontageNoRedesign':True,'jpegQuality':85,'files':files},indent=2)+'\n')
print(out/'contact-sheet.png')
