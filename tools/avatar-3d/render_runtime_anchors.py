"""Bounded semantic anchors from the exact accepted runtime GLB.

Load the saved .blend only for its documented camera/lights. All source meshes,
rigs and empties are deleted; Blender's glTF importer supplies the actual model.
"""
import bpy, pathlib, json, hashlib, math
from mathutils import Matrix, Vector
from bpy_extras.object_utils import world_to_camera_view
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/avatar-live/geometry-work/runtime-anchors';OUT.mkdir(exist_ok=True)
asset=ROOT/'assets/avatar/jarvis-runtime-v1.glb'
EXPECTED='f39a89fc8b119487ab983151f8e56203c4adaa6b3aa9c5edbd0b937d1318f84e'
assert hashlib.sha256(asset.read_bytes()).hexdigest()==EXPECTED
for obj in list(bpy.data.objects):
    if obj.type not in ['LIGHT','CAMERA']:bpy.data.objects.remove(obj,do_unlink=True)
bpy.ops.import_scene.gltf(filepath=str(asset))
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=16;scene.cycles.use_denoising=False
scene.render.threads_mode='FIXED';scene.render.threads=2
scene.render.resolution_x=512;scene.render.resolution_y=512;scene.render.resolution_percentage=100
cam=bpy.data.objects['PreviewCamera'];scene.camera=cam;cam.data.type='ORTHO';cam.data.ortho_scale=.40
arm=next(o for o in bpy.data.objects if o.type=='ARMATURE')
empties={n:bpy.data.objects[n]for n in ['EyeLeft','EyeRight','Jaw']}
empty_rest={n:o.matrix_basis.copy()for n,o in empties.items()}
face=bpy.data.objects['Face'];threads=bpy.data.objects['NeuralThreads']
records=[]

def reset():
    for pb in arm.pose.bones:pb.matrix_basis=Matrix.Identity(4)
    for n,o in empties.items():o.matrix_basis=empty_rest[n].copy()
    for obj in [face,threads]:
        for k in obj.data.shape_keys.key_blocks:k.value=0
    bpy.context.view_layer.update()

def rotate_control(name,yaw=0,pitch=0,roll=0):
    rotation=Matrix.Rotation(math.radians(yaw),4,'Z')@Matrix.Rotation(math.radians(pitch),4,'X')@Matrix.Rotation(math.radians(-roll),4,'Y')
    if name in arm.pose.bones:
        pb=arm.pose.bones[name];world=arm.matrix_world@pb.matrix;p=world.translation.copy()
        pb.matrix=arm.matrix_world.inverted()@(Matrix.Translation(p)@rotation@Matrix.Translation(-p)@world)
    else:
        obj=empties[name];world=obj.matrix_world.copy();p=world.translation.copy()
        obj.matrix_world=Matrix.Translation(p)@rotation@Matrix.Translation(-p)@world
    bpy.context.view_layer.update()

def camera(azimuth=0,elevation=0):
    a=math.radians(azimuth);e=math.radians(elevation)
    target=Vector((0,-.025,.23))
    cam.location=target+Vector((math.sin(a)*math.cos(e),-math.cos(a)*math.cos(e),math.sin(e)))*.9
    cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler()

def frame(name,weights=None,azimuth=0,elevation=0,head=(0,0,0),eyes=(0,0),jaw=0):
    reset();camera(azimuth,elevation)
    for obj in [face,threads]:
        for n,w in (weights or {}).items():obj.data.shape_keys.key_blocks[n].value=w
    rotate_control('Head',*head)
    for n in ['EyeLeft','EyeRight']:rotate_control(n,yaw=eyes[0],pitch=eyes[1])
    rotate_control('Jaw',pitch=jaw)
    scene.render.filepath=str(OUT/(name+'.png'));bpy.ops.render.render(write_still=True)
    records.append({'file':name+'.png','weights':weights or {},'cameraAzimuthDegrees':azimuth,'cameraElevationDegrees':elevation,'headYawPitchRollDegrees':head,'eyesYawPitchDegrees':eyes,'innerJawPitchDegrees':jaw})
    print('RUNTIME_ANCHOR_COMPLETE',name,flush=True)

