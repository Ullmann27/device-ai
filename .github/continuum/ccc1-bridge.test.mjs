// CCC1 through the Android bridge exactly as Termux runs it: the fixed builder script as its own process with an
// argument array (no shell), JSON on stdout, exit code 2 for every refusal. Covers the owner chain
// submit -> run-once -> status -> abort on a throw-away repository with a real regression, and the negative paths
// that only exist at this boundary (order replay, lost return channel, worker crash, refusals as data).
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {parseArgs,CCC1_COMMANDS} from '../../scripts/continuum-mobile-builder.mjs';

process.env.HOME=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-bridge-home-'));
const nested=Boolean(process.env.CONTINUUM_REPAIR_DEPTH);   // never start real repair sessions inside a repair worktree
const BUILDER=fileURLToPath(new URL('../../scripts/continuum-mobile-builder.mjs',import.meta.url));
const GOOD='/** Slug of a title for file names. */\nexport function slug(title){\n  return title.trim().toLowerCase().replace(/\\s+/g,"-");\n}\n';

/** Throw-away checkout with the CCC1 runtime (the preflight requires it) on an isolated branch. */
function fixture({broken=true}={}){
 const home=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-bridge-'));
 const root=path.join(home,'device-ai'),dir=path.join(root,'.github','continuum');
 fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'text-utils.mjs'),GOOD);
 fs.writeFileSync(path.join(dir,'text-utils.test.mjs'),"import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
  "import {slug} from './text-utils.mjs';\ntest('slug',()=>assert.equal(slug(' Hallo Welt '),'hallo-welt'));\n");
 for(const name of ['ccc1-cli.mjs','ccc1-worker.mjs','ccc1-path-policy.mjs','local-teamleader-loop.mjs'])
  fs.copyFileSync(new URL('./'+name,import.meta.url),path.join(dir,name));
 const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:15000});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 const commit=msg=>{git('add','.');git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm',msg);};
 git('init','-q','-b','ccc1-work');commit('seed');
 if(broken){fs.writeFileSync(path.join(dir,'text-utils.mjs'),GOOD.replace('toLowerCase','toUpperCase'));commit('regression: slug upper-cases');}
 return {home,root,git,state:path.join(home,'ccc1-state')};
}

/** One Termux RUN_COMMAND: node <builder> <op> <validated args>; stdout must be exactly one JSON document. */
function bridge(f,op,args=[],env={}){
 const r=spawnSync(process.execPath,[BUILDER,op,'--project',f.root,'--state-dir',f.state,...args],
  {cwd:f.root,encoding:'utf8',timeout:600000,env:{...process.env,NODE_TEST_CONTEXT:'',...env}});
 let json=null;
 try{json=JSON.parse(r.stdout);}catch{}
 return {code:r.status,signal:r.signal,json,stderr:r.stderr};
}

test('the bridge accepts only the fixed CCC1 operations with validated values',()=>{
 assert.deepEqual([...CCC1_COMMANDS],['ccc1-compile-repair','ccc1-submit-repair','ccc1-run-once','ccc1-status','ccc1-abort']);
 const order=randomUUID();
 assert.equal(parseArgs(['ccc1-submit-repair','--chain','node','--order',order]).order,order);
 assert.match(parseArgs(['ccc1-status']).stateDir,/device-ai-continuum[/\\]ccc1$/,'CCC1 state has its own default outside the checkout');
 for(const [args,re] of [
  [['ccc1-submit-repair','--chain','node'],/--order is required/],
  [['ccc1-submit-repair','--chain','node; rm -rf /','--order',order],/--chain must be/],
  [['ccc1-submit-repair','--chain','node','--order','ABC'],/lowercase UUIDv4/],
  [['ccc1-run-once','--chain','node'],/--chain is required for repair orders only/],
  [['ccc1-abort'],/--task is required/],
  [['ccc1-abort','--task','t-123'],/CCC1 task id/],
  [['ccc1-status','--goal','anything'],/Unsupported argument/],
  [['ccc1-status','--module','x.mjs'],/Unsupported argument/],
  [['ccc1-status','--offline'],/does not take --offline/],
  [['ccc1-cycle'],/Usage/]])
  assert.throws(()=>parseArgs(args),re,args.join(' '));
});

