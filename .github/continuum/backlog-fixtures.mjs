import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import * as B from './development-backlog.mjs';

/**
 * Shared fixtures of the development backlog. Node (development-backlog.test.mjs)
 * and Kotlin (DevelopmentBacklogTest.kt) must produce exactly these results.
 * Regenerate after an intentional format or rule change:
 *   node .github/continuum/backlog-fixtures.mjs write
 * Cross-runtime round trip (CI: Node writes, Kotlin changes, Node verifies):
 *   node .github/continuum/backlog-fixtures.mjs roundtrip-prepare <dir>
 *   CONTINUUM_BACKLOG_ROUNDTRIP_DIR=<dir> gradle :app:testDebugUnitTest
 *   node .github/continuum/backlog-fixtures.mjs roundtrip-verify <dir>
 */
export const FIXTURE_DIR=fileURLToPath(new URL('../../app/src/test/resources/continuum/backlog/',import.meta.url));

const T=n=>n.toString(16).padStart(32,'0');
const S=c=>c.repeat(64);
const COMMIT='a'.repeat(40);
const step=(op,expect='ok')=>({op,expect});

export function evidence(taskId,token,artifact,{at,executed=12,passed=true,failed=0,profile='node-continuum',repair=null}){
  return {type:'TEST_RUN',taskId,submissionToken:token,artifactSha256:artifact,chain:'node',commit:COMMIT,
    profiles:[{profile,passed,executed,failed,skipped:0}],repair,at,by:'ci-runner'};
}

function lifecycle(){
  const a='task-alpha',b='task-beta',c='task-gamma',u='cli';
  return {name:'lifecycle with evidence, leases and rejection',start:{at:1000,actor:'fixture'},steps:[
    step({op:'propose',actor:u,at:1100,task:{id:a,title:'Reparatur: Größe prüfen – 🚀 schnell',kind:'REPAIR',source:'cli',priority:900}}),
    step({op:'propose',actor:'android-app',at:1110,task:{id:b,title:'Integrationstest für Alpha (zweite Zeile)',kind:'TEST',source:'android',dependsOn:[a]}}),
    step({op:'propose',actor:'code-analyzer',at:1120,task:{id:c,title:'Investigate CognitiveRaidPlanner',kind:'INVESTIGATE',source:'code-analyzer',priority:230,
      subject:{file:'app/src/main/java/x/Y.kt',line:12,confirmed:false}},reason:'finding TEST_ONLY'}),
    step({op:'accept',actor:'heinz',at:1130,id:a}),
    step({op:'accept',actor:'heinz',at:1140,id:b,reason:'passt'}),
    step({op:'accept',actor:'heinz',at:1150,id:c}),
    step({op:'claim',actor:'worker-1',at:1200,id:b,owner:'worker-1',token:T(2),leaseMs:60000},'DEPENDENCY_NOT_DONE'),
    step({op:'claimNext',actor:'worker-1',at:1300,owner:'worker-1',token:T(1),leaseMs:60000}),
    step({op:'renew',actor:'worker-1',at:1350,id:a,token:T(9),leaseMs:60000},'TOKEN_MISMATCH'),
    step({op:'renew',actor:'worker-1',at:1400,id:a,token:T(1),leaseMs:120000}),
    step({op:'submit',actor:'worker-1',at:1500,id:a,token:T(1),artifact:{type:'PATCH',sha256:S('b')}}),
    step({op:'complete',actor:'ci',at:1600,id:a,evidence:evidence(a,T(1),S('b'),{at:1600,executed:0})},'EVIDENCE_REJECTED'),
    step({op:'complete',actor:'ci',at:1600,id:a,evidence:evidence(a,T(1),S('c'),{at:1600})},'EVIDENCE_REJECTED'),
    step({op:'complete',actor:'ci',at:1600,id:a,evidence:evidence(a,T(1),S('b'),{at:1400})},'EVIDENCE_REJECTED'),
    step({op:'complete',actor:'ci',at:1600,id:a,evidence:evidence(a,T(1),S('b'),{at:1600,failed:1,passed:false})},'EVIDENCE_REJECTED'),
    step({op:'complete',actor:'ci',at:1600,id:a,evidence:{...evidence(a,T(1),S('b'),{at:1600}),commit:'nope'}},'INVALID_FIELD'),
    step({op:'complete',actor:'ci',at:1700,id:a,evidence:evidence(a,T(1),S('b'),{at:1700,executed:95,
      repair:{diagnostic:'0123456789abcdef',candidate:'fedcba9876543210',strategy:'JS_TYPO_REFERENCE',patchSha256:S('b')}})}),
    step({op:'claim',actor:'worker-2',at:1800,id:b,owner:'worker-2',token:T(2),leaseMs:60000}),
    step({op:'fail',actor:'worker-2',at:1900,id:b,token:T(2),error:{code:'TEST_FAILURE',message:'2 Tests rot'}}),
    step({op:'claim',actor:'worker-2',at:2000,id:b,owner:'worker-2',token:T(3),leaseMs:60000}),
    step({op:'expire',actor:'android-app',at:62001}),
    step({op:'expire',actor:'android-app',at:62002}),
    step({op:'renew',actor:'worker-2',at:62050,id:b,token:T(3),leaseMs:60000},'INVALID_TRANSITION'),
    step({op:'setPriority',actor:'heinz',at:62100,id:c,priority:50}),
    step({op:'setPriority',actor:'heinz',at:62200,id:c,priority:50}),
    step({op:'reject',actor:'heinz',at:62300,id:c,reason:'kein Handlungsbedarf'}),
    step({op:'reject',actor:'heinz',at:62310,id:c},'INVALID_TRANSITION'),
    step({op:'abort',actor:'heinz',at:62320,id:a,reason:'zu spät'},'INVALID_TRANSITION'),
    step({op:'retry',actor:'heinz',at:62330,id:b,reason:'noch einmal'},'INVALID_TRANSITION'),
    step({op:'claim',actor:'worker-2',at:62400,id:b,owner:'worker-2',token:T(4),leaseMs:60000}),
    step({op:'submit',actor:'worker-2',at:62500,id:b,token:T(4),artifact:{type:'PROPOSAL',sha256:S('d')}}),
    step({op:'fail',actor:'reviewer',at:62600,id:b,token:T(4),error:{code:'REVIEW_REJECTED',message:'Vorschlag abgelehnt'},
      evidence:evidence(b,T(4),S('d'),{at:62600,executed:40,failed:2,passed:false,profile:'android-unit'})}),
    step({op:'retry',actor:'heinz',at:62700,id:b,reason:'neuer Anlauf nach Analyse'})
  ]};
}

