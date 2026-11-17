import test from 'node:test';
import assert from 'node:assert/strict';
import {validWorkspaceBinding,validWorkspaceEvidence} from './isolated-executor.mjs';

test('workspace binding is bounded correlation, never a shell or grant',()=>{
 const binding={workerId:'worker-alpha',taskId:'t-'+'a'.repeat(16),requestMessageId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  requestHash:'b'.repeat(64),sourceCommit:'c'.repeat(40),jobId:'dddddddd-dddd-4ddd-8ddd-dddddddddddd'};
 assert.equal(validWorkspaceBinding(binding),true);
 for(const change of [{workerId:'../../owner'},{sourceCommit:'main'},{taskId:'owner'},{requestHash:null},{command:'rm -rf /'}, {journalId:'self-authorized'}])
  assert.equal(validWorkspaceBinding({...binding,...change}),false);
});

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {bindCcc1Workspace} from './ccc1-workspace.mjs';
import {recover} from './ccc1-recovery.mjs';

import {workspaceFixture} from './ccc1-workspace-fixture.mjs';

test('lookalike executors and self-named journals cannot bind a workspace',()=>{
 assert.throws(()=>bindCcc1Workspace({executor:{forWorkspace(){throw Error('never');}},taskId:'t-'+crypto.randomBytes(8).toString('hex')}),/WORKSPACE_ISOLATION_REQUIRED/);
});

test('canonical recovery preserves pending identity and rejects corrupt/torn journal',()=>{
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'workspace-journal-'));
 try{
  const f=workspaceFixture(home,'worker-unit');
  const result=recover({project:f.root,stateDir:f.stateDir,journal:f.journal});
  assert.equal(result.report.allowedNext,'EXECUTE_PENDING');
  assert.deepEqual(result.report.pending,{request:f.request.messageId,job:f.job,status:'NOT_STARTED'});
  fs.appendFileSync(f.journal.file,'{"torn":');
  assert.throws(()=>f.journal.entries(),/torn last line/);
  assert.equal(recover({project:f.root,stateDir:f.stateDir,journal:f.journal}).report.allowedNext,'EXECUTE_PENDING');
  const raw=fs.readFileSync(f.journal.file,'utf8');fs.writeFileSync(f.journal.file,raw.replace('synthetic isolation','edited isolation'));
  assert.throws(()=>f.journal.entries(),/modified/);
 }finally{fs.rmSync(home,{recursive:true,force:true});}
});

test('bound workspace evidence requires observation and teardown for the exact resource policy',()=>{
 const value={schema:'continuum-isolation-v1',image:'sha256:'+'a'.repeat(64),network:'none',user:'1000:1000',
  readOnly:true,noNewPrivileges:true,capabilities:[],profile:'node-continuum',sourceSha256:'b'.repeat(64),
  untrustedReports:true,memoryMb:256,pids:64,cpus:1,timeoutMs:1000,runtime:'runc',
  workspaceId:'continuum-'+'c'.repeat(24),workspaceBinding:{workerId:'worker-unit',taskId:'t-'+'a'.repeat(16),
   requestMessageId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',requestHash:'b'.repeat(64),sourceCommit:'c'.repeat(40),
   jobId:'dddddddd-dddd-4ddd-8ddd-dddddddddddd'},teardownConfirmed:true,
  observed:{containerId:'d'.repeat(64),runtime:'runc',image:'sha256:'+'a'.repeat(64),user:'1000:1000',network:'none',
   readOnly:true,memoryBytes:268435456,memorySwapBytes:268435456,nanoCpus:1e9,pids:64,capDrop:['ALL'],
   securityOpt:['no-new-privileges:true'],writableHostMounts:0}};
 assert.equal(validWorkspaceEvidence(value,'node-continuum'),true);
 for(const change of [{teardownConfirmed:false},{workspaceId:'self-supplied'},{workspaceBinding:null},
  {observed:{...value.observed,pids:128}},{observed:{...value.observed,writableHostMounts:1}},
  {observed:{...value.observed,runtime:'runsc'}},{observed:{...value.observed,securityOpt:[]}}])
  assert.equal(validWorkspaceEvidence({...value,...change},'node-continuum'),false);
});
