#!/usr/bin/env python3
"""Build the schema and hash manifest; no model or application code is executed."""
import hashlib, json
from pathlib import Path
p=Path(__file__).resolve().parent
strtype={'type':'string'}
sha={'type':'string','pattern':'^[0-9a-f]{64}$'}
idtype={'type':'string','pattern':'^[a-zA-Z0-9._:-]{1,96}$'}
rev={'type':'integer','minimum':1}
spanprops={'text':{'type':'string','minLength':1},'sourceId':idtype,'revision':rev,'sha256':sha,
           'start':{'type':'integer','minimum':0},'endExclusive':{'type':'integer','minimum':1}}
sourceprops={k:spanprops[k] for k in ['text','sourceId','revision','sha256']}
def obj(props): return {'type':'object','properties':props,'required':list(props),'additionalProperties':False}
caseprops={'id':{'type':'string','pattern':'^C[0-9]{3}$'},'split':{'enum':['PRIMARY','ADVERSARIAL']},
'category':{'enum':['SUPPORTED','MISSING','NEGATION','CONFLICT','TIME','ROLE','RELATION','SAME_NAME_SOURCE_BOUNDARY']},
'question':{'type':'string','minLength':1,'maxLength':159},'sources':{'type':'array','minItems':1,'maxItems':4,'items':obj(sourceprops)},
'expected':{'oneOf':[{'type':'null'},obj(spanprops)]}}
schema=obj({'schema':{'const':'ISSUE340_BLIND_C_V1'},'seed':{'const':34020261101},'cases':{'type':'array','minItems':55,'maxItems':55,'items':obj(caseprops)}})
schema['$schema']='https://json-schema.org/draft/2020-12/schema'
schema['$comment']='Cross-field identity, digest, span, uniqueness and split counts validated by generator; expected offsets are UTF-16 code units.'
(p/'corpus.schema.json').write_text(json.dumps(schema,indent=2,sort_keys=True)+'\n')
files=['generate.py','freeze.py','corpus.json','inputs.json','corpus.schema.json','overlap.json','SCORING.md']
manifest={'schema':'ISSUE340_BLIND_C_FREEZE_V1','seed':34020261101,'status':'FROZEN_NOT_RUN_LOCAL_ONLY',
'counts':{'primarySupported':20,'primaryMissing':5,'adversarial':30,'total':55},
'files':{name:{'sha256':hashlib.sha256((p/name).read_bytes()).hexdigest(),'bytes':(p/name).stat().st_size} for name in files}}
(p/'MANIFEST.json').write_text(json.dumps(manifest,indent=2,sort_keys=True)+'\n')
(p/'SHA256SUMS').write_text(''.join(f'{v["sha256"]}  {name}\n' for name,v in manifest['files'].items())+hashlib.sha256((p/'MANIFEST.json').read_bytes()).hexdigest()+'  MANIFEST.json\n')
print(json.dumps({'manifestSha256':hashlib.sha256((p/'MANIFEST.json').read_bytes()).hexdigest(),'corpusSha256':manifest['files']['corpus.json']['sha256'],'counts':manifest['counts'],'path':str(p)}))
