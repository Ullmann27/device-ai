"""Build the standalone Continuum working display in Blender 4.3+.

Run:
  blender -b --python build_continuum_workdisplay.py -- --output-dir /path/to/output

The editable .blend retains separate geometry, materials, collections and text.
The GLB contains the display asset only, without studio floor, camera or lights.
No avatar or energy-orb files are loaded or changed.
"""
import argparse
import json
import math
import os
import sys
from pathlib import Path

import bpy
import bmesh
from mathutils import Vector


argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
parser = argparse.ArgumentParser()
parser.add_argument('--output-dir', default=str(Path(__file__).resolve().parent))
parser.add_argument('--skip-renders', action='store_true')
args = parser.parse_args(argv)
OUT = Path(args.output_dir).resolve()
OUT.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.context.preferences.filepaths.save_version = 0
scene = bpy.context.scene
scene.unit_settings.system = 'METRIC'
scene.unit_settings.scale_length = 1.0


def collection(name, parent=None):
    c = bpy.data.collections.new(name)
    (parent.children if parent else scene.collection.children).link(c)
    return c


asset = collection('CONTINUUM_Arbeitsdisplay_ASSET')
frame_col = collection('01_Metallfassung_und_Materialstaerke', asset)
glass_col = collection('02_Optisches_Rauchglas', asset)
rear_col = collection('03_Rueckplatte_und_Bahnen', asset)
content_col = collection('04_Inhalt_Sechs_Phasen_ENTWURFSWERTE', asset)
light_col = collection('05_Cyan_Lichtkanten', asset)
studio_col = collection('STUDIO_NUR_RENDER_NICHT_GLB')
root = bpy.data.objects.new('CONTINUUM_ARBEITSDISPLAY_ROOT', None)
asset.objects.link(root)
root['asset_role'] = 'Standalone editable working-display body; no avatar or orb'
root['dimensions_m'] = [1.20, 0.047, 0.74]
root['content_note'] = 'Six phase timing values describe the design choreography, not measured AI runtime.'
root['orientation'] = 'Blender: X horizontal, Y thickness, Z vertical; front faces -Y. GLB exported Y-up.'


def material(name, color, metallic=0.0, roughness=0.4, emission=None,
             strength=0.0, transmission=0.0, alpha=1.0, ior=1.45):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes.get('Principled BSDF')
    p.inputs['Base Color'].default_value = (*color, alpha)
    p.inputs['Metallic'].default_value = metallic
    p.inputs['Roughness'].default_value = roughness
    p.inputs['IOR'].default_value = ior
    p.inputs['Alpha'].default_value = alpha
    p.inputs['Transmission Weight'].default_value = transmission
    if emission:
        p.inputs['Emission Color'].default_value = (*emission, 1)
        p.inputs['Emission Strength'].default_value = strength
    if 'Coat Weight' in p.inputs:
        p.inputs['Coat Weight'].default_value = .26 if metallic else .12
        p.inputs['Coat Roughness'].default_value = .16
    m.diffuse_color = (*color, alpha)
    if alpha < 1 and hasattr(m, 'surface_render_method'):
        m.surface_render_method = 'DITHERED'
    return m


M = {
    'frame': material('METAL_Obsidian_Titanium', (.027, .050, .068), .84, .24),
    'bezel': material('METAL_Brushed_Graphite_Bezel', (.083, .122, .151), .9, .28),
    'edge': material('METAL_Polished_Edge', (.28, .39, .45), .94, .16),
    'rear': material('METAL_Rear_DarkGraphite', (.019, .032, .046), .75, .32),
    'inlay': material('INLAY_Deep_Obsidian', (.004, .010, .018), .25, .27),
    'glass': material('GLASS_Blue_Smoke_Transmission', (.12, .28, .35), .025, .075, transmission=.72, alpha=.28, ior=1.45),
    'content': material('CONTENT_Recessed_DeepBlue', (.004, .018, .032), .20, .22),
    'cyan': material('LIGHT_Electric_Cyan', (.015, .45, .62), .24, .19, (.025, .77, 1.0), 2.6),
    'cyan_soft': material('LIGHT_Cyan_Technical_Traces', (.019, .18, .25), .4, .23, (.018, .52, .75), .95),
    'violet': material('LIGHT_Indigo_Secondary', (.20, .12, .39), .15, .24, (.36, .22, .78), 1.0),
    'amber': material('LIGHT_Amber_Confirmation', (.62, .28, .08), .12, .25, (.93, .48, .15), .85),
    'white': material('TYPE_Ice_White', (.63, .82, .90), .0, .4, (.67, .87, .95), .45),
    'muted': material('TYPE_Muted_Blue', (.13, .28, .39), .0, .5, (.13, .32, .47), .28),
    'plot': material('PLOT_Quiet_Grid', (.02, .08, .12), .15, .4, (.02, .09, .14), .2),
}


