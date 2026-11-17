import hashlib,json,os,shutil,subprocess,sys,time
from pathlib import Path
base=Path('/workspace/scratch/e210d3a663f2/qa/code48')
legacy=Path('/workspace/scratch/7df35dbe256f/qa/focused-ui')
root=Path('/workspace/scratch/7df35dbe256f/device-ai-code47-fixes')
frozen=Path('/workspace/scratch/7df35dbe256f/device-ai-code47')
label=sys.argv[1]
out=base/label
out.mkdir(exist_ok=True)
classes=out/'classes'; classes.mkdir(exist_ok=True)
prod=frozen/'app/build/tmp/kotlin-classes/debug'
cp=(legacy/'classpath.txt').read_text().strip()
sources=[root/'app/src/uiTest/java/at/heinz/deviceai'/n for n in ['ContinuumDocumentSceneUiTest.kt','CockpitHostFramePump.kt']]
snapshot={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
record={'productionCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=frozen,text=True).strip(),'testSourceBase':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'testSourceSha256':snapshot,'startedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'evidenceClass':'HOST_REAL_ANDROID_ROBOLECTRIC_CONTROLLED_MODEL_DEVICE_PORT','physicalDeviceTested':False,'commands':[]}
def run(name,cmd,cwd):
 with (out/(name+'.log')).open('w') as f:
  p=subprocess.run(cmd,cwd=cwd,stdout=f,stderr=subprocess.STDOUT)
 record['commands'].append({'name':name,'command':cmd,'cwd':str(cwd),'exit':p.returncode,'logSha256':hashlib.sha256((out/(name+'.log')).read_bytes()).hexdigest()})
 (out/'receipt.json').write_text(json.dumps(record,indent=2)+'\n')
 print(name, p.returncode, flush=True)
 return p.returncode
cmd=[shutil.which('kotlinc'),'-J-Xmx256m','-jvm-target','17','-module-name','DeviceAI_app_debug',f'-Xfriend-paths={prod}','-cp',f'{prod}:{os.environ["ANDROID_HOME"]}/platforms/android-36/android.jar:{cp}','-d',str(classes),*[str(p) for p in sources]]
if run('compile',cmd,root): sys.exit(1)
tmp=out/'tmp';tmp.mkdir(exist_ok=True)
methods=sys.argv[2:] or ['at.heinz.deviceai.ContinuumDocumentFlowUiTest#stoppedScreenReadRemainsEphemeral','at.heinz.deviceai.ContinuumDocumentFlowUiTest#politePdfInputOpensTheExistingPickerBeforeAnyModelCall','at.heinz.deviceai.ContinuumTaskEffectsCallerUiTest#authorizedRealRunnerEntryAndExitReachTheVisibleSurface']
cmd=[shutil.which('java'),'-Xmx768m',f'-Djava.io.tmpdir={tmp}','-Drobolectric.offline=true',f'-Drobolectric.dependency.dir={frozen}/app/build/ui-test-sdk','-cp',f'{legacy}/runner:{classes}:{cp}','UiMethods',*methods]
code=run('junit',cmd,frozen/'app')
record['finishedAt']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())
record['sourceUnchanged']=snapshot=={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
(out/'receipt.json').write_text(json.dumps(record,indent=2)+'\n')
sys.exit(code)
