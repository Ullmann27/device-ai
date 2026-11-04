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

function selectionDocument(overrides){
  const template=B.applyOp(B.emptyDocument(0,'test'),propose('task-template')).doc.tasks[0];
  const tasks=overrides.map(task=>({...structuredClone(template),state:'READY',...task}));
  tasks.sort((a,b)=>a.id<b.id?-1:a.id>b.id?1:0);
  const doc={...B.emptyDocument(1000,'test'),tasks};
  B.validateDocument(doc);
  const freeze=value=>{
    if(value!==null&&typeof value==='object'){
      for(const child of Object.values(value))freeze(child);
      Object.freeze(value);
    }
    return value;
  };
  return freeze(doc);
}

test('nextEligible retains task identity and priority, oldest-first and id tie breaks without mutation',()=>{
  const doc=selectionDocument([
    {id:'task-aaa',priority:999,createdAt:0},
    {id:'task-bbb',priority:1000,createdAt:1},
    {id:'task-ccc',priority:1000,createdAt:0},
    {id:'task-ddd',priority:1000,createdAt:0}
  ]);
  const before=B.canonical(doc);
  assert.strictEqual(B.nextEligible(doc),doc.tasks[2]);
  assert.equal(B.canonical(doc),before);
  assert.strictEqual(B.nextEligible(selectionDocument([])),null);
  const single=selectionDocument([{id:'task-only'}]);
  assert.strictEqual(B.nextEligible(single),single.tasks[0]);
});

test('nextEligible combines every optional scope and respects empty filters and null subjects',()=>{
  const doc=selectionDocument([
    {id:'task-aaa',priority:1000,kind:'REPAIR',source:'owner',subject:{base:'old',operation:'REPAIR'}},
    {id:'task-bbb',priority:900,kind:'TEST',source:'owner',subject:{base:'head',operation:'ASSERT_NODE_EXPORT'}},
    {id:'task-ccc',priority:800,kind:'TEST',source:'cli',subject:{base:'head',operation:'ASSERT_NODE_EXPORT'}},
    {id:'task-ddd',priority:700,kind:'TEST',source:'owner',subject:{base:'head',operation:'OTHER'}},
    {id:'task-eee',priority:600,kind:'TEST',source:'owner',subject:null}
  ]);
  const choose=(index,...filters)=>assert.strictEqual(B.nextEligible(doc,...filters),index===null?null:doc.tasks[index]);
  choose(0);choose(1,['TEST']);choose(2,null,['cli']);choose(1,null,null,'head');
  choose(4,null,null,null,'task-eee');choose(3,null,null,null,null,'OTHER');
  choose(1,['TEST'],['owner'],'head','task-bbb','ASSERT_NODE_EXPORT');
  choose(null,['TEST'],['owner'],'head','task-ccc','ASSERT_NODE_EXPORT');
  choose(null,[]);choose(null,null,[]);choose(null,['UNKNOWN']);choose(null,null,['unknown']);
  choose(null,null,null,'missing');choose(null,null,null,null,'task-missing');
  choose(null,null,null,null,null,'MISSING');
  choose(null,null,null,'head','task-eee');
});

test('nextEligible checks eligibility before kinds and sources, retaining filter short circuit order',()=>{
  const doc=selectionDocument([
    {id:'task-aaa',state:'PROPOSED',kind:'PROPOSED'},
    {id:'task-bbb',kind:'WAITING',dependsOn:['task-aaa']},
    {id:'task-ccc',kind:'EXHAUSTED',attempts:3},
    {id:'task-ddd',kind:'REPAIR',source:'cli'},
    {id:'task-eee',kind:'TEST',source:'owner'}
  ]);
  const calls=[],kinds=['TEST'],sources=['owner'];
  kinds.includes=value=>{calls.push('kind:'+value);return Array.prototype.includes.call(kinds,value);};
  sources.includes=value=>{calls.push('source:'+value);return Array.prototype.includes.call(sources,value);};
  assert.strictEqual(B.nextEligible(doc,kinds,sources),doc.tasks[4]);
  assert.deepEqual(calls,['kind:REPAIR','kind:TEST','source:owner']);
  assert.strictEqual(B.nextEligible(doc,['PROPOSED','WAITING','EXHAUSTED']),null);
});

