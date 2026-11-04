from pathlib import Path
import hashlib,json,os,subprocess,sys,xml.etree.ElementTree as ET,zipfile

repo=Path('/workspace/scratch/7dcf5d78f2cb/round2-build')
envroot=Path('/workspace/scratch/7dcf5d78f2cb/round2-android-env')
cache=envroot/'gradle-cache/caches/modules-2/files-2.1'
donor=Path('/workspace/scratch/822e72396ed9/continuum-combined')
business=donor/'app/build/tmp/kotlin-classes/debug'
revision=sys.argv[1]
phase=sys.argv[2]
methods=sys.argv[3:]
out=envroot/'direct-robolectric'/phase
out.mkdir(parents=True,exist_ok=False)
(out/'classes').mkdir()
(out/'tmp').mkdir()
libs=envroot/'direct-robolectric/lib'
libs.mkdir(parents=True,exist_ok=True)
sdks=envroot/'direct-robolectric/sdk'
sdks.mkdir(exist_ok=True)
java=envroot/'jdk17/bin/java'
javac=envroot/'jdk17/bin/javac'
android=envroot/'android-sdk/platforms/android-36/android.jar'

def sha(path):
 with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def artifact(group,name,version,suffix='jar'):
 files=list((cache/group/name/version).glob('*/'+name+'-'+version+'.'+suffix))
 if len(files)!=1:raise RuntimeError('Expected one cached artifact: '+':'.join([group,name,version])+':'+str(files))
 return files[0]

ns={'m':'http://maven.apache.org/POM/4.0.0'}
groups={'org.robolectric','junit','org.hamcrest'}
seen=set()
while groups-seen:
 group=sorted(groups-seen)[0]
 seen.add(group)
 if group in ['org.jetbrains.kotlin','org.jetbrains.kotlinx','androidx.annotation']:
  continue
 for pom in (cache/group).glob('*/*/*/*.pom'):
  for dep in ET.parse(pom).findall('m:dependencies/m:dependency',ns):
   scope=dep.findtext('m:scope',default='compile',namespaces=ns)
   if scope not in ['compile','runtime']:continue
   target=dep.findtext('m:groupId',default='',namespaces=ns)
   if target and '${' not in target:groups.add(target)

runtime=[]
for group in sorted(groups-{'org.jetbrains.kotlin','org.jetbrains.kotlinx'}):
 for jar in sorted((cache/group).glob('*/*/*/*.jar')):
  if group=='org.robolectric' and jar.parent.parent.parent.name=='android-all-instrumented':continue
  if group=='com.google.guava' and jar.name.endswith('-android.jar'):continue
  runtime.append(jar)
stdlib=artifact('org.jetbrains.kotlin','kotlin-stdlib','2.4.0')
runtime.insert(0,stdlib)
for group,name,version in [('androidx.test','monitor','1.7.2'),('androidx.test.espresso','espresso-idling-resource','3.6.1')]:
 aar=artifact(group,name,version,'aar')
 target=libs/(name+'-'+version+'-classes.jar')
 if not target.exists():
  with zipfile.ZipFile(aar) as archive:target.write_bytes(archive.read('classes.jar'))
 runtime.append(target)
for jar in (cache/'org.robolectric/android-all-instrumented').glob('*/*/*.jar'):
 target=sdks/jar.name
 if not target.exists():target.symlink_to(jar)

sources='''avatar/AvatarContract.kt
cockpit/CockpitAnimationEvent.kt
cockpit/CockpitContracts.kt
motion/DampedSpring.kt
motion/Easings.kt
motion/FaceMotion.kt
motion/MotionContracts.kt
motion/MotionPresets.kt
motion/Staggers.kt
ui/AndroidContinuumPainter.kt
ui/BrainOrbAnimationRenderer.kt
ui/ContinuumFonts.kt
ui/ContinuumIcons.kt
ui/ContinuumPainter.kt
ui/ContinuumTheme.kt
ui/JarvisCockpitGrid.kt
ui/JarvisDesign.kt
ui/JarvisMotion.kt
ui/JarvisNeon.kt
ui/JarvisReferenceHud.kt
ui/JarvisSequenceEffects.kt
ui/JarvisSequenceView.kt
ui/JarvisShellLayout.kt
ui/JarvisWindowProfile.kt'''.splitlines()
paths=['app/src/main/java/at/heinz/deviceai/'+name for name in sources]
paths+=['app/src/uiTest/java/at/heinz/deviceai/ui/JarvisSequenceTurnLifecycleUiTest.kt']
source_hashes={}
source_files=[]
for name in paths:
 data=subprocess.check_output(['git','show',revision+':'+name],cwd=repo)
 path=out/'src'/name
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(data)
 source_hashes[name]=hashlib.sha256(data).hexdigest()
 source_files.append(path)
