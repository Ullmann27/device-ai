import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {parseKotlinDiagnostics,parseJUnit,parseJsErrorText,fingerprint} from './repair-diagnostics.mjs';
import {proposeCandidates,applyEdits,hunk,unescapeCodeNewlines,nearestNames,insertImport,editDistance} from './repair-strategies.mjs';
import {critique} from './repair-critic.mjs';
import {emptyLedger,recordSession,verifyLedger} from './repair-ledger.mjs';
import {createWorktree,removeWorktree,repairSession,rankCandidates,PROFILES,CHAINS,commitProposal} from './repair-loop.mjs';
import {validateVerifiedReport,assertProposalBranch,publishRepair} from './repair-publish.mjs';

const nested=process.env.CONTINUUM_REPAIR_DEPTH?'skipped inside a repair session (no nested loops)':false;

test('kotlin compiler output becomes structured, repository-relative diagnostics',()=>{
 const root='/w/repo';
 const out=parseKotlinDiagnostics([
  "e: file:///w/repo/app/src/main/A.kt:12:7 Unresolved reference 'Fils'.",
  'e: /w/repo/app/src/main/B.kt: (3, 1): Syntax error: Expecting an element.',
  "e: file:///etc/passwd:1:1 Unresolved reference 'x'.",
  'w: file:///w/repo/app/src/main/A.kt:1:1 warning only'
 ].join('\n'),root);
 assert.equal(out.length,2);
 assert.deepEqual([out[0].file,out[0].line,out[0].col,out[0].kind,out[0].symbol],['app/src/main/A.kt',12,7,'UNRESOLVED_REFERENCE','Fils']);
 assert.equal(out[1].kind,'SYNTAX_ERROR');
 assert.equal(fingerprint({...out[0],line:99}),out[0].fingerprint,'fingerprints ignore line numbers');
});

test('JUnit with ">" in attributes and Node load errors are parsed',()=>{
 const xml='<testsuites><testcase name="ok" classname="t"/><testcase name="bad" classname="t"><failure message="expected a &gt; 0" type="x">AssertionError\n at file:///w/repo/.github/continuum/x.mjs:4:2</failure></testcase></testsuites>';
 const j=parseJUnit(xml,'/w/repo');
 assert.equal(j.total,2);assert.equal(j.failed,1);
 assert.deepEqual([j.diagnostics[0].file,j.diagnostics[0].line,j.diagnostics[0].message],['.github/continuum/x.mjs',4,'expected a > 0']);
 const js=parseJsErrorText('# ReferenceError: sha256 is not defined\n#     at file:///w/repo/.github/continuum/n.mjs:5:20\n','/w/repo');
 assert.deepEqual([js[0].kind,js[0].symbol,js[0].file,js[0].line],['REFERENCE_ERROR','sha256','.github/continuum/n.mjs',5]);
});

test('strategies generate exact, unique, replayable hunks',()=>{
 assert.equal(unescapeCodeNewlines('val a=x.also {\\n  y()\\n} // "\\n"'),'val a=x.also {\n  y()\n} // "\\n"');
 assert.equal(unescapeCodeNewlines('val s="keep\\n"'),null);
 assert.equal(editDistance('sigmiod','sigmoid'),1);
 assert.deepEqual(nearestNames('Encodr',new Map([['Encoder',3],['Decoder',2],['Other',9]])),['Encoder']);
 assert.equal(insertImport('package a\n\nimport b.C\nimport d.E\n\nclass X','c.Z'),'package a\n\nimport b.C\nimport c.Z\nimport d.E\n\nclass X');
 const h=hunk('f.kt','a\nb\nc\nb\n','a\nB\nc\nb\n');
 assert.equal('a\nb\nc\nb\n'.split(h.oldText).length,2,'hunk must be unique');
 const files={'app/src/main/K.kt':'package p\n\nimport q.Z\n\nclass K { val f = Files.exists(x) }\n'};
 const d={kind:'UNRESOLVED_REFERENCE',symbol:'Files',file:'app/src/main/K.kt',line:5,col:19,fingerprint:'aaaa'};
 const c=proposeCandidates([d],{readFile:f=>files[f]??null,index:null,jsSiblings:new Map(),changedInBase:[]});
 const imp=c.find(x=>x.strategy==='KOTLIN_MISSING_IMPORT');
 assert.ok(imp);assert.match(applyEdits(f=>files[f],imp.edits).get('app/src/main/K.kt'),/import java\.nio\.file\.Files/);
});