function budgetAndInput(){
  const d='task-delta',e='task-epsilon';
  return {name:'budget, blocking, expiry and invalid input',start:{at:5,actor:'fixture'},steps:[
    step({op:'propose',actor:'repair-loop',at:10,task:{id:d,title:'Build reparieren',kind:'REPAIR',source:'repair-loop',maxAttempts:1,priority:800}}),
    step({op:'propose',actor:'cli',at:20,task:{id:e,title:'Folgeaufgabe',kind:'IMPLEMENT',source:'cli',dependsOn:[d,d]}}),
    step({op:'accept',actor:'heinz',at:30,id:d}),
    step({op:'accept',actor:'heinz',at:40,id:e}),
    step({op:'claim',actor:'agent',at:50,id:d,owner:'agent',token:T(5),leaseMs:60000}),
    step({op:'fail',actor:'agent',at:55,id:d,token:T(6),error:{code:'X_FAILED',message:''}},'TOKEN_MISMATCH'),
    step({op:'fail',actor:'agent',at:60,id:d,token:T(5),error:{code:'NO_VERIFIED_REPAIR',message:''}}),
    step({op:'claim',actor:'agent',at:70,id:e,owner:'agent',token:T(7),leaseMs:60000},'INVALID_TRANSITION'),
    step({op:'claimNext',actor:'agent',at:80,owner:'agent',token:T(7),leaseMs:60000},'NO_ELIGIBLE_TASK'),
    step({op:'retry',actor:'heinz',at:90,id:d,reason:'Budget erneuert'}),
    step({op:'claimNext',actor:'agent',at:100,owner:'agent',token:T(7),leaseMs:60000,kinds:['TEST']},'NO_ELIGIBLE_TASK'),
    step({op:'claimNext',actor:'agent',at:105,owner:'agent',token:T(7),leaseMs:1000,kinds:['REPAIR']},'INVALID_FIELD'),
    step({op:'claimNext',actor:'agent',at:110,owner:'agent',token:T(7),leaseMs:60000,kinds:['REPAIR']}),
    step({op:'submit',actor:'agent',at:200000,id:d,token:T(7),artifact:{type:'PATCH',sha256:S('e')}},'LEASE_EXPIRED'),
    step({op:'expire',actor:'agent',at:200000}),
    step({op:'abort',actor:'heinz',at:200100,id:e,reason:'abgebrochen'}),
    step({op:'propose',actor:'cli',at:200150,task:{id:d,title:'Doppelt',kind:'REPAIR',source:'cli'}},'DUPLICATE_ID'),
    step({op:'propose',actor:'cli',at:200160,ifAbsent:true,task:{id:d,title:'Doppelt',kind:'REPAIR',source:'cli'}}),
    step({op:'propose',actor:'cli',at:200170,task:{id:'task-zeta',title:'Z',kind:'REPAIR',source:'cli',dependsOn:['task-missing']}},'UNKNOWN_DEPENDENCY'),
    step({op:'propose',actor:'cli',at:200180,task:{id:'X',title:'Z',kind:'REPAIR',source:'cli'}},'INVALID_FIELD'),
    step({op:'propose',actor:'cli',at:200190,task:{id:'task-zeta',title:'zwei\nZeilen',kind:'REPAIR',source:'cli'}},'INVALID_FIELD'),
    step({op:'propose',actor:'cli',at:200200,task:{id:'task-zeta',title:'Z',kind:'REPAIR',source:'cli',priority:1.5}},'INVALID_FIELD'),
    step({op:'propose',actor:'cli',at:200210,task:{id:'task-zeta',title:'Z',kind:'REPAIR',source:'cli',priority:1001}},'INVALID_FIELD'),
    step({op:'propose',actor:'cli',at:200220,task:{id:'task-zeta',title:'Z',kind:'REPAIR',source:'cli',subject:{deep:{x:1}}}},'INVALID_FIELD'),
    step({op:'explode',actor:'cli',at:200230},'INVALID_OP'),
    step({op:'accept',actor:'cli',at:200240,id:'task-unknown'},'NOT_FOUND'),
    step({op:'accept',actor:'not valid!',at:200250,id:d},'INVALID_FIELD'),
    step({op:'accept',actor:'cli',at:-1,id:d},'INVALID_FIELD'),
    step({op:'claim',actor:'agent',at:200260,id:d,owner:'agent',token:T(8),leaseMs:60000},'INVALID_TRANSITION'),
    step({op:'reject',actor:'heinz',at:200270,id:d,reason:'Budget aufgebraucht'}),
    step({op:'propose',actor:'cli',at:200280,task:{id:'task-zeta',title:'Z',kind:'REPAIR',source:'cli',maxAttempts:2}}),
    step({op:'setPriority',actor:'heinz',at:200290,id:'task-zeta',priority:999})
  ]};
}

