from pathlib import Path
import datetime, fcntl, hashlib, json, os, re, subprocess, sys, time

ROOT = Path('/workspace/scratch/174c84456ee3/continuum-c1')
QA = ROOT.parent / 'qa/avatar-idle'
phase = sys.argv[1]
if phase not in ('red','green'): raise SystemExit('Expected red or green')
output = QA / phase
output.mkdir()
source = ROOT / 'app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt'
test = QA / 'AvatarIdleMotionBoundaryTest.kt'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
now = lambda: datetime.datetime.now(datetime.timezone.utc).isoformat()
locks = []
record = {'taskId':'C1-AVATAR-005','phase':phase,'sourceBase':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
          'productionPath':str(source.relative_to(ROOT)),'productionSha256':sha(source),'testSha256':sha(test),
          'requestedAt':now(),'scope':'Actual internal Kotlin production class; JUnit boundary cases; no Android/GPU/device/model execution.',
          'commands':[],'status':'BLOCKED','exitCode':75}
deadline = time.monotonic()+45
try:
    for name in ['/workspace/scratch/b6ee64d418cf/toolchain/gradle-build.lock','/workspace/scratch/d663e8444310/qa/gradle.lock']:
        handle=open(name,'a');locks.append(handle)
        while True:
            try: fcntl.flock(handle,fcntl.LOCK_EX|fcntl.LOCK_NB);break
            except BlockingIOError:
                if time.monotonic()>=deadline: raise SystemExit(75)
                time.sleep(.25)
    compiler='/workspace/scratch/18845e31bc4a/regression-audit/real-kotlinc/bin/kotlinc'
    deps=Path('/workspace/scratch/18845e31bc4a/host-qa/compiler-deps')
    jar=output/'classes.jar'
    compile=[compiler,str(source),str(test),'-jvm-target','17','-classpath',str(deps/'junit-4.13.2.jar'),'-d',str(jar)]
    junit=['/workspace/scratch/b6ee64d418cf/toolchain/jdk17/bin/java','-Xmx256m','-cp',os.pathsep.join(map(str,[jar,deps/'junit-4.13.2.jar',deps/'hamcrest-core-1.3.jar',deps/'kotlin-stdlib-2.4.0.jar'])),'org.junit.runner.JUnitCore','at.heinz.deviceai.avatar3d.AvatarIdleMotionBoundaryTest']
    record['startedAt']=now()
    for label,command,timeout in [('compile',compile,90),('junit',junit,30)]:
        with (output/(label+'.txt')).open('x') as log:
            try: code=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=timeout).returncode
            except subprocess.TimeoutExpired: code=124
        record['commands'].append({'stage':label,'command':command,'exitCode':code})
        record['exitCode']=code
        if code:break
    record['productionUnchanged']=sha(source)==record['productionSha256']
    record['testUnchanged']=sha(test)==record['testSha256']
    record['status']='PASS' if record['exitCode']==0 and record['productionUnchanged'] and record['testUnchanged'] else 'FAIL'
    record['finishedAt']=now()
finally:
    for handle in reversed(locks):fcntl.flock(handle,fcntl.LOCK_UN);handle.close()
    (output/'result.json').write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps(record))
    if (output/'junit.txt').exists():print((output/'junit.txt').read_text())
raise SystemExit(record['exitCode'])
