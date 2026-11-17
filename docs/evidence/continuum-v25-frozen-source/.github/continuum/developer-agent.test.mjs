import test from 'node:test';
import assert from 'node:assert/strict';
import {triageRun,summarize} from './developer-agent.mjs';
test('detects failed unit tests without reading or executing logs',()=>{
 const run={id:123,conclusion:'failure',head_sha:'a'.repeat(40),name:'Build'};
 const result=triageRun(run,[{name:'build',steps:[{name:'Run unit tests',conclusion:'failure'}]}]);
 assert.equal(result.category,'TEST_FAILURE');assert.equal(result.failedSteps.length,1);
});
test('successful and pending runs remain separate',()=>{
 const result=summarize([{id:1,conclusion:'success'},{id:2,conclusion:null}]);
 assert.equal(result.successes,1);assert.equal(result.pending,1);
});
test('invalid run IDs fail closed',()=>assert.throws(()=>triageRun({id:'not-a-number'})));

import {command} from './developer-agent.mjs';
import * as B from './development-backlog.mjs';

// watch reads the state branch through the API: a stub stands in for GitHub.
async function watchWith(files,{status={}}={}){
  const head='b'.repeat(40);
  const original=globalThis.fetch;
  globalThis.fetch=async url=>{
    const u=String(url);
    const json=body=>({ok:true,status:200,json:async()=>body,arrayBuffer:async()=>new ArrayBuffer(0)});
    if(u.includes('/contents/')){
      const name=decodeURIComponent(u.split('/contents/')[1].split('?')[0]);
      if(status[name])return {ok:false,status:status[name]};
      if(!files[name])return {ok:false,status:404};
      const bytes=Buffer.from(files[name]);
      return {ok:true,status:200,arrayBuffer:async()=>bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.length)};
    }
    if(u.includes('/branches/'))return json({commit:{sha:head}});
    if(u.includes('/actions/runs'))return json({workflow_runs:[{path:'.github/workflows/android.yml',conclusion:'failure'}]});
    throw Error('unexpected '+u);
  };
  try{return await command(['watch','--branch','dev'],{env:{GITHUB_TOKEN:'t',GITHUB_REPOSITORY:'o/r'},log:()=>{}});}
  finally{globalThis.fetch=original;}
}

test('watch: a failed head unknown to ledger and backlog requests one repair',async()=>{
  const d=await watchWith({});
  assert.equal(d.needsRepair,true);assert.equal(d.alreadyAttempted,false);
});

test('watch: a head the backlog already tracks or a running repair is not repaired again',async()=>{
  let doc=B.emptyDocument(0,'t');
  doc=B.applyOp(doc,{op:'propose',actor:'t',at:1,task:{id:'rep-0000000000000001',title:'R',kind:'REPAIR',source:'repair-loop',subject:{base:'b'.repeat(40)}}}).doc;
  const known=await watchWith({'development-backlog.json':B.encodeDocument(doc)});
  assert.deepEqual([known.needsRepair,known.backlogTask],[false,'rep-0000000000000001']);
  let other=B.emptyDocument(0,'t');
  other=B.applyOps(other,[{op:'propose',actor:'t',at:1,task:{id:'rep-0000000000000002',title:'R',kind:'REPAIR',source:'repair-loop',subject:{base:'c'.repeat(40)}}},
    {op:'accept',actor:'t',at:2,id:'rep-0000000000000002'},
    {op:'claim',actor:'t',at:Date.now(),id:'rep-0000000000000002',owner:'t',token:'1'.repeat(32),leaseMs:3_600_000}]).doc;
  const running=await watchWith({'development-backlog.json':B.encodeDocument(other)});
  assert.deepEqual([running.needsRepair,running.runningRepair],[false,'rep-0000000000000002']);
});

test('watch: a manually retried FAILED repair is eligible again exactly while READY',async()=>{
  let doc=B.emptyDocument(0,'t');
  const id='rep-0000000000000099',token='9'.repeat(32);
  doc=B.applyOps(doc,[
    {op:'propose',actor:'t',at:1,task:{id,title:'Retry',kind:'REPAIR',source:'repair-loop',
      maxAttempts:1,subject:{base:'b'.repeat(40)}}},
    {op:'accept',actor:'t',at:2,id},
    {op:'claim',actor:'t',at:3,id,owner:'t',token,leaseMs:1800000},
    {op:'fail',actor:'t',at:4,id,token,error:{code:'TEST_FAILED',message:'Previously failed'}},
    {op:'retry',actor:'t',at:5,id,reason:'New evidence warrants retry'}
  ]).doc;
  const d=await watchWith({'development-backlog.json':B.encodeDocument(doc)});
  assert.equal(d.backlogTask,id);
  assert.equal(d.alreadyAttempted,false);
  assert.equal(d.needsRepair,true);
  const ledger={schemaVersion:2,attempts:[],experiences:[],
    sessions:[{at:new Date(7).toISOString(),base:'b'.repeat(40),outcome:'NO_VERIFIED_REPAIR'}]};
  const repeated=await watchWith({'development-backlog.json':B.encodeDocument(doc),
    'repair-ledger.json':JSON.stringify(ledger)});
  assert.equal(repeated.needsRepair,false,'recorded retry session must suppress duplicate repair');
  doc=B.applyOp(doc,{op:'claim',actor:'t',at:6,id,owner:'t',token:'8'.repeat(32),leaseMs:1800000}).doc;
  const claimed=await watchWith({'development-backlog.json':B.encodeDocument(doc)});
  assert.equal(claimed.needsRepair,false);
});

test('watch: unreadable or corrupt state fails instead of looking empty',async()=>{
  await assert.rejects(watchWith({},{status:{'development-backlog.json':500}}),/HTTP 500/);
  await assert.rejects(watchWith({'development-backlog.json':'{"schema":"continuum-development-backlog"}'}),/CORRUPT/);
});