function boundedHistory(){
  const steps=[step({op:'propose',actor:'cli',at:1,task:{id:'task-history',title:'Viele Änderungen',kind:'TEST',source:'cli'}}),
    step({op:'accept',actor:'cli',at:2,id:'task-history'})];
  for(let k=0;k<45;k++)steps.push(step({op:'setPriority',actor:'cli',at:3+k,id:'task-history',priority:k%2?100:200}));
  return {name:'history is bounded and drops are counted',start:{at:0,actor:'fixture'},steps};
}

function boundedEvidence(){
  const id='task-evidence';
  const steps=[step({op:'propose',actor:'cli',at:1,task:{id,title:'Viele Läufe',kind:'TEST',source:'cli',maxAttempts:20}}),
    step({op:'accept',actor:'cli',at:2,id})];
  for(let k=1;k<=10;k++){
    steps.push(step({op:'claim',actor:'w',at:k*100,id,owner:'w',token:T(100+k),leaseMs:60000}));
    steps.push(step({op:'fail',actor:'w',at:k*100+50,id,token:T(100+k),error:{code:'TEST_FAILURE',message:'Lauf '+k},
      evidence:evidence(id,T(100+k),S('f'),{at:k*100+50,executed:k,failed:1,passed:false})}));
  }
  return {name:'evidence is bounded and drops are counted',start:{at:0,actor:'fixture'},steps};
}

/** Run a case with this implementation; returns per-step outcomes and the final document. */
export function runCase(c){
  let doc=B.emptyDocument(c.start.at,c.start.actor);
  const outcomes=c.steps.map(s=>{
    try{doc=B.applyOp(doc,s.op).doc;return 'ok';}
    catch(e){if(e instanceof B.BacklogError)return e.code;throw e;}
  });
  return {outcomes,doc};
}

