import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const SRC=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../scripts/local-m4-android.sh');

function command(executable,args,options={}) {
  const result=spawnSync(executable,args,{encoding:'utf8',timeout:15_000,...options});
  assert.ifError(result.error);
  return result;
}

function fixture() {
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-preflight-test-'));
  const root=path.join(home,'repo');
  fs.mkdirSync(path.join(root,'scripts'),{recursive:true});
  fs.copyFileSync(SRC,path.join(root,'scripts','local-m4-android.sh'));
  fs.mkdirSync(path.join(root,'.github','continuum'),{recursive:true});
  fs.writeFileSync(path.join(root,'.github','continuum','m4-local-gate.mjs'),'mocked for wrapper preflight only\n');
  fs.mkdirSync(path.join(root,'backend','test'),{recursive:true});
  fs.writeFileSync(path.join(root,'backend','test','dummy.test.js'),'mocked\n');
  fs.mkdirSync(path.join(home,'tools'),{recursive:true});
  const tools=path.join(home,'tools');
  const tool=(name,body)=>{
    const file=path.join(tools,name);
    fs.writeFileSync(file,'#!/bin/sh\n'+body+'\n',{mode:0o700});
  };
  tool('java','echo \'openjdk version "17.0.12" 2025-02-18\' >&2');
  tool('gradle','printf "Gradle 8.11.1\\n"');
  // This verifies only CLI orchestration; a real APK/proof is not produced.
  tool('node','if [ "$1" = "-p" ]; then echo 22; exit 0; fi\n'+
    'if [ "$2" = "--verify" ]; then echo \'{"valid":true,"releaseReady":true}\'; exit 0; fi\n'+
    'printf \'{"complete":true,"report":"/tmp/fake-m4-proof.json"}\\n\'');
  const sdk=path.join(home,'sdk');
  fs.mkdirSync(path.join(sdk,'platforms','android-36'),{recursive:true});
  fs.writeFileSync(path.join(sdk,'platforms','android-36','android.jar'),'mock SDK platform');
  fs.mkdirSync(path.join(sdk,'build-tools','35.0.0'),{recursive:true});
  fs.writeFileSync(path.join(sdk,'build-tools','35.0.0','zipalign'),'#!/bin/sh\n',{mode:0o700});
  assert.equal(command('git',['init','-q',root]).status,0);
  assert.equal(command('git',['-C',root,'add','.']).status,0);
  assert.equal(command('git',['-C',root,'-c','user.name=Test','-c','user.email=test@invalid.local','commit','-qm','fixture']).status,0);
  const head=command('git',['-C',root,'rev-parse','HEAD']).stdout.trim();
  const env={...process.env,PATH:tools+path.delimiter+process.env.PATH,ANDROID_HOME:sdk,
    XDG_STATE_HOME:path.join(home,'state')};
  delete env.JAVA_HOME;
  const run=(args=[head],changes={})=>command('bash',[path.join(root,'scripts','local-m4-android.sh'),...args],{
    cwd:root,env:{...env,...changes}
  });
  return {home,root,sdk,head,tools,tool,run,dispose:()=>fs.rmSync(home,{recursive:true,force:true})};
}

for (const [name,mutate,expectedCode,expectedMessage] of [
  ['rejects malformed expected SHA',f=>({args:['bad']}),64,'Usage:'],
  ['rejects wrong Git HEAD',f=>({args:['0'.repeat(40)]}),69,'HEAD mismatch'],
  ['rejects dirty Git checkout',f=>{fs.writeFileSync(path.join(f.root,'new-file'),'dirty');return {};},69,'Checkout is not clean'],
  ['rejects absent Android SDK',f=>({env:{ANDROID_HOME:path.join(f.home,'missing')}}),69,'Android SDK 36'],
  ['rejects incompatible JDK',f=>{f.tool('java','echo \'openjdk version "21.0.2" 2024-01-16\' >&2');return {};},69,'JDK 17'],
  ['rejects incompatible Gradle',f=>{f.tool('gradle','printf "Gradle 9.0\\n"');return {};},69,'Gradle 8.11.1'],
  ['runs M4 and proof verification after validated preflight',f=>({}),0,'M4 proof passed']
]) test(name,()=>{
  const f=fixture();
  try {
    const changed=mutate(f);
    const result=f.run(changed.args??[f.head],changed.env??{});
    assert.equal(result.status,expectedCode,result.stdout+'\n'+result.stderr);
    assert.match(result.stdout+'\n'+result.stderr,new RegExp(expectedMessage));
  } finally { f.dispose(); }
});
