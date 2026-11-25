import bpy, sys, math, os, json
from mathutils import Vector
argv=sys.argv[sys.argv.index("--")+1:]; glb, outpng, maskjson, lod = argv[0], argv[1], argv[2], argv[3]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
scn=bpy.context.scene
for ob in scn.objects:
    if ob.type=='ARMATURE':
        ob.data.pose_position='REST'
    if ob.type=='MESH' and ob.name!='MeshyYouthBody': ob.hide_render=True
body=bpy.data.objects['MeshyYouthBody']; me=body.data
mask=json.load(open(maskjson))
red=bpy.data.materials.new("mask"); red.use_nodes=True; red.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value=(1,0.05,0.02,1)
me.materials.append(red); ri=len(me.materials)-1
# glTF import keeps triangle order for a single primitive (no merge of vertices across faces is done for indices order); verify count
import numpy as np
cnt=0
for side in "LR":
    faces=mask[f"{lod}_{side}"]["faces"]
    for fi in faces:
        if fi < len(me.polygons): me.polygons[fi].material_index=ri; cnt+=1
print("MASKED_FACES",cnt,"of",len(me.polygons))
w=bpy.data.worlds.new("w"); scn.world=w; w.use_nodes=True; w.node_tree.nodes["Background"].inputs[0].default_value=(0.8,0.8,0.85,1)
sun=bpy.data.objects.new("sun",bpy.data.lights.new("sun","SUN")); scn.collection.objects.link(sun); sun.rotation_euler=(math.radians(50),0,math.radians(30)); sun.data.energy=3
scn.render.engine='CYCLES'; scn.cycles.device='CPU'; scn.cycles.samples=16; scn.cycles.use_denoising=True
scn.render.resolution_x=900; scn.render.resolution_y=500
cam=bpy.data.objects.new("cam",bpy.data.cameras.new("cam")); scn.collection.objects.link(cam); scn.camera=cam
cam.data.type='ORTHO'; cam.data.ortho_scale=0.45
for name,loc,tgt in (("front",(0,-1,0.06),(0,0,0.06)),("below",(0,-0.02,-1),(0,-0.02,0))):
    cam.location=Vector(loc); cam.rotation_euler=(Vector(tgt)-cam.location).to_track_quat('-Z','Y').to_euler()
    scn.render.filepath=outpng.replace(".png",f"_{name}.png"); bpy.ops.render.render(write_still=True); print("RENDERED",scn.render.filepath)