export function conformance(){
  const cases=[lifecycle(),budgetAndInput(),boundedHistory(),boundedEvidence()].map(c=>{
    const {outcomes,doc}=runCase(c);
    outcomes.forEach((o,k)=>{if(o!==c.steps[k].expect)throw Error(`${c.name} step ${k}: expected ${c.steps[k].expect}, got ${o}`);});
    return {...c,final:{revision:doc.revision,integrity:B.integrityOf(doc),
      states:Object.fromEntries(doc.tasks.map(t=>[t.id,t.state]))}};
  });
  return {name:'continuum-development-backlog conformance',schemaVersion:B.SCHEMA_VERSION,cases};
}

const canonicalWithIntegrity=doc=>{const {integrity,...rest}=doc;return Buffer.from(B.canonical({...rest,integrity:B.integrityOf(rest)})+'\n','utf8');};

/** All fixture files as {name: Buffer}; `write` stores them, the tests compare. */
export function fixtureFiles(){
  const files={};
  const conf=conformance();
  files['conformance.json']=Buffer.from(JSON.stringify(conf,null,1)+'\n','utf8');
  const valid=runCase(lifecycle()).doc;
  files['valid-v2.json']=B.encodeDocument(valid);
  const unknown=structuredClone(valid);
  unknown.futureFeature={flags:[1,'x',true,null],nested:{k:'v'}};
  unknown.tasks.find(t=>t.id==='task-gamma').note='bleibt erhalten';
  files['unknown-fields.json']=B.encodeDocument(unknown);
  const v1=JSON.parse(fs.readFileSync(path.join(FIXTURE_DIR,'v1-planner.json'),'utf8'));
  files['v1-migrated.json']=B.encodeDocument(B.migrateV1(v1,{at:1700000000000,actor:'migration'}));
  const text=files['valid-v2.json'].toString('utf8');
  const invalid=[];
  const add=(name,bytes,code)=>{files[name]=Buffer.isBuffer(bytes)?bytes:Buffer.from(bytes,'utf8');invalid.push({file:name,code});};
  add('invalid-duplicate-key.json',text.replace('{"integrity"','{"revision":1,"integrity"'),'CORRUPT');
  add('invalid-float.json',text.replace('"priority":900','"priority":900.5'),'CORRUPT');
  add('invalid-integrity.json',text.replace('Integrationstest','Integrationstext'),'INTEGRITY_MISMATCH');
  add('invalid-utf8.json',Buffer.concat([Buffer.from(text.slice(0,40),'utf8'),Buffer.from([0xc3,0x28]),Buffer.from(text.slice(40),'utf8')]),'CORRUPT');
  add('invalid-bom.json',Buffer.concat([Buffer.from([0xef,0xbb,0xbf]),files['valid-v2.json']]),'CORRUPT');
  add('invalid-trailing.json',text.trimEnd()+'x\n','CORRUPT');
  add('invalid-lone-surrogate.json',text.replace('Viele','\\ud800').replace('Integrationstest','Integr\\ud800tionstest'),'CORRUPT');
  const mutated=(change)=>{const d=structuredClone(valid);change(d);return canonicalWithIntegrity(d);};
  add('invalid-future-version.json',mutated(d=>{d.schemaVersion=3;}),'UNSUPPORTED_VERSION');
  add('invalid-state.json',mutated(d=>{d.tasks[1].state='WORKING';}),'CORRUPT');
  add('invalid-done-without-evidence.json',mutated(d=>{const t=d.tasks[1];t.state='DONE';t.submission={token:T(4),by:'x',at:1,artifact:{type:'PATCH',sha256:S('d')}};}),'CORRUPT');
  add('invalid-forged-evidence.json',mutated(d=>{d.tasks[0].evidence=d.tasks[0].evidence.map(e=>({...e,profiles:[{...e.profiles[0],executed:0}]}));}),'CORRUPT');
  add('invalid-cycle.json',mutated(d=>{d.tasks[0].dependsOn=['task-beta'];}),'CORRUPT');
  add('invalid-unsorted.json',mutated(d=>{d.tasks.reverse();}),'CORRUPT');
  add('invalid-claim-outside-progress.json',mutated(d=>{d.tasks[1].claim={owner:'x',token:T(1),at:1,leaseUntil:2};}),'CORRUPT');
  add('invalid-not-backlog.json','{"schema":"something-else","schemaVersion":2}\n','CORRUPT');
  files['invalid.json']=Buffer.from(JSON.stringify(invalid,null,1)+'\n','utf8');
  files['roundtrip.json']=Buffer.from(JSON.stringify(roundtripKotlinPlan(),null,1)+'\n','utf8');
  const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'backlog-fixture-'));
  try{
    const prepared=roundtripPrepare(tmp);
    files['roundtrip-node.json']=fs.readFileSync(path.join(tmp,'backlog.json'));
    files['roundtrip-node-state.json']=Buffer.from(JSON.stringify(prepared.state)+'\n','utf8');
  }finally{fs.rmSync(tmp,{recursive:true,force:true});}
  return files;
}

