import os, sys, subprocess, pathlib, json, time, urllib.parse, fcntl, signal, shutil, fnmatch
import xml.etree.ElementTree as ET
repo = pathlib.Path('/workspace/scratch/5900afa6af29/device-ai')
base = repo.parent / 'apk53-a980-android'
base.mkdir(exist_ok=False)
expected = 'a980a19ed3ab44323009ae27eb299d322ff11679'
def git(*args):
    return subprocess.check_output(['git', *args], cwd=repo, text=True).strip()
def source():
    if git('rev-parse', 'HEAD') != expected or git('status', '--porcelain', '--untracked-files=normal'):
        raise RuntimeError('Exact clean source changed')
source()
env = os.environ.copy()
env.update(JAVA_HOME='/tmp/continuum-toolchain/jdk', ANDROID_HOME='/tmp/continuum-toolchain/android-sdk', GRADLE_USER_HOME=str(repo.parent/'qa-gradle-home'))
def gradle_options(environment):
    options = [environment.get('GRADLE_OPTS', '')]
    configured = environment.get('HTTPS_PROXY') or environment.get('HTTP_PROXY')
    if configured:
        proxy = urllib.parse.urlparse(configured)
        if proxy.scheme not in ('http', 'https') or not proxy.hostname:
            raise ValueError('Expected HTTP(S) proxy URL')
        port = proxy.port or (443 if proxy.scheme == 'https' else 80)
        options.extend(f'-D{scheme}.proxy{key}={value}' for scheme in ('http', 'https')
                       for key, value in [('Host', proxy.hostname), ('Port', port)])
    trust = pathlib.Path('/etc/ssl/certs/java/cacerts')
    if trust.is_file():
        options.append(f'-Djavax.net.ssl.trustStore={trust}')
    return ' '.join(option for option in options if option)
env['GRADLE_OPTS'] = gradle_options(env)
env['PATH'] = env['JAVA_HOME']+'/bin:'+env['PATH']
env['CONTINUUM_CAPTURE_SOURCE_COMMIT'] = expected
for key in ['CONTINUUM_MENTOR_SIGNING_FILE','CONTINUUM_MENTOR_SIGNING_PASSWORD','CONTINUUM_LOCAL_SIGNING_FILE','CONTINUUM_LOCAL_SIGNING_PASSWORD']:
    env.pop(key, None)
patterns = ['at.heinz.deviceai.ContinuumSystemStatusWorkWindowUiTest.cameraReturnsAfterStopAndDoesNotReplayAfterLifecycleBoundaries','at.heinz.deviceai.ContinuumSystemStatusWorkWindowUiTest.speakingInsideAnOpenWindowKeepsTheCameraAndStopReturnsItAfterSpeechEnds','at.heinz.deviceai.avatar3d.AvatarCameraMotionTest']
for name in ['apk53-test-memory.gradle','apk53-compile-only.gradle']:
    shutil.copyfile(pathlib.Path('/tmp')/name, base/name)
(base/'targeted.gradle').write_text('gradle.projectsEvaluated { gradle.rootProject.allprojects { p -> p.tasks.withType(org.gradle.api.tasks.testing.Test).configureEach { t -> if (t.path == ":app:testDebugUnitTest") { t.filter.setIncludePatterns(['+','.join(json.dumps(t) for t in patterns)+'] as String[]) } } } }\n')
shutil.copyfile(repo/'tools/capture/force_capture.gradle', base/'force_capture.gradle')
results=[]
def junit(out, full):
    src=repo/'app/build/test-results/testDebugUnitTest'
    dest=out/'junit'; shutil.copytree(src,dest)
    cases=[]
    for path in dest.glob('TEST-*.xml'):
        cases.extend(ET.parse(path).getroot().iter('testcase'))
    counts={'total':len(cases),'failed':sum(c.find('failure') is not None or c.find('error') is not None for c in cases),'skipped':sum(c.find('skipped') is not None for c in cases)}
    (out/'junit-counts.json').write_text(json.dumps(counts,indent=2))
    if not cases or counts['failed']: raise RuntimeError('JUnit not green: '+str(counts))
    if full:
        subprocess.run(['python','scripts/continuum-code52-junit-check.py',str(dest),str(out/'required-junit.txt')],cwd=repo,check=True)
        required={'LuxuryHoloButtonRendererTest','PerimeterWingChoreographerTest','SpatialWindowLightTest','HollywoodFuiWorkActivityTest','JarvisRenderQualityTest','TtsHologramSignalTest'}
        found={c.get('classname','').split('.')[-1] for c in cases if c.get('classname','').split('.')[-1] in required and c.find('skipped') is None}
        if found != required: raise RuntimeError('Required Cinema classes missing')
        for c in cases:
            if c.get('classname','').split('.')[-1] in required and c.find('skipped') is not None: raise RuntimeError('Required Cinema case skipped')
    else:
        for pattern in patterns:
            if not any(fnmatch.fnmatch(c.get('classname',''),pattern) or fnmatch.fnmatch(c.get('classname','')+'.'+c.get('name','').removesuffix('()'),pattern) for c in cases):
                raise RuntimeError('Requested case missing: '+pattern)
        if counts['skipped']: raise RuntimeError('Focused cases skipped')
    return counts

