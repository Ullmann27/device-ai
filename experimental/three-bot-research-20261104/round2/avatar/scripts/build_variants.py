#!/usr/bin/env python3
"""Append portable local material atlases to the actual GLB; never rewrite geometry.

This is deterministic asset data generation, not AI image generation or a new face.
The original GLB and all round-1 artifacts are read-only inputs.
"""
from pathlib import Path
import argparse
import copy
import hashlib
import io
import json
import struct
import time
import numpy as np
from PIL import Image,ImageFilter

ROOT=Path(__file__).resolve().parents[1]
DEFAULT_SOURCE=ROOT.parents[1]/'avatar/source/p491/continuum-avatar.glb'
def sha(data):return hashlib.sha256(data).hexdigest()

class Glb:
    def __init__(self,path):
        self.raw=Path(path).read_bytes()
        assert struct.unpack_from('<III',self.raw)==(0x46546c67,2,len(self.raw))
        n,kind=struct.unpack_from('<II',self.raw,12);assert kind==0x4e4f534a
        self.doc=json.loads(self.raw[20:20+n]);o=20+n
        length,kind=struct.unpack_from('<II',self.raw,o);assert kind==0x004e4942
        self.binary=self.raw[o+8:o+8+length]
    def accessor(self,index):
        a=self.doc['accessors'][index];v=self.doc['bufferViews'][a['bufferView']]
        assert 'sparse' not in a and not a.get('normalized',False)
        dtype=np.dtype({5121:'u1',5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']])
        width={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
        return np.ndarray((a['count'],width),dtype=dtype,buffer=self.binary,
            offset=v.get('byteOffset',0)+a.get('byteOffset',0),
            strides=(v.get('byteStride',dtype.itemsize*width),dtype.itemsize)).copy()

def bake_actual_surface(g,size):
    face=next(m for m in g.doc['meshes'] if m['name']=='FaceMesh')['primitives'][0]
    pos=g.accessor(face['attributes']['POSITION']);normal=g.accessor(face['attributes']['NORMAL'])
    uv=g.accessor(face['attributes']['TEXCOORD_0']);indices=g.accessor(face['indices']).reshape(-1,3)
    pmap=np.zeros((size,size,3),np.float32);nmap=np.zeros_like(pmap);covered=np.zeros((size,size),bool)
    for tri in indices:
        q=uv[tri]*(size-1)
        if np.ptp(q[:,0])>size/2:continue
        lo=np.maximum(0,np.floor(q.min(axis=0)).astype(int));hi=np.minimum(size-1,np.ceil(q.max(axis=0)).astype(int))
        if np.any(hi<lo):continue
        yy,xx=np.mgrid[lo[1]:hi[1]+1,lo[0]:hi[0]+1];a,b,c=q
        den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-9:continue
        wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
        wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;wc=1-wa-wb
        inside=(wa>=-1e-6)&(wb>=-1e-6)&(wc>=-1e-6)
        sl=np.s_[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
        for dst,source in [(pmap,pos),(nmap,normal)]:
            values=wa[...,None]*source[tri[0]]+wb[...,None]*source[tri[1]]+wc[...,None]*source[tri[2]]
            dst[sl][inside]=values[inside]
        covered[sl]|=inside
    nmap/=np.maximum(1e-12,np.linalg.norm(nmap,axis=2,keepdims=True))
    return face,pmap,nmap,covered

def encode(array):
    b=io.BytesIO();Image.fromarray(array.astype(np.uint8)).save(b,format='PNG');return b.getvalue()

def append_texture(doc,binary,png,name):
    binary.extend(b'\0'*(-len(binary)%4));offset=len(binary);binary.extend(png)
    vi=len(doc['bufferViews']);doc['bufferViews'].append({'buffer':0,'byteOffset':offset,'byteLength':len(png)})
    ii=len(doc['images']);doc['images'].append({'bufferView':vi,'mimeType':'image/png','name':name})
    ti=len(doc['textures']);doc['textures'].append({'source':ii,'sampler':0})
    return ti

def build():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,default=DEFAULT_SOURCE)
    parser.add_argument('--params',type=Path,default=ROOT/'scripts/variant_params.json')
    args=parser.parse_args();start=time.perf_counter()
    for folder in ['variants','results','renders','tmp']:(ROOT/folder).mkdir(exist_ok=True,parents=True)
    params=json.loads(args.params.read_text());g=Glb(args.source)
    assert sha(g.raw)==params['sourceSha256']
    face,pmap,nmap,coverage=bake_actual_surface(g,params['atlasPixels'])
    # Verify the existing cylindrical atlas, then use continuous UV coordinates.
    # Direct position interpolation was visibly broken by overlapping lip/eyelid UVs.
    source_positions=g.accessor(face['attributes']['POSITION'])
    source_uv=g.accessor(face['attributes']['TEXCOORD_0'])
    y_coeff=np.polyfit(source_uv[:,1],source_positions[:,1],1)
    y_error=float(np.max(np.abs(np.polyval(y_coeff,source_uv[:,1])-source_positions[:,1])))
    expected_u=(np.arctan2(source_positions[:,0],source_positions[:,2]-.04)/(2*np.pi)+.5)%1
    u_error=float(np.max(np.abs(expected_u-source_uv[:,0])))
    assert y_error<1e-6 and u_error<1e-6
    v,u=np.mgrid[0:params['atlasPixels'],0:params['atlasPixels']].astype(np.float32)/(params['atlasPixels']-1)
    y=np.polyval(y_coeff,v);theta=(u-.5)*2*np.pi
    x=.09*np.sin(theta);z=.04+.09*np.cos(theta)
    # Deliberate artistic contour field, mapped through the verified original UVs.
    # The cylinder is a coordinate construction only, never replacement geometry.
    field=y+.018*np.sin(26*x+15*z)+.009*np.sin(45*z-17*x)
    field+=.021*np.exp(-((np.abs(x)-.031)/.027)**2-((y-.286)/.021)**2)
    field-=.012*np.exp(-(x/.035)**2-((y-.225)/.019)**2)
    mix=np.clip((x+.055)/.11,0,1)
    front=np.clip((nmap[...,2]+.18)/.36,0,1)
    records=[]
    for cfg in params['variants']:
        t=time.perf_counter();doc=copy.deepcopy(g.doc);binary=bytearray(g.binary)
        spacing=cfg['lineSpacingMetres'];distance=np.abs((field+spacing/2)%spacing-spacing/2)
        lines=np.exp(-(distance/cfg['lineSigmaMetres'])**2)
        rim=(np.maximum(0,1-np.abs(nmap[...,2]))**cfg['rimExponent'])*front*coverage
        rim=np.asarray(Image.fromarray((rim*255).astype(np.uint8)).filter(
            ImageFilter.GaussianBlur(params['rimBlurTexels'])),dtype=np.float32)/255
        # Keep eyelid volume dark enough that a blink remains readable.
        eyelid=np.ones_like(lines)
        for eye_x in [-.0305119436,.0305119436]:
            d=((x-eye_x)/.022)**2+((y-.2884144118)/.015)**2
            eyelid*=np.clip((d-.30)/1.0,0,1)
        rim*=eyelid
        violet=np.asarray(cfg['violetRgb8'],np.float32);cyan=np.asarray(cfg['cyanRgb8'],np.float32)
        tint=violet*(1-mix[...,None])+cyan*mix[...,None]
        base=np.asarray(cfg['baseRgb8'],np.float32)
        base_rgb=np.clip(base+(tint-base)*(lines*.36)[...,None],0,255)
        emission=np.clip(tint*np.clip(lines*cfg['lineGain']+rim*cfg['rimGain'],0,1)[...,None],0,255)
        base_png=encode(base_rgb);emission_png=encode(emission)
        for label,data in [('base',base_png),('emission',emission_png)]:
            (ROOT/'variants'/f'{cfg["id"]}-{label}.png').write_bytes(data)
        base_index=append_texture(doc,binary,base_png,cfg['id']+'-base')
        emission_index=append_texture(doc,binary,emission_png,cfg['id']+'-emission')
        mats={m['name']:m for m in doc['materials']}
        skin=mats['DeepIndigoSkin'];pbr=skin['pbrMetallicRoughness']
        pbr.update(baseColorFactor=[1,1,1,1],baseColorTexture={'index':base_index},metallicFactor=.08,roughnessFactor=cfg['skinRoughness'])
        skin['emissiveFactor']=[1,1,1];skin['emissiveTexture']={'index':emission_index};skin['alphaMode']='OPAQUE'
        skin.setdefault('extensions',{})['KHR_materials_emissive_strength']={'emissiveStrength':cfg['emissiveStrength']}
        mats['NaturalSclera']['pbrMetallicRoughness'].update(baseColorFactor=cfg['scleraBaseLinear'],metallicFactor=0,roughnessFactor=cfg['eyeRoughness'])
        mats['CyanIris']['pbrMetallicRoughness'].update(baseColorFactor=cfg['irisBaseLinear'],metallicFactor=cfg['irisMetallic'],roughnessFactor=cfg['eyeRoughness'])
        mats['CyanIris']['emissiveFactor']=cfg['irisEmissionLinear']
        for name in ['VioletThreads','CyanThreads']:
            mats[name]['pbrMetallicRoughness']['baseColorFactor'][3]=cfg['physicalThreadAlpha']
            mats[name]['emissiveFactor']=[0,0,0];mats[name]['alphaMode']='BLEND'
        doc['buffers'][0]['byteLength']=len(binary);binary.extend(b'\0'*(-len(binary)%4))
        encoded=json.dumps(doc,separators=(',',':')).encode();encoded+=b' '*(-len(encoded)%4)
        raw=struct.pack('<III',0x46546c67,2,28+len(encoded)+len(binary))
        raw+=struct.pack('<II',len(encoded),0x4e4f534a)+encoded
        raw+=struct.pack('<II',len(binary),0x004e4942)+binary
        path=ROOT/'variants'/f'{cfg["id"]}.glb';path.write_bytes(raw)
        # Exact invariants: same accessors, geometry, nodes, skin and original binary prefix.
        reread=Glb(path)
        stable=['accessors','meshes','nodes','skins','scenes','scene','animations']
        invariant={key:reread.doc.get(key)==g.doc.get(key) for key in stable}
        invariant['originalBinaryPrefixEqual']=reread.binary[:len(g.binary)]==g.binary
        assert all(invariant.values())
        original_names=[m['extras']['targetNames'] for m in g.doc['meshes'] if m.get('extras',{}).get('targetNames')]
        assert all(len(names)==21 for names in original_names)
        records.append({'id':cfg['id'],'file':str(path.relative_to(ROOT)),'bytes':len(raw),'sha256':sha(raw),
            'parameters':cfg,'invariants':invariant,'morphTargetsPerMorphedMesh':21,
            'textureHashes':{'base':sha(base_png),'emission':sha(emission_png)},
            'newTextureBytes':len(base_png)+len(emission_png),'durationSeconds':time.perf_counter()-t})
    assert sha(args.source.read_bytes())==params['sourceSha256']
    result={'evidenceClass':'HOST_MATERIAL_VARIANTS_NOT_DEVICE','source':str(args.source.resolve()),
        'sourceSha256BeforeAndAfter':sha(g.raw),'parametersSha256':sha(args.params.read_bytes()),
        'scriptSha256':sha(Path(__file__).read_bytes()),'atlasPixels':params['atlasPixels'],
        'uvMappingVerification':{'yFromV':y_coeff.tolist(),'maximumYErrorMetres':y_error,'maximumUError':u_error},
        'coveredAtlasTexels':int(coverage.sum()),'variants':records,'durationSeconds':time.perf_counter()-start,
        'limitations':['No changed head/rig/morph geometry','Not final reference likeness',
            'Original physical threads are transparent; replacement contours are baked into the face UVs',
            'Contour emission uses neutral object-space normals, not view-dependent rim lighting',
            'Filament, live speech, aliasing in motion and Fold7 costs NOT_RUN']}
    (ROOT/'results/variant-manifest.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'durationSeconds':result['durationSeconds'],'sourceSha256':sha(g.raw),
        'variants':[{'id':r['id'],'bytes':r['bytes'],'sha256':r['sha256'],'invariants':r['invariants']} for r in records]},indent=2))

if __name__=='__main__':build()
