import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {createIsolatedExecutor,isIsolatedExecutor,loadIsolationConfig,validIsolationEvidence} from './isolated-executor.mjs';
import {runChain} from './repair-loop.mjs';
import {emptyLedger,recordSession} from './repair-ledger.mjs';

test('untrusted executor objects cannot grant execution rights',()=>{
 const fake={execute(){throw Error('must never run');}};
 assert.equal(isIsolatedExecutor(fake),false);
 assert.throws(()=>runChain('node',process.cwd(),'/tmp',{executor:fake}),/UNTRUSTED_ISOLATION_EXECUTOR/);
});
test('mutable images and unbounded resource policies are rejected before daemon access',()=>{
 for(const config of [{image:'latest'},{image:'sha256:'+'a'.repeat(64),pids:1000},{image:'sha256:'+'a'.repeat(64),timeoutMs:Infinity}])
  assert.throws(()=>createIsolatedExecutor(config),/ISOLATION_INVALID_POLICY/);
});
test('receipts with network, privilege, missing limits or missing snapshot are invalid',()=>{
 const receipt={schema:'continuum-isolation-v1',image:'sha256:'+'a'.repeat(64),network:'none',user:'1000:1000',readOnly:true,
  noNewPrivileges:true,capabilities:[],profile:'node-continuum',sourceSha256:'b'.repeat(64),untrustedReports:true,memoryMb:512,pids:32,cpus:1,timeoutMs:1000};
 assert.equal(validIsolationEvidence(receipt,'node-continuum'),true);
 for(const change of [{network:'host'},{user:'0:0'},{readOnly:false},{capabilities:['SYS_ADMIN']},{sourceSha256:null},{pids:1000},{untrustedReports:false}])
  assert.equal(validIsolationEvidence({...receipt,...change},'node-continuum'),false);
 assert.equal(validIsolationEvidence(receipt,'android-unit'),false);
});
test('configuration cannot supply shell commands or symlinked owner data',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-isolation-config-'));
 try{
  const file=path.join(dir,'config.json');fs.writeFileSync(file,JSON.stringify({command:'arbitrary shell'}));
  assert.throws(()=>loadIsolationConfig(file),/UNKNOWN_CONFIG_OPTION/);
  fs.writeFileSync(file,'{}');fs.symlinkSync(file,path.join(dir,'link'));
  assert.throws(()=>loadIsolationConfig(path.join(dir,'link')),/UNSAFE_CONFIG/);
  assert.throws(()=>loadIsolationConfig(file,{sourceRoot:dir}),/CONFIG_MUST_BE_EXTERNAL/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('broad model reports never become learned success even with a true independent flag',()=>{
 const report={outcome:'VERIFIED',base:'a'.repeat(40),chain:'node',verified:{strategy:'LOCAL_ISOLATED_PATCH',patchSha256:'b'.repeat(64)},
  attempts:[{outcome:'VERIFIED',strategy:'LOCAL_ISOLATED_PATCH',candidate:'a'.repeat(16),diagnostic:'b'.repeat(16),diagnosticKind:'JS_ASSERTION',edits:[],tests:[]}]};
 const ledger=recordSession(emptyLedger(),report,{independentPass:true});
 assert.equal(ledger.sessions[0].outcome,'PENDING_OWNER_REVIEW');
 assert.deepEqual(ledger.experiences,[]);assert.equal(ledger.attempts[0].patchSha256,null);
});
