"""Blender CPU stills of the existing packaged GLB, exact settled mapper weights.
HOST_RENDER_NOT_DEVICE. No app UI, video, generated face or reference-image edit.
Run: blender -b -t 2 --python scripts/render_contact.py
"""
from pathlib import Path
import hashlib
import json
import math
import time
import bpy
from mathutils import Vector, Quaternion

ROOT=Path(__file__).resolve().parents[1]
ASSET=ROOT/'source/p491/continuum-avatar.glb'
OUT=ROOT/'renders'
OUT.mkdir(exist_ok=True)
start=time.perf_counter()
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(ASSET))
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=12
scene.cycles.use_denoising=False;scene.cycles.seed=491503
scene.render.threads_mode='FIXED';scene.render.threads=2
scene.render.resolution_x=512;scene.render.resolution_y=576;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.world=bpy.data.worlds.new('HostReviewWorld');scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.001,.003,.012,1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value=.25
scene.view_settings.view_transform='AgX'
def point(x,y,z):return Vector((x,-z,y))
target=point(0,.275,.06)
for name,position,energy,color in [
    ('Key',(.35,.48,.62),5,(.66,.85,1)),
    ('Fill',(-.36,.30,.36),2,(.58,.45,1)),
    ('Rim',(.28,.38,-.12),3,(.25,.75,1))]:
    light=bpy.data.lights.new(name,'AREA');light.energy=energy;light.color=color;light.size=.35
    obj=bpy.data.objects.new(name,light);scene.collection.objects.link(obj);obj.location=point(*position)
    obj.rotation_euler=(target-obj.location).to_track_quat('-Z','Y').to_euler()
camdata=bpy.data.cameras.new('TechnicalCamera');camdata.type='ORTHO';camdata.ortho_scale=.28
cam=bpy.data.objects.new('TechnicalCamera',camdata);scene.collection.objects.link(cam);scene.camera=cam
cam.location=target+Vector((0,-.9,0));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler()
meshes=[o for o in scene.objects if o.type=='MESH' and o.data.shape_keys]
jaw=bpy.data.objects.get('Jaw')
assert jaw is not None,'Expected actual glTF Jaw node'
jaw.rotation_mode='QUATERNION';rest=jaw.rotation_quaternion.copy()
poses=[
    ('neutral','Neutral',{},0),
    ('aa','AA | jaw input 0.8',{'AA':1},.8125*12),
    ('o','O | jaw input 0.8',{'O':1,'JawOpen':.35},.8*12),
    ('pp_p503','PP #503 | jaw input 0.8',{'PP':1},0),
    ('blink','Blink L+R',{'BlinkLeft':1,'BlinkRight':1},0),
    ('pp_p491','PP #491 | jaw input 0.8',{'PP':1,'JawOpen':.8},.8*12),
]
records=[]
for name,label,weights,angle in poses:
    t=time.perf_counter()
    for obj in meshes:
        for key in obj.data.shape_keys.key_blocks:key.value=weights.get(key.name,0)
    jaw.rotation_quaternion=rest @ Quaternion((1,0,0),math.radians(angle))
    bpy.context.view_layer.update()
    path=OUT/(name+'.png');scene.render.filepath=str(path)
    bpy.ops.render.render(write_still=True)
    records.append({'name':name,'label':label,'weights':weights,'jawPitchDegrees':angle,
        'file':str(path.relative_to(ROOT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
        'durationSeconds':time.perf_counter()-t})
    print('HOST_FRAME',json.dumps(records[-1]),flush=True)
manifest={'evidenceClass':'HOST_RENDER_NOT_DEVICE','backend':f'Blender {bpy.app.version_string} Cycles CPU',
    'assetSha256':hashlib.sha256(ASSET.read_bytes()).hexdigest(),'samples':12,'threads':2,
    'framePixels':[512,576],'camera':'Orthographic frontal, same framing for all states',
    'originalMaterialsPreserved':True,'shapeKeyObjects':[o.name for o in meshes],
    'jawNode':jaw.name,'poses':records,'durationSeconds':time.perf_counter()-start,
    'limitations':['No Filament pixels, GPU/device FPS, live audio or camera proof',
        'Technical weights, not natural phoneme timing','No bloom composite; not final cockpit/UI',
        'No identity-acceptance metric','No geometry or reference face replaced']}
(OUT/'render-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
