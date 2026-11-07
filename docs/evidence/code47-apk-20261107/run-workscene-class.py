import datetime, hashlib, json, os, subprocess
from pathlib import Path
root=Path('/workspace/scratch/7df35dbe256f/device-ai-code47-fixes')
focused=Path('/workspace/scratch/7df35dbe256f/qa/focused-ui/resume-20261107-callbacks')
out=Path('/workspace/scratch/e210d3a663f2/qa/workscene-class')
out.mkdir(exist_ok=True)
prior=json.loads((focused/'receipt.json').read_text())
cmd=next(x['command'] for x in prior['commands'] if x['name']=='junit')
i=cmd.index('UiMethods')
cmd=cmd[:i]+['org.junit.runner.JUnitCore','at.heinz.deviceai.OwnerAppActionWorkSceneUiTest']
started=datetime.datetime.now(datetime.timezone.utc).isoformat()
log=out/'junit.log'
with log.open('x') as f:
 result=subprocess.run(cmd,cwd='/workspace/scratch/7df35dbe256f/device-ai-code47/app',stdout=f,stderr=subprocess.STDOUT)
r={'startedAt':started,'finishedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'exitCode':result.returncode,'command':cmd,'sourceSha256':prior['testSourceSha256'],'productionCommit':prior['productionCommit'],'sourceBase':'7b9c03804301e00ab9801708eef8a2b50ed3e5f2','logSha256':hashlib.sha256(log.read_bytes()).hexdigest(),'physicalDeviceTested':False}
(out/'receipt.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps({'exitCode':result.returncode,'log':str(log)}),flush=True)
raise SystemExit(result.returncode)
