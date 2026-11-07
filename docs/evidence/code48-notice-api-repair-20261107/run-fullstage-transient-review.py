import hashlib,json,os,subprocess,sys,time
from pathlib import Path
root=Path('/workspace/scratch/3d1967be6193/candidate')
feature=root
base=Path('/workspace/scratch/3d1967be6193/device-ai')
legacy=Path('/workspace/scratch/7df35dbe256f/qa/focused-ui')
build=Path('/workspace/scratch/b31a9d731016/build-env')
out=Path('/workspace/scratch/3d1967be6193/qa/fullstage-transient-review')/sys.argv[1]
out.mkdir(parents=True,exist_ok=False)
prod=base/'app/build/tmp/kotlin-classes/debug'
overlay=out/'production';overlay.mkdir(exist_ok=True)
classes=out/'classes';classes.mkdir(exist_ok=True)
paths=['ContinuumHologramView.kt','ui/ContinuumHomeView.kt','ui/JarvisSequenceView.kt','ui/video3/Video3WarpScene.kt','ui/video3/Video3FileTunnel.kt','ui/video3/Video3MemoryScene.kt']
sources=[feature/'app/src/main/java/at/heinz/deviceai'/p for p in paths]
tests=[root/'app/src/uiTest/java/at/heinz/deviceai'/p for p in ['FullStageMemoryReviewUiTest.kt','CockpitHostFramePump.kt']]
cp=(legacy/'classpath.txt').read_text().strip()
record={'startedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'baselineCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=base,text=True).strip(),'testSourceBase':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'productionSourceSha256':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},'testSourceSha256':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in tests},'commands':[],'physicalDeviceTested':False,'filamentGpuPixelsTested':False,'evidenceClass':'REAL_ACTIVITY_NATIVE_ANDROID_CANVAS_SYNTHETIC_MEMORY_EVENTS'}
def run(name,cmd,cwd):
 log=out/(name+'.log')
 with log.open('w') as f:r=subprocess.run(cmd,cwd=cwd,stdout=f,stderr=subprocess.STDOUT)
 record['commands'].append({'name':name,'command':cmd,'cwd':str(cwd),'exitCode':r.returncode,'logSha256':hashlib.sha256(log.read_bytes()).hexdigest()})
 (out/'receipt.json').write_text(json.dumps(record,indent=2)+'\n')
 print(name,r.returncode,flush=True)
 return r.returncode
compiler=build/'kotlinc/bin/kotlinc'
compilecp=f'{overlay}:{prod}:{build}/android-sdk/platforms/android-36/android.jar:{cp}'
shared=[str(compiler),'-J-Xmx512m','-jvm-target','17','-module-name','DeviceAI_app_debug',f'-Xfriend-paths={prod},{overlay}','-cp',compilecp]
if run('production-compile',shared+['-d',str(overlay),*[str(p) for p in sources]],root):sys.exit(1)
if run('test-compile',shared+['-d',str(classes),*[str(p) for p in tests]],root):sys.exit(1)
methods=sys.argv[2:] or [f'at.heinz.deviceai.FullStageMemoryReviewUiTest#{m}' for m in ['actualActivityMemoryBusEnlargesAndReturnsTheOriginalOrb','falseSignalsAndAnEventFloodCannotInventOrExtendTheEpisode','realStopRejectsQueuedMemoryAndChatReparentingCannotReplay','actualWindowCloseAndSystemTapRemainUsableAboveTheScene','theEntireActualControlRectangleStaysOutsideTheCompositor']]
java=[str(build/'jdk17-pinned/bin/java'),'-Xmx768m','-Drobolectric.offline=true',f'-Drobolectric.dependency.dir={base}/app/build/ui-test-sdk']
if os.environ.get('FULLSTAGE_REVIEW_CAPTURE')=='1':java.append(f'-Dcontinuum.ui.evidenceDir={out}/frames')
java+=['-cp',f'{legacy}/runner:{classes}:{overlay}:{prod}:{cp}','UiMethods',*methods]
code=run('junit',java,base/'app')
record['productionSourceUnchanged']=record['productionSourceSha256']=={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
record['testSourceUnchanged']=record['testSourceSha256']=={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in tests}
record['finishedAt']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())
(out/'receipt.json').write_text(json.dumps(record,indent=2)+'\n')
sys.exit(code)
