"""One frozen case; no answers, patches or provider overrides. Serial CPU only."""
import json,os,pathlib,subprocess,sys,hashlib
assert len(sys.argv)==2 and sys.argv[1] in ['u03']
case=sys.argv[1]; here=pathlib.Path(__file__).resolve().parent
base=pathlib.Path('/workspace/genesis-known-u03-experiment'); run=base/'runs'/case
run.mkdir(parents=True,exist_ok=False,mode=0o700)
confpath=pathlib.Path('/workspace/synapse-local-model/runtime-env.json'); conf=json.loads(confpath.read_text())
env=os.environ.copy();env.update(conf)
env.update(CONTINUUM_CODING_ISOLATION_CONFIG=str(base/'isolation.json'),CONTINUUM_MOMEGA1='1',MOMEGA_QA_TECHNICAL_RECALL='off')
assert not env.get('MOMEGA_QA_INHERITED_STATE')
env['GENESIS_PROFILE_STORE']=str(base/'profile-store')
env['GENESIS_HANDOFF_CLASSPATH']=(pathlib.Path('/workspace/genesis-g04-experiment/kotlin-host/classpath.txt')).read_text()
assert (pathlib.Path('/workspace/genesis-g04-experiment/kotlin-host/source-sha.txt')).read_text().strip()=='1595389274acb840c4ab544fa699c9eb2a04bd53'
fixture=base/'fixtures'/case
cmd=['python3',str(here/'observe-native-resources.py'),str(run/'native-resources.json'),conf['CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH'],'node',str(here/'run-known-u03-task.mjs'),'/workspace/genesis-g04-source','1595389274acb840c4ab544fa699c9eb2a04bd53',str(fixture),str(run/'evidence')]
(run/'launch.json').write_text(json.dumps({'case':case,'command':cmd,'launcherSha256':hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),'runtimeEnvironmentSha256':hashlib.sha256(confpath.read_bytes()).hexdigest(),'fixture':str(fixture),'maxReservedCalls':1,'externalAnswer':False},indent=2)+'\n')
with (run/'driver.log').open('w') as log: p=subprocess.run(cmd,env=env,stdout=log,stderr=subprocess.STDOUT)
(run/'driver.exit').write_text(str(p.returncode)+'\n')
print(json.dumps({'case':case,'exit':p.returncode,'evidence':str(run)}))
raise SystemExit(p.returncode)
