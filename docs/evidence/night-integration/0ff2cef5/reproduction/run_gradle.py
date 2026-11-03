import os,sys,subprocess,urllib.parse,pathlib
b=pathlib.Path(__file__).resolve().parent
env=dict(os.environ)
env.update(JAVA_HOME=str(b/'jdk17'),ANDROID_HOME=str(b/'android-sdk'),ANDROID_SDK_ROOT=str(b/'android-sdk'),GRADLE_USER_HOME=str(b/'gradle-cache'))
env['PATH']=str(b/'jdk17/bin')+':'+str(b/'android-sdk/build-tools/35.0.0')+':'+env['PATH']
a=[str(b/'gradle-8.11.1/bin/gradle'),'--no-daemon','--max-workers=2','--console=plain','-Dorg.gradle.jvmargs=-Xmx1600m -XX:MaxMetaspaceSize=512m','-Pkotlin.compiler.execution.strategy=in-process']
for scheme in ['http','https']:
 p=urllib.parse.urlparse(env.get(scheme.upper()+'_PROXY',''))
 if p.hostname:a += ['-D'+scheme+'.proxyHost='+p.hostname,'-D'+scheme+'.proxyPort='+str(p.port)]
a += ['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts','-Djavax.net.ssl.trustStorePassword=changeit']
log=pathlib.Path(sys.argv[1]);log.parent.mkdir(parents=True,exist_ok=True)
a+=sys.argv[2:]
with log.open('w') as f:r=subprocess.run(a,env=env,stdout=f,stderr=subprocess.STDOUT)
print('GRADLE_EXIT='+str(r.returncode))
print(log.read_text(errors='replace')[-6500:])
sys.exit(r.returncode)
