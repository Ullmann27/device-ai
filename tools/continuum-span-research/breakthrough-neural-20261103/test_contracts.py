#!/usr/bin/env python3
"""Real-process contract tests using previously compiled frozen predictor classes."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[3]
CLASSES=Path(sys.argv.pop(1)).resolve()
CP=str(CLASSES)+':/workspace/continuum-build-env/jvm-deps/json-20240303.jar:/workspace/continuum-build-env/kotlin-official/kotlinc/lib/kotlin-stdlib.jar'
EVIDENCE=ROOT/'docs/evidence/breakthrough-neural-20261103'
BASE=ROOT/'docs/evidence/issue340-20261101/m2/frozen-dev/checkpoint.json'
CURRENT=ROOT/'docs/evidence/synapse-iii-20261102/m2-adapter/candidate/checkpoint-17.json'
CANDIDATE=EVIDENCE/'checkpoints/residual15.json'
def digest(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()

class ContractTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.directory=Path(self.temp.name)
        self.snapshot={p:digest(p) for p in [BASE,CURRENT,CANDIDATE]}
    def tearDown(self):
        self.assertEqual(self.snapshot,{p:digest(p) for p in self.snapshot})
        self.temp.cleanup()
    def save(self,name,value):
        path=self.directory/name;path.write_text(json.dumps(value,ensure_ascii=False)+'\n');return path
    def invoke(self,inputs,candidate=CANDIDATE):
        env=dict(os.environ,JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -Xmx1g')
        return subprocess.run(['java','-cp',CP,'at.heinz.deviceai.learning.span.GeneralizationProbe',
            str(BASE),str(CURRENT),str(inputs),str(self.directory/'out'),str(candidate)],
            capture_output=True,text=True,timeout=30,env=env)
    def test_gold_labels_are_refused_by_predictor(self):
        cases=json.loads((EVIDENCE/'development/inputs.json').read_text())['cases'][:1]
        cases[0]['expected']=None
        result=self.invoke(self.save('leaked.json',dict(cases=cases)))
        self.assertNotEqual(result.returncode,0)
        self.assertIn('label-free request required',result.stderr)
        self.assertFalse((self.directory/'out/trained15-predictions.json').exists())
    def test_frozen_weight_mutation_is_refused(self):
        candidate=json.loads(CANDIDATE.read_text());candidate['weights'][4]+=.01
        result=self.invoke(EVIDENCE/'development/inputs.json',self.save('mutated.json',candidate))
        self.assertNotEqual(result.returncode,0)
        self.assertFalse((self.directory/'out/trained15-predictions.json').exists())
    def test_request_count_bound_is_enforced(self):
        case=json.loads((EVIDENCE/'development/inputs.json').read_text())['cases'][0]
        result=self.invoke(self.save('oversized.json',dict(cases=[dict(case,id=f'oversize-{i}') for i in range(257)])))
        self.assertNotEqual(result.returncode,0)
        self.assertFalse((self.directory/'out').exists())
    def test_nonfinite_residual_is_refused(self):
        candidate=json.loads(CANDIDATE.read_text());candidate['weights'][13]='NaN'
        result=self.invoke(EVIDENCE/'development/inputs.json',self.save('nan.json',candidate))
        self.assertNotEqual(result.returncode,0)
        self.assertFalse((self.directory/'out/trained15-predictions.json').exists())
    def test_training_reproduces_frozen_checkpoint_exactly(self):
        result=subprocess.run(['java','-XX:ActiveProcessorCount=2','-Xmx1g','-cp',CP,
            'at.heinz.deviceai.learning.span.GroundedSlotResidual',str(CURRENT),
            str(EVIDENCE/'training/corpus.json'),str(self.directory/'checkpoint.json')],
            capture_output=True,text=True,timeout=30)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(digest(self.directory/'checkpoint.json'),digest(CANDIDATE))

if __name__=='__main__': unittest.main(verbosity=2)
