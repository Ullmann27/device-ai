import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {WORKER_LESSONS,FUTURE_DISCIPLINES,
 practiceLesson,academyStatus,academyCliArgs} from './worker-academy.mjs';

function fixture(){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-academy-test-'));
 const root=path.join(base,'git');
 const state=path.join(base,'workstate');
 fs.mkdirSync(root,{recursive:true});
 execFileSync('git',['init','-q'],{cwd:root});
 execFileSync('git',['config','user.name','Academy Test'],{cwd:root});
 execFileSync('git',['config','user.email','test@example.invalid'],{cwd:root});
 for(const lesson of WORKER_LESSONS){
  for(const name of [lesson.source,lesson.test]){
   const file=path.join(root,name);
   fs.mkdirSync(path.dirname(file),{recursive:true});
   fs.writeFileSync(file,'export const value=1;\n');
  }
 }
 execFileSync('git',['add','.'],{cwd:root});
 execFileSync('git',['commit','-qm','immutable academy baseline'],{cwd:root});
 return {base,root,state,cleanup:()=>fs.rmSync(base,{recursive:true,force:true})};
}

test('academy distinguishes testable coding practice from unavailable iOS and regulated domains',()=>{
 assert.ok(WORKER_LESSONS.length>=6);
 const ids=WORKER_LESSONS.map(x=>x.id);
 assert.equal(new Set(ids).size,ids.length);
 const ios=FUTURE_DISCIPLINES.find(x=>x.id==='ios-swift');
 const finance=FUTURE_DISCIPLINES.find(x=>x.id==='financial-systems');
 assert.equal(ios.state,'NEEDS_MACOS_TOOLCHAIN');
 assert.equal(finance.state,'NO_DOMAIN_CERTIFICATION');
});

test('only bounded, fixed tests are executed, with source and test digests',()=>{
 const w=fixture();
 try{
  const requests=[];
  const fake=(cmd,args,options)=>{
   requests.push({cmd,args,options});
   return {status:0,stdout:'# tests 7\n# fail 0\n',stderr:''};
  };
  const result=practiceLesson({root:w.root,state:w.state,run:fake,clock:()=>123456});
  assert.equal(result.passed,true);
  assert.equal(result.executed,7);
  assert.equal(result.lesson,'android-architecture');
  assert.equal(result.userSecretsRead,false);
  assert.equal(result.automaticMerge,false);
  assert.match(result.sourceSha256,/^[a-f0-9]{64}$/);
  assert.match(result.testSha256,/^[a-f0-9]{64}$/);
  assert.equal(requests.length,1);
  assert.equal(requests[0].cmd,process.execPath);
  assert.equal(requests[0].options.shell,false);
  assert.equal(requests[0].args[0],'--test');
  const status=academyStatus({root:w.root,state:w.state});
  assert.equal(status.completedPracticeRuns,1);
  assert.equal(status.latest[0].verifiedForCurrentHead,true);
  assert.equal(status.latest[1].status,'NOT_PRACTICED');
  assert.equal(fs.statSync(path.join(w.state,'worker-academy-v1.json')).mode&0o077,0);
 }finally{w.cleanup();}
});

test('repeated autonomous runs rotate to the next subject and do not rewrite source',()=>{
 const w=fixture();
 try{
  const before=execFileSync('git',['rev-parse','HEAD'],{cwd:w.root,encoding:'utf8'}).trim();
  const fake=()=>({status:0,stdout:'# tests 3\n# fail 0\n',stderr:''});
  const first=practiceLesson({root:w.root,state:w.state,run:fake});
  const second=practiceLesson({root:w.root,state:w.state,run:fake});
  assert.equal(first.lesson,WORKER_LESSONS[0].id);
  assert.equal(second.lesson,WORKER_LESSONS[1].id);
  assert.equal(academyStatus({root:w.root,state:w.state}).completedPracticeRuns,2);
  assert.equal(execFileSync('git',['rev-parse','HEAD'],{cwd:w.root,encoding:'utf8'}).trim(),before);
  assert.equal(execFileSync('git',['status','--porcelain'],{cwd:w.root,encoding:'utf8'}),'');
 }finally{w.cleanup();}
});

test('zero executed tests, failure and timeout never become acquired skills',()=>{
 const w=fixture();
 try{
  const status0=practiceLesson({root:w.root,state:w.state,
   run:()=>({status:0,stdout:'# tests 0\n# fail 0\n'})});
  assert.equal(status0.passed,false);
  assert.equal(status0.status,'NOT_EXECUTED');
  const failed=practiceLesson({root:w.root,state:w.state,
   run:()=>({status:1,stdout:'# tests 3\n# fail 1\n'})});
  assert.equal(failed.passed,false);
  assert.equal(failed.status,'FAILED');
  const timedOut=practiceLesson({root:w.root,state:w.state,
   run:()=>({status:null,error:{code:'ETIMEDOUT'},stdout:''})});
  assert.equal(timedOut.status,'TIMEOUT');
  assert.equal(academyStatus({root:w.root,state:w.state}).completedPracticeRuns,0);
 }finally{w.cleanup();}
});

test('corrupt evidence, concurrent lock, symlink lessons and dirty checkouts fail closed',()=>{
 const w=fixture();
 try{
  fs.mkdirSync(w.state,{recursive:true});
  const lock=path.join(w.state,'worker-academy.lock');
  fs.writeFileSync(lock,'other process');
  assert.throws(()=>practiceLesson({root:w.root,state:w.state}),/ACADEMY_BUSY/);
  fs.unlinkSync(lock);
  fs.writeFileSync(path.join(w.state,'worker-academy-v1.json'),'{bad');
  assert.throws(()=>practiceLesson({root:w.root,state:w.state}),SyntaxError);
  fs.unlinkSync(path.join(w.state,'worker-academy-v1.json'));
  const file=path.join(w.root,WORKER_LESSONS[0].source);
  fs.unlinkSync(file);
  fs.symlinkSync(path.join(w.root,WORKER_LESSONS[0].test),file);
  assert.throws(()=>practiceLesson({root:w.root,state:w.state}),/dirty source checkout/);
  fs.unlinkSync(file);
  fs.writeFileSync(file,'export const value=1;\n');
  fs.writeFileSync(path.join(w.root,'untracked.txt'),'some change');
  assert.throws(()=>practiceLesson({root:w.root,state:w.state}),/dirty checkout/);
 }finally{w.cleanup();}
});

test('academy CLI cannot accept commands, scripts or arbitrary tests',()=>{
 assert.equal(academyCliArgs(['--mode','practice']).mode,'practice');
 for(const args of [
  ['--mode','shell'],['--mode','practice','--cmd','bash'],
  ['--mode','practice','--mode','status'],['--mode'],
  ['--mode','practice','--test','arbitrary.js']
 ])assert.throws(()=>academyCliArgs(args));
});
