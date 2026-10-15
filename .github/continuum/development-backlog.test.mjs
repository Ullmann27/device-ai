import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn} from 'node:child_process';
import * as B from './development-backlog.mjs';
import {FIXTURE_DIR,conformance,runCase,fixtureFiles,roundtripPrepare,roundtripSimulateKotlin,roundtripVerify} from './backlog-fixtures.mjs';

const fixture=name=>fs.readFileSync(path.join(FIXTURE_DIR,name));
const codeOf=fn=>{try{fn();return null;}catch(e){if(e instanceof B.BacklogError)return e.code;throw e;}};
const withDir=fn=>{const dir=fs.mkdtempSync(path.join(os.tmpdir(),'backlog-test-'));try{return fn(dir);}finally{fs.rmSync(dir,{recursive:true,force:true});}};
const propose=(id,at=1)=>({op:'propose',actor:'test',at,task:{id,title:'Aufgabe '+id,kind:'TEST',source:'cli'}});

test('conformance vectors: every step and final state as recorded (shared with Kotlin)',()=>{
  const recorded=JSON.parse(fixture('conformance.json'));
  assert.equal(recorded.cases.length,5);
  for(const c of recorded.cases){
    const {outcomes,doc}=runCase(c);
    assert.deepEqual(outcomes,c.steps.map(s=>s.expect),c.name);
    assert.equal(doc.revision,c.final.revision,c.name);
    assert.equal(B.integrityOf(doc),c.final.integrity,c.name);
    assert.deepEqual(Object.fromEntries(doc.tasks.map(t=>[t.id,t.state])),c.final.states,c.name);
  }
});

test('committed fixtures are exactly what the current rules produce',()=>{
  for(const [name,bytes] of Object.entries(fixtureFiles()))
    assert.ok(bytes.equals(fixture(name)),name+' is stale: run node .github/continuum/backlog-fixtures.mjs write');
  assert.deepEqual(conformance(),JSON.parse(fixture('conformance.json')));
});

test('documents round-trip byte-identically and unknown fields survive operations',()=>{
  for(const name of ['valid-v2.json','unknown-fields.json','v1-migrated.json','roundtrip-node.json']){
    const bytes=fixture(name);
    assert.ok(B.encodeDocument(B.decodeDocument(bytes).doc).equals(bytes),name);
  }
  const {doc}=B.decodeDocument(fixture('unknown-fields.json'));
  const next=B.applyOp(doc,{op:'setPriority',actor:'cli',at:70000,id:'task-beta',priority:5}).doc;
  const again=B.decodeDocument(B.encodeDocument(next)).doc;
  assert.deepEqual(again.futureFeature,{flags:[1,'x',true,null],nested:{k:'v'}});
  assert.equal(again.tasks.find(t=>t.id==='task-gamma').note,'bleibt erhalten');
  assert.equal(again.revision,doc.revision+1);
});

test('invalid documents fail with the shared error codes',()=>{
  const cases=JSON.parse(fixture('invalid.json'));
  assert.ok(cases.length>=15);
  for(const c of cases)assert.equal(codeOf(()=>B.decodeDocument(fixture(c.file))),c.code,c.file);
});

test('development-planner output (schema 1) migrates without losing data',()=>{
  const v1=fixture('v1-planner.json');
  assert.equal(codeOf(()=>B.decodeDocument(v1)),'UNSUPPORTED_VERSION');
  const {doc,migratedFrom}=B.decodeDocument(v1,{migrate:{at:1700000000000,actor:'migration'}});
  assert.equal(migratedFrom,1);
  assert.ok(B.encodeDocument(doc).equals(fixture('v1-migrated.json')));
  const planner=JSON.parse(v1);
  assert.equal(doc.tasks.length,planner.tasks.length);
  for(const p of planner.tasks){
    const t=doc.tasks.find(x=>x.id===B.findingTaskId(p.kind,B.repoPath(p.file),p.symbol));
    assert.ok(t,'missing '+p.symbol);
    assert.deepEqual([t.state,t.subject.score,t.subject.action,t.subject.confidence],['PROPOSED',p.score,p.action,p.confidence]);
  }
  assert.equal(doc.migratedFrom.analyzedAt,planner.analyzedAt);
});

