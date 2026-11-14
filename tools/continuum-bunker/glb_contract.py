"""Preserve source animation samples exactly."""
import copy
import hashlib
import json
import math
from pathlib import Path
import struct

COMPONENTS={5120:('b',1),5121:('B',1),5122:('h',2),5123:('H',2),5125:('I',4),5126:('f',4)}
WIDTHS={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT2':4,'MAT3':9,'MAT4':16}


def read(path):
    data=Path(path).read_bytes()
    magic,version,length=struct.unpack_from('<III',data)
    assert (magic,version,length)==(0x46546c67,2,len(data))
    size,kind=struct.unpack_from('<II',data,12)
    assert kind==0x4e4f534a
    document=json.loads(data[20:20+size])
    count,kind=struct.unpack_from('<II',data,20+size)
    assert kind==0x004e4942 and len(data)==28+size+count
    return document,data[28+size:]


def accessor(document,binary,index):
    entry=document['accessors'][index]
    assert 'sparse' not in entry
    view=document['bufferViews'][entry['bufferView']]
    code,size=COMPONENTS[entry['componentType']]
    width=WIDTHS[entry['type']]
    start=view.get('byteOffset',0)+entry.get('byteOffset',0)
    stride=view.get('byteStride',size*width)
    assert start+(entry['count']-1)*stride+size*width<=len(binary)
    raw=b''.join(binary[start+i*stride:start+i*stride+size*width] for i in range(entry['count']))
    values=list(struct.iter_unpack('<'+code*width,raw))
    assert all(math.isfinite(v) for row in values for v in row)
    return raw,values


def preserve(source,target):
    original,source_bin=read(source)
    exported,target_bin=read(target)
    binary=bytearray(target_bin)
    new_animations={a['name']:a for a in exported['animations']}
    preserved=[]
    for animation in original['animations']:
        mapped=new_animations[animation['name']]
        assert len(mapped['channels'])==len(animation['channels'])
        restored=copy.deepcopy(animation)
        for channel,current in zip(restored['channels'],mapped['channels']):
            assert channel['target']['path']==current['target']['path']
            old_name=original['nodes'][channel['target']['node']]['name']
            new_name=exported['nodes'][current['target']['node']]['name']
            assert new_name==old_name or new_name.startswith(old_name+'.')
            channel['target']['node']=current['target']['node']
        for sampler in restored['samplers']:
            for key in ['input','output']:
                index=sampler[key]
                raw,_=accessor(original,source_bin,index)
                binary.extend(b'\0'*((-len(binary))%4))
                entry=copy.deepcopy(original['accessors'][index])
                entry.pop('byteOffset',None)
                entry['bufferView']=len(exported['bufferViews'])
                exported['bufferViews'].append({'buffer':0,'byteOffset':len(binary),'byteLength':len(raw)})
                sampler[key]=len(exported['accessors'])
                exported['accessors'].append(entry)
                binary.extend(raw)
        preserved.append(restored)
    exported['animations']=preserved
    exported['buffers'][0]['byteLength']=len(binary)
    binary.extend(b'\0'*((-len(binary))%4))
    data=json.dumps(exported,separators=(',',':'),ensure_ascii=False).encode()
    data+=b' '*((-len(data))%4)
    output=struct.pack('<III',0x46546c67,2,28+len(data)+len(binary))
    output+=struct.pack('<II',len(data),0x4e4f534a)+data
    output+=struct.pack('<II',len(binary),0x004e4942)+binary
    Path(target).write_bytes(output)


def validate(path,source=None):
    document,binary=read(path)
    assert not document.get('images')
    assert all('uri' not in b for b in document.get('buffers',[]))
    for index in range(len(document['accessors'])):
        accessor(document,binary,index)
    triangles=0
    for mesh in document['meshes']:
        for primitive in mesh['primitives']:
            position=document['accessors'][primitive['attributes']['POSITION']]
            if 'indices' in primitive:
                _,indices=accessor(document,binary,primitive['indices'])
                assert all(0<=i[0]<position['count'] for i in indices)
                count=len(indices)
            else:
                count=position['count']
            assert primitive.get('mode',4)==4 and count%3==0
            triangles+=count//3
    animations=[]
    if source:
        original,source_bin=read(source)
        for expected,actual in zip(original['animations'],document['animations']):
            assert expected['name']==actual['name']
            for a,b in zip(expected['samplers'],actual['samplers']):
                assert a.get('interpolation','LINEAR')==b.get('interpolation','LINEAR')
                for key in ['input','output']:
                    assert accessor(original,source_bin,a[key])[0]==accessor(document,binary,b[key])[0]
            animations.append(actual['name'])
        assert len(original['animations'])==len(document['animations'])==4
    return {'file':Path(path).name,'bytes':Path(path).stat().st_size,
        'sha256':hashlib.sha256(Path(path).read_bytes()).hexdigest(),
        'meshes':len(document['meshes']),'nodes':len(document['nodes']),
        'materials':len(document['materials']),'triangles':triangles,
        'animationSamplesIdentical':animations,
        'externalResources':False,'finiteAccessors':True,'validIndices':True}
