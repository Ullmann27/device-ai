"""Deterministic original mesh; no downloaded assets."""
import json, math, struct, hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
blob=bytearray(); views=[]; access=[]; meshes=[]; nodes=[]
def data(vals,components,kind):
 while len(blob)%4: blob.append(0)
 off=len(blob)
 if kind==5126: blob.extend(struct.pack('<'+'f'*len(vals),*vals))
 else: blob.extend(struct.pack('<'+'H'*len(vals),*vals))
 vi=len(views);views.append({'buffer':0,'byteOffset':off,'byteLength':len(blob)-off})
 ai=len(access);a={'bufferView':vi,'componentType':kind,'count':len(vals)//components,'type':{1:'SCALAR',3:'VEC3'}[components]}
 if components==3:
  a['min']=[min(vals[c::3]) for c in range(3)];a['max']=[max(vals[c::3]) for c in range(3)]
 access.append(a);return ai

def cube():
 pos=[];norm=[];ind=[]
 faces=[((0,0,1),[(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]),((0,0,-1),[(1,-1,-1),(-1,-1,-1),(-1,1,-1),(1,1,-1)]),((1,0,0),[(1,-1,1),(1,-1,-1),(1,1,-1),(1,1,1)]),((-1,0,0),[(-1,-1,-1),(-1,-1,1),(-1,1,1),(-1,1,-1)]),((0,1,0),[(-1,1,1),(1,1,1),(1,1,-1),(-1,1,-1)]),((0,-1,0),[(-1,-1,-1),(1,-1,-1),(1,-1,1),(-1,-1,1)])]
 for normal,corners in faces:
  start=len(pos)//3
  for v in corners: pos.extend(t*.5 for t in v);norm.extend(normal)
  ind.extend([start,start+1,start+2,start,start+2,start+3])
 return data(pos,3,5126),data(norm,3,5126),data(ind,1,5123)
a,n,i=cube()
materials=[
 {'name':'smoked-metal','pbrMetallicRoughness':{'baseColorFactor':[.022,.035,.06,1],'metallicFactor':.72,'roughnessFactor':.24}},
 {'name':'bevel-silver','pbrMetallicRoughness':{'baseColorFactor':[.12,.19,.27,1],'metallicFactor':.85,'roughnessFactor':.19}},
 {'name':'cyan-rim','pbrMetallicRoughness':{'baseColorFactor':[.04,.57,.8,1],'metallicFactor':.2,'roughnessFactor':.35},'emissiveFactor':[.02,.6,.95]},
 {'name':'violet-rim','pbrMetallicRoughness':{'baseColorFactor':[.32,.10,.8,1],'metallicFactor':.2,'roughnessFactor':.35},'emissiveFactor':[.32,.05,.75]},
 {'name':'opaque-screen','pbrMetallicRoughness':{'baseColorFactor':[.002,.004,.009,1],'metallicFactor':0,'roughnessFactor':1}},
 {'name':'smoked-glass','alphaMode':'BLEND','doubleSided':True,'pbrMetallicRoughness':{'baseColorFactor':[.04,.11,.19,.20],'metallicFactor':.1,'roughnessFactor':.16}}
]
for mat in range(len(materials)):meshes.append({'primitives':[{'attributes':{'POSITION':a,'NORMAL':n},'indices':i,'material':mat}]})
def part(name,xyz,scale,mat,group):
 node={'name':name,'mesh':mat,'translation':xyz,'scale':scale};idx=len(nodes);nodes.append(node);group.append(idx)
room=[];monitor=[]
# Architectural depth, open central volume.
for side in [-1,1]:
 for k in range(3):
  x=side*(.45-k*.028);z=-.24-k*.11
  part(f'arch-{side}-{k}',[x,.09,z],[.014,.76,.023],0,room)
  part(f'arch-light-{side}-{k}',[x-side*.008,.09,z+.013],[.0025,.68,.006],2 if side<0 else 3,room)
 part(f'ceiling-{side}',[side*.35,.46,-.40],[.26,.012,.035],1,room)
 part(f'ceiling-line-{side}',[side*.35,.45,-.38],[.24,.002,.006],2 if side<0 else 3,room)
 part(f'wing-{side}',[side*.36,-.28,-.03],[.22,.012,.30],5,room)
 part(f'wing-edge-{side}',[side*.36,-.274,.12],[.22,.004,.005],2 if side<0 else 3,room)
# Raised console and underglow.
part('desk',[0,-.48,.02],[.91,.035,.30],0,room)
part('desk-bevel',[0,-.46,.17],[.91,.007,.012],1,room)
part('desk-cyan',[0,-.472,.179],[.87,.003,.004],2,room)
for k in range(9):
 part(f'deck-key-{k}',[-.12+k*.03,-.451,.08],[.022,.004,.028],1,room)
# Monitor local front plane. Transform is native.
part('screen-back',[0,0,-.008],[1,1,.035],0,monitor)
part('screen-black',[0,0,.012],[.95,.88,.009],4,monitor)
for side in [-1,1]:
 part(f'monitor-side-{side}',[side*.4875,0,.014],[.025,1,.026],1,monitor)
 part(f'monitor-side-light-{side}',[side*.48,0,.029],[.003,.96,.003],2 if side<0 else 3,monitor)
 part(f'monitor-horizontal-{side}',[0,side*.4875,.014],[.95,.025,.026],1,monitor)
 part(f'monitor-horizontal-light-{side}',[0,side*.48,.029],[.95,.003,.003],2 if side>0 else 3,monitor)
part('monitor-status-sill',[0,.435,.022],[.94,.07,.012],0,monitor)
part('monitor-lower-lip',[0,-.47,.028],[.94,.012,.012],0,monitor)
part('stand',[0,-.60,-.11],[.035,.22,.026],1,monitor)
part('stand-foot',[0,-.715,-.08],[.25,.012,.12],0,monitor)
roomid=len(nodes);nodes.append({'name':'cockpit-room','children':room})
monitorid=len(nodes);nodes.append({'name':'cockpit-monitor','children':monitor})
root={'asset':{'version':'2.0','generator':'Continuum owned procedural cockpit 20261112'},'scene':0,'scenes':[{'nodes':[roomid,monitorid]}],'nodes':nodes,'meshes':meshes,'materials':materials,'buffers':[{'byteLength':len(blob)}],'bufferViews':views,'accessors':access}
js=json.dumps(root,separators=(',',':')).encode();js+=b' '*((-len(js))%4);blob+=b'\0'*((-len(blob))%4)
out=struct.pack('<III',0x46546c67,2,12+8+len(js)+8+len(blob))+struct.pack('<II',len(js),0x4e4f534a)+js+struct.pack('<II',len(blob),0x004e4942)+blob
path=ROOT/'app/src/main/assets/cinema3d/cockpit-workstation.glb';path.write_bytes(out)
print(path.relative_to(ROOT),len(out),hashlib.sha256(out).hexdigest())
