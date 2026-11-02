import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {submitOnce,runCcc1Cycle,listTasks} from './ccc1-worker.mjs';

function fixture(){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-binding-'));
 const project=path.join(home,'a'),other=path.join(home,'b'),stateDir=path.join(home,'state');
 fs.mkdirSync(project);fs.writeFileSync(path.join(project,'readme.txt'),'synthetic fixture\n');
 const git=(cwd,...args)=>{const p=spawnSync('git',args,{cwd,encoding:'utf8'});assert.equal(p.status,0,p.stderr);return p.stdout.trim();};
 git(project,'init','-q','-b','research/fixture');git(project,'add','.');
 git(project,'-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','seed');
 git(home,'clone','-q',project,other);
 return {project,other,stateDir,git,home,sourceCommit:git(project,'rev-parse','HEAD')};
}
const args=f=>({project:f.project,stateDir:f.stateDir,order:randomUUID(),chain:'node',sourceCommit:f.sourceCommit});

test('same Android order cannot bind another checkout even with identical commit bytes',()=>{
 const f=fixture(),a=args(f);submitOnce(a);
 assert.throws(()=>submitOnce({...a,project:f.other}),/CCC1_CHECKOUT_MISMATCH/);
 assert.equal(listTasks(f.stateDir).length,1);
});
test('new order cannot deduplicate an open goal belonging to another checkout',()=>{
 const f=fixture();submitOnce(args(f));
 assert.throws(()=>submitOnce({...args(f),project:f.other}),/CCC1_CHECKOUT_MISMATCH/);
 assert.equal(listTasks(f.stateDir).length,1);
});
test('stale source supplied to a new order is refused before a task exists',()=>{
 const f=fixture();assert.throws(()=>submitOnce({...args(f),sourceCommit:'f'.repeat(40)}),/CCC1_SOURCE_MISMATCH/);
 assert.equal(listTasks(f.stateDir).length,0);
});
test('same checkout/order is idempotent after module restart and canonical symlink',async()=>{
 const f=fixture(),a=args(f);const first=submitOnce(a);
 const restarted=await import('./ccc1-worker.mjs?restart='+randomUUID());
 const link=path.join(f.home,'alias');fs.symlinkSync(f.project,link,'dir');
 assert.deepEqual(restarted.submitOnce({...a,project:link}),{taskId:first.taskId,duplicate:true});
 assert.equal(listTasks(f.stateDir).length,1);
});
test('bound worker cycle refuses a different checkout before indexing or execution',async()=>{
 const f=fixture();submitOnce(args(f));
 await assert.rejects(runCcc1Cycle({project:f.other,stateDir:f.stateDir,signal:AbortSignal.abort()}),/CCC1_CHECKOUT_MISMATCH/);
 assert.equal(listTasks(f.stateDir)[0].state,'DRAFT');
 assert.equal(fs.existsSync(path.join(f.stateDir,'builder')),false);
});

test('retry cannot relabel an old task with a newer checkout HEAD',()=>{
 const f=fixture(),a=args(f);submitOnce(a);
 fs.appendFileSync(path.join(f.project,'readme.txt'),'new revision\n');
 f.git(f.project,'-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qam','next revision');
 const current=f.git(f.project,'rev-parse','HEAD');
 assert.throws(()=>submitOnce({...a,sourceCommit:current}),/CCC1_ORDER_SOURCE_MISMATCH/);
 assert.equal(listTasks(f.stateDir)[0].baseCommitSha,f.sourceCommit);
});
test('missing legacy binding requires owner review, never automatic state adoption',()=>{
 const f=fixture(),a=args(f);submitOnce(a);
 fs.unlinkSync(path.join(f.stateDir,'checkout-binding.json'));
 assert.throws(()=>submitOnce(a),/CCC1_CHECKOUT_UNBOUND/);
 assert.equal(fs.existsSync(path.join(f.stateDir,'checkout-binding.json')),false);
});
