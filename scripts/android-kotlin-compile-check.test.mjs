import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

test('actual host classifier preserves membership under pipefail and an early grep match',()=>{
  const source=fs.readFileSync(new URL('./android-kotlin-compile-check.sh',import.meta.url),'utf8');
  const condition=source.match(/^  (if .+; then) external\+=/m)?.[1];
  assert.ok(condition,'the actual classifier condition must be exercised');
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-host-membership-'));
  try {
    const input=path.join(dir,'unavailable-files.txt');
    fs.writeFileSync(input,'target.kt\n'+'other.kt\n'.repeat(20000)+'last.kt\n');
    const body='set -o pipefail; unavailable=$(cat "$1"); file="$2"; '+condition+' exit 0; else exit 1; fi';
    for(const [file,expected] of [['target.kt',0],['last.kt',0],['unknown.kt',1]]) {
      const run=spawnSync('bash',['-c',body,'classifier',input,file],{encoding:'utf8',timeout:5000});
      assert.equal(run.status,expected,file+': '+run.stderr);
    }
  } finally { fs.rmSync(dir,{recursive:true,force:true}); }
});

test('host compile check never reports compiler crashes as a successful partial check',()=>{
  const root=path.resolve(new URL('..',import.meta.url).pathname);
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-host-exit-'));
  try {
    const bin=path.join(dir,'bin'),deps=path.join(dir,'deps');
    fs.mkdirSync(bin);fs.mkdirSync(deps);
    for(const name of ['android-all-15','okhttp','okio','shizuku-api','shizuku-aidl','shizuku-shared','shizuku-provider'])
      fs.writeFileSync(path.join(deps,name+'.jar'),'fixture');
    fs.writeFileSync(path.join(bin,'kotlinc'),'#!/bin/sh\ncat "$COMPILER_LOG"\nexit "$COMPILER_STATUS"\n',{mode:0o755});
    const external=path.join(root,'app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt')+":6:19: error: unresolved reference 'ai'.\n";
    for(const [status,diagnostic,expected] of [
      [0,'',0], [1,external,0], // documented partial check, only missing LiteRT-LM
      [137,external,137], [2,external,2],
      [1,'exception: java.lang.OutOfMemoryError: Java heap space\n'+external,1],
      [1,'unparsed compiler failure\n',1],
      [1,path.join(root,'app/src/main/java/at/heinz/deviceai/AgentActivity.kt')+':1:1: error: broken\n',1]
    ]) {
      const log=path.join(dir,'compiler.log');fs.writeFileSync(log,diagnostic);
      const run=spawnSync('bash',[path.join(root,'scripts/android-kotlin-compile-check.sh'),path.join(dir,'out')],{
        cwd:root,encoding:'utf8',timeout:10000,
        env:{...process.env,PATH:bin+path.delimiter+process.env.PATH,CONTINUUM_ANDROID_DEPS:deps,
          COMPILER_LOG:log,COMPILER_STATUS:String(status)}
      });
      assert.equal(run.status,expected,JSON.stringify({status,diagnostic,stdout:run.stdout,stderr:run.stderr}));
      if(expected!==0)assert.doesNotMatch(run.stdout,/KOTLIN_COMPILE_CHECKED:/);
    }
  } finally {fs.rmSync(dir,{recursive:true,force:true});}
});
