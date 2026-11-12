"""Deterministic original mesh; no downloaded assets."""
import json, math, struct, hashlib
from itertools import product
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

def bevelled_box(bevel=.07):
 pos=[];norm=[];ind=[]
 inner=.5-bevel
 def face(points,normal):
  u=[points[1][c]-points[0][c] for c in range(3)]
  v=[points[2][c]-points[0][c] for c in range(3)]
  cross=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
  if sum(cross[c]*normal[c] for c in range(3))<0: points=points[::-1]
  first=len(pos)//3
  for point in points: pos.extend(point);norm.extend(normal)
  for j in range(1,len(points)-1): ind.extend((first,first+j,first+j+1))
 for axis in range(3):
  others=[j for j in range(3) if j!=axis]
  for sign in [-1,1]:
   normal=[0.,0.,0.];normal[axis]=sign
   points=[]
   for s,t in [(-1,-1),(1,-1),(1,1),(-1,1)]:
    point=[0.,0.,0.];point[axis]=sign*.5
    point[others[0]]=s*inner;point[others[1]]=t*inner;points.append(point)
   face(points,normal)
 for free in range(3):
  fixed=[j for j in range(3) if j!=free]
  for s,t in product([-1,1],repeat=2):
   normal=[0.,0.,0.];normal[fixed[0]]=s/math.sqrt(2);normal[fixed[1]]=t/math.sqrt(2)
   points=[]
   for k,outer in [(-1,0),(1,0),(1,1),(-1,1)]:
    point=[0.,0.,0.];point[free]=k*inner
    point[fixed[0]]=s*(.5 if outer==0 else inner)
    point[fixed[1]]=t*(inner if outer==0 else .5);points.append(point)
   face(points,normal)
 for signs in product([-1,1],repeat=3):
  normal=[s/math.sqrt(3) for s in signs]
  points=[[signs[c]*(.5 if c==axis else inner) for c in range(3)] for axis in range(3)]
  face(points,normal)
 return data(pos,3,5126),data(norm,3,5126),data(ind,1,5123)

bevel_attributes=bevelled_box()
materials=[
 {'name':'smoked-metal','pbrMetallicRoughness':{'baseColorFactor':[.025,.038,.064,1],'metallicFactor':.55,'roughnessFactor':.28}},
 {'name':'bevel-silver','pbrMetallicRoughness':{'baseColorFactor':[.15,.22,.30,1],'metallicFactor':.62,'roughnessFactor':.22}},
 {'name':'cyan-rim','pbrMetallicRoughness':{'baseColorFactor':[.04,.57,.8,1],'metallicFactor':.2,'roughnessFactor':.35},'emissiveFactor':[.02,.6,.95]},
 {'name':'violet-rim','pbrMetallicRoughness':{'baseColorFactor':[.32,.10,.8,1],'metallicFactor':.2,'roughnessFactor':.35},'emissiveFactor':[.32,.05,.75]},
 {'name':'opaque-screen','pbrMetallicRoughness':{'baseColorFactor':[.002,.004,.009,1],'metallicFactor':0,'roughnessFactor':1}},
 {'name':'smoked-glass','alphaMode':'BLEND','doubleSided':True,'pbrMetallicRoughness':{'baseColorFactor':[.045,.13,.23,.18],'metallicFactor':.15,'roughnessFactor':.12}},
 {'name':'cyan-frost','alphaMode':'BLEND','doubleSided':True,'pbrMetallicRoughness':{'baseColorFactor':[.035,.28,.36,.065],'metallicFactor':.05,'roughnessFactor':.30},'emissiveFactor':[.002,.018,.026]},
 {'name':'violet-frost','alphaMode':'BLEND','doubleSided':True,'pbrMetallicRoughness':{'baseColorFactor':[.22,.10,.37,.065],'metallicFactor':.05,'roughnessFactor':.30},'emissiveFactor':[.015,.004,.025]},
 {'name':'prism-highlight','pbrMetallicRoughness':{'baseColorFactor':[.45,.69,.83,1],'metallicFactor':.3,'roughnessFactor':.18},'emissiveFactor':[.14,.24,.30]}
]
for mat in range(len(materials)):
 pa,na,ia=bevel_attributes if mat in (0,1,5) else (a,n,i)
 meshes.append({'primitives':[{'attributes':{'POSITION':pa,'NORMAL':na},'indices':ia,'material':mat}]})
def part(name,xyz,scale,mat,group):
 node={'name':name,'mesh':mat,'translation':xyz,'scale':scale};idx=len(nodes);nodes.append(node);group.append(idx)