test('critic blocks protected paths, test edits, weakening and dangerous code',()=>{
 const edit=(file,o,n)=>({strategy:'KOTLIN_TYPO_REFERENCE',prior:0.6,edits:[{file,oldText:o,newText:n}]});
 assert.equal(critique(edit('app/src/main/A.kt','val a=fo()','val a=foo()')).approved,true);
 assert.equal(critique(edit('.github/workflows/x.yml','a','b')).approved,false);
 assert.equal(critique(edit('app/build.gradle.kts','a','b')).approved,false);
 assert.equal(critique(edit('app/src/main/cognitive/InternetConsentPolicy.kt','a','b')).approved,false);
 assert.equal(critique(edit('app/src/test/A.kt','a','b')).approved,false,'tests are the specification');
 assert.equal(critique({strategy:'KOTLIN_ESCAPED_NEWLINE',edits:[{file:'app/src/test/A.kt',oldText:'a\\nb',newText:'a\nb'}]}).approved,true);
 assert.equal(critique(edit('app/src/main/A.kt','assertTrue(x)\nf()','f()')).approved,false);
 assert.equal(critique(edit('app/src/main/A.kt','f()','Runtime.getRuntime().exec("x")')).approved,false);
 assert.equal(critique(edit('app/src/main/A.kt','f(a)','f(a!!)')).approved,false);
 assert.equal(critique(edit('../etc/x','a','b')).approved,false);
 assert.equal(critique(edit('app/src/main/A.kt','x',Array(50).fill('y').join('\n'))).approved,false);
});

