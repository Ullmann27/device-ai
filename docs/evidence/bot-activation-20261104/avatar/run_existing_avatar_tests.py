from pathlib import Path
import subprocess, json, hashlib, datetime, re, sys

repo = Path('/workspace/scratch/8f5df1fd155a/continuum-bot-activation')
out = Path('/workspace/scratch/8f5df1fd155a/probe-evidence/avatar')
lib = Path('/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib')
jsonjar = Path('/workspace/scratch/b7d23de0e715/build-toolchain/jvm-deps/json-20240303.jar')
source_commit = 'e101e393dd6c80803ac9d9ac91fccd9812c4a18d'
main = repo / 'app/src/main/java/at/heinz/deviceai'
test = repo / 'app/src/test/java/at/heinz/deviceai'
sources = [main/'avatar/AvatarContract.kt', main/'avatar/AvatarDirector.kt',
    main/'avatar3d/AvatarGlbContract.kt', main/'avatar3d/AvatarMorphMapper.kt',
    main/'cognitive/AvatarCameraPolicy.kt'] + sorted((main/'motion').glob('*.kt'))
tests = [test/'avatar/AvatarDirectorTest.kt', test/'avatar/AvatarSpeechOrderingRegressionTest.kt',
    test/'avatar/AvatarObservedGazeTest.kt', test/'cognitive/AvatarCameraPolicyTest.kt',
    test/'motion/LipSyncFaceSourceTest.kt', test/'motion/MotionLibraryTest.kt']
classes = [
    'at.heinz.deviceai.avatar.AvatarDirectorTest',
    'at.heinz.deviceai.avatar.AvatarPortedPartsTest',
    'at.heinz.deviceai.avatar.AvatarSpeechOrderingRegressionTest',
    'at.heinz.deviceai.avatar.AvatarObservedGazeTest',
    'at.heinz.deviceai.cognitive.AvatarCameraPolicyTest',
    'at.heinz.deviceai.motion.LipSyncFaceSourceTest',
    'at.heinz.deviceai.motion.MotionLibraryTest']

def git(*args):
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()

def execute(name, args):
    result = subprocess.run(args, cwd=repo, capture_output=True, text=True, timeout=90)
    (out/(name+'.log')).write_text(result.stdout + result.stderr)
    (out/(name+'.command.json')).write_text(json.dumps(args, indent=2)+'\n')
    return result

paths = [str(p.relative_to(repo)) for p in sources+tests]
changed = git('diff', '--name-only', source_commit, 'HEAD', '--', *paths)
if changed:
    raise RuntimeError('Selected sources differ from declared source commit: '+changed)
result = {'schema':'continuum-avatar-host-existing-tests', 'version':1,
    'recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'sourceCommit':source_commit, 'checkoutHead':git('rev-parse','HEAD'),
    'selectedSourcesMatchSourceCommit':True, 'execution':'DIRECT_KOTLIN_COMPILER_AND_JUNIT4',
    'networkUsed':False, 'modelCalls':0, 'deviceRun':False, 'productionFilesModified':False,
    'javaVersion':subprocess.run(['java','-version'],capture_output=True,text=True).stderr.strip(),
    'limitations':['Host JVM only; no Android UI or hardware rendering',
        'No live bot model, owner service, grant, queue, or repair execution was started',
        'Existing tests executed directly; no synthetic production classes or assertion stubs'],
    'sourceSha256':{str(p.relative_to(repo)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources+tests},
    'testClasses':[]}
classpath = ':'.join(str(p) for p in [lib/'kotlin-stdlib-2.0.20.jar', lib/'kotlin-reflect-2.0.20.jar',
    lib/'junit-4.13.2.jar', lib/'hamcrest-core-1.3.jar', lib/'annotations-24.0.1.jar', jsonjar])
classesdir=out/'classes'
classesdir.mkdir(exist_ok=True)
compilecmd=['java','-cp',str(lib/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
    '-no-stdlib','-no-reflect','-jvm-target','17','-classpath',classpath,
    '-d',str(classesdir)]+[str(p) for p in sources+tests]
compiled=execute('compile', compilecmd)
result['compileExitCode']=compiled.returncode
if compiled.returncode==0:
    for classname in classes:
        name=classname.rsplit('.',1)[1]
        run=execute(name,['java','-cp',str(classesdir)+':'+classpath,'org.junit.runner.JUnitCore',classname])
        match=re.search(r'OK \((\d+) tests?\)', run.stdout)
        result['testClasses'].append({'class':classname,'exitCode':run.returncode,
            'passedTests':int(match.group(1)) if match else None,'log':name+'.log'})
        print(name+': '+run.stdout.strip(), flush=True)
else:
    print(compiled.stdout+compiled.stderr, flush=True)
result['passedTests']=sum(x['passedTests'] or 0 for x in result['testClasses'])
result['status']='PASS' if compiled.returncode==0 and len(result['testClasses'])==len(classes) and all(x['exitCode']==0 and x['passedTests'] is not None for x in result['testClasses']) else 'FAIL'
result['finishedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat()
(out/'result.json').write_text(json.dumps(result, indent=2)+'\n')
print(json.dumps({'status':result['status'],'passedTests':result['passedTests'],'sourceCommit':source_commit,'checkoutHead':result['checkoutHead'],'resultPath':str(out/'result.json')},indent=2))
sys.exit(0 if result['status']=='PASS' else 1)