room=[];monitor=[]
# Architectural depth, open central volume.
for side in [-1,1]:
 for k in range(3):
  x=side*(.45-k*.028);z=-.24-k*.11
  part(f'arch-{side}-{k}',[x,.09,z],[.014,.76,.023],0,room)
  part(f'arch-bevel-{side}-{k}',[x,.09,z+.010],[.009,.75,.008],1,room)
  part(f'arch-light-{side}-{k}',[x-side*.008,.09,z+.013],[.0025,.68,.006],2 if side<0 else 3,room)
 part(f'ceiling-{side}',[side*.35,.46,-.40],[.26,.012,.035],1,room)
 part(f'ceiling-line-{side}',[side*.35,.45,-.38],[.24,.002,.006],2 if side<0 else 3,room)
 part(f'wing-{side}',[side*.36,-.28,-.03],[.22,.012,.30],5,room)
 part(f'wing-rear-{side}',[side*.36,-.302,-.06],[.20,.006,.26],6 if side<0 else 7,room)
 part(f'wing-edge-{side}',[side*.36,-.274,.12],[.22,.004,.005],2 if side<0 else 3,room)
 part(f'wing-inner-edge-{side}',[side*.25,-.279,-.03],[.004,.010,.30],8,room)
 part(f'glass-fin-{side}',[side*.445,.035,-.27],[.008,.56,.24],5,room)
 part(f'glass-fin-back-{side}',[side*.455,.035,-.31],[.004,.52,.22],6 if side<0 else 7,room)
 part(f'glass-fin-edge-{side}',[side*.445,.035,-.145],[.003,.56,.003],2 if side<0 else 3,room)
# Raised console and underglow.
part('desk',[0,-.48,.02],[.91,.035,.30],0,room)
part('desk-bevel',[0,-.46,.17],[.91,.007,.012],1,room)
part('desk-cyan',[0,-.472,.179],[.87,.003,.004],2,room)
part('desk-glass',[0,-.4615,.02],[.89,.002,.28],5,room)
part('desk-rear-light',[0,-.4605,-.12],[.78,.002,.003],3,room)
for k in range(9):
 part(f'deck-key-{k}',[-.12+k*.03,-.4585,.08],[.022,.004,.028],1,room)
# Monitor local front plane. Transform is native.
part('screen-back',[0,0,-.008],[1,1,.035],0,monitor)
part('screen-rear-bevel',[0,0,-.020],[1.018,1.018,.014],1,monitor)
part('screen-black',[0,0,.012],[.95,.88,.009],4,monitor)
for side in [-1,1]:
 part(f'monitor-side-{side}',[side*.4875,0,.014],[.025,1,.026],1,monitor)
 part(f'monitor-side-light-{side}',[side*.48,0,.029],[.003,.96,.003],2 if side<0 else 3,monitor)
 part(f'monitor-glass-side-{side}',[side*.476,0,.034],[.010,.965,.009],5,monitor)
 part(f'monitor-horizontal-{side}',[0,side*.4875,.014],[.95,.025,.026],1,monitor)
 part(f'monitor-horizontal-light-{side}',[0,side*.48,.029],[.95,.003,.003],2 if side>0 else 3,monitor)
 for end in [-1,1]:
  part(f'corner-catchlight-{side}-{end}',[side*.480,end*.458,.036],[.003,.035,.002],8,monitor)
  part(f'corner-prism-{side}-{end}',[side*.466,end*.479,.036],[.030,.003,.002],8,monitor)
part('monitor-status-sill',[0,.435,.022],[.94,.07,.012],0,monitor)
part('monitor-lower-lip',[0,-.47,.028],[.94,.012,.012],0,monitor)
part('stand',[0,-.50175,-.11],[.035,.0235,.026],1,monitor)
part('stand-foot',[0,-.5195,-.08],[.25,.012,.12],1,monitor)
roomid=len(nodes);nodes.append({'name':'cockpit-room','children':room})
monitorid=len(nodes);nodes.append({'name':'cockpit-monitor','children':monitor})
root={'asset':{'version':'2.0','generator':'Continuum owned procedural cockpit 20261112'},'scene':0,'scenes':[{'nodes':[roomid,monitorid]}],'nodes':nodes,'meshes':meshes,'materials':materials,'buffers':[{'byteLength':len(blob)}],'bufferViews':views,'accessors':access}
js=json.dumps(root,separators=(',',':')).encode();js+=b' '*((-len(js))%4);blob+=b'\0'*((-len(blob))%4)
out=struct.pack('<III',0x46546c67,2,12+8+len(js)+8+len(blob))+struct.pack('<II',len(js),0x4e4f534a)+js+struct.pack('<II',len(blob),0x004e4942)+blob
assert len(out)<=32_768, 'Cockpit asset budget exceeded'
path=ROOT/'app/src/main/assets/cinema3d/cockpit-workstation.glb';path.write_bytes(out)
print(path.relative_to(ROOT),len(out),hashlib.sha256(out).hexdigest())