// ---------------------------------------------------------------------------
// Cross-runtime round trip: Node prepares, Kotlin changes, Node verifies.
// ---------------------------------------------------------------------------
export const ROUNDTRIP={nodeActor:'node-roundtrip',kotlinActor:'android-roundtrip'};

export function roundtripPrepare(dir){
  fs.mkdirSync(dir,{recursive:true});
  const file=path.join(dir,'backlog.json');
  for(const f of fs.readdirSync(dir))if(f.startsWith('backlog.json'))fs.rmSync(path.join(dir,f));
  const store=new B.BacklogStore(file,{actor:ROUNDTRIP.nodeActor,clock:()=>1_800_000_000_000});
  const at=1_800_000_000_000;
  const r=store.apply([
    {op:'propose',actor:'node-roundtrip',at,task:{id:'rt-repair',title:'Rundreise: Reparatur – Ünïcödé ✓ 𝄞',kind:'REPAIR',source:'repair-loop',priority:900}},
    {op:'propose',actor:'node-roundtrip',at,task:{id:'rt-follow',title:'Rundreise: Folgeaufgabe',kind:'IMPLEMENT',source:'cli',dependsOn:['rt-repair']}},
    {op:'propose',actor:'node-roundtrip',at,task:{id:'rt-investigate',title:'Rundreise: Befund prüfen',kind:'INVESTIGATE',source:'code-analyzer',priority:230,
      subject:{file:'app/src/main/java/a/B.kt',symbol:'a.B',confidence:'medium'}}},
    {op:'accept',actor:'heinz',at:at+1,id:'rt-repair'},
    {op:'accept',actor:'heinz',at:at+1,id:'rt-follow'},
    {op:'claim',actor:'node-roundtrip',at:at+2,id:'rt-repair',owner:'node-roundtrip',token:T(0xabc),leaseMs:3_600_000},
    {op:'submit',actor:'node-roundtrip',at:at+3,id:'rt-repair',token:T(0xabc),artifact:{type:'PATCH',sha256:S('1')}}
  ]);
  // an unknown field written by a newer Node version must survive the Kotlin rewrite
  const withUnknown=store.update(doc=>({...doc,revision:doc.revision+1,roundtripMarker:{writtenBy:'node',items:[1,2,3]}}));
  const state={revision:withUnknown.doc.revision,integrity:withUnknown.doc.integrity};
  fs.writeFileSync(path.join(dir,'node-state.json'),JSON.stringify(state)+'\n');
  return {state,results:r.results};
}

/**
 * What the Kotlin side does (DevelopmentBacklogRoundtripTest.kt reads this from
 * roundtrip.json): `ops` through DevelopmentBacklogStore, then it writes `opLog`
 * with its own canonical writer to kotlin-ops.json for Node to apply.
 */
export function roundtripKotlinPlan(){
  const at=1_800_000_100_000;
  return {
    ops:[
      {op:'complete',actor:ROUNDTRIP.kotlinActor,at,id:'rt-repair',evidence:{...evidence('rt-repair',T(0xabc),S('1'),{at,executed:412,profile:'android-unit'}),chain:'android'}},
      {op:'claim',actor:ROUNDTRIP.kotlinActor,at:at+1,id:'rt-follow',owner:ROUNDTRIP.kotlinActor,token:T(0xdef),leaseMs:3_600_000},
      {op:'reject',actor:ROUNDTRIP.kotlinActor,at:at+2,id:'rt-investigate',reason:'auf dem Gerät geprüft: kein Fehler'},
      {op:'propose',actor:ROUNDTRIP.kotlinActor,at:at+3,task:{id:'rt-android',title:'Vom Gerät: Größenprüfung 📱',kind:'IMPLEMENT',source:'android'}}
    ],
    opLog:[
      {op:'accept',actor:'heinz',at:at+10,id:'rt-android'},
      {op:'accept',actor:'heinz',at:at+11,id:'rt-repair'},
      {op:'setPriority',actor:'heinz',at:at+12,id:'rt-android',priority:600}
    ],
    expected:{revisionDelta:4,states:{'rt-repair':'DONE','rt-follow':'IN_PROGRESS','rt-investigate':'REJECTED','rt-android':'PROPOSED'}}
  };
}
const KOTLIN_EXPECTED=roundtripKotlinPlan().expected;

