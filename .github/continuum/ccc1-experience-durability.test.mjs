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
