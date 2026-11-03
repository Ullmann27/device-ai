#!/usr/bin/env python3
"""Independent EPSILON B geometry challenge. No renderer/asset-generator code imported."""
import argparse, hashlib, json, math, struct
from pathlib import Path

TARGETS = 'SIL PP FF TH DD KK CH SS NN RR AA E I O U BlinkLeft BlinkRight BrowUp BrowDown Smile JawOpen'.split()
NODES = ['AvatarRoot', 'Neck', 'Head', 'EyeLeft', 'EyeRight', 'Jaw']
FORMATS = {5121: ('B', 1), 5123: ('H', 2), 5125: ('I', 4), 5126: ('f', 4)}
WIDTHS = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def require(value, message):
    if not value:
        raise ValueError(message)


def matrix_multiply(a, b):
    return [[sum(a[i][k]*b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def transform(m, p):
    return tuple(sum(m[i][j]*(*p, 1)[j] for j in range(4)) for i in range(3))


def identity():
    return [[float(i == j) for j in range(4)] for i in range(4)]


def rotation(axis, degrees):
    m = identity(); c = math.cos(math.radians(degrees)); s = math.sin(math.radians(degrees))
    a, b = ((1, 2), (2, 0), (0, 1))[axis]
    m[a][a] = m[b][b] = c; m[a][b] = -s; m[b][a] = s
    return m


class Glb:
    def __init__(self, data):
        require(28 <= len(data) <= 16*1024*1024, 'GLB size')
        require(struct.unpack_from('<III', data) == (0x46546c67, 2, len(data)), 'GLB header')
        length, kind = struct.unpack_from('<II', data, 12)
        require(kind == 0x4e4f534a and 0 < length <= 1024*1024 and length % 4 == 0 and 20+length+8 <= len(data), 'JSON chunk')
        self.doc = json.loads(data[20:20+length])
        offset = 20+length
        size, kind = struct.unpack_from('<II', data, offset)
        require(kind == 0x004e4942 and size % 4 == 0 and offset+8+size == len(data), 'BIN chunk')
        self.binary = data[offset+8:]
        require(self.doc['asset']['version'] == '2.0', 'glTF version')
        require(not self.doc.get('extensionsRequired'), 'Unsupported required extension')
        require(not self.doc.get('animations'), 'Unexpected authored animation')
        buffers = self.doc['buffers']
        require(len(buffers) == 1 and 'uri' not in buffers[0], 'External buffer')
        require(0 <= len(self.binary)-buffers[0]['byteLength'] < 4, 'Buffer byte length')
        require(all('uri' not in i for i in self.doc.get('images', [])), 'External image')
        require(len(self.doc.get('nodes', [])) <= 64, 'Node budget')
        self.parents = [-1]*len(self.doc['nodes'])
        for i, node in enumerate(self.doc['nodes']):
            for child in node.get('children', []):
                require(0 <= child < len(self.parents) and child != i and self.parents[child] == -1, 'Invalid child/multiple parents')
                self.parents[child] = i
        for i in range(len(self.parents)):
            seen = set(); j = i
            while j != -1:
                require(j not in seen, 'Node cycle'); seen.add(j); j = self.parents[j]
        self.names = [n.get('name', '') for n in self.doc['nodes']]
        for name in NODES:
            require(self.names.count(name) == 1, 'Missing/duplicate node '+name)
        for i in range(len(self.doc['accessors'])):
            self.accessor(i)

    def accessor(self, index):
        require(type(index) is int and 0 <= index < len(self.doc['accessors']), 'Accessor index')
        a = self.doc['accessors'][index]; require('sparse' not in a, 'Sparse unsupported')
        view = a['bufferView']; require(0 <= view < len(self.doc['bufferViews']), 'Buffer view index')
        v = self.doc['bufferViews'][view]
        require(v['buffer'] == 0, 'Buffer index')
        fmt, scalar = FORMATS[a['componentType']]; width = WIDTHS[a['type']]
        count = a['count']; stride = v.get('byteStride', scalar*width); offset = a.get('byteOffset', 0)
        start = v.get('byteOffset', 0)
        require(0 < count <= 600000 and scalar*width <= stride <= 252 and stride % scalar == 0, 'Accessor dimensions')
        require(offset >= 0 and offset % scalar == 0 and start >= 0 and start+v['byteLength'] <= len(self.binary), 'View bounds')
        require(offset+(count-1)*stride+scalar*width <= v['byteLength'], 'Accessor bounds')
        result = [struct.unpack_from('<'+fmt*width, self.binary, start+offset+n*stride) for n in range(count)]
        require(all(math.isfinite(x) for row in result for x in row), 'Nonfinite binary value')
        return result

    def world(self, index, overrides=None):
        node = self.doc['nodes'][index]; require('matrix' not in node, 'TRS-only contract')
        rest_rotation=node.get('rotation',[0,0,0,1])
        require(len(rest_rotation)==4 and all(math.isfinite(a) and abs(a-b)<1e-5 for a,b in zip(rest_rotation,[0,0,0,1])), 'Nonidentity rest rotation')
        scale = node.get('scale',[1,1,1]); require(len(scale)==3 and all(math.isfinite(s) and s>0 for s in scale), 'Mirrored/nonfinite scale')
        local = identity()
        for i in range(3):
            local[i][i] = scale[i]; local[i][3] = node.get('translation',[0,0,0])[i]
        require(all(math.isfinite(x) for row in local for x in row), 'Nonfinite transform')
        if overrides and index in overrides:
            local = matrix_multiply(local, overrides[index])
        parent = self.parents[index]
        return matrix_multiply(self.world(parent, overrides), local) if parent >= 0 else local

    def descendant(self, name, ancestor):
        i = self.parents[self.names.index(name)]
        while i != -1:
            if i == self.names.index(ancestor): return True
            i = self.parents[i]
        return False


def validate(data):
    g = Glb(data); errors=[]; measures={}
    def check(name, fn):
        try: fn()
        except (AssertionError,ValueError,IndexError,KeyError,TypeError,ZeroDivisionError) as exc: errors.append({'test':name,'error':str(exc)})
    def hierarchy():
        for child, parent in [('Neck','AvatarRoot'),('Head','Neck'),('EyeLeft','Head'),('EyeRight','Head'),('Jaw','Head')]:
            require(g.descendant(child,parent), child+' must descend from '+parent)
        for i in range(len(g.parents)): g.world(i)
        left,right,jaw=[transform(g.world(g.names.index(n)),(0,0,0)) for n in ['EyeLeft','EyeRight','Jaw']]
        separation=left[0]-right[0]; measures['eye_separation_m']=separation
        require(.01 <= separation <= .15 and min(left[1],right[1]) > jaw[1], 'Mirrored eyes, scale or vertical pivots')
        # Quantitative schema ranges frozen before output inspection; pivots cannot move the test boundary freely.
        eye_y=(left[1]+right[1])/2
        require(.25 <= eye_y <= .32 and .20 <= jaw[1] <= .25 and .04 <= eye_y-jaw[1] <= .09,
                'Eye/Jaw pivots outside preregistered metre-scale bust anatomy')
        require(abs(left[1]-right[1]) <= .01 and abs(left[2]-right[2]) <= .01, 'Asymmetric rest eye pivots')
    check('rig_hierarchy_orientation',hierarchy)
    if errors:
        return {'status':'FAIL','sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'failures':errors,'measurements':measures}
    total_vertices=total_triangles=0; morph_sets=0
    for mi,mesh in enumerate(g.doc['meshes']):
        for pi,primitive in enumerate(mesh['primitives']):
            base=g.accessor(primitive['attributes']['POSITION']); total_vertices+=len(base)
            indices=g.accessor(primitive['indices']); total_triangles+=len(indices)//3
            check(f'mesh_{mi}_{pi}_indices',lambda:require(primitive.get('mode',4)==4 and len(indices)%3==0 and all(0 <= i[0] < len(base) for i in indices),'Triangle/index bounds'))
            if 'targets' not in primitive: continue
            morph_sets+=1
            names=mesh.get('extras',{}).get('targetNames',[])
            def targets():
                require(len(names)==21 and set(names)==set(TARGETS), 'Missing/duplicate target names')
                require(len(primitive['targets'])==len(names), 'Target count')
                ids=[t['POSITION'] for t in primitive['targets']]
                require(len(set(ids))==len(ids), 'Repeated target accessor')
                require(all(w==0 for w in mesh.get('weights',[])), 'Neutral weights must be zero')
            check(f'mesh_{mi}_{pi}_target_schema',targets)
            if len(names)!=len(primitive['targets']): continue
            deltas={n:g.accessor(t['POSITION']) for n,t in zip(names,primitive['targets'])}
            bounds=[(min(p[a] for p in base), max(p[a] for p in base)) for a in range(3)]
            diagonal=math.sqrt(sum((b-a)**2 for a,b in bounds))
            mesh_node=next(i for i,n in enumerate(g.doc['nodes']) if n.get('mesh')==mi)
            regional_base=[transform(g.world(mesh_node),p) for p in base]
            eye_y=sum(transform(g.world(g.names.index(n)),(0,0,0))[1] for n in ['EyeLeft','EyeRight'])/2
            jaw_y=transform(g.world(g.names.index('Jaw')),(0,0,0))[1]
            middle_y=(eye_y+jaw_y)/2
            tolerance=abs(eye_y-jaw_y)*.1
            regional_bounds=[(min(p[a] for p in regional_base),max(p[a] for p in regional_base)) for a in range(3)]
            def pivot_surface_bounds():
                for name in ['EyeLeft','EyeRight','Jaw']:
                    pivot=transform(g.world(g.names.index(name)),(0,0,0))
                    require(all(lo-.01 <= pivot[a] <= hi+.01 for a,(lo,hi) in enumerate(regional_bounds)),
                            name+' pivot lies outside rest skin bounds')
            check(f'mesh_{mi}_{pi}_pivot_surface_bounds',pivot_surface_bounds)
            measures[f'mesh_{mi}_{pi}_bounds']=bounds
            for name,delta in deltas.items():
                def deformation(name=name,delta=delta):
                    require(len(delta)==len(base),'Morph count')
                    magnitude=[math.sqrt(sum(x*x for x in d)) for d in delta]
                    require(0.02 <= diagonal <= 2 and (name=='SIL' or max(magnitude)>1e-7) and max(magnitude)<=diagonal*.2,'Zero/excessive deformation or bounds')
                    changed=[i for i,m in enumerate(magnitude) if m>1e-7]
                    if name.startswith('Blink') or name.startswith('Brow'):
                        require(all(regional_base[i][1]>=middle_y-tolerance for i in changed),'Eye/brow deforms lower face')
                    if name in TARGETS[:15]:
                        require(all(regional_base[i][1]<=middle_y+tolerance for i in changed),'Mouth deforms upper face')
                    if name.startswith('Blink'):
                        left=[m for p,m in zip(regional_base,magnitude) if p[0]>0]; right=[m for p,m in zip(regional_base,magnitude) if p[0]<0]
                        require(left and right,'Both face sides required')
                        l=sum(left)/len(left);r=sum(right)/len(right)
                        require(l>3*r if name=='BlinkLeft' else r>3*l,'Blink mirrored or not independent')
                    posed=[tuple(p[a]+d[a] for a in range(3)) for p,d in zip(base,delta)]
                    require(all(math.isfinite(x) for p in posed for x in p),'Nonfinite posed vertices')
                    require(all(max(p[a] for p in posed)>min(p[a] for p in posed) for a in range(3)),'Degenerate posed bounds')
                    require([tuple(p[a]+0*d[a] for a in range(3)) for p,d in zip(base,delta)]==base,'Neutral restoration')
                check(f'mesh_{mi}_{pi}_{name}_deformation',deformation)
            for combination in [('JawOpen','AA'),('JawOpen','PP'),('JawOpen','O'),('Smile','BlinkLeft','BlinkRight')]:
                def mixed(combination=combination):
                    require(all(n in deltas for n in combination),'Missing mixed target')
                    posed=[tuple(p[a]+sum(deltas[n][i][a] for n in combination) for a in range(3)) for i,p in enumerate(base)]
                    require(all(math.isfinite(x) for p in posed for x in p),'Mixed nonfinite')
                    require(all(max(p[a] for p in posed)>min(p[a] for p in posed) for a in range(3)),'Mixed degenerate bounds')
                check(f'mesh_{mi}_{pi}_mix_'+'_'.join(combination),mixed)
            def skin():
                nodes=[n for n in g.doc['nodes'] if n.get('mesh')==mi];require(nodes,'Mesh node missing')
                js=g.accessor(primitive['attributes']['JOINTS_0']);ws=g.accessor(primitive['attributes']['WEIGHTS_0'])
                require(len(js)==len(ws)==len(base),'Skin count')
                require(all(all(0<=x<=1 for x in row) and abs(sum(row)-1)<.005 for row in ws),'Skin weights')
                for node in nodes:
                    s=g.doc['skins'][node['skin']];joint_ids=s['joints'];ib=g.accessor(s['inverseBindMatrices'])
                    require(len(ib)==len(joint_ids) and len(set(joint_ids))==len(joint_ids) and all(0<=i<len(g.parents) for i in joint_ids),'Skin joint references')
                    require(all(0<=j<len(joint_ids) for row in js for j in row),'Joint influence bounds')
                    def posed(overrides):
                        matrices=[]
                        for joint,inv in zip(joint_ids,ib):
                            mat=[[inv[c*4+r] for c in range(4)] for r in range(4)]
                            matrices.append(matrix_multiply(g.world(joint,overrides),mat))
                        return [tuple(sum(w*transform(matrices[j],p)[axis] for j,w in zip(jrow,wrow)) for axis in range(3)) for p,jrow,wrow in zip(base,js,ws)]
                    neutral=posed({}); head=g.names.index('Head')
                    for degrees in [-25,25]:
                        rotated=posed({head:rotation(1,degrees)})
                        require(any(math.dist(a,b)>1e-5 for a,b in zip(neutral,rotated)),'Head does not deform face')
                    for name in ['EyeLeft','EyeRight','Jaw']:
                        for axis,degrees in [(1,-25),(1,25),(0,-15),(0,15)]:
                            moved=posed({g.names.index(name):rotation(axis,degrees)})
                            require(all(math.dist(a,b)<1e-7 for a,b in zip(neutral,moved)),name+' unexpectedly deforms exterior face')
            check(f'mesh_{mi}_{pi}_skin_rotation',skin)
    check('resource_budgets',lambda:require(total_vertices<=100000 and total_triangles<=200000 and morph_sets>0,'Mesh budgets/no morphs'))
    return {'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'vertices':total_vertices,'triangles':total_triangles,'morph_primitives':morph_sets,
            'status':'FAIL' if errors else 'PASS','failures':errors,'measurements':measures,
            'limits':'Numerical geometry only; no likeness/phonetic/naturalness/device certification.'}


def main():
    parser=argparse.ArgumentParser();parser.add_argument('asset');parser.add_argument('--output');args=parser.parse_args()
    try: result=validate(Path(args.asset).read_bytes())
    except Exception as exc: result={'status':'FAIL','failures':[{'test':'binary_structure','error':str(exc)}]}
    encoded=json.dumps(result,indent=2)+'\n';print(encoded,end='')
    if args.output:Path(args.output).write_text(encoded)
    return 0 if result['status']=='PASS' else 1

if __name__=='__main__':raise SystemExit(main())
