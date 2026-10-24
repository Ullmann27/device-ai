import test from 'node:test';
import assert from 'node:assert/strict';
import os from 'node:os';
import fs from 'node:fs';
import path from 'node:path';
import {assessCcc1Preflight,parseArgs,explainCcc1Findings} from './ccc1-local-preflight.mjs';
const SHA='a'.repeat(40);
const REQUIRED=['ccc1-cli.mjs','ccc1-worker.mjs','ccc1-path-policy.mjs','local-teamleader-loop.mjs'];
function setup(){
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-preflight-'));
  fs.mkdirSync(path.join(root,'.github','continuum'),{recursive:true});
  for(const f of REQUIRED)fs.writeFileSync(path.join(root,'.github','continuum',f),'//fixture\n');
  const state=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-state-'));
  return {root,state,cleanup:()=>{fs.rmSync(root,{recursive:true,force:true});fs.rmSync(state,{recursive:true,force:true});}};
}
function git(root,commands){
  if(commands[0]==='rev-parse'&&commands[1]==='--show-toplevel')return root+'\n';
  if(commands[0]==='rev-parse')return SHA+'\n';
  if(commands[0]==='branch')return 'experiment/owner-review\n';
  if(commands[0]==='status')return '';
  throw Error('Unexpected git args '+commands.join(' '));
}
test('read-only clean isolated local preflight is not an execution authorization',()=>{
  const f=setup();try{
    const r=assessCcc1Preflight({project:f.root,stateDir:f.state,runGit:git});
    assert.equal(r.result,'READY_TO_REQUEST_OWNER_REVIEW');
    assert.equal(r.sourceCommit,SHA);
    assert.equal(r.workerExecuted,false);assert.equal(r.patchVerified,false);assert.equal(r.ownerApproved,false);
  } finally{f.cleanup();}
});
test('rejects source-internal state including symlink redirections',()=>{
  const f=setup();try{
    const link=path.join(f.state,'alias');fs.symlinkSync(f.root,link,'dir');
    for(const target of [path.join(f.root,'state'),path.join(link,'state')]){
      const r=assessCcc1Preflight({project:f.root,stateDir:target,runGit:git});
      assert.equal(r.result,'BLOCKED');assert.ok(r.findings.includes('CCC1_STATE_INSIDE_SOURCE'));
    }
  } finally{f.cleanup();}
});
test('rejects main branch, dirty tree and absent CCC1 CLI',()=>{
  const f=setup();try{
    fs.unlinkSync(path.join(f.root,'.github','continuum','ccc1-cli.mjs'));
    const r=assessCcc1Preflight({project:f.root,stateDir:f.state,runGit:(root,args)=>
      args[0]==='branch'?'main\n':args[0]==='status'?' M README.md\n':git(root,args)});
    assert.equal(r.result,'BLOCKED');assert.ok(r.findings.includes('CCC1_BRANCH_NOT_ISOLATED'));
    assert.ok(r.findings.includes('CCC1_WORKTREE_NOT_CLEAN'));
    assert.ok(r.findings.includes('CCC1_REQUIRED_FILE_MISSING:.github/continuum/ccc1-cli.mjs'));
  }finally{f.cleanup();}
});
test('rejects invalid state path and non-directory parent',()=>{
  const f=setup();try{
    const file=path.join(f.state,'existing-file');fs.writeFileSync(file,'file');
    const r=assessCcc1Preflight({project:f.root,stateDir:path.join(file,'newdir'),runGit:git});
    assert.equal(r.result,'BLOCKED');assert.ok(r.findings.includes('CCC1_STATE_NOT_DIRECTORY'));
    assert.throws(()=>parseArgs(['--project','x','--project','y']),/ARGUMENTS_INVALID/);
  }finally{f.cleanup();}
});
test('every preflight finding gets one owner hint with the concrete next step, codes are never dropped', () => {
  const hints = explainCcc1Findings(['CCC1_BRANCH_NOT_ISOLATED', 'CCC1_WORKTREE_NOT_CLEAN',
    'CCC1_REQUIRED_FILE_MISSING:.github/continuum/ccc1-cli.mjs', 'CCC1_REQUIRED_FILE_MISSING:.github/continuum/ccc1-worker.mjs',
    'CCC1_NODE_VERSION_UNSUPPORTED', 'CCC1_STATE_INSIDE_SOURCE', 'CCC1_GIT_ROOT_UNVERIFIED']);
  assert.equal(hints.length, 6, 'one hint per kind of finding');
  assert.match(hints[0], /ccc1-prepare/);
  assert.match(hints[1], /git -C ~\/device-ai status --short/);
  assert.match(hints[2], /zu alt/);
  assert.match(hints[3], /pkg upgrade nodejs/);
  assert.match(hints[4], /~\/\.local\/state\/device-ai-continuum\/ccc1/);
  assert.match(hints[5], /^CCC1_GIT_ROOT_UNVERIFIED: /);
  assert.deepEqual(explainCcc1Findings([]), []);
});