def link_object(obj, col, parent=root):
    col.objects.link(obj)
    obj.parent = parent
    return obj


def contour(width, height, radius, steps=12):
    hw, hh = width / 2, height / 2
    corners = [(hw-radius, hh-radius, 0), (-hw+radius, hh-radius, 90),
               (-hw+radius, -hh+radius, 180), (hw-radius, -hh+radius, 270)]
    return [(cx + radius*math.cos(math.radians(start+90*i/steps)),
             cz + radius*math.sin(math.radians(start+90*i/steps)))
            for cx, cz, start in corners for i in range(steps+1)]


def finish_mesh(name, vertices, faces, col, mat, bevel=.0):
    mesh = bpy.data.meshes.new(name+'_Mesh')
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    bm = bmesh.new(); bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    bm.to_mesh(mesh); bm.free()
    obj = link_object(bpy.data.objects.new(name, mesh), col)
    obj.data.materials.append(mat)
    if bevel:
        mod = obj.modifiers.new('Editable_Precision_Bevel', 'BEVEL')
        mod.width = bevel
        mod.segments = 3
        mod.affect = 'EDGES'
        mod.limit_method = 'ANGLE'
        mod.angle_limit = .35
        normals = obj.modifiers.new('Weighted_Surface_Normals', 'WEIGHTED_NORMAL')
        normals.keep_sharp = True
        for polygon in mesh.polygons: polygon.use_smooth = True
    return obj


def ring(name, outer_w, outer_h, inner_w, inner_h, outer_r, inner_r,
         front, back, col, mat, bevel=0):
    a = contour(outer_w, outer_h, outer_r)
    b = contour(inner_w, inner_h, inner_r)
    n = len(a)
    v = [(x,y,z) for y,loop in [(front,a),(front,b),(back,a),(back,b)] for x,z in loop]
    f = []
    for i in range(n):
        j=(i+1)%n
        f.extend([(i,j,n+j,n+i), (2*n+j,2*n+i,3*n+i,3*n+j),
                  (j,i,2*n+i,2*n+j), (n+i,n+j,3*n+j,3*n+i)])
    return finish_mesh(name,v,f,col,mat,bevel)


