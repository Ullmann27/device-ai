// Deterministic synthetic Node -> Kotlin -> Node trained-SWB1 parity protocol.
import fs from 'node:fs';
import path from 'node:path';
import {SubwordEncoder} from './portable-subword.mjs';
import {trainContrastiveCandidate} from './contrastive-views.mjs';

const train=[
  'sonnenlicht bringt der pflanze energie',
  'motor getriebe bewegen das fahrzeug',
  'klavier gitarre und musik instrumente',
  'regen wolken feuchtigkeit wetter',
  'stern mond galaxie planeten',
  'backofen brot rezept und mehl'
];
const holdout=[
  'fluss wasser fließt durch ein tal',
  'arzt medizin hilft bei gesundheitsfragen',
  'computer tastatur monitor und bildschirm',
  'katze hund tiere auf dem bauernhof'
];
const EPS=1e-8;
const run=()=>trainContrastiveCandidate(
  new SubwordEncoder(91),train,holdout,{steps:350,seed:43,drop:0.65,rate:0.10}
);
const vals=r=>{
  const out={steps:r.steps,interrupted:String(r.interrupted)};
  for(const tag of ['trainingBefore','trainingAfter','validationBefore','validationAfter'])
    for(const metric of ['top1','total','reciprocalRank'])
      out[tag+'.'+metric]=r[tag][metric];
  return out;
};
const parse=file=>Object.fromEntries(fs.readFileSync(file,'utf8').trim().split('\n')
  .map(row=>{const i=row.indexOf('=');if(i<1)throw Error('Invalid metric');return [row.slice(0,i),row.slice(i+1)]}));
const file=(dir,name)=>path.join(dir,name);
export function main(command,dir){
  if(!dir||!['prepare','verify'].includes(command))throw Error('Usage: prepare|verify DIRECTORY');
  const r=run(),metrics=vals(r);
  if(command==='prepare'){
    fs.mkdirSync(dir,{recursive:true});
    fs.writeFileSync(file(dir,'node-candidate.swb1'),r.candidate.save());
    fs.writeFileSync(file(dir,'node-metrics.tsv'),
      Object.entries(metrics).map(([k,v])=>k+'='+v).join('\n')+'\n');
    console.log('Train MRR:',r.trainingBefore.reciprocalRank,'->',r.trainingAfter.reciprocalRank,
      'Holdout MRR:',r.validationBefore.reciprocalRank,'->',r.validationAfter.reciprocalRank);
    return;
  }
  const check=parse(file(dir,'kotlin-metrics.tsv'));
  if(Object.keys(check).length!==Object.keys(metrics).length)throw Error('Metric count mismatch');
  for(const [k,v] of Object.entries(metrics)){
    if(!(k in check))throw Error('Missing '+k);
    const good=typeof v==='number'?Number.isFinite(Number(check[k]))&&Math.abs(Number(check[k])-v)<=EPS:check[k]===v;
    if(!good)throw Error('Metric discrepancy '+k+': '+check[k]+' vs '+v);
  }
  const a=SubwordEncoder.restore(r.candidate.save());
  const b=SubwordEncoder.restore(fs.readFileSync(file(dir,'kotlin-candidate.swb1')));
  if(a.seed!==b.seed||a.updates!==b.updates)throw Error('Model provenance mismatch');
  let max=0;
  for(const key of ['embeddings','head']){
    if(a[key].length!==b[key].length)throw Error('Dimensions mismatch');
    for(let i=0;i<a[key].length;i++){
      const difference=Math.abs(a[key][i]-b[key][i]);
      if(!(difference<=EPS))throw Error('Weight mismatch '+key+'['+i+']');
      max=Math.max(max,difference);
    }
  }
  console.log('Cross-runtime parity verified:',a.embeddings.length+a.head.length,'weights, max error',max);
}
if(process.argv[1]?.endsWith('contrastive-parity.mjs'))main(process.argv[2],process.argv[3]);
