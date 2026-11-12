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

def outline(bounds,radius,steps=4):
 left,bottom,right,top=bounds
 points=[]; normals=[]
 for x,y,start in [(right-radius,top-radius,0),(left+radius,top-radius,90),
                   (left+radius,bottom+radius,180),(right-radius,bottom+radius,270)]:
  for step in range(steps+1):
   angle=math.radians(start+90*step/steps)
   nx,ny=math.cos(angle),math.sin(angle)
   points.append((x+radius*nx,y+radius*ny));normals.append((nx,ny))
 return points,normals

def rounded_prism(radius,bevel=0):
 points,edge_normals=outline((-.5+bevel,-.5+bevel,.5-bevel,.5-bevel),radius-bevel)
 count=len(points);pos=[];norm=[];indices=[]
 for side in [1,-1]:
  first=len(pos)//3
  for x,y in points: pos.extend((x,y,side*.5));norm.extend((0,0,side))
  for index in range(1,count-1):
   tri=[first,first+index,first+index+1]
   indices.extend(tri if side>0 else tri[::-1])
 layers=[(-.5,bevel),(-.28,0),(.28,0),(.5,bevel)] if bevel else [(-.5,0),(.5,0)]
 for lower,upper in zip(layers,layers[1:]):
  first=len(pos)//3
  nz=(upper[1]-lower[1])/(upper[0]-lower[0]);length=math.sqrt(1+nz*nz)
  for z,inset in [lower,upper]:
   ring,normals=outline((-.5+inset,-.5+inset,.5-inset,.5-inset),radius-inset)
   for (x,y),(nx,ny) in zip(ring,normals):
    pos.extend((x,y,z));norm.extend((nx/length,ny/length,nz/length))
  for index in range(count):
   a=first+index;b=first+(index+1)%count
   indices.extend((a,b,b+count,a,b+count,a+count))
 return data(pos,3,5126),data(norm,3,5126),data(indices,1,5123)

def frame_ring(outer,inner,radii,split=False):
 outside,_=outline(outer,radii[0]);inside,_=outline(inner,radii[1])
 pos=[];norm=[];groups=[[],[]] if split else [[]]
 for out,ins in zip(outside,inside):
  for x,y in [out,ins]: pos.extend((x,y,0));norm.extend((0,0,1))
 for index in range(len(outside)):
  following=(index+1)%len(outside)
  side=int(outside[index][0]+outside[following][0]>0) if split else 0
  a=index*2;b=following*2
  groups[side].extend((a,b,b+1,a,b+1,a+1))
 return data(pos,3,5126),data(norm,3,5126),[data(group,1,5123) for group in groups]

def mesh(name,geometry,material):
 pa,na,indices=geometry
 groups=indices if isinstance(indices,list) else [indices]
 mats=material if isinstance(material,list) else [material]
 index=len(meshes)
 meshes.append({'name':name,'primitives':[
  {'attributes':{'POSITION':pa,'NORMAL':na},'indices':ia,'material':ma}
  for ia,ma in zip(groups,mats)]})
 return index

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

