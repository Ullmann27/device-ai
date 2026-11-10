from pathlib import Path
import subprocess,json,hashlib,os
repo=Path(os.environ['CONTINUUM_SOURCE_ROOT']).resolve()
out=Path(os.environ['CONTINUUM_CHECK_OUT']).resolve()
if out == repo or repo in out.parents: raise SystemExit('Output must be outside repository')
out.mkdir(parents=True,exist_ok=True)
base=Path(os.environ['CONTINUUM_QA_CACHE']).resolve()
gradle=base/'qa-gradle-home/caches'
installed=base/'qa-installed-tools'
modules=[p for p in (gradle/'modules-2/files-2.1').rglob('*.jar') if not any(x in str(p) for x in ['/android-all-instrumented/','/kotlin-compiler-embeddable/','/kotlin-stdlib/1.','/kotlin-stdlib/2.0','/kotlin-stdlib/2.1','/kotlin-stdlib/2.2'])]
transforms=list((gradle/'8.11.1/transforms').glob('*/transformed/*-runtime.jar'))
app=repo/'app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar'
android=installed/'android-sdk/platforms/android-36/android.jar'
cp=':'.join(map(str,[app,android,installed/'kotlinc/lib/kotlin-stdlib.jar',*modules,*transforms]))
sources=[repo/'app/src/main/java/at/heinz/deviceai/systemintelligence/SystemDiagnosis.kt',repo/'app/src/main/java/at/heinz/deviceai/ui/SystemDiagnosisHologramView.kt',Path(__file__).with_name('SystemSurfaceCaptureTest.kt')]
command=[str(installed/'kotlinc/bin/kotlinc'),'-J-Xmx256m','-jvm-target','17','-cp',cp,'-d',str(out/'capture.jar'),*map(str,sources)]
env=os.environ.copy();env['JAVA_HOME']=str(installed/'jdk');env['PATH']=str(installed/'jdk/bin')+':'+env['PATH']
with (out/'compile.txt').open('w') as log: code=subprocess.call(command,env=env,stdout=log,stderr=subprocess.STDOUT)
print('compile',code,flush=True)
if code: raise SystemExit(code)
deps=out/'robo-deps';deps.mkdir(exist_ok=True)
for p in (gradle/'modules-2/files-2.1/org.robolectric/android-all-instrumented').rglob('*.jar'):
 dest=deps/p.name
 if not dest.exists(): dest.symlink_to(p)
command=[str(installed/'jdk/bin/java'),'-Xmx512m','-Drobolectric.offline=true','-Drobolectric.dependency.dir='+str(deps),'-Dcontinuum.surface.output='+str(out),'-cp',str(out/'capture.jar')+':'+cp,'org.junit.runner.JUnitCore','at.heinz.deviceai.SystemSurfaceCaptureTest']
with (out/'junit.txt').open('w') as log: code=subprocess.call(command,cwd=repo,env=env,stdout=log,stderr=subprocess.STDOUT)
print('junit',code,flush=True);print((out/'junit.txt').read_text()[-4000:])
(out/'receipt.json').write_text(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'junit_exit':code,'sources':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},'dependency_appjar_sha256':hashlib.sha256(app.read_bytes()).hexdigest(),'images':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.glob('*.png')},'renderer':'Actual SystemDiagnosisHologramView source compiled directly; Robolectric API28 native Canvas; cached unrelated app classes','scope':'isolated data surface, no Activity or Cinema integration','inputs':'explicit synthetic fixture; no device data','Fold7':'NOT_RUN','GPU':'NOT_RUN','Filament':'NOT_RUN'},indent=2))
raise SystemExit(code)