def slab(name, width, height, radius, front, back, col, mat, bevel=0, x=0,z=0):
    points=contour(width,height,radius,8)
    n=len(points)
    v=[(px+x,y,pz+z) for y in (front,back) for px,pz in points]
    f=[tuple(range(n)),tuple(range(2*n-1,n-1,-1))]
    f += [(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
    return finish_mesh(name,v,f,col,mat,bevel)


def trace(name, points, col, mat, radius=.0008):
    data=bpy.data.curves.new(name+'_Curve','CURVE')
    data.dimensions='3D';data.resolution_u=1;data.bevel_depth=radius;data.bevel_resolution=2
    spline=data.splines.new('POLY');spline.points.add(len(points)-1)
    for p,co in zip(spline.points,points):p.co=(*co,1)
    obj=link_object(bpy.data.objects.new(name,data),col)
    data.materials.append(mat)
    return obj


font_dir=Path(__file__).resolve().parent/'fonts'
if not font_dir.exists():font_dir=Path(__file__).resolve().parent.parent/'continuum-preview'/'assets'/'fonts'
font_body=bpy.data.fonts.load(str(font_dir/'manrope-0.ttf')) if (font_dir/'manrope-0.ttf').exists() else None
font_heading=bpy.data.fonts.load(str(font_dir/'space-grotesk-5.ttf')) if (font_dir/'space-grotesk-5.ttf').exists() else font_body


def text(name,body,x,y,z,size,mat,back=False,heading=False,align='LEFT'):
    curve=bpy.data.curves.new(name+'_EditableText','FONT')
    curve.body=body;curve.size=size;curve.align_x=align;curve.align_y='TOP_BASELINE';curve.extrude=.000045
    curve.resolution_u=6
    font=font_heading if heading else font_body
    if font:curve.font=font
    obj=link_object(bpy.data.objects.new(name,curve),rear_col if back else content_col)
    obj.location=(x,y,z);obj.rotation_euler=(math.pi/2,0,math.pi if back else 0)
    curve.materials.append(mat)
    return obj


# Explicit physical layers. Their names and modifiers remain editable in .blend.
ring('FRAME_Primary_ObsidianMetal',1.20,.74,1.106,.646,.043,.021,-.019,.018,frame_col,M['frame'],.0025)
ring('FRAME_Front_BrushedBezel',1.194,.734,1.116,.656,.040,.022,-.022,-.0193,frame_col,M['bezel'],.0008)
ring('FRAME_Inner_OpticalLip',1.120,.660,1.110,.650,.024,.019,-.0228,-.020,frame_col,M['edge'],.00035)
ring('WALL_Separate_MaterialThickness',1.190,.730,1.170,.710,.039,.030,-.017,.019,frame_col,M['edge'],.0012)
slab('GLASS_Separate_BlueSmoke_Front',1.110,.650,.019,-.0202,-.0180,glass_col,M['glass'],.0005)
slab('CONTENT_Recessed_Panel',1.098,.638,.017,-.0150,-.0125,content_col,M['content'],.0005)
slab('REAR_Structural_GraphitePlate',1.170,.710,.032,.0189,.0222,rear_col,M['rear'],.0012)
ring('REAR_Perimeter_Inlay',1.152,.692,1.146,.686,.027,.024,.0223,.0230,rear_col,M['inlay'],.0002)
ring('LIGHT_Front_Perimeter_Cyan',1.192,.732,1.188,.728,.039,.037,-.0227,-.0222,light_col,M['cyan'],.0002)
ring('LIGHT_Inner_Optical_Cyan',1.115,.655,1.112,.652,.0215,.020,-.0228,-.0223,light_col,M['cyan_soft'],.00015)
ring('LIGHT_Rear_Edge_Cyan',1.164,.704,1.161,.701,.030,.0285,.0224,.0231,light_col,M['cyan_soft'],.00015)

# Deliberately separated rear paths, fastening points and inset thermal details.
rear_paths=[
    [(-.51,.0236,.26),(-.30,.0236,.26),(-.26,.0236,.22),(-.26,.0236,.13)],
    [(.51,.0236,.26),(.31,.0236,.26),(.26,.0236,.21),(.26,.0236,.13)],
    [(-.51,.0236,-.26),(-.31,.0236,-.26),(-.27,.0236,-.22),(-.27,.0236,-.12)],
    [(.51,.0236,-.26),(.32,.0236,-.26),(.27,.0236,-.21),(.27,.0236,-.12)],
    [(-.49,.0236,.02),(-.36,.0236,.02),(-.32,.0236,-.02),(-.32,.0236,-.08)],
    [(.49,.0236,.02),(.36,.0236,.02),(.32,.0236,-.02),(.32,.0236,-.08)],
]
for i,p in enumerate(rear_paths):trace(f'REAR_Trace_{i+1:02d}',p,rear_col,M['cyan_soft'],.00085)
for x in (-.548,.548):
    for z in (-.312,.312):
        bpy.ops.mesh.primitive_cylinder_add(vertices=12,radius=.0043,depth=.0011,location=(x,.0231,z),rotation=(math.pi/2,0,0))
        bolt=bpy.context.object;bolt.name=f'REAR_Fastener_{x:+.3f}_{z:+.3f}'
        for c in list(bolt.users_collection):c.objects.unlink(bolt)
        rear_col.objects.link(bolt);bolt.parent=root;bolt.data.materials.append(M['edge'])
for i in range(5):
    slab(f'REAR_Thermal_Inset_{i+1:02d}',.125,.006,.002,.0223,.0228,rear_col,M['inlay'],x=(i-2)*.142,z=-.185)
text('REAR_Identity','CONTINUUM',0,.0242,.061,.039,M['muted'],back=True,heading=True,align='CENTER')
text('REAR_Identity_Subline','WISSEN / PRAESENZ / HANDLUNG',0,.0242,.011,.0105,M['muted'],back=True,align='CENTER')
text('REAR_Material_Identification','ARBEITSDISPLAY  /  1.20 m',0,.0242,-.073,.012,M['muted'],back=True,align='CENTER')

# Exact design-phase values from the interactive preview, no invented measurements.
text('CONTENT_Header','CONTINUUM / ARBEITSRAUM',-.505,-.0165,.268,.021,M['white'],heading=True)
text('CONTENT_Subtitle','SECHS PHASEN  /  950 ms',-.505,-.0165,.225,.018,M['cyan_soft'],heading=True)
text('CONTENT_Provenance','Entwurfswerte - keine Laufzeitmessung',-.505,-.0165,.192,.0105,M['muted'])
trace('CONTENT_HeaderRule',[(-.505,-.0168,.177),(.505,-.0168,.177)],content_col,M['plot'],.00045)
phases=[('Aufmerksamkeit',0,120),('Raum schaffen',90,260),('Licht wird Form',180,480),
        ('Einflug',360,760),('Inhalt',620,900),('Arbeit',900,950)]
plot_start=-.205;plot_width=.645
for tick in (0,250,500,750,1000):
    x=plot_start+tick/1000*plot_width
    trace(f'CONTENT_Axis_{tick:04d}',[(x,-.0167,-.209),(x,-.0167,.143)],content_col,M['plot'],.00035)
    text(f'CONTENT_AxisLabel_{tick:04d}',str(tick),x,-.0174,-.244,.0095,M['muted'],align='CENTER')
for i,(name,start,end) in enumerate(phases):
    z=.125-i*.062
    text(f'CONTENT_PhaseLabel_{i+1:02d}',f'{i+1:02d}  {name}',-.505,-.0174,z-.006,.0125,M['white'] if i==2 else M['muted'])
    x0=plot_start+start/1000*plot_width;x1=plot_start+end/1000*plot_width
    bar=slab(f'CONTENT_PhaseBar_{i+1:02d}_{start}_{end}ms',x1-x0,.019,.0035,-.0181,-.0169,content_col,
             M['cyan'] if i==2 else M['amber'] if i==5 else M['violet'] if i==3 else M['cyan_soft'],x=(x0+x1)/2,z=z)
    bar['phase_start_ms']=start;bar['phase_end_ms']=end;bar['meaning']='Design choreography, not measured processing duration'
    text(f'CONTENT_EndValue_{i+1:02d}',str(end),.485,-.0182,z-.005,.0125,M['white'],align='CENTER')
text('CONTENT_Footnote','ARGON4 / DESIGN-DEMONSTRATION',-.505,-.0174,-.283,.010,M['muted'])
text('CONTENT_Units','ms',.485,-.0174,-.244,.0095,M['muted'],align='CENTER')

# Studio scene, explicitly excluded from export.
def move_to_studio(obj):
    for c in list(obj.users_collection):c.objects.unlink(obj)
    studio_col.objects.link(obj)
    return obj


floor_mat=material('STUDIO_Matte_Obsidian',(.008,.014,.024),.18,.39)
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.49))
floor=move_to_studio(bpy.context.object);floor.name='STUDIO_Floor';floor.data.materials.append(floor_mat)


