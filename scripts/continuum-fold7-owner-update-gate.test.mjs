import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const source = path.join(path.dirname(fileURLToPath(import.meta.url)),
  'continuum-fold7-owner-update-gate.sh');
const FP = '6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23';
function cmd(executable,args,options={}) {
  return spawnSync(executable,args,{encoding:'utf8',timeout:10000,...options});
}
function fixture(action,{origin='https://github.com/Ullmann27/device-ai.git',packageId=true,regressionFails=false}={}) {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-lokal-gate-'));
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-owner-home-'));
  const record=path.join(os.tmpdir(),`continuum-test-record-${path.basename(root)}`);
  const scripts=path.join(root,'scripts');
  const app=path.join(root,'app');
  fs.mkdirSync(scripts);fs.mkdirSync(app);
  const write=(name,data)=>fs.writeFileSync(path.join(root,name),data);
  const git=(...args)=>{
    const result=cmd('git',args,{cwd:root});
    assert.equal(result.status,0,result.stderr);
    return result.stdout.trim();
  };
  try {
    fs.copyFileSync(source,path.join(scripts,'continuum-fold7-owner-update-gate.sh'));
    write('app/build.gradle.kts',packageId ? 'applicationId = "at.heinz.deviceai.continuumlocal"\n' : 'applicationId = "at.heinz.deviceai.mentorpaired"\n');
    // Mirror the real standalone builder: it owns the *single* pre-sign QA.
    // The owner gate must delegate rather than double-running the costly suite.
    write('scripts/continuum-fold7-standalone-apk.sh',
      '#!/usr/bin/env bash\n# -PcontinuumLocalStandalone=true\n'+
      'cd "$(dirname "$0")/.." || exit 69\n'+
      '[[ "$CONTINUUM_OWNER_LOCAL_APPROVED_SHA" == "$(git rev-parse HEAD)" ]] || exit 69\n'+
      'bash "$(dirname "$0")/continuum-fusion-full-regression.sh" "$CONTINUUM_OWNER_LOCAL_APPROVED_SHA" || exit 69\n'+
      'printf "local-build:%s:%s\\n" "$1" "$2" >> "$TEST_RECORD"\n');
    write('scripts/continuum-fusion-full-regression.sh',
      '#!/usr/bin/env bash\n'+
      '[[ -z "${CONTINUUM_LOCAL_SIGNING_PASSWORD:-}" && -z "${CONTINUUM_LOCAL_SIGNING_FILE:-}" && -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" && -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" ]] || exit 38\n'+
      'printf "full-regression:%s\\n" "$1" >> "$TEST_RECORD"\n'+
      (regressionFails?'exit 9\n':''));
    write('scripts/continuum-fold7-standalone-apk.test.mjs',
      `import test from 'node:test';\nimport assert from 'node:assert/strict';\ntest('synthetic-only contract',()=>assert.equal(true,true));\n`);
    git('init','-q');git('checkout','-qb','research/fixture');git('config','user.name','Gate Fixture');git('config','user.email','gate@example.invalid');
    git('remote','add','origin',origin);
    git('add','app','scripts');git('commit','-qm','synthetic fixture');
    const sha=git('rev-parse','HEAD');
    const keydir=path.join(home,'.local/share/device-ai-continuum/signing');
    const run=(mode,hash=sha,env={})=>cmd('bash',[path.join(scripts,'continuum-fold7-owner-update-gate.sh'),mode,hash],{
      env:{...process.env,HOME:home,TEST_RECORD:record,
        CONTINUUM_OWNER_LOCAL_APPROVED_SHA:'',CONTINUUM_OWNER_NEXT_VERSION_CODE:'',
        CONTINUUM_MENTOR_SIGNING_FILE:'',CONTINUUM_MENTOR_SIGNING_PASSWORD:'',
        CONTINUUM_LOCAL_SIGNING_FILE:'',CONTINUUM_LOCAL_SIGNING_PASSWORD:'',...env}
    });
    const createSigner=(fingerprint=FP)=>{
      fs.mkdirSync(keydir,{recursive:true});
      fs.writeFileSync(path.join(keydir,'continuum-local.p12'),'dummy test bytes, never a real key');
      fs.writeFileSync(path.join(keydir,'continuum-local-cert-sha256.txt'),fingerprint+'\n');
    };
    action({root,record,sha,run,createSigner,home});
  } finally {
    fs.rmSync(root,{recursive:true,force:true});
    fs.rmSync(home,{recursive:true,force:true});
    fs.rmSync(record,{force:true});
  }
}

