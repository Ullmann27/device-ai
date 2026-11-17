import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {ExperienceLog} from './ccc1-experience.mjs';
import {canonical} from './ccc1-envelope.mjs';
const sha=v=>createHash('sha256').update(canonical(v)).digest('hex');
const fixture=t=>{const d=fs.mkdtempSync(path.join(os.tmpdir(),'momega1-exp-'));t.after(()=>fs.rmSync(d,{recursive:true,force:true}));return path.join(d,'experience.jsonl');};
test('experience journal refuses complete JSON without newline as uncertain torn append',t=>{
 const file=fixture(t),log=new ExperienceLog(file);log.append({taskId:'a'});
 fs.writeFileSync(file,fs.readFileSync(file,'utf8').trimEnd());
 assert.throws(()=>log.entries(),/torn|damaged/);
});
test('experience journal validates sequence independently of rehashed bytes',t=>{
 const file=fixture(t),log=new ExperienceLog(file);log.append({taskId:'a'});
 const r=JSON.parse(fs.readFileSync(file));r.sequence=10;const {hash,...data}=r;
 fs.writeFileSync(file,JSON.stringify({...data,hash:sha(data)})+'\n');
 assert.throws(()=>log.entries(),/sequence|damaged/);
});
test('experience journal rejects an oversized record before publication',t=>{
 const file=fixture(t);assert.throws(()=>new ExperienceLog(file).append({taskId:'a',data:'x'.repeat(262144)}),/bound|large/);
 assert.equal(fs.existsSync(file),false);
});
test('experience journal refuses a held cross-process writer lease',t=>{
 const file=fixture(t);fs.mkdirSync(file+'.lock');
 assert.throws(()=>new ExperienceLog(file).append({taskId:'a'}),/locked/);
});
test('experience journal refuses symlink storage',t=>{
 const file=fixture(t),other=file+'.target';fs.writeFileSync(other,'');fs.symlinkSync(other,file);
 assert.throws(()=>new ExperienceLog(file).append({taskId:'a'}),/unsafe|symlink/);
 assert.equal(fs.readFileSync(other,'utf8'),'');
});
test('experience journal enforces record and byte hard bounds',t=>{
 const file=fixture(t),rows=[];let prevHash=null;
 for(let sequence=0;sequence<4097;sequence++){
  const data={sequence,prevHash,record:{taskId:'synthetic-'+sequence}};
  prevHash=sha(data);rows.push(JSON.stringify({...data,hash:prevHash}));
 }
 fs.writeFileSync(file,rows.join('\n')+'\n');
 assert.throws(()=>new ExperienceLog(file).entries(),/record bound/);
 fs.writeFileSync(file,'x'.repeat(16*1024*1024+1));
 assert.throws(()=>new ExperienceLog(file).entries(),/byte bound/);
});
test('experience journal rejects blank lines and duplicate tasks even with recomputed hashes',t=>{
 const file=fixture(t),log=new ExperienceLog(file);log.append({taskId:'synthetic'});
 const first=log.entries()[0],data={sequence:1,prevHash:first.hash,record:{taskId:'synthetic'}};
 fs.appendFileSync(file,JSON.stringify({...data,hash:sha(data)})+'\n');assert.throws(()=>log.entries(),/damaged/);
 fs.writeFileSync(file,'\n');assert.throws(()=>log.entries(),/damaged/);
});
