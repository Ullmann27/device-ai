import hashlib, json, subprocess
from pathlib import Path
root=Path(__file__).resolve().parent
qa=root.parent
names={'before':'initial-red','after':'combined-holdout-green'}
manifests={k:json.loads((qa/v/'manifest.json').read_text()) for k,v in names.items()}
java=Path(manifests['before']['compileCommand'][0])
subprocess.run([str(java.with_name('javac')),str(root/'RedactorExperiment.java')],check=True,timeout=30)
runs=[]
for round_number, order in enumerate([['before','after'],['after','before'],['before','after']]):
 for label in order:
  manifest=manifests[label]
  cp=manifest['compileCommand'][manifest['compileCommand'].index('-classpath')+1]
  command=[str(java),'-Xmx384m','-XX:ActiveProcessorCount=1','-cp',str(root)+':'+str(qa/names[label]/'classes')+':'+cp,'RedactorExperiment']
  target=root/f'{round_number}-{label}.jsonl'
  with target.open('x') as output:
   result=subprocess.run(command,stdout=output,stderr=subprocess.STDOUT,timeout=90)
  assert result.returncode==0, target.read_text()
  rows=[json.loads(s) for s in target.read_text().splitlines()]
  assert len(rows)==20
  runs.append({'label':label,'round':round_number,'source':manifest['source'],'tree':manifest['tree'],'manifestSha256':hashlib.sha256((qa/names[label]/'manifest.json').read_bytes()).hexdigest(),'output':target.name,'outputSha256':hashlib.sha256(target.read_bytes()).hexdigest(),'command':command})
  print(json.dumps({'round':round_number,'label':label,'samples':len(rows)}),flush=True)
receipt={'runs':runs,'driverSha256':hashlib.sha256((root/'RedactorExperiment.java').read_bytes()).hexdigest(),'runnerSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'scope':'Private production redactor, reflection overhead included; host JVM, not model latency or Android battery performance. Three process pairs, fixed synthetic inputs, no pattern cache.'}
(root/'manifest.json').write_text(json.dumps(receipt,indent=2)+'\n')
