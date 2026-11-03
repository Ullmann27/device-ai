#!/usr/bin/env python3
"""Launch one authorized frozen model task with exact public runtime configuration."""
import hashlib,json,os,pathlib,subprocess,sys
if len(sys.argv) not in (5,6):
    raise SystemExit('usage: SOURCE FULL_SHA FIXTURE_REPO NEW_OUTPUT_ROOT [1.5b|7b]')
source,sha,project,out=sys.argv[1:5]
profile=sys.argv[5] if len(sys.argv)==6 else '1.5b'
assert profile in ('1.5b','7b'),'Only explicitly approved model profiles'
root=pathlib.Path(out)
root.mkdir(parents=True,exist_ok=False)
here=pathlib.Path(__file__).resolve().parent
env_file=pathlib.Path('/workspace/momega1-local-model/'+('qwen7b/' if profile=='7b' else '')+'runtime-env.json')
configured=json.loads(env_file.read_text())
env=os.environ.copy()
env.update(configured)
env['CONTINUUM_CODING_ISOLATION_CONFIG']='/workspace/momega1-build-env/isolation.json'
env['CONTINUUM_MOMEGA1']='1'
env['MOMEGA_QA_MODEL_PROFILE']=profile
command=['python3',str(here/'observe-native-resources.py'),str(root/'native-resources.json'),
         configured['CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH'],'node',str(here/'run-real-model-task.mjs'),
         source,sha,project,str(root/'task')]
(root/'launch.json').write_text(json.dumps({'command':command,'publicRuntimeEnvironmentSha256':hashlib.sha256(env_file.read_bytes()).hexdigest()},indent=2)+'\n')
with (root/'driver.log').open('w') as output:
    result=subprocess.run(command,env=env,stdout=output,stderr=subprocess.STDOUT)
(root/'driver.exit').write_text(str(result.returncode)+'\n')
print(json.dumps({'output':out,'exit':result.returncode}))
raise SystemExit(result.returncode)