anchors=[
 ('01-neutral',{}),('02-midline-landmarks',{}),
 ('03-left45',{'azimuth':90/2}),('04-right45',{'azimuth':-45}),
 ('05-left90',{'azimuth':90}),('06-right90',{'azimuth':-90}),
 ('07-head-up',{'head':(0,-9,0)}),('08-head-down',{'head':(0,9,0)}),
 ('09-top-oblique',{'azimuth':35,'elevation':35}),('10-rear',{'azimuth':180}),
 ('11-subtle-smile',{'weights':{'Smile':.35}}),('12-broader-smile',{'weights':{'Smile':.85}}),
 ('13-attentive-brows',{'weights':{'BrowUp':.6}}),
 ('14-focused-side-gaze',{'weights':{'BrowDown':.35},'head':(5,0,0),'eyes':(-15,0)}),
 ('15-curious-approximation',{'weights':{'BrowUp':.4},'head':(0,0,7),'eyes':(8,-3)}),
 ('16-eyes-closed',{'weights':{'BlinkLeft':1,'BlinkRight':1}}),
 ('17-half-blink',{'weights':{'BlinkLeft':.5,'BlinkRight':.5}}),
 ('18-eyes-viewer-left',{'eyes':(-18,0)}),('19-eyes-viewer-right',{'eyes':(18,0)}),
 ('20a-eyes-up',{'eyes':(0,-12)}),('20b-eyes-down',{'eyes':(0,12)}),
 ('21-mouth-rest',{}),('22-PP',{'weights':{'PP':1}}),('23-FF',{'weights':{'FF':1}}),
 ('24-AA',{'weights':{'AA':1},'jaw':12.6}),('25-E',{'weights':{'E':1}}),
 ('26-I',{'weights':{'I':1}}),('27-O',{'weights':{'O':1},'jaw':6.3}),
 ('28-U',{'weights':{'U':1}}),('29-slight-jaw',{'weights':{'JawOpen':.2},'jaw':3.6}),
 ('30-smiling-speech',{'weights':{'Smile':.55,'AA':.5},'jaw':7}),
]
for name,kwargs in anchors:
    frame(name,**kwargs)
    if name=='02-midline-landmarks':
        verts=[face.matrix_world@v.co for v in face.data.vertices]
        nearest=lambda p:min(verts,key=lambda q:(q-Vector(p)).length)
        landmarks={'EyeLeft pivot':empties['EyeLeft'].matrix_world.translation,'EyeRight pivot':empties['EyeRight'].matrix_world.translation,
                   'Nose surface':nearest((0,-.167,.263)),'Lip surface':nearest((0,-.151,.226)),'Chin surface':nearest((0,-.143,.192)),
                   'Midline top':Vector((0,-.2,.392)),'Midline bottom':Vector((0,-.2,.18))}
        projected={}
        for label,p in landmarks.items():
            q=world_to_camera_view(scene,cam,p);projected[label]={'worldBlender':list(p),'pixel':[q.x*512,(1-q.y)*512]}
        (OUT/'landmarks.json').write_text(json.dumps({'assetSha256':EXPECTED,'method':'Control pivots and actual mesh vertices nearest authored anatomical estimates; illustration, not biometrics','points':projected},indent=2)+'\n')
for i in range(12):frame('orbit-%02d-%03d'%(i,i*30),azimuth=i*30)
report={'runtimeAssetSha256':EXPECTED,'sourceMeshesDeletedBeforeImport':True,'importer':'Blender4.3.2 glTF2 importer','semanticAnchorCount':30,'anchorRenderCount':31,'orbitFrameCount':12,'pixels':[512,512],'samples':16,'cpuThreads':2,'cameraOrthoScaleMetres':.40,
        'records':records,'controlImplementation':'Head pose bone; EyeLeft/EyeRight/Jaw imported glTF empties; world-pivot bounded rotations; named imported morph keys',
        'limitations':['Appearance not owner accepted','512px is not high resolution','Curious/focused/attentive are deliberate bounded approximations using available controls','Bigger smile scales existing Smile target; no independent cheek-smile target','Anchor20 is two subviews: up and down','No phonetic accuracy or phone-performance claim']}
(OUT/'runtime-anchor-manifest.json').write_text(json.dumps(report,indent=2)+'\n')
print('RUNTIME_ANCHORS_COMPLETE',EXPECTED,flush=True)
