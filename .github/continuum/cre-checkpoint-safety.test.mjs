import test from 'node:test';
import assert from 'node:assert/strict';
import { bucket, RelationalEncoder, features } from './cre-encoder.mjs';
import { trainedVocabulary } from './relational-retrieval-audit.mjs';
import { prepare } from './cre-parity.mjs';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';

test('CRE1 unseen-name gate uses exact normalized words, never 16k hash buckets',()=>{
 const trained=trainedVocabulary(
  [{query:'Wann hat Anna Geburtstag?',memory:'m'}],
  [{id:'m',text:'Anna ist am 3. Mai geboren.'}]
 );
 assert.equal(trained.has('anna'),true);
 assert.equal(trained.has(bucket('w:anna')),false);
 const known=bucket('w:anna');
 let collision=null;
 for(let i=0;i<100000;i++){
  const candidate='personx'+i;
  if(bucket('w:'+candidate)===known){collision=candidate;break;}
 }
 assert.ok(collision,'expected FNV collision in 16,384 buckets');
 assert.equal(trained.has(collision),false,'unknown person must remain unknown despite hash collision');
});

test('CRE1 trained checkpoint remains isolated from SWB1/SWB2 and invalid checksums',()=>{
 const m=new RelationalEncoder(42);
 const text='Welche Sprache spricht Anna?';
 const feats=features(text);
 assert.ok(feats.length>4);
 assert.ok(Math.abs(feats.reduce((s,f)=>s+f.w,0)-1)<1e-12);
 const raw=m.save();
 assert.equal(raw.readInt32BE(0),0x43524531);
 const restored=RelationalEncoder.restore(raw);
 assert.ok(Math.abs(restored.score(text,'Anna spricht Französisch.')-m.score(text,'Anna spricht Französisch.'))<1e-12);
 const broken=Buffer.from(raw);broken[Math.floor(broken.length/2)]^=1;
 assert.throws(()=>RelationalEncoder.restore(broken),/checksum/i);
});
