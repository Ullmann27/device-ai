"""Reproducible CC0-data-derived avatar research bust. Blender 4.3+.

blender -b -t 2 --python tools/avatar-3d/build_bust.py -- --preview
No network and no upstream program logic. Reference is never image-processed.
"""
import bpy, math, json, pathlib, sys, hashlib, struct
from mathutils import Vector
from collections import defaultdict

ROOT = pathlib.Path(__file__).resolve().parents[2]
VENDOR = ROOT / 'tools/avatar-3d/vendor/makehuman'
OUT = ROOT / 'docs/avatar-live/geometry-work'
ASSETS = ROOT / 'assets/avatar'
OUT.mkdir(parents=True, exist_ok=True); ASSETS.mkdir(parents=True, exist_ok=True)
ARGS = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
PREVIEW = '--preview' in ARGS
MORPHS = ['SIL','PP','FF','TH','DD','KK','CH','SS','NN','RR','AA','E','I','O','U',
          'BlinkLeft','BlinkRight','BrowUp','BrowDown','Smile','JawOpen']

def obj_data():
    verts=[]; groups=defaultdict(list); group=''
    for line in (VENDOR/'base.obj').read_text().splitlines():
        bits=line.split()
        if not bits: continue
        if bits[0]=='v': verts.append(Vector(map(float,bits[1:4])))
        elif bits[0]=='g': group=bits[1]
        elif bits[0]=='f': groups[group].append([int(t.split('/')[0])-1 for t in bits[1:]])
    return verts,groups

def target(name):
    result={}
    for line in (VENDOR/(name+'.target')).read_text().splitlines():
        bits=line.split()
        if bits and not bits[0].startswith('#'): result[int(bits[0])]=Vector(map(float,bits[1:4]))
    return result

original,groups=obj_data()
neutral=[v.copy() for v in original]
# An authored proportion pass toward the derived reference: reduced facial mass,
# slender oval head. This is a candidate, not a likeness certification.
for name,weight in [('head-fat-decr',.28),('head-oval',.18),('head-scale-horiz-decr',.12)]:
    for i,d in target(name).items(): neutral[i]+=d*weight

def adapted(v):
    x,y,z=v
    # decimetres -> metres, base cut at 4.4; +Z forward in canonical glTF.
    face=max(0,min(1,(y-5.7)/.5))
    return Vector((x*(.96-.035*face)*.1, (y-4.4)*.1, z*.1))

# Bind mirror pairs from original neutral topology, then average both sides.
from mathutils.kdtree import KDTree
kd=KDTree(len(original))
for i,v in enumerate(original): kd.insert(v,i)
kd.balance()
pairs=[]
for i,v in enumerate(original):
    _,j,d=kd.find(Vector((-v.x,v.y,v.z)))
    if d<.001 and i<=j: pairs.append((i,j))

def symmetrize(arr):
    for i,j in pairs:
        a=arr[i]; b=arr[j]
        m=Vector(((a.x-b.x)/2,(a.y+b.y)/2,(a.z+b.z)/2))
        arr[i]=m; arr[j]=Vector((-m.x,m.y,m.z))
    return arr

neutral=symmetrize(neutral)
canon=[adapted(v) for v in neutral]
bodyfaces=[f for f in groups['body'] if min(neutral[i].y for i in f)>=4.4]
used=sorted({i for f in bodyfaces for i in f}); remap={old:i for i,old in enumerate(used)}
faces=[[remap[i] for i in f] for f in bodyfaces]
points=[canon[i] for i in used]

def blender_vec(v): return Vector((v.x,-v.z,v.y))
def canonical_vec(v): return Vector((v.x,v.z,-v.y))

bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
def mesh_object(name,pts,polys):
    mesh=bpy.data.meshes.new(name+'Mesh'); mesh.from_pydata([blender_vec(p) for p in pts],[],polys); mesh.update()
    obj=bpy.data.objects.new(name,mesh); bpy.context.collection.objects.link(obj)
    for p in mesh.polygons: p.use_smooth=True
    return obj

