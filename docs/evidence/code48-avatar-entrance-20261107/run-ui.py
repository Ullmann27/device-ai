import hashlib,json,os,shutil,subprocess,sys,time
from pathlib import Path
legacy=Path('/workspace/scratch/7df35dbe256f/qa/focused-ui')
base=Path('/workspace/scratch/3d1967be6193/qa/entrance')
base.mkdir(parents=True,exist_ok=True)
root=Path('/workspace/scratch/3d1967be6193/entrance')
frozen=Path('/workspace/scratch/7df35dbe256f/device-ai-code47')
label=sys.argv[1]
out=base/label
out.mkdir(exist_ok=True)
classes=out/'classes'; classes.mkdir(exist_ok=True)
prod=frozen/'app/build/tmp/kotlin-classes/debug'
cp=(legacy/'classpath.txt').read_text().strip()
sources=[root/'app/src/uiTest/java/at/heinz/deviceai'/n for n in ['ui/AvatarEntranceUiTest.kt']]
snapshot={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
record={'productionCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=frozen,text=True).strip(),'testSourceBase':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'testSourceSha256':snapshot,'startedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'evidenceClass':'HOST_REAL_ANDROID_ROBOLECTRIC_CONTROLLED_MODEL_DEVICE_PORT','physicalDeviceTested':False,'commands':[]}
def run(name,cmd,cwd):
 with (out/(name+'.log')).open('w') as f:
  p=subprocess.run(cmd,cwd=cwd,stdout=f,stderr=subprocess.STDOUT)
 record['commands'].append({'name':name,'command':cmd,'cwd':str(cwd),'exit':p.returncode,'logSha256':hashlib.sha256((out/(name+'.log')).read_bytes()).hexdigest()})
 (out/'receipt.json').write_text(json.dumps(record,indent=2)+'\n')
 print(name, p.returncode, flush=True)
 return p.returncode
overlay=out/'production'; overlay.mkdir(exist_ok=True)
changed=[p for p in subprocess.check_output(['git','diff','--name-only','9b53b059f1411a1f0b5173de2f32a75b1f1a7bec'],cwd=root,text=True).splitlines() if p.startswith('app/src/main/') and p.endswith('.kt')]
changed = sorted(set(changed + ['app/src/main/java/at/heinz/deviceai/avatar/AvatarIconBurst.kt','app/src/main/java/at/heinz/deviceai/avatar3d/AvatarEntranceMotion.kt']))
changed = [p for p in changed if not p.endswith(('/AgentActivity.kt', '/JarvisWorkWindowHost.kt'))]
external={'app/src/main/java/at/heinz/deviceai/ui/CockpitAnimationPresenter.kt':Path('/workspace/scratch/3d1967be6193/device-ai/app/src/main/java/at/heinz/deviceai/ui/CockpitAnimationPresenter.kt')}
productionSources=[external.get(p,root/p) for p in changed]
record['externalProductionSha256']={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in external.values()}
record['productionOverlayBase']=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
record['productionSourceSha256']={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in changed}
if label != 'red':
 pcmd=[shutil.which('kotlinc'),'-J-Xmx512m','-jvm-target','17','-module-name','DeviceAI_app_debug',f'-Xfriend-paths={prod},{overlay}','-cp',f'{overlay}:{prod}:{os.environ["ANDROID_HOME"]}/platforms/android-36/android.jar:{cp}','-d',str(overlay),*[str(p) for p in productionSources]]
 if run('production-compile',pcmd,root): sys.exit(1)
 cp=f'{overlay}:{cp}'
cmd=[shutil.which('kotlinc'),'-J-Xmx256m','-jvm-target','17','-module-name','DeviceAI_app_debug',f'-Xfriend-paths={prod},{overlay}','-cp',f'{overlay}:{prod}:{os.environ["ANDROID_HOME"]}/platforms/android-36/android.jar:{cp}','-d',str(classes),*[str(p) for p in sources]]
if run('compile',cmd,root): sys.exit(1)
tmp=out/'tmp';tmp.mkdir(exist_ok=True)
methods=sys.argv[2:] or ['at.heinz.deviceai.ContinuumDocumentFlowUiTest#stoppedScreenReadRemainsEphemeral','at.heinz.deviceai.ContinuumDocumentFlowUiTest#politePdfInputOpensTheExistingPickerBeforeAnyModelCall','at.heinz.deviceai.ContinuumTaskEffectsCallerUiTest#authorizedRealRunnerEntryAndExitReachTheVisibleSurface']
cmd=[shutil.which('java'),f'-Dcontinuum.ui.evidenceDir={out}/frames','-Xmx768m',f'-Djava.io.tmpdir={tmp}','-Drobolectric.offline=true',f'-Drobolectric.dependency.dir={frozen}/app/build/ui-test-sdk','-cp',f'{legacy}/runner:{classes}:{cp}','UiMethods',*methods]
code=run('junit',cmd,frozen/'app')
record['finishedAt']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())
record['sourceUnchanged']=snapshot=={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
(out/'receipt.json').write_text(json.dumps(record,indent=2)+'\n')
sys.exit(code)
