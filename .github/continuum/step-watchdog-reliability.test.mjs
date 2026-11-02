import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {runProfile} from './repair-loop.mjs';

const WATCHDOG=fileURLToPath(new URL('./step-watchdog.mjs',import.meta.url));
const linux=process.platform==='linux';
function fixture(t,preload='') {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-reliability-'));
  t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const hook=path.join(dir,'hook.cjs');fs.writeFileSync(hook,preload);
  return {dir,receipt:path.join(dir,'receipt.json'),backlog:path.join(dir,'missing.json'),
    env:{...process.env,NODE_OPTIONS:`--require=${hook}`}};
}
const denyProc=`const fs=require('node:fs');const read=fs.readFileSync;
fs.readFileSync=(file,...args)=>{if(/^\\/proc\\/[0-9]+\\/stat$/.test(String(file)))throw Object.assign(Error('synthetic denial'),{code:'EACCES'});return read(file,...args);};`;
function watch(f,code) {
  return spawnSync(process.execPath,[WATCHDOG,f.backlog,'repair-watched',f.receipt,'5000',String(process.pid),'--',process.execPath,'-e',code],
    {env:f.env,encoding:'utf8',timeout:10000});
}
test('unreadable proc cannot turn a live detached child into watchdog success',{skip:!linux},t=>{
  const f=fixture(t,denyProc),marker=path.join(f.dir,'pid');
  const code=`const {spawn}=require('node:child_process');const p=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{detached:true,stdio:'ignore'});require('node:fs').writeFileSync(${JSON.stringify(marker)},String(p.pid));p.unref();`;
  const r=watch(f,code),pid=Number(fs.readFileSync(marker,'utf8'));
  t.after(()=>{try{process.kill(pid,'SIGKILL');}catch{}});
  assert.doesNotThrow(()=>process.kill(pid,0),'fixture really leaves an untracked child');
  assert.equal(r.error,undefined);assert.equal(r.status,1);
  const completion=JSON.parse(fs.readFileSync(f.receipt+'.completion.json','utf8'));
  assert.equal(completion.code,0,'actual command exit is preserved');
  assert.equal(completion.terminationConfirmed,false);
  assert.equal(completion.error,'TERMINATION_UNCONFIRMED');
});
for(const [name,hook,confirmed] of [
  ['confirmed', '', true], ['denied proc',denyProc,false],
  ['missing completion',`const fs=require('node:fs');const write=fs.writeFileSync;fs.writeFileSync=(p,...a)=>String(p).endsWith('.completion.json')?undefined:write(p,...a);`,false],
  ['wrong task',`const fs=require('node:fs');const write=fs.writeFileSync;fs.writeFileSync=(p,data,...a)=>write(p,String(p).endsWith('.completion.json')?JSON.stringify({...JSON.parse(data),taskId:'wrong-task'}):data,...a);`,false]
])test('repair profile requires a valid completion: '+name,{skip:!linux||Boolean(process.env.CONTINUUM_REPAIR_DEPTH)},t=>{
  const f=fixture(t,hook),wt=path.join(f.dir,'wt'),out=path.join(f.dir,'out');
  fs.mkdirSync(path.join(wt,'.github/continuum'),{recursive:true});fs.mkdirSync(out);
  fs.writeFileSync(path.join(wt,'.github/continuum/pass.test.mjs'),"import test from 'node:test';test('real pass',()=>{});");
  const original=process.env.NODE_OPTIONS;
  process.env.NODE_OPTIONS=f.env.NODE_OPTIONS;
  let result;try{result=runProfile('node-continuum',wt,out,{abortWatch:{backlog:f.backlog,taskId:'repair-watched'}});}
  finally{if(original===undefined)delete process.env.NODE_OPTIONS;else process.env.NODE_OPTIONS=original;}
  assert.equal(result.tests.executed,1);
  assert.equal(result.passed,confirmed);
  assert.equal(result.terminationConfirmed,confirmed);
  if(!confirmed)assert.equal(result.error,'TERMINATION_UNCONFIRMED');
});
