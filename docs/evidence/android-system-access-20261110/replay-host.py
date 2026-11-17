from pathlib import Path
import os,subprocess,json,hashlib
repo=Path(os.environ['CONTINUUM_SOURCE_ROOT']).resolve()
out=Path(os.environ['CONTINUUM_CHECK_OUT']).resolve()
if out == repo or repo in out.parents: raise SystemExit('Output must be outside repository')
out.mkdir(parents=True,exist_ok=True)
base=Path(os.environ['CONTINUUM_QA_CACHE']).resolve()
gradle=base/'qa-gradle-home/caches'
tools=base/'qa-installed-tools'
modules=list((gradle/'modules-2/files-2.1').rglob('*.jar'))
modules=[p for p in modules if not any(x in str(p) for x in ['/android-all-instrumented/','/kotlin-compiler-embeddable/','/kotlin-stdlib/1.','/kotlin-stdlib/2.0','/kotlin-stdlib/2.1','/kotlin-stdlib/2.2'])]
transforms=list((gradle/'8.11.1/transforms').glob('*/transformed/*-runtime.jar'))
app=base/'device-ai/app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar'
android=tools/'android-sdk/platforms/android-36/android.jar'
cp=':'.join(map(str,[app,android,tools/'kotlinc/lib/kotlin-stdlib.jar',*modules,*transforms]))
sources=[repo/f'app/src/main/java/at/heinz/deviceai/{x}.kt' for x in ['ShizukuCapabilities','ShizukuBridge','CheckedShellCallDispatcher','ShellExecution']]
sources.append(repo/'app/src/uiTest/java/at/heinz/deviceai/ShizukuBridgeLifecycleTest.kt')
cmd=[str(tools/'kotlinc/bin/kotlinc'),'-J-Xmx256m','-jvm-target','17','-cp',cp,'-Xfriend-paths='+str(app),'-d',str(out/'lifecycle.jar'),*map(str,sources)]
with (out/'compile.txt').open('w') as f:r=subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT)
print('compile',r.returncode,flush=True)
if r.returncode:
 print((out/'compile.txt').read_text());raise SystemExit(r.returncode)
deps=out/'robo-deps';deps.mkdir(exist_ok=True)
for p in (gradle/'modules-2/files-2.1/org.robolectric/android-all-instrumented').rglob('*.jar'):
 dest=deps/p.name
 if not dest.exists():dest.symlink_to(p)
run=['java','-Xmx384m','-Drobolectric.offline=true','-Drobolectric.dependency.dir='+str(deps),'-cp',str(out/'lifecycle.jar')+':'+cp,'org.junit.runner.JUnitCore','at.heinz.deviceai.ShizukuBridgeLifecycleTest']
with (out/'lifecycle.txt').open('w') as f:r=subprocess.run(run,cwd=repo,stdout=f,stderr=subprocess.STDOUT)
print('junit',r.returncode,flush=True)
print((out/'lifecycle.txt').read_text()[-14000:])
(out/'manifest.json').write_text(json.dumps({'compile_exit':0,'junit_exit':r.returncode,'sources':{str(p.relative_to(repo)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},'dependency_appjar_sha256':hashlib.sha256(app.read_bytes()).hexdigest(),'dependencies':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [android,tools/'kotlinc/lib/kotlin-stdlib.jar']},'runtime':'Direct Kotlin compilation of actual bridge; Robolectric Android API28; cached app classes supply unchanged unrelated types; Shizuku server shadowed. No Gradle or device.'},indent=2))
