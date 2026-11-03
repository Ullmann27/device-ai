import copy
import json
import math
import struct
import unittest
from validate_glb import Glb, TARGETS, validate


def fixture(change=lambda d: None, zero=None, mirror=False, huge=False):
    binary=bytearray(); accessors=[]; views=[]
    def accessor(rows, kind='VEC3', component=5126):
        fmt,width={5126:('f',4),5123:('H',2)}[component]
        flat=[n for row in rows for n in row]
        packed=struct.pack('<'+fmt*len(flat),*flat)
        views.append({'buffer':0,'byteOffset':len(binary),'byteLength':len(packed)})
        binary.extend(packed)
        while len(binary)%4:binary.append(0)
        accessors.append({'bufferView':len(views)-1,'componentType':component,'count':len(rows),'type':kind})
        return len(accessors)-1
    positions=[(.03,.12,.01),(-.03,.12,.01),(.01,.09,.03),(.03,.01,.01),(-.03,.01,.01),(0,0,.04)]
    position=accessor(positions); indices=accessor([(i,) for i in range(6)],'SCALAR',5123)
    joints=accessor([(1,0,0,0)]*6,'VEC4',5123); weights=accessor([(1,0,0,0)]*6,'VEC4')
    inverse=accessor([tuple(float(i in (0,5,10,15)) for i in range(16))]*2,'MAT4')
    targets=[]
    for name in TARGETS:
        deltas=[]
        for x,y,z in positions:
            active=(y>.06 if name.startswith(('Blink','Brow')) else y<.06)
            if name=='BlinkLeft':active=active and (x<0 if mirror else x>0)
            if name=='BlinkRight':active=active and x<0
            value=(1 if huge else .002) if active and name!=zero else 0
            deltas.append((0,value,0))
        targets.append({'POSITION':accessor(deltas)})
    doc={'asset':{'version':'2.0'},'buffers':[{'byteLength':len(binary)}],'bufferViews':views,'accessors':accessors,
         'nodes':[{'name':'AvatarRoot','children':[1,6]},{'name':'Neck','children':[2]}, {'name':'Head','children':[3,4,5]},
                  {'name':'EyeLeft','translation':[.03,.1,.03]},{'name':'EyeRight','translation':[-.03,.1,.03]},
                  {'name':'Jaw','translation':[0,.02,.01]},{'name':'Face','mesh':0,'skin':0}],
         'skins':[{'joints':[1,2],'inverseBindMatrices':inverse}],
         'meshes':[{'extras':{'targetNames':TARGETS.copy()},'primitives':[{'attributes':{'POSITION':position,'JOINTS_0':joints,'WEIGHTS_0':weights},'indices':indices,'targets':targets}]}]}
    change(doc)
    encoded=json.dumps(doc,separators=(',',':')).encode()
    while len(encoded)%4:encoded+=b' '
    return struct.pack('<III',0x46546c67,2,28+len(encoded)+len(binary))+struct.pack('<II',len(encoded),0x4e4f534a)+encoded+struct.pack('<II',len(binary),0x004e4942)+binary


class GeometryContractTest(unittest.TestCase):
    def test_full_fixture_and_zero_sil(self):
        for data in [fixture(),fixture(zero='SIL')]:
            result=validate(data);self.assertEqual('PASS',result['status'],result['failures'])
    def test_independent_blink_mirror_detected(self):
        result=validate(fixture(mirror=True));self.assertTrue(any('BlinkLeft' in f['test'] for f in result['failures']))
    def test_zero_non_silent_target_and_excessive_deformation(self):
        self.assertEqual('FAIL',validate(fixture(zero='AA'))['status'])
        self.assertEqual('FAIL',validate(fixture(huge=True))['status'])
    def test_named_morph_duplicate_and_missing(self):
        for mutation in [lambda d:d['meshes'][0]['extras']['targetNames'].__setitem__(0,'PP'),
                         lambda d:d['meshes'][0]['primitives'][0]['targets'].__setitem__(1,d['meshes'][0]['primitives'][0]['targets'][0])]:
            self.assertEqual('FAIL',validate(fixture(mutation))['status'])
    def test_eye_node_mirror_and_rig_nonmoving(self):
        self.assertEqual('FAIL',validate(fixture(lambda d:d['nodes'][3].update(translation=[-.03,.1,.03])))['status'])
        self.assertEqual('FAIL',validate(fixture(lambda d:d['skins'][0].update(joints=[1,1])))['status'])
    def test_invalid_binary_and_resources_fail_before_geometry(self):
        valid=fixture()
        candidates=[valid[:27],valid[:-1],b'xxxx'+valid[4:],fixture(lambda d:d['buffers'][0].update(uri='evil.bin')),
                    fixture(lambda d:d['nodes'][2].update(children=[0,3,4,5])),
                    fixture(lambda d:d['accessors'][0].update(count=1000000)),
                    fixture(lambda d:d['bufferViews'][0].update(byteOffset=999999)),
                    fixture(lambda d:d['accessors'][0].update(sparse={})),
                    fixture(lambda d:d['nodes'][3].update(name='Head'))]
        for candidate in candidates:
            with self.assertRaises((ValueError,KeyError,IndexError,struct.error)):
                Glb(candidate)
    def test_nonfinite_geometry_rejected(self):
        data=bytearray(fixture());json_length=struct.unpack_from('<I',data,12)[0]
        struct.pack_into('<f',data,28+json_length,math.nan)
        with self.assertRaises(ValueError):Glb(data)

if __name__=='__main__':unittest.main()
