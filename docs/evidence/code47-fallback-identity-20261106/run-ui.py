import datetime, hashlib, json, os, pathlib, subprocess, sys
base=pathlib.Path('/workspace/scratch/b31a9d731016')
root=base/'wiring-fallback-identity'
ev=base/'fallback-identity-evidence'
envroot=base/'build-env'
sdk=base/'app-query-format-evidence/sdk-baseline'
phase=sys.argv[1]
cp=(base/'context-contract-evidence/ui-runtime-classpath.txt').read_text().strip()
classes=ev/'test-classes'
classes.mkdir(exist_ok=True)
source=root/'app/src/uiTest/java/at/heinz/deviceai/OwnerFallbackIdentityUiTest.kt'
env=dict(os.environ,JAVA_HOME=str(envroot/'jdk17-pinned'))
def run(stage, command):
    r=subprocess.run(command,cwd=root/'app',env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    (ev/(phase+'-'+stage+'.txt')).write_text(r.stdout)
    (ev/(phase+'-'+stage+'-command.json')).write_text(json.dumps({'command':command,'cwd':str(root/'app'),'exit':r.returncode,'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'baseline':'c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e','test_sha256':hashlib.sha256(source.read_bytes()).hexdigest()},indent=2))
    print(stage,'exit',r.returncode);print(r.stdout)
    return r.returncode
if phase=='red':
    compilecp=os.pathsep.join([str(sdk),cp,str(envroot/'android-sdk/platforms/android-36/android.jar')])
    command=[str(envroot/'kotlinc/bin/kotlinc'),'-J-Xmx256m','-jvm-target','17','-Xfriend-paths='+str(sdk),'-cp',compilecp,str(source),'-d',str(classes)]
    if run('compile',command):sys.exit(2)
extra=[sys.argv[2]] if len(sys.argv)>2 else []
runtimecp=os.pathsep.join([str(classes),*extra,str(sdk),str(ev/'ui-config'),cp])
command=[str(envroot/'jdk17-pinned/bin/java'),'-Xmx1024m','-Djava.io.tmpdir='+str(ev/'ui-tmp'),'-Drobolectric.offline=true','-Drobolectric.dependency.dir='+str(base/'device-ai-wiring/app/build/ui-test-sdk'),'-cp',runtimecp,'org.junit.runner.JUnitCore','at.heinz.deviceai.OwnerFallbackIdentityUiTest']
sys.exit(run('junit',command))
