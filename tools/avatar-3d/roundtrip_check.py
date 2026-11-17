"""Render the actual portable runtime GLB after Blender's independent import.

Invoke with the source .blend only to reuse its documented camera and lights:
blender -b assets/avatar/jarvis-research-v1.blend -t 2 --python tools/avatar-3d/roundtrip_check.py
The source mesh is deleted before the runtime GLB is imported.
"""
import bpy, pathlib, json, hashlib
from mathutils import Vector
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/avatar-live/geometry-work'
asset=ROOT/'assets/avatar/jarvis-runtime-v1.glb'
for obj in list(bpy.data.objects):
    if obj.type not in ['LIGHT','CAMERA']:bpy.data.objects.remove(obj,do_unlink=True)
bpy.ops.import_scene.gltf(filepath=str(asset),import_pack_images=True)
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=32;scene.cycles.use_denoising=False
scene.render.threads_mode='FIXED';scene.render.threads=2
scene.render.resolution_x=640;scene.render.resolution_y=640;scene.render.resolution_percentage=100
camera=bpy.data.objects['PreviewCamera'];camera.location=(0,-.9,.23)
camera.rotation_euler=(Vector((0,-.025,.215))-camera.location).to_track_quat('-Z','Y').to_euler()
scene.camera=camera
def render(name,weights=None):
    for obj in bpy.data.objects:
        if obj.type=='MESH' and obj.data.shape_keys:
            for key in obj.data.shape_keys.key_blocks:key.value=(weights or {}).get(key.name,0)
    scene.render.filepath=str(OUT/(name+'.png'));bpy.ops.render.render(write_still=True)
render('roundtrip-runtime-front')
render('roundtrip-runtime-blink',{'BlinkLeft':1,'BlinkRight':1})
render('roundtrip-runtime-smile-aa',{'Smile':.6,'AA':.6})
report={'assetSha256':hashlib.sha256(asset.read_bytes()).hexdigest(),'sourceMeshesDeletedBeforeImport':True,
        'importedMeshes':[{'name':o.name,'vertices':len(o.data.vertices),'shapeKeys':[k.name for k in o.data.shape_keys.key_blocks]if o.data.shape_keys else []}for o in bpy.data.objects if o.type=='MESH'],
        'armatures':[{'name':o.name,'bones':[b.name for b in o.data.bones]}for o in bpy.data.objects if o.type=='ARMATURE'],
        'renderViews':['roundtrip-runtime-front.png','roundtrip-runtime-blink.png','roundtrip-runtime-smile-aa.png'],
        'scope':'Actual runtime GLB import and CPU render; not Android GPU or physical phone acceptance.'}
(OUT/'roundtrip-import.json').write_text(json.dumps(report,indent=2)+'\n')
print('RUNTIME_GLTF_ROUNDTRIP_COMPLETE',flush=True)