def look_at(obj,point):
    obj.rotation_euler=(Vector(point)-obj.location).to_track_quat('-Z','Y').to_euler()


def area(name,pos,energy,color,size,target=(0,0,0)):
    data=bpy.data.lights.new(name,'AREA');data.energy=energy;data.color=color;data.shape='DISK';data.size=size
    obj=bpy.data.objects.new(name,data);studio_col.objects.link(obj);obj.location=pos;look_at(obj,target)
    return obj


area('STUDIO_Key_SoftNeutral',(-1.4,-1.8,2.0),180,(.80,.91,1.0),1.5)
area('STUDIO_Fill_Cyan',(1.5,-.9,.7),85,(.24,.65,1.0),1.2)
area('STUDIO_Rim_Indigo',(.6,1.4,1.4),160,(.38,.29,1.0),1.1)
area('STUDIO_Rear_Readability',(-.9,1.8,.9),130,(.62,.80,1.0),1.3)
world=bpy.data.worlds.new('STUDIO_DeepBlue_World');world.use_nodes=True
world.node_tree.nodes['Background'].inputs['Color'].default_value=(.012,.022,.046,1)
world.node_tree.nodes['Background'].inputs['Strength'].default_value=.32;scene.world=world
camera_data=bpy.data.cameras.new('STUDIO_Camera');camera=bpy.data.objects.new('STUDIO_Camera',camera_data);studio_col.objects.link(camera)
camera_data.lens=59;camera_data.sensor_width=36;scene.camera=camera
camera.location=(1.36,-2.34,.86);look_at(camera,(0,0,-.005))
scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=32;scene.cycles.use_denoising=False
scene.cycles.max_bounces=8;scene.cycles.transmission_bounces=6;scene.cycles.transparent_max_bounces=8
scene.render.resolution_x=960;scene.render.resolution_y=600;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA';scene.render.film_transparent=False
scene.render.threads_mode='FIXED';scene.render.threads=8
scene.view_settings.view_transform='AgX';scene.view_settings.look='AgX - Medium High Contrast'
scene.render.fps=30
scene['asset_spec']='1.20 x 0.74 m working display; optical stack and physical sidewall; front faces -Y'
scene['renders']='CPU Cycles, 32 samples, 960x600; studio omitted from GLB'

