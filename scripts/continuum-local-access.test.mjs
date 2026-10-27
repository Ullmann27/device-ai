import test from 'node:test';import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
function fixture(t){
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-local-access-')),root=path.join(temp,'repo'),bin=path.join(temp,'bin');
 t.after(()=>fs.rmSync(temp,{recursive:true,force:true}));fs.mkdirSync(path.join(root,'scripts'),{recursive:true});fs.mkdirSync(bin);
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git('init','-q');git('remote','add','origin','git@github.com:Ullmann27/device-ai.git');
 const executable=(name,text)=>{const p=path.join(bin,name);fs.writeFileSync(p,'#!/usr/bin/env bash\n'+text,{mode:0o700});return p;};
 return {temp,root,bin,git,executable};
}
test('HTTPS setup verifies access first, converts only official origin and redacts credential tool output',t=>{
 const f=fixture(t),realGit=spawnSync('which',['git'],{encoding:'utf8'}).stdout.trim();
 fs.copyFileSync(new URL('./continuum-github-https.sh',import.meta.url),path.join(f.root,'scripts/access.sh'));
 f.executable('gh','echo PRIVATE_AUTH_SENTINEL\nif [[ "$1 $2" == "auth status" && "${TEST_AUTH_FAIL:-}" == 1 ]]; then exit 1; fi\n');
 f.executable('git','for arg in "$@"; do if [[ "$arg" == ls-remote ]]; then echo PRIVATE_AUTH_SENTINEL; exit "${TEST_READ_EXIT:-0}"; fi; done\nexec '+realGit+' "$@"\n');
 const run=(mode,extra={})=>spawnSync('bash',[path.join(f.root,'scripts/access.sh'),mode],{env:{...process.env,PATH:f.bin+':'+process.env.PATH,...extra},encoding:'utf8',timeout:5000});
 const doctor=run('doctor');assert.equal(doctor.status,0,doctor.stderr);assert.match(doctor.stdout,/SSH_UNCHANGED_RUN_SETUP/);
 assert.match(f.git('remote','get-url','origin'),/^git@/);
 const denied=run('setup',{TEST_READ_EXIT:'1'});assert.equal(denied.status,69);assert.match(f.git('remote','get-url','origin'),/^git@/);
 f.git('config','url.ssh://git@github.com/.insteadOf','https://github.com/');
 const setup=run('setup');assert.equal(setup.status,0,setup.stderr);assert.equal(f.git('remote','get-url','origin'),'https://github.com/Ullmann27/device-ai.git');
 for(const r of [doctor,denied,setup])assert.equal((r.stdout+r.stderr).includes('PRIVATE_AUTH_SENTINEL'),false);
 f.git('remote','set-url','origin','git@github.com:someone/other.git');assert.equal(run('setup').status,69);
 assert.equal(f.git('remote','get-url','origin'),'git@github.com:someone/other.git');
});
test('missing auth gives bounded device login instructions without changing remote',t=>{
 const f=fixture(t);fs.copyFileSync(new URL('./continuum-github-https.sh',import.meta.url),path.join(f.root,'scripts/access.sh'));
 f.executable('gh','exit 1\n');
 const r=spawnSync('bash',[path.join(f.root,'scripts/access.sh'),'setup'],{env:{...process.env,PATH:f.bin+':'+process.env.PATH},encoding:'utf8'});
 assert.equal(r.status,69);assert.match(r.stderr,/timeout --foreground 300 gh auth login/);assert.match(f.git('remote','get-url','origin'),/^git@/);
});
test('owner doctor rejects a JRE and uses complete JAVA_HOME ahead of system Java',t=>{
 const f=fixture(t),script=path.join(f.root,'scripts/build.sh');
 fs.copyFileSync(new URL('./continuum-local-owner-apk.sh',import.meta.url),script);
 fs.mkdirSync(path.join(f.root,'app/src/main'),{recursive:true});fs.writeFileSync(path.join(f.root,'app/build.gradle.kts'),'// fixture');fs.writeFileSync(path.join(f.root,'settings.gradle.kts'),'// fixture');
 const sdk=path.join(f.temp,'sdk'),jdk=path.join(f.temp,'jdk');fs.mkdirSync(path.join(sdk,'platforms/android-36'),{recursive:true});fs.writeFileSync(path.join(sdk,'platforms/android-36/android.jar'),'fixture');fs.mkdirSync(path.join(sdk,'build-tools/35.0.0'),{recursive:true});fs.mkdirSync(path.join(jdk,'bin'),{recursive:true});
 const write=(p,s)=>fs.writeFileSync(p,'#!/usr/bin/env bash\n'+s,{mode:0o700});
 write(path.join(jdk,'bin/java'),'echo \'openjdk version "17.0.1"\' >&2\n');write(path.join(jdk,'bin/keytool'),'echo "  SHA256: wrong"\n');
 const gradle=f.executable('gradle','echo "Gradle 8.11.1"\n');
 write(path.join(sdk,'build-tools/35.0.0/aapt2'),'exit 0\n');write(path.join(sdk,'build-tools/35.0.0/apksigner'),'exit 0\n');
 f.executable('node','echo 24\n');f.executable('java','echo \'openjdk version "21.0.1"\' >&2\n');
 f.git('add','.');f.git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixture');
 const env={...process.env,PATH:f.bin+':'+process.env.PATH,JAVA_HOME:jdk,ANDROID_HOME:sdk,CONTINUUM_GRADLE_PATH:gradle};
 const run=(mode,extra={})=>spawnSync('bash',[script,mode],{env:{...env,...extra},encoding:'utf8',timeout:5000});
 assert.equal(run('doctor').status,69);write(path.join(jdk,'bin/javac'),'echo "javac 17.0.1"\n');
 const ok=run('doctor');assert.equal(ok.status,0,ok.stderr);assert.match(ok.stdout,/DOCTOR=READY/);
 const key=path.join(f.temp,'dummy.p12');fs.writeFileSync(key,'TEST_FILE_NOT_A_KEY');
 const rejected=run('build',{CONTINUUM_OWNER_LOCAL_APPROVED_SHA:f.git('rev-parse','HEAD'),CONTINUUM_MENTOR_SIGNING_FILE:key,CONTINUUM_MENTOR_SIGNING_PASSWORD:'TEST_ONLY'});
 assert.equal(rejected.status,69);assert.match(rejected.stderr,/certificate does not match/);assert.doesNotMatch(rejected.stdout,/OWNER_APK=/);
});
