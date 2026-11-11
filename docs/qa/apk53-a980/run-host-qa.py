import json, os, pathlib, subprocess, time
repo=pathlib.Path('/workspace/scratch/5900afa6af29/device-ai')
base=repo.parent/'apk53-a980-host'
base.mkdir(exist_ok=False)
expected='a980a19ed3ab44323009ae27eb299d322ff11679'
def git(*args): return subprocess.check_output(['git',*args],cwd=repo,text=True).strip()
assert git('rev-parse','HEAD') == expected and not git('status','--porcelain')
env=os.environ.copy()
env['JAVA_HOME']='/tmp/continuum-toolchain/jdk'
env['PATH']='/tmp/continuum-toolchain/kotlinc/bin:'+env['JAVA_HOME']+'/bin:'+env['PATH']
env['JAVA_OPTS']='-Xmx384m -XX:ActiveProcessorCount=2'
checks=[{'check': 'critical', 'command': ['node', 'scripts/continuum-reliability-qa.mjs', 'critical', '--out', '/workspace/scratch/5900afa6af29/apk53-a980-host/critical']}, {'check': 'full-node', 'command': ['node', 'scripts/continuum-reliability-qa.mjs', 'full-node', '--out', '/workspace/scratch/5900afa6af29/apk53-a980-host/full-node']}, {'check': 'backend-check', 'command': ['npm', '--prefix', 'backend', 'run', 'check']}, {'check': 'backend-test', 'command': ['npm', '--prefix', 'backend', 'test']}, {'check': 'source-safety', 'command': ['bash', 'scripts/code52-targeted-kotlin-check.sh']}, {'check': 'fidelity', 'command': ['bash', 'scripts/hollywood-fui-reference-fidelity.sh', '/workspace/scratch/5900afa6af29/apk53-a980-host/fidelity', 'CODE53-a980']}, {'check': 'preview', 'command': ['bash', 'scripts/hollywood-fui-cinematics-preview.sh', '/workspace/scratch/5900afa6af29/apk53-a980-host/preview']}]
for item in checks:
 print('START',item['check'],flush=True)
 with (base/(item['check']+'.log')).open('w') as log:
  item['exit']=subprocess.run(item['command'],cwd=repo,env=env,stdout=log,stderr=subprocess.STDOUT).returncode
 print('FINISHED',item['check'],item['exit'],flush=True)
result={'source':expected,'endSource':git('rev-parse','HEAD'),'endStatus':git('status','--porcelain'),'checks':checks}
(base/'result.json').write_text(json.dumps(result,indent=2))
assert result['endSource']==expected and not result['endStatus'] and all(c['exit']==0 for c in checks)