test('nextEligible releases dependent tasks only after matching completed-task evidence',()=>{
  let doc=B.emptyDocument(0,'test');
  const run=op=>{doc=B.applyOp(doc,{actor:'test',...op}).doc;};
  run({...propose('task-dependency'),at:1});run({op:'accept',at:2,id:'task-dependency'});
  run({op:'propose',at:3,task:{id:'task-waiting',title:'Waiting',kind:'TEST',source:'owner',priority:1000,dependsOn:['task-dependency']}});
  run({op:'accept',at:4,id:'task-waiting'});
  const token='a'.repeat(32),sha='b'.repeat(64);
  run({op:'claim',at:5,id:'task-dependency',owner:'test',token,leaseMs:60000});
  assert.strictEqual(B.nextEligible(doc),null);
  run({op:'submit',at:6,id:'task-dependency',token,artifact:{type:'REPORT',sha256:sha}});
  assert.strictEqual(B.nextEligible(doc),null);
  run({op:'complete',at:7,id:'task-dependency',evidence:{type:'TEST_RUN',taskId:'task-dependency',
    submissionToken:token,artifactSha256:sha,chain:'test',commit:'c'.repeat(40),at:7,by:'test',repair:null,
    profiles:[{profile:'node-continuum',passed:true,executed:1,failed:0,skipped:0}]}});
  assert.strictEqual(B.nextEligible(doc),doc.tasks[1]);
});

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

test('stability backlog completion rejects a backend profile with zero executed tests',()=>{
  let doc=B.emptyDocument(0,'t');
  const run=op=>{doc=B.applyOp(doc,{actor:'t',...op}).doc;};
  const task='task-backend-zero',token='a'.repeat(32),digest='b'.repeat(64);
  run({op:'propose',at:1,task:{id:task,title:'Backend evidence',kind:'REPAIR',source:'cli'}});
  run({op:'accept',at:2,id:task});
  run({op:'claim',at:3,id:task,owner:'t',token,leaseMs:60000});
  run({op:'submit',at:4,id:task,token,artifact:{type:'PATCH',sha256:digest}});
  const good={type:'TEST_RUN',taskId:task,submissionToken:token,artifactSha256:digest,
    chain:'stability',commit:'c'.repeat(40),at:5,by:'t',repair:null,
    profiles:[
      {profile:'node-continuum',passed:true,executed:10,failed:0,skipped:0},
      {profile:'node-backend',passed:true,executed:5,failed:0,skipped:0}
    ]};
  const complete=evidence=>B.applyOp(doc,{op:'complete',actor:'t',at:5,id:task,evidence});
  assert.equal(codeOf(()=>complete({...good,profiles:[
    good.profiles[0],{...good.profiles[1],executed:0}
  ]})),'EVIDENCE_REJECTED');
  assert.equal(complete(good).doc.tasks.find(t=>t.id===task).state,'DONE');
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

test('stale backlog lock is not stolen while same-host PID is still alive',()=>withDir(dir=>{
  const file=path.join(dir,'backlog.json'),lock=file+'.lock';
  const old=new Date(Date.now()-120000);
  fs.writeFileSync(lock,JSON.stringify({
    owner:'test',pid:process.pid,host:os.hostname().replace(/[^\w.-]/g,'').slice(0,64)||'host',
    token:'a'.repeat(32),at:Date.now()-120000
  })+'\n');
  fs.utimesSync(lock,old,old);
  const store=new B.BacklogStore(file,{actor:'test',waitMs:120,staleLockMs:1000});
  assert.equal(codeOf(()=>store.apply([propose('must-not-steal')])),'LOCK_TIMEOUT');
  assert.ok(fs.existsSync(lock));
  assert.equal(fs.existsSync(file),false);
  fs.rmSync(lock);
  fs.writeFileSync(lock,JSON.stringify({
    owner:'test',pid:2147483647,host:os.hostname().replace(/[^\w.-]/g,'').slice(0,64)||'host',
    token:'b'.repeat(32),at:Date.now()-120000
  })+'\n');
  fs.utimesSync(lock,old,old);
  assert.equal(store.apply([propose('safe-recovery')]).written,true);
  assert.equal(fs.existsSync(lock),false);
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
