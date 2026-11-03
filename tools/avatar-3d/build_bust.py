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
for name,weight in [('head-fat-decr',.28),('head-oval',.18),('head-scale-horiz-decr',.12),('mouth-compression',.12),('mouth-open',-.07)]:
    for i,d in target(name).items(): neutral[i]+=d*weight

def adapted(v):
    x,y,z=v
    # decimetres -> metres, base cut at 4.4; +Z forward in canonical glTF.
    face=max(0,min(1,(y-5.7)/.5))
    # Widen the eye aperture locally while preserving eye symmetry and applying
    # the very same smooth map to every expression displacement.
    c=.3077 if x>=0 else -.3077
    eye=math.exp(-((x-c)/.24)**4-((y-7.2842)/.29)**4)*max(0,min(1,(z-.85)/.3))
    x += (x-c)*.24*eye
    y += (y-7.2842)*.12*eye
    if y>7.4:y=7.4+(y-7.4)*.85
    return Vector((x*(.96+.06*face)*.1, (y-4.4)*.1, z*.1))

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
skin=material('DeepIndigoSkin',(.005,.009,.025),.05,.60)
face.data.materials.append(skin)
eye_white=material('NaturalSclera',(.16,.20,.26),.0,.35)
iris_mat=material('CyanIris',(.008,.035,.075),.05,.28,.03)
pupil_mat=material('Pupil',(.001,.003,.007),0,.25)
inner=material('MouthInterior',(.013,.005,.015),0,.9)

def empty(name,p,parent=None):
    o=bpy.data.objects.new(name,None);bpy.context.collection.objects.link(o)
    o['canonicalPivot']=list(p)
    o.parent=parent
    o.location=blender_vec(p-Vector(parent['canonicalPivot']) if parent else p)
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
    tooth=name.startswith('Tooth')
    bpy.ops.mesh.primitive_uv_sphere_add(segments=8 if tooth else 24,ring_count=6 if tooth else 12,location=blender_vec(p))
    o=bpy.context.object;o.name=name;o.scale=(scale[0],scale[2],scale[1])
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    o.data.materials.append(mat)
    for poly in o.data.polygons:poly.use_smooth=True
    if parent:
        o.parent=parent;o.location=blender_vec(p-Vector(parent['canonicalPivot']))
    return o
for side,p in eye_centers.items():
    sphere('Eyeball'+side,p,(.0160,.0148,.0137),eye_white,eye_nodes[side])
    sphere('Iris'+side,p+Vector((0,0,.01265)),(.0067,.0067,.00135),iris_mat,eye_nodes[side])
    sphere('Pupil'+side,p+Vector((0,0,.01365)),(.0029,.0029,.00055),pupil_mat,eye_nodes[side])

# Interior backing is physically behind the lips. Teeth are deliberately absent
# until occlusion is evaluated, rather than supplying protruding generic teeth.
mouthback=sphere('MouthInterior',Vector((0,.220,.122)),(.027,.016,.014),inner,jaw)
teethmat=material('NaturalTeeth',(.42,.40,.34),0,.43)
for row,parent,y in [('Upper',head,.230),('Lower',jaw,.218)]:
    for i in range(8):
        x=(i-3.5)*.0053
        sphere('Tooth'+row+str(i),Vector((x,y,.144-abs(x)*.26)),(.00265,.0043,.0025),teethmat,parent)

# A render subdivision is used only for the first inspection. The final exporter
# below records actual mesh counts and does not claim viewport-only subdivision.
sub=face.modifiers.new('InspectionSubdivision','SUBSURF');sub.levels=1;sub.render_levels=1

