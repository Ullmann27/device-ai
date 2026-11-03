"""Small deterministic glTF 2 exporter for the authored canonical avatar schema.

Writes ordinary uncompressed FLOAT accessors, skinning, named morphs, materials
and animation channels. Runtime imports do not require Blender or this script.
"""
import bpy, json, struct, math, hashlib
from mathutils import Vector

def export_avatar(path, manifest_path, morphs):
    blob=bytearray();views=[];accessors=[];meshes=[];nodes=[];materials=[]
    def access(data,kind='VEC3',component=5126,target=None,limits=False):
        rows=[[v] if isinstance(v,(int,float)) else list(v) for v in data]
        flat=[x for row in rows for x in row]
        while len(blob)%4:blob.append(0)
        offset=len(blob);fmt={5126:'f',5125:'I',5123:'H'}[component]
        blob.extend(struct.pack('<'+fmt*len(flat),*flat))
        view={'buffer':0,'byteOffset':offset,'byteLength':len(blob)-offset}
        if target:view['target']=target
        views.append(view)
        a={'bufferView':len(views)-1,'componentType':component,'count':len(rows),'type':kind}
        if limits:
            a['min']=[min(r[i] for r in rows)for i in range(len(rows[0]))]
            a['max']=[max(r[i] for r in rows)for i in range(len(rows[0]))]
        accessors.append(a);return len(accessors)-1
    def canon(v):return Vector((v.x,v.z,-v.y))
    def material(mat):
        if mat.name in material_names:return material_names.index(mat.name)
        bs=mat.node_tree.nodes.get('Principled BSDF');color=list(bs.inputs['Base Color'].default_value)
        emissive=list(bs.inputs['Emission Color'].default_value[:3]);strength=bs.inputs['Emission Strength'].default_value
        item={'name':mat.name,'pbrMetallicRoughness':{'baseColorFactor':color,'metallicFactor':bs.inputs['Metallic'].default_value,'roughnessFactor':bs.inputs['Roughness'].default_value},'doubleSided':True}
        if strength>0:
            # Core glTF emissive factor is bounded to [0,1]; no optional shader
            # extension is required by the mobile importer.
            item['emissiveFactor']=[min(1,c*strength)for c in emissive]
        materials.append(item);material_names.append(mat.name);return len(materials)-1
    material_names=[]
    control_names=['AvatarRoot','Neck','Head','EyeLeft','EyeRight','Jaw']
    parents=[None,0,1,2,2,2]
    pivots=[Vector(bpy.data.objects[n]['canonicalPivot'])for n in control_names]
    for i,name in enumerate(control_names):
        p=pivots[i]-(pivots[parents[i]] if parents[i] is not None else Vector())
        nodes.append({'name':name,'translation':list(p),'children':[]})
        if parents[i] is not None:nodes[parents[i]]['children'].append(i)
    def weights(p):
        y=p.y
        if y<.145:return [1,0,0,0]
        if y<.19:
            t=(y-.145)/.045;t=t*t*(3-2*t);return [1-t,t,0,0]
        if y<.245:
            t=(y-.19)/.055;t=t*t*(3-2*t);return [0,1-t,t,0]
        return [0,0,1,0]
    stats=[];face_node=None;thread_node=None
    bpy.context.view_layer.update()
    objects=sorted([o for o in bpy.data.objects if o.type=='MESH'],key=lambda o:o.name)
    for obj in objects:
        deform=obj.name in ['Face','NeuralThreads']
        parent_index=0 if deform else control_names.index(obj.parent.name)
        pivot=Vector() if deform else pivots[parent_index]
        mesh=obj.data;mesh.calc_loop_triangles()
        pos=[canon(obj.matrix_world@v.co)-pivot for v in mesh.vertices]
        normmatrix=obj.matrix_world.to_3x3().inverted().transposed()
        normals=[canon(normmatrix@v.normal).normalized()for v in mesh.vertices]
        attrib={'POSITION':access(pos,target=34962,limits=True),'NORMAL':access(normals,target=34962)}
        targets=[]
        if deform:
            skinweights=[weights(p)for p in pos]
            attrib['JOINTS_0']=access([[j if w>0 else 0 for j,w in zip((0,1,2,0),row)]for row in skinweights],'VEC4',5123,34962)
            attrib['WEIGHTS_0']=access(skinweights,'VEC4',5126,34962)
            basis=obj.data.shape_keys.key_blocks['Basis']
            for name in morphs:
                key=obj.data.shape_keys.key_blocks[name]
                delta=[canon(k.co-b.co)for k,b in zip(key.data,basis.data)]
                targets.append({'POSITION':access(delta,target=34962,limits=True)})
        primitives=[]
        for midx,mat in enumerate(mesh.materials):
            indices=[i for tri in mesh.loop_triangles if tri.material_index==midx for i in tri.vertices]
            if not indices:continue
            primitive={'attributes':attrib,'indices':access(indices,'SCALAR',5125,34963),'material':material(mat),'mode':4}
            if deform:primitive['targets']=targets
            primitives.append(primitive)
        item={'name':obj.name+'Mesh','primitives':primitives}
        if deform:item.update(weights=[0]*len(morphs),extras={'targetNames':morphs})
        meshes.append(item)
        ni=len(nodes);node={'name':obj.name,'mesh':len(meshes)-1}
        if deform:node['skin']=0
        nodes.append(node)
        if not deform:nodes[parent_index]['children'].append(ni)
        if obj.name=='Face':face_node=ni
        if obj.name=='NeuralThreads':thread_node=ni
        stats.append({'name':obj.name,'vertices':len(pos),'triangles':len(mesh.loop_triangles),'morphTargets':len(targets)})
    inverse=[]
    for p in pivots[:3]:inverse.append([1,0,0,0,0,1,0,0,0,0,1,0,-p.x,-p.y,-p.z,1])
    skins=[{'name':'BustSkin','joints':[0,1,2],'skeleton':0,'inverseBindMatrices':access(inverse,'MAT4')}]
    animations=[]
    def weight_clip(name,frames):
        times=access([t for t,w in frames],'SCALAR',limits=True)
        values=access([x for t,w in frames for x in w],'SCALAR')
        animations.append({'name':name,'samplers':[{'input':times,'output':values,'interpolation':'LINEAR'}],
          'channels':[{'sampler':0,'target':{'node':n,'path':'weights'}}for n in [face_node,thread_node]]})
    def vector(**kwargs):return [kwargs.get(n,0)for n in morphs]
    frames=[(0,vector())]
    for i,n in enumerate(morphs[:15]):frames.extend([(i+.35,vector(**{n:1})),(i+.75,vector())])
    weight_clip('VisemeSweep',frames)
    weight_clip('ExpressionAndSpeech',[(0,vector()),(1,vector(Smile=1)),(2,vector(Smile=.7,AA=.7)),(2.2,vector(Smile=.7,AA=.7,BlinkLeft=1,BlinkRight=1)),(2.4,vector(Smile=.7,O=.6)),(3,vector(BrowUp=1)),(4,vector(BrowDown=.8)),(5,vector())])
    samplers=[];channels=[]
    for name,axis,angle in [('Neck',0,3),('Head',1,12),('EyeLeft',1,18),('EyeRight',1,18),('Jaw',0,18)]:
        quats=[]
        for a in [0,angle,0,-angle if name!='Jaw' else 0,0]:
            q=[0,0,0,math.cos(math.radians(a)/2)];q[axis]=math.sin(math.radians(a)/2);quats.append(q)
        si=len(samplers);samplers.append({'input':access([0,1,2,3,4],'SCALAR',limits=True),'output':access(quats,'VEC4'),'interpolation':'LINEAR'})
        channels.append({'sampler':si,'target':{'node':control_names.index(name),'path':'rotation'}})
    animations.append({'name':'IndependentControlDemo','samplers':samplers,'channels':channels})
    doc={'asset':{'version':'2.0','generator':'Continuum EPSILON Blender canonical exporter v1','copyright':'CC0 MakeHuman graphical data with authored adaptation; see provenance.json'},
         'scene':0,'scenes':[{'name':'CanonicalBust','nodes':[0,face_node,thread_node]}],'nodes':nodes,'meshes':meshes,'materials':materials,'skins':skins,'animations':animations,
         'buffers':[{'byteLength':len(blob)}],'bufferViews':views,'accessors':accessors,
         'extras':{'schema':'continuum.avatar.v1','up':'+Y','front':'+Z','anatomicalLeft':'+X','units':'metres','identityStatus':'RESEARCH_CANDIDATE_NOT_OWNER_ACCEPTED'}}
    payload=json.dumps(doc,separators=(',',':'),ensure_ascii=True).encode()
    payload+=b' '*((-len(payload))%4);blob+=b'\0'*((-len(blob))%4)
    result=struct.pack('<III',0x46546c67,2,12+8+len(payload)+8+len(blob))+struct.pack('<II',len(payload),0x4e4f534a)+payload+struct.pack('<II',len(blob),0x004e4942)+blob
    path.write_bytes(result)
    manifest={'schemaVersion':1,'asset':str(path.name),'sha256':hashlib.sha256(result).hexdigest(),'bytes':len(result),'coordinateSystem':{'up':'+Y','front':'+Z','anatomicalLeft':'+X','units':'metres'},
       'controlNodes':[{'name':n,'nodeIndex':i,'pivotWorld':list(p),'parent':control_names[parents[i]]if parents[i]is not None else None}for i,(n,p)in enumerate(zip(control_names,pivots))],
       'morphNames':morphs,'morphIndices':{n:i for i,n in enumerate(morphs)},'meshes':stats,'totalTriangles':sum(s['triangles']for s in stats),
       'animations':[a['name']for a in animations],'textureCount':0,'identityStatus':'PARTIAL_REFERENCE_ADAPTATION_REQUIRES_OWNER_REVIEW','deviceStatus':'NOT_RUN',
       'jawPolicy':'Exterior via JawOpen; Jaw node rotates inner lower dental arch and mouth backing; expression and jaw weights independently controlled.',
       'limits':['No phonetic accuracy acceptance','No Fold7 FPS/thermal acceptance','No biometric identity claim','Morph POSITION only; normals use neutral vertex normals','Filament offset follows barycentric displacement without pose-corrective normal refit']}
    manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
    print('GLB_EXPORTED',str(path),'bytes',len(result),'triangles',manifest['totalTriangles'],flush=True)
