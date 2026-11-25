import bpy, sys, math, os
from mathutils import Vector
argv=sys.argv[sys.argv.index("--")+1:]; glb, outdir, tag = argv[0], argv[1], argv[2]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
scn=bpy.context.scene
# Stop animations: rest pose
for ob in scn.objects:
    if ob.type=='ARMATURE':
        ob.data.pose_position='REST'
        if ob.animation_data: ob.animation_data.action=None
# hide weapon meshes
for ob in scn.objects:
    if ob.type=='MESH' and 'Blade' in ob.name: ob.hide_render=True
print("OBJECTS",[ (o.name,o.type) for o in scn.objects])
mesh=[o for o in scn.objects if o.type=='MESH' and not o.hide_render]
# floor
bpy.ops.mesh.primitive_plane_add(size=2, location=(0,0,0)); fl=bpy.context.object
if os.environ.get('NOFLOOR'): fl.hide_render=True
mat=bpy.data.materials.new("floor"); mat.use_nodes=True
mat.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value=(0.55,0.6,0.65,1); fl.data.materials.append(mat)
w=bpy.data.worlds.new("w"); scn.world=w; w.use_nodes=True; w.node_tree.nodes["Background"].inputs[1].default_value=1.0
w.node_tree.nodes["Background"].inputs[0].default_value=(0.8,0.8,0.85,1)
sun=bpy.data.objects.new("sun",bpy.data.lights.new("sun","SUN")); scn.collection.objects.link(sun); sun.rotation_euler=(math.radians(50),0,math.radians(30)); sun.data.energy=3
scn.render.engine='CYCLES'; scn.cycles.device='CPU'; scn.cycles.samples=24; scn.cycles.use_denoising=True
scn.render.resolution_x=900; scn.render.resolution_y=700
cam=bpy.data.objects.new("cam",bpy.data.cameras.new("cam")); scn.collection.objects.link(cam); scn.camera=cam
def look(loc,target,lens=50,ortho=None):
    cam.location=Vector(loc); d=Vector(target)-cam.location; cam.rotation_euler=d.to_track_quat('-Z','Y').to_euler()
    if ortho: cam.data.type='ORTHO'; cam.data.ortho_scale=ortho
    else: cam.data.type='PERSP'; cam.data.lens=lens
import os
views={k:v for k,v in {
 "full_front":((0,-3.2,0.75),(0,0,0.75),None,1.8),
 "feet_front":((0,-1.0,0.06),(0,0,0.06),None,0.42),
 "feet_side_R":((-1.0,0.04,0.06),(0,0.04,0.06),None,0.42),
 "feet_back":((0,1.0,0.06),(0,0,0.06),None,0.42),
 "feet_below":((0,-0.02,-1.0),(0,-0.02,0.0),None,0.42),
 "feet_front_high":((0.35,-0.75,0.55),(0,-0.03,0.04),None,0.45),
}.items() if k in os.environ.get("VIEWS","full_front,feet_front,feet_side_R,feet_back").split(",")}
for name,(loc,tgt,lens,orth) in views.items():
    look(loc,tgt,ortho=orth); scn.render.filepath=f"{outdir}/{tag}_{name}.png"; bpy.ops.render.render(write_still=True)
    print("RENDERED",scn.render.filepath)
