from pathlib import Path
import subprocess, sys, json, hashlib
repo=Path('/workspace/scratch/2d62a799e417/omega-logic')
cache=Path('/workspace/scratch/c9f74e2967e3/gradle-home/caches/modules-2/files-2.1')
compiled=Path('/workspace/scratch/6139925ecf70/device-ai/app/build/tmp/kotlin-classes/debug')
out=Path('/workspace/scratch/2d62a799e417/logic-qa')/sys.argv[1]
out.mkdir(parents=True,exist_ok=True)
def jar(group,name,ver): return next((cache/group/name/ver).glob('*/*.jar'))
compiler=[jar('org.jetbrains.kotlin',n,'2.4.0') for n in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-reflect']]+[jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),jar('org.jetbrains','annotations','23.0.0')]
junit=jar('junit','junit','4.13.2'); hamcrest=jar('org.hamcrest','hamcrest-core','1.3'); jsonjar=jar('org.json','json','20240303')
cp=':'.join(str(x) for x in [compiled,junit,hamcrest,jsonjar,*compiler[1:]])
classes=['at.heinz.deviceai.BootstrapBrainOwnerIntentSafetyTest','at.heinz.deviceai.systemintelligence.SpokenDiagnosisRoutingTest','at.heinz.deviceai.cognitive.OwnerAppOpenRoutingTest']
if len(sys.argv)>2:classes=sys.argv[2:]
sources=[repo/'app/src/test/java'/Path(x.replace('.','/')+'.kt') for x in classes]
for name in ['BootstrapBrain.kt','systemintelligence/SystemDiagnosis.kt','cognitive/OwnerAppLookupIntent.kt','cognitive/OwnerDeviceRequestSafety.kt','cognitive/OwnerAppClarification.kt']:
 p=repo/'app/src/main/java/at/heinz/deviceai'/name
 if p.exists(): sources.append(p)
meta={'source_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'cached_dependencies':'/workspace/scratch/6139925ecf70/device-ai/app/build/tmp/kotlin-classes/debug','test_classes':classes,'source_sha256':{str(p.relative_to(repo)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}}
(out/'inputs.json').write_text(json.dumps(meta,indent=2))
cmd=['java','-Xmx512m','-XX:ActiveProcessorCount=1','-cp',':'.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-Xfriend-paths='+str(compiled),'-classpath',cp,'-d',str(out/'classes'),*map(str,sources)]
with (out/'compile.log').open('w') as f:r=subprocess.run(cmd,cwd=repo,stdout=f,stderr=subprocess.STDOUT)
print('COMPILE',r.returncode)
if r.returncode:print((out/'compile.log').read_text()[-12000:]);sys.exit(r.returncode)
with (out/'junit.log').open('w') as f:r=subprocess.run(['java','-Xmx512m','-XX:ActiveProcessorCount=1','-cp',str(out/'classes')+':'+cp,'org.junit.runner.JUnitCore',*classes],cwd=repo,stdout=f,stderr=subprocess.STDOUT)
print((out/'junit.log').read_text());print('EXIT',r.returncode)
sys.exit(r.returncode)
