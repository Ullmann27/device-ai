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
results=[]
for label,width,height in [('phone',279,390),('fold',587,470)]:
    aspect=width/height
    for mode in ['full-bust','head-upper-neck']:
        if mode=='full-bust':
            target=center.copy();vertical=max(extent[0],extent[1])*1.18/min(aspect,1)
        else:
            # Exact camera values emitted by the Kotlin preflight consumed by Filament.
            target=[focus['centerX'],focus['centerY'],focus['centerZ']]
            vertical=max(focus['halfHeight'],focus['halfWidth']/aspect)*1.08
        scene.render.resolution_x=width*2;scene.render.resolution_y=height*2
        camdata.ortho_scale=vertical*2
        # Blender may fit ortho_scale horizontally: verify and correct to Filament's explicit vertical planes.
        frame=camdata.view_frame(scene=scene);actual=max(v.y for v in frame)-min(v.y for v in frame)
        camdata.ortho_scale*=vertical*2/actual
        frame=camdata.view_frame(scene=scene)
        assert abs(max(v.y for v in frame)-min(v.y for v in frame)-vertical*2)<1e-6
        cam.location=point(target[0],target[1],target[2]+2)
        cam.rotation_euler=(point(*target)-cam.location).to_track_quat('-Z','Y').to_euler()
        scene.render.filepath=str(out/(label+'-'+mode+'.png'));bpy.ops.render.render(write_still=True)
        record={'frame':label+'-'+mode,'logicalViewport':[width,height],'renderPixels':[width*2,height*2], 'targetCanonical':target,'verticalWorldSpan':vertical*2,'horizontalWorldSpan':vertical*2*aspect}
        results.append(record);print('CAMERA_RENDER_COMPLETE',json.dumps(record),flush=True)
(out/'camera-spec.json').write_text(json.dumps({'assetSha256':hashlib.sha256(raw).hexdigest(),'source':'Actual GLB import and exact Kotlin preflight camera focus, neutral pose, Blender CPU render; not Android/Filament pixel equivalence.','cameras':results},indent=2)+'\n')
