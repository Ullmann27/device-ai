"""Annotate actual mesh projections and assemble untouched GLB render evidence."""
import pathlib,json,hashlib
from PIL import Image,ImageDraw,ImageFont
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/avatar-live/geometry-work/runtime-anchors'
manifest=json.loads((OUT/'runtime-anchor-manifest.json').read_text())
assert len(manifest['records'])==43
fontpath='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font=ImageFont.truetype(fontpath,13);small=ImageFont.truetype(fontpath,10)
points=json.loads((OUT/'landmarks.json').read_text())['points']
annotated=Image.open(OUT/'02-midline-landmarks.png').convert('RGB');d=ImageDraw.Draw(annotated)
start=points['Midline top']['pixel'];end=points['Midline bottom']['pixel'];d.line([tuple(start),tuple(end)],fill=(255,222,110),width=1)
for i,(name,record) in enumerate(points.items()):
    if name.startswith('Midline'):continue
    x,y=record['pixel'];d.ellipse((x-3,y-3,x+3,y+3),outline=(255,222,110),width=1)
    tx=6 if x<256 else 365;ty=55+i*20
    d.line([(x,y),(tx+70,ty+5)],fill=(165,150,80),width=1);d.text((tx,ty),name,font=small,fill=(255,230,150))
annotated.save(OUT/'02-midline-landmarks-annotated.png')
updown=Image.new('RGB',(512,512),(4,6,15));d=ImageDraw.Draw(updown)
for i,name in enumerate(['20a-eyes-up.png','20b-eyes-down.png']):
    im=Image.open(OUT/name).convert('RGB');im.thumbnail((256,256));updown.paste(im,(256*i,110));d.text((256*i+12,380),'eyes up'if i==0 else'eyes down',font=font,fill=(205,222,245))
updown.save(OUT/'20-eyes-up-down-panel.png')
anchors=[]
for n in range(1,31):
    if n==2:p=OUT/'02-midline-landmarks-annotated.png'
    elif n==20:p=OUT/'20-eyes-up-down-panel.png'
    else:p=next(OUT.glob('%02d-*.png'%n))
    anchors.append(p)
sheet=Image.new('RGB',(1920,1740),(4,6,15));draw=ImageDraw.Draw(sheet)
draw.text((12,12),'EXACT RUNTIME GLB f39a89fc · 30 semantic anchors · 512px research evidence · appearance NOT ACCEPTED',font=font,fill=(214,229,249))
for i,p in enumerate(anchors):
    im=Image.open(p).convert('RGB');im.thumbnail((320,320));x=(i%6)*320;y=40+(i//6)*340;sheet.paste(im,(x,y));draw.text((x+6,y+320),p.stem.replace('-',' '),font=font,fill=(182,203,227))
sheet.save(OUT/'semantic-contact-sheet.png')
for record in manifest['records']:
    p=OUT/record['file'];record['sha256']=hashlib.sha256(p.read_bytes()).hexdigest();record['bytes']=p.stat().st_size
manifest['annotations']={'landmarks':'02-midline-landmarks-annotated.png','upDownPanel':'20-eyes-up-down-panel.png','contactSheet':'semantic-contact-sheet.png','referenceImageProcessed':False}
manifest['artifacts']=[{'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size}for p in sorted(OUT.glob('*.png'))]
(OUT/'runtime-anchor-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
sequence=[]
for p in sorted(OUT.glob('orbit-*.png')):sequence.append((p.name,.25))
for name,duration in [('01-neutral.png',.35),('17-half-blink.png',.2),('16-eyes-closed.png',.25),('17-half-blink.png',.2),('01-neutral.png',.3),('11-subtle-smile.png',.4),('12-broader-smile.png',.45),('22-PP.png',.35),('23-FF.png',.35),('24-AA.png',.4),('25-E.png',.35),('26-I.png',.35),('27-O.png',.4),('28-U.png',.4),('30-smiling-speech.png',.65)]:sequence.append((name,duration))
text=[]
for name,duration in sequence:text.extend(["file '"+name+"'",'duration '+str(duration)])
text.append("file '"+sequence[-1][0]+"'")
(OUT/'proof-sequence.ffconcat').write_text('\n'.join(text)+'\n')
print('RUNTIME_PACKAGE_COMPLETE',len(anchors),'semantic anchors;',len(manifest['records']),'actual renders')
