// Read-only CRE1 Node/Kotlin feature and trained-checkpoint parity.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { RelationalEncoder, train, features, tokens } from './cre-encoder.mjs';
import { files } from './relational-memory-data.mjs';
const CASES=[
 ['Wann hat Anna Geburtstag?','Anna ist am 3. Mai geboren.'],
 ['Wo lebt Lukas?','Lukas wohnt seit Jahren in Graz.'],
 ['Welche Sprachkenntnisse hat Sophie?','Sophie spricht fließend Italienisch.'],
 ['Wie erreiche ich Felix telefonisch?','Die Telefonnummer von Felix ist 0664 123 4567.'],
 ['Was steht bei Marie in der Garage?','Marie fährt einen VW Golf.'],
 ['Wo liegt der Ersatzschlüssel?','Der Ersatzschlüssel liegt in der obersten Schublade.'],
 ['Welches Tier hat Jonas?','Jonas hat einen Hund namens Bello.']
];
const num=x=>x.toPrecision(17);
const file=(dir,name)=>path.join(dir,name);
const scores=m=>CASES.map(([q,t])=>{
 const qv=m.embed(q,'q'),mv=m.embed(t,'m');
 return [q,t,num(m.score(q,t)),num(qv[0]),num(qv[31]),num(mv[0]),num(mv[31])].join('\t');
});
export function prepare(dir){
 fs.mkdirSync(dir,{recursive:true});
 const {curriculum}=files(),m=new RelationalEncoder(42);
 train(m,curriculum.pairs.slice(0,24),curriculum.memories,
  {epochs:2,batch:6,lr:0.005,tau:0.1,size:6,hard:2,seed:43});
 fs.writeFileSync(file(dir,'node-cre1.cre1'),m.save());
 fs.writeFileSync(file(dir,'node-scores.tsv'),scores(m).join('\n')+'\n');
 fs.writeFileSync(file(dir,'node-features.tsv'),CASES.map(([q])=>
  [q,tokens(q).join(','),features(q).map(f=>f.id+':'+num(f.w)).join(',')].join('\t')
 ).join('\n')+'\n');
 console.log('Prepared CRE1 model '+m.steps+' steps, '+CASES.length+' cases');
}
export function verify(dir){
 const m=RelationalEncoder.restore(fs.readFileSync(file(dir,'node-cre1.cre1')));
 const expected=scores(m),got=fs.readFileSync(file(dir,'kotlin-scores.tsv'),'utf8').trimEnd().split('\n');
 assert.equal(got.length,expected.length);
 got.forEach((line,i)=>{
  const a=line.split('\t'),b=expected[i].split('\t');
  assert.equal(a.length,b.length);
  assert.deepEqual(a.slice(0,2),b.slice(0,2));
  for(let j=2;j<a.length;j++)assert.ok(Number.isFinite(Number(a[j]))&&Math.abs(Number(a[j])-Number(b[j]))<=1e-8,
    'CRE1 score or embedding mismatch row '+i+' field '+j+': '+a[j]+' vs '+b[j]);
 });
 console.log('CRE1 Kotlin/Node inference parity verified on '+got.length+' cases');
}
if(process.argv[1]?.endsWith('cre-parity.mjs')){
 const [mode,dir]=process.argv.slice(2);
 if(!dir||!['prepare','verify'].includes(mode))throw Error('Usage: cre-parity.mjs prepare|verify <dir>');
 if(mode==='prepare')prepare(dir);else verify(dir);
}
