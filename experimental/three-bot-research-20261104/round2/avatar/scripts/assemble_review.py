#!/usr/bin/env python3
"""Label original renders; create compact review copies without redesigning their content."""
from pathlib import Path
import argparse
import json
import hashlib
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'renders'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--reference',type=Path,default=ROOT.parents[1]/'avatar/references/ref-01-head-frontal.jpeg')
args=parser.parse_args()
manifest=json.loads((ROOT/'results/render-manifest-all.json').read_text())
fontfile='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font=ImageFont.truetype(fontfile,22);small=ImageFont.truetype(fontfile,18)
variants=['original','v1_soft_flow','v2_fine_flow'];poses=['neutral','speech','blink']
labels=['Original GLB','V1 | ruhige Konturen','V2 | feinere Konturen']
w,h,pad,header,cellfooter=512,576,16,112,38
sheet=Image.new('RGB',(3*w+4*pad,header+3*(h+cellfooter+pad)+42),(4,7,17));d=ImageDraw.Draw(sheet)
d.text((pad,12),'LOKALE MATERIALVARIANTEN | ECHTER GLB | HOST, KEIN ANDROID-BELEG',font=font,fill=(185,235,250))
d.text((pad,47),'Derselbe Kopf, Rig und 21 Morphs. Gleiche Kamera, Beleuchtung, Belichtung und Posen.',font=small,fill=(180,190,215))
frames={(f['variant'],f['pose']):f for f in manifest['frames']}
for col,label in enumerate(labels):d.text((pad+col*(w+pad)+8,80),label,font=font,fill=(205,225,245))
for row,pose in enumerate(poses):
    for col,variant in enumerate(variants):
        rec=frames[(variant,pose)];x=pad+col*(w+pad);y=header+row*(h+cellfooter+pad)
        sheet.paste(Image.open(ROOT/rec['file']).convert('RGB'),(x,y))
        d.text((x+8,y+h+7),rec['poseLabel'],font=font,fill=(195,230,245))
d.text((pad,sheet.height-30),'Ausschliesslich Material/Atlas. Originale Geometriebytes unveraendert. Keine abschliessende Referenzabnahme.',font=small,fill=(180,190,215))
sheet.save(out/'variant-contact.png')
preview=sheet.resize((1200,round(sheet.height*1200/sheet.width)),Image.Resampling.LANCZOS)
preview.save(out/'variant-contact.jpg',quality=85)
# Reference has another camera/lighting. It is displayed as a distinct input, not as a controlled render.
ref=args.reference
compare=Image.new('RGB',(4*400+5*pad,566),(4,7,17));draw=ImageDraw.Draw(compare)
draw.text((pad,12),'ORIGINALREFERENZ UND NEUTRALE HOSTVARIANTEN',font=font,fill=(185,235,250))
draw.text((pad,45),'Referenz besitzt andere Kamera/Licht. Kein Aehnlichkeits-Prozentwert und kein neues Gesicht.',font=small,fill=(180,190,215))
for col,(path,label) in enumerate([(ref,'Referenz Ref-01 (anderes Licht)')]+[(ROOT/frames[(v,'neutral')]['file'],l) for v,l in zip(variants,labels)]):
    img=Image.open(path).convert('RGB');img.thumbnail((400,450),Image.Resampling.LANCZOS)
    x=pad+col*(400+pad)+(400-img.width)//2;y=90+(450-img.height)//2
    compare.paste(img,(x,y));draw.text((pad+col*(400+pad)+3,540),label,font=small,fill=(200,225,245))
compare.save(out/'reference-neutral-comparison.png')
compare.resize((1260,425),Image.Resampling.LANCZOS).save(out/'reference-neutral-comparison.jpg',quality=85)
files=[]
for name in ['variant-contact.png','variant-contact.jpg','reference-neutral-comparison.png','reference-neutral-comparison.jpg']:
    p=out/name
    with Image.open(p) as im:size=list(im.size)
    files.append({'file':str(p.relative_to(ROOT)),'bytes':p.stat().st_size,'pixels':size,
        'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'jpegQuality':85 if p.suffix=='.jpg' else None})
(ROOT/'results/review-image-manifest.json').write_text(json.dumps({'files':files,'evidenceClass':'HOST_RENDER_NOT_DEVICE'},indent=2)+'\n')
print(json.dumps(files,indent=2))