materials=[
 {'name':'smoked-metal','pbrMetallicRoughness':{'baseColorFactor':[.026,.038,.058,1],'metallicFactor':.54,'roughnessFactor':.27}},
 {'name':'brushed-edge','pbrMetallicRoughness':{'baseColorFactor':[.15,.21,.28,1],'metallicFactor':.65,'roughnessFactor':.24}},
 {'name':'cyan-rim','pbrMetallicRoughness':{'baseColorFactor':[.025,.52,.70,1],'metallicFactor':.2,'roughnessFactor':.3},'emissiveFactor':[.015,.44,.65]},
 {'name':'violet-rim','pbrMetallicRoughness':{'baseColorFactor':[.30,.09,.59,1],'metallicFactor':.2,'roughnessFactor':.3},'emissiveFactor':[.25,.045,.47]},
 {'name':'opaque-screen','pbrMetallicRoughness':{'baseColorFactor':[.002,.004,.009,1],'metallicFactor':0,'roughnessFactor':1}},
 {'name':'smoked-glass','alphaMode':'BLEND','doubleSided':True,'pbrMetallicRoughness':{'baseColorFactor':[.048,.11,.18,.24],'metallicFactor':.12,'roughnessFactor':.13}},
 {'name':'keycaps','pbrMetallicRoughness':{'baseColorFactor':[.20,.35,.43,1],'metallicFactor':.30,'roughnessFactor':.30},'emissiveFactor':[.008,.030,.042]}
]
bevel=bevelled_box()
rounded=rounded_prism(.05)
desktop=rounded_prism(.32,.012)
bevel_metal=mesh('bevel-metal',bevel,1)
body_mesh=mesh('rounded-monitor-shell',rounded,0)
back_mesh=mesh('rounded-rear-edge',rounded,1)
desk_mesh=mesh('rounded-desk',desktop,0)
desk_edge=mesh('rounded-desk-edge',desktop,1)
desk_glass=mesh('rounded-smoked-glass',desktop,5)
main_ring=frame_ring((-.5,-.5,.5,.5),(-.470,-.450,.470,.385),(.05,.004))
main_bezel=mesh('monitor-metal-bezel',main_ring,1)
main_glass=mesh('monitor-glass-bezel',main_ring,5)
main_rim=mesh('monitor-light-rim',frame_ring((-.505,-.505,.505,.505),
 (-.499,-.499,.499,.499),(.055,.049),True),[2,3])
side_ring=frame_ring((-.537,-.527,.537,.527),(-.502,-.502,.502,.502),(.05,.001))
side_bezel=mesh('side-metal-bezel',side_ring,1)
side_glass=mesh('side-glass-bezel',side_ring,5)
side_rim=mesh('side-light-rim',frame_ring((-.539,-.529,.539,.529),
 (-.533,-.523,.533,.523),(.052,.046),True),[2,3])
desk_rim=mesh('rounded-desk-light',frame_ring((-.5,-.5,.5,.5),
 (-.497,-.497,.497,.497),(.32,.317),True),[2,3])

def part(name,xyz,scale,mesh_index,group,rotation=None):
 node={'name':name,'mesh':mesh_index,'translation':xyz,'scale':scale}
 if rotation: node['rotation']=rotation
 index=len(nodes);nodes.append(node);group.append(index)

room=[];monitor=[];side_monitors=[]
horizontal=[-.7071067812,0,0,.7071067812]
# Backdrop stays beneath avatar.
part('desk',[0,-.4515,.015],[.94,.32,.037],desk_mesh,room,horizontal)
part('desk-bevel',[0,-.434,.015],[.946,.322,.006],desk_edge,room,horizontal)
part('desk-glass',[0,-.4315,.015],[.94,.32,.003],desk_glass,room,horizontal)
part('desk-rim',[0,-.42975,.015],[.942,.322,1],desk_rim,room,horizontal)
tilt=math.radians(12)
keyboard_rotation=[math.sin((-math.pi/2+tilt)/2),0,0,math.cos((-math.pi/2+tilt)/2)]
keyboard_y=-.43+.040*math.sin(tilt)+.003*math.cos(tilt)
part('keyboard-case',[0,keyboard_y,.115],[.34,.080,.006],desk_mesh,room,keyboard_rotation)
part('keyboard-rim',[0,keyboard_y+.003*math.cos(tilt)+.0002,.115+.003*math.sin(tilt)],
 [.336,.076,1],desk_rim,room,keyboard_rotation)
leg_top=keyboard_y+math.tan(tilt)*.032-.003/math.cos(tilt)
for side in [-1,1]:
 part(f'keyboard-support-{side}',[side*.125,(leg_top-.43)*.5,.083],
  [.018,leg_top+.43,.012],bevel_metal,room)

# Shared key geometry, one draw.
key_pos=[];key_norm=[];key_indices=[]
def key(x,z,w,d):
 first=len(key_pos)//3
 for px,pz in [(x-w/2,z-d/2),(x-w/2,z+d/2),(x+w/2,z+d/2),(x+w/2,z-d/2)]:
  py=keyboard_y-math.tan(tilt)*(pz-.115)+.003/math.cos(tilt)+.00035
  key_pos.extend((px,py,pz));key_norm.extend((0,math.cos(tilt),math.sin(tilt)))
 key_indices.extend((first,first+1,first+2,first,first+2,first+3))