# Preserve editable texts/curves in the .blend. Pack local fonts for portability.
bpy.ops.object.select_all(action='DESELECT');root.select_set(True);bpy.context.view_layer.objects.active=root
try:bpy.ops.file.pack_all()
except RuntimeError:pass
blend_path=OUT/'Continuum_Arbeitsdisplay.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(blend_path))

# Export evaluated mesh versions of curves/text without flattening the editable source.
asset_objects=list(asset.all_objects)
export_objects=[];temporary=[]
depsgraph=bpy.context.evaluated_depsgraph_get()
for obj in asset_objects:
    if obj.type in {'CURVE','FONT'}:
        evaluated=obj.evaluated_get(depsgraph)
        mesh=bpy.data.meshes.new_from_object(evaluated,depsgraph=depsgraph)
        dup=bpy.data.objects.new(obj.name,mesh);asset.objects.link(dup)
        dup.matrix_world=obj.matrix_world.copy();dup.parent=root;dup.matrix_parent_inverse=root.matrix_world.inverted()
        temporary.append(dup);export_objects.append(dup)
    else:export_objects.append(obj)
bpy.ops.object.select_all(action='DESELECT')
for obj in export_objects:obj.select_set(True)
bpy.context.view_layer.objects.active=root
bpy.ops.export_scene.gltf(filepath=str(OUT/'Continuum_Arbeitsdisplay.glb'),export_format='GLB',
                          use_selection=True,export_apply=True,export_yup=True,export_materials='EXPORT',
                          export_extras=True,export_cameras=False,export_lights=False)
for obj in temporary:bpy.data.objects.remove(obj,do_unlink=True)

report={
    'blender_version':bpy.app.version_string,
    'asset':'Continuum_Arbeitsdisplay',
    'dimensions_m':{'width':1.2,'height':.74,'nominal_depth':.047},
    'collections':[c.name for c in asset.children],
    'objects':[{'name':o.name,'type':o.type,'materials':[m.name for m in getattr(o.data,'materials',[]) if m]} for o in asset_objects],
    'materials':list(M.keys()),
    'phases':[{'name':name,'start_ms':start,'end_ms':end} for name,start,end in phases],
    'notes':['Timing values are design choreography, not measured device or AI performance.',
             'Avatar and energy sphere were not loaded or modified.',
             'Studio lights, camera and floor are excluded from GLB.',
             'BLEND preserves editable text, curves, bevels and distinct layers.']
}
(OUT/'Continuum_Arbeitsdisplay_Struktur.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
if not args.skip_renders:
    scene.render.filepath=str(OUT/'Continuum_Arbeitsdisplay_Vorne.png')
    bpy.ops.render.render(write_still=True)
    camera.location=(-1.36,2.34,.86);look_at(camera,(0,0,-.005))
    scene.render.filepath=str(OUT/'Continuum_Arbeitsdisplay_Hinten.png')
    bpy.ops.render.render(write_still=True)
print('CONTINUUM_DISPLAY_COMPLETE '+json.dumps({'blend':str(blend_path),'glb':str(OUT/'Continuum_Arbeitsdisplay.glb'),'source_objects':len(asset_objects),'materials':len(M)}))
