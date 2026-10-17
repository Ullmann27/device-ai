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
import {createWorktree,removeWorktree,repairSession,rankCandidates,PROFILES,CHAINS,commitProposal,boundedWorktreeFile} from './repair-loop.mjs';
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
 assert.equal(critique(edit('.github/continuum/fixtures/node-reporter/junit-v22.22.0.xml','a','b')).approved,false,'recorded fixtures are the specification');
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

test('a whole-file revert never outranks minimal patches even with a misleading success history',()=>{
 const candidates=[
  {id:'a',diagnostic:'d',diagnosticKind:'REFERENCE_ERROR',strategy:'REVERT_FILE_TO_PARENT',prior:1},
  {id:'b',diagnostic:'d',diagnosticKind:'REFERENCE_ERROR',strategy:'REVERT_HUNK_TO_PARENT',prior:0.9},
  {id:'c',diagnostic:'d',diagnosticKind:'REFERENCE_ERROR',strategy:'JS_TYPO_REFERENCE',prior:0.1}
 ];
 const stats=[
  {kind:'REFERENCE_ERROR|REVERT_FILE_TO_PARENT',successes:100,failures:0},
  {kind:'REFERENCE_ERROR|JS_TYPO_REFERENCE',successes:0,failures:100}
 ];
 assert.deepEqual(rankCandidates(candidates,stats).map(c=>c.strategy),
  ['JS_TYPO_REFERENCE','REVERT_HUNK_TO_PARENT','REVERT_FILE_TO_PARENT']);
});

test('check profiles are fixed and unknown chains fail closed',()=>{
 assert.deepEqual(Object.keys(CHAINS).sort(),['android','jvm','node']);
 assert.throws(()=>PROFILES['jvm-harness']('/tmp'),/not configured/);
 assert.equal(PROFILES['android-compile']('/w').cmd,'gradle');
 assert.throws(()=>assertProposalBranch('main'));
 assert.throws(()=>assertProposalBranch('continuum-agent/../main'));
 assert.equal(assertProposalBranch('continuum-agent/repair-abc'),'continuum-agent/repair-abc');
});

test('repair worktree guard excludes symlinks, parent escapes and null path components',()=>{
 const sandbox=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-patch-sandbox-'));
 const wt=path.join(sandbox,'checkout');
 const outside=path.join(sandbox,'outside');
 try {
  fs.mkdirSync(path.join(wt,'src'),{recursive:true});
  fs.mkdirSync(outside);
  fs.writeFileSync(path.join(wt,'src','safe.mjs'),'export const safe=true;\n');
  fs.writeFileSync(path.join(outside,'private.mjs'),'PRIVATE\n');
  assert.equal(boundedWorktreeFile(wt,'src/safe.mjs'),
   path.join(wt,'src','safe.mjs'));
  fs.symlinkSync(path.join(outside,'private.mjs'),path.join(wt,'bad-file.mjs'));
  fs.symlinkSync(outside,path.join(wt,'linked-folder'));
  for(const filename of ['../outside/private.mjs','/etc/passwd','bad-file.mjs',
   'linked-folder/private.mjs','src/../bad-file.mjs',
   'src/safe.mjs\0ignore','src//safe.mjs']){
   assert.throws(()=>boundedWorktreeFile(wt,filename),undefined,
    'Worktree edit unexpectedly accepted '+JSON.stringify(filename));
  }
  assert.equal(fs.readFileSync(path.join(outside,'private.mjs'),'utf8'),'PRIVATE\n');
 }finally{fs.rmSync(sandbox,{recursive:true,force:true});}
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
  // a report whose success does not rest on executed tests is never published,
  // even when its own flags claim otherwise
  const patchText=fs.readFileSync(path.join(out,'repair.patch'),'utf8');
  const tampered=change=>{const t=structuredClone(report);change(t.attempts.find(a=>a.outcome==='VERIFIED'));return t;};
  assert.throws(()=>validateVerifiedReport(tampered(a=>{a.tests[0].testProfile=false;a.tests[0].tests.executed=0;}),patchText),/executed test evidence/);
  assert.throws(()=>validateVerifiedReport(tampered(a=>{a.evidence.sufficient=false;}),patchText),/executed test evidence/);
  assert.throws(()=>validateVerifiedReport(tampered(a=>{delete a.evidence;}),patchText),/executed test evidence/);
  assert.throws(()=>validateVerifiedReport(tampered(a=>{a.tests=[];}),patchText),/every check/);
  return publishRepair({root:dir,outDir:out,targetBranch:'dev',push:false}).then(r=>{
   assert.equal(r.branch.startsWith('continuum-agent/repair-'),true);assert.match(r.commit,/^[0-9a-f]{40}$/);
  });
 }finally{fs.rmSync(dir,{recursive:true,force:true});fs.rmSync(out,{recursive:true,force:true});}
});