def bake_and_cap(obj):
    """Bake identical subdivision topology for every target, then close base."""
    baked={}; polygons=None
    for name in ['Basis']+MORPHS:
        for k in obj.data.shape_keys.key_blocks:k.value=1 if k.name==name and name!='Basis' else 0
        bpy.context.view_layer.update()
        evaluated=obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
        mesh=bpy.data.meshes.new_from_object(evaluated)
        baked[name]=[canonical_vec(v.co) for v in mesh.vertices]
        if polygons is None:polygons=[list(p.vertices) for p in mesh.polygons]
        bpy.data.meshes.remove(mesh)
    edgecounts=defaultdict(int)
    for f in polygons:
        for a,b in zip(f,f[1:]+f[:1]):edgecounts[tuple(sorted((a,b)))]+=1
    border=[(a,b) for (a,b),n in edgecounts.items() if n==1 and max(baked['Basis'][a].y,baked['Basis'][b].y)<.09]
    ids=sorted({i for e in border for i in e})
    for pts in baked.values():
        for i in ids:pts[i].y=0
        pts.append(sum((pts[i] for i in ids),Vector())/len(ids))
    cap=len(baked['Basis'])-1
    for a,b in border:polygons.append([b,a,cap])
    old=obj;new=mesh_object('FaceBaked',baked['Basis'],polygons)
    new.data.materials.append(skin)
    for name,pts in baked.items():
        key=new.shape_key_add(name=name)
        for item,co in zip(key.data,pts):item.co=blender_vec(co)
    bpy.data.objects.remove(old,do_unlink=True);new.name='Face'
    return new,baked

face,baked=bake_and_cap(face)
points=baked['Basis'];morphpoints={n:baked[n] for n in MORPHS}

def make_threads():
    """Physical triangular filaments following a wavy isocontour field.

    Every point retains barycentric binding to the canonical face vertices;
    every facial target deforms the filaments too. No 2D portrait overlay.
    """
    mesh=face.data; mesh.calc_loop_triangles()
    normals=[canonical_vec(v.normal) for v in mesh.vertices]
    fields=[]
    for mode in [0,1]:
        fields.append([p.y+(.038 if mode==0 else -.038)*math.sin(22*p.x+19*p.z)
         +.012*math.sin(48*p.z-13*p.x)+.023*math.exp(-((abs(p.x)-.033)/.03)**2-((p.y-.285)/.03)**2)
         -.017*math.exp(-(p.x/.04)**2-((p.y-.22)/.022)**2) for p in points])
    paths=[]
    for leveln in range(1,73):
        field=fields[leveln%2]
        level=leveln*.0055; segments=[]; binding={}
        for tri in mesh.loop_triangles:
            ids=list(tri.vertices); hits=[]
            for a,b in zip(ids,ids[1:]+ids[:1]):
                if (field[a]<level)<(field[b]<level) or (field[a]<level)>(field[b]<level):
                    key=tuple(sorted((a,b)));t=(level-field[a])/(field[b]-field[a]);binding[key]=(a,b,t);hits.append(key)
            if len(hits)==2:segments.append(hits)
        adjacency=defaultdict(list)
        for a,b in segments:adjacency[a].append(b);adjacency[b].append(a)
        seen=set()
        for start in adjacency:
            if start in seen:continue
            chain=[];current=start
            while current not in seen:
                seen.add(current);chain.append(binding[current]);nxt=[n for n in adjacency[current] if n not in seen]
                if not nxt:break
                current=nxt[0]
            if len(chain)<5:continue
            # Arc-aware spacing keeps geometry bounded while retaining curved
            # paths. Surface bindings remain exact for chosen samples.
            sampled=[];last=None;length=0
            for item in chain:
                a,b,t=item;p=points[a].lerp(points[b],t)
                if last is None or (p-last).length>.007:sampled.append(item);last=p
            if len(sampled)>2:paths.append(sampled)
    verts=[];polys=[];allmorph={n:[] for n in MORPHS};colors=[]
    for chain in paths:
        offset=len(verts)
        for a,b,t in chain:
            p=points[a].lerp(points[b],t);normal=normals[a].lerp(normals[b],t).normalized()
            tangent=normal.cross(Vector((0,1,0)))
            if tangent.length<.01:tangent=normal.cross(Vector((1,0,0)))
            tangent.normalize();other=normal.cross(tangent).normalized()
            p+=normal*.00034
            for k in range(3):
                delta=(tangent*math.cos(k*math.tau/3)+other*math.sin(k*math.tau/3))*.000105
                verts.append(p+delta);colors.append(0 if p.x<0 else 1)
                for name in MORPHS:
                    move=(morphpoints[name][a]-points[a]).lerp(morphpoints[name][b]-points[b],t)
                    allmorph[name].append(p+delta+move)
        for j in range(len(chain)-1):
            for k in range(3):
                a=offset+3*j+k;b=offset+3*j+(k+1)%3;c=b+3;d=a+3
                polys.append([a,b,c,d])
    obj=mesh_object('NeuralThreads',verts,polys)
    violet=material('VioletThreads',(.23,.018,.72),.1,.4,2.6)
    cyan=material('CyanThreads',(.008,.32,.8),.1,.4,2.6)
    obj.data.materials.append(violet);obj.data.materials.append(cyan)
    for p in obj.data.polygons:p.material_index=colors[p.vertices[0]]
    obj.shape_key_add(name='Basis')
    for name,pts in allmorph.items():
        key=obj.shape_key_add(name=name)
        for item,co in zip(key.data,pts):item.co=blender_vec(co)
    return obj