face=mesh_object('Face',points,faces)
face.shape_key_add(name='Basis')
# Licensed expression displacement units are combined with deliberately bounded
# weights. Different visemes are geometrically distinct; phonetic quality still
# requires human evaluation and native TTS timing remains device work.
recipes={
 'SIL':{'mouth-compression':.035},
 'PP':{'mouth-compression':.6,'mouth-pursing':.12},
 'FF':{'mouth-elevation':.25,'mouth-retraction':.16,'mouth-open':.06},
 'TH':{'mouth-open':.18,'mouth-protusion':.12,'mouth-eversion':.16},
 'DD':{'mouth-open':.20,'mouth-part-later':.18},
 'KK':{'mouth-open':.30,'mouth-retraction':.10},
 'CH':{'mouth-pursing':.32,'mouth-open':.15},
 'SS':{'mouth-part-later':.27,'mouth-compression':.13},
 'NN':{'mouth-open':.11,'mouth-elevation':.12},
 'RR':{'mouth-pursing':.22,'mouth-open':.22,'mouth-protusion':.08},
 'AA':{'mouth-open':.65,'mouth-part-later':.15},
 'E':{'mouth-open':.23,'mouth-part-later':.42},
 'I':{'mouth-part-later':.57,'mouth-open':.10},
 'O':{'mouth-open':.36,'mouth-pursing':.58,'mouth-protusion':.20},
 'U':{'mouth-pursing':.70,'mouth-protusion':.35,'mouth-open':.10},
 'BlinkLeft':{'eye-left-closure':1}, 'BlinkRight':{'eye-right-closure':1},
 'BrowUp':{'eyebrows-left-up':.65,'eyebrows-right-up':.65},
 'BrowDown':{'eyebrows-left-down':.6,'eyebrows-right-down':.6},
 'Smile':{'mouth-corner-puller':.55,'mouth-upward-retraction':.3},
 'JawOpen':{'mouth-open':.8},
}
morphpoints={}
for name in MORPHS:
    shaped=[v.copy() for v in neutral]
    for source,weight in recipes[name].items():
        for i,d in target(source).items(): shaped[i]+=d*weight
    if name not in ['BlinkLeft','BlinkRight']: shaped=symmetrize(shaped)
    pts=[adapted(shaped[i]) for i in used]; morphpoints[name]=pts
    key=face.shape_key_add(name=name)
    for p,co in zip(key.data,pts):p.co=blender_vec(co)

def material(name,color,metal=.1,rough=.5,emission=0):
    mat=bpy.data.materials.new(name); mat.diffuse_color=(*color,1); mat.use_nodes=True
    bs=mat.node_tree.nodes.get('Principled BSDF')
    bs.inputs['Base Color'].default_value=(*color,1)
    bs.inputs['Metallic'].default_value=metal; bs.inputs['Roughness'].default_value=rough
    bs.inputs['Emission Color'].default_value=(*color,1);bs.inputs['Emission Strength'].default_value=emission
    return mat
skin=material('DeepIndigoSkin',(.035,.055,.13),.22,.43)
face.data.materials.append(skin)
eye_white=material('NaturalSclera',(.37,.43,.52),.05,.27)
iris_mat=material('CyanIris',(.012,.12,.20),.15,.3,.14)
pupil_mat=material('Pupil',(.001,.003,.007),0,.25)
inner=material('MouthInterior',(.013,.005,.015),0,.9)

def empty(name,p,parent=None):
    o=bpy.data.objects.new(name,None);bpy.context.collection.objects.link(o)
    o.location=blender_vec(p)
    if parent:
        world=o.matrix_world.copy();o.parent=parent;o.matrix_world=world
    return o
def center(group):
    ids={i for f in groups[group] for i in f};return sum((canon[i] for i in ids),Vector())/len(ids)
root=empty('AvatarRoot',Vector((0,0,0)))
neck=empty('Neck',center('joint-neck'),root)
head=empty('Head',Vector((0,.224,.025)),neck)
eye_centers={side:center('joint-'+('l' if side=='Left' else 'r')+'-eye') for side in ['Left','Right']}
eye_nodes={side:empty('Eye'+side,p,head) for side,p in eye_centers.items()}
jaw=empty('Jaw',Vector((0,.227,.035)),head)

