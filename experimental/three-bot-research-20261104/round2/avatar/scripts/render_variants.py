"""Render actual original + locally derived material GLBs with identical camera/lights.
Usage: blender -b -t 2 --python scripts/render_variants.py -- [--pose neutral]
All output is HOST_RENDER_NOT_DEVICE. No new face or cockpit renderer.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import sys
import time
import bpy
from mathutils import Vector,Quaternion

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--pose',choices=['neutral','speech','blink','all'],default='all')
parser.add_argument('--samples',type=int,default=24)
args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
assert 4<=args.samples<=64
manifest=json.loads((ROOT/'results/variant-manifest.json').read_text())
assets=[('original','Original GLB',Path(manifest['source']),manifest['sourceSha256BeforeAndAfter'])]
assets += [(v['id'],v['parameters']['label'],ROOT/v['file'],v['sha256']) for v in manifest['variants']]
poses=[('neutral','Neutral',{},0),
       ('speech','Moderates AA',{'AA':.38,'JawOpen':.11125},5.04),
       ('blink','Blink',{'BlinkLeft':1,'BlinkRight':1},0)]
if args.pose!='all':poses=[p for p in poses if p[0]==args.pose]
out=ROOT/'renders';out.mkdir(parents=True,exist_ok=True)
records=[];start=time.perf_counter()
def point(x,y,z):return Vector((x,-z,y))
for aid,alabel,asset,expected in assets:
    assert hashlib.sha256(asset.read_bytes()).hexdigest()==expected
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(asset))
    scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.device='CPU'
    scene.cycles.samples=args.samples;scene.cycles.use_denoising=False;scene.cycles.seed=491503
    scene.render.threads_mode='FIXED';scene.render.threads=2
    scene.render.resolution_x=512;scene.render.resolution_y=576;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG';scene.render.film_transparent=False
    scene.world=bpy.data.worlds.new('SharedHostWorld');scene.world.use_nodes=True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.001,.003,.012,1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value=.25
    scene.view_settings.view_transform='AgX';scene.view_settings.exposure=0
    target=point(0,.275,.06)
    for name,position,energy,color in [
        ('Key',(.35,.48,.62),5,(.66,.85,1)),
        ('Fill',(-.36,.30,.36),2,(.58,.45,1)),
        ('Rim',(.28,.38,-.12),3,(.25,.75,1))]:
        light=bpy.data.lights.new(name,'AREA');light.energy=energy;light.color=color;light.size=.35
        obj=bpy.data.objects.new(name,light);scene.collection.objects.link(obj);obj.location=point(*position)
        obj.rotation_euler=(target-obj.location).to_track_quat('-Z','Y').to_euler()
    camdata=bpy.data.cameras.new('SharedHostCamera');camdata.type='ORTHO';camdata.ortho_scale=.28
    cam=bpy.data.objects.new('SharedHostCamera',camdata);scene.collection.objects.link(cam);scene.camera=cam
    cam.location=target+Vector((0,-.9,0));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler()
    meshes=[o for o in scene.objects if o.type=='MESH' and o.data.shape_keys]
    assert sorted(len(o.data.shape_keys.key_blocks)-1 for o in meshes)==[21,21]
    jaw=bpy.data.objects.get('Jaw');assert jaw is not None
    jaw.rotation_mode='QUATERNION';rest=jaw.rotation_quaternion.copy()
    for pid,plabel,weights,angle in poses:
        began=time.perf_counter()
        for obj in meshes:
            for key in obj.data.shape_keys.key_blocks:key.value=weights.get(key.name,0)
        jaw.rotation_quaternion=rest @ Quaternion((1,0,0),math.radians(angle))
        bpy.context.view_layer.update()
        path=out/f'{aid}-{pid}.png';scene.render.filepath=str(path)
        bpy.ops.render.render(write_still=True)
        record={'variant':aid,'variantLabel':alabel,'pose':pid,'poseLabel':plabel,'file':str(path.relative_to(ROOT)),
            'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'assetSha256':expected,
            'weights':weights,'jawPitchDegrees':angle,'durationSeconds':time.perf_counter()-began}
        records.append(record);print('HOST_VARIANT_FRAME',json.dumps(record),flush=True)
    assert hashlib.sha256(asset.read_bytes()).hexdigest()==expected
result={'evidenceClass':'HOST_RENDER_NOT_DEVICE','renderer':f'Blender {bpy.app.version_string} Cycles CPU',
    'samples':args.samples,'cpuThreads':2,'pixels':[512,576],'sameCameraLightsExposureAndSeed':True,
    'camera':{'projection':'orthographic','scaleMetres':.28,'targetCanonical':[0,.275,.06],
        'positionCanonical':[0,.275,.96]},'noBloomOrImagePostProcessing':True,
    'poseBasis':'Moderate AA .38, measured-jaw input modeled as .42; shared-opening compensation .42-.38*.8125=.11125',
    'frames':records,'durationSeconds':time.perf_counter()-start,
    'limits':['Static poses, not live speech','No Fold7 or Filament execution','Reference likeness is not certified']}
(ROOT/'results'/f'render-manifest-{args.pose}.json').write_text(json.dumps(result,indent=2)+'\n')
