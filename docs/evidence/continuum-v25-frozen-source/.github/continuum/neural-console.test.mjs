import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {SubwordEncoder} from './portable-subword.mjs';
const script=fileURLToPath(new URL('./zero-cost-core.mjs',import.meta.url));
test('real console persists training, pause and compatible export across processes',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-console-'));
 const run=(comment,user='Ullmann27')=>spawnSync(process.execPath,[script],{cwd:dir,encoding:'utf8',timeout:10000,env:{...process.env,GITHUB_TOKEN:'',CONTINUUM_COPILOT:'0',CONTINUUM_EVENT:'issue_comment',CONTINUUM_REPOSITORY:'Ullmann27/device-ai',CONTINUUM_COMMENT_USER:user,CONTINUUM_COMMENT:comment}});
 try{
  assert.equal(run('/train').status,0);
  const read=()=>JSON.parse(fs.readFileSync(path.join(dir,'.continuum/state.json')));
  const s=read();assert.ok(s.neural?.accepted>=1);
  const bytes=fs.readFileSync(path.join(dir,'.continuum/neural-public.bin'));assert.ok(SubwordEncoder.restore(bytes).updates>0);
  const manifest=JSON.parse(fs.readFileSync(path.join(dir,'.continuum/neural-public.json')));assert.equal(manifest.modelHash,s.neural.modelHash);
  assert.equal(run('/learn Mein privates Kennwort bleibt privat.').status,0);
  assert.equal(run('/pause').status,0);
  assert.equal(run('/train').status,0);assert.equal(read().neural.modelHash,s.neural.modelHash);
  const raw=fs.readFileSync(path.join(dir,'.continuum/state.json'),'utf8');
  assert.equal(run('/resume','stranger').status,0);assert.equal(fs.readFileSync(path.join(dir,'.continuum/state.json'),'utf8'),raw);
  assert.equal(run('/chat Was weißt du über mein Kennwort?').status,0);
  assert.match(read().dialogue.turns.at(-1).answer,/Kennwort/);
  assert.deepEqual(bytes,fs.readFileSync(path.join(dir,'.continuum/neural-public.bin')));
  fs.writeFileSync(path.join(dir,'.continuum/state.json'),raw.replace('privat','verändert'));
  assert.notEqual(run('/status').status,0);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
