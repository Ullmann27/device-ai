"""One frozen case; no answers, patches or provider overrides. Serial CPU only."""
import json,os,pathlib,subprocess,sys,hashlib
assert len(sys.argv)==2 and sys.argv[1] in ['g01','g02','g03']
case=sys.argv[1]; here=pathlib.Path(__file__).resolve().parent
base=pathlib.Path('/workspace/genesis-a0-experiment'); run=base/'runs'/case
run.mkdir(parents=True,exist_ok=False,mode=0o700)
confpath=pathlib.Path('/workspace/synapse-local-model/runtime-env.json'); conf=json.loads(confpath.read_text())
env=os.environ.copy();env.update(conf)
env.update(CONTINUUM_CODING_ISOLATION_CONFIG=str(base/'isolation.json'),CONTINUUM_MOMEGA1='1',MOMEGA_QA_TECHNICAL_RECALL='off')
assert not env.get('MOMEGA_QA_INHERITED_STATE')
fixture=base/('fixtures-v2' if case=='g03' else 'fixtures')/case
cmd=['python3',str(here/'observe-native-resources.py'),str(run/'native-resources.json'),conf['CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH'],'node',str(here/'run-real-model-task.mjs'),'/workspace/genesis-a0-source','fd743adce297c0958aadce1576b80580817837f0',str(fixture),str(run/'evidence')]
(run/'launch.json').write_text(json.dumps({'case':case,'command':cmd,'launcherSha256':hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),'runtimeEnvironmentSha256':hashlib.sha256(confpath.read_bytes()).hexdigest(),'fixture':str(fixture),'maxReservedCalls':2,'externalAnswer':False},indent=2)+'\n')
with (run/'driver.log').open('w') as log: p=subprocess.run(cmd,env=env,stdout=log,stderr=subprocess.STDOUT)
(run/'driver.exit').write_text(str(p.returncode)+'\n')
print(json.dumps({'case':case,'exit':p.returncode,'evidence':str(run)}))
raise SystemExit(p.returncode)
