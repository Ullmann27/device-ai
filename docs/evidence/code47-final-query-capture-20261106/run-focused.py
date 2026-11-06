import datetime, hashlib, json, os, pathlib, subprocess, sys
root=pathlib.Path('/workspace/scratch/b31a9d731016/wiring-app-query-format')
evidence=pathlib.Path('/workspace/scratch/b31a9d731016/app-query-format-evidence')
envroot=pathlib.Path('/workspace/scratch/b31a9d731016/build-env')
phase=sys.argv[1]
base=evidence/'sdk-baseline'
libs=sorted((envroot/'test-libs').glob('*.jar'))
classpath=os.pathsep.join(map(str,[base,*libs,envroot/'android-sdk/platforms/android-36/android.jar']))
paths=[
'app/src/main/java/at/heinz/deviceai/ToolRegistry.kt',
'app/src/main/java/at/heinz/deviceai/cognitive/NativeAppLookup.kt',
'app/src/test/java/at/heinz/deviceai/AppLookupToolPolicyTest.kt',
'app/src/test/java/at/heinz/deviceai/AppLookupReadableQueryTest.kt',
'app/src/test/java/at/heinz/deviceai/cognitive/NativeAppLookupTest.kt',
'app/src/test/java/at/heinz/deviceai/cognitive/OwnerAppActionFlowTest.kt']
classes=['at.heinz.deviceai.AppLookupToolPolicyTest',
'at.heinz.deviceai.AppLookupReadableQueryTest',
'at.heinz.deviceai.cognitive.NativeAppLookupTest',
'at.heinz.deviceai.cognitive.OwnerAppActionFlowTest']
jar=evidence/(phase+'-tests.jar')
env=dict(os.environ,JAVA_HOME=str(envroot/'jdk17-pinned'))
compilecmd=[str(envroot/'kotlinc/bin/kotlinc'),'-J-Xmx256m','-jvm-target','17',
'-Xfriend-paths='+str(base),'-classpath',classpath,'-d',str(jar),*[str(root/p) for p in paths]]
def run(label,cmd):
    result=subprocess.run(cmd,cwd=root,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (evidence/(phase+'-'+label+'.txt')).write_text(result.stdout)
    (evidence/(phase+'-'+label+'-command.json')).write_text(json.dumps({'command':cmd,'exit':result.returncode,'source_base':'c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e','utc':datetime.datetime.now(datetime.timezone.utc).isoformat()},indent=2))
    print(label, 'exit',result.returncode);print(result.stdout)
    return result.returncode
(evidence/(phase+'-source-sha256.json')).write_text(json.dumps({p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in paths},indent=2))
if run('compile',compilecmd):sys.exit(2)
runtime=os.pathsep.join(map(str,[jar,base,*libs,envroot/'kotlinc/lib/kotlin-stdlib.jar',envroot/'kotlinc/lib/kotlin-stdlib-jdk7.jar',envroot/'kotlinc/lib/kotlin-stdlib-jdk8.jar']))
sys.exit(run('junit',[str(envroot/'jdk17-pinned/bin/java'),'-Xmx256m','-cp',runtime,'org.junit.runner.JUnitCore',*classes]))
