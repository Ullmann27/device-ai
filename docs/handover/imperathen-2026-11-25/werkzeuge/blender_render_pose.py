import bpy, sys, math, os
from mathutils import Vector
argv=sys.argv[sys.argv.index("--")+1:]; glb, outprefix = argv[0], argv[1]; shots=argv[2].split(",")  # clip:frame
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
scn=bpy.context.scene
rig=next(o for o in scn.objects if o.type=='ARMATURE')
for ob in scn.objects:
    if ob.type=='MESH' and ob.name!='MeshyYouthBody': ob.hide_render=True
print("ACTIONS",[a.name for a in bpy.data.actions])
bpy.ops.mesh.primitive_plane_add(size=3, location=(0,0,0)); fl=bpy.context.object
mat=bpy.data.materials.new("floor"); mat.use_nodes=True; mat.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value=(0.55,0.58,0.6,1); fl.data.materials.append(mat)
w=bpy.data.worlds.new("w"); scn.world=w; w.use_nodes=True; w.node_tree.nodes["Background"].inputs[0].default_value=(0.8,0.8,0.85,1)
sun=bpy.data.objects.new("sun",bpy.data.lights.new("sun","SUN")); scn.collection.objects.link(sun); sun.rotation_euler=(math.radians(45),0,math.radians(35)); sun.data.energy=3.5
scn.render.engine='CYCLES'; scn.cycles.device='CPU'; scn.cycles.samples=20; scn.cycles.use_denoising=True
scn.render.resolution_x=800; scn.render.resolution_y=800; scn.render.fps=60
cam=bpy.data.objects.new("cam",bpy.data.cameras.new("cam")); scn.collection.objects.link(cam); scn.camera=cam
cam.data.type='ORTHO'
for shot in shots:
    clip,frame,view=shot.split(":")
    act=next(a for a in bpy.data.actions if a.name.split("_")[0]==clip.split("_")[0] and a.name.startswith(clip))
    rig.animation_data.action=act
    if rig.animation_data.nla_tracks:
        for tr in rig.animation_data.nla_tracks: tr.mute=True
    scn.frame_set(int(frame))
    if view=="feet":
        cam.data.ortho_scale=0.6; loc=Vector((0.45,-0.75,0.5)); tgt=Vector((0,-0.05,0.05))
    else:
        cam.data.ortho_scale=1.9; loc=Vector((1.6,-2.6,1.2)); tgt=Vector((0,0,0.75))
    cam.location=loc; cam.rotation_euler=(tgt-loc).to_track_quat('-Z','Y').to_euler()
    scn.render.filepath=f"{outprefix}_{clip}_f{frame}_{view}.png"; bpy.ops.render.render(write_still=True); print("RENDERED",scn.render.filepath,act.name)