test('ledger learns per diagnostic kind and strategy and skips proven failures',()=>{
 const report={base:'a'.repeat(40),chain:'node',outcome:'VERIFIED',finishedAt:'2026-01-01T00:00:00Z',verified:{patchSha256:'b'.repeat(64),strategy:'S'},
  attempts:[{candidate:'c1c1c1c1',diagnostic:'d1d1',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_TYPO_REFERENCE',outcome:'FAILED_TESTS',edits:[],score:0.5},
   {candidate:'c2c2c2c2',diagnostic:'d1d1',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_MISSING_IMPORT',outcome:'VERIFIED',edits:[],score:0.6}]};
 const ledger=recordSession(emptyLedger(),report,{trigger:'test'});
 const stats=Object.fromEntries(ledger.experiences.map(e=>[e.kind,[e.successes,e.failures]]));
 assert.deepEqual(stats,{'REFERENCE_ERROR|JS_MISSING_IMPORT':[1,0],'REFERENCE_ERROR|JS_TYPO_REFERENCE':[0,1]});
 const ranked=rankCandidates([
  {id:'c1c1c1c1',diagnostic:'d1d1',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_TYPO_REFERENCE',prior:0.7},
  {id:'c3c3c3c3',diagnostic:'d1d1',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_TYPO_REFERENCE',prior:0.7},
  {id:'c4c4c4c4',diagnostic:'d1d1',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_MISSING_IMPORT',prior:0.6}],
  ledger.experiences,new Set(['d1d1:c1c1c1c1']));
 assert.deepEqual(ranked.map(c=>c.id),['c4c4c4c4','c3c3c3c3'],'proven failure skipped, learned strategy first');
 assert.throws(()=>verifyLedger({...ledger,attempts:[{...ledger.attempts[0],edits:[{file:'../x',oldText:'',newText:''}]}]}));
});

test('check profiles are fixed and unknown chains fail closed',()=>{
 assert.deepEqual(Object.keys(CHAINS).sort(),['android','jvm','node']);
 assert.throws(()=>PROFILES['jvm-harness']('/tmp'),/not configured/);
 assert.equal(PROFILES['android-compile']('/w').cmd,'gradle');
 assert.throws(()=>assertProposalBranch('main'));
 assert.throws(()=>assertProposalBranch('continuum-agent/../main'));
 assert.equal(assertProposalBranch('continuum-agent/repair-abc'),'continuum-agent/repair-abc');
});

function tempRepo(){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-loop-test-'));
 const g=(...a)=>{const r=spawnSync('git',a,{cwd:dir,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();};
 fs.mkdirSync(path.join(dir,'.github/continuum'),{recursive:true});
 fs.writeFileSync(path.join(dir,'.github/continuum/math.mjs'),"export function square(x){return x*x;}\nexport function quad(x){return square(square(x));}\n");
 fs.writeFileSync(path.join(dir,'.github/continuum/math.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\nimport {quad} from './math.mjs';\ntest('quad',()=>assert.equal(quad(2),16));\n");
 g('init','-q');g('-c','user.name=t','-c','user.email=t@t','add','.');g('-c','user.name=t','-c','user.email=t@t','commit','-qm','clean');
 fs.writeFileSync(path.join(dir,'.github/continuum/math.mjs'),"export function square(x){return x*x;}\nexport function quad(x){return sqare(square(x));}\n");
 g('-c','user.name=t','-c','user.email=t@t','commit','-qam','fault');
 return {dir,head:g('rev-parse','HEAD'),g};
}

test('end-to-end: injected fault is reproduced, repaired in isolation, verified and committed',{skip:nested},()=>{
 const {dir,head,g}=tempRepo();
 const out=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-loop-out-'));
 try{
  const wt=createWorktree(dir,head);
  let report;
  try{
   report=repairSession({wt,base:head,chain:'node',out,ledger:emptyLedger()});
   assert.equal(report.baseline.passed,false);
   assert.equal(report.outcome,'VERIFIED');
   assert.equal(report.verified.strategy,'JS_TYPO_REFERENCE');
   assert.match(fs.readFileSync(path.join(out,'repair.patch'),'utf8'),/-export function quad\(x\)\{return sqare/);
   const commit=commitProposal(wt,report,{branch:'continuum-agent/repair-test',message:'fix'});
   assert.match(commit,/^[0-9a-f]{40}$/);
  }finally{removeWorktree(dir,wt);}
  // the caller's checkout is untouched
  assert.match(fs.readFileSync(path.join(dir,'.github/continuum/math.mjs'),'utf8'),/sqare/);
  assert.equal(g('status','--porcelain'),'');
  // publisher re-validates the verified patch independently
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report));
  const attempt=validateVerifiedReport(report,fs.readFileSync(path.join(out,'repair.patch'),'utf8'));
  assert.equal(attempt.strategy,'JS_TYPO_REFERENCE');
  assert.throws(()=>validateVerifiedReport(report,fs.readFileSync(path.join(out,'repair.patch'),'utf8')+'\n'),/hash/);
  return publishRepair({root:dir,outDir:out,targetBranch:'dev',push:false}).then(r=>{
   assert.equal(r.branch.startsWith('continuum-agent/repair-'),true);assert.match(r.commit,/^[0-9a-f]{40}$/);
  });
 }finally{fs.rmSync(dir,{recursive:true,force:true});fs.rmSync(out,{recursive:true,force:true});}
});

test('Node 22.16 JUnit ReferenceError wrapper retains actionable typo diagnosis',()=>{
 const xml='<testcase name="quad"><failure message="sqare is not defined">cause: ReferenceError [Error]: sqare is not defined\n at quad (file:///repo/.github/continuum/math.mjs:2:25)</failure></testcase>';
 const result=parseJUnit(xml,'/repo');
 assert.equal(result.diagnostics[0].kind,'REFERENCE_ERROR');
 assert.equal(result.diagnostics[0].symbol,'sqare');
 assert.equal(result.diagnostics[0].file,'.github/continuum/math.mjs');
});