import {evidenceSufficient,runProfile,parseUnifiedZeroHunks} from './repair-loop.mjs';
import {revertHunk} from './repair-strategies.mjs';

test('hunk revert handles modification, insertion and deletion hunks exactly',()=>{
 const parent='a\nb\nc\nd\n';
 assert.equal(revertHunk('a\nB\nc\nd\n',parent,{oldStart:2,oldCount:1,newStart:2,newCount:1}),parent);
 assert.equal(revertHunk('a\nb\nX\nY\nc\nd\n',parent,{oldStart:2,oldCount:0,newStart:3,newCount:2}),parent);
 assert.equal(revertHunk('a\nd\n',parent,{oldStart:2,oldCount:2,newStart:1,newCount:0}),parent);
 assert.equal(revertHunk('a\n',parent,{oldStart:9,oldCount:1,newStart:9,newCount:1}),null,'out-of-range hunks are rejected');
 assert.deepEqual(parseUnifiedZeroHunks('@@ -2 +2 @@\n-b\n+B\n@@ -4,0 +5,2 @@\n+x\n+y\n'),
  [{oldStart:2,oldCount:1,newStart:2,newCount:1},{oldStart:4,oldCount:0,newStart:5,newCount:2}]);
});

test('baseline passing count excludes crashed test files and failures',()=>{
 const base={profile:'node-continuum',passed:false,testProfile:true,tests:{total:94,placeholders:3,executed:91,failed:5,passed:89,skipped:0}};
 const after=e=>({profile:'node-continuum',passed:true,testProfile:true,tests:{total:e,placeholders:0,executed:e,failed:0,passed:e,skipped:0}});
 assert.equal(evidenceSufficient([base],[after(88)]).sufficient,false,'one previously passing test no longer runs');
 assert.equal(evidenceSufficient([base],[after(89)]).sufficient,true);
});

test('success requires executed tests that do not shrink against the baseline',()=>{
 const res=(executed,skipped=0,failed=0,passed=true)=>({profile:'node-continuum',passed,testProfile:true,tests:{executed,skipped,failed,total:executed+skipped}});
 assert.equal(evidenceSufficient([res(10,0,1,false)],[res(10)]).sufficient,true);
 assert.equal(evidenceSufficient([res(10,0,1,false)],[res(0)]).sufficient,false,'no executed tests');
 assert.equal(evidenceSufficient([res(10,0,1,false)],[res(8)]).sufficient,false,'fewer tests than passed before');
 assert.equal(evidenceSufficient([res(10,1,1,false)],[res(9,2)]).sufficient,false,'more skipped tests');
 // a profile the baseline never reached (compile failed first) needs executed > 0
 const unit=e=>({profile:'android-unit',passed:true,testProfile:true,tests:{executed:e,skipped:0,failed:0}});
 const compile={profile:'android-compile',passed:true,testProfile:false,tests:{executed:0,skipped:0,failed:0}};
 assert.equal(evidenceSufficient([{...compile,passed:false}],[compile,unit(40)]).sufficient,true);
 assert.equal(evidenceSufficient([{...compile,passed:false}],[compile,unit(0)]).sufficient,false);
});