threads=make_threads()

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
area('SoftKey',(.4,.6,.7),3.5,(.65,.8,1),.45)
area('VioletRim',(-.4,.4,-.1),5,(.5,.16,1),.35)
area('CyanRim',(.4,.4,-.1),5,(.1,.65,1),.35)
area('FrontFill',(-.1,.1,.6),.5,(.65,.7,1),.4)
camd=bpy.data.cameras.new('PreviewCamera');cam=bpy.data.objects.new('PreviewCamera',camd);bpy.context.collection.objects.link(cam);scene.camera=cam
camd.type='ORTHO';camd.ortho_scale=.47
def camera(angle):
    a=math.radians(angle);cam.location=blender_vec(Vector((math.sin(a)*.9,.23,math.cos(a)*.9)))
    cam.rotation_euler=(blender_vec(Vector((0,.215,.025)))-cam.location).to_track_quat('-Z','Y').to_euler()
def reset():
    jaw.rotation_euler.x=0
    for obj in [face,threads]:
        for k in obj.data.shape_keys.key_blocks:k.value=0
def render(name,angle=0,weights=None):
    reset()
    for obj in [face,threads]:
        for k,v in (weights or {}).items():obj.data.shape_keys.key_blocks[k].value=v
    jaw.rotation_euler.x=math.radians(18)*max((weights or {}).get('JawOpen',0),(weights or {}).get('AA',0)*.7,(weights or {}).get('O',0)*.35)
    camera(angle);scene.render.filepath=str(OUT/(name+'.png'));bpy.ops.render.render(write_still=True)
    print('RENDER_COMPLETE',name,flush=True)

reset()
sys.path.insert(0,str(pathlib.Path(__file__).parent))
from export_glb import export_avatar
export_avatar(ASSETS/'jarvis-research-v1.glb',OUT/'asset-manifest.json',MORPHS)
bpy.ops.wm.save_as_mainfile(filepath=str(ASSETS/'jarvis-research-v1.blend'))
if PREVIEW:
    for name,angle,weights in [('anatomy-front',0,{}),('anatomy-profile',90,{}),('anatomy-blink',0,{'BlinkLeft':1,'BlinkRight':1}),('anatomy-aa',0,{'AA':1})]:render(name,angle,weights)
    print('PREVIEW_COMPLETE',flush=True)
else:
    for n in range(24):render('view-%02d-neutral-%03d'%(n,n*15),n*15)
    for n,label,weights in [(24,'blink',{'BlinkLeft':1,'BlinkRight':1}),(25,'smile',{'Smile':1}),(26,'speech-aa',{'AA':1}),(27,'speech-o',{'O':1}),(28,'speech-u',{'U':1}),(29,'brow-speech',{'BrowUp':.7,'E':.7})]:render('view-%02d-%s'%(n,label),0,weights)
    reset()
    print('THIRTY_SAME_GEOMETRY_VIEWS_COMPLETE',flush=True)
