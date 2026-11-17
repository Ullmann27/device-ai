from pathlib import Path
import subprocess,sys,json,hashlib,os
root=Path(os.environ.get('THERMAL_REPO', str(Path(__file__).resolve().parents[3])))
cache=Path(os.environ.get('CONTINUUM_KOTLIN_CACHE', '/workspace/scratch/c9f74e2967e3/gradle-home/caches/modules-2/files-2.1'))
java=os.environ.get('CONTINUUM_JAVA', '/workspace/scratch/6139925ecf70/toolchain/jdk-21.0.13+9/bin/java')
def jar(group,name,version): return str(next((cache/group/name/version).glob('*/*.jar')))
compiler=[jar('org.jetbrains.kotlin',n,'2.4.0') for n in ['kotlin-compiler-embeddable','kotlin-build-tools-api','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']]
compiler += [jar('org.jetbrains.kotlin','kotlin-reflect','1.6.10'),jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),jar('org.jetbrains','annotations','13.0')]
baseline=os.environ.get('CONTINUUM_COMPILED_APP', '/workspace/scratch/6139925ecf70/device-ai/app/build/tmp/kotlin-classes/debug')
cp=[baseline,jar('org.jetbrains.kotlin','kotlin-stdlib','2.4.0'),jar('junit','junit','4.13.2'),jar('org.hamcrest','hamcrest-core','1.3'),jar('org.json','json','20240303')]
name=sys.argv[1]
out=Path(os.environ['CONTINUUM_QA_OUT'])/name
out.mkdir(parents=True,exist_ok=True)
manifest=json.loads((Path(__file__).parent/'sources.json').read_text())
paths=[root/p for p in manifest['sources']]
tests=[root/p for p in manifest['tests']]
cmd=[java,'-Xmx512m','-XX:ActiveProcessorCount=1','-cp',':'.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-Xfriend-paths='+baseline,'-classpath',':'.join(cp),'-d',str(out/'classes'),*map(str,paths+tests)]
(out/'manifest.json').write_text(json.dumps({'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'command':cmd,'sources':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths+tests}},indent=2))
with (out/'compile.log').open('w') as f: rc=subprocess.call(cmd,cwd=root,stdout=f,stderr=subprocess.STDOUT)
print((out/'compile.log').read_text())
if rc:sys.exit(rc)
classes=['at.heinz.deviceai.learning.'+p.stem for p in tests]
run=[java,'-Xmx256m','-XX:ActiveProcessorCount=1','-cp',':'.join([str(out/'classes')]+cp),'org.junit.runner.JUnitCore',*classes]
with (out/'test.log').open('w') as f: rc=subprocess.call(run,cwd=root,stdout=f,stderr=subprocess.STDOUT)
print((out/'test.log').read_text());(out/'exit.txt').write_text(str(rc));sys.exit(rc)
