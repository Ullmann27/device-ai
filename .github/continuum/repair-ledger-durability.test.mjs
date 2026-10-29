import test from 'node:test';import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawn,spawnSync} from 'node:child_process';
import {once} from 'node:events';import {setTimeout as delay} from 'node:timers/promises';
import {emptyLedger,loadLedger,saveLedger} from './repair-ledger.mjs';
const moduleUrl=new URL('./repair-ledger.mjs',import.meta.url).href;
function fixture(t){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ledger-durable-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));return {dir,file:path.join(dir,'ledger.json')};}
test('old schema2 data roundtrips with private mode and unique temporary files',t=>{
 const f=fixture(t);saveLedger(f.file,emptyLedger());const original=fs.readFileSync(f.file);
 const orphan=f.file+'.tmp-'+process.pid;fs.writeFileSync(orphan,'unrelated partial checkpoint');
 saveLedger(f.file,loadLedger(f.file));assert.deepEqual(fs.readFileSync(f.file),original);
 assert.equal(fs.statSync(f.file).mode&0o777,0o600);assert.equal(fs.readFileSync(orphan,'utf8'),'unrelated partial checkpoint');
});
test('dangling and live symlink checkpoints fail closed without touching their targets',t=>{
 const f=fixture(t),target=path.join(f.dir,'outside');fs.writeFileSync(target,'sentinel');
 for(const destination of [target,path.join(f.dir,'absent')]){
  fs.symlinkSync(destination,f.file);assert.throws(()=>loadLedger(f.file));assert.throws(()=>saveLedger(f.file,emptyLedger()));fs.unlinkSync(f.file);
 }
 assert.equal(fs.readFileSync(target,'utf8'),'sentinel');
});
test('malformed UTF8 and invalid JSON checkpoints do not become empty history',t=>{
 const f=fixture(t);for(const bytes of [Buffer.from([0xff]),Buffer.from('{partial')]){fs.writeFileSync(f.file,bytes);assert.throws(()=>loadLedger(f.file));}
});
test('a FIFO checkpoint without a writer is refused within a bounded child process',t=>{
 const f=fixture(t);
 const created=spawnSync('mkfifo',[f.file],{encoding:'utf8',timeout:2000});assert.equal(created.status,0,created.stderr);
 const code=`import {loadLedger} from ${JSON.stringify(moduleUrl)};
 try{loadLedger(process.argv[1]);process.exitCode=3;}catch(error){process.exitCode=error.message==='Invalid repair ledger file'?0:2;}`;
 const checked=spawnSync(process.execPath,['--input-type=module','-e',code,f.file],{encoding:'utf8',timeout:1000,killSignal:'SIGKILL'});
 assert.equal(checked.status,0,'invalid FIFO must fail before blocking open: '+checked.error?.code);
});
test('real SIGKILL before file fsync preserves last committed checkpoint and allows restart',{timeout:10000},async t=>{
 const f=fixture(t);saveLedger(f.file,emptyLedger());const original=fs.readFileSync(f.file);
 const marker=path.join(f.dir,'before-sync');
 // Fault injection pauses at the real durability boundary; no successful write is faked.
 const program=`import fs from 'node:fs';import {saveLedger,emptyLedger} from ${JSON.stringify(moduleUrl)};
 fs.fsyncSync=()=>{fs.writeFileSync(process.argv[2],'ready');Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,30000);};
 const state=emptyLedger();state.experiences=[{kind:'public-fixture',successes:1,failures:0}];saveLedger(process.argv[1],state);`;
 const child=spawn(process.execPath,['--input-type=module','-e',program,f.file,marker],{stdio:'ignore'});
 const exited=once(child,'exit');t.after(()=>child.kill('SIGKILL'));
 const end=Date.now()+5000;while(!fs.existsSync(marker)&&Date.now()<end)await delay(10);
 assert.ok(fs.existsSync(marker),'child must reach actual fsync boundary');child.kill('SIGKILL');const [,signal]=await exited;assert.equal(signal,'SIGKILL');
 assert.deepEqual(fs.readFileSync(f.file),original);assert.deepEqual(loadLedger(f.file),emptyLedger());
 saveLedger(f.file,{...emptyLedger(),experiences:[{kind:'after-restart',successes:1,failures:0}]});
 assert.equal(loadLedger(f.file).experiences[0].kind,'after-restart');
});