test('Bash parses and the entry point never mentions the historic builder',()=>{
  assert.equal(cmd('bash',['-n',source]).status,0);
  const script=fs.readFileSync(source,'utf8');
  assert.doesNotMatch(script,/continuum-local-owner-apk\.sh|continuum-local-mainapp-qa\.sh/);
  assert.match(script,/continuum-fold7-standalone-apk\.sh/);
  assert.doesNotMatch(script,/\b(?:adb|pm)\s+(?:install|uninstall|clear)\b/);
});
test('rejects invalid argument combinations before inspecting any keys',()=>{
  for(const args of [[],['owner'],['install','a'.repeat(40)],['qa','b'],['owner','A'.repeat(40)],['qa','a'.repeat(40),'extra']]) {
    assert.equal(cmd('bash',[source,...args]).status,64,args.join(' '));
  }
});
test('pinned Git HEAD rejects mismatches',()=>fixture(({run})=>{
  const r=run('qa','b'.repeat(40));
  assert.equal(r.status,69);assert.match(r.stderr,/Wrong source SHA/);
}));
test('refuses unofficial source remote',()=>fixture(({run})=>{
  const r=run('doctor');assert.equal(r.status,69);assert.match(r.stderr,/Git origin is not/);
},{origin:'https://example.invalid/rogue.git'}));
test('wrong package source is refused before any test',()=>fixture(({run,record})=>{
  const r=run('qa');assert.equal(r.status,69);
  assert.match(r.stderr,/cannot produce the Continuum Lokal package/);
  assert.equal(fs.existsSync(record),false);
},{packageId:false}));
test('doctor is read-only and explicitly identifies Continuum Lokal',()=>fixture(({run,record})=>{
  const r=run('doctor');assert.equal(r.status,0,r.stderr);
  assert.match(r.stdout,/at\.heinz\.deviceai\.continuumlocal/);
  assert.match(r.stdout,/INSTALLABLE=FALSE/);
  assert.equal(fs.existsSync(record),false);
}));
test('QA runs full tests, strips signer material, and never builds an APK',()=>fixture(({run,record,sha})=>{
  const r=run('qa',sha,{CONTINUUM_LOCAL_SIGNING_PASSWORD:'synthetic',CONTINUUM_MENTOR_SIGNING_PASSWORD:'synthetic'});
  assert.equal(r.status,0,r.stderr);
  assert.equal(fs.readFileSync(record,'utf8'),`full-regression:${sha}\n`);
  assert.match(r.stdout,/QA_INSTALLABLE=FALSE/);
}));
test('QA fail-closed on actual regression failure',()=>fixture(({run})=>{
  const r=run('qa');assert.equal(r.status,69);assert.match(r.stderr,/QA contracts failed/);
},{regressionFails:true}));
test('signing refuses absent owner SHA approval and absent P12',()=>fixture(({run,sha,record})=>{
  const first=run('owner',sha,{CONTINUUM_OWNER_NEXT_VERSION_CODE:'27'});
  assert.equal(first.status,69);assert.match(first.stderr,/approval missing/);
  const second=run('owner',sha,{CONTINUUM_OWNER_NEXT_VERSION_CODE:'27',CONTINUUM_OWNER_LOCAL_APPROVED_SHA:sha});
  assert.equal(second.status,69);assert.match(second.stderr,/Original Continuum Lokal P12/);
  assert.equal(fs.existsSync(record),false);
}));
test('mentor signer is never accepted as Continuum Lokal',()=>fixture(({run,createSigner,sha})=>{
  createSigner();const r=run('owner',sha,{CONTINUUM_OWNER_LOCAL_APPROVED_SHA:sha,CONTINUUM_OWNER_NEXT_VERSION_CODE:'27',CONTINUUM_MENTOR_SIGNING_PASSWORD:'legacy'});
  assert.equal(r.status,69);assert.match(r.stderr,/Historic Mentor Pair signer/);
}));
test('certificate pin and versionCode enforce Continuum Lokal identity',()=>fixture(({run,createSigner,sha,record})=>{
  createSigner('f'.repeat(64));
  const env={CONTINUUM_OWNER_LOCAL_APPROVED_SHA:sha,CONTINUUM_OWNER_NEXT_VERSION_CODE:'27'};
  assert.match(run('owner',sha,env).stderr,/certificate pin differs/);
  assert.equal(run('owner',sha,{...env,CONTINUUM_OWNER_NEXT_VERSION_CODE:'26'}).status,69);
  assert.equal(fs.existsSync(record),false);
}));
test('mocked approved owner lane runs full regression before ONLY local standalone build',()=>fixture(({run,createSigner,sha,record})=>{
  createSigner();
  const r=run('owner',sha,{CONTINUUM_OWNER_LOCAL_APPROVED_SHA:sha,CONTINUUM_OWNER_NEXT_VERSION_CODE:'27'});
  assert.equal(r.status,0,r.stderr);
  assert.equal(fs.readFileSync(record,'utf8'),`full-regression:${sha}\nlocal-build:build:27\n`);
  assert.match(r.stdout,/ARTIFACT_NOT_INSTALLED=TRUE/);
}));
test('owner refuses to sign when full regression fails',()=>fixture(({run,createSigner,sha,record})=>{
  createSigner();
  const r=run('owner',sha,{CONTINUUM_OWNER_LOCAL_APPROVED_SHA:sha,CONTINUUM_OWNER_NEXT_VERSION_CODE:'27'});
  assert.equal(r.status,69);assert.match(r.stderr,/Continuum Lokal release build failed/);
  assert.equal(fs.readFileSync(record,'utf8'),`full-regression:${sha}\n`);
},{regressionFails:true}));

test('production branch is refused even with exact source hash',()=>fixture(({root,run})=>{
  const branch=cmd('git',['branch','-M','main'],{cwd:root});
  assert.equal(branch.status,0,branch.stderr);
  const r=run('owner');assert.equal(r.status,69);assert.match(r.stderr,/Production branch/);
}));
