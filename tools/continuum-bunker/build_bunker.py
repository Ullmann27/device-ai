"""Build the preserved Continuum stage."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys

import bpy
import bmesh
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
from glb_contract import preserve
REPO = HERE.parents[1]
BASE = HERE / 'source/Continuum_Mission_Desk_V3.glb'
AVATAR = REPO / 'app/src/main/assets/avatar3d/continuum-avatar.glb'
COLLECTIONS = {}
CURRENT = None
BOX_MESHES = {}


def collection(name):
    global CURRENT
    CURRENT = COLLECTIONS.get(name)
    print('BUILD_MODULE', name, flush=True)
    if CURRENT is None:
        CURRENT = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(CURRENT)
        COLLECTIONS[name] = CURRENT
    return CURRENT


def adopt(obj, name, material=None):
    obj.name = name
    for old in list(obj.users_collection):
        old.objects.unlink(obj)
    CURRENT.objects.link(obj)
    if material is not None:
        obj.data.materials.clear()
        obj.data.materials.append(material)
    return obj


def material(name, rgb, metal=0, rough=.45, glow=0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.diffuse_color = (*rgb, 1)
    bsdf = mat.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (*rgb, 1)
    bsdf.inputs['Metallic'].default_value = metal
    bsdf.inputs['Roughness'].default_value = rough
    bsdf.inputs['Emission Color'].default_value = (*rgb, 1)
    bsdf.inputs['Emission Strength'].default_value = glow
    return mat


def box(name, pos, size, mat, bevel=.025):
    key = (*size, bevel, mat.name)
    mesh = BOX_MESHES.get(key)
    if mesh is None:
        bm = bmesh.new()
        bmesh.ops.create_cube(bm, size=1)
        for v in bm.verts:
            v.co = Vector(tuple(v.co[i] * size[i] for i in range(3)))
        if bevel:
            bmesh.ops.bevel(bm, geom=list(bm.edges), offset=min(bevel,min(size)*.24),
                segments=2, affect='EDGES')
        bm.normal_update()
        mesh=bpy.data.meshes.new(name)
        bm.to_mesh(mesh)
        bm.free()
        mesh.materials.append(mat)
        BOX_MESHES[key]=mesh
    obj=bpy.data.objects.new(name,mesh)
    CURRENT.objects.link(obj)
    obj.location=pos
    return obj


def cylinder(name, pos, radius, depth, mat, axis=None, vertices=24):
    bm=bmesh.new()
    bmesh.ops.create_cone(bm,cap_ends=True,cap_tris=False,segments=vertices,
        radius1=radius,radius2=radius,depth=depth)
    mesh=bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    mesh.materials.append(mat)
    obj=bpy.data.objects.new(name,mesh)
    CURRENT.objects.link(obj)
    obj.location=pos
    if axis is not None:
        obj.rotation_euler = Vector(axis).to_track_quat('Z', 'Y').to_euler()
    return obj


def beam(name, a, b, width, mat):
    a, b = Vector(a), Vector(b)
    obj = box(name, (a + b) / 2, (width, width, (b-a).length), mat, width * .15)
    obj.rotation_euler = (b-a).to_track_quat('Z', 'Y').to_euler()
    return obj


def cable(name, points, radius, mat):
    curve = bpy.data.curves.new(name, 'CURVE')
    curve.dimensions = '3D'
    curve.resolution_u = 8
    curve.bevel_depth = radius
    curve.bevel_resolution = 1
    spline = curve.splines.new('BEZIER')
    spline.bezier_points.add(len(points)-1)
    for p, co in zip(spline.bezier_points, points):
        p.co = co
        p.handle_left_type = p.handle_right_type = 'AUTO'
    obj = bpy.data.objects.new(name, curve)
    CURRENT.objects.link(obj)
    obj.data.materials.append(mat)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.convert(target='MESH')
    obj.select_set(False)
    return obj


def text_mesh(name, text, pos, size, mat, rotation=(math.pi/2, 0, 0)):
    data = bpy.data.curves.new(name, 'FONT')
    data.body, data.size = text, size
    data.align_x = 'CENTER'
    data.extrude = .0006
    data.resolution_u = 3
    obj = bpy.data.objects.new(name, data)
    CURRENT.objects.link(obj)
    obj.location, obj.rotation_euler = pos, rotation
    data.materials.append(mat)
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.convert(target='MESH')
    obj.select_set(False)
    return obj


def empty(name, members):
    obj = bpy.data.objects.new(name, None)
    bpy.context.scene.collection.objects.link(obj)
    for child in members:
        if child.parent not in members:
            matrix = child.matrix_world.copy()
            child.parent = obj
            child.matrix_world = matrix
    return obj


def batch_collection(col):
    buckets = {}
    for obj in list(col.objects):
        if obj.type == 'MESH' and obj.animation_data is None:
            key = tuple(m.name for m in obj.data.materials)
            buckets.setdefault(key, []).append(obj)
    result = []
    for key, objects in buckets.items():
        bpy.ops.object.select_all(action='DESELECT')
        for obj in objects:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = objects[0]
        bpy.ops.object.join()
        joined = bpy.context.object
        joined.name = col.name + ' / ' + (key[0] if key else 'Geometry')
        result.append(joined)
    return result


def select_export(objects, path, animations=False):
    bpy.ops.object.select_all(action='DESELECT')
    for obj in objects:
        obj.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(path), export_format='GLB', use_selection=True,
        export_animations=animations, export_animation_mode='NLA_TRACKS',
        export_force_sampling=False, export_extras=True, export_cameras=False,
        export_lights=False, export_yup=True, export_texcoords=True,
        export_normals=True, export_materials='EXPORT')


def light(name, pos, target, color, power, size):
    data = bpy.data.lights.new(name, 'AREA')
    data.color, data.energy, data.shape, data.size = color, power, 'DISK', size
    obj = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = pos
    obj.rotation_euler = (Vector(target)-obj.location).to_track_quat('-Z', 'Y').to_euler()
    return obj


def build(output):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.unit_settings.system = 'METRIC'
    scene.render.fps = 24
    bpy.ops.import_scene.gltf(filepath=str(BASE))
    original = list(bpy.data.objects)
    collection('MissionDesk_V3_Preserved')
    for obj in original:
        adopt(obj, obj.name)
    original_action_names = [a.name for a in bpy.data.actions]
    workspace = empty('WorkspaceRoot', original)
    workspace['source_sha256'] = hashlib.sha256(BASE.read_bytes()).hexdigest()
    workspace.scale = (.62,)*3
    workspace.location.z = 1.15
    bpy.context.view_layer.update()

    steel = material('V04 Graphite steel', (.035,.060,.090), .78, .32)
    navy = material('V04 Navy composite', (.014,.027,.048), .38, .44)
    titanium = material('V04 Brushed titanium', (.16,.23,.32), .85, .28)
    concrete = material('V04 Basalt concrete', (.055,.074,.100), .12, .67)
    dark = material('V04 Cooling recess', (.006,.012,.022), .15, .63)
    cyan = material('V04 Cyan edge', (.025,.57,.78), .25, .31, 3.2)
    violet = material('V04 Violet edge', (.29,.065,.64), .25, .31, 3.2)
    blue = material('V04 Cobalt edge', (.045,.19,.55), .3, .3, 1.7)
    white = material('V04 Ice marking', (.48,.75,.91), .05, .46, .7)
    amber = material('V04 Amber caution', (.65,.28,.035), .2, .43, .5)
    screen = material('V04 Unbound screen', (.008,.025,.048), .12, .24, .22)

    original_materials = {m for o in original if o.type == 'MESH' for m in o.data.materials}
    for mat in original_materials:
        if mat.use_nodes:
            for node in mat.node_tree.nodes:
                if node.type == 'BSDF_PRINCIPLED':
                    emission=node.inputs['Emission Color'].default_value
                    if max(emission[:3])>0:
                        node.inputs['Emission Strength'].default_value *= .12
                        base=node.inputs['Base Color'].default_value
                        node.inputs['Base Color'].default_value=(*[v*.18 for v in base[:3]],base[3])

    stale_labels=[o for o in original if o.name in ['CONTINUUM Schrift','CONTINUUM Schrift.001',
        'System Anzeige','System Anzeige.001','Systemkennung']]
    for obj in stale_labels:
        original.remove(obj)
        bpy.data.objects.remove(obj,do_unlink=True)

    # Neutralize baked telemetry.
    for obj in original:
        if obj.type != 'MESH':
            continue
        if any('Live command' in m.name or 'live UI' in m.name or 'screen content' in m.name for m in obj.data.materials):
            obj.data.materials.clear()
            obj.data.materials.append(screen)
            obj['surface_role'] = 'preview_only; Android supplies live content'
        if obj.name.startswith('Hauptdisplay - 3D') and obj.parent == workspace:
            obj.scale.y *= .58
        if obj.parent == workspace and any(obj.name.startswith(p) for p in
                ['Hauptmonitor /', 'Monitor Rueckseite /', 'Rueckseite /', 'Hauptdisplay /']):
            for vertex in obj.data.vertices:
                vertex.co.y *= .58

    collection('MonitorEngineering')
    for x in [-1.92,-1.30]:
        beam('Main articulated arm', (x,.17,1.48), (x,.48,2.03), .10, titanium)
        beam('Main articulated arm', (x,.48,2.03), (x,.22,2.28), .10, steel)
        cylinder('Main hinge', (x,.48,2.03), .095,.14,titanium,(1,0,0))
        cylinder('Main hinge inset', (x+.075,.48,2.03), .062,.012,cyan,(1,0,0))
    box('Main VESA service block', (-1.61,.20,2.21),(.65,.13,.42),steel)
    cable('Main monitor service cable',[(-1.61,.25,2.12),(-1.61,.62,1.8),(-1.61,.62,1.23),(-2.2,.6,1.12)],.027,dark)
    box('Right monitor precision housing',(2.69,1.058,2.576),(1.22,.09,.96),steel)
    box('Right monitor backplate',(2.69,1.12,2.576),(1.10,.035,.83),navy)
    for i in range(11):
        box('Right cooling fin',(2.25+i*.085,1.15,2.63),(.026,.036,.48),titanium,.003)
    for i in range(4):
        box('Right I/O port',(2.45+i*.13,1.16,2.29),(.09,.02,.04),dark,.002)
    beam('Right floating arm',(2.69,1.2,1.2),(2.69,1.42,2.17),.10,steel)
    beam('Right floating arm',(2.69,1.42,2.17),(2.69,1.18,2.58),.10,titanium)
    cylinder('Right hinge',(2.69,1.42,2.17),.10,.15,titanium,(1,0,0))
    cable('Right service cable',[(2.69,1.2,2.3),(2.83,1.55,2.1),(2.8,1.6,1.14)],.022,dark)
    monitor_details = batch_collection(CURRENT)
    monitor_root = empty('MonitorEngineeringRoot', monitor_details)

    collection('PreviewSurfaceLabels')
    text_mesh('Preview heading','CONTINUUM  /  ARBEITSBEREICH',(-1.61,-.115,2.51),.115,white)
    text_mesh('Preview disclosure','3D VORSCHAU',(-1.61,-.115,2.19),.18,cyan)
    text_mesh('Preview source','ANDROID-INHALTE IN DER APP',(-1.61,-.115,1.92),.078,white)
    for x in [-2.50,-1.61,-.72]:
        box('Preview idle line',(x,-.112,1.62),(.58,.009,.014),blue,.001)
    text_mesh('Orb preview','CONTINUUM',(2.69,1.001,2.80),.118,white)
    text_mesh('Orb disclosure','3D VORSCHAU',(2.69,1.001,2.53),.11,violet)
    labels = list(CURRENT.objects)

    collection('FloorModules')
    box('Foundation',(0,2,-.20),(21,20,.38),dark)
    for row in range(10):
        for col in range(12):
            x,y = (col-5.5)*1.72, -6.4+row*1.95
            box('Floor cassette',(x,y,.005),(1.69,1.92,.10),steel if (row+col)%3 else navy,.035)
    for x in [-5.1,5.1]:
        for y in [-4,0,4,8]:
            box('Access duct',(x,y,.08),(.55,3.65,.12),dark)
            for j in range(12):
                box('Grille slat',(x,y-1.55+j*.28,.155),(.45,.045,.025),titanium,.003)
    for side,mat in [(-1,cyan),(1,violet)]:
        for y in [-4.6,-1.1,2.4,5.9,9.4]:
            box('Floor guide',(side*4.45,y,.076),(.035,2.92,.018),mat,.002)
    collection('DeskSupports')
    for x in [-3.2,3.2]:
        box('Desk foot',(x,.35,.35),(.45,2.9,.7),steel,.09)
        box('Desk foot guide',(x,-1.115,.25),(.34,.035,.10),cyan if x<0 else violet,.008)

    collection('BunkerStructure')
    box('Back armored wall',(0,11.7,3.9),(21,.4,7.8),concrete,.05)
    for side in [-1,1]:
        box('Side retaining wall',(side*10.4,2.5,3.65),(.38,18.8,7.3),concrete,.05)
        for y in [-5,0,5,10]:
            box('Structural pier',(side*9.6,y,2.6),(.55,.72,5.2),steel,.07)
            beam('Roof diagonal',(side*9.6,y,5.1),(side*7.35,y,7.45),.55,titanium)
            box('Roof crossbeam',(0,y,7.45),(14.9,.55,.5),steel,.05)
            beam('Rib inner edge',(side*9.27,y-.39,4.8),(side*7.18,y-.39,7.07),.045,cyan if side<0 else violet)
        for y in [-3.3,1.7,6.7]:
            box('Wall armored infill',(side*10.11,y,3.4),(.14,3.9,5.4),navy)
            for z in [.7,5.8]:
                box('Wall service rail',(side*9.96,y,z),(.15,3.8,.16),titanium)
        for z in [6.05,6.38]:
            beam('Longitudinal conduit',(side*9.6,-6,z),(side*9.6,11,z),.15,steel)
    for x in [-7.2,-4.8,4.8,7.2]:
        box('Back panel',(x,11.43,3.8),(2.23,.1,6.5),navy)
        box('Back light line',(x,11.35,3.8),(.035,.035,5.4),cyan if x<0 else violet,.002)
    box('Door inset',(0,11.39,2.6),(4.5,.20,5.2),dark)
    for side in [-1,1]:
        box('Door leaf',(side*1.03,11.23,2.45),(2.02,.12,4.7),steel)
        beam('Door diagonal',(side*2.32,11.13,4.4),(side*1.65,11.13,5.22),.22,titanium)
        box('Door jamb',(side*2.32,11.13,2.22),(.22,.22,4.44),titanium)
        box('Door seal',(side*.055,11.11,2.45),(.027,.027,4.4),cyan if side<0 else violet,.002)
    box('Door lintel',(0,11.13,5.22),(3.4,.22,.22),titanium)
    for x in [-1.3,1.3]:
        box('Door grip',(x,11.07,2.5),(.10,.10,.55),dark)
    text_mesh('Architecture title','C O N T I N U U M',(0,11.03,6.22),.47,white)
    text_mesh('Architecture caption','PRIVATE  /  LOCAL  /  WORKSPACE',(0,11.03,5.75),.15,blue)

    collection('ServerModules')
    for side in [-1,1]:
        accent = cyan if side<0 else violet
        for y in [2.8,6.3,9.4]:
            x=side*8.05
            box('Server cabinet',(x,y,1.9),(1.7,1.1,3.8),steel,.08)
            box('Server recessed front',(x,y-.57,1.97),(1.47,.05,3.42),dark)
            for unit in range(10):
                z=.48+unit*.315
                box('Replaceable server module',(x,y-.62,z),(1.36,.09,.25),navy)
                for vent in range(7):
                    box('Server ventilation',(x-.43+vent*.115,y-.67,z),(.052,.022,.12),titanium,.002)
                box('Server accent',(x+.51,y-.69,z),(.035,.02,.13),accent,.001)
            for z in [.12,3.7]:
                box('Rack rail',(x,y-.6,z),(1.52,.08,.065),titanium)
            cable('Rack power conduit',[(x,y+.4,3.85),(x,y+.9,4.35),(side*9.85,y+.9,4.35)],.055,dark)

    collection('VentilationModules')
    for x in [-6.2,6.2]:
        cylinder('Cooling fan surround',(x,11.15,4.75),.78,.18,titanium,(0,1,0),48)
        cylinder('Cooling fan recess',(x,11.02,4.75),.67,.10,dark,(0,1,0),48)
        for a in range(9):
            angle=a*math.tau/9
            cx,cz=x+math.cos(angle)*.36,4.75+math.sin(angle)*.36
            blade=box('Cooling fan blade',(cx,10.95,cz),(.13,.035,.48),steel,.008)
            blade.rotation_euler.y=-angle+.3
        cylinder('Cooling fan axle',(x,10.90,4.75),.15,.10,titanium,(0,1,0))
        for i in range(7):
            box('Fan safety grille',(x,10.84,4.23+i*.17),(1.05,.028,.024),titanium,.003)

    collection('LightFixtures')
    for side,mat in [(-1,cyan),(1,violet)]:
        for y in [-3,2,7]:
            box('Ceiling fixture',(side*5.8,y,7.15),(.34,3.1,.16),steel)
            box('Ceiling lens',(side*5.8,y,7.055),(.18,2.8,.028),mat,.003)
        for y in [-5,0,5,10]:
            box('Pillar emitter',(side*9.28,y-.39,2.7),(.04,.03,3.9),mat,.002)
    for y in [-2.5,2.5,7.5]:
        box('Overhead aperture',(0,y,7.45),(4.2,.50,.16),steel)
        box('Overhead diffuse strip',(0,y,7.35),(3.9,.34,.025),white,.002)

    collection('CableRouting')
    for side,mat in [(-1,cyan),(1,violet)]:
        for offset in [-.09,.09]:
            cable('Protected power line',[(side*3.2,.6,.65),(side*4.8,1,.35),
                (side*5.6,3,.24),(side*5.6,9,.24),(side*7.8,10,.35)],.045,dark)
        for y in [3.5,5,6.5,8]:
            box('Conduit clamp',(side*5.6,y,.26),(.26,.09,.18),titanium)
        for y in [-3.8,-2.9]:
            for i in range(5):
                o=box('Deck caution marker',(side*3.5+i*.12,y,.073),(.065,.36,.009),amber,.001)
                o.rotation_euler.z=-.55

    environment=[]
    for name in ['FloorModules','BunkerStructure','ServerModules','VentilationModules','LightFixtures','CableRouting']:
        environment.extend(batch_collection(COLLECTIONS[name]))
    bunker=empty('BunkerRoot',environment)
    bunker['role']='static_environment'
    supports=batch_collection(COLLECTIONS['DeskSupports'])
    anchor=bpy.data.objects.new('AvatarAnchor',None)
    scene.collection.objects.link(anchor)
    anchor.location=(-2.25,1.25,2.55)
    anchor['role']='original_avatar_loaded_separately'
    scene.frame_set(1)
    full=[workspace,*original,bunker,*environment,monitor_root,*monitor_details,*labels,*supports,anchor]
    select_export(full,output/'Continuum_Bunker_Stage_V04.glb',animations=True)
    preserve(BASE,output/'Continuum_Bunker_Stage_V04.glb')

    # Runtime excludes decorative screens.
    desk_names=['Bodenplattform /','CONTINUUM Schriftzug','Zentralpodest /','Basis /','Interaktive Konsole',
        'Konsole -','Konsole /','Rechter Energietisch','Energieplateau /','Cabinet mounting','Cabinet standoff']
    desk=[o for o in original if o.type=='MESH' and any(o.name.startswith(n) for n in desk_names)]+supports
    bpy.ops.object.select_all(action='DESELECT')
    collection('RuntimeDesk')
    runtime_desk=[]
    for obj in desk:
        copy=obj.copy()
        copy.data=obj.data.copy()
        CURRENT.objects.link(copy)
        copy.matrix_world=obj.matrix_world.copy()
        matrix=copy.matrix_world.copy()
        copy.parent=None
        copy.matrix_world=matrix
        runtime_desk.append(copy)
    runtime_desk=batch_collection(CURRENT)
    runtime_root=empty('continuum-mission-desk',runtime_desk)

    collection('RuntimeMonitorEngineering')
    rear_names=['Monitor Rueckseite /','Rueckseite /','Hauptdisplay / Rear',
        'Hauptdisplay / rear','Hauptmonitor / rear','Hauptmonitor / Rear']
    rear=[o for o in original if o.type=='MESH' and any(o.name.startswith(n) for n in rear_names)]
    for obj in rear+monitor_details:
        mesh=obj.data.copy()
        mesh.transform(obj.matrix_world)
        bm=bmesh.new()
        bm.from_mesh(mesh)
        bmesh.ops.delete(bm,geom=[v for v in bm.verts if v.co.x>0],context='VERTS')
        for v in bm.verts:
            v.co=Vector(((v.co.x+1.612)/2.506,(v.co.y+.03)/1.545,(v.co.z-2.111)/1.545))
        bm.to_mesh(mesh)
        bm.free()
        if not mesh.vertices:
            bpy.data.meshes.remove(mesh)
            continue
        copy=bpy.data.objects.new('Monitor mechanism',mesh)
        CURRENT.objects.link(copy)
    runtime_rear=batch_collection(CURRENT)
    rear_root=empty('continuum-monitor-engineering',runtime_rear)
    old_name=bunker.name
    bunker.name='continuum-bunker'
    select_export([bunker,*environment,runtime_root,*runtime_desk,rear_root,*runtime_rear],
        output/'Continuum_Bunker_Stage_V04_Android.glb')
    bunker.name=old_name
    for obj in [*runtime_desk,runtime_root,*runtime_rear,rear_root]:
        bpy.data.objects.remove(obj,do_unlink=True)

    # Original avatar, render companion only.
    before=set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(AVATAR))
    avatar=list(set(bpy.data.objects)-before)
    avatar_root=empty('OriginalAvatarRenderRoot',avatar)
    avatar_root.location=anchor.location
    avatar_root.scale=(2.8,)*3
    collection('OriginalAvatar_Unchanged_Source')
    for obj in avatar:
        adopt(obj,obj.name)
        if obj.type=='MESH' and not obj.data.materials:
            obj.hide_render=True
            obj.hide_set(True)
    scene.frame_set(1)
    scene.render.engine='CYCLES'
    scene.cycles.device='CPU'
    scene.cycles.samples=32
    scene.cycles.seed=731
    scene.cycles.use_denoising=False
    scene.cycles.max_bounces=5
    scene.cycles.diffuse_bounces=3
    scene.cycles.glossy_bounces=3
    scene.render.threads_mode='FIXED'
    scene.render.threads=6
    scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG'
    scene.render.image_settings.color_mode='RGB'
    scene.render.film_transparent=False
    world=bpy.data.worlds.new('Bunker ambient')
    scene.world=world
    world.use_nodes=True
    bg=world.node_tree.nodes['Background']
    bg.inputs[0].default_value=(.032,.058,.105,1)
    bg.inputs[1].default_value=.28
    scene.view_settings.view_transform='AgX'
    scene.view_settings.look='AgX - Medium High Contrast'
    light('Cyan key',(-5,-3,6),(0,1,1.6),(.12,.62,1),2100,5)
    light('Violet key',(5,-1,5),(0,1,1.6),(.45,.15,1),1800,5)
    light('Soft overhead',(0,2,6.7),(0,1,0),(.65,.80,1),2400,7)
    light('Front softbox',(0,-5,4),(0,1,2),(.45,.60,.85),950,6)
    light('Rear silhouette',(0,9,5),(0,0,2),(.22,.40,.9),2200,5)
    light('Left server fill',(-6,6,5),(-8,6,2),(.08,.60,1),1000,4)
    light('Right server fill',(6,6,5),(8,6,2),(.46,.12,1),1000,4)
    data=bpy.data.cameras.new('InspectionCamera')
    camera=bpy.data.objects.new('InspectionCamera',data)
    scene.collection.objects.link(camera)
    scene.camera=camera
    data.lens=27
    data.clip_start=.1
    data.clip_end=120
    scene.use_nodes=True
    nodes=scene.node_tree.nodes
    nodes.clear()
    render=nodes.new('CompositorNodeRLayers')
    glow=nodes.new('CompositorNodeGlare')
    glow.glare_type='FOG_GLOW'
    glow.quality='HIGH'
    glow.threshold=1.8
    glow.size=7
    glow.mix=-.88
    composite=nodes.new('CompositorNodeComposite')
    scene.node_tree.links.new(render.outputs['Image'],glow.inputs['Image'])
    scene.node_tree.links.new(glow.outputs['Image'],composite.inputs['Image'])
    pose_camera(scene,'Front')
    bpy.ops.wm.save_as_mainfile(filepath=str(output/'Continuum_Bunker_Stage_V04.blend'))
    info={'sourceSha256':hashlib.sha256(BASE.read_bytes()).hexdigest(),
        'originalAvatarSha256':hashlib.sha256(AVATAR.read_bytes()).hexdigest(),
        'originalActionNames':original_action_names,'renderEngine':'Blender '+bpy.app.version_string+' Cycles CPU',
        'referenceImages':['63606(1).jpg','63604(1).jpg'],
        'avatarEmbeddedInStageGlb':False,'avatarIncludedInBlendRenders':True,
        'bakedTelemetryRemoved':True,'androidRuntimeExcludesDecorativeScreens':True}
    (output/'build-info.json').write_text(json.dumps(info,indent=2)+'\n')


VIEWS={
    'Front':((0,-12.8,5.8),(0,3.5,3.0),27,1600,1000),
    'Rear':((6.8,6.5,4.5),(-.6,-.5,1.9),34,1600,1000),
    'Top':((0,2.0,6.9),(0,2.0,0),31,1400,1400),
    'FoldWide':((.2,-11.5,4.5),(0,3.5,3.0),27,1800,1100),
    'MonitorRear':((-3.5,3.4,2.95),(-1.6,.30,2.08),48,1400,1000),
}


def pose_camera(scene,view):
    pos,target,lens,w,h=VIEWS[view]
    avatar=bpy.data.collections.get('OriginalAvatar_Unchanged_Source')
    if avatar is not None:
        avatar.hide_render=view=='MonitorRear'
    cam=scene.camera
    cam.data.type='ORTHO' if view=='Top' else 'PERSP'
    if view=='Top':
        cam.data.ortho_scale=22.2
    cam.location=pos
    cam.rotation_euler=(Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler()
    cam.data.lens=lens
    scene.render.resolution_x=w
    scene.render.resolution_y=h


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--render',default='')
    parser.add_argument('--samples',type=int,default=32)
    parser.add_argument('--percentage',type=int,default=100)
    parser.add_argument('--reuse',action='store_true')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:])
    args.output.mkdir(parents=True,exist_ok=True)
    if args.reuse:
        bpy.ops.wm.open_mainfile(filepath=str(args.output/'Continuum_Bunker_Stage_V04.blend'))
    else:
        build(args.output)
    for view in args.render.split(','):
        if not view:
            continue
        scene=bpy.context.scene
        pose_camera(scene,view)
        scene.cycles.samples=args.samples
        scene.render.resolution_percentage=args.percentage
        scene.render.filepath=str(args.output/f'Continuum_Bunker_Stage_V04_{view}.png')
        bpy.ops.render.render(write_still=True)
        print('RENDER_WRITTEN',view,scene.render.filepath,flush=True)


if __name__=='__main__':
    main()