leaf='app/src/main/java/at/heinz/deviceai/cockpit/SafePillActionGate.kt'
current=subprocess.check_output(['git','show',revision+':'+leaf],cwd=repo)
if current!=(donor/leaf).read_bytes():raise RuntimeError('Real business dependency source differs.')
leaf_classes={str(path.relative_to(business)):sha(path) for path in business.glob('at/heinz/deviceai/cockpit/ToolGatePillActionGate*.class')}
if not leaf_classes:raise RuntimeError('Real compiled business dependency absent.')
compiler=[artifact('org.jetbrains.kotlin',name,'2.4.0') for name in ['kotlin-compiler-embeddable','kotlin-build-tools-api','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']]
compiler+=[artifact('org.jetbrains.kotlin','kotlin-reflect','1.6.10')]
compiler+=list((cache/'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm').glob('*/*/*.jar'))
compiler+=list((cache/'org.jetbrains/annotations').glob('*/*/*.jar'))
compile_cp=[android,*runtime,business]
cp=os.pathsep.join(map(str,compile_cp))
command=[str(java),'-Xmx768m','-XX:ActiveProcessorCount=2','-XX:+UseSerialGC','-cp',os.pathsep.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,'-d',str(out/'classes'),*map(str,source_files)]
manifest={'sourceCommit':revision,'sourceTree':subprocess.check_output(['git','rev-parse',revision+'^{tree}'],cwd=repo,text=True).strip(),'sourceSha256':source_hashes,'realBusinessSource':{'path':leaf,'sha256':hashlib.sha256(current).hexdigest(),'matchesDonor':True,'donorCheckout':str(donor),'classes':leaf_classes},'compileCommand':command,'runtimeArtifacts':{str(path):sha(path) for path in runtime},'androidCompileJar':{'path':str(android),'sha256':sha(android)},'sdkJars':{str(path):sha(path) for path in sdks.iterdir()},'frameworkStandIns':False,'businessStandIns':False,'methods':methods,'limitation':'Direct widget Robolectric test, not a full Gradle/app manifest/resource/device gate. Real precompiled business classes are last on classpath; candidate UI classes are compiled first from full original files.'}
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
with (out/'compile.log').open('w') as log:compiled=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT)
print('COMPILE_EXIT_CODE='+str(compiled.returncode),flush=True)
if compiled.returncode:
 print((out/'compile.log').read_text(),flush=True)
 sys.exit(compiled.returncode)
runner=out/'MethodRunner.java'
runner.write_text('''import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
public class MethodRunner {
  public static void main(String[] args) throws Exception {
    Class<?> test = Class.forName(args[0]);
    int total = 0, failed = 0;
    for (int i = 1; i < args.length; i++) {
      Result result = new JUnitCore().run(Request.method(test, args[i]));
      total += result.getRunCount(); failed += result.getFailureCount();
      System.out.println("METHOD " + args[i] + " RUN=" + result.getRunCount() + " FAIL=" + result.getFailureCount());
      for (Failure failure : result.getFailures()) System.out.println(failure.getTrace());
    }
    System.out.println("TESTS=" + total + " FAILURES=" + failed);
    System.exit(failed == 0 && total == args.length - 1 ? 0 : 1);
  }
}
''')
runtime_cp=[out/'classes',android,*runtime,business]
classpath=os.pathsep.join(map(str,runtime_cp))
subprocess.run([str(javac),'-cp',classpath,'-d',str(out/'classes'),str(runner)],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
test_class='at.heinz.deviceai.ui.JarvisSequenceTurnLifecycleUiTest'
run=[str(java),'-Xmx512m','-XX:ActiveProcessorCount=2','-XX:+UseSerialGC','-Drobolectric.offline=true','-Drobolectric.dependency.dir='+str(sdks),'-Djava.io.tmpdir='+str(out/'tmp'),'-Xlog:class+load=info:file='+str(out/'class-load.log'),'-cp',classpath]
run+=(['MethodRunner',test_class,*methods] if methods else ['org.junit.runner.JUnitCore',test_class])
manifest['runCommand']=run
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
with (out/'junit.log').open('w') as log:result=subprocess.run(run,cwd=out,stdout=log,stderr=subprocess.STDOUT)
print((out/'junit.log').read_text(),flush=True)
print('JUNIT_EXIT_CODE='+str(result.returncode),flush=True)
(out/'result.json').write_text(json.dumps({'compileExitCode':compiled.returncode,'junitExitCode':result.returncode},indent=2)+'\n')
sys.exit(result.returncode)