def run(phase):
    source(); out=base/phase; out.mkdir()
    heap=2200 if phase in ['compile','assemble'] else 1100
    cmd=['/tmp/continuum-toolchain/gradle-8.11.1/bin/gradle','--no-daemon','--no-build-cache','--max-workers=1','--console=plain',f'-Dorg.gradle.jvmargs=-Xmx{heap}m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2','-Pkotlin.compiler.execution.strategy=in-process','-PdeviceAiMentorPreview=true','-PcontinuumOwnerArm64=true','-PcontinuumStandaloneVersionCode=53']
    if phase=='assemble':
        host=json.loads((repo.parent/'apk53-a980-host/result.json').read_text())
        if host['source'] != expected or host['endSource'] != expected or host['endStatus'] or len(host['checks']) != 7 or any(c['exit'] for c in host['checks']): raise RuntimeError('Final host gates not green')
        cmd += ['-PcontinuumLocalRenderUnsigned=true',':app:assembleRelease']
    else:
        cmd += ['-PcontinuumLocalStandaloneQa=true','-PcontinuumUiWidgetTests=true',f'-PcontinuumUiEvidenceDir={out/"images"}','-I',str(base/'apk53-test-memory.gradle')]
        if phase=='lint': cmd += [':app:lintDebug']
        else:
            cmd += [':app:testDebugUnitTest']
            if phase=='compile': cmd += ['-I',str(base/'apk53-compile-only.gradle')]
            else:
                cmd += ['-I',str(base/'force_capture.gradle')]
                if phase=='targeted':cmd += ['-I',str(base/'targeted.gradle')]
    record={'phase':phase,'source':expected,'sourceTree':git('rev-parse','HEAD^{tree}'),'sourceStatus':'','command':cmd,'started':time.time()}
    (out/'invocation.json').write_text(json.dumps(record,indent=2)); print('START',phase,flush=True)
    with (out/'run.log').open('w') as log:
        proc=subprocess.Popen(cmd,cwd=repo,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        try:code=proc.wait(timeout=7200 if phase=='full-test' else 1800)
        except BaseException:
            if proc.poll() is None:
                os.killpg(proc.pid,signal.SIGTERM)
                try:proc.wait(timeout=5)
                except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
            raise
    source();record.update(exit=code,finished=time.time(),endSource=git('rev-parse','HEAD'),endStatus=git('status','--porcelain'))
    if phase in ['targeted','full-test']:
        record['junit']=junit(out,phase=='full-test')
    (out/'result.json').write_text(json.dumps(record,indent=2));results.append(record)
    print('FINISHED',phase,code,record.get('junit'),flush=True)
    if code:raise RuntimeError('Gradle phase failed: '+phase)

status='IN_PROGRESS';failure=None
try:
    with (repo.parent/'cinema-gradle.lock').open('a') as lock:
        print('WAITING_SHARED_GRADLE_LEASE',flush=True);fcntl.flock(lock,fcntl.LOCK_EX)
        try:
            source();print('ACQUIRED_SHARED_GRADLE_LEASE',flush=True)
            for phase in ['compile','targeted','full-test','lint','assemble']:run(phase)
            status='PASS'
        finally:fcntl.flock(lock,fcntl.LOCK_UN);print('RELEASED_SHARED_GRADLE_LEASE',flush=True)
except BaseException as exc:
    status='FAILED';failure=type(exc).__name__+': '+str(exc);print(failure,flush=True)
finally:
    (base/'pipeline-result.json').write_text(json.dumps({'source':expected,'status':status,'failure':failure,'phases':results},indent=2))
sys.exit(0 if status=='PASS' else 1)
