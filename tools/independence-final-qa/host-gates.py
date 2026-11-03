#!/usr/bin/env python3
"""Serialize existing host checks; consumes no neural holdout and downloads nothing."""
import argparse, hashlib, json, os, pathlib, subprocess, time
p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--output',required=True);p.add_argument('--classpath-file',required=True);p.add_argument('--runtime-env',required=True);p.add_argument('--isolation-config',required=True);a=p.parse_args()
root=pathlib.Path(a.source).resolve();out=pathlib.Path(a.output).resolve();out.mkdir(parents=True,exist_ok=False)
own=pathlib.Path(__file__).resolve().parent
def git(*args):return subprocess.check_output(['git',*args],cwd=root,text=True).strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def manifest():
 paths=git('ls-files','.github/continuum','scripts','tools/genesis-workspaces','app/src/main','app/build.gradle.kts','AGENTS.md').splitlines()
 external={str(f):digest(f) for base in [own,pathlib.Path('/workspace/independence-service-review/tools/independence-service-review'),pathlib.Path('/workspace/independence-mcp-review/tools/independence-mcp-review')] for f in sorted(base.rglob('*')) if f.is_file()}
 return {'source':git('rev-parse','HEAD'),'status':git('status','--porcelain','--untracked-files=all'),'inputs':{x:digest(root/x) for x in paths if (root/x).is_file()},'externalTests':external}
before=manifest();assert before['status']=='';(out/'before.json').write_text(json.dumps(before,indent=2)+'\n')
env=os.environ.copy();env.update({'PATH':'/workspace/continuum-build-env/jdk17/bin:'+os.environ['PATH'],
 'SERVICE_REVIEW_CANDIDATE':str(root),'SERVICE_REVIEW_CLASSPATH_FILE':a.classpath_file,'SERVICE_REVIEW_RUNTIME_ENV':a.runtime_env,
 'SERVICE_REVIEW_ISOLATION':a.isolation_config,'SERVICE_REVIEW_EVIDENCE':str(out/'service-details'),'SERVICE_REVIEW_KEEP_FIXTURES':'1',
 'CONTINUUM_MCP_CANDIDATE':str(root),'CONTINUUM_PROFILE_CANDIDATE':str(root),'CONTINUUM_SERVICE_CANDIDATE':str(root),
 'GENESIS_HANDOFF_CLASSPATH':pathlib.Path(a.classpath_file).read_text().strip(),'CONTINUUM_REVIEW_RUNTIME_ENV':a.runtime_env})
baseline=json.loads((root/'docs/evidence/bot-genesis/final-node-1595/before.json').read_text())['tests']
commands=[('canonical-and-modules',['node','--test','--test-concurrency=1','--test-reporter=tap',*baseline,
 '.github/continuum/coding-service.test.mjs','.github/continuum/coding-mcp.test.mjs','.github/continuum/coding-mcp-process.test.mjs']),
 ('service-independent',['node','--test','--test-concurrency=1','--test-reporter=tap',*[f'/workspace/independence-service-review/tools/independence-service-review/{f}.test.mjs' for f in ['lifecycle','active-recovery']]]),
 ('mcp-independent',['node','--test','--test-concurrency=1','--test-reporter=tap',*[f'/workspace/independence-mcp-review/tools/independence-mcp-review/{f}.test.mjs' for f in ['transport','profile-bridge','canonical-reader','canonical-http']]]),
 ('integrated-actual',['node',str(own/'integrated-check.mjs'),'--source',str(root),'--classpath-file',a.classpath_file,'--runtime-env',a.runtime_env,'--isolation-config',a.isolation_config,'--output',str(out/'integrated-actual.json')])]
results=[]
for name,command in commands:
 (out/f'{name}.command.json').write_text(json.dumps(command,indent=2)+'\n');start=time.monotonic()
 with (out/f'{name}.log').open('w') as log:r=subprocess.run(command,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
 row={'name':name,'exit':r.returncode,'elapsedSeconds':time.monotonic()-start};results.append(row)
 (out/f'{name}.exit').write_text(str(r.returncode)+'\n');(out/'results.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps(row),flush=True)
 if r.returncode:break # no dependent process operation after an unresolved failure
after=manifest();(out/'after.json').write_text(json.dumps(after,indent=2)+'\n')
assert before==after,'Input drift'
(out/'SHA256SUMS').write_text(''.join(f'{digest(f)}  {f.relative_to(out)}\n' for f in sorted(out.rglob('*')) if f.is_file() and f.name!='SHA256SUMS'))
raise SystemExit(next((r['exit'] for r in results if r['exit']),0))
