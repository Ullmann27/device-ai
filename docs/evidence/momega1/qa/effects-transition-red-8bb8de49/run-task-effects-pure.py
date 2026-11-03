#!/usr/bin/env python3
"""Execute the actual immutable pure painter graph and authored JUnit tests."""
import hashlib,json,os,pathlib,subprocess,sys

sha,out=sys.argv[1:3]
repo='/workspace/device-ai'
def git(*args):
    return subprocess.check_output(['git','-C',repo,*args])
assert git('rev-parse',sha+'^{commit}').decode().strip()==sha
root=pathlib.Path(out);root.mkdir(parents=True,exist_ok=False)
source=root/'source';source.mkdir()
paths=['app/src/main/java/at/heinz/deviceai/ui/ContinuumPainter.kt',
       'app/src/main/java/at/heinz/deviceai/ui/ContinuumTaskEffects.kt',
       'app/src/test/java/at/heinz/deviceai/ui/ContinuumTaskEffectsTest.kt']
inputs=[]
for path in paths:
    content=git('show',sha+':'+path)
    target=source/pathlib.Path(path).name;target.write_bytes(content)
    inputs.append({'path':path,'gitBlob':git('rev-parse',sha+':'+path).decode().strip(),
                   'sha256':hashlib.sha256(content).hexdigest()})
base=pathlib.Path('/workspace/momega1-build-env')
compiler=base/'kotlinc/bin/kotlinc'
deps=[base/'jvm-deps/junit-4.13.2.jar',base/'jvm-deps/hamcrest-core-1.3.jar',base/'kotlinc/lib/kotlin-stdlib.jar']
cpu=str(sorted(os.sched_getaffinity(0))[min(2,len(os.sched_getaffinity(0))-1)])
env=os.environ.copy();env['JAVA_HOME']=str(base/'jdk17')
classes=root/'classes'
compile_cmd=['taskset','-c',cpu,str(compiler),'-J-XX:ActiveProcessorCount=1','-J-Xmx512m',
             *map(str,sorted(source.glob('*.kt'))),'-cp',os.pathsep.join(map(str,deps)),'-d',str(classes)]
test_cmd=['taskset','-c',cpu,str(base/'jdk17/bin/java'),'-XX:ActiveProcessorCount=1','-Xmx512m',
          '-cp',os.pathsep.join(map(str,[classes,*deps])),'org.junit.runner.JUnitCore',
          'at.heinz.deviceai.ui.ContinuumTaskEffectsTest']
manifest={'sourceCommit':sha,'scope':'actual pure production helper and Painter; no Android stubs',
          'inputs':inputs,'cpuAffinity':cpu,'compileCommand':compile_cmd,'testCommand':test_cmd,
          'dependencies':[{'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in deps]}
(root/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
for name,cmd in [('compile',compile_cmd),('junit',test_cmd)]:
    with (root/(name+'.txt')).open('w') as log:
        result=subprocess.run(cmd,env=env,stdout=log,stderr=subprocess.STDOUT)
    (root/(name+'.exit')).write_text(str(result.returncode)+'\n')
    if result.returncode:
        raise SystemExit(result.returncode)
