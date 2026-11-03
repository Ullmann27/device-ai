#!/usr/bin/env python3
"""Pinned-source paired QA execution. Never imports implementer test helpers."""
import json,pathlib,hashlib,subprocess,sys,time
root=pathlib.Path(__file__).resolve().parents[4]
lane,base,candidate,fixture,outdir=sys.argv[1:]
out=pathlib.Path(outdir);out.mkdir(parents=True,exist_ok=False)
here=pathlib.Path(__file__).resolve().parent
sources={'cognition':['cognitive/OwnerStatementTimeline.kt'], 'memory':['learning/ContinuumLearningExperience.kt','learning/Lex3LearningExperienceStore.kt','learning/PersonalLearningTransfer.kt'], 'research':['cognitive/ResearchEngine.kt']}[lane]
runner={'cognition':'CognitionHoldout','memory':'MemoryHoldout','research':'ResearchHoldout'}[lane]
cp=':'.join(map(str,sorted(pathlib.Path('/workspace/continuum-build-env/jvm-deps').glob('*.jar'))))
dep='/workspace/continuum-quality/app/build/tmp/kotlin-classes/debug'
if lane=='research':cp+=':'+dep
receipt={'fixtureSha256':hashlib.sha256(pathlib.Path(fixture).read_bytes()).hexdigest(),'runnerSha256':hashlib.sha256((here/(runner+'.kt')).read_bytes()).hexdigest(),'runs':[]}
for label,sha in [('baseline',base),('candidate',candidate)]:
 d=out/label;d.mkdir();paths=[]
 for name in sources:
  target=d/pathlib.Path(name).name
  target.write_bytes(subprocess.check_output(['git','show',sha+':app/src/main/java/at/heinz/deviceai/'+name],cwd=root))
  paths.append(str(target))
 receipt[label]={'sha':sha,'sources':{str(p):hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest() for p in paths}}
 commands=[['kotlinc',*paths,str(here/(runner+'.kt')),'-module-name','app','-Xfriend-paths='+dep,'-jvm-target','17','-cp',cp,'-d',str(d/'classes')],['java','-Xmx256m','-cp',str(d/'classes')+':'+cp,'at.heinz.deviceai.'+('learning' if lane=='memory' else 'cognitive')+'.'+runner,fixture,str(d/'results.json')]]
 for name,command in zip(['compile','execute'],commands):
  start=time.monotonic();r=subprocess.run(command,cwd=root,capture_output=True,text=True)
  (d/(name+'.txt')).write_text('COMMAND='+repr(command)+'\n'+r.stdout+r.stderr+'\nEXIT='+str(r.returncode)+'\n')
  receipt['runs'].append({'label':label,'step':name,'exit':r.returncode,'seconds':time.monotonic()-start})
  (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
  print(label,name,r.returncode,r.stdout[-200:],flush=True)
  if r.returncode:sys.exit(r.returncode)
