import test from 'node:test';
import assert from 'node:assert/strict';
import {prioritizeFindings,planBacklog} from './development-planner.mjs';
test('prioritizes high-impact integration findings deterministically',()=>{
 const report={findings:[
  {kind:'UNREFERENCED',symbol:'A',file:'a.kt'},
  {kind:'TEST_ONLY',symbol:'B',file:'b.kt'},
  {kind:'DEAD_CLUSTER',symbol:'C',file:'c.kt'},
  {kind:'SOMETHING_ELSE',symbol:'D',file:'d.kt'}
 ]};
 assert.deepEqual(prioritizeFindings(report).map(x=>x.symbol),['C','B','A']);
 assert.ok(prioritizeFindings(report).every(x=>x.action==='INVESTIGATE_ONLY'));
});
test('rejects malformed findings input',()=>assert.throws(()=>prioritizeFindings({})));
test('creates project backlog without altering sources',()=>{
 const result=planBacklog(process.cwd(),3);
 assert.equal(result.schemaVersion,1);
 assert.equal(result.mode,'review-required');
 assert.ok(result.tasks.length<=3);
});

// ---------------------------------------------------------------------------
// Persistent backlog integration
// ---------------------------------------------------------------------------
import {syncBacklogOps,repairSessionOps,repairTaskId} from './development-planner.mjs';
import * as B from './development-backlog.mjs';

const apply=(doc,ops)=>B.applyOps(doc,ops,{stopOnError:false});

test('analyzer findings become proposals once; vanished proposals are withdrawn, human decisions kept',()=>{
  const report={findings:[
    {kind:'DEAD_CLUSTER',symbol:'a.Dead',file:'main/java/a/Dead.kt',confidence:'medium'},
    {kind:'TEST_ONLY',symbol:'a.OnlyTests',file:'main/java/a/OnlyTests.kt',confidence:'medium'},
    {kind:'UNREFERENCED',symbol:'a.Lonely',file:'main/java/a/Lonely.kt',confidence:'high'},
    {kind:'POSSIBLY_UNUSED',symbol:'a.Maybe',file:'main/java/a/Maybe.kt',confidence:'low'}]};
  let doc=B.emptyDocument(0,'t');
  const first=apply(doc,syncBacklogOps(doc,report,{at:1,limit:2}));doc=first.doc;
  assert.deepEqual(doc.tasks.map(t=>t.subject.symbol).sort(),['a.Dead','a.OnlyTests'],'highest priority first, limited per run');
  assert.ok(doc.tasks.every(t=>t.state==='PROPOSED'&&t.subject.file.startsWith('app/src/main/')));
  doc=apply(doc,syncBacklogOps(doc,report,{at:2,limit:2})).doc;
  assert.equal(doc.tasks.length,3,'the remaining finding follows in the next run, nothing is duplicated');
  assert.deepEqual(syncBacklogOps(doc,report,{at:3,limit:2}),[],'a stable analysis changes nothing');
  const dead=doc.tasks.find(t=>t.subject.symbol==='a.Dead'),lonely=doc.tasks.find(t=>t.subject.symbol==='a.Lonely');
  doc=apply(doc,[{op:'accept',actor:'heinz',at:4,id:dead.id},{op:'reject',actor:'heinz',at:4,id:lonely.id,reason:'gewollt'}]).doc;
  const ops=syncBacklogOps(doc,{findings:[]},{at:5});
  assert.deepEqual(ops.map(o=>[o.op,o.id]),[['abort',doc.tasks.find(t=>t.subject.symbol==='a.OnlyTests').id]],'only untouched proposals are withdrawn');
  doc=apply(doc,ops).doc;
  assert.deepEqual(doc.tasks.map(t=>t.state).sort(),['ABORTED','READY','REJECTED']);
  assert.deepEqual(syncBacklogOps(doc,report,{at:6}),[],'rejected or aborted findings are not resurrected');
});

