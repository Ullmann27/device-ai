#!/usr/bin/env python3
"""Actual packaged GLB measurements; no renderer or Android performance claims."""
from pathlib import Path
import hashlib
import json
import struct
import sys
import time
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSET = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'source/p491/continuum-avatar.glb'
OUT = ROOT / 'results/glb-analysis.json'
OPENING = dict(SIL=0, PP=0, FF=.06/.8, TH=.18/.8, DD=.20/.8,
               KK=.30/.8, CH=.15/.8, SS=0, NN=.11/.8, RR=.22/.8,
               AA=.65/.8, E=.23/.8, I=.10/.8, O=.36/.8, U=.10/.8)


class Glb:
    def __init__(self, path):
        self.raw = Path(path).read_bytes()
        magic, version, size = struct.unpack_from('<III', self.raw)
        assert (magic, version, size) == (0x46546c67, 2, len(self.raw))
        offset = 12
        while offset < size:
            length, kind = struct.unpack_from('<II', self.raw, offset)
            chunk = self.raw[offset+8:offset+8+length]
            if kind == 0x4e4f534a:
                self.doc = json.loads(chunk)
            elif kind == 0x004e4942:
                self.binary = chunk
            offset += 8+length

    def accessor(self, index):
        a = self.doc['accessors'][index]
        assert 'sparse' not in a, 'This experiment deliberately rejects sparse data'
        v = self.doc['bufferViews'][a['bufferView']]
        assert v.get('buffer', 0) == 0
        dtype = np.dtype({5120:'i1',5121:'u1',5122:'<i2',5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']])
        width = dict(SCALAR=1,VEC2=2,VEC3=3,VEC4=4,MAT4=16)[a['type']]
        offset = v.get('byteOffset',0)+a.get('byteOffset',0)
        stride = v.get('byteStride',dtype.itemsize*width)
        result = np.ndarray((a['count'],width),dtype=dtype,buffer=self.binary,
                            offset=offset,strides=(stride,dtype.itemsize)).copy()
        assert not a.get('normalized',False), 'Normalized accessors need explicit handling'
        return result


def mm_norm(a):
    n = np.linalg.norm(a.astype(np.float64),axis=1)*1000
    return dict(max=float(n.max(initial=0)),rms=float(np.sqrt(np.mean(n*n))),
                changedVertices=int(np.count_nonzero(n > 1e-4)))


def main():
    start=time.perf_counter()
    g=Glb(ASSET); d=g.doc
    result={'evidenceClass':'HOST_GEOMETRY_MEASUREMENT_NOT_DEVICE',
            'asset':str(ASSET.relative_to(ROOT)) if ASSET.is_relative_to(ROOT) else str(ASSET),
            'sha256':hashlib.sha256(g.raw).hexdigest(),'bytes':len(g.raw),
            'nodes':len(d['nodes']),'meshes':len(d['meshes']),
            'numpy':np.__version__,'primitives':[],'totalTriangles':0,'primitiveVertexCountSum':0}
    position_accessors=set()
    for mi,m in enumerate(d['meshes']):
        for pi,p in enumerate(m['primitives']):
            assert p.get('mode',4)==4
            base=g.accessor(p['attributes']['POSITION']).astype(np.float64)
            indices=g.accessor(p['indices']).reshape(-1)
            assert len(indices)%3==0 and indices.max()<len(base)
            result['totalTriangles']+=len(indices)//3; result['primitiveVertexCountSum']+=len(base)
            position_accessors.add(p['attributes']['POSITION'])
            if not p.get('targets'): continue
            names=m['extras']['targetNames']
            morph={name:g.accessor(t['POSITION']).astype(np.float64) for name,t in zip(names,p['targets'])}
            jaw=morph['JawOpen']; jaw_power=float(np.sum(jaw*jaw))
            entry={'mesh':m['name'],'primitive':pi,'vertices':len(base),'triangles':len(indices)//3,
                   'targetNames':names,'boundsMetres':[base.min(axis=0).tolist(),base.max(axis=0).tolist()],
                   'allFinite':bool(np.isfinite(base).all() and all(np.isfinite(x).all() for x in morph.values())),
                   'targetDisplacementMm':{name:mm_norm(delta) for name,delta in morph.items()},
                   'openingProjectionDiagnostic':{}}
            # Projection measures correlation, NOT correctness: lips intentionally have other shapes.
            for name,ratio in OPENING.items():
                delta=morph[name]; residual=delta-ratio*jaw
                entry['openingProjectionDiagnostic'][name]={'authoredOpeningRatio':ratio,
                    'fullVectorProjectionOntoJaw':float(np.sum(delta*jaw)/jaw_power),
                    'lipShapeResidualMm':mm_norm(residual),
                    'speechDeltaAtOrAboveY026mMm':mm_norm(delta[base[:,1]>=.260]) if np.any(base[:,1]>=.260) else None}
            # Independent shared-opening reconstruction from known AA/I recipes:
            # AA=.65 open+.15 lateral; I=.10 open+.57 lateral; JawOpen=.8 open.
            inferred_jaw=.8*(.57*morph['AA']-.15*morph['I'])/(.65*.57-.10*.15)
            entry['jawReconstructedFromAAAndIResidualMm']=mm_norm(inferred_jaw-jaw)
            predicted_e=.23/.8*jaw+.42/.57*(morph['I']-.10/.8*jaw)
            entry['EPredictedFromJawAndIResidualMm']=mm_norm(predicted_e-morph['E'])
            # Baseline vs latest mapper exact settled PP, loud jaw input 0.8.
            baseline_pp=morph['PP']+.8*jaw
            latest_pp=morph['PP']
            largest=int(np.argmax(np.linalg.norm(baseline_pp-latest_pp,axis=1)))
            entry['ppAtJawInput08']={'p491VsP503PositionDifferenceMm':mm_norm(baseline_pp-latest_pp),
                 'maximumDifferenceVertexIndex':largest,'maximumDifferenceNeutralPositionMetres':base[largest].tolist(),
                 'meaning':'Maximum vertex movement, not measured aperture or lip contact distance',
                 'p491Weights':{'PP':1,'JawOpen':.8},'p503Weights':{'PP':1,'JawOpen':0},
                 'p491JawPitchDegrees':9.6,'p503JawPitchDegrees':0}
            # Synthetic combinations exercise the actual delta arrays, not playback or collisions.
            rng=np.random.default_rng(491503)
            max_disp=0.0; finite=True
            for _ in range(500):
                v1,v2=rng.choice(list(OPENING)[1:],2,replace=False)
                a=float(rng.random()); b=1-a; desired=float(rng.random())
                opening=OPENING[v1]*a+OPENING[v2]*b
                combined=base+a*morph[v1]+b*morph[v2]+max(0,desired-opening)*jaw
                finite=finite and bool(np.isfinite(combined).all())
                max_disp=max(max_disp,float(np.linalg.norm(combined-base,axis=1).max())*1000)
            entry['synthetic500ConvexVisemePairs']={'finite':finite,'maximumDisplacementMm':max_disp,
                'seed':491503,'doesNotProve':'No self-intersection, lip contact, aesthetic or phoneme validity is asserted'}
            if 'WEIGHTS_0' in p['attributes']:
                w=g.accessor(p['attributes']['WEIGHTS_0']);j=g.accessor(p['attributes']['JOINTS_0'])
                entry['skin']={'maxWeightSumError':float(np.abs(w.sum(axis=1)-1).max()),
                    'usedJointIndices':sorted(int(x) for x in np.unique(j[w>0]))}
            result['primitives'].append(entry)
    result['uniquePositionAccessorVertexCount']=sum(d['accessors'][i]['count'] for i in position_accessors)
    result['vertexCountNote']='Primitive sum counts shared NeuralThreads POSITION accessor twice; unique accessor count removes that reuse, not coincident vertices.'
    notice=json.loads((ASSET.parent/'NOTICE.json').read_text())
    result['bundledNotice']={'recordedSha256':notice['sha256'],'recordedBytes':notice['bytes'],
        'shaMatchesActual':notice['sha256']==result['sha256'],'bytesMatchActual':notice['bytes']==result['bytes']}
    result['skinJoints']=[{'skin':s['name'],'nodes':[d['nodes'][i]['name'] for i in s['joints']]} for s in d.get('skins',[])]
    result['jawNode']=[n for n in d['nodes'] if n.get('name')=='Jaw'][0]
    result['durationSeconds']=time.perf_counter()-start
    OUT.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'output':str(OUT),'triangles':result['totalTriangles'],
        'primitiveVertexCountSum':result['primitiveVertexCountSum'],
        'uniquePositionAccessorVertexCount':result['uniquePositionAccessorVertexCount'],
        'durationSeconds':result['durationSeconds'],'primitives':[{
            'mesh':p['mesh'],'primitive':p['primitive'],
            'jawReconstructionMaxMm':p['jawReconstructedFromAAAndIResidualMm']['max'],
            'EPredictionMaxMm':p['EPredictedFromJawAndIResidualMm']['max'],
            'ppBaselineVsLatestMaxMm':p['ppAtJawInput08']['p491VsP503PositionDifferenceMm']['max'],
            'largestMorph':max(p['targetDisplacementMm'],key=lambda k:p['targetDisplacementMm'][k]['max'])
        } for p in result['primitives']]},indent=2))


if __name__=='__main__':main()