test('a task is never DONE without matching executed-test evidence, also when forged in a file',()=>{
  let doc=B.emptyDocument(0,'t');
  const run=op=>{doc=B.applyOp(doc,{actor:'t',...op}).doc;};
  run({op:'propose',at:1,task:{id:'task-x',title:'X',kind:'TEST',source:'cli'}});
  run({op:'accept',at:2,id:'task-x'});
  const token='1'.repeat(32);
  run({op:'claim',at:3,id:'task-x',owner:'w',token,leaseMs:60000});
  assert.equal(codeOf(()=>B.applyOp(doc,{op:'complete',actor:'t',at:4,id:'task-x',evidence:{}})),'INVALID_TRANSITION','not yet submitted');
  run({op:'submit',at:4,id:'task-x',token,artifact:{type:'PATCH',sha256:'2'.repeat(64)}});
  const good={type:'TEST_RUN',taskId:'task-x',submissionToken:token,artifactSha256:'2'.repeat(64),chain:'android',commit:'3'.repeat(40),
    profiles:[{profile:'android-compile',passed:true,executed:0,failed:0,skipped:0},{profile:'android-unit',passed:true,executed:412,failed:0,skipped:0}],repair:null,at:5,by:'ci'};
  for(const bad of [{...good,taskId:'task-y'},{...good,submissionToken:'4'.repeat(32)},{...good,profiles:[good.profiles[0]]},
    {...good,profiles:[{...good.profiles[1],executed:0}]},{...good,profiles:[{...good.profiles[1],failed:1}]}])
    assert.equal(codeOf(()=>B.applyOp(doc,{op:'complete',actor:'t',at:5,id:'task-x',evidence:bad})),'EVIDENCE_REJECTED');
  run({op:'complete',at:5,id:'task-x',evidence:good});
  assert.equal(doc.tasks[0].state,'DONE');
  // the same rule on load: strip the evidence of the DONE task and re-hash
  const forged=structuredClone(doc);forged.tasks[0].evidence=[];
  const {integrity,...rest}=forged;
  assert.equal(codeOf(()=>B.decodeDocument(Buffer.from(B.canonical({...rest,integrity:B.integrityOf(rest)})+'\n'))),'CORRUPT');
});

test('canonical JSON escaping and ordering equal JSON.stringify for valid strings',()=>{
  const s='\u0000\u0001\b\t\n\u000b\f\r\u001f\u007f "\\/é😀';
  assert.equal(B.canonical(s),JSON.stringify(s));
  assert.equal(B.canonical({b:1,a:[true,null,-0,'x'],A:{},'é':'',aa:0}),'{"A":{},"a":[true,null,0,"x"],"aa":0,"b":1,"é":""}');
  assert.equal(codeOf(()=>B.canonical(1.5)),'INVALID_FIELD');
  assert.equal(codeOf(()=>B.canonical('\ud800')),'INVALID_FIELD');
  assert.equal(B.clip('ab😀',3),'ab');
  assert.equal(codeOf(()=>B.parseStrict('{"a":1,"a":2}')),'CORRUPT');
  assert.equal(codeOf(()=>B.parseStrict('{"a":"\\u00e9","b":"\\u０041"}')),'CORRUPT');
});

// ---------------------------------------------------------------------------
// File store
// ---------------------------------------------------------------------------
test('store writes atomically, keeps a backup and does not rewrite unchanged content',()=>withDir(dir=>{
  const store=new B.BacklogStore(path.join(dir,'backlog.json'),{actor:'test',clock:()=>5});
  assert.equal(store.apply([propose('task-one')]).doc.revision,1);
  assert.equal(store.apply([propose('task-two',2)]).doc.revision,2);
  assert.equal(B.decodeDocument(fs.readFileSync(path.join(dir,'backlog.json.bak'))).doc.revision,1);
  assert.equal(store.update(d=>d).written,false);
  assert.deepEqual(fs.readdirSync(dir).sort(),['backlog.json','backlog.json.bak']);
}));

test('a corrupt backlog is recovered from the backup and quarantined, never deleted',()=>withDir(dir=>{
  const file=path.join(dir,'backlog.json');
  const store=new B.BacklogStore(file,{actor:'test'});
  store.apply([propose('task-one')]);store.apply([propose('task-two',2)]);
  const damaged=fs.readFileSync(file);damaged[damaged.length>>1]^=0x20;fs.writeFileSync(file,damaged);
  const read=store.read();
  assert.ok(read.recovered);assert.equal(read.recovered.quarantined,null);
  assert.ok(fs.readFileSync(file).equals(damaged),'read() never modifies files');
  const r=store.apply([propose('task-three',3)]);
  assert.ok(fs.readFileSync(r.recovered.quarantined).equals(damaged));
  assert.deepEqual(r.doc.tasks.map(t=>t.id),['task-one','task-three']);
}));

test('without a valid backup a corrupt backlog fails closed and nothing is touched',()=>withDir(dir=>{
  const file=path.join(dir,'backlog.json');
  fs.writeFileSync(file,'{"schema":"continuum-development-backlog"');
  const store=new B.BacklogStore(file,{actor:'test'});
  assert.equal(codeOf(()=>store.read()),'CORRUPT');
  assert.equal(codeOf(()=>store.apply([propose('task-one')])),'CORRUPT');
  assert.deepEqual(fs.readdirSync(dir),['backlog.json']);
  // a newer schema is never overwritten or "recovered" away
  fs.writeFileSync(file,fixture('invalid-future-version.json'));fs.writeFileSync(file+'.bak',fixture('valid-v2.json'));
  assert.equal(codeOf(()=>store.apply([propose('task-one')])),'UNSUPPORTED_VERSION');
  assert.ok(fs.readFileSync(file).equals(fixture('invalid-future-version.json')));
}));