test('owner chain over the bridge: real regression -> verified patch for review; replays never run again',{skip:nested,timeout:600000},()=>{
 const f=fixture();
 const compiled=bridge(f,'ccc1-compile-repair',['--chain','node']);
 assert.equal(compiled.code,0,compiled.stderr);
 assert.equal(compiled.json.schema,'continuum-ccc1-bridge');
 assert.equal(compiled.json.result.status,'READY',JSON.stringify(compiled.json));
 const order=randomUUID();
 const sub=bridge(f,'ccc1-submit-repair',['--chain','node','--order',order]);
 assert.equal(sub.code,0,sub.stderr);
 assert.equal(sub.json.status,'OK');
 const {taskId}=sub.json.result;
 assert.equal(sub.json.result.duplicate,false);
 assert.equal(sub.json.result.sourceCommit,f.git('rev-parse','HEAD'));
 // the return channel was lost and the app sends the same order again: same task, nothing new
 const again=bridge(f,'ccc1-submit-repair',['--chain','node','--order',order]);
 assert.deepEqual([again.json.result.taskId,again.json.result.duplicate],[taskId,true]);
 // a double tap with a new request id while the task is open: the open task is reused
 assert.equal(bridge(f,'ccc1-submit-repair',['--chain','node','--order',randomUUID()]).json.result.taskId,taskId);

 const run=bridge(f,'ccc1-run-once');
 assert.equal(run.code,0,run.stderr);
 assert.equal(run.json.result.ccc1,'COMPLETED',JSON.stringify(run.json));
 assert.equal(run.json.result.outcome,'VERIFIED_PATCH_PENDING_REVIEW');
 const status=bridge(f,'ccc1-status');
 assert.equal(status.code,0);
 const t=status.json.result.tasks.find(x=>x.taskId===taskId);
 assert.equal(t.state,'COMPLETED');
 assert.match(t.confirmed,/^repair:node@[0-9a-f]{12}$/);
 assert.equal(status.json.result.tasks.length,1,'replays created no second task');
 // the owner sees the confirmed patch itself, re-hashed from disk; it is only offered: the checkout is untouched
 assert.equal(t.review.intact,true);
 assert.equal(t.review.patchSha256.slice(0,12),t.confirmed.split('@')[1]);
 assert.deepEqual(t.review.files,['.github/continuum/text-utils.mjs']);
 assert.match(t.review.preview,/^-.*toUpperCase.*\n\+.*toLowerCase/m);
 assert.equal(f.git('status','--porcelain'),'');
 const original=fs.readFileSync(t.review.path);
 fs.appendFileSync(t.review.path,'+ injected\n');
 const tampered=bridge(f,'ccc1-status').json.result.tasks[0].review;
 assert.deepEqual([tampered.intact,tampered.preview],[false,null],'a changed patch file is never shown as the confirmed patch');
 fs.writeFileSync(t.review.path,original);

 // re-running the finished order: answered from the record, nothing executes
 const replay=bridge(f,'ccc1-submit-repair',['--chain','node','--order',order]);
 assert.deepEqual([replay.json.result.taskId,replay.json.result.duplicate],[taskId,true]);
 const idle=bridge(f,'ccc1-run-once');
 assert.equal(idle.json.result.outcome,'IDLE');
 const late=bridge(f,'ccc1-abort',['--task',taskId]);
 assert.equal(late.code,0);
 assert.deepEqual([late.json.result.journal,late.json.result.state],['ALREADY_ENDED','COMPLETED'],'a finished task cannot be cancelled into another state');
});

