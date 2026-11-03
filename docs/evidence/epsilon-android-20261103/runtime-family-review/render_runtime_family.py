"""Independent real GLB import + orthographic framing comparison (Blender, no A builder imported)."""
import bpy, json, pathlib, struct, sys, hashlib
from mathutils import Vector
args=sys.argv[sys.argv.index('--')+1:]; asset=pathlib.Path(args[0]); out=pathlib.Path(args[1]);out.mkdir(parents=True,exist_ok=True)
preflight=json.loads(pathlib.Path(args[2]).read_text());assert preflight['status']=='PASS';focus=preflight['focus']
raw=asset.read_bytes();assert hashlib.sha256(raw).hexdigest()==preflight['sha256'];doc=json.loads(raw[20:20+struct.unpack_from('<I',raw,12)[0]])
# Read actual neutral Face positions before Blender's coordinate conversion.
face=next(n for n in doc['nodes'] if n.get('name')=='Face');primitive=doc['meshes'][face['mesh']]['primitives'][0]
a=doc['accessors'][primitive['attributes']['POSITION']];bv=doc['bufferViews'][a['bufferView']]
bin_start=28+struct.unpack_from('<I',raw,12)[0];start=bin_start+bv.get('byteOffset',0)+a.get('byteOffset',0);stride=bv.get('byteStride',12)
verts=[struct.unpack_from('<fff',raw,start+i*stride) for i in range(a['count'])]
lo=[min(p[i] for p in verts) for i in range(3)];hi=[max(p[i] for p in verts) for i in range(3)]
center=[(a+b)/2 for a,b in zip(lo,hi)];extent=[(b-a)/2 for a,b in zip(lo,hi)]
# Canonical schema is Y-up; import converts it to Blender Z-up.
bpy.ops.wm.read_factory_settings(use_empty=True);bpy.ops.import_scene.gltf(filepath=str(asset))
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=8;scene.cycles.use_denoising=False
scene.render.image_settings.file_format='PNG';scene.render.resolution_percentage=100
scene.world=bpy.data.worlds.new('IndependentReviewWorld');scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.008,.014,.028,1);scene.world.node_tree.nodes['Background'].inputs[1].default_value=.3
scene.view_settings.view_transform='AgX'
camdata=bpy.data.cameras.new('IndependentReviewCamera');cam=bpy.data.objects.new('IndependentReviewCamera',camdata);scene.collection.objects.link(cam);scene.camera=cam;camdata.type='ORTHO'
def point(x,y,z):return Vector((x,-z,y))
for name,pos,power,color in [('Key',(.4,.6,.7),4,(.65,.85,1)),('Fill',(-.4,.3,.5),1.5,(.55,.6,1))]:
    data=bpy.data.lights.new(name,'AREA');data.energy=power;data.shape='DISK';data.size=.5;data.color=color
    obj=bpy.data.objects.new(name,data);scene.collection.objects.link(obj);obj.location=point(*pos)
    obj.rotation_euler=(point(0,.28,.04)-obj.location).to_track_quat('-Z','Y').to_euler()

from mathutils import Matrix
import math
arm=next(o for o in bpy.data.objects if o.type=='ARMATURE')
jaw=bpy.data.objects['Jaw'];jaw_rest=jaw.matrix_world.copy();jaw_pivot=jaw_rest.translation.copy()
meshes=[bpy.data.objects['Face'],bpy.data.objects['NeuralThreads']]
scene.render.resolution_x=558;scene.render.resolution_y=780
aspect=279/390
vertical=max(focus['halfHeight'],focus['halfWidth']/aspect)*1.08
target=[focus['centerX'],focus['centerY'],focus['centerZ']]
camdata.ortho_scale=2*vertical
frame=camdata.view_frame(scene=scene);camdata.ortho_scale*=vertical*2/(max(v.y for v in frame)-min(v.y for v in frame))
cam.location=point(target[0],target[1],target[2]+2)
cam.rotation_euler=(point(*target)-cam.location).to_track_quat('-Z','Y').to_euler()
records=[]
for q in [0.0,0.5,1.0]:
    for pb in arm.pose.bones:pb.matrix_basis=Matrix.Identity(4)
    for obj in meshes:
        for key in obj.data.shape_keys.key_blocks:key.value=0
        obj.data.shape_keys.key_blocks['AA'].value=1
        obj.data.shape_keys.key_blocks['JawOpen'].value=q
    jaw.matrix_world=Matrix.Translation(jaw_pivot)@Matrix.Rotation(math.radians(12*q),4,'X')@Matrix.Translation(-jaw_pivot)@jaw_rest
    bpy.context.view_layer.update()
    name='AA1-JawOpen'+str(q).replace('.','_')+'-Jaw'+str(12*q).replace('.','_')
    scene.render.filepath=str(out/(name+'.png'));bpy.ops.render.render(write_still=True)
    record={'file':name+'.png','AA':1.0,'JawOpen':q,'jawPitchDegrees':12*q,'headYawPitchDegrees':[0,0],'otherMorphs':0,'pngSha256':hashlib.sha256((out/(name+'.png')).read_bytes()).hexdigest()}
    records.append(record);print('REACHABLE_POSE_RENDERED',json.dumps(record),flush=True)
(out/'runtime-family-manifest.json').write_text(json.dumps({'assetSha256':hashlib.sha256(raw).hexdigest(),'sourceCommit':'853a8ee0','mapperFileSha256':'d12224172eb34e96fb198454f7329b42858bf0504616bbcf4b43a5d1eaddb394','records':records,'scope':'Exact mapper geometry controls in Blender; no Android GPU validation'},indent=2)+'\n')