test('revision conflicts and rejected batches write nothing; lenient batches report every rejection',()=>withDir(dir=>{
  const file=path.join(dir,'backlog.json');
  const store=new B.BacklogStore(file,{actor:'test'});
  const bad=[propose('task-one'),{op:'accept',actor:'test',at:2,id:'task-missing'}];
  assert.equal(codeOf(()=>store.apply(bad)),'NOT_FOUND');
  assert.equal(fs.existsSync(file),false);
  const lenient=store.apply(bad,{stopOnError:false});
  assert.deepEqual(lenient.results.map(r=>r.ok?'ok':r.code),['ok','NOT_FOUND']);
  const before=fs.readFileSync(file);
  assert.equal(codeOf(()=>store.apply([propose('task-two')],{expectedRevision:0})),'CONFLICT');
  assert.ok(fs.readFileSync(file).equals(before));
}));

test('a fresh foreign lock times out, a stale one is broken; interrupted writes are cleaned',()=>withDir(dir=>{
  const file=path.join(dir,'backlog.json'),lock=file+'.lock';
  fs.writeFileSync(lock,'{"token":"someone-else"}\n');
  const store=new B.BacklogStore(file,{actor:'test',waitMs:200,staleLockMs:60000});
  assert.equal(codeOf(()=>store.apply([propose('task-one')])),'LOCK_TIMEOUT');
  assert.ok(fs.existsSync(lock));
  const old=new Date(Date.now()-120000);fs.utimesSync(lock,old,old);
  assert.equal(store.apply([propose('task-one')]).written,true);
  assert.equal(fs.existsSync(lock),false);
  const leftover=file+'.tmp-deadbeef';fs.writeFileSync(leftover,'{"half":');fs.utimesSync(leftover,old,old);
  assert.equal(store.read().doc.revision,1);
  store.apply([propose('task-two',2)]);
  assert.equal(fs.existsSync(leftover),false);
}));

test('concurrent writer processes lose no update',async()=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'backlog-procs-'));
  try{
    const file=path.join(dir,'backlog.json');
    const moduleUrl=new URL('./development-backlog.mjs',import.meta.url).href;
    const script=`import {BacklogStore} from ${JSON.stringify(moduleUrl)};
      const [file,w]=process.argv.slice(1);const s=new BacklogStore(file,{actor:'writer-'+w,waitMs:60000});
      for(let k=0;k<4;k++)s.apply([{op:'propose',actor:'writer-'+w,at:k,task:{id:'task-'+w+'-'+k,title:'T',kind:'TEST',source:'cli'}}]);`;
    const runs=[...Array(5).keys()].map(w=>new Promise((resolve,reject)=>{
      const env={...process.env};delete env.NODE_TEST_CONTEXT;
      const p=spawn(process.execPath,['--input-type=module','-e',script,file,String(w)],{env,stdio:['ignore','ignore','pipe']});
      let err='';p.stderr.on('data',d=>{err+=d;});
      p.on('exit',code=>code===0?resolve():reject(Error('writer '+w+' failed: '+err)));
    }));
    await Promise.all(runs);
    const doc=new B.BacklogStore(file,{actor:'reader'}).read().doc;
    assert.equal(doc.tasks.length,20);assert.equal(doc.revision,20);
    assert.equal(fs.existsSync(file+'.lock'),false);
  }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('a planner v1 file is migrated on the first write and the original is kept',()=>withDir(dir=>{
  const file=path.join(dir,'backlog.json');
  fs.writeFileSync(file,fixture('v1-planner.json'));
  const store=new B.BacklogStore(file,{actor:'migration',clock:()=>1700000000000});
  assert.equal(store.read().migratedFrom,1);
  assert.ok(fs.readFileSync(file).equals(fixture('v1-planner.json')),'reading never rewrites');
  const r=store.update(d=>d);
  assert.equal(r.written,true);assert.equal(r.migratedFrom,1);
  assert.ok(fs.readFileSync(file).equals(fixture('v1-migrated.json')));
  assert.ok(fs.readFileSync(file+'.v1.json').equals(fixture('v1-planner.json')));
}));

test('round-trip verifier accepts a correct Kotlin half and rejects a missing or wrong one',()=>withDir(dir=>{
  roundtripPrepare(dir);
  assert.ok(fs.readFileSync(path.join(dir,'backlog.json')).equals(fixture('roundtrip-node.json')),'Node half is deterministic');
  assert.throws(()=>roundtripVerify(dir),/ENOENT/,'no Kotlin output is a failure, not a pass');
  roundtripSimulateKotlin(dir);
  assert.deepEqual(roundtripVerify(dir).problems,[]);
  // tampering with what "Kotlin" wrote is detected
  const other=fs.mkdtempSync(path.join(os.tmpdir(),'backlog-rt-'));
  try{
    roundtripPrepare(other);roundtripSimulateKotlin(other);
    const state=JSON.parse(fs.readFileSync(path.join(other,'kotlin-state.json'),'utf8'));
    fs.writeFileSync(path.join(other,'kotlin-state.json'),JSON.stringify({...state,revision:state.revision-1}));
    fs.writeFileSync(path.join(other,'kotlin-ops.json'),'[]');
    assert.ok(roundtripVerify(other).problems.length>0);
  }finally{fs.rmSync(other,{recursive:true,force:true});}
}));