function gitRepo(files){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-evidence-'));
 const g=(...a)=>{const r=spawnSync('git',a,{cwd:dir,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();};
 const write=fs_=>{for(const [f,c] of Object.entries(fs_)){fs.mkdirSync(path.dirname(path.join(dir,f)),{recursive:true});fs.writeFileSync(path.join(dir,f),c);}};
 write(files);g('init','-q');g('-c','user.name=t','-c','user.email=t@t','add','.');g('-c','user.name=t','-c','user.email=t@t','commit','-qm','base');
 return {dir,g,write,commit:(msg,next)=>{write(next);g('-c','user.name=t','-c','user.email=t@t','commit','-qam',msg);return g('rev-parse','HEAD');}};
}

test('a test profile without executed tests is not a pass',{skip:nested},()=>{
 const {dir}=gitRepo({'.github/continuum/empty.test.mjs':"import fs from 'node:fs';\n"});
 const out=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-evidence-out-'));
 try{
  const r=runProfile('node-continuum',dir,out);
  assert.equal(r.exitCode,0,'node itself exits successfully');
  assert.equal(r.tests.placeholders,1);assert.equal(r.tests.executed,0);
  assert.equal(r.passed,false,'exit 0 without executed tests must not count as verified');
 }finally{fs.rmSync(dir,{recursive:true,force:true});fs.rmSync(out,{recursive:true,force:true});}
});

test('regression search reverts only the culprit hunk and keeps unrelated changes',{skip:nested},()=>{
 const math="export function square(x){return x*x;}\n\nexport function quad(x){return square(square(x));}\n\n// end\n";
 const repo=gitRepo({'.github/continuum/math.mjs':math,
  '.github/continuum/math.test.mjs':"import test from 'node:test';\nimport assert from 'node:assert/strict';\nimport {quad} from './math.mjs';\ntest('quad',()=>assert.equal(quad(2),16));\n"});
 // one commit with an unrelated addition AND a behavior regression in the same file
 const broken="export function square(x){return x+x;}\n\nexport function quad(x){return square(square(x));}\n\n// end\nexport function cube(x){return x*x*x;}\n";
 const head=repo.commit('mixed change',{'.github/continuum/math.mjs':broken});
 const out=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-hunk-out-'));
 try{
  const wt=createWorktree(repo.dir,head);
  try{
   const report=repairSession({wt,base:head,chain:'node',out,ledger:emptyLedger()});
   assert.equal(report.outcome,'VERIFIED');
   assert.equal(report.verified.strategy,'REVERT_HUNK_TO_PARENT');
   const repaired=fs.readFileSync(path.join(wt,'.github/continuum/math.mjs'),'utf8');
   assert.match(repaired,/return x\*x;/,'culprit hunk restored');
   assert.match(repaired,/export function cube/,'unrelated change kept');
   const patch=fs.readFileSync(path.join(out,'repair.patch'),'utf8');
   assert.ok(!/cube/.test(patch),'patch does not touch the unrelated hunk');
   const verifiedAttempt=report.attempts.find(a=>a.outcome==='VERIFIED');
   assert.ok(verifiedAttempt.tests.every(t=>!t.testProfile||t.tests.executed>0));
   assert.equal(verifiedAttempt.evidence.sufficient,true);
  }finally{removeWorktree(repo.dir,wt);}
  assert.equal(repo.g('status','--porcelain'),'','caller checkout untouched');
  assert.equal(repo.g('worktree','list','--porcelain').split('\n').filter(l=>l.startsWith('worktree ')).length,1,'no leftover worktree');
 }finally{fs.rmSync(repo.dir,{recursive:true,force:true});fs.rmSync(out,{recursive:true,force:true});}
});

test('a module that crashes while loading is located and gets its missing import back',{skip:nested},()=>{
 const lib="import {other,hash} from './util.mjs';\nexport const value=hash('abc')+other();\n";
 const repo=gitRepo({'.github/continuum/util.mjs':"export function hash(s){return s.length;}\nexport function other(){return 1;}\n",
  '.github/continuum/lib.mjs':lib,
  '.github/continuum/lib.test.mjs':"import test from 'node:test';\nimport assert from 'node:assert/strict';\nimport {value} from './lib.mjs';\ntest('value',()=>assert.equal(value,4));\n"});
 const head=repo.commit('drop import',{'.github/continuum/lib.mjs':lib.replace('{other,hash}','{other}')});
 const out=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-load-out-'));
 try{
  const wt=createWorktree(repo.dir,head);
  try{
   const report=repairSession({wt,base:head,chain:'node',out,ledger:emptyLedger()});
   // the crash is located in lib.mjs (not only "test failed" for the test file)
   const located=report.baseline.diagnostics.find(d=>d.kind==='REFERENCE_ERROR');
   assert.deepEqual([located?.symbol,located?.file,located?.line],['hash','.github/continuum/lib.mjs',2]);
   assert.equal(report.outcome,'VERIFIED');
   assert.equal(report.verified.strategy,'JS_MISSING_IMPORT');
   assert.equal(fs.readFileSync(path.join(wt,'.github/continuum/lib.mjs'),'utf8'),lib);
  }finally{removeWorktree(repo.dir,wt);}
 }finally{fs.rmSync(repo.dir,{recursive:true,force:true});fs.rmSync(out,{recursive:true,force:true});}
});