def sphere(name,p,scale,mat,parent=None):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32,ring_count=16,location=blender_vec(p))
    o=bpy.context.object;o.name=name;o.scale=(scale[0],scale[2],scale[1])
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    o.data.materials.append(mat)
    for poly in o.data.polygons:poly.use_smooth=True
    if parent:
        m=o.matrix_world.copy();o.parent=parent;o.matrix_world=m
    return o
for side,p in eye_centers.items():
    sphere('Eyeball'+side,p,(.0137,.0137,.0137),eye_white,eye_nodes[side])
    sphere('Iris'+side,p+Vector((0,0,.01265)),(.006,.006,.00135),iris_mat,eye_nodes[side])
    sphere('Pupil'+side,p+Vector((0,0,.01365)),(.0026,.0026,.00055),pupil_mat,eye_nodes[side])

# Interior backing is physically behind the lips. Teeth are deliberately absent
# until occlusion is evaluated, rather than supplying protruding generic teeth.
mouthback=sphere('MouthInterior',Vector((0,.220,.122)),(.027,.016,.014),inner,jaw)

# A render subdivision is used only for the first inspection. The final exporter
# below records actual mesh counts and does not claim viewport-only subdivision.
sub=face.modifiers.new('InspectionSubdivision','SUBSURF');sub.levels=1;sub.render_levels=1

scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=32
scene.render.threads_mode='FIXED';scene.render.threads=2
scene.cycles.use_denoising=False  # This local Blender build has no OpenImageDenoise.
scene.render.resolution_x=640;scene.render.resolution_y=640;scene.render.resolution_percentage=100
scene.world.color=(.015,.015,.015)
scene.view_settings.view_transform='AgX'
world=scene.world;world.use_nodes=True;world.node_tree.nodes.get('Background').inputs[0].default_value=(.005,.009,.023,1)
world.node_tree.nodes.get('Background').inputs[1].default_value=.22

def area(name,p,power,color,size):
    d=bpy.data.lights.new(name,'AREA');d.energy=power;d.color=color;d.shape='DISK';d.size=size
    o=bpy.data.objects.new(name,d);bpy.context.collection.objects.link(o);o.location=blender_vec(Vector(p))
    o.rotation_euler=(blender_vec(Vector((0,.25,.05)))-o.location).to_track_quat('-Z','Y').to_euler()
area('SoftKey',(.4,.6,.7),35,(.65,.8,1),.45)
area('VioletRim',(-.4,.4,-.1),25,(.5,.16,1),.35)
area('CyanRim',(.4,.4,-.1),25,(.1,.65,1),.35)
area('FrontFill',(-.1,.1,.6),5,(.65,.7,1),.4)
camd=bpy.data.cameras.new('PreviewCamera');cam=bpy.data.objects.new('PreviewCamera',camd);bpy.context.collection.objects.link(cam);scene.camera=cam
camd.type='ORTHO';camd.ortho_scale=.47
def camera(angle):
    a=math.radians(angle);cam.location=blender_vec(Vector((math.sin(a)*.9,.23,math.cos(a)*.9)))
    cam.rotation_euler=(blender_vec(Vector((0,.215,.025)))-cam.location).to_track_quat('-Z','Y').to_euler()
def reset():
    for k in face.data.shape_keys.key_blocks:k.value=0
def render(name,angle=0,weights=None):
    reset()
    for k,v in (weights or {}).items():face.data.shape_keys.key_blocks[k].value=v
    camera(angle);scene.render.filepath=str(OUT/(name+'.png'));bpy.ops.render.render(write_still=True)
    print('RENDER_COMPLETE',name,flush=True)

bpy.ops.wm.save_as_mainfile(filepath=str(ASSETS/'jarvis-research-v1.blend'))
if PREVIEW:
    for name,angle,weights in [('anatomy-front',0,{}),('anatomy-profile',90,{}),('anatomy-blink',0,{'BlinkLeft':1,'BlinkRight':1}),('anatomy-aa',0,{'AA':1})]:render(name,angle,weights)
    print('PREVIEW_COMPLETE',flush=True)
else:
    render('anatomy-front')
