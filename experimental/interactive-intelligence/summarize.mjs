import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const dir=process.argv[2];
if(!dir)throw Error('output directory required');
const read=(p)=>fs.readFileSync(path.join(dir,p),'utf8');
const tsv=p=>{const [head,...rows]=read(p).trim().split('\n');const names=head.split('\t');return rows.map(r=>Object.fromEntries(r.split('\t').map((v,i)=>[names[i],v])));};
const files=fs.readdirSync(dir);
const results={scope:'synthetic native JVM research; NOT device or Gemma inference',files:{},dialogue:{},models:{}};
for(const f of files.filter(x=>/^(baseline|results)-.*\.tsv$/.test(x))){
 const rows=tsv(f); const groups=Map.groupBy(rows,x=>x.arm);
 results.dialogue[f]=Object.fromEntries([...groups].map(([arm,items])=>{
 const ns=items.map(x=>+x.nanos).sort((a,b)=>a-b);
 return [arm,{n:items.length,correct:items.filter(x=>x.correct==='true').length,notApplicable:items.filter(x=>x.correct==='NA').length,
 abstain:items.filter(x=>x.abstain==='true').length,wrong:items.filter(x=>x.wrong==='true').length,retained:items.filter(x=>x.retained==='true').length,
 p50Microseconds:ns[Math.floor(ns.length*.50)]/1000,p95Microseconds:ns[Math.floor(ns.length*.95)]/1000,maxContextBytes:Math.max(...items.map(x=>+x.bytes))}];
 }));
}
if(files.includes('models.tsv'))for(const [arm,rows] of Map.groupBy(tsv('models.tsv'),x=>x.arm))results.models[arm]={correct:rows.reduce((s,r)=>s+(+r.correct),0),total:rows.reduce((s,r)=>s+(+r.total),0),seeds:rows.map(r=>+r.seed)};
if(files.includes('mechanisms.tsv'))results.mechanisms=tsv('mechanisms.tsv');
for(const f of files)if(fs.statSync(path.join(dir,f)).isFile()&&f!=='summary.json')results.files[f]=crypto.createHash('sha256').update(fs.readFileSync(path.join(dir,f))).digest('hex');
fs.writeFileSync(path.join(dir,'summary.json'),JSON.stringify(results,null,2)+'\n');
console.log(JSON.stringify({dialogue:results.dialogue,models:results.models},null,2));