test('a worker crash before execution is never a success; the owner cancels the pending order cleanly',{skip:nested,timeout:600000},()=>{
 const f=fixture();
 const sub=bridge(f,'ccc1-submit-repair',['--chain','node','--order',randomUUID()]);
 const {taskId}=sub.json.result;
 const crashed=bridge(f,'ccc1-run-once',[],{CCC1_CRASH_AT:'before-execute'});
 assert.equal(crashed.signal,'SIGKILL');
 assert.equal(crashed.json,null,'a killed worker returns nothing the app could read as a result');
 let s=bridge(f,'ccc1-status').json.result.tasks[0];
 assert.equal(s.state,'APPROVED','the request is journaled and pending, not done');
 const abort=bridge(f,'ccc1-abort',['--task',taskId]);
 assert.equal(abort.code,0,abort.stderr);
 assert.equal(abort.json.result.journal,'ABORTED');
 assert.equal(abort.json.result.state,'ABORTED');
 assert.equal(abort.json.result.job.status,'BLOCKED_NO_SUCH_JOB','the job had not started; nothing to stop');
 assert.equal(bridge(f,'ccc1-run-once').json.result.outcome,'IDLE','an aborted task never runs');
 s=bridge(f,'ccc1-status').json.result.tasks[0];
 assert.deepEqual([s.state,s.confirmed],['ABORTED',null]);
 assert.equal(bridge(f,'ccc1-abort',['--task','t-'+'0'.repeat(16)]).json.failure.code,'CCC1_NO_SUCH_TASK');
});

test('refusals are data with exit code 2: protected branch, dirty tree, state in or linked into the checkout, damaged journal',{timeout:120000},()=>{
 const f=fixture({broken:false});
 f.git('checkout','-q','-b','main');
 let r=bridge(f,'ccc1-submit-repair',['--chain','node','--order',randomUUID()]);
 assert.equal(r.code,2);
 assert.equal(r.json.status,'BLOCKED_PREFLIGHT');
 assert.match(r.json.failure.message,/CCC1_BRANCH_NOT_ISOLATED/);
 f.git('checkout','-q','ccc1-work');
 fs.writeFileSync(path.join(f.root,'stray.txt'),'x');
 r=bridge(f,'ccc1-submit-repair',['--chain','node','--order',randomUUID()]);
 assert.deepEqual([r.code,r.json.status],[2,'BLOCKED_PREFLIGHT']);
 assert.match(r.json.failure.message,/CCC1_WORKTREE_NOT_CLEAN/);
 fs.rmSync(path.join(f.root,'stray.txt'));
 r=spawnSync(process.execPath,[BUILDER,'ccc1-status','--project',f.root,'--state-dir',path.join(f.root,'state')],{encoding:'utf8'});
 assert.equal(r.status,2);
 assert.equal(JSON.parse(r.stdout).status,'BLOCKED_STATE_PATH');
 const link=path.join(f.home,'linked-state');
 fs.mkdirSync(path.join(f.root,'.git','x'));fs.symlinkSync(path.join(f.root,'.git','x'),link);
 r=spawnSync(process.execPath,[BUILDER,'ccc1-status','--project',f.root,'--state-dir',link],{encoding:'utf8'});
 assert.deepEqual([r.status,JSON.parse(r.stdout).status],[2,'BLOCKED_STATE_PATH'],'a symlink into the checkout is refused');
 assert.equal(fs.readdirSync(path.join(f.root,'.git','x')).length,0,'nothing was written through the link');
 // damaged journal: listed as CORRUPT, the cycle stops with ERROR and nothing is executed or repaired
 const sub=bridge(f,'ccc1-submit-repair',['--chain','node','--order',randomUUID()]);
 const file=path.join(f.state,'tasks',sub.json.result.taskId+'.jsonl');
 fs.writeFileSync(file,fs.readFileSync(file,'utf8').replace('Testkette','Testkettx'));
 assert.equal(bridge(f,'ccc1-status').json.result.tasks[0].state,'CORRUPT');
 const run=bridge(f,'ccc1-run-once');
 assert.equal(run.json.result.outcome,'ERROR');
 assert.match(run.json.result.detail,/damaged task journal/);
 assert.equal(fs.existsSync(path.join(f.state,'builder')),false,'no job was started');
});
