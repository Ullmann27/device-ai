import pathlib,subprocess,json,hashlib
repo=pathlib.Path('/workspace/continuum-breakthrough-quality');out=pathlib.Path('/workspace/continuum-quality-runs/pool-f61fc3d5');out.mkdir(exist_ok=True)
base='853a8ee08359ce41375ddb9334ac8ae9c2e2c59e';candidate='f61fc3d56b9437d762b78625f748e64f887ef5f6';src='app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt';test='app/src/test/java/at/heinz/deviceai/chief/ChiefWorkerPoolTest.kt'
cp=str(repo/'app/build/tmp/kotlin-classes/debugUnitTest')+':'+str(repo/'app/build/tmp/kotlin-classes/debug')+':'+pathlib.Path('/workspace/continuum-build-env/baseline64-ui-classpath.txt').read_text().strip()
for name,sha in [('baseline',base),('candidate',candidate)]:
 dest=out/name;dest.mkdir(exist_ok=True)
 hashes={}
 for p,s in [(src,sha),(test,candidate)]:
  data=subprocess.check_output(['git','show',s+':'+p],cwd=repo);f=dest/pathlib.Path(p).name;f.write_bytes(data);hashes[p]={'source_sha':s,'sha256':hashlib.sha256(data).hexdigest()}
 command=['/workspace/continuum-build-env/kotlin-official/kotlinc/bin/kotlinc',str(dest/'ChiefWorkerPool.kt'),str(dest/'ChiefWorkerPoolTest.kt'),'-jvm-target','17','-cp',cp,'-d',str(dest/'classes')]
 with (dest/'compile.txt').open('w') as f:r=subprocess.run(command,stdout=f,stderr=subprocess.STDOUT)
 (dest/'compile.exit').write_text(str(r.returncode)+'\n');assert r.returncode==0
 run=['/usr/bin/java','-Xmx384m','-cp',str(dest/'classes')+':'+cp,'org.junit.runner.JUnitCore','at.heinz.deviceai.chief.ChiefWorkerPoolTest']
 if name=='candidate':run+=['at.heinz.deviceai.chief.ChiefBrainWiringTest']
 with (dest/'junit.txt').open('w') as f:r=subprocess.run(run,stdout=f,stderr=subprocess.STDOUT)
 (dest/'junit.exit').write_text(str(r.returncode)+'\n');(dest/'binding.json').write_text(json.dumps({'sources':hashes,'compile':command,'run':run},indent=2)+'\n');print(name,'exit',r.returncode,(dest/'junit.txt').read_text())