/** JavaScript stand-in for the Kotlin side, used to test the verifier itself. */
export function roundtripSimulateKotlin(dir){
  const plan=roundtripKotlinPlan();
  const store=new B.BacklogStore(path.join(dir,'backlog.json'),{actor:ROUNDTRIP.kotlinActor});
  const r=store.apply(plan.ops);
  fs.writeFileSync(path.join(dir,'kotlin-ops.json'),B.canonical(plan.opLog)+'\n');
  fs.writeFileSync(path.join(dir,'kotlin-state.json'),B.canonical({revision:r.doc.revision,integrity:r.doc.integrity})+'\n');
}

export function roundtripVerify(dir){
  const file=path.join(dir,'backlog.json');
  const node=JSON.parse(fs.readFileSync(path.join(dir,'node-state.json'),'utf8'));
  const kotlin=JSON.parse(fs.readFileSync(path.join(dir,'kotlin-state.json'),'utf8'));
  const bytes=fs.readFileSync(file);
  const {doc}=B.decodeDocument(bytes);
  const problems=[];
  const expect=(ok,message)=>{if(!ok)problems.push(message);};
  expect(B.encodeDocument(doc).equals(bytes),'Kotlin output is not byte-identical to the canonical Node encoding');
  expect(doc.integrity===kotlin.integrity,'integrity differs from what Kotlin reported');
  expect(doc.revision===node.revision+KOTLIN_EXPECTED.revisionDelta,`revision ${doc.revision}, expected ${node.revision}+${KOTLIN_EXPECTED.revisionDelta}`);
  for(const [id,state] of Object.entries(KOTLIN_EXPECTED.states))expect(doc.tasks.find(t=>t.id===id)?.state===state,`${id} should be ${state}`);
  expect(doc.roundtripMarker?.writtenBy==='node'&&JSON.stringify(doc.roundtripMarker.items)==='[1,2,3]','unknown field lost');
  expect(doc.tasks.find(t=>t.id==='rt-repair')?.title==='Rundreise: Reparatur – Ünïcödé ✓ 𝄞','UTF-8 title changed');
  expect(doc.tasks.find(t=>t.id==='rt-android')?.title==='Vom Gerät: Größenprüfung 📱','Kotlin UTF-8 title changed');
  expect(!fs.existsSync(file+'.lock'),'lock file left behind');
  expect(!fs.readdirSync(dir).some(f=>f.includes('.tmp-')),'temporary file left behind');
  // operations recorded by Kotlin are applied with the same rules here
  const ops=JSON.parse(fs.readFileSync(path.join(dir,'kotlin-ops.json'),'utf8'));
  const store=new B.BacklogStore(file,{actor:ROUNDTRIP.nodeActor});
  const applied=store.apply(ops,{stopOnError:false});
  const codes=applied.results.map(r=>r.ok?'ok':r.code);
  expect(JSON.stringify(codes)===JSON.stringify(['ok','INVALID_TRANSITION','ok']),'Kotlin op log results '+JSON.stringify(codes));
  expect(applied.doc.tasks.find(t=>t.id==='rt-android')?.state==='READY','op log did not accept rt-android');
  expect(applied.doc.revision===doc.revision+2,'op log revision');
  return {problems,revision:applied.doc.revision};
}

if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1])){
  const [cmd,dir]=process.argv.slice(2);
  if(cmd==='write'){
    for(const [name,bytes] of Object.entries(fixtureFiles()))fs.writeFileSync(path.join(FIXTURE_DIR,name),bytes);
    console.log('fixtures written to',FIXTURE_DIR);
  }else if(cmd==='roundtrip-prepare'&&dir){
    console.log(JSON.stringify(roundtripPrepare(path.resolve(dir)).state));
  }else if(cmd==='roundtrip-verify'&&dir){
    const r=roundtripVerify(path.resolve(dir));
    if(r.problems.length){console.error('Round trip FAILED:\n- '+r.problems.join('\n- '));process.exitCode=1;}
    else console.log('Round trip Node -> Kotlin -> Node verified, revision',r.revision);
  }else{console.error('usage: backlog-fixtures.mjs write | roundtrip-prepare <dir> | roundtrip-verify <dir>');process.exitCode=2;}
}
