// M14 · real-process chaos for the EXISTING teamleader: SIGKILL in the middle of a repair, two workers at once,
// a torn state file and a missing PATH. Each worker is a separate Node process running runTeamleaderCycle with a
// recording fake driver; nothing here changes the teamleader, it only checks what it does after real crashes.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
import {decodeLeaderState} from './autonomous-teamleader.mjs';

const MODULE=new URL('./autonomous-teamleader.mjs',import.meta.url).pathname;
const CLI=new URL('./teamleader-cli.mjs',import.meta.url).pathname;
const CHILD=`
import fs from 'node:fs';
import {runTeamleaderCycle} from ${JSON.stringify(MODULE)};
const [root,stateDir,calls,mode]=process.argv.slice(1);
const sleep=ms=>Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,ms);
const driver={head:()=> 'a'.repeat(40),readyInvestigation:()=>null,prepareSafeInvestigation:()=>null,
 repair:()=>{fs.appendFileSync(calls,'repair '+process.pid+'\\n');
  if(mode==='hang')sleep(60000);if(mode==='slow')sleep(1500);return {outcome:'NO_FAILURE'};},
 investigate:()=>({status:'DONE',staticResult:'REPRODUCED_STATIC_FINDING',executed:1,failed:0})};
const now=mode==='later'?()=>Date.now()+6*60_000:Date.now;   // 'later': the owner restarts after the 5-minute lock grace
try{const r=runTeamleaderCycle({root,stateDir,driver,now});console.log('OUTCOME '+r.outcome);}
catch(e){console.log('REFUSED '+e.message);process.exitCode=3;}
`;
function sandbox(fn){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'teamleader-chaos-'));
 const ctx={root:path.join(dir,'source'),stateDir:path.join(dir,'state'),calls:path.join(dir,'calls.log')};
 fs.mkdirSync(ctx.root);
 return Promise.resolve(fn(ctx)).finally(()=>fs.rmSync(dir,{recursive:true,force:true}));
}
const args=(c,mode)=>['--input-type=module','-e',CHILD,c.root,c.stateDir,c.calls,mode];
const run=(c,mode='normal',env=process.env)=>spawnSync(process.execPath,args(c,mode),{encoding:'utf8',timeout:20000,env});
const repairs=c=>fs.existsSync(c.calls)?fs.readFileSync(c.calls,'utf8').trim().split('\n').filter(Boolean).length:0;
const statePath=c=>path.join(c.stateDir,'teamleader-state.json');
const waitFor=async(pred,ms=10000)=>{const end=Date.now()+ms;while(Date.now()<end){if(pred())return true;await new Promise(r=>setTimeout(r,20));}return false;};

test('SIGKILL during a repair: refused within the 5-minute lock grace, then recovered with INTERRUPTED and no repeated repair',()=>sandbox(async c=>{
 const child=spawn(process.execPath,args(c,'hang'),{stdio:'ignore'});
 assert.ok(await waitFor(()=>repairs(c)===1),'repair started');
 child.kill('SIGKILL');
 await new Promise(r=>child.on('exit',r));
 const soon=run(c);                                                     // a dead owner's fresh lock: fail closed
 assert.equal(soon.status,3,soon.stdout+soon.stderr);
 assert.match(soon.stdout,/REFUSED Teamleader already running or lock requires recovery/);
 assert.equal(repairs(c),1);
 const blocked=run(c,'later');                                          // lock grace over, but the build lease
 assert.match(blocked.stdout,/REFUSED CONTINUUM_BUILD_LEASE_BUSY/);       // is never freed by age alone
 const owner=spawnSync(process.execPath,[CLI,'--release-dead-lease','--root',c.root,'--state',c.stateDir],{encoding:'utf8'});
 assert.equal(owner.status,0,owner.stdout+owner.stderr);                // explicit owner step: holder provably dead
 assert.equal(JSON.parse(owner.stdout).reason,'HOLDER_DEAD');
 const after=run(c,'later');
 assert.equal(after.status,0,after.stdout+after.stderr);
 assert.match(after.stdout,/OUTCOME /);
 const state=decodeLeaderState(fs.readFileSync(statePath(c)));         // integrity-checked decode
 assert.ok(state.events.some(e=>e.outcome==='INTERRUPTED'),'crash recorded as INTERRUPTED, never as success');
 assert.ok(!state.events.some(e=>e.outcome==='RUNNING'&&!state.events.some(f=>f.id===e.id&&f.outcome!=='RUNNING')),'no dangling RUNNING');
 assert.equal(repairs(c),1,'the interrupted repair is not silently repeated on the same HEAD');
}));

test('two workers at once on one state: exactly one repairs, the other is refused',()=>sandbox(async c=>{
 const spawnOne=()=>new Promise(res=>{const p=spawn(process.execPath,args(c,'slow'),{stdio:['ignore','pipe','pipe']});
  let out='';p.stdout.on('data',d=>out+=d);p.stderr.on('data',d=>out+=d);p.on('exit',code=>res({code,out}));});
 const [a,b]=await Promise.all([spawnOne(),spawnOne()]);
 assert.equal(repairs(c),1,'one repair only');
 assert.equal([a,b].filter(x=>/OUTCOME /.test(x.out)).length,1,a.out+'\n'+b.out);
 assert.equal([a,b].filter(x=>/REFUSED /.test(x.out)).length,1,a.out+'\n'+b.out);
}));

test('a torn state file (power loss mid-write) fails closed and is not overwritten',()=>sandbox(async c=>{
 assert.equal(run(c).status,0);
 const good=fs.readFileSync(statePath(c));
 const torn=good.subarray(0,Math.floor(good.length/2));
 fs.writeFileSync(statePath(c),torn);
 const r=run(c);
 assert.equal(r.status,3,r.stdout+r.stderr);
 assert.match(r.stdout,/REFUSED /);
 assert.deepEqual(fs.readFileSync(statePath(c)),torn,'evidence of the torn write is preserved for the owner');
 assert.equal(repairs(c),1,'no repair after a state it cannot trust');
}));

test('a missing PATH (no git, no tools) does not corrupt state; the core cycle needs no external tool',()=>sandbox(async c=>{
 const r=run(c,'normal',{HOME:os.tmpdir()});
 assert.equal(r.status,0,r.stdout+r.stderr);
 decodeLeaderState(fs.readFileSync(statePath(c)));
}));
