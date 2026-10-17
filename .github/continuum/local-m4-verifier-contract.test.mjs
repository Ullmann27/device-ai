import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const SRC = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../scripts/local-m4-android.sh');

function execute(binary, args, opts={}) {
  const result=spawnSync(binary,args,{encoding:'utf8',timeout:15_000,...opts});
  assert.ifError(result.error);
  return result;
}

function setup() {
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-verify-'));
  const root=path.join(home,'repo');
  const script=path.join(root,'scripts','local-m4-android.sh');
  const dir=path.join(root,'.github','continuum');
  fs.mkdirSync(path.dirname(script),{recursive:true});
  fs.copyFileSync(SRC,script);
  fs.mkdirSync(dir,{recursive:true});
  fs.writeFileSync(path.join(dir,'m4-local-gate.mjs'),'simulated runner; no real build\n');
  fs.mkdirSync(path.join(root,'backend','test'),{recursive:true});
  fs.writeFileSync(path.join(root,'backend','test','fake.test.js'),'mock\n');
  const tools=path.join(home,'tools');
  fs.mkdirSync(tools,{recursive:true});
  function tool(name,content){
    const target=path.join(tools,name);
    fs.writeFileSync(target,'#!/bin/sh\n'+content+'\n',{mode:0o700});
  }
  tool('java','echo \'openjdk version "17.0.12" 2025-02-18\' >&2');
  tool('gradle','printf "Gradle 8.11.1\\n"');
  tool('node',[
    'if [ "$1" = "-p" ]; then printf "22\\n"; exit 0; fi',
    'if [ "$1" = "-e" ]; then exec "$CONTINUUM_TEST_NODE" "$@"; fi',
    'if [ "$2" = "--verify" ]; then',
    '  case "$CONTINUUM_TEST_RESULT" in',
    '   red) printf \'{"head":"%s","proof":"%s","valid":false,"releaseReady":false}\\n\' "$CONTINUUM_TEST_HEAD" "$3" ;;',
    '   invalid-but-ready) printf \'{"head":"%s","proof":"%s","valid":false,"releaseReady":true}\\n\' "$CONTINUUM_TEST_HEAD" "$3" ;;',
    '   wrong-proof) printf \'{"head":"%s","proof":"%s","valid":true,"releaseReady":true}\\n\' "$CONTINUUM_TEST_HEAD" "/tmp/wrong-m4-proof.json" ;;',
    '   wrong-head) printf \'{"head":"%s","proof":"%s","valid":true,"releaseReady":true}\\n\' "ffffffffffffffffffffffffffffffffffffffff" "$3" ;;',
    '   malformed) printf \'not JSON\\n\' ;;',
    '   *) printf \'{"head":"%s","proof":"%s","valid":true,"releaseReady":true}\\n\' "$CONTINUUM_TEST_HEAD" "$3" ;;',
    '  esac',
    '  exit 0',
    'fi',
    'printf \'{"complete":true,"report":"/tmp/mock-gate-report.json"}\\n\''
  ].join('\n'));
  const sdk=path.join(home,'sdk');
  fs.mkdirSync(path.join(sdk,'platforms','android-36'),{recursive:true});
  fs.writeFileSync(path.join(sdk,'platforms','android-36','android.jar'),'mock');
  fs.mkdirSync(path.join(sdk,'build-tools','35.0.0'),{recursive:true});
  fs.writeFileSync(path.join(sdk,'build-tools','35.0.0','zipalign'),'mock',{mode:0o700});
  assert.equal(execute('git',['init','-q',root]).status,0);
  assert.equal(execute('git',['-C',root,'add','.']).status,0);
  assert.equal(execute('git',['-C',root,'-c','user.name=Fixture',
    '-c','user.email=fixture@example.invalid','commit','-qm','fixture']).status,0);
  const head=execute('git',['-C',root,'rev-parse','HEAD']).stdout.trim();
  const env={...process.env,PATH:tools+path.delimiter+process.env.PATH,
    ANDROID_HOME:sdk,XDG_STATE_HOME:path.join(home,'state'),
    CONTINUUM_TEST_NODE:process.execPath,CONTINUUM_TEST_HEAD:head};
  delete env.JAVA_HOME;
  return {
    head, dispose:()=>fs.rmSync(home,{recursive:true,force:true}),
    run:(mode)=>execute('bash',[script,head],{
      cwd:root,env:{...env,CONTINUUM_TEST_RESULT:mode}
    })
  };
}

for(const [mode,shouldPass] of [
  ['green',true],
  ['red',false],
  ['wrong-head',false],
  ['invalid-but-ready',false],
  ['wrong-proof',false],
  ['malformed',false]
]){
  test('M4 wrapper verifier contract '+mode,()=>{
    const f=setup();
    try{
      const r=f.run(mode);
      assert.equal(r.status,shouldPass?0:69,r.stdout+'\n'+r.stderr);
      if(shouldPass)assert.match(r.stdout,/M4 proof passed/);
      else assert.match(r.stderr,/BLOCKED: M4 proof verifier did not confirm/);
    }finally{f.dispose();}
  });
}