function repairReport({verified=true,base='a'.repeat(40),startedAt='2026-10-15T10:00:00.000Z',finishedAt='2026-10-15T10:20:00.000Z'}={}){
  const tests=[{profile:'android-compile',passed:true,testProfile:false,tests:{executed:0,failed:0,skipped:0}},
    {profile:'android-unit',passed:true,testProfile:true,tests:{executed:413,failed:0,skipped:0}}];
  return {base,chain:'android',startedAt,finishedAt,outcome:verified?'VERIFIED':'NO_VERIFIED_REPAIR',
    baseline:{diagnostics:[{kind:'UNRESOLVED_REFERENCE',file:'app/src/main/java/A.kt',line:7,fingerprint:'0123456789abcdef'}]},
    attempts:[{candidate:'fedcba9876543210',diagnostic:'0123456789abcdef',strategy:'KOTLIN_TYPO_REFERENCE',outcome:verified?'VERIFIED':'FAILED_TESTS',tests}],
    verified:verified?{candidate:'fedcba9876543210',strategy:'KOTLIN_TYPO_REFERENCE',patchSha256:'c'.repeat(64)}:null};
}

test('a verified repair session completes its REPAIR task with the evidence that verified it, exactly once',()=>{
  let doc=B.emptyDocument(0,'t');
  const report=repairReport();
  const {id,ops,skipped}=repairSessionOps(doc,report);
  assert.equal(id,repairTaskId(report));assert.equal(skipped,null);
  const r=apply(doc,ops);
  assert.ok(r.results.every(x=>x.ok&&!x.duplicate),JSON.stringify(r.results));
  doc=r.doc;
  const task=doc.tasks.find(t=>t.id===id);
  assert.equal(task.state,'DONE');
  assert.deepEqual(task.evidence[0].profiles.map(p=>[p.profile,p.executed]),[['android-compile',0],['android-unit',413]]);
  assert.equal(task.evidence[0].repair.strategy,'KOTLIN_TYPO_REFERENCE');
  const again=apply(doc,repairSessionOps(doc,report).ops);
  assert.ok(again.results.every(x=>x.ok&&(x.duplicate||x.changed===false)),'recording the same session twice is a no-op');
  assert.equal(again.doc.revision,doc.revision);
  assert.equal(repairSessionOps(doc,repairReport({startedAt:'2026-10-15T11:00:00.000Z',finishedAt:'2026-10-15T11:10:00.000Z'})).skipped,`task ${id} is DONE`);
});

test('independent worktree check is recorded rather than first-pass evidence',()=>{
 const report=repairReport();
 report.independent={passed:true,profiles:[
  {profile:'android-compile',passed:true,executed:0,failed:0,skipped:0},
  {profile:'android-unit',passed:true,executed:518,failed:0,skipped:0}
 ]};
 const initial=B.emptyDocument(0,'t');
 const record=apply(initial,repairSessionOps(initial,report).ops);
 assert.ok(record.results.every(x=>x.ok),JSON.stringify(record.results));
 assert.equal(record.doc.tasks[0].state,'DONE');
 assert.equal(record.doc.tasks[0].evidence[0].profiles.find(x=>
  x.profile==='android-unit').executed,518,
  'independent result must replace earlier 413-test candidate report');
});

test('failed repair sessions spend the attempt budget and then stop',()=>{
  let doc=B.emptyDocument(0,'t');
  for(let k=0;k<3;k++){
    const report=repairReport({verified:false,startedAt:`2026-10-15T1${k}:00:00.000Z`,finishedAt:`2026-10-15T1${k}:30:00.000Z`});
    doc=apply(doc,repairSessionOps(doc,report).ops).doc;
  }
  const task=doc.tasks[0];
  assert.deepEqual([task.state,task.attempts,task.error.code],['FAILED',3,'NO_VERIFIED_REPAIR']);
  const fourth=repairSessionOps(doc,repairReport({verified:false,startedAt:'2026-10-15T14:00:00.000Z',finishedAt:'2026-10-15T14:30:00.000Z'}));
  assert.deepEqual(fourth.ops,[]);assert.match(fourth.skipped,/FAILED/);
  assert.deepEqual(repairSessionOps(doc,{...repairReport(),outcome:'NO_FAILURE'}).ops,[]);
});