for row in range(3):
 for column in range(11): key((column-5)*.027+(row%2)*.004,.093+row*.015,.021,.010)
key(0,.139,.13,.010)
for side in [-1,1]:
 for column in range(2): key(side*(.095+column*.03),.139,.022,.010)
keys=mesh('keyboard-keycaps',(data(key_pos,3,5126),data(key_norm,3,5126),data(key_indices,1,5123)),6)
part('keyboard-keys',[0,0,0],[1,1,1],keys,room)

# Native content stays unobstructed.
part('screen-back',[0,0,-.008],[1.014,1.014,.04],body_mesh,monitor)
part('screen-rear-bevel',[0,0,-.027],[1.026,1.026,.012],back_mesh,monitor)
part('monitor-bezel',[0,0,.019],[1,1,1],main_bezel,monitor)
part('monitor-glass',[0,0,.022],[1,1,1],main_glass,monitor)
part('monitor-rim',[0,0,.027],[1,1,1],main_rim,monitor)
for side in [-1,1]:
 for end in [-1,1]:
  part(f'monitor-fastener-{side}-{end}',[side*.483,end*.459,.024],[.007,.010,.006],bevel_metal,monitor)
foot_bottom=(-.43+.215)/.35
foot_height=.026
foot_top=foot_bottom+foot_height
part('stand',[0,(-.49+foot_top)*.5,-.08],[.055,-.49-foot_top,.042],bevel_metal,monitor)
part('stand-foot',[0,foot_bottom+foot_height*.5,-.08],[.34,.16,foot_height],desk_edge,monitor,horizontal)

# Front planes match native slots.
for side in [-1,1]:
 x=side*.38;y=-.22
 part(f'side-{side}-back',[x,y,-.010],[.218,.321,.018],body_mesh,side_monitors)
 part(f'side-{side}-bezel',[x,y,0],[.20,.30,1],side_bezel,side_monitors)
 part(f'side-{side}-glass',[x,y,.0002],[.20,.30,1],side_glass,side_monitors)
 part(f'side-{side}-rim',[x,y,.0004],[.20,.30,1],side_rim,side_monitors)
 part(f'side-{side}-stand',[x,-.395,-.030],[.014,.046,.022],bevel_metal,side_monitors)
 part(f'side-{side}-foot',[x,-.424,-.030],[.105,.09,.012],desk_edge,side_monitors,horizontal)

root_ids=[]
for name,children in [('cockpit-room',room),('cockpit-monitor',monitor),('cockpit-side-monitors',side_monitors)]:
 root_ids.append(len(nodes));nodes.append({'name':name,'children':children})
root={'asset':{'version':'2.0','generator':'Continuum owned procedural reference cockpit 20261112'},
 'scene':0,'scenes':[{'nodes':root_ids}],'nodes':nodes,'meshes':meshes,'materials':materials,
 'buffers':[{'byteLength':len(blob)}],'bufferViews':views,'accessors':access}
js=json.dumps(root,separators=(',',':')).encode();js+=b' '*((-len(js))%4);blob+=b'\0'*((-len(blob))%4)
out=struct.pack('<III',0x46546c67,2,12+8+len(js)+8+len(blob))+struct.pack('<II',len(js),0x4e4f534a)+js+struct.pack('<II',len(blob),0x004e4942)+blob
assert len(out)<=32_768, 'Cockpit asset budget exceeded'
path=ROOT/'tools/cockpit-workstation/source/pr801-workstation.glb';path.write_bytes(out)
metadata={'source_base_commit':'5348087805fa4ab4152ea04e9f20bb41afae9d95',
 'generator_path':'tools/cockpit-workstation/build_scene.py',
 'generator_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
 'source_path':str(path.relative_to(ROOT)), 'sha256':hashlib.sha256(out).hexdigest(), 'bytes':len(out),
 'provenance':'Owned reference workstation seed; unified packaging adds only the live texture plane.'}
path.with_suffix('.json').write_text(json.dumps(metadata,indent=2)+'\n')
print(path.relative_to(ROOT),len(out),hashlib.sha256(out).hexdigest())
