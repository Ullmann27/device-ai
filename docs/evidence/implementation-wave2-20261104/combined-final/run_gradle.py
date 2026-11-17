from __future__ import annotations
import os, sys, subprocess, pathlib, json, fcntl, datetime, hashlib
work=pathlib.Path('/workspace/scratch/d663e8444310')
tc=pathlib.Path('/workspace/scratch/b6ee64d418cf/toolchain')
checkout=pathlib.Path(sys.argv[1]).resolve()
label=sys.argv[2]
if pathlib.Path(label).name!=label: raise SystemExit('label must be a filename')
args=[str(tc/'gradle-8.11.1/bin/gradle'),'--offline','--no-daemon','--max-workers=1','--console=plain','-Pkotlin.compiler.execution.strategy=in-process',*sys.argv[3:]]
env=dict(os.environ)
env.update(JAVA_HOME=str(tc/'jdk17'),ANDROID_HOME=str(tc/'android-sdk'),ANDROID_SDK_ROOT=str(tc/'android-sdk'),ANDROID_USER_HOME=str(work/'android-user'),GRADLE_USER_HOME=str(work/'gradle-cache'))
env['PATH']=str(tc/'jdk17/bin')+os.pathsep+env.get('PATH','')
for name in ['CONTINUUM_LOCAL_SIGNING_FILE','CONTINUUM_LOCAL_SIGNING_PASSWORD','CONTINUUM_LOCAL_SIGNING_P12_BASE64','CONTINUUM_MENTOR_SIGNING_FILE','CONTINUUM_MENTOR_SIGNING_PASSWORD','CONTINUUM_MENTOR_SIGNING_P12_BASE64']: env.pop(name,None)
with (work/'qa/gradle.lock').open('a') as lock:
 fcntl.flock(lock,fcntl.LOCK_EX)
 def git(*a): return subprocess.check_output(['git',*a],cwd=checkout,text=True).strip()
 manifest={str(p.relative_to(checkout)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((checkout/'app/src').rglob('*')) if p.is_file()}
 header={'source':git('rev-parse','HEAD'),'tree':git('rev-parse','HEAD^{tree}'),'dirty':git('status','--porcelain'),'checkout':str(checkout),'args':args,'started_at':datetime.datetime.now(datetime.timezone.utc).isoformat()}
 (work/'qa'/f'{label}.sources.json').write_text(json.dumps(manifest,indent=2)+'\n')
 with (work/'qa'/f'{label}.log').open('x') as out:
  out.write(json.dumps(header)+'\n'); out.flush()
  print(json.dumps(header),flush=True)
  p=subprocess.Popen(args,cwd=checkout,env=env,stdout=out,stderr=subprocess.STDOUT)
  rc=p.wait()
  out.write(f'\nEXIT_CODE={rc}\n')
 manifest_after={str(p.relative_to(checkout)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((checkout/'app/src').rglob('*')) if p.is_file()}
 result={**header,'exit_code':rc,'source_unchanged':manifest==manifest_after,'finished_at':datetime.datetime.now(datetime.timezone.utc).isoformat()}
 (work/'qa'/f'{label}.result.json').write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps(result),flush=True)
 raise SystemExit(rc)
